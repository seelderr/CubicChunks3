package io.github.opencubicchunks.cubicchunks.mixin.core.client.chunk;

import java.util.Map;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.ColumnCubeGetter;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LevelCube;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// TODO: Maybe Resolve redirect conflict with fabric-lifecycle-events-v1.mixins.json:server .WorldChunkMixin->@Redirect::onRemoveBlockEntity(Fabric API). We implement their events
@Environment(EnvType.CLIENT)
@Mixin(value = LevelChunk.class, priority = 0) // Priority 0 to always ensure our redirects are on top. Should also prevent fabric api crashes that have occur(ed) here. See removeTileEntity
public abstract class MixinLevelChunk extends ChunkAccess {
    public MixinLevelChunk(ChunkPos chunkPos, UpgradeData upgradeData,
                           LevelHeightAccessor levelHeightAccessor,
                           Registry<Biome> registry, long l,
                           LevelChunkSection[] levelChunkSections,
                           BlendingData blendingData) {
        super(chunkPos, upgradeData, levelHeightAccessor, registry, l, levelChunkSections, blendingData);
        throw new RuntimeException("MixinLevelChunk constructor should never be called");
    }

    @Shadow public abstract Level getLevel();

    // TODO: don't target all Map.get()
    @SuppressWarnings({ "rawtypes", "UnresolvedMixinReference" })
    @Redirect(method = "*",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object getBlockEntity(Map map, Object key) {
        if (map == this.blockEntities) {
            if (!((CubicLevelHeightAccessor) this).isCubic()) {
                return map.get(key);
            }
            LevelCube cube = (LevelCube) ((ColumnCubeGetter) this).getCube(Coords.blockToSection(((BlockPos) key).getY()));
            return cube.getTileEntityMap().get(key);
        } else if (map == this.pendingBlockEntities) {
            if (!((CubicLevelHeightAccessor) this).isCubic()) {
                return map.get(key);
            }
            LevelCube cube = (LevelCube) ((ColumnCubeGetter) this).getCube(Coords.blockToSection(((BlockPos) key).getY()));
            return cube.getPendingBlockEntities().get(key);
        }
        return map.get(key);
    }

    @SuppressWarnings({ "rawtypes", "UnresolvedMixinReference" }) @Nullable
    @Redirect(
        method = "*",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object removeBlockEntity(Map map, Object key) {
        // to respect our priority over theirs.

        if (map == this.blockEntities) {
            // TODO: handle this better
            if (!((CubicLevelHeightAccessor) this).isCubic()) {
                @Nullable
                Object removed = map.remove(key);

                if (this.getLevel() instanceof ServerLevel) {
                    ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload((BlockEntity) removed, (ServerLevel) this.getLevel());
                } else if ((this.getLevel() instanceof ClientLevel)) {
                    ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload((BlockEntity) removed, (ClientLevel) this.getLevel());
                }
                return removed;
            }
            LevelCube cube = (LevelCube) ((ColumnCubeGetter) this).getCube(Coords.blockToSection(((BlockPos) key).getY()));

            @Nullable
            BlockEntity removed = cube.getTileEntityMap().remove(key);

            if (this.getLevel() instanceof ServerLevel) {
                ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload(removed, (ServerLevel) this.getLevel());
            } else if ((this.getLevel() instanceof ClientLevel)) {
                ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload(removed, (ClientLevel) this.getLevel());
            }
            return removed;
        } else if (map == this.pendingBlockEntities) {
            if (!((CubicLevelHeightAccessor) this).isCubic()) {
                return map.remove(key);
            }
            LevelCube cube = (LevelCube) ((ColumnCubeGetter) this).getCube(Coords.blockToSection(((BlockPos) key).getY()));
            return cube.getPendingBlockEntities().remove(key);
        }
        return map.remove(key);
    }

    @SuppressWarnings({ "rawtypes", "unchecked", "UnresolvedMixinReference" }) @Nullable
    @Redirect(method = "*",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object putBlockEntity(Map map, Object key, Object value) {
        if (map == this.blockEntities) {
            if (this.getLevel() instanceof ServerLevel) {
                ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.invoker().onLoad((BlockEntity) value, (ServerLevel) this.getLevel());
            } else if (this.getLevel() instanceof ClientLevel) {
                ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.invoker().onLoad((BlockEntity) value, (ClientLevel) this.getLevel());
            }

            if (!((CubicLevelHeightAccessor) this).isCubic()) {
                return map.put(key, value);
            }
            LevelCube cube = (LevelCube) ((ColumnCubeGetter) this).getCube(Coords.blockToSection(((BlockPos) key).getY()));
            return cube.getTileEntityMap().put((BlockPos) key, (BlockEntity) value);
        } else if (map == this.pendingBlockEntities) {
            if (!((CubicLevelHeightAccessor) this).isCubic()) {
                return map.put(key, value);
            }
            LevelCube cube = (LevelCube) ((ColumnCubeGetter) this).getCube(Coords.blockToSection(((BlockPos) key).getY()));
            return cube.getPendingBlockEntities().put((BlockPos) key, (CompoundTag) value);
        }
        return map.put(key, value);
    }
}
