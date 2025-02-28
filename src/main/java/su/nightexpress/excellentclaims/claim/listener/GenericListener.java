package su.nightexpress.excellentclaims.claim.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentclaims.ClaimPlugin;
import su.nightexpress.excellentclaims.claim.ClaimManager;
import su.nightexpress.excellentclaims.config.Lang;
import su.nightexpress.excellentclaims.util.Relation;
import su.nightexpress.excellentclaims.util.RelationType;
import su.nightexpress.excellentclaims.util.pos.BlockPos;
import su.nightexpress.nightcore.language.message.LangMessage;
import su.nightexpress.nightcore.manager.AbstractListener;
import su.nightexpress.nightcore.util.bukkit.NightItem;

import java.util.Iterator;
import java.util.List;

public class GenericListener extends AbstractListener<ClaimPlugin> {

    private final ClaimManager manager;

    public GenericListener(@NotNull ClaimPlugin plugin, @NotNull ClaimManager manager) {
        super(plugin);
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (super.plugin.getSelectionManager().hasDisablePlacingProtections(event.getPlayer().getUniqueId()))
            return;
        BlockPos blockPos = BlockPos.from(event.getBlock());
        World world = event.getBlock().getWorld();
        String type = null;
        BlockPos maxBlockPos = null;
        BlockPos minBlockPos = null;
        if (event.getBlockPlaced().getType() == Material.IRON_BLOCK) {
            type = "irn";
            maxBlockPos = new BlockPos(
                    blockPos.getX() + 10,
                    world.getMaxHeight(),
                    blockPos.getZ() + 10
            );
            minBlockPos = new BlockPos(
                    blockPos.getX() - 10,
                    world.getMinHeight(),
                    blockPos.getZ() - 10
            );
        } else if (event.getBlockPlaced().getType() == Material.GOLD_BLOCK) {
            type = "gld";
            maxBlockPos = new BlockPos(
                    blockPos.getX() + 20,
                    world.getMaxHeight(),
                    blockPos.getZ() + 20
            );
            minBlockPos = new BlockPos(
                    blockPos.getX() - 20,
                    world.getMinHeight(),
                    blockPos.getZ() - 20
            );
        } else if (event.getBlockPlaced().getType() == Material.EMERALD_BLOCK) {
            type = "eme";
            maxBlockPos = new BlockPos(
                    blockPos.getX() + 30,
                    world.getMaxHeight(),
                    blockPos.getZ() + 30
            );
            minBlockPos = new BlockPos(
                    blockPos.getX() - 30,
                    world.getMinHeight(),
                    blockPos.getZ() - 30
            );
        } else if (event.getBlockPlaced().getType() == Material.DIAMOND_BLOCK) {
            type = "dmd";
            maxBlockPos = new BlockPos(
                    blockPos.getX() + 40,
                    world.getMaxHeight(),
                    blockPos.getZ() + 40
            );
            minBlockPos = new BlockPos(
                    blockPos.getX() - 40,
                    world.getMinHeight(),
                    blockPos.getZ() - 40
            );
        } else if (event.getBlockPlaced().getType() == Material.NETHERITE_BLOCK) {
            type = "nth";
            maxBlockPos = new BlockPos(
                    blockPos.getX() + 50,
                    world.getMaxHeight(),
                    blockPos.getZ() + 50
            );
            minBlockPos = new BlockPos(
                    blockPos.getX() - 50,
                    world.getMinHeight(),
                    blockPos.getZ() - 50
            );
        }
        if (type != null)
            event.setCancelled(
                    !this.manager.claimRegion(
                            event.getPlayer(),
                            world,
                            blockPos,
                            maxBlockPos,
                            minBlockPos,
                            type + "-" + world.getName() + "-" + blockPos.getX() + "-" + blockPos.getZ()
                    )
            );
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        BlockPos blockPos = BlockPos.from(event.getBlock());
        if (!this.manager.isProtectionBlock(event.getBlock().getWorld(), blockPos))
            return;
        event.setCancelled(
                !this.manager.removeRegionByBlock(
                        event.getPlayer(),
                        event.getBlock().getWorld(),
                        blockPos
                )
        );
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            BlockPos blockPos = BlockPos.from(block);
            return this.manager.isProtectionBlock(event.getBlock().getWorld(), blockPos);
        });
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            BlockPos blockPos = BlockPos.from(block);
            return this.manager.isProtectionBlock(event.getEntity().getWorld(), blockPos);
        });
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPistonPush(BlockPistonExtendEvent event) {
        this.handlePiston(event, event.getBlocks());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        this.handlePiston(event, event.getBlocks());
    }

    private void handlePiston(BlockPistonEvent event, List<Block> blocks) {
        event.setCancelled(
                blocks.stream().map(BlockPos::from)
                        .anyMatch(blockPos -> this.manager.isProtectionBlock(event.getBlock().getWorld(), blockPos))
        );
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldLoad(WorldLoadEvent event) {
        this.manager.handleWorldLoad(event.getWorld());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldUnload(WorldUnloadEvent event) {
        this.manager.handleWorldUnload(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (to.getX() == from.getX() && to.getZ() == from.getZ() && to.getY() == from.getY()) return;

        Player player = event.getPlayer();
        this.handleMovement(player, from, to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        Player player = event.getPlayer();
        this.handleMovement(player, from, to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();
        this.handleMovement(player, location, location);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location from = player.getLocation();
        Location to = event.getRespawnLocation();
        this.handleMovement(player, from, to);
    }

    private void handleMovement(@NotNull Player player, @NotNull Location from, @NotNull Location to) {
        Relation relation = this.manager.getRelation(from, to);
        if (relation.isEmpty()) return;

        RelationType type = relation.getType();
        LangMessage greetings;
        if (type == RelationType.TO_WILDERNESS) {
            Lang.GREETING_WILDERNESS.getMessage().send(player);
        } else if (type != RelationType.WILDERNESS && type != RelationType.INSIDE) {
            Lang.GREETING_CLAIM.getMessage().send(player, replacer -> replacer.replace(relation.getTargetClaim().replacePlaceholders()));
        }
    }
}
