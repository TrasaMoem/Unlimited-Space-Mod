package com.modscreating.unlimitedspace.block;

import com.modscreating.unlimitedspace.UnlimitedSpace;
import com.rae.creatingspace.content.rocket.RocketContraptionEntity;
import com.rae.creatingspace.content.rocket.contraption.RocketContraption;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;

/**
 * R15.1: BlockEntity of the Unlimited Space Rocket Control Block — a FUNCTIONAL
 * REPLACEMENT of Creating Space's RocketControlsBlockEntity.
 *
 * <p>The assembly sequence is a bytecode-faithful reproduction of CS 1.7.18
 * {@code RocketControlsBlockEntity.assemble()} using ONLY public CS/Create APIs, so the REAL
 * Creating Space contraption/glue/rocket infrastructure does the work:
 *
 * <pre>
 *   new RocketContraption()
 *   contraption.assemble(level, pos)            // Create SuperGlue-based structure search
 *   contraption.removeBlocksFromWorld(level, ZERO)
 *   destination = destination != null ? destination : level.dimension().location()
 *   RocketContraptionEntity.create(level, contraption, destination)
 *   entity.setPos(pos); entity.setInitialPosMap(initialPosMap); level.addFreshEntity(entity)
 *   AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, pos)
 * </pre>
 *
 * No fake rocket, no second glue system, no second contraption system.
 */
public class USRocketControlBlockEntity extends SmartBlockEntity {

    /** CS destination for the next launch (null = current dimension — the CS default). */
    private ResourceLocation destination;
    /** CS initial position map (destination -> arrival anchor), same semantics as CS. */
    public HashMap<ResourceLocation, BlockPos> initialPosMap = new HashMap<>();
    /** Last assembly failure reason, straight from Create's AssemblyException. */
    public boolean hasException = false;
    public String exceptionMessage = "";
    /** Entity id of the assembled RocketContraptionEntity, -1 when none. */
    private int rocketId = -1;
    private boolean assembleNextTick = false;

    public USRocketControlBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---- CS-compatible assembly lifecycle ----

    /** Same as CS: queue assembly for the next tick with an explicit destination. */
    public void queueAssembly(ResourceLocation destination) {
        this.destination = destination;
        this.assembleNextTick = true;
        setChanged();
    }

    /** Same as CS: queue assembly with the default (current dimension) destination. */
    public void queueAssembly() {
        queueAssembly(null);
    }

