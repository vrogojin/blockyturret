package fi.blocky.blockyturret.bukkit;

import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

public class BlockyTurret extends JavaPlugin implements Listener {

    private static BlockyTurret instance;

    private NamespacedKey passphraseKey;

//    private Set<Location> turrets = new HashSet<>();
    private Map<Location, String> turretPassphrases = new HashMap<>();

    private RegionScheduler regionScheduler;

    @Override
    public void onEnable() {

//	Server server = getServer();

        getServer().getPluginManager().registerEvents(this, this);
	regionScheduler = getServer().getRegionScheduler();

	passphraseKey = new NamespacedKey(this, "BlockyTurret_turretPassphrase");

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
    public void onChunkLoad(ChunkLoadEvent event) {
        for (BlockState blockState : event.getChunk().getTileEntities()) {
            Block block = blockState.getBlock();
//            if (block.getType() == Material.DISPENSER && block.getRelative(0, -1, 0).getType() == Material.OBSERVER) {
	      if(isTurret(block)){
//                turrets.add(block.getLocation());
		scheduleTurret(block);
		if (blockState instanceof Dispenser) {
		    Dispenser dispenser = (Dispenser) blockState;
            	    PersistentDataContainer dataContainer = dispenser.getPersistentDataContainer();
            	    if (dataContainer.has(passphraseKey, PersistentDataType.STRING)) {
                	String passphrase = dataContainer.get(passphraseKey, PersistentDataType.STRING);
                	turretPassphrases.put(block.getLocation(), passphrase);
            	    }
        	}
                getLogger().info("BlockyTurret loaded");
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

	if (clickedBlock != null && isTurret(clickedBlock)) {
    	    String turretPassphrase = turretPassphrases.get(clickedBlock.getLocation());

    	    if (itemInHand.getType() == Material.PAPER) {
        	ItemMeta meta = itemInHand.getItemMeta();
        	if (meta != null && meta.hasDisplayName()) {
            	    String paperPassphrase = meta.getDisplayName();
                
            	    if (turretPassphrase == null || playerHasPassphrase(player, turretPassphrase)) {
                	turretPassphrases.put(clickedBlock.getLocation(), paperPassphrase);
			BlockState blockState = clickedBlock.getState();
		        if (blockState instanceof Dispenser) {
			    Dispenser dispenser = (Dispenser) blockState;
		            PersistentDataContainer dataContainer = dispenser.getPersistentDataContainer();
		            dataContainer.set(passphraseKey, PersistentDataType.STRING, paperPassphrase);
		            blockState.update();
		        }
                	player.sendMessage(ChatColor.GREEN + "Turret passphrase has been updated.");
            	    } else {
                	player.sendMessage(ChatColor.RED + "You are not authorized to change the turret passphrase.");
            	    }
        	}
    	    }
	}
    }

/*    private void checkTurrets() {
	Iterator<Location> iterator = turrets.iterator();
	
    }*/

    private ScheduledTask scheduleTurret(Block block){
	return regionScheduler.runDelayed(
	    this,
	    block.getLocation(),
	    (task) -> {
		runTurret(block.getLocation());
	    },
	    20L
	);
    }

    private void runTurret(Location turretLoc) {
    	    World world = turretLoc.getWorld();
    	    Block block = turretLoc.getBlock();

//    	    if (block.getType() != Material.DISPENSER) {
	    if(!isTurret(block)){
        	// The turret has been destroyed, remove it from the set
//        	iterator.remove();
		turretPassphrases.remove(turretLoc);
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
	    String turretPassphrase = turretPassphrases.get(turretLoc);

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

}
