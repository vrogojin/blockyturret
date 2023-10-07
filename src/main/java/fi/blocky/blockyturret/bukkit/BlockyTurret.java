package fi.blocky.blockyturret.bukkit;

import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.BlastFurnace;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Furnace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
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

import java.util.Collections;
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
/*        new BukkitRunnable() {
            @Override
            public void run() {
                checkTurrets();
            }
        }.runTaskTimer(this, 0, 20); // Run every second (20 ticks)*/
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
//        if (block.getType() == Material.DISPENSER && block.getRelative(0, -1, 0).getType() == Material.OBSERVER) {
	  if(isTurret(block)){
//            turrets.add(block.getLocation());
/*	    regionScheduler.runDelayed(
		this,
		block.getLocation(),
		(task) -> {
		    runTurret(block.getLocation());
		},
		20L
	    );*/
	    scheduleTurret(block);
            getLogger().info("BlockyTurret created");
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
//                turrets.add(block.getLocation());
		scheduleTurret(block);
		getLogger().info("BlockyTurret loaded");
	      }
	      if(blockState instanceof TileState) {
		TileState tileState = (TileState)blockState;
                PersistentDataContainer dataContainer = tileState.getPersistentDataContainer();
            	if (dataContainer.has(passphraseKey, PersistentDataType.STRING)) {
            	    String passphrase = dataContainer.get(passphraseKey, PersistentDataType.STRING);
            	    blockPassphrases.put(block.getLocation(), passphrase);
            	}
    	      }
        }
    }

/*    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Iterator<Location> iterator = turrets.iterator();
        while (iterator.hasNext()) {
            Location turretLoc = iterator.next();
            if (turretLoc.getChunk().equals(event.getChunk())) {
                iterator.remove();
		turretPassphrases.remove(turretLoc);
                getLogger().info("BlockyTurret unloaded");
            }
        }
    }*/

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
	Player player = event.getPlayer();
	ItemStack itemInHand = player.getInventory().getItemInMainHand();
	Block clickedBlock = event.getClickedBlock();

//	if (clickedBlock != null && isTurret(clickedBlock)) {
	if(clickedBlock == null)return;
	BlockState blockState = clickedBlock.getState();
	if (clickedBlock != null && (blockState instanceof TileState)) {
	    TileState tileState = (TileState)blockState;
    	    String blockPassphrase = blockPassphrases.get(clickedBlock.getLocation());

    	    if (itemInHand.getType() == Material.PAPER) {
        	ItemMeta meta = itemInHand.getItemMeta();
        	if (meta != null && meta.hasDisplayName()) {
            	    String paperPassphrase = meta.getDisplayName();
            	    if (blockPassphrase == null || playerHasPassphrase(player, blockPassphrase)) {
                	blockPassphrases.put(clickedBlock.getLocation(), paperPassphrase);
		        PersistentDataContainer dataContainer = tileState.getPersistentDataContainer();
		        dataContainer.set(passphraseKey, PersistentDataType.STRING, paperPassphrase);
		        blockState.update();
                	player.sendMessage(ChatColor.GREEN + "Block passphrase has been updated.");
            	    } else {
                	player.sendMessage(ChatColor.RED + "You are not authorized to change the block passphrase.");
            	    }
        	}
    	    }
	}
    }

    private void createEmeraldBurnRecipe() {
        // Define the source item (emerald)
        ItemStack sourceItem = new ItemStack(Material.EMERALD);

        // Define the result (air)
        ItemStack resultItem = new ItemStack(Material.ENDER_PEARL);

        // Create the furnace recipe with long cooking time
        FurnaceRecipe burnEmerald = new FurnaceRecipe(new NamespacedKey(this, "generate_protection"), resultItem, sourceItem.getType(), 0, 72000);  // 72000 ticks = 1 hour
	BlastingRecipe blastingRecipe = new BlastingRecipe(new NamespacedKey(this, "emerald_to_ender_pearl_blasting"), resultItem, sourceItem.getType(), 0.1f, 144000);  // 36000 ticks = 30 minutes

        // Add the recipe to the server
        getServer().addRecipe(burnEmerald);
	getServer().addRecipe(blastingRecipe);
    }

    private boolean blockNeighborhoodFree(Block block, Player player){
	int r = 15;
        Location loc = block.getLocation();
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
		if(smeltingItem.getType() == Material.EMERALD){
		    return false;
		}
	    }
	}
	return true;
    }

    private ScheduledTask scheduleTurret(Block block){
	Location location = block.getLocation();
//	getLogger().info("CHECKPOINT1");
	try{
	    if(!acquireTurretTaskSemaphore(location))return null;
//	    getLogger().info("CHECKPOINT2");
	    return regionScheduler.runDelayed(
		this,
		location,
		(task) -> {
//		    getLogger().info("CHECKPOINT3");
		    releaseTurretTaskSemaphore(location);
//		    getLogger().info("CHECKPOINT4");
		    runTurret(location);
//		    getLogger().info("CHECKPOINT5");
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

//    	    if (block.getType() != Material.DISPENSER) {
	    if(!isTurret(block)){
        	// The turret has been destroyed, remove it from the set
//        	iterator.remove();
		blockPassphrases.remove(turretLoc);
        	getLogger().info("BlockyTurret destroyed");
        	return;
    	    }

    	    Dispenser dispenser = (Dispenser) block.getState();
//    	    ItemStack projectile = dispenser.getInventory().getItem(0);
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
			getLogger().info("Entity is authorized or dead, skipping");
		        continue;
		    }
            	    getLogger().info("Target detected");
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
                    	    getLogger().info("Target acquired");
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
                        	getLogger().info("Target inaccessible");
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
        	    getLogger().info("Piu!!!");
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