    /** Assemble NOW (server side). Bytecode-faithful reproduction of CS assemble(). */
    public void assembleNow() {
        Level level = this.level;
        if (level == null || level.isClientSide) return;
        assembleNextTick = false;
        RocketContraption contraption = new RocketContraption();
        hasException = false;
        exceptionMessage = "";
        try {
            if (!contraption.assemble(level, worldPosition)) {
                // Create found NO movable structure from this anchor: the BFS ended without
                // adding a single block. Explain the actual requirement instead of failing
                // silently (this is what made glued sections appear to "not join").
                hasException = true;
                exceptionMessage =
                        "No movable structure found. Blocks must touch the control block directly "
                                + "or be connected to it with Super Glue.";
                UnlimitedSpace.LOGGER.warn(
                        "[unlimitedspace][R15.1] rocket assembly returned no structure at {}", worldPosition);
                sendData();
                return;
            }
        } catch (com.simibubi.create.content.contraptions.AssemblyException e) {
            hasException = true;
            exceptionMessage = e.component == null ? String.valueOf(e.getMessage()) : e.component.getString();
            UnlimitedSpace.LOGGER.warn("[unlimitedspace][R15.1] rocket assembly failed at {}: {}",
                    worldPosition, exceptionMessage);
            sendData();
            return;
        }
        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
        if (destination == null) {
            destination = level.dimension().location();
        }
        RocketContraptionEntity entity =
                RocketContraptionEntity.create(level, contraption, destination);
        entity.setPos(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        entity.setInitialPosMap(new HashMap<>(initialPosMap));
        level.addFreshEntity(entity);
        rocketId = entity.getId();
        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);
        UnlimitedSpace.LOGGER.info(
                "[unlimitedspace][R15.1] rocket assembled at {}: blocks={} thrust={} dryMass={}",
                worldPosition, contraption.getBlocks().size(),
                contraption.getThrust(), contraption.getDryMass());
        sendData();
        setChanged();
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide) {
            if (assembleNextTick) {
                assembleNow();
            }
            // invalidate the cached rocket if it disappeared (disassembled / dimension change)
            if (rocketId != -1 && getRocket() == null) {
                rocketId = -1;
                setChanged();
            }
        }
    }

    /** The REAL assembled CS rocket, or null. */
    public RocketContraptionEntity getRocket() {
        if (level == null || rocketId == -1) return null;
        return level.getEntity(rocketId) instanceof RocketContraptionEntity r ? r : null;
    }

    public int rocketId() {
        return rocketId;
    }

    public ResourceLocation destination() {
        return destination;
    }

    public void setDestination(ResourceLocation destination) {
        this.destination = destination;
        setChanged();
    }

    public void setInitialPosMap(HashMap<ResourceLocation, BlockPos> map) {
        this.initialPosMap = map == null ? new HashMap<>() : map;
        setChanged();
    }

    // ---- NBT (same keys/semantics as CS where applicable) ----

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (destination != null) tag.putString("Destination", destination.toString());
        tag.putInt("RocketId", rocketId);
        tag.putBoolean("AssembleNextTick", assembleNextTick);
        tag.put("InitialPosMap",
                com.rae.creatingspace.content.rocket.rocket_control.RocketControlsBlockEntity
                        .putPosMap(initialPosMap));
        if (hasException) tag.putString("LastException", exceptionMessage);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        destination = tag.contains("Destination")
                ? ResourceLocation.tryParse(tag.getString("Destination")) : null;
        rocketId = tag.getInt("RocketId");
        assembleNextTick = tag.getBoolean("AssembleNextTick");
        if (tag.contains("InitialPosMap")) {
            initialPosMap = com.rae.creatingspace.content.rocket.rocket_control.RocketControlsBlockEntity
                    .getPosMap(tag.getCompound("InitialPosMap"));
        }
        hasException = tag.contains("LastException");
        exceptionMessage = hasException ? tag.getString("LastException") : "";
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // CS's RocketControlsBlockEntity adds no behaviours either.
    }

    /** Server-authoritative status snapshot for the control UI (only real CS values). */
    public Snapshot snapshot() {
        RocketContraptionEntity rocket = getRocket();
        if (rocket == null) {
            return new Snapshot(false, hasException ? "INVALID" : "NOT_ASSEMBLED",
                    "", "", "", "", exceptionMessage, -1, false, "");
        }
        String status;
        try {
            status = rocket.getEntityData()
                    .get(RocketContraptionEntity.STATUS_DATA_ACCESSOR).getSerializedName();
        } catch (Throwable t) {
            status = "IDLE";
        }
        String schedState = "";
        boolean hasSchedule = false;
        try {
            if (rocket.schedule != null && rocket.schedule.getSchedule() != null) {
                hasSchedule = true;
                schedState = String.valueOf(rocket.schedule.state);
            }
        } catch (Throwable ignored) {
        }
        return new Snapshot(true, status,
                String.format(java.util.Locale.ROOT, "%.0f", rocket.totalThrust),
                String.format(java.util.Locale.ROOT, "%.0f", rocket.initialMass),
                String.format(java.util.Locale.ROOT, "%.0f", rocket.deltaV()),
                String.valueOf(rocket.destination),
                "", rocket.getId(), hasSchedule, schedState);
    }

    /** Flattened, networkable rocket status (only values CS actually exposes). */
    public record Snapshot(boolean assembled, String status, String thrust, String dryMass,
                           String deltaV, String destination, String exception,
                           int rocketId, boolean hasSchedule, String scheduleState) {}
}
