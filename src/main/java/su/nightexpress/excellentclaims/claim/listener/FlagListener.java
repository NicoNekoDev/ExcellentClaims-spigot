package su.nightexpress.excellentclaims.claim.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SpawnEggMeta;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellentclaims.ClaimPlugin;
import su.nightexpress.excellentclaims.Placeholders;
import su.nightexpress.excellentclaims.api.claim.Claim;
import su.nightexpress.excellentclaims.api.claim.ClaimPermission;
import su.nightexpress.excellentclaims.claim.ClaimManager;
import su.nightexpress.excellentclaims.config.Lang;
import su.nightexpress.excellentclaims.flag.list.EntityFlags;
import su.nightexpress.excellentclaims.flag.impl.list.BooleanFlag;
import su.nightexpress.excellentclaims.flag.impl.list.DamageTypeListFlag;
import su.nightexpress.excellentclaims.flag.impl.list.ListModeFlag;
import su.nightexpress.excellentclaims.flag.list.NaturalFlags;
import su.nightexpress.excellentclaims.flag.list.PlayerFlags;
import su.nightexpress.excellentclaims.flag.type.DamageTypeList;
import su.nightexpress.excellentclaims.flag.type.EntityList;
import su.nightexpress.excellentclaims.flag.type.ListMode;
import su.nightexpress.excellentclaims.flag.type.MaterialList;
import su.nightexpress.excellentclaims.util.Relation;
import su.nightexpress.excellentclaims.util.RelationType;
import su.nightexpress.nightcore.language.LangAssets;
import su.nightexpress.nightcore.manager.AbstractListener;

import java.util.List;

public class FlagListener extends AbstractListener<ClaimPlugin> {

    private final ClaimManager manager;

