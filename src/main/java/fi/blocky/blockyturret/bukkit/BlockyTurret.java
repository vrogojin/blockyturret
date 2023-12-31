package fi.blocky.blockyturret.bukkit;

import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.BlastFurnace;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Dispenser;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Furnace;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionData;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.Collection;
import java.util.Collections;
//import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
//import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

public class BlockyTurret extends JavaPlugin implements Listener {

    private static BlockyTurret instance;

    private NamespacedKey passphraseKey;

//    private Set<Location> turrets = new HashSet<>();
    private final ConcurrentMap<Location, Semaphore> turretSemaphores = new ConcurrentHashMap<>();
    private Map<Location, String> blockPassphrases = new ConcurrentHashMap<>();

    private RegionScheduler regionScheduler;

    @Override
    public void onEnable() {

//	Server server = getServer();

        getServer().getPluginManager().registerEvents(this, this);
	regionScheduler = getServer().getRegionScheduler();

	passphraseKey = new NamespacedKey(this, "BlockyTurret_turretPassphrase");

	createEmeraldBurnRecipe();
        getLogger().info("BlockyTurret plugin enabled");
    }

    public static BlockyTurret getInstance() {
        return instance;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
	Player player = event.getPlayer();
	if(!blockNeighborhoodFree(block, player)){
            event.setCancelled(true);
            return;
        }
	  if(isTurret(block)){
	    scheduleTurret(block);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
	Player player = event.getPlayer();
	if(!blockNeighborhoodFree(block, player)){
            event.setCancelled(true);
            return;
        }
	blockPassphrases.remove(block.getLocation());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (BlockState blockState : event.getChunk().getTileEntities()) {
    	      Block block = blockState.getBlock();
	      if(isTurret(block)){
		scheduleTurret(block);
	      }
	      if(blockState instanceof TileState) {
		TileState tileState = (TileState)blockState;
                PersistentDataContainer dataContainer = tileState.getPersistentDataContainer();
            	if (dataContainer.has(passphraseKey, PersistentDataType.STRING)) {
            	    String passphrase = dataContainer.get(passphraseKey, PersistentDataType.STRING);
            	    blockPassphrases.put(block.getLocation(), passphrase);
            	}
    	      }
	      if (blockState instanceof Furnace) { // Check if the tile entity is a furnace
                Furnace furnace = (Furnace) blockState;
                if (furnace.getInventory().getSmelting() != null &&
                    furnace.getInventory().getSmelting().getType() == Material.EMERALD) { // Check if the smelting item is an emerald
                    
                    // Reset the furnace burn time or cook time
//                    furnace.setBurnTime((short) 0);
                    furnace.setCookTime((short) 0);
                    furnace.update(); // Update the furnace to apply changes
                }
            }
	    if (blockState instanceof BlastFurnace) { // Check if the tile entity is a furnace
                BlastFurnace furnace = (BlastFurnace) blockState;
                if (furnace.getInventory().getSmelting() != null &&
                    furnace.getInventory().getSmelting().getType() == Material.EMERALD) { // Check if the smelting item is an emerald
                    
                    // Reset the furnace burn time or cook time
//                    furnace.setBurnTime((short) 0);
                    furnace.setCookTime((short) 0);
                    furnace.update(); // Update the furnace to apply changes
                }
            }


        }
    }

    @EventHandler
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        Entity remover = event.getRemover();
        Hanging hanging = event.getEntity();

        // Check if a player is trying to remove the entity
        if (remover instanceof Player) {
            // Preventing interaction with Item Frames and Paintings
            if (hanging instanceof ItemFrame || hanging instanceof Painting) {
		if(isNearSpawn(hanging.getLocation()))event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();

        // Preventing interaction with Armor Stands
        if (
		(entity instanceof ArmorStand)||
		(entity instanceof ItemFrame)
	    ) {
	    if(isNearSpawn(entity.getLocation()))event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
	Player player = event.getPlayer();
	if (player.getGameMode() == GameMode.CREATIVE && player.isOp())return;
	Block clickedBlock = event.getClickedBlock();

	if(clickedBlock == null)return;
	if(isNearSpawn(clickedBlock.getLocation()))event.setCancelled(true);
	BlockState blockState = clickedBlock.getState();
	if (clickedBlock != null && (blockState instanceof TileState)) {
	    ItemStack itemInHand = player.getInventory().getItemInMainHand();
    	    if (itemInHand.getType() == Material.PAPER) {
        	ItemMeta meta = itemInHand.getItemMeta();
        	if (meta != null && meta.hasDisplayName()) {
            	    String paperPassphrase = meta.getDisplayName();
		    
		    if(authorizeInvAccess(clickedBlock, player)){
			DoubleChest doubleChest = getDoubleChest(blockState);
			if(doubleChest != null){
				Block leftBlock = ((BlockState)doubleChest.getLeftSide()).getLocation().getBlock();
        			Block rightBlock = ((BlockState)doubleChest.getRightSide()).getLocation().getBlock();
				setBlockPasswd(leftBlock, paperPassphrase);
				setBlockPasswd(rightBlock, paperPassphrase);
				player.sendMessage(ChatColor.GREEN + "Double chest passphrase has been updated.");
			}else{
			    setBlockPasswd(clickedBlock, paperPassphrase);
                	    player.sendMessage(ChatColor.GREEN + "Block passphrase has been updated.");
			}
			event.setCancelled(true);
            	    } else {
                	player.sendMessage(ChatColor.RED + "You are not authorized to change the block passphrase.");
            	    }
        	}
    	    }
	}
	if(blockState instanceof Sign){
	    if(!authorizeInvAccess(clickedBlock, player)){
		event.setCancelled(true);
		player.sendMessage(ChatColor.RED + "You are not authorized to change text on the block");
	    }
	}
	if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Material blockType = clickedBlock.getType();
            
            // Check if the block is interactive
            if (isInteractiveBlock(blockType)) {
                // Apply your custom condition
		if(!blockNeighborhoodFree(clickedBlock, player)){
        	    event.setCancelled(true);
		    event.getPlayer().sendMessage(ChatColor.RED + "You cannot interact with this block right now!");
    		}
            }
        }
    }

    private void setBlockPasswd(Block block, String passwd){
        blockPassphrases.put(block.getLocation(), passwd);
	TileState tileState = (TileState)block.getState();
	PersistentDataContainer dataContainer = tileState.getPersistentDataContainer();
	dataContainer.set(passphraseKey, PersistentDataType.STRING, passwd);
	tileState.update();
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
	// Check if the holder of the inventory is a BlockState
	Player player = (Player) event.getPlayer();
	if (event.getInventory().getHolder() instanceof DoubleChest) {
	    DoubleChest doubleChest = (DoubleChest)event.getInventory().getHolder();
	    Block leftBlock = ((BlockState)doubleChest.getLeftSide()).getLocation().getBlock();
    	    Block rightBlock = ((BlockState)doubleChest.getRightSide()).getLocation().getBlock();
	    if(!authorizeInvAccess(leftBlock, player)&&!authorizeInvAccess(rightBlock, player)){
		event.setCancelled(true);
            	player.sendMessage(ChatColor.RED + "You are not authorized to access this double chest!");
		return;
	    }
	}else if(event.getInventory().getHolder() instanceof BlockState){
    	    // Get the block
	    BlockState blockState = (BlockState)event.getInventory().getHolder();
    	    Block block = ((BlockState) event.getInventory().getHolder()).getBlock();
	    if(!authorizeInvAccess(block, player)){
		event.setCancelled(true);
            	player.sendMessage(ChatColor.RED + "You are not authorized to access this inventory!");
		return;
	    }
	}
    }

    @EventHandler
    public void onBlockDispense(BlockDispenseEvent event) {
        if (event.getBlock().getType() == Material.DISPENSER) {
            Dispenser dispenser = (Dispenser) event.getBlock().getState();
            ItemStack[] contents = dispenser.getInventory().getContents();
	    ItemStack dispensedItem = event.getItem();
	    BlockFace facing = getDispenserFacing(dispenser.getBlock());
	    Block frontBlock = event.getBlock().getRelative(facing);
	    if (dispensedItem.getType() == Material.DIAMOND_SWORD) {
		dealDamage(dispenser);
		event.setCancelled(true); // Prevent the hoe from being dispensed
		return;
	    }else
	    if (dispensedItem.getType() == Material.DIAMOND_HOE) {
		canPlow(dispenser, frontBlock, dispensedItem);
		event.setCancelled(true); // Prevent the hoe from being dispensed
		return;
	    } else
	    if (dispensedItem.getType() == Material.DIAMOND_AXE) {
        	chopWood(event.getBlock());
        	event.setCancelled(true); // Prevent the axe from being dispensed
		return;
    	    } else if (dispensedItem.getType() == Material.DIAMOND_PICKAXE) {
        	breakBlock(event.getBlock());
        	event.setCancelled(true); // Prevent the pickaxe from being dispensed
		return;
    	    } else if (isTreeSapling(dispensedItem.getType())) {
                if (plantSapling(frontBlock, dispensedItem)) {
                    event.setCancelled(true);  // Prevent the sapling from being dispensed out

		    // Schedule the sapling consumption for the next tick
		    regionScheduler.runDelayed(
			this,
			dispenser.getBlock().getLocation(),
			(task) -> {
		    		consumeItemFromDispenser(dispenser, dispensedItem.getType());
			},
			1L
		    );
		    return;
                }
            } else if (plowAndPlantSeed(dispenser, frontBlock, dispensedItem)){
                    event.setCancelled(true);  // Prevent the sapling from being dispensed out

		    // Schedule the sapling consumption for the next tick
		    regionScheduler.runDelayed(
			this,
			dispenser.getBlock().getLocation(),
			(task) -> {
		    		consumeItemFromDispenser(dispenser, dispensedItem.getType());
			},
			1L
		    );
		    return;
	    }
            for (ItemStack item : contents) {
                if (item != null) {
		    if (item.getType() == Material.DIAMOND_SWORD) {
			dealDamage(dispenser);
			event.setCancelled(true); // Prevent the sword from being dispensed
			return;
		    } else
                    if (item.getType() == Material.DIAMOND_AXE) {
                        chopWood(dispenser.getBlock());
                        event.setCancelled(true); // Prevent the axe from being dispensed
                        return;
                    } else if (item.getType() == Material.DIAMOND_PICKAXE) {
                        breakBlock(dispenser.getBlock());
                        event.setCancelled(true); // Prevent the pickaxe from being dispensed
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Check if the spawned entity is a Monster (hostile mob)
        if ((event.getEntity() instanceof Monster)||(event.getEntityType() == EntityType.PHANTOM)) {
            // Check if it's within the protected radius
    	    Location loc = event.getLocation();
	
	    if(isNearSpawn(event.getLocation()))
		event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent event) {
	if(isNearSpawn(event.getBlock().getLocation()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
	if(isNearSpawn(event.getLocation()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
	if(isNearSpawn(entity.getLocation()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onPortalCreate(PortalCreateEvent event) {
        // Get the world spawn location

        // Check if any block in the portal is within 128 blocks of spawn
        for (BlockState blockState : event.getBlocks()) {
	    Location blockLocation = blockState.getLocation();
	    if(isNearSpawn(blockLocation)){
                // Cancel the event if it's within the restricted area
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isNearSpawn(Location loc){
	int spawn_safe_r = 127;
	World world = loc.getWorld();

	if(world.getEnvironment() != World.Environment.NORMAL)return false;

	Location spawn = world.getSpawnLocation();

	return ((Math.abs(spawn.getBlockX()-loc.getBlockX())<=spawn_safe_r)&&(Math.abs(spawn.getBlockZ()-loc.getBlockZ())<=spawn_safe_r));
    }
    
    private void chopWood(Block dispenserBlock) {
        BlockFace facing = getDispenserFacing(dispenserBlock);
        Block targetBlock = dispenserBlock.getRelative(facing);
        if (isWoodLog(targetBlock.getType())) {
            targetBlock.breakNaturally(new ItemStack(Material.DIAMOND_AXE));
        }
    }

    private void breakBlock(Block dispenserBlock) {
        BlockFace facing = getDispenserFacing(dispenserBlock);
        dispenserBlock.getRelative(facing).breakNaturally(new ItemStack(Material.DIAMOND_PICKAXE));
    }

    private BlockFace getDispenserFacing(Block dispenserBlock) {
	if (dispenserBlock.getBlockData() instanceof Directional) {
    	    Directional directional = (Directional) dispenserBlock.getBlockData();
    	    return directional.getFacing();
	}
	return null; // This shouldn't happen for a dispenser, but it's a good fallback just in case.
    }

    private void consumeItemFromDispenser(Dispenser dispenser, Material material) {
	for (int i = 0; i < dispenser.getInventory().getSize(); i++) {
    	    ItemStack item = dispenser.getInventory().getItem(i);
    	    if (item != null && item.getType() == material) {
        	if (item.getAmount() > 1) {
            	    item.setAmount(item.getAmount() - 1);
        	} else {
            	    dispenser.getInventory().clear(i);
        	}
        	break; // stop after consuming one item
    	    }
	}
    }

    private boolean isWoodLog(Material material) {
        switch (material) {
            case OAK_LOG:
            case SPRUCE_LOG:
            case BIRCH_LOG:
            case JUNGLE_LOG:
            case ACACIA_LOG:
            case DARK_OAK_LOG:
                return true;
            default:
                return false;
        }
    }

    private boolean plantSapling(Block block, ItemStack item) {
        Material below = block.getType();
	Block aboveBlock = block.getRelative(BlockFace.UP);
        Material above = aboveBlock.getType();
        boolean canPlant = 
	    (below == Material.GRASS_BLOCK || 
	    below == Material.DIRT ||
	    below == Material.COARSE_DIRT || 
	    below == Material.ROOTED_DIRT || 
	    below == Material.MUD || 
	    below == Material.MUDDY_MANGROVE_ROOTS || 
	    below == Material.FARMLAND 
	    ) && above == Material.AIR;
	if(canPlant)aboveBlock.setType(item.getType());
	return canPlant;
    }

    private boolean isTreeSapling(Material material) {
        switch (material) {
            case OAK_SAPLING:
            case SPRUCE_SAPLING:
            case BIRCH_SAPLING:
            case JUNGLE_SAPLING:
            case ACACIA_SAPLING:
            case DARK_OAK_SAPLING:
	    case CHERRY_SAPLING:
                return true;
            default:
                return false;
        }
    }

    private boolean isSeed(Material material) {
	switch (material) {
    	    case WHEAT_SEEDS:
    	    case BEETROOT_SEEDS:
    	    case MELON_SEEDS:
    	    case PUMPKIN_SEEDS:
	    case TORCHFLOWER_SEEDS:
        	return true;
    	    default:
        	return false;
	}
    }

    private boolean plowAndPlantSeed(Dispenser dispenser, Block block, ItemStack item) {
        Material below = block.getType();
	Block aboveBlock = block.getRelative(BlockFace.UP);
        Material above = aboveBlock.getType();
        boolean canPlow = canPlow(dispenser, block, item);
	boolean canPlant = (
	    below == Material.FARMLAND 
	    ) && above == Material.AIR && isSeed(item.getType());
	if(canPlant){
	    aboveBlock.setType(getPlantTypeFromSeed(item.getType()));
	}
	return canPlant;
    }

    private boolean canPlow(Dispenser dispenser, Block block, ItemStack item){
        Material below = block.getType();
	Block aboveBlock = block.getRelative(BlockFace.UP);
        Material above = aboveBlock.getType();
        boolean canPlow = 
	    (below == Material.GRASS_BLOCK || 
	    below == Material.DIRT ||
	    below == Material.COARSE_DIRT || 
	    below == Material.ROOTED_DIRT 
	    ) && above == Material.AIR && dispenserContainsHoe(dispenser, item);
	if(canPlow){
	    block.setType(Material.FARMLAND);
	}
	return canPlow;
    }

    private boolean dispenserContainsHoe(Dispenser dispenser, ItemStack dispensedItem) {
    	if (dispensedItem != null && (dispensedItem.getType() == Material.DIAMOND_HOE)) {
    	    return true;
    	}
	for (ItemStack item : dispenser.getInventory().getContents()) {
    	    if (item != null && (item.getType() == Material.DIAMOND_HOE)) {
        	return true;
    	    }
	}
	return false;
    }

    private Material getPlantTypeFromSeed(Material seedType) {
	switch (seedType) {
    	    case WHEAT_SEEDS:
        	return Material.WHEAT;
    	    case BEETROOT_SEEDS:
        	 return Material.BEETROOTS;
    	    case MELON_SEEDS:
        	return Material.MELON_STEM;
    	    case PUMPKIN_SEEDS:
        	return Material.PUMPKIN_STEM;
	    case TORCHFLOWER_SEEDS:
		return Material.TORCHFLOWER;
    	    default:
        	return null;
	}
    }

    private void dealDamage(Dispenser dispenser){
	BlockFace facing = getDispenserFacing(dispenser.getBlock());
        Collection<Entity> entities = getEntitiesInFrontOfDispenser(dispenser, facing);

        for (Entity entity : entities) {
            if (entity instanceof LivingEntity) {
                LivingEntity livingEntity = (LivingEntity) entity;
                livingEntity.damage(7); // Diamond sword deals 7 points (3.5 hearts) of damage
            }
        }
    }

    private Collection<Entity> getEntitiesInFrontOfDispenser(Dispenser dispenser, BlockFace facing) {
	Location loc = dispenser.getLocation().add(0.5, 0.5, 0.5).add(facing.getModX(), facing.getModY(), facing.getModZ());
	return loc.getWorld().getNearbyEntities(loc, 1.5, 1.5, 1.5);  // Adjust the radius if needed
    }

    private boolean authorizeInvAccess(Block block, Player player){
	String blockPassphrase = blockPassphrases.get(block.getLocation());
	return (blockPassphrase == null || playerHasPassphrase(player, blockPassphrase));
    }

    private DoubleChest getDoubleChest(BlockState blockState){
	if(blockState instanceof Container){
	    Container container = (Container)blockState;
	    if(container.getInventory().getHolder() instanceof DoubleChest)
		return ((DoubleChest)container.getInventory().getHolder());
	    else
		return null;
	}else
	    return null;
    }

    private boolean isInteractiveBlock(Material material) {
	switch (material) {
    	    // Doors
    	    case OAK_DOOR:
    	    case SPRUCE_DOOR:
    	    case BIRCH_DOOR:
    	    case JUNGLE_DOOR:
    	    case ACACIA_DOOR:
    	    case DARK_OAK_DOOR:
    	    case IRON_DOOR:
        
    	    // Trapdoors
    	    case OAK_TRAPDOOR:
    	    case SPRUCE_TRAPDOOR:
    	    case BIRCH_TRAPDOOR:
    	    case JUNGLE_TRAPDOOR:
    	    case ACACIA_TRAPDOOR:
    	    case DARK_OAK_TRAPDOOR:
    	    case IRON_TRAPDOOR:
        
    	    // Gates
    	    case OAK_FENCE_GATE:
    	    case SPRUCE_FENCE_GATE:
    	    case BIRCH_FENCE_GATE:
    	    case JUNGLE_FENCE_GATE:
    	    case ACACIA_FENCE_GATE:
    	    case DARK_OAK_FENCE_GATE:
        
    	    // Levers
    	    case LEVER:
        
    	    // Stone buttons
    	    case STONE_BUTTON:
    	    case POLISHED_BLACKSTONE_BUTTON:
        
    	    // Wooden buttons
    	    case OAK_BUTTON:
    	    case SPRUCE_BUTTON:
	    case BIRCH_BUTTON:
    	    case JUNGLE_BUTTON:
    	    case ACACIA_BUTTON:
    	    case DARK_OAK_BUTTON:
    	
	    // Redstone devices
	    case COMPARATOR:
	    case REPEATER:
	    case DAYLIGHT_DETECTOR:

	    // Bees
	    case BEEHIVE:
	    case BEE_NEST:
        
            return true;
        default:
            return false;
	}
    }

    private void createEmeraldBurnRecipe() {
        // Define the source item (emerald)
        ItemStack sourceItem = new ItemStack(Material.EMERALD);

        // Define the result (air)
        ItemStack resultItem = new ItemStack(Material.ENDER_PEARL);

        // Create the furnace recipe with long cooking time
        FurnaceRecipe burnEmerald = new FurnaceRecipe(new NamespacedKey(this, "generate_protection"), resultItem, sourceItem.getType(), 0, 72000);  // 72000 ticks = 1 hour
	burnEmerald.setCookingTime(72000*9);
	BlastingRecipe blastingRecipe = new BlastingRecipe(new NamespacedKey(this, "emerald_to_ender_pearl_blasting"), resultItem, sourceItem.getType(), 0.1f, 144000);  // 36000 ticks = 30 minutes
	blastingRecipe.setCookingTime(144000*9);

        // Add the recipe to the server
        getServer().addRecipe(burnEmerald);
	getServer().addRecipe(blastingRecipe);
    }

    private boolean blockNeighborhoodFree(Block block, Player player){
	if (player.getGameMode() == GameMode.CREATIVE && player.isOp())return true;
//	int spawn_safe_r = 127;
	int r = 15;
        Location loc = block.getLocation();
//	Location spawn = loc.getWorld().getSpawnLocation();
	
//	if((Math.abs(spawn.getBlockX()-loc.getBlockX())<=spawn_safe_r)&&(Math.abs(spawn.getBlockZ()-loc.getBlockZ())<=spawn_safe_r))
	if(isNearSpawn(loc))
	    return false;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
//                    if (x == 0 && y == 0 && z == 0) continue; // skip the original block
                    Block neighborBlock = loc.clone().add(x, y, z).getBlock();
		    if(!authorizeBlockOp(neighborBlock, player, Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z))))
			return false;
                }
            }
        }
        return true;
    }

    private boolean authorizeBlockOp(Block block, Player player, int dist){
	Material blockType = block.getType();
	if(((blockType == Material.FURNACE)&&(dist < 8))||(blockType == Material.BLAST_FURNACE)){
	    String blockPassphrase = blockPassphrases.get(block.getLocation());
	    if(blockPassphrase != null && (playerHasPassphrase(player, blockPassphrase)))
		return true;
	    int burnTime = (blockType == Material.FURNACE)?((Furnace)block.getState()).getBurnTime():
		((BlastFurnace)block.getState()).getBurnTime();
	    if(burnTime>0){
		ItemStack smeltingItem = (blockType == Material.FURNACE)?((Furnace)block.getState()).getInventory().getSmelting():
		    ((BlastFurnace)block.getState()).getInventory().getSmelting();
		Material smeltMaterial = smeltingItem.getType();
		if((smeltMaterial != null)&&(smeltMaterial == Material.EMERALD)){
		    return false;
		}
	    }
	}
	return true;
    }

    private ScheduledTask scheduleTurret(Block block){
	Location location = block.getLocation();
	try{
	    if(!acquireTurretTaskSemaphore(location))return null;
	    return regionScheduler.runDelayed(
		this,
		location,
		(task) -> {
		    releaseTurretTaskSemaphore(location);
		    runTurret(location);
		},
		20L
	    );
	}catch(Throwable e){
	    getLogger().severe("Could not aquire lock");
	    return null;
	}
    }

    private void runTurret(Location turretLoc) {
    	    World world = turretLoc.getWorld();
    	    Block block = turretLoc.getBlock();

	    if(!isTurret(block)){
        	// The turret has been destroyed, remove it from the set
		blockPassphrases.remove(turretLoc);
        	return;
    	    }

    	    Dispenser dispenser = (Dispenser) block.getState();
    	    ItemStack projectile = getProjectile(dispenser);

    	    if (projectile == null) {
        	// The dispenser is empty, skip this turret
		scheduleTurret(block);
        	return;
    	    }

    	    BlockFace facing = ((Directional) block.getBlockData()).getFacing();
    	    Vector turretFacingDirection = facing.getDirection();
    	    Location turretEyeLocation = turretLoc.clone().add(0.5, 0.5, 0.5).add(turretFacingDirection.clone().multiply(0.51));
	    String turretPassphrase = blockPassphrases.get(turretLoc);

    	    double range = 8.0;

    	    Vector targetDirection = null;
    	    double targetDistance = 0;
    	    for (Entity entity : block.getWorld().getNearbyEntities(turretLoc, range, range, range)) {
        	if (entity instanceof LivingEntity && entity.getType().isAlive() && !isNeutralMob(entity.getType())) {
		    if (turretPassphrase != null && entity instanceof Player && (playerHasPassphrase((Player) entity, turretPassphrase)||(((Player)entity).isDead()))) {
			((Player)entity).spawnParticle(Particle.VILLAGER_HAPPY,  
                                block.getLocation(),
                                10,  // Count of particles
                                0.5, 0.5, 0.5,  // Offset
                                0);  // Speed
		        continue;
		    }
            	    Location[] entityLocations = new Location[3];
            	    entityLocations[0] = entity.getLocation().clone().add(0, entity.getHeight() * 0.75, 0); // Aim at the upper part of the entity
            	    entityLocations[1] = entity.getLocation().clone().add(0, entity.getHeight() * 0.5, 0); // Aim at the center of the entity
            	    entityLocations[2] = entity.getLocation().clone().add(0, entity.getHeight() * 0.25, 0); // Aim at the lower part of the entity
            	    for(int i=0;i<3;i++){
                	Location entityLocation = entityLocations[i];
                	Vector directionToEntity = entityLocation.subtract(turretEyeLocation).toVector();
                	double distanceToEntity = directionToEntity.length();
                	directionToEntity.normalize();
                	if (directionToEntity.dot(turretFacingDirection) > 0.5) {
                    	    // Check if there is a direct line-of-sight between the turret and the entity.
                    	    RayTraceResult rayTraceResult = world.rayTraceBlocks(turretEyeLocation, directionToEntity, distanceToEntity, FluidCollisionMode.NEVER, true);
                    	    if (rayTraceResult == null) {
                        	// The turret has a direct line-of-sight to the entity. Trigger the dispenser to shoot.
                        	targetDirection = directionToEntity.clone();
                        	targetDistance = distanceToEntity;
                        	break;
                    	    }else{
                        	Location hitLocation = rayTraceResult.getHitPosition().toLocation(block.getWorld());
                        	block.getWorld().spawnParticle(Particle.FLAME, hitLocation, 1, 0, 0, 0, 0);
                        	if (rayTraceResult.getHitEntity() != null && rayTraceResult.getHitEntity().equals(entity)) {
                            	    // Shoot the item at the entity
                            	    targetDirection = directionToEntity.clone();
                            	    targetDistance = distanceToEntity;
                            	    break;
                        	}
                    	    }
                	}
            	    }
            	    if(targetDirection != null)break;
        	}
    	    }

    	    if (targetDirection != null) {
        	// Calculate the upward vector to compensate for the arrow drop-off
    		
        
    		// Shoot the projectile
    		EntityType entityType = getProjectileTypeFromItemStack(projectile);
    		if (entityType != null) {
        	    Entity projectileEntity = world.spawn(turretEyeLocation, entityType.getEntityClass());
		    if (projectileEntity instanceof Arrow) {
			double arrowDropCompensation = targetDistance * 0.015; // You can adjust this value to tweak the compensation
    			Vector upVector = new Vector(0, arrowDropCompensation, 0);
        		projectileEntity.setVelocity(targetDirection.add(upVector).normalize());
		    }else
			projectileEntity.setVelocity(targetDirection);
            
        	    if (projectileEntity instanceof Arrow) {
			Arrow arrow = (Arrow)projectileEntity;
            		arrow.setShooter(null); // Set the shooter to null if you don't want the arrow to be associated with a specific player
			arrow.setPickupStatus(Arrow.PickupStatus.ALLOWED); // Set the pickup status if you want to prevent players from picking up the arrow
			setArrowEffect(arrow, projectile);
        	    }
            
        	    // Consume one projectile from the dispenser
        	    if (projectile.getAmount() > 1) {
            		projectile.setAmount(projectile.getAmount() - 1);
        	    } else {
            		dispenser.getInventory().removeItem(projectile);
        	    }
            
        	    // Play dispenser sound
        	    world.playEffect(turretEyeLocation, Effect.CLICK2, 0);
    		}
	    }

	    scheduleTurret(block);
    }

    private ItemStack getProjectile(Dispenser dispenser) {
	for (int i = 0; i < dispenser.getInventory().getSize(); i++) {
    	    ItemStack itemStack = dispenser.getInventory().getItem(i);
    	    if (itemStack != null && itemStack.getAmount() > 0) {
        	EntityType projectileType = getProjectileTypeFromItemStack(itemStack);
        	if (projectileType != null) {
            	    return itemStack;
        	}
    	    }
	}
	return null; // Return -1 if no projectile slot is found
    }

    

    private EntityType getProjectileTypeFromItemStack(ItemStack itemStack) {
	Material material = itemStack.getType();
    
	if(material == Material.ARROW || material == Material.TIPPED_ARROW || material == Material.SPECTRAL_ARROW)
	    return EntityType.ARROW;
	if(material == Material.FIRE_CHARGE)
	    return EntityType.FIREBALL;
	return null;
    }

    private void setArrowEffect(Arrow arrow, ItemStack arrowItem){
	Material material = arrowItem.getType();

	if (material == Material.TIPPED_ARROW || material == Material.SPECTRAL_ARROW) {
            ItemMeta itemMeta = arrowItem.getItemMeta();
            if (itemMeta instanceof PotionMeta) {
                PotionMeta potionMeta = (PotionMeta) itemMeta;
                PotionData potionData = potionMeta.getBasePotionData();
                arrow.setBasePotionData(potionData);
                arrow.setColor(potionMeta.getColor());
            }
        }
    }

    private boolean playerHasPassphrase(Player player, String passphrase) {
	for (ItemStack item : player.getInventory().getContents()) {
    	    if (item != null && item.getType() == Material.PAPER) {
        	ItemMeta meta = item.getItemMeta();
        	if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals(passphrase)) {
            	    return true;
        	}
    	    }
	}
	return false;
    }

    public boolean isTurret(Block block) {
//	if(!block.getChunk().isLoaded())
//	    return false;
	if(!isBlockInLoadedChunk(block.getLocation()))
	    return false;
	if (block.getType() == Material.DISPENSER && block.getRelative(0, -1, 0).getType() == Material.OBSERVER) {
    	    return true;
        }
	return false;
    }

    public boolean isBlockInLoadedChunk(Location location) {
	World world = location.getWorld();
	int chunkX = location.getBlockX() >> 4;  // Dividing by 16; equivalent to blockX / 16
	int chunkZ = location.getBlockZ() >> 4;  // Dividing by 16; equivalent to blockZ / 16

	// The 'false' argument ensures the chunk isn't loaded if it's currently unloaded.
	return world.isChunkLoaded(chunkX, chunkZ);
    }

    private boolean isNeutralMob(EntityType entityType) {
	return entityType == EntityType.PIG ||
    	    entityType == EntityType.COW ||
    	    entityType == EntityType.SHEEP ||
    	    entityType == EntityType.CHICKEN ||
    	    entityType == EntityType.VILLAGER ||
	    entityType == EntityType.WOLF ||
    	    entityType == EntityType.OCELOT ||
    	    entityType == EntityType.HORSE ||
    	    entityType == EntityType.LLAMA ||
    	    entityType == EntityType.POLAR_BEAR ||
    	    entityType == EntityType.PANDA ||
    	    entityType == EntityType.FOX ||
    	    entityType == EntityType.DOLPHIN ||
    	    entityType == EntityType.MUSHROOM_COW ||
    	    entityType == EntityType.PARROT ||
    	    entityType == EntityType.TURTLE;
    }

    private Semaphore getTurretTaskSemaphore(Location location) {
        return turretSemaphores.computeIfAbsent(location, k -> new Semaphore(1));
    }

    private boolean acquireTurretTaskSemaphore(Location location) throws InterruptedException {
        Semaphore semaphore = getTurretTaskSemaphore(location);
        return semaphore.tryAcquire();
    }

    private void releaseTurretTaskSemaphore(Location location) {
        Semaphore semaphore = getTurretTaskSemaphore(location);
        semaphore.release();
    }


}
