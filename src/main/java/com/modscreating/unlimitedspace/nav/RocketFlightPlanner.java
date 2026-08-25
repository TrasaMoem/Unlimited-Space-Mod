package com.modscreating.unlimitedspace.nav;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.rae.creatingspace.content.planets.CSDimensionUtil;
import com.rae.creatingspace.content.rocket.RocketContraptionEntity;
import com.rae.creatingspace.content.rocket.contraption.RocketContraption;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * R15.2 flight-requirement planner. Recomputes the exact CS trajectory math
 * (from 1.7.18 bytecode of {@code RocketContraptionEntity.handelTrajectoryCalculation})
 * so the UI can show how much fuel / thrust a trip needs and how much is missing.
 *
 * <p>Formulas (mirror CS): accel = thrust/mass - gravity; per-tick speed =
 * sign(a)·ln(1.4 + |a|/20) clamped to [-1,1]; travelTime = distance/speed;
 * requiredFuel(kg) = consumption(kg/s)·travelTime. Available fuel (kg) =
 * Σ tank amount·density/1000 over consumable fluids.
 */
public final class RocketFlightPlanner {

    /** CS ascent altitude used by trajectory calc (the "300" constant). */
    private static final double SPACE_ALTITUDE = 300.0;

    private RocketFlightPlanner() {}

    /** Everything a trip needs, computed from the real rocket + destination. */
    public record Requirements(float requiredFuelKg, float availableFuelKg, float fuelShortageKg,
                               float thrustRequired, float thrustAvailable,
                               boolean fuelOk, boolean thrustOk,
                               double travelSeconds, float consumptionKgS,
                               String perPropellant, float launchSurchargeDeltaV,
                               float distanceSurchargeDeltaV, float distanceFuelKg,
                               String fluidBalance, String fuelShortageReason) {
        public boolean anyShortage() {
            return fuelShortageKg > 0.5f || !thrustOk;
        }
    }

    // ---- R16: LIFT-OFF surcharge (deltaV added on top of the route cost) ----
    // Climbing out of a GRAVITY WELL is what really burns fuel: starting from a
    // planet/moon SURFACE costs a lot, from a STAR surface even more, while starting
    // from an ORBIT or an ASTEROID FIELD is essentially free.
    public static final int SURCHARGE_STAR_SURFACE = 4000;
    public static final int SURCHARGE_PLANET_SURFACE = 1500;
    public static final int SURCHARGE_MOON_SURFACE = 1200;
    public static final int SURCHARGE_ORBIT = 0;

    /**
     * Lift-off surcharge for the dimension the rocket is standing in.
     * Recognised keys: {@code star/.../surface}, {@code planet|moon|.../surface},
     * everything with "orbit" / "asteroid" / the void "space" counts as free.
     */
    public static int liftOffSurcharge(ResourceLocation originRl) {
        String p = originRl == null ? "" : originRl.getPath();
        if (p.contains("orbit") || p.contains("asteroid") || p.equals("space")) {
            return SURCHARGE_ORBIT;
        }
        if (p.startsWith("star/") && p.endsWith("surface")) {
            return SURCHARGE_STAR_SURFACE;
        }
        boolean moon = p.contains("moon");
        boolean solSurface = p.equals("overworld") || p.equals("the_moon")
                || p.equals("mars") || p.equals("venus");
        if (!moon && (solSurface || p.endsWith("surface"))) {
            return SURCHARGE_PLANET_SURFACE;
        }
        if (moon && p.endsWith("surface")) {
            return SURCHARGE_MOON_SURFACE;
        }
        return SURCHARGE_ORBIT;
    }

