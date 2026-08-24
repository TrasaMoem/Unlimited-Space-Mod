package com.modscreating.unlimitedspace.nav;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.rae.creatingspace.content.planets.CSDimensionUtil;
import com.rae.creatingspace.content.rocket.RocketContraptionEntity;
import com.rae.creatingspace.content.rocket.contraption.RocketContraption;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.HashSet;
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
                               String perPropellant) {
        public boolean anyShortage() {
            return fuelShortageKg > 0.5f || !thrustOk;
        }
    }

    /** null / absent rocket -> benign all-zero requirement (never blocks). */
    public static Requirements compute(RocketContraptionEntity rocket, ResourceLocation destRL) {
        if (rocket == null || destRL == null
                || !(rocket.getContraption() instanceof RocketContraption contraption)) {
            return new Requirements(0, 0, 0, 0, 0, true, true, 0, 0, "");
        }
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
            float consumptionKgS = 0f;
            StringBuilder perPropellant = new StringBuilder();
            var tpt = contraption.getTPTFluidConsumption();
            if (tpt != null) {
                for (var entry : tpt.entrySet()) {
                    var info = entry.getValue();
                    if (info == null || info.propellantConsumption() == null) continue;
                    float typeTotal = 0f;
                    for (float v : info.propellantConsumption().values()) {
                        consumptionKgS += v;
                        typeTotal += v;
                    }
                    if (typeTotal > 0 && perPropellant.length() < 220) {
                        if (perPropellant.length() > 0) perPropellant.append(';');
                        perPropellant.append(entry.getKey().toString())
                                .append('=')
                                .append(String.format(java.util.Locale.ROOT, "%.2f", typeTotal));
                    }
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
            if (ve > 0 && routeCost > 0) {
                requiredFuelKg = (float) (m0 * (1.0 - Math.exp(-routeCost / ve)));
            } else {
                // destination-independent fallback: flags a missing/broken route in the graph
                requiredFuelKg = consumptionKgS * (float) travelSeconds;
                UnlimitedSpace.LOGGER.warn(
                        "[US][R15.2] route cost missing/zero ({} -> {} = {}); using ascent-only fuel estimate",
                        rocket.level().dimension().location(), destRL, routeCost);
            }
            float shortage = Math.max(0f, requiredFuelKg - availableKg);
            boolean fuelOk = shortage <= 0.5f;

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
                    travelSeconds, consumptionKgS, perPropellant.toString());
        } catch (Throwable t) {
            UnlimitedSpace.LOGGER.warn("[US][R15.2] flight planner failed", t);
            return new Requirements(0, 0, 0, 0, 0, true, true, 0, 0, "");
        }
    }

    /** Mirror of CS {@code getPerTickSpeed(F)}: sign(a)·ln(1.4 + |a|/20), clamped [-1,1]. */
    private static double perTickSpeed(float accel) {
        float sign = Math.signum(accel);
        double raw = sign * Math.log(1.4 + Math.abs(accel) / 20.0);
        return Mth.clamp((float) raw, -1.0f, 1.0f);
    }
}