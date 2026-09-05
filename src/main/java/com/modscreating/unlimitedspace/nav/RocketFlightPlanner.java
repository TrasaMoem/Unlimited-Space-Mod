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

import java.util.ArrayList;
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

    // ---- R27 correlatable runtime trace ----
    private static final java.util.concurrent.atomic.AtomicLong TRACE_SEQ =
            new java.util.concurrent.atomic.AtomicLong();
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    /** Begin a correlatable fuel trace; returns the id to pass between log lines. */
    public static long beginTrace() {
        long id = TRACE_SEQ.incrementAndGet();
        TRACE_ID.set("req=" + id);
        return id;
    }

    public static void endTrace() {
        TRACE_ID.remove();
    }

    /** Current trace id tag for log prefixes and on-screen correlation ("req=N" or "?"). */
    public static String traceId() {
        String id = TRACE_ID.get();
        return id == null ? "?" : id;
    }

    // ---- R25 same-inputs -> same-output assertion (temporary diagnostic) ----
    private record FuelInputsKey(String origin, String dest, float dryMass, float totalFluidKg,
                                 float thrust, float consumptionKgS, float routeCost,
                                 int liftOff, float distSurcharge) {}
    private static FuelInputsKey lastInputs;
    private static float lastRequiredFuel;

    /**
     * Temporary R25 diagnostic: when ALL calculation inputs are identical but the produced
     * requiredFuel differs, log a full ERROR with both snapshots. This distinguishes
     * "input changed legitimately" from "hidden mutable state inside the calculator".
     */
    private static void assertSameInputsGiveSameFuel(String origin, String dest,
            float dryMass, float totalFluidKg, float thrust, float consumptionKgS,
            float routeCost, int liftOff, float distSurcharge, float requiredFuelKg) {
        FuelInputsKey key = new FuelInputsKey(origin, dest, dryMass, totalFluidKg,
                thrust, consumptionKgS, routeCost, liftOff, distSurcharge);
        if (lastInputs != null && lastInputs.equals(key)
                && Math.abs(lastRequiredFuel - requiredFuelKg) > 0.5f) {
            UnlimitedSpace.LOGGER.error(
                    "[FUEL-DEBUG] DETERMINISM VIOLATION: identical inputs produced different "
                            + "fuel: last={} now={} inputs={}",
                    lastRequiredFuel, requiredFuelKg, key);
        }
        lastInputs = key;
        lastRequiredFuel = requiredFuelKg;
    }

    /** Everything a trip needs, computed from the real rocket + destination. */
    public record Requirements(float requiredFuelKg, float availableFuelKg, float fuelShortageKg,
                               float thrustRequired, float thrustAvailable,
                               boolean fuelOk, boolean thrustOk,
                               double travelSeconds, float consumptionKgS,
                               String perPropellant, float launchSurchargeDeltaV,
                               float distanceSurchargeDeltaV, float distanceFuelKg,
                               String fluidBalance, String fuelShortageReason,
                               float routeCost, float m0, float ve,
                               float kinematicFuelKg,
                               String destKey, String engineSource) {
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
     * R37: destination-ARRIVAL surcharge - the mirror of {@link #liftOffSurcharge}.
     * Landing on a SURFACE means dropping into the destination's gravity well and
     * burns fuel; arriving at an ORBIT / asteroid field is (nearly) free. This is
     * what makes FUEL REQUIRED differ between the objects of the SAME system:
     * previously every surface / orbit / satellite of one system showed the
     * identical price, because the cruise was priced from the SYSTEM distance only.
     */
    public static int arrivalSurcharge(ResourceLocation destRl) {
        String p = destRl == null ? "" : destRl.getPath();
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

    // ---- R30: requirement-driven fuel model -------------------------------------
    //
    // requiredFuel = MAX( kinematic, gate )
    //   kinematic = ASCENT only (origin-gravity burn; descent/reentry is UNPOWERED in CS
    //             - decompiled tickConsumptionAndSpeed skips consumePropellant on reentry)
    //             -> what the flight ACTUALLY drains (runtime-confirmed twice);
    //   gate      = Tsiolkovsky on CSDimensionUtil.cost(origin, dest)
    //             -> what CS's launch gate actually ENFORCES; the cost graph's
    //                overworld->destination hub edge embeds the origin-relative
    //                DISTANCE surcharge, so this term grows with trip distance.
    //
    // * flying TO an orbit skips the DESCENT leg  -> slightly cheaper than to a surface;
    // * flying FROM an orbit skips the ASCENT leg -> much cheaper than from a surface;
    // * planet gravity drives both the burn time AND the THRUST REQ (mass * gravity);
    // * systems farther than MAX_TRAVEL_LY cannot be flown to at all (server-enforced).
    // The two terms are MAXed, NOT summed: summing double-counts the trip (the gate
    // already prices the route, the kinematics already drain it) and blocked launches
    // whose real fuel budget was fine (runtime case: 1079 ly, REQ 56102 vs HAVE 42890).

    /** Hard flight range: systems beyond this distance (ly) are unreachable. */
    public static final double MAX_TRAVEL_LY = 1600.0;

    /** R32: cruise deltaV charged per light-year (added on top of the ascent burn).
     *  Calibrated so the FARTHEST reachable flight (1600 ly) stays fuel-feasible. */
    public static final double CRUISE_DV_PER_LY = 1.0;

    /** True when a dimension key is an orbit-like (weightless) location. */
    public static boolean isOrbitKey(String path) {
        if (path == null) return false;
        return path.contains("orbit") || path.contains("asteroid") || path.equals("space");
    }

    /**
     * R30: the trip distance in LIGHT-YEARS between the system the rocket is standing in
     * and the destination system. 0 for same-system hops (surface<->orbit) and for the
     * Sol/official CS destinations that have no procedural system index.
     */
    public static double systemDistanceLy(RocketContraptionEntity rocket, ResourceLocation destRL) {
        try {
            long seed = 0;
            var server = rocket.level().getServer();
            if (server != null) seed = server.overworld().getSeed();
            // R30b: positions MUST come from the CANONICAL GalaxyLayout map (the same source
            // the galaxy UI and the space dimension use), NOT from Galaxy.getStarSystem /
            // SystemPlacer - those are a DIFFERENT coordinate source and produced wildly
            // wrong distances (a 650 ly hop measured as ~6000 ly -> false OUT_OF_RANGE).
            var map = com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel.from(seed);
            double radius = map.layout().galaxyRadiusGu();
            String originPath = rocket.level().dimension().location().getPath();
            int fromIdx = com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel
                    .systemIndexFromKey(originPath);
            int toIdx = com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel
                    .systemIndexFromKey(destRL.getPath());
            if (toIdx < 0) return 0;
            // R38 FIX: the ORIGIN may be a Sol/official dimension ("overworld", "the_moon",
            // ...) which has NO "system_" index - the old early-return collapsed EVERY trip
            // started from Earth to distance 0, so the launch menu showed the same price for
            // all flights. systemPos() already resolves such origins to the Sol anchor.
            double[] from = systemPos(map, radius, fromIdx, originPath);
            double[] to = systemPos(map, radius, toIdx, destRL.getPath());
            if (from == null || to == null) return 0;
            if (fromIdx >= 0 && fromIdx == toIdx) return 0;
            return com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel
                    .distanceLightYears(from[0], from[1], to[0], to[1], radius);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** CANONICAL map position of a system index / dimension key; null when unknown. */
    private static double[] systemPos(
            com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel map,
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
        var pos = map.systemByIndex(systemIndex);
        if (pos == null) return null;
        return new double[]{pos.x(), pos.z()};
    }

    /**
     * The origin-relative distance surcharge (dV) EXACTLY as the authoritative CS cost
     * graph charges it: {@code surchargeFrom(current system, target system)} over the
     * CANONICAL map positions - the same quantity ProceduralMetadataGenerator mirrors
     * into the overworld->destination hub edge. 0 for same-system / Sol destinations.
     */
    public static int graphDistanceSurchargeDv(RocketContraptionEntity rocket, ResourceLocation destRL) {
        try {
            long seed = 0;
            var server = rocket.level().getServer();
            if (server != null) seed = server.overworld().getSeed();
            var map = com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel.from(seed);
            double radius = map.layout().galaxyRadiusGu();
            String originPath = rocket.level().dimension().location().getPath();
            int fromIdx = com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel
                    .systemIndexFromKey(originPath);
            int toIdx = com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel
                    .systemIndexFromKey(destRL.getPath());
            if (toIdx < 0) return 0;
            double[] from = systemPos(map, radius, fromIdx, originPath);
            double[] to = systemPos(map, radius, toIdx, destRL.getPath());
            if (from == null || to == null) return 0;
            return com.modscreating.unlimitedspace.core.galaxy.layout.GalaxyMapModel
                    .surchargeFrom(from[0], from[1], to[0], to[1], radius);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** null / absent rocket -> benign all-zero requirement (never blocks). */
    public static Requirements compute(RocketContraptionEntity rocket, ResourceLocation destRL) {
        if (rocket == null || destRL == null
                || !(rocket.getContraption() instanceof RocketContraption contraption)) {
            UnlimitedSpace.LOGGER.warn(
                    "[FUEL-TRACE][{}][COMPUTE-EARLY-RETURN] rocket={} contraption={} dest={}",
                    traceId(), rocket, rocket == null ? "-" : rocket.getContraption(), destRL);
            return new Requirements(0, 0, 0, 0, 0, true, true, 0, 0, "", 0, 0, 0, "", "",
                    0, 0, 0, 0, destRL == null ? "" : destRL.toString(), "no-contraption");
        }
        // R31: the destination IS the dimension the rocket is standing in - there is no
        // trip to price (no distance, no cost, no burn). Return an empty requirement set
        // so the UI block reads "-" instead of fabricated numbers.
        if (destRL.equals(rocket.level().dimension().location())) {
            return new Requirements(0, 0, 0, 0, 0, true, true, 0, 0, "", 0, 0, 0, "", "",
                    0, 0, 0, 0, destRL.toString(), "already-here");
        }
        // R24 ROOT-CAUSE FIX: the planner MUST NOT mutate the rocket. Previously this
        // called CsTravelBridge.ensureEngineData(rocket), which REPAIRED an empty TPT map
        // in place on the first computation after landing. Consequence: computation #1 used
        // the empty-TPT fallback ve = 30 000 N·s/kg, and (because that same call repaired the
        // map) computations #2..N used the real consumption -> DIFFERENT fuel requirement for
        // the SAME route after assemble/disassemble/Surface-Orbit-Surface. Now the effective
        // engine table is resolved READ-ONLY (locally rebuilt when the map is empty), so
        // identical inputs always give identical outputs, and they match the repaired state
        // that the authoritative launch path (CsTravelBridge.launch) prepares.
        var tpt = com.modscreating.unlimitedspace.nav.CsTravelBridge.resolveEngineData(rocket);
        // ---- R30: DISTANCE pricing (current system -> destination system) ----
        // The ly distance drives the 1600 ly range gate; the deltaV surcharge is the
        // SAME origin-relative quantity the authoritative CS cost graph charges
        // (mirrored from the canonical map by ProceduralMetadataGenerator).
        double tripDistanceLy = systemDistanceLy(rocket, destRL);
        boolean outOfRange = tripDistanceLy > MAX_TRAVEL_LY + 1e-9;
        // R32: the CRUISE component is priced DIRECTLY from the ly distance and ADDED on
        // top of the kinematic ascent burn. A pure max(kinematic, gate) made the whole
        // requirement origin-dominated: the ascent burn is IDENTICAL for every destination
        // from the same planet, so FUEL REQ / DIST FUEL looked frozen across selections.
        // R38b: price the cruise DIRECTLY from the REAL ly distance - the old
        // Math.min(MAX_TRAVEL_LY, ...) clamp gave every system beyond 1600 ly the
        // IDENTICAL surcharge, so the Launch menu showed one and the same price for
        // all distant planets. Out-of-range trips stay blocked by the separate
        // ROUTE/OUT-OF-RANGE gate; the price itself must keep growing with distance.
        int distanceSurcharge = (int) Math.round(
                tripDistanceLy * CRUISE_DV_PER_LY);
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
            // names (methane/oxygen). R24: rates come from the READ-ONLY resolved table.
            float consumptionKgS = 0f;
            StringBuilder perPropellant = new StringBuilder();
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
            // ---- R30 gravity model ----
            // ASCENT uses ONLY the ORIGIN gravity; the destination's gravity no longer
            // pollutes the launch math. Orbit-like origins/destinations are weightless.
            float originGravity;
            try {
                originGravity = CSDimensionUtil.gravity(rocket.level().dimension().location());
            } catch (Throwable t) {
                originGravity = 9.81f;
            }
            String originPath = rocket.level().dimension().location().getPath();
            boolean originIsOrbit = isOrbitKey(originPath) || originGravity < 0.01f;
            if (originIsOrbit) originGravity = 0f;
            float destGravity;
            try {
                destGravity = CSDimensionUtil.gravity(destRL);
            } catch (Throwable ignored) {
                destGravity = 0f;
            }
            boolean destIsOrbit = isOrbitKey(destRL.getPath()) || destGravity < 0.01f;
            if (destIsOrbit) destGravity = 0f;
            // THRUST REQ = the weight the rocket must beat to lift off from where it IS.
            // From an orbit there is no gravity well: only a small manoeuvring margin.
            float gravity = Math.max(originGravity, 0.05f);
            float thrustRequired = originIsOrbit ? mass * 0.05f : mass * gravity;

            // ---- acceleration & travel time ----
            float accel = (mass > 0) ? (thrustAvailable / mass - gravity) : -gravity;
            boolean thrustOk = thrustAvailable > thrustRequired + 0.1f;
            // ASCENT leg: kinematic burn in the origin gravity well (skipped from orbit).
            double ascentTicks = 0;
            if (!originIsOrbit && thrustOk) {
                double speedUp = perTickSpeed(
                        (float) (thrustAvailable / Math.max(1.0f, mass) - gravity));
                ascentTicks = Math.max(0.0, SPACE_ALTITUDE - rocket.getY())
                        / Math.max(0.05, speedUp);
            }
            // DESCENT leg: unpowered reentry. RUNTIME-CONFIRMED against the decompiled CS
            // flight loop (_rocket_bc.txt, tickConsumptionAndSpeed): consumePropellant is
            // SKIPPED while isReentry() - the descent/landing phase burns NO fuel.
            // It still takes travel time, but it is free, so flying to a surface costs
            // the same kinematic burn as to an orbit (the CS cost-graph gate is what
            // makes surface trips the more expensive ones).
            double descentTicks = 0;
            if (!destIsOrbit && thrustOk) {
                double speedDown = perTickSpeed(
                        (float) (thrustAvailable / Math.max(1.0f, mass) - Math.max(destGravity, 0.05f)));
                double destArrivalHeight;
                try {
                    destArrivalHeight = CSDimensionUtil.arrivalHeight(destRL);
                } catch (Throwable t) {
                    destArrivalHeight = 64.0;
                }
                descentTicks = Math.max(0.0, SPACE_ALTITUDE - destArrivalHeight)
                        / Math.max(0.05, speedDown);
            }
            double kinematicSeconds = (ascentTicks + descentTicks) / 20.0;
            // FUEL: only the ASCENT drains propellant (reentry is unpowered in CS).
            // The drain rate is NOT the theoretical TPT flow - consumePropellant reads
            // the REAL per-tag map (realPerTagFluidConsumption, built at assembly from
            // the engines actually present). Using the theoretical flow over-predicted
            // the burn (runtime: menu 19559 vs actual 15414 kg).
            double burnFlowKgS = realAscentFlowKgS(rocket);
            if (burnFlowKgS <= 0) burnFlowKgS = consumptionKgS;
            double burnSeconds = ascentTicks / 20.0;
            double kinematicFuelKg = burnFlowKgS * burnSeconds;
            double travelSeconds = kinematicSeconds;

            // ---- R15.3: m0 (full launch mass, exactly like CS) ----
            float inertMass = contraption.getDryMass() + (totalFluidKg - availableKg);
            float m0 = inertMass + availableKg;
            // R25b REGRESSION FIX ("FUEL REQ disappeared"): when the engine consumption
            // table cannot be resolved at all (empty TPT AND engine-NBT rebuild failed -
            // e.g. right after arrival on a freshly generated planet), consumptionKgS stays
            // 0 -> ve = 0 -> requiredFuelKg = 0 in BOTH branches -> the UI hid FUEL REQ
            // entirely. That violates the UI contract: the authoritative planner must always
            // produce a finite, >= 0 estimate. Use the SAME documented ve fallback already
            // used by USRocketControlBlockEntity.remainingDeltaV (30 000 N·s/kg) so the
            // Tsiolkovsky math yields a real, deterministic number, and flag it as
            // engineSource="fallback-ve" (also visible in the CALC FOR row and FUEL-DEBUG).
            boolean veFallbackUsed = false;
            if (consumptionKgS <= 0f && thrustAvailable > 0f) {
                consumptionKgS = thrustAvailable / 30_000f;
                veFallbackUsed = true;
                UnlimitedSpace.LOGGER.warn(
                        "[US][R25b] engine consumption unresolved for ({} -> {}); using ve "
                                + "fallback 30000 N·s/kg so FUEL REQ stays visible",
                        rocket.level().dimension().location(), destRL);
            }
            float ve = consumptionKgS > 0 ? thrustAvailable / consumptionKgS : 0f;
            // R25 ROOT-CAUSE FIX (16500 -> 9600 -> 41000 for the SAME route):
            // CSDimensionUtil.cost() reads a GLOBAL, MUTABLE, ROUTE-PRUNED cost graph.
            // ProceduralCsRuntime.ensureCostRoute() rebuilds that graph to contain ONLY the
            // (origin + destination + overworld hub) rows of the route it was last called
            // with, and prunes everything else. compute() previously read the cost WITHOUT
            // guaranteeing the (origin -> dest) row exists in the CURRENT pruned state:
            //   - route row present            -> full Tsiolkovsky            (CASE A, 16500)
            //   - row pruned, cost() == -1     -> effectiveCost = surcharges ONLY -> less fuel
            //                                     (CASE B, 9600)
            //   - row pruned, effectiveCost<=0 -> ascent-only fallback consumption*travelTime
            //                                     (CASE C, 41000)
            // i.e. the fuel input "routeCost" silently depended on WHICH ROUTE WAS ENSURED
            // LAST (assemble/disassemble actions recompute requirements via LAST_STATUS_DEST
            // without ensuring; Surface->Orbit pruned the Surface row). Fix: guarantee the
            // route for THIS exact (origin, dest) pair before reading the cost - the call is
            // synchronized + idempotent (early-out when the row already exists, a few ms).
            var routeServer = rocket.level().getServer();
            if (routeServer != null) {
                try {
                    com.modscreating.unlimitedspace.cs.ProceduralCsRuntime.ensureCostRoute(
                            routeServer, rocket.level().dimension().location(), destRL);
                } catch (Throwable t) {
                    UnlimitedSpace.LOGGER.warn("[US][R25] ensureCostRoute failed ({} -> {})",
                            rocket.level().dimension().location(), destRL, t);
                }
            }
            float routeCost;
            try {
                routeCost = CSDimensionUtil.cost(
                        rocket.level().dimension().location(), destRL);
            } catch (Throwable t) {
                routeCost = 0;
            }
            if (routeCost < 0) {
                // a pruned/absent row must NOT silently shrink effectiveCost by -1
                UnlimitedSpace.LOGGER.warn(
                        "[US][R25] cost lookup returned {} for ({} -> {}); treating as 0",
                        routeCost, rocket.level().dimension().location(), destRL);
                routeCost = 0;
            }
            // ---- R32: required fuel = kinematic ASCENT + CRUISE, MAXed with the CS gate ----
            // CRUISE = Tsiolkovsky on the ly-distance deltaV (see distanceSurcharge above):
            // this is what makes FUEL REQ / DIST FUEL grow with the trip distance.
            float requiredFuelKg;
            // R16: lift-off surcharge - kept as an INFORMATIONAL row (LIFT-OFF) only.
            int liftOff = liftOffSurcharge(rocket.level().dimension().location());
            float cruiseFuelKg = 0f;
            float distOnlyFuelKg = 0f;
            // R37: the cruise dV = distance-to-system dV + destination-arrival dV
            // (gravity well of a surface target). Splitting the burn sequentially
            // (distance first, then arrival on the lighter mass) keeps DIST FUEL
            // a pure "distance" number while FUEL REQUIRED varies per object.
            // R39 FIX ("one price for every surface / orbit"): the category constant
            // made ALL planet surfaces cost the same and ALL orbits cost the same.
            // The arrival dV is now read from the REAL cost graph: the difference
            // between the route to the SURFACE and the route to ITS ORBIT is exactly
            // the per-body descent edge (gravity/size/index dependent), so every
            // planet, moon and star has its own unique price. Category constants
            // remain only as the fallback when the graph lookup fails.
            int arrivalSurcharge = arrivalSurcharge(destRL);
            String destPath = destRL.getPath();
            if (isOrbitKey(destPath)) {
                // R39: orbits of DIFFERENT bodies must not share one price either. The
                // cost graph differentiates them by the per-body hash term baked into
                // the overworld hub edge (ProceduralMetadataGenerator: %13*60 for
                // orbits, %6*100 for asteroid fields), but the MAX() with the
                // kinematic burn hides it - so mirror that same deterministic per-body
                // term ON TOP, exactly like the surface arrival below.
                arrivalSurcharge = destPath.contains("/asteroid")
                        ? Math.abs(destPath.hashCode() % 6) * 100
                        : Math.abs(destPath.hashCode() % 13) * 60;
            } else if (destPath.endsWith("/surface")) {
                try {
                    String p = destRL.getPath();
                    var orbitRl = ResourceLocation.fromNamespaceAndPath(destRL.getNamespace(),
                            p.substring(0, p.length() - "/surface".length()) + "/orbit");
                    // the route-scoped graph contains the WHOLE destination system
                    // (mergeRoute adds every entry of the system), so the orbit row is
                    // readable without another rebuild
                    int orbitRouteCost = CSDimensionUtil.cost(
                            rocket.level().dimension().location(), orbitRl);
                    if (orbitRouteCost > 0 && routeCost > orbitRouteCost) {
                        arrivalSurcharge = (int) (routeCost - orbitRouteCost);
                    }
                } catch (Throwable t) {
                    UnlimitedSpace.LOGGER.warn(
                            "[US][R39] graph arrival lookup failed for {}; using category fallback",
                            destRL, t);
                }
            }
            // R36: the cruise burn starts AFTER the ascent burn, so the mass it
            // accelerates is the POST-ASCENT mass (m0 - kinematic), not the launch
            // mass. Pricing the cruise off the full launch mass over-counted and,
            // worse, made DIST FUEL swing with the CURRENT fuel load: the more
            // propellant was in the tanks, the bigger the "distance" charge looked
            // for the very same trip (runtime case: 1000 ly -> +9979 vs +3032 kg).
            float cruiseMassKg = (float) Math.max(0.0, m0 - kinematicFuelKg);
            distOnlyFuelKg = ve > 0 && distanceSurcharge > 0
                    ? (float) (cruiseMassKg * (1.0 - Math.exp(-distanceSurcharge / ve)))
                    : 0f;
            float midMass = Math.max(0f, cruiseMassKg - distOnlyFuelKg);
            float arrivalFuelKg = ve > 0 && arrivalSurcharge > 0
                    ? (float) (midMass * (1.0 - Math.exp(-arrivalSurcharge / ve)))
                    : 0f;
            cruiseFuelKg = distOnlyFuelKg + arrivalFuelKg;
            float gateFuelKg = 0f;
            if (ve > 0 && routeCost > 0) {
                gateFuelKg = (float) (m0 * (1.0 - Math.exp(-routeCost / ve)));
            }
            // R37b: MAX(kinematic + distance-cruise, gate) + arrival. The gate is priced
            // from the CS route cost, which is IDENTICAL for every object of the same
            // system; folding the arrival dV into the MAX let the gate swallow it
            // (gate >= kinematic + cruise almost always), so every surface / orbit of
            // one system showed the same FUEL REQUIRED. The arrival (gravity-well)
            // component must therefore be added ON TOP of the max - it always shows
            // and always differentiates the objects.
            requiredFuelKg = Math.max((float) kinematicFuelKg + distOnlyFuelKg, gateFuelKg)
                    + arrivalFuelKg;
            // R30c: +1% safety margin. The residual (~0.8% at runtime: predicted 24034 vs
            // actual 24228 kg) comes from CS drain quantization that cannot be modeled
            // exactly: per-tick mB truncation ((int) casts in consumePropellant), the
            // transition ticks at the 300-block boundary, and mid-flight tank-list
            // refreshes. Under-promising here is dangerous (a rocket fueled to exactly
            // the prediction would run dry), so round the requirement UP by 1%.
            requiredFuelKg *= 1.01f;
            if (routeCost <= 0 && distanceSurcharge <= 0 && !originIsOrbit) {
                UnlimitedSpace.LOGGER.warn(
                        "[US][R30] no route cost for ({} -> {}); kinematic-only estimate",
                        rocket.level().dimension().location(), destRL);
            }
            float shortage = Math.max(0f, requiredFuelKg - availableKg);

            // R32/R37: DIST FUEL = the DISTANCE component only (0 for same-system hops);
            // the arrival (gravity-well) component is folded into FUEL REQUIRED.
            float distanceFuelKg = distOnlyFuelKg;

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
                // R23 FIX: after several flights / reassemblies CS can leave
                // consumableFluids empty or stale (its fluid lists no longer match
                // what is actually in the tanks). Then every HAVE below collapsed to
                // 0 while FUEL HAVE (raw tank scan) still showed real mass - and the
                // launch was denied. Fall back to classifying the tank fluids
                // directly by propellant-tag membership.
                float haveTotal0 = 0f;
                for (float v : haveByTag.values()) haveTotal0 += v;
                if (haveByTag.isEmpty() || haveTotal0 <= 0f) {
                    haveByTag.clear();
                    counted.clear();
                    Set<TagKey<Fluid>> tags = new LinkedHashSet<>(rateByTag.keySet());
                    if (tags.isEmpty() && tpt != null) {
                        for (var entry : tpt.entrySet()) {
                            var info = entry.getValue();
                            if (info != null && info.propellantConsumption() != null)
                                tags.addAll(info.propellantConsumption().keySet());
                        }
                    }
                    for (var tm : tankMass.entrySet()) {
                        if (tm.getKey() == null || tm.getValue() <= 0) continue;
                        for (TagKey<Fluid> tag : tags) {
                            if (tm.getKey().is(tag)) {
                                haveByTag.merge(tag, tm.getValue(), Float::sum);
                            }
                        }
                    }
                    if (!haveByTag.isEmpty()) {
                        UnlimitedSpace.LOGGER.info(
                                "[US][R23] consumableFluids empty/stale - per-fluid HAVE rebuilt from tank tags ({} fluids)",
                                tankMass.size());
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
            // R30: a destination beyond the hard flight range can never be launched to,
            // regardless of the tanks - surface the reason instead of a fuel number.
            if (outOfRange) {
                fuelOk = false;
                shortageReason = String.format(java.util.Locale.ROOT,
                        "Destination system is %.0f ly away - beyond the %.0f ly flight range.",
                        tripDistanceLy, MAX_TRAVEL_LY);
            }

            // ---- R25 FUEL-DEBUG SNAPSHOT: every input of the calculation, one line ----
            // R28 GUARD: this block is DIAGNOSTICS and must never alter the result. It ran
            // AFTER a fully successful computation and an IllegalFormatConversionException
            // here ("%.0f" on int) sent every good calculation into the catch-all as zeros -
            // the actual, runtime-confirmed root cause of the missing FUEL REQ (req=5/6/13).
            String engineSource;
            try {
                String engineBase = veFallbackUsed ? "fallback-ve"
                        : (tpt == contraption.getTPTFluidConsumption()) ? "live" : "rebuilt";
                // R27: the trace id travels INSIDE the payload, so PACKET-IN and the GUI REQ STATE
                // row prove WHICH server calculation the displayed numbers came from.
                engineSource = traceId() + ":" + engineBase;
                UnlimitedSpace.LOGGER.info(
                        "[FUEL-TRACE][{}][COMPUTE] origin={} dest={} engineTable={} dryMass={} totalFluidKg={} "
                                + "availableKg={} inertMass={} m0={} thrust={} consumptionKgS={} ve={} "
                                + "routeCost={} liftOff={} distSurcharge={} arrivalDv={} cruiseDV={} "
                                + "travelSeconds={} requiredFuel={} availableFuel={}",
                        traceId(),
                        rocket.level().dimension().location(), destRL, engineBase,
                        String.format(java.util.Locale.ROOT, "%.1f", contraption.getDryMass()),
                        String.format(java.util.Locale.ROOT, "%.1f", totalFluidKg),
                        String.format(java.util.Locale.ROOT, "%.1f", availableKg),
                        String.format(java.util.Locale.ROOT, "%.1f", inertMass),
                        String.format(java.util.Locale.ROOT, "%.1f", m0),
                        String.format(java.util.Locale.ROOT, "%.0f", thrustAvailable),
                        String.format(java.util.Locale.ROOT, "%.2f", consumptionKgS),
                        String.format(java.util.Locale.ROOT, "%.1f", ve),
                        String.format(java.util.Locale.ROOT, "%.0f", routeCost),
                        // R28 FIX (runtime-confirmed, req=5/6/13): liftOff and distanceSurcharge
                        // are INTs - "%.0f" threw IllegalFormatConversionException.
                        String.format(java.util.Locale.ROOT, "%d", liftOff),
                        String.format(java.util.Locale.ROOT, "%d", distanceSurcharge),
                        String.format(java.util.Locale.ROOT, "%d", arrivalSurcharge),
                        String.format(java.util.Locale.ROOT, "%.0f", (double) distanceSurcharge),
                        String.format(java.util.Locale.ROOT, "%.1f", travelSeconds),
                        String.format(java.util.Locale.ROOT, "%.1f", requiredFuelKg),
                        String.format(java.util.Locale.ROOT, "%.1f", availableKg));
                assertSameInputsGiveSameFuel(rocket.level().dimension().location().toString(),
                        destRL.toString(), contraption.getDryMass(), totalFluidKg,
                        thrustAvailable, consumptionKgS, routeCost, liftOff, distanceSurcharge,
                        requiredFuelKg);
            } catch (Throwable logFailure) {
                UnlimitedSpace.LOGGER.warn(
                        "[FUEL-TRACE][{}][DIAG-LOG-FAILED] calculation result is UNAFFECTED",
                        traceId(), logFailure);
                engineSource = traceId() + ":diag-log-failed";
            }

            return new Requirements(requiredFuelKg, availableKg, shortage,
                    thrustRequired, thrustAvailable, fuelOk, thrustOk,
                    travelSeconds, consumptionKgS, perPropellant.toString(), liftOff,
                    distanceSurcharge, distanceFuelKg, fluidBalance.toString(), shortageReason,
                    routeCost, m0, ve, (float) kinematicFuelKg, destRL.toString(), engineSource);
        } catch (Throwable t) {
            // R27: a compute failure MUST be loud and visible - previously this returned
            // all-zero requirements at WARN level, which the UI rendered as "-" and was
            // indistinguishable from "requirements never sent".
            UnlimitedSpace.LOGGER.error(
                    "[FUEL-TRACE][{}][COMPUTE-FAILED] dest={} - returning zero requirements",
                    traceId(), destRL, t);
            return new Requirements(0, 0, 0, 0, 0, true, true, 0, 0, "", 0, 0, 0, "", "",
                    0, 0, 0, 0, destRL == null ? "" : destRL.toString(),
                    "error:" + t.getClass().getSimpleName());
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

    /**
     * Drain up to {@code kg} from the given fluids, proportional to each fluid's
     * current tank mass. Returns the kg actually removed.
     */
    private static float drainKgFromFluids(IFluidHandler tf, List<Fluid> fluids,
            Map<Fluid, Float> tankMass, float kg) {
        if (kg <= 0 || fluids == null || fluids.isEmpty()) return 0f;
        float pool = 0f;
        for (Fluid f : fluids) {
            Float m = tankMass.get(f);
            if (m != null && m > 0) pool += m;
        }
        if (pool <= 0) return 0f;
        float drainedKg = 0f;
        for (Fluid f : fluids) {
            Float m = tankMass.get(f);
            if (m == null || m <= 0) continue;
            float partKg = Math.min(m, kg * (m / pool));
            if (partKg <= 0) continue;
            int mB = (int) Math.ceil(partKg * 1000.0 / f.getFluidType().getDensity());
            if (mB <= 0) continue;
            FluidStack drained = tf.drain(new FluidStack(f, mB),
                    IFluidHandler.FluidAction.EXECUTE);
            if (drained != null && !drained.isEmpty()) {
                drainedKg += drained.getAmount()
                        * drained.getFluid().getFluidType().getDensity() / 1000.0f;
            }
        }
        return drainedKg;
    }

    /**
     * The rocket's REAL per-tag propellant consumption (kg per tick per tag), read
     * from {@code realPerTagFluidConsumption} via reflection - the same map CS's
     * {@code consumePropellant} uses. Empty when unavailable (assembly-time data).
     */
    private static Map<TagKey<Fluid>, Float> realPerTagConsumption(RocketContraptionEntity rocket) {
        Map<TagKey<Fluid>, Float> out = new LinkedHashMap<>();
        for (Class<?> cls : new Class<?>[]{RocketContraptionEntity.class, RocketContraption.class}) {
            try {
                var f = cls.getDeclaredField("realPerTagFluidConsumption");
                f.setAccessible(true);
                Object holder = (cls == RocketContraptionEntity.class)
                        ? rocket : rocket.getContraption();
                if (holder == null) continue;
                Object obj = f.get(holder);
                if (!(obj instanceof Map<?, ?> map)) continue;
                for (Object v : map.values()) {
                    if (v instanceof RocketContraption.ConsumptionInfo info
                            && info.propellantConsumption() != null) {
                        for (var e : info.propellantConsumption().entrySet()) {
                            if (e.getValue() > 0) out.merge(e.getKey(), e.getValue(), Float::sum);
                        }
                    }
                }
                if (!out.isEmpty()) break;
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    /** Mirror of CS {@code getPerTickSpeed(F)}: sign(a)·ln(1.4 + |a|/20), clamped [-1,1]. */
    private static double perTickSpeed(float accel) {
        float sign = Math.signum(accel);
        double raw = sign * Math.log(1.4 + Math.abs(accel) / 20.0);
        return Mth.clamp((float) raw, -1.0f, 1.0f);
    }

    /**
     * R36: the flight loop of Creating Space drains ONLY the ascent burn
     * ({@code consumePropellant} is skipped on reentry and there is no cruise
     * consumption in space), but the planner PRICES the trip as
     * {@code kinematic + cruise}. The un-priced gap made the launcher promise
     * 29482 kg while the flight actually drained 24336 kg. This method closes
     * the loop: right after a successful launch (same server tick, before the
     * first flight tick) it drains the cruise/gate component
     * ({@code requiredFuelKg - kinematicFuelKg}) from the rocket tanks, split
     * over the propellant fluids in the same ratio CS burns them (real per-tag
     * consumption rates, falling back to an equal mass split).
     *
     * @return the kg actually drained (0 when nothing was due or a failure occurred)
     */
    public static float applyCruiseSurcharge(RocketContraptionEntity rocket, ResourceLocation destRL) {
        try {
            Requirements req = compute(rocket, destRL);
            if (req == null || (req.distanceSurchargeDeltaV() <= 0 && req.routeCost() <= 0)) return 0f;
            float drainKg = req.requiredFuelKg() - req.kinematicFuelKg();
            if (drainKg <= 0.5f) return 0f;

            IFluidHandler tf = rocket.getContraption().getStorage().getFluids();
            // ---- tank inventory (fluid -> kg) ----
            Map<Fluid, Float> tankMass = new LinkedHashMap<>();
            for (int i = 0; i < tf.getTanks(); i++) {
                FluidStack fs = tf.getFluidInTank(i);
                if (fs == null || fs.isEmpty()) continue;
                tankMass.merge(fs.getFluid(),
                        fs.getAmount() * fs.getFluid().getFluidType().getDensity() / 1000.0f,
                        Float::sum);
            }
            if (tankMass.isEmpty()) return 0f;

            // ---- burn split: real per-tag consumption rates (kg per tick per tag) ----
            Map<TagKey<Fluid>, Float> rateByTag = realPerTagConsumption(rocket);
            Map<TagKey<Fluid>, List<Fluid>> fluidsByTag = new LinkedHashMap<>();
            if (rocket.consumableFluids != null) {
                for (var te : rocket.consumableFluids.entrySet()) {
                    if (te.getValue() != null) fluidsByTag.put(te.getKey(), te.getValue());
                }
            }
            float totalRate = 0f;
            for (float v : rateByTag.values()) totalRate += v;

            float drainedTotalKg = 0f;
            if (totalRate > 0 && !fluidsByTag.isEmpty()) {
                // split the surcharge across tags by burn-rate share
                for (var te : rateByTag.entrySet()) {
                    float share = te.getValue() / totalRate;
                    drainedTotalKg += drainKgFromFluids(tf,
                            fluidsByTag.getOrDefault(te.getKey(), List.of()), tankMass,
                            drainKg * share);
                }
            } else {
                // fallback: spread proportionally over ALL propellant-bearing tank fluids
                drainedTotalKg = drainKgFromFluids(tf, new ArrayList<>(tankMass.keySet()),
                        tankMass, drainKg);
            }
            UnlimitedSpace.LOGGER.info(
                    "[US][R36] cruise surcharge drained: dest={} due={} drained={} kg "
                            + "(requiredFuel={} kinematic={})",
                    destRL, String.format(java.util.Locale.ROOT, "%.1f", drainKg),
                    String.format(java.util.Locale.ROOT, "%.1f", drainedTotalKg),
                    String.format(java.util.Locale.ROOT, "%.1f", req.requiredFuelKg()),
                    String.format(java.util.Locale.ROOT, "%.1f", req.kinematicFuelKg()));
            return drainedTotalKg;
        } catch (Throwable t) {
            UnlimitedSpace.LOGGER.warn(
                    "[US][R36] cruise surcharge drain failed for {} - flight proceeds, "
                            + "only the surcharge drain is skipped", destRL, t);
            return 0f;
        }
    }

    /**
     * The ACTUAL propellant flow (kg/s) the CS flight loop drains during ascent:
     * summed from the rocket's {@code realPerTagFluidConsumption} map - the map
     * {@code consumePropellant} really reads (its values are kg per TICK, drained
     * every non-reentry tick). Falls back to 0 when the map is unavailable; the
     * caller then uses the theoretical flow.
     */
    private static double realAscentFlowKgS(RocketContraptionEntity rocket) {
        for (Class<?> cls : new Class<?>[]{RocketContraptionEntity.class, RocketContraption.class}) {
            try {
                var f = cls.getDeclaredField("realPerTagFluidConsumption");
                f.setAccessible(true);
                Object holder = (cls == RocketContraptionEntity.class)
                        ? rocket : rocket.getContraption();
                if (holder == null) continue;
                Object obj = f.get(holder);
                if (!(obj instanceof Map<?, ?> map)) continue;
                double perTickKg = 0;
                for (Object v : map.values()) {
                    if (v instanceof RocketContraption.ConsumptionInfo info
                            && info.propellantConsumption() != null) {
                        for (float val : info.propellantConsumption().values()) {
                            if (val > 0) perTickKg += val;
                        }
                    }
                }
                if (perTickKg > 0) return perTickKg * 20.0;
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }
}