    /**
     * R16: DISTANCE surcharge between the system the rocket is currently in and the
     * destination system - the server twin of the GALAXY-tab "Dist. surcharge" preview.
     * Returns 0 when either side cannot be resolved (Sol anchor is fully supported).
     */
    private static int distanceSurcharge(RocketContraptionEntity rocket, ResourceLocation destRL) {
        try {
            long seed = 0;
            var server = rocket.level().getServer();
            if (server != null) seed = server.overworld().getSeed();
            var galaxy = com.modscreating.unlimitedspace.core.galaxy.Galaxy.from(seed);
            double radius = galaxy.parameters().radius();

            String originPath = rocket.level().dimension().location().getPath();
            int fromIdx = com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel
                    .systemIndexFromKey(originPath);
            int toIdx = com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel
                    .systemIndexFromKey(destRL.getPath());

            double[] from = systemPos(galaxy, radius, fromIdx,
                    rocket.level().dimension().location().toString());
            double[] to = systemPos(galaxy, radius, toIdx, destRL.getPath());
            if (from == null || to == null) return 0;
            return com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel
                    .surchargeFrom(from[0], from[1], to[0], to[1], radius);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** GU position of a system index / dimension key; null when unknown. */
    private static double[] systemPos(
            com.modscreating.unlimitedspace.core.galaxy.Galaxy galaxy,
            double radius, int systemIndex, String dimKey) {
        double[] sol = com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel
                .solPosition(radius);
        if (systemIndex == -2 || dimKey.startsWith("overworld")
                || dimKey.startsWith("the_moon") || dimKey.startsWith("mars")
                || dimKey.startsWith("venus") || dimKey.endsWith("_orbit")
                && !dimKey.contains("system_")) {
            return sol;
        }
        if (systemIndex < 0) return null;
        var pos = galaxy.getStarSystem(
                com.modscreating.unlimitedspace.core.stars.StarSystemId.of(systemIndex)).position();
        return new double[]{pos.x(), pos.z()};
    }

    /** null / absent rocket -> benign all-zero requirement (never blocks). */
    public static Requirements compute(RocketContraptionEntity rocket, ResourceLocation destRL) {
        if (rocket == null || destRL == null
                || !(rocket.getContraption() instanceof RocketContraption contraption)) {
            return new Requirements(0, 0, 0, 0, 0, true, true, 0, 0, "", 0, 0, 0, "", "");
        }
        // ---- R16: DISTANCE surcharge (current system -> destination system) ----
        // Mirrors the GALAXY-tab preview: the farther the target is from where you
        // are NOW, the more deltaV the trip burns. Computed from the canonical
        // system positions so the server value matches the UI mechanic exactly.
        int distanceSurcharge = distanceSurcharge(rocket, destRL);
        try {
            // ---- available fuel (kg) = Σ amount·density/1000 over consumable fluids ----
            Set<Fluid> consumable = new HashSet<>();
            if (rocket.consumableFluids != null) {
                rocket.consumableFluids.values().forEach(list -> {
                    if (list != null) consumable.addAll(list);
                });
            }
            float availableKg = 0f;
            try {
                IFluidHandler fluids = contraption.getStorage().getFluids();
                for (int i = 0; i < fluids.getTanks(); i++) {
                    FluidStack fs = fluids.getFluidInTank(i);
                    if (fs == null || fs.isEmpty()) continue;
                    Fluid f = fs.getFluid();
                    if (!consumable.isEmpty() && !consumable.contains(f)) continue;
                    availableKg += fs.getAmount() * f.getFluidType().getDensity() / 1000.0f;
                }
            } catch (Throwable t) {
                UnlimitedSpace.LOGGER.warn("[US][R15.2] could not read fuel tanks", t);
            }

            // ---- theoretical consumption (kg/s) summed over propellant tags ----
            // R19: aggregate by the actual propellant FLUID so the UI shows real propellant
            // names (methane/oxygen) instead of the opaque engine map key whose toString()
            // was "PropellantType{propellantRatio=...}", which looked broken in the ROCKET
            // panel under TRIP TIME.
            float consumptionKgS = 0f;
            StringBuilder perPropellant = new StringBuilder();
            var tpt = contraption.getTPTFluidConsumption();
            if (tpt != null) {
                Map<String, Float> rateByLabel = new LinkedHashMap<>();
                for (var entry : tpt.entrySet()) {
                    var info = entry.getValue();
                    if (info == null || info.propellantConsumption() == null) continue;
                    for (var pc : info.propellantConsumption().entrySet()) {
                        float v = pc.getValue();
                        if (v <= 0) continue;
                        consumptionKgS += v;
                        String lbl = fluidLabel(pc.getKey());
                        if (lbl.isEmpty()) lbl = "propellant";
                        rateByLabel.merge(lbl, v, Float::sum);
                    }
                }
                for (var e : rateByLabel.entrySet()) {
                    if (perPropellant.length() >= 220) break;
                    if (perPropellant.length() > 0) perPropellant.append(';');
                    perPropellant.append(e.getKey()).append('=')
                            .append(String.format(java.util.Locale.ROOT, "%.2f", e.getValue()));
                }
            }

            // ---- thrust, mass, gravity ----
            // R16 FIX: the launch-gate mass must include ALL fluids on board, exactly like
            // CS does. Previously only the consumable propellant was counted here, so the
            // UI understated the rocket weight whenever tanks held other fluids
            // (oxidizer, coolant, ...) -> the panel showed THRUST OK while the real
            // Creating Space ascent refused with "not enough thrust".
            float totalFluidKg = 0f;
            try {
                IFluidHandler allFluids = contraption.getStorage().getFluids();
                for (int i = 0; i < allFluids.getTanks(); i++) {
                    FluidStack fs = allFluids.getFluidInTank(i);
                    if (fs == null || fs.isEmpty()) continue;
                    totalFluidKg += fs.getAmount() * fs.getFluid().getFluidType().getDensity() / 1000.0f;
                }
            } catch (Throwable t) {
                totalFluidKg = availableKg; // best effort if the tank capability is unreadable
            }
            float thrustAvailable = contraption.getThrust();
            // R16 FIX: TPT consumption is often EMPTY until engines ignite, which zeroed
            // ve / requiredFuel / everything downstream ("-" in the ROCKET panel).
            // Fall back to a CS-scale estimate derived from the actual thrust.
            float VE_FALLBACK = 30_000f; // N per kg/s, typical Creating Space engine scale
            if (consumptionKgS <= 0 && thrustAvailable > 0) {
                consumptionKgS = thrustAvailable / VE_FALLBACK;
                if (perPropellant.length() == 0) {
                    perPropellant.append("(estimated @ ve=").append(VE_FALLBACK).append(')');
                }
            }
            float mass = contraption.getDryMass() + Math.max(totalFluidKg, availableKg);
            float gravity;
            try {
                gravity = CSDimensionUtil.gravity(rocket.level().dimension().location());
            } catch (Throwable t) {
                gravity = 9.81f;
            }
            // R16 FIX: an orbit origin has ~zero weight -> mass*g was 0 and THRUST REQ
            // displayed "0". Plan the ascent against at least the DESTINATION body's
            // gravity so the numbers stay meaningful; the launch gate itself is still
            // checked against the ORIGIN by CS below.
            float destGravity = gravity;
            try {
                destGravity = CSDimensionUtil.gravity(destRL);
            } catch (Throwable ignored) {
            }
            gravity = Math.max(Math.max(gravity, Math.min(destGravity, 1.0f)), 0.05f);
            float thrustRequired = mass * gravity;

            // ---- acceleration & travel time ----
            float accel = (mass > 0) ? (thrustAvailable / mass - gravity) : -gravity;
            boolean thrustOk = thrustAvailable > thrustRequired + 0.1f;
            double travelTicks = 0;
            if (thrustOk) {
                double speed = perTickSpeed(accel);
                double distance = Math.max(1.0, SPACE_ALTITUDE - rocket.getY());
                if (speed > 0.0001) travelTicks = distance / speed;
            }
            double travelSeconds = travelTicks / 20.0;

            // ---- R15.3: required fuel via CS Tsiolkovsky form (bytecode 577-604):
            // m0 = inertMass + propellantOnBoard ; burned = m0 * (1 - e^(-cost/ve))
            // where cost = CSDimensionUtil.cost(origin, dest) -> destination-dependent!
            float inertMass = contraption.getDryMass() + (totalFluidKg - availableKg);
            float m0 = inertMass + availableKg;
            float ve = consumptionKgS > 0 ? thrustAvailable / consumptionKgS : 0f;
            float routeCost;
            try {
                routeCost = CSDimensionUtil.cost(
                        rocket.level().dimension().location(), destRL);
            } catch (Throwable t) {
                routeCost = 0;
            }
            float requiredFuelKg;
            // R16: lift-off surcharge - climbing out of a gravity well costs extra
            int liftOff = liftOffSurcharge(rocket.level().dimension().location());
            float effectiveCost = routeCost + Math.max(0, liftOff)
                    + Math.max(0, distanceSurcharge);
            if (ve > 0 && effectiveCost > 0) {
                requiredFuelKg = (float) (m0 * (1.0 - Math.exp(-effectiveCost / ve)));
                if (routeCost <= 0) {
                    UnlimitedSpace.LOGGER.warn(
                            "[US][R16] route cost missing/zero ({} -> {}); fuel from lift-off surcharge {}",
                            rocket.level().dimension().location(), destRL, liftOff);
                }
            } else {
                // destination-independent fallback: flags a missing/broken route in the graph
                requiredFuelKg = consumptionKgS * (float) travelSeconds;
                UnlimitedSpace.LOGGER.warn(
                        "[US][R15.2] no usable cost/ve for ({} -> {} = {}, lift-off {}); ascent-only estimate",
                        rocket.level().dimension().location(), destRL, routeCost, liftOff);
            }
            float shortage = Math.max(0f, requiredFuelKg - availableKg);

            // R20: distance-only fuel - how much of the burned fuel comes purely from the
            // DISTANCE surcharge. Recompute with the surcharge zeroed out; the difference is
            // the extra mass the longer trip consumes (mirrors the GALAXY-tab "Extra fuel").
            float distanceFuelKg = 0f;
            if (ve > 0 && distanceSurcharge > 0 && effectiveCost > 0) {
                float noDistCost = routeCost + Math.max(0, liftOff);
                float fuelNoDist = (float) (m0 * (1.0 - Math.exp(-noDistCost / ve)));
                distanceFuelKg = Math.max(0f, requiredFuelKg - fuelNoDist);
            }

            // R17: per-fluid balance - CS burns methane+oxygen at once, so a launch can be
            // denied because ONE propellant is short even when total mass looks fine. Split
            // required mass by consumption rate (keyed by TagKey<Fluid>); consumableFluids
            // maps each tag to the actual tank fluids. Fall back to a proportional split and
            // flag it as estimated when no real rate is known yet (engines not ignited).
            float worstShort = shortage;
            boolean perFluidOk = true;
            int shortCount = 0;
            StringBuilder fluidBalance = new StringBuilder();
            StringBuilder shortNames = new StringBuilder();
            boolean fluidEstimated = false;
            String bindLabel = null;
            float bindReq = 0f, bindHave = 0f;
            try {
                Map<Fluid, Float> tankMass = new HashMap<>();
                try {
                    IFluidHandler tf = contraption.getStorage().getFluids();
                    for (int i = 0; i < tf.getTanks(); i++) {
                        FluidStack fs = tf.getFluidInTank(i);
                        if (fs == null || fs.isEmpty()) continue;
                        tankMass.merge(fs.getFluid(),
                                fs.getAmount() * fs.getFluid().getFluidType().getDensity() / 1000.0f,
                                Float::sum);
                    }
                } catch (Throwable ignored) { }

                Map<TagKey<Fluid>, Float> haveByTag = new LinkedHashMap<>();
                Set<Fluid> counted = new HashSet<>();
                if (rocket.consumableFluids != null) {
                    for (var te : rocket.consumableFluids.entrySet()) {
                        List<Fluid> fluids = te.getValue();
                        if (fluids == null) continue;
                        float m = 0f;
                        for (Fluid fr : fluids) {
                            if (fr == null || counted.contains(fr)) continue;
                            Float t = tankMass.get(fr);
                            if (t != null && t > 0) m += t;
                            counted.add(fr);
                        }
                        haveByTag.put(te.getKey(), m);
                    }
                }

                Map<TagKey<Fluid>, Float> rateByTag = new LinkedHashMap<>();
                if (tpt != null) {
                    for (var entry : tpt.entrySet()) {
                        var info = entry.getValue();
                        if (info == null || info.propellantConsumption() == null) continue;
                        for (var pc : info.propellantConsumption().entrySet()) {
                            rateByTag.merge(pc.getKey(), pc.getValue(), Float::sum);
                        }
                    }
                }
                float totalRate = 0f;
                for (float v : rateByTag.values()) totalRate += v;
                fluidEstimated = totalRate <= 0f;

                Set<TagKey<Fluid>> keys = new LinkedHashSet<>();
                keys.addAll(rateByTag.keySet());
                keys.addAll(haveByTag.keySet());
                float trackedHave = 0f;
                for (float v : haveByTag.values()) trackedHave += v;

                for (TagKey<Fluid> tag : keys) {
                    float rate = rateByTag.getOrDefault(tag, 0f);
                    float have = haveByTag.getOrDefault(tag, 0f);
                    float req;
                    if (totalRate > 0) {
                        req = requiredFuelKg * rate / totalRate;
                    } else if (trackedHave > 0) {
                        req = requiredFuelKg * have / trackedHave;
                    } else {
                        req = requiredFuelKg / Math.max(1, keys.size());
                    }
                    float s = Math.max(0f, req - have);
                    if (s > 0.5f) {
                        perFluidOk = false;
                        shortCount++;
                        if (s > worstShort) {
                            worstShort = s;
                            bindLabel = fluidLabel(tag);
                            bindReq = req;
                            bindHave = have;
                        }
                        if (shortNames.length() > 0) shortNames.append(" and ");
                        shortNames.append(fluidLabel(tag));
                    }
                    if (fluidBalance.length() > 0) fluidBalance.append(';');
                    fluidBalance.append(fluidLabel(tag)).append('=')
                            .append(String.format(java.util.Locale.ROOT, "%.0f,%.0f", req, have));
                }
            } catch (Throwable t) {
                UnlimitedSpace.LOGGER.warn("[US][R17] per-fluid balance failed", t);
            }
            if (fluidEstimated) fluidBalance.append(";~est");

            boolean fuelOk = perFluidOk && shortage <= 0.5f;
            String shortageReason = "";
            if (!fuelOk) {
                if (shortCount == 1 && bindLabel != null) {
                    shortageReason = String.format(java.util.Locale.ROOT,
                            "Not enough %s: need %.0f kg, have %.0f kg (short %.0f kg).",
                            bindLabel, bindReq, bindHave, Math.max(0f, bindReq - bindHave));
                } else {
                    String what = shortNames.length() > 0 ? shortNames.toString() : "fuel";
                    shortageReason = String.format(java.util.Locale.ROOT,
                            "Not enough %s: need %.1f kg, have %.1f kg (short %.1f kg).",
                            what, requiredFuelKg, availableKg, shortage);
                }
            }

            UnlimitedSpace.LOGGER.info(
                    "[US][R15.2] requirements: origin={} dest={} routeCost={} ve={} m0={} "
                            + "(inert={} prop={}) requiredFuel={} availableFuel={} thrust {}/{}",
                    rocket.level().dimension().location(), destRL, routeCost,
                    String.format(java.util.Locale.ROOT, "%.1f", ve),
                    String.format(java.util.Locale.ROOT, "%.1f", m0),
                    String.format(java.util.Locale.ROOT, "%.1f", inertMass),
                    String.format(java.util.Locale.ROOT, "%.1f", availableKg),
                    String.format(java.util.Locale.ROOT, "%.1f", requiredFuelKg),
                    String.format(java.util.Locale.ROOT, "%.1f", availableKg),
                    String.format(java.util.Locale.ROOT, "%.0f", thrustAvailable),
                    String.format(java.util.Locale.ROOT, "%.0f", thrustRequired));

            return new Requirements(requiredFuelKg, availableKg, shortage,
                    thrustRequired, thrustAvailable, fuelOk, thrustOk,
                    travelSeconds, consumptionKgS, perPropellant.toString(), liftOff,
                    distanceSurcharge, distanceFuelKg, fluidBalance.toString(), shortageReason);
        } catch (Throwable t) {
            UnlimitedSpace.LOGGER.warn("[US][R15.2] flight planner failed", t);
            return new Requirements(0, 0, 0, 0, 0, true, true, 0, 0, "", 0, 0, 0, "", "");
        }
    }

    /** Short display name for a fluid-tag key (drops the namespace / path prefix). */
    private static String fluidLabel(TagKey<Fluid> tag) {
        if (tag == null) return "propellant";
        String p = tag.location().getPath();
        int i = p.lastIndexOf('/');
        if (i >= 0) p = p.substring(i + 1);
        return p;
    }

    /** Mirror of CS {@code getPerTickSpeed(F)}: sign(a)·ln(1.4 + |a|/20), clamped [-1,1]. */
    private static double perTickSpeed(float accel) {
        float sign = Math.signum(accel);
        double raw = sign * Math.log(1.4 + Math.abs(accel) / 20.0);
        return Mth.clamp((float) raw, -1.0f, 1.0f);
    }
}