    public FlagListener(@NotNull ClaimPlugin plugin, @NotNull ClaimManager manager) {
        super(plugin);
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!this.manager.canBreak(player, block)) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_BREAK.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(block.getType())).send(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (!this.manager.canBuild(player, block)) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_PLACE.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(block.getType())).send(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockFerilize(BlockFertilizeEvent event) {
        Player player = event.getPlayer();
        if (player != null && plugin.getMemberManager().isAdminMode(player)) return;

        //plugin.debug("BlockFertilize = " + event.getBlock().getType().name());

        Block block = event.getBlock();
        Location originLocation = block.getLocation();
        Claim origin = this.manager.getPrioritizedClaim(originLocation);

        if (origin != null && !origin.getFlag(PlayerFlags.BLOCK_FERTILIZE) && (player != null && !origin.isOwnerOrMember(player))) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_FERTILIZE.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(block.getType())).send(player);
            return;
        }

        event.getBlocks().removeIf(currentBlock -> {
            Relation relation = this.manager.getRelation(origin, currentBlock.getLocation());
            return relation.getType(player) == RelationType.INVADE || (player != null && !relation.isTargetMember(player) && !relation.checkTargetFlag(PlayerFlags.BLOCK_FERTILIZE));
        });

        if (event.getBlocks().isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTreeGrow(StructureGrowEvent event) {
        Player player = event.getPlayer();
        if (player != null && plugin.getMemberManager().isAdminMode(player)) return;

        Location sourceLocation = event.getLocation();
        TreeType type = event.getSpecies();
        boolean isMushroom = type == TreeType.RED_MUSHROOM || type == TreeType.BROWN_MUSHROOM;
        boolean isBoneMeal = event.isFromBonemeal();
        BooleanFlag flag = /*isBoneMeal ? Flags.BLOCK_FERTILIZE :*/ (isMushroom ? NaturalFlags.MUSHROOM_GROW : NaturalFlags.TREE_GROW);

        //plugin.debug("StructureGrow: " + player + " / isBoneMeal: " + isBoneMeal + " / " + flag.getId());

        Claim origin = this.manager.getPrioritizedClaim(sourceLocation);
        if (origin != null && !origin.getFlag(flag)) {
            event.setCancelled(true);
            return;
        }

        event.getBlocks().removeIf(state -> {
            Relation relation = this.manager.getRelation(origin, state.getLocation());
            return relation.getType(player) == RelationType.INVADE || !relation.checkTargetFlag(flag);
        });

        if (event.getBlocks().isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        BlockState newState = event.getNewState();
        BlockData blockData = newState.getBlockData();
        Material fromType = block.getType();
        Material toType = newState.getType();
        BooleanFlag flag;

        //plugin.debug("BlockGrow = " + fromType.name() + " -> " + toType.name());

        Location source = block.getLocation();
        Location target = newState.getLocation();

        if (fromType == Material.TURTLE_EGG) {
            flag = NaturalFlags.TURTLE_EGG_HATCH;
        }
        else if (fromType == Material.AIR && toType == Material.CACTUS) {
            flag = NaturalFlags.CACTUS_GROW;
            source = block.getRelative(BlockFace.DOWN).getLocation(); // Because it grows at new 'air' position.
        }
        else if (fromType == Material.AIR && toType == Material.SUGAR_CANE) {
            flag = NaturalFlags.SUGAR_CANE_GROW;
            source = block.getRelative(BlockFace.DOWN).getLocation(); // Because it grows at new 'air' position.
        }
        else if (fromType == Material.SMALL_AMETHYST_BUD || fromType == Material.MEDIUM_AMETHYST_BUD || fromType == Material.LARGE_AMETHYST_BUD || fromType == Material.AMETHYST_CLUSTER) {
            flag = NaturalFlags.AMETHYST_GROW;
        }
        else if (blockData instanceof Ageable) {
            flag = NaturalFlags.CROP_GROW;
        }
        else return;


        Relation relation = this.manager.getRelation(source, target);
        if (relation.getType() == RelationType.INVADE || !relation.checkBothFlag(flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        Block block = event.getBlock();
        BlockState newState = event.getNewState();
        Material oldMaterial = block.getType();
        Material material = newState.getType();
        BooleanFlag flag;

        if (material == Material.ICE) {
            flag = NaturalFlags.ICE_FORM;
        }
        else if (material == Material.SNOW) {
            flag = NaturalFlags.SNOW_FORM;
        }
        else if (material == Material.CAVE_VINES || material == Material.VINE) {
            flag = NaturalFlags.VINE_GROW;
        }
        else return;

        Location source = block.getLocation();
        Location target = newState.getLocation();
        Relation relation = this.manager.getRelation(source, target);
        if (relation.getType() == RelationType.INVADE || !relation.checkBothFlag(flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        Block sourceBlock = event.getSource();
        Block targetBlock = event.getBlock();
        BlockState targetState = event.getNewState();
        Material sourceType = sourceBlock.getType();
        Material targetType = targetState.getType();

        BooleanFlag flag;

        //plugin.debug("BlockSpread = " + sourceType.name() + " -> " + targetType.name());

        if (targetType == Material.GRASS_BLOCK) {
            flag = NaturalFlags.GRASS_GROW;
        }
        else if (targetType == Material.BAMBOO) {
            flag = NaturalFlags.BAMBOO_GROW;
        }
        else if (targetType == Material.MYCELIUM) {
            flag = NaturalFlags.MYCELIUM_SPREAD;
        }
        else if (targetType == Material.FIRE) {
            flag = NaturalFlags.FIRE_SPREAD;
        }
        else if (targetType == Material.CAVE_VINES || targetType == Material.VINE || targetType == Material.KELP) {
            flag = NaturalFlags.VINE_GROW;
        }
        else if (targetType == Material.SMALL_AMETHYST_BUD || targetType == Material.MEDIUM_AMETHYST_BUD || targetType == Material.LARGE_AMETHYST_BUD) {
            flag = NaturalFlags.AMETHYST_FORM;
        }
        else {

            return;
        }

        Location source = sourceBlock.getLocation();
        Location target = targetBlock.getLocation();

        Relation relation = this.manager.getRelation(source, target);
        if (relation.getType() == RelationType.INVADE || !relation.checkBothFlag(flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        Block block = event.getBlock();
        BlockState newState = event.getNewState();
        Material fromMaterial = block.getType();
        Material toMaterial = newState.getType();
        BooleanFlag flag;

        //plugin.debug("BlockFade = " + fromMaterial.name() + " -> " + toMaterial.name());

        if (fromMaterial == Material.ICE && toMaterial == Material.WATER) {
            flag = NaturalFlags.ICE_MELT;
        }
        else if (fromMaterial == Material.SNOW && toMaterial == Material.AIR) {
            flag = NaturalFlags.SNOW_MELT;
        }
        else if (fromMaterial == Material.FIRE && toMaterial == Material.AIR) {
            flag = NaturalFlags.FIRE_BURN_OUT;
        }
        else if (fromMaterial == Material.FARMLAND && toMaterial == Material.DIRT) {
            flag = NaturalFlags.FARMLAND_DRY;
        }
        else if (Tag.CORALS.isTagged(fromMaterial) || Tag.CORAL_BLOCKS.isTagged(fromMaterial)) {
            flag = NaturalFlags.CORAL_DIE;
        }
        else return;

        Location source = block.getLocation();
        Location target = newState.getLocation();
        Relation relation = this.manager.getRelation(source, target);
        if (relation.getType() == RelationType.INVADE || !relation.checkBothFlag(flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeafDecay(LeavesDecayEvent event) {
        Block block = event.getBlock();
        Claim claim = this.manager.getPrioritizedClaim(block);
        if (claim != null && !claim.getFlag(NaturalFlags.LEAF_DECAY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        Block from = event.getIgnitingBlock();

        Location source = from == null ? null : from.getLocation();
        Location target = block.getLocation();
        Relation relation = this.manager.getRelation(source, target);
        if (relation.getType() == RelationType.INVADE || !relation.checkBothFlag(NaturalFlags.FIRE_DAMAGE_BLOCKS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        List<Block> blockList = event.blockList();

        //plugin.debug("BlockExplode = " + event.getExplodedBlockState().getType().name());

        BooleanFlag flag = EntityFlags.EXPLOSION_BLOCK_DAMAGE;

        blockList.removeIf(block -> {
            Claim claim = this.manager.getPrioritizedClaim(block);
            return claim != null && !claim.getFlag(flag);
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        Block block = event.getBlock();
        Location source = block.getLocation();

        if (!this.checkPistonBlocks(event.getBlocks(), source, event.getDirection(), true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        Block block = event.getBlock();
        Location source = block.getLocation();

        if (!this.checkPistonBlocks(event.getBlocks(), source, event.getDirection(), false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        // TODO Config option high frequency flags
        Block sourceBlock = event.getBlock();
        Block targetBlock = event.getToBlock();
        Material fromType = sourceBlock.getType();

        BooleanFlag flag;
        if (fromType == Material.WATER) {
            flag = NaturalFlags.WATER_FLOW;
        }
        else if (fromType == Material.LAVA) {
            flag = NaturalFlags.LAVA_FLOW;
        }
        else return;

        Relation relation = this.manager.getRelation(sourceBlock.getLocation(), targetBlock.getLocation());
        if (relation.getType() == RelationType.INVADE && !relation.checkTargetFlag(flag)) {
            event.setCancelled(true);
        }
    }

    private boolean checkPistonBlocks(@NotNull List<Block> blocks, @NotNull Location source, @NotNull BlockFace direction, boolean isExtend) {
        if (blocks.isEmpty()) {
            Location target = (isExtend ? source.getBlock().getRelative(direction) : source.getBlock()).getLocation();
            Relation relation = this.manager.getRelation(source, target);
            return relation.getType() != RelationType.INVADE && relation.checkBothFlag(NaturalFlags.PISTON_USE);
        }

        return blocks.stream().allMatch(block -> {
            Location target = (isExtend ? block.getRelative(direction) : block).getLocation();
            Relation relation = this.manager.getRelation(source, target);
            return relation.getType() != RelationType.INVADE && relation.checkBothFlag(NaturalFlags.PISTON_USE);
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;

        //plugin.debug("PlayerInteractBlock = " + player.getName() + " -> " + block + " (" + event.getAction().name() + ")");

        if (event.useInteractedBlock() == Event.Result.DENY) return;

        if (!this.manager.canUseBlock(player, block, event.getAction())) {
            event.setUseInteractedBlock(Event.Result.DENY);
            Lang.PROTECTION_BLOCK_INTERACT.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(block.getType())).send(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerItemUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack itemStack = event.getItem();
        if (itemStack == null || itemStack.getType().isAir()) return;
        if (event.useItemInHand() == Event.Result.DENY) return;

        //plugin.debug("PlayerInteractItem = " + player.getName() + " -> " + itemStack);

        if (plugin.getMemberManager().isAdminMode(player)) return;

        Claim claim = this.manager.getPrioritizedClaim(player.getLocation());
        if (claim == null || claim.isOwnerOrMember(player)) return;

        ItemMeta meta = itemStack.getItemMeta();
        Material itemType = itemStack.getType();
        BooleanFlag explicitFlag = null;

        if (meta instanceof SpawnEggMeta) {
            explicitFlag = PlayerFlags.SPAWN_EGG_USE;
        }
        else if (itemType == Material.ENDER_PEARL) {
            explicitFlag = PlayerFlags.ENDER_PEARL_USE;
        }
        else if (itemType == Material.CHORUS_FRUIT) {
            explicitFlag = PlayerFlags.CHORUS_FRUIT_USE;
        }

        if (explicitFlag != null) {
            if (!claim.getFlag(explicitFlag)) {
                event.setUseItemInHand(Event.Result.DENY);
            }
        }
        else {
            ListMode mode = claim.getFlag(PlayerFlags.ITEM_USE_MODE);
            MaterialList materialList = claim.getFlag(PlayerFlags.ITEM_USE_LIST);
            if (!materialList.isAllowed(mode, itemType)) {
                event.setUseItemInHand(Event.Result.DENY);
            }
        }

        if (event.useItemInHand() == Event.Result.DENY) {
            Lang.PROTECTION_ITEM_USE.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(itemType)).send(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!this.manager.canBreak(player, block)) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_BREAK.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(block.getType())).send(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (!this.manager.canBuild(player, event.getBlock())) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_PLACE.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(event.getBucket())).send(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getEntity();
        if (!this.manager.canBreak(player, entity.getLocation())) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_BREAK.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(entity.getType())).send(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerEntityInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        EntityType entityType = entity.getType();
        Player player = event.getPlayer();

        //plugin.debug("PlayerEntityInteract = " + player.getName() + " -> " + entityType.name());

        if (plugin.getMemberManager().isAdminMode(player)) return;

        Claim claim = this.manager.getPrioritizedClaim(entity.getLocation());
        if (claim == null) return;

        BooleanFlag explicitFlag = null;

        if (entityType == EntityType.VILLAGER) {
            explicitFlag = PlayerFlags.VILLAGER_INTERACT;
        }
        else if (entity instanceof Vehicle && !(entity instanceof LivingEntity)) {
            if (entity instanceof InventoryHolder) {
                if (entityType == EntityType.CHEST_MINECART || entityType == EntityType.CHEST_BOAT) {
                    explicitFlag = PlayerFlags.CHEST_ACCESS;
                }
                else explicitFlag = PlayerFlags.CONTAINER_ACCESS;
            }
            else explicitFlag = PlayerFlags.VEHICLE_USE;
        }

        if (explicitFlag != null) {
            if (claim.getFlag(explicitFlag)) return;
        }
        else {
            ListMode mode = claim.getFlag(PlayerFlags.ENTITY_INTERACT_MODE);
            EntityList list = claim.getFlag(PlayerFlags.ENTITY_INTERACT_LIST);
            if (list.isAllowed(mode, entityType)) return;
        }

        if (!claim.isOwnerOrMember(player) || !claim.hasPermission(player, ClaimPermission.ENTITY_INTERACT)) {
            event.setCancelled(true);
            Lang.PROTECTION_ENTITY_INTERACT.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(entityType)).send(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerArmorStandUse(PlayerArmorStandManipulateEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();

        if (plugin.getMemberManager().isAdminMode(player)) return;

        Claim claim = this.manager.getPrioritizedClaim(entity.getLocation());
        if (claim == null || claim.getFlag(PlayerFlags.ARMOR_STAND_USE)) return;

        if (!claim.isOwnerOrMember(player) || !claim.hasPermission(player, ClaimPermission.ENTITY_INTERACT)) {
            event.setCancelled(true);
            Lang.PROTECTION_ENTITY_INTERACT.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(entity.getType())).send(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerPortalUse(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (plugin.getMemberManager().isAdminMode(player)) return;

        Claim claim = this.manager.getPrioritizedClaim(player.getLocation());
        if (claim == null || claim.isOwnerOrMember(player)) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        BooleanFlag flag = switch (cause) {
            case NETHER_PORTAL -> PlayerFlags.NETHER_PORTAL_USE;
            case END_PORTAL -> PlayerFlags.END_PORTAL_USE;
            default -> null;
        };
        if (flag == null) return;

        if (!claim.getFlag(flag)) {
            event.setCancelled(true);
            Lang.PROTECTION_PORTAL_USE.getMessage().send(player);
        }
    }


    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player player) return;

        Claim claim = this.manager.getPrioritizedClaim(entity.getLocation());
        if (claim == null) return;

        BooleanFlag explicitFlag = null;
        if (entity instanceof Animals) {
            explicitFlag = EntityFlags.ANIMAL_SPAWN;
        }
        else if (entity instanceof Monster) {
            explicitFlag = EntityFlags.MONSTER_SPAWN;
        }

        if (explicitFlag != null) {
            if (!claim.getFlag(explicitFlag)) {
                event.setCancelled(true);
            }
            return;
        }

        ListMode mode = claim.getFlag(EntityFlags.ENTITY_SPAWN_MODE);
        EntityList entityList = claim.getFlag(EntityFlags.ENTITY_SPAWN_LIST);
        if (!entityList.isAllowed(mode, entity.getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (player != null && !this.manager.canBuild(player, block)) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_PLACE.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(event.getEntity().getType())).send(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        EntityType entityType = entity.getType();
        List<Block> blockList = event.blockList();

        //plugin.debug("EntityExplode = " + entity);

        BooleanFlag flag;

        if (entityType == EntityType.TNT || entityType == EntityType.TNT_MINECART) {
            flag = NaturalFlags.TNT_BLOCK_DAMAGE;
        }
        else if (entityType == EntityType.CREEPER) {
            flag = EntityFlags.CREEPER_BLOCK_DAMAGE;
        }
        else if (entityType == EntityType.WITHER_SKULL || entityType == EntityType.WITHER) {
            flag = EntityFlags.WITHER_BLOCK_DAMAGE;
        }
        else if (entityType == EntityType.FIREBALL || entityType == EntityType.SMALL_FIREBALL || entityType == EntityType.DRAGON_FIREBALL) {
            flag = EntityFlags.FIREBALL_BLOCK_DAMAGE;
        }
        else if (entityType == EntityType.END_CRYSTAL) {
            flag = EntityFlags.END_CRYSTAL_BLOCK_DAMAGE;
        }
        else {
            flag = EntityFlags.EXPLOSION_BLOCK_DAMAGE;
        }

        blockList.removeIf(block -> {
            Claim claim = this.manager.getPrioritizedClaim(block);
            return claim != null && !claim.getFlag(flag);
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        Entity entity = event.getEntity();
        Block block = event.getBlock();

        //plugin.debug("EntityInteract = " + entity + " -> " + block);

        if (!this.manager.canUseBlock(entity, block, null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityBlockChange(EntityChangeBlockEvent event) {
        Block sourceBlock = event.getBlock();
        Claim claim = this.manager.getPrioritizedClaim(sourceBlock);
        if (claim == null) return;

        Entity entity = event.getEntity();
        Player player = entity instanceof Player p ? p : null;
        if (player != null && (plugin.getMemberManager().isAdminMode(player) || claim.isOwnerOrMember(player))) return;

        //BlockData toData = event.getBlockData();
        Material fromType = sourceBlock.getType();
        Material toType = event.getTo();
        EntityType entityType = entity.getType();

        //plugin.debug("EntityBlockChange = " + entity.getType().name() + " changed " + fromType.name() + " -> " + toType.name());

        BooleanFlag flag;
        if (entityType == EntityType.SILVERFISH) {
            flag = EntityFlags.SILVERFISH_INFEST;
        }
        else if (entityType == EntityType.PLAYER && fromType == Material.FARMLAND) {
            flag = PlayerFlags.BLOCK_TRAMPLING;
        }
        else if (entityType == EntityType.VILLAGER) {
            flag = EntityFlags.VILLAGER_FARM;
        }
        else if (entity instanceof Animals) {
            flag = EntityFlags.ANIMAL_GRIEF;
        }
        else if (entityType == EntityType.ENDERMAN) {
            flag = EntityFlags.ENDERMAN_GRIEF;
        }
        else if (entityType == EntityType.RAVAGER) {
            flag = EntityFlags.RAVAGER_GRIEF;
        }
        else if (entityType == EntityType.ENDER_DRAGON) {
            flag = EntityFlags.ENDER_DRAGON_GRIEF;
        }
        else return;

        if (!claim.getFlag(flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        Entity entity = event.getEntity();
        Block sourceBlock = event.getBlock();
        BlockState toState = event.getNewState();
        Material fromType = sourceBlock.getType();
        Material toType = toState.getType();
        EntityType entityType = entity.getType();

        //plugin.debug("EntityBlockForm = " + entity.getType().name() + " formed " + sourceBlock.getType().name() + " -> " + toType.name());

        BooleanFlag flag;
        if (entityType == EntityType.SNOW_GOLEM && toType == Material.SNOW) {
            flag = EntityFlags.SNOWMAN_TRAIL;
        }
        else return;

        Relation relation = this.manager.getRelation(sourceBlock.getLocation(), toState.getLocation());
        if (relation.getType() == RelationType.INVADE || !relation.checkBothFlag(flag)) {
            event.setCancelled(true);
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        DamageSource source = event.getDamageSource();
        Entity entity = event.getEntity();
        Entity damager = source.getCausingEntity();
        EntityType entityType = entity.getType();

        Player playerDamager = damager instanceof Player p ? p : null;
        if (playerDamager != null) {
            if (entityType == EntityType.ARMOR_STAND || entityType == EntityType.END_CRYSTAL) {
                if (!this.manager.canBreak(playerDamager, entity.getLocation())) {
                    event.setCancelled(true);
                    Lang.PROTECTION_BLOCK_BREAK.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(entityType)).send(playerDamager);
                    return;
                }
            }
        }

        Claim claim = this.manager.getPrioritizedClaim(entity.getLocation());
        if (claim == null) return;

        //plugin.debug("EntityDamage Type = " + BukkitThing.toString(source.getDamageType()));

        ListModeFlag modeFlag;
        DamageTypeListFlag listFlag;

        if (entity instanceof Player player) {
            if (playerDamager != null && !claim.getFlag(PlayerFlags.PLAYER_DAMAGE_PLAYERS)) {
                Lang.PROTECTION_DAMAGE_ENTITY.getMessage().replace(Placeholders.GENERIC_VALUE, player.getName()).send(playerDamager);
                event.setCancelled(true);
                return;
            }
            if (damager instanceof Monster && !claim.getFlag(EntityFlags.MONSTER_DAMAGE_PLAYERS)) {
                event.setCancelled(true);
                return;
            }
            modeFlag = PlayerFlags.PLAYER_DAMAGE_MODE;
            listFlag = PlayerFlags.PLAYER_DAMAGE_LIST;
        }
        else if (entityType == EntityType.VILLAGER && playerDamager != null) {
            if (claim.isOwnerOrMember(playerDamager)) return; // Members should be able to damage villagers.
            if (!claim.getFlag(PlayerFlags.PLAYER_DAMAGE_VILLAGERS)) {
                Lang.PROTECTION_DAMAGE_ENTITY.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(entityType)).send(playerDamager);
                event.setCancelled(true);
            }
            return;
        }
        else if (entity instanceof Animals) {
            if (playerDamager != null) {
                if (claim.isOwnerOrMember(playerDamager)) return; // Members should be able to damage animals.
                if (!claim.getFlag(PlayerFlags.PLAYER_DAMAGE_ANIMALS)) {
                    Lang.PROTECTION_DAMAGE_ENTITY.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(entityType)).send(playerDamager);
                    event.setCancelled(true);
                    return;
                }
            }
            modeFlag = EntityFlags.ANIMAL_DAMAGE_MODE;
            listFlag = EntityFlags.ANIMAL_DAMAGE_LIST;
        }
        else return;

        ListMode mode = claim.getFlag(modeFlag);
        DamageTypeList list = claim.getFlag(listFlag);

        if (!list.isAllowed(mode, source.getDamageType())) {
            event.setCancelled(true);
            if (playerDamager != null) {
                Lang.PROTECTION_DAMAGE_ENTITY.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(entityType)).send(playerDamager);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Entity remover = event.getRemover();
        Hanging hanging = event.getEntity();
        Location location = hanging.getLocation();

        if (!(remover instanceof Player player) || (!this.manager.canBreak(player, location))) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_BREAK.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(hanging.getType())).send(remover);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (player != null && !this.manager.canBuild(player, block)) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_PLACE.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(event.getEntity().getType())).send(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Entity remover = event.getAttacker();
        Vehicle vehicle = event.getVehicle();
        Location location = vehicle.getLocation();

        if (!(remover instanceof Player player) || (!this.manager.canBreak(player, location))) {
            event.setCancelled(true);
            Lang.PROTECTION_BLOCK_BREAK.getMessage().replace(Placeholders.GENERIC_VALUE, LangAssets.get(vehicle.getType())).send(remover);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.getMemberManager().isAdminMode(player)) return;

        Claim claim = this.manager.getPrioritizedClaim(player.getLocation());
        if (claim == null || claim.isOwnerOrMember(player)) return;

        if (!claim.getFlag(PlayerFlags.PLAYER_ITEM_DROP)) {
            event.setCancelled(true);
            Lang.PROTECTION_ITEM_DROP.getMessage().send(player);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getMemberManager().isAdminMode(player)) return;

        Claim claim = this.manager.getPrioritizedClaim(player.getLocation());
        if (claim == null || claim.isOwnerOrMember(player)) return;

        if (!claim.getFlag(PlayerFlags.PLAYER_ITEM_PICKUP)) {
            event.setCancelled(true);
            Lang.PROTECTION_ITEM_PICKUP.getMessage().send(player);
        }
    }
}
