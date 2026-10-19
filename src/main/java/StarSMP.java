package dev.starsmp;

import java.util.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.bukkit.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

// ===== Merged from StarSMP.java =====

public final class StarSMP extends JavaPlugin {
    private NamespacedKey itemIdKey;
    private NamespacedKey starTypeKey;
    private NamespacedKey ownerKey;
    private CustomItems customItems;
    private AbilityManager abilityManager;
    private GUIManager guiManager;
    private EclipseListener eclipseListener;
    private StarAltarManager altarManager;

    @Override public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        if (getResource("recipes.yml") != null && !new java.io.File(getDataFolder(), "recipes.yml").exists()) saveResource("recipes.yml", false);
        itemIdKey = new NamespacedKey(this, "custom_item_id");
        starTypeKey = new NamespacedKey(this, "star_type");
        ownerKey = new NamespacedKey(this, "bound_owner");
        customItems = new CustomItems(this);
        new RecipeManager(this).registerAll();
        abilityManager = new AbilityManager(this);
        guiManager = new GUIManager(this);
        eclipseListener = new EclipseListener(this);
        altarManager = new StarAltarManager(this);
        getServer().getPluginManager().registerEvents(new MobDropListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(abilityManager, this);
        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(eclipseListener, this);
        getServer().getPluginManager().registerEvents(altarManager, this);
        StarCommand command = new StarCommand(this);
        getCommand("starsmp").setExecutor(command);
        getCommand("starsmp").setTabCompleter(command);
        // BlissGems-style cooldown HUD: refresh twice per second while holding a Star.
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (!getConfig().getBoolean("settings.actionbar-cooldowns", true)) return;
            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                org.bukkit.inventory.ItemStack held = player.getInventory().getItemInMainHand();
                StarType type = customItems.starType(held);
                if (type != null && customItems.isBoundTo(held, player.getUniqueId())) {
                    player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(abilityManager.cooldownStatus(player, type)));
                } else if (customItems.is(held, "star_eclipse") && customItems.isBoundTo(held, player.getUniqueId())) {
                    player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(eclipseListener.cooldownStatus(player)));
                }
            }
        }, 0L, 10L);
        getLogger().info("StarSMP enabled. Eight Stars registered.");
    }

    @Override public void onDisable() {
        if (abilityManager != null) abilityManager.shutdown();
        if (eclipseListener != null) eclipseListener.shutdown();
    }
    public NamespacedKey itemIdKey() { return itemIdKey; }
    public NamespacedKey starTypeKey() { return starTypeKey; }
    public NamespacedKey ownerKey() { return ownerKey; }
    public CustomItems items() { return customItems; }
    public AbilityManager abilities() { return abilityManager; }
    public GUIManager guis() { return guiManager; }
    public StarAltarManager altar() { return altarManager; }
}


// ===== Merged from StarType.java =====

enum StarType {
    SOLAR("Sunheart", "Solar Star", ChatColor.GOLD, Material.BLAZE_ROD),
    LUNAR("Moonveil", "Lunar Star", ChatColor.LIGHT_PURPLE, Material.AMETHYST_SHARD),
    THUNDER("Stormcore", "Thunder Star", ChatColor.YELLOW, Material.LIGHTNING_ROD),
    VOID("Abyssal Eye", "Void Star", ChatColor.DARK_PURPLE, Material.ECHO_SHARD),
    FROST("Glacial Heart", "Frost Star", ChatColor.AQUA, Material.BLUE_ICE),
    NATURE("Worldseed", "Nature Star", ChatColor.GREEN, Material.MOSS_BLOCK),
    SOUL("Wraithlight", "Soul Star", ChatColor.GRAY, Material.GHAST_TEAR),
    CELESTIAL("Astral Crown", "Celestial Star", ChatColor.WHITE, Material.END_CRYSTAL);

    private final String artifactName;
    private final String displayName;
    private final ChatColor color;
    private final Material icon;

    StarType(String artifactName, String displayName, ChatColor color, Material icon) {
        this.artifactName = artifactName;
        this.displayName = displayName;
        this.color = color;
        this.icon = icon;
    }
    public String id() { return name().toLowerCase(); }
    public String artifactName() { return artifactName; }
    public String displayName() { return displayName; }
    public ChatColor color() { return color; }
    public Material icon() { return icon; }
    public static StarType fromId(String id) {
        if (id == null) return null;
        try { return valueOf(id.trim().toUpperCase()); } catch (IllegalArgumentException ex) { return null; }
    }
}


// ===== Merged from abilities/AbilityManager.java =====

final class AbilityManager implements Listener {
    private final StarSMP plugin;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Set<UUID> passiveCooldown = new HashSet<>();
    public AbilityManager(StarSMP plugin) { this.plugin = plugin; }

    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        StarType star = plugin.items().starType(p.getInventory().getItemInMainHand());
        if (star == null) {
            if (plugin.items().is(p.getInventory().getItemInMainHand(), "star_catcher")) { event.setCancelled(true); catchTarget(p); }
            return;
        }
        if (!plugin.items().isBoundTo(p.getInventory().getItemInMainHand(), p.getUniqueId())) { p.sendMessage(ChatColor.RED + "This Star is bound to another player."); event.setCancelled(true); return; }
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || isDisabledWorld(p)) return;
        event.setCancelled(true);
        if (p.isSneaking()) activate(p, star, 2); else activate(p, star, 1);
    }

    @EventHandler public void onSwap(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        StarType star = plugin.items().starType(p.getInventory().getItemInMainHand());
        if (star == null || !p.isSneaking()) return;
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || isDisabledWorld(p)) return;
        event.setCancelled(true);
        if (!plugin.items().isBoundTo(p.getInventory().getItemInMainHand(), p.getUniqueId())) { p.sendMessage(ChatColor.RED + "This Star is bound to another player."); return; }
        activate(p, star, 3);
    }

    private void activate(Player p, StarType s, int slot) {
        String id = s.id() + ":" + slot;
        long now = System.currentTimeMillis();
        long end = cooldowns.getOrDefault(p.getUniqueId() + ":" + id, 0L);
        if (end > now) { p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(ChatColor.RED + "Cooldown: " + ((end - now + 999) / 1000) + "s")); return; }
        String path = "abilities." + s.id() + ".";
        int seconds = plugin.getConfig().getInt(path + (slot == 3 ? "ultimate-cooldown" : slot == 1 ? firstCooldownKey(s) : secondCooldownKey(s)), slot == 3 ? 300 : 20);
        boolean success = switch (s) {
            case SOLAR -> solar(p, slot);
            case LUNAR -> lunar(p, slot);
            case THUNDER -> thunder(p, slot);
            case VOID -> voidStar(p, slot);
            case FROST -> frost(p, slot);
            case NATURE -> nature(p, slot);
            case SOUL -> soul(p, slot);
            case CELESTIAL -> celestial(p, slot);
        };
        if (success) {
            cooldowns.put(p.getUniqueId() + ":" + id, now + seconds * 1000L);
            p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(s.color() + s.displayName() + " ability activated! " + ChatColor.GRAY + "Cooldown: " + seconds + "s"));
        }
    }
    private String firstCooldownKey(StarType s) { return switch(s) { case SOLAR -> "flare-cooldown"; case LUNAR -> "moonstep-cooldown"; case THUNDER -> "strike-cooldown"; case VOID -> "blink-cooldown"; case FROST -> "nova-cooldown"; case NATURE -> "vine-cooldown"; case SOUL -> "drain-cooldown"; case CELESTIAL -> "starfall-cooldown"; }; }
    private String secondCooldownKey(StarType s) { return switch(s) { case SOLAR -> "beam-cooldown"; case LUNAR -> "eclipse-cooldown"; case THUNDER -> "chain-cooldown"; case VOID -> "snare-cooldown"; case FROST -> "spear-cooldown"; case NATURE -> "pulse-cooldown"; case SOUL -> "dash-cooldown"; case CELESTIAL -> "shield-cooldown"; }; }

    /** Status line used by the repeating action-bar display, similar to BlissGems' cooldown HUD. */
    public String cooldownStatus(Player player, StarType star) {
        String prefix = player.getUniqueId() + ":" + star.id() + ":";
        long now = System.currentTimeMillis();
        String first = remaining(prefix + "1", now);
        String second = remaining(prefix + "2", now);
        String ultimate = remaining(prefix + "3", now);
        return star.color() + "✦ " + star.displayName() + ChatColor.DARK_GRAY + "  |  "
                + ChatColor.YELLOW + "I " + first + ChatColor.DARK_GRAY + "  "
                + ChatColor.AQUA + "II " + second + ChatColor.DARK_GRAY + "  "
                + ChatColor.LIGHT_PURPLE + "ULT " + ultimate;
    }

    private String remaining(String key, long now) {
        long end = cooldowns.getOrDefault(key, 0L);
        return end > now ? ChatColor.RED + (((end - now + 999) / 1000) + "s") : ChatColor.GREEN + "READY";
    }

    private boolean solar(Player p, int slot) {
        if (slot == 1) { burst(p, 5, 5, true, Particle.FLAME, Sound.ITEM_FIRECHARGE_USE); return true; }
        if (slot == 2) { LivingEntity target = lookTarget(p, 18); if (target == null) { p.sendMessage(ChatColor.RED + "No target in sight."); return false; } damage(p, target, 7); target.setFireTicks(50); p.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0,1,0), 25,.3,.5,.3,.02); p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1, 1.2f); return true; }
        burst(p, 8, 12, true, Particle.FLAME, Sound.ENTITY_GENERIC_EXPLODE); return true;
    }
    private boolean lunar(Player p, int slot) {
        if (slot == 1) return safeBlink(p, 7);
        if (slot == 2) { areaEffects(p, 6, PotionEffectType.DARKNESS, 80, 0, Particle.REVERSE_PORTAL); areaEffects(p, 6, PotionEffectType.SLOWNESS, 80, 1, Particle.REVERSE_PORTAL); p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 1, .7f); return true; }
        areaEffects(p, 9, PotionEffectType.SLOWNESS, 100, 2, Particle.END_ROD); pullTargets(p, 9, .9); return true;
    }
    private boolean thunder(Player p, int slot) {
        if (slot == 1) { LivingEntity t = lookTarget(p, 18); if (t == null) { p.sendMessage(ChatColor.RED + "No target in sight."); return false; } lightning(p, t); damage(p,t,6); return true; }
        if (slot == 2) { List<LivingEntity> targets = nearby(p, 10, 4); if (targets.isEmpty()) { p.sendMessage(ChatColor.RED + "No enemies nearby."); return false; } int i=0; for (LivingEntity t: targets) { lightning(p,t); damage(p,t,Math.max(2,6-i)); i++; } return true; }
        List<LivingEntity> targets = nearby(p, 9, 8); if (targets.isEmpty()) { p.sendMessage(ChatColor.GRAY + "No enemies nearby, but the storm answers."); p.getWorld().strikeLightningEffect(p.getLocation()); return true; } for (LivingEntity t: targets) { lightning(p,t); damage(p,t,5); t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 1)); } return true;
    }
    private boolean voidStar(Player p, int slot) {
        if (slot == 1) return safeBlink(p, 7);
        if (slot == 2) { areaEffects(p, 6, PotionEffectType.DARKNESS, 60, 0, Particle.PORTAL); areaEffects(p, 6, PotionEffectType.SLOWNESS, 60, 2, Particle.PORTAL); return true; }
        pullTargets(p, 8, 1.15); areaEffects(p, 8, PotionEffectType.DARKNESS, 60, 0, Particle.PORTAL); burst(p, 8, 4, false, Particle.PORTAL, Sound.ENTITY_ENDERMAN_SCREAM); return true;
    }
    private boolean frost(Player p, int slot) {
        if (slot == 1) { areaDamage(p, 5, 4); areaEffects(p, 5, PotionEffectType.SLOWNESS, 80, 1, Particle.SNOWFLAKE); p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1, .7f); return true; }
        if (slot == 2) { LivingEntity t = lookTarget(p, 18); if (t == null) { p.sendMessage(ChatColor.RED + "No target in sight."); return false; } damage(p,t,5); t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2)); t.getWorld().spawnParticle(Particle.SNOWFLAKE,t.getLocation().add(0,1,0),30,.4,.5,.4,.02); return true; }
        areaDamage(p, 7, 5); areaEffects(p, 7, PotionEffectType.SLOWNESS, 100, 3, Particle.SNOWFLAKE); return true;
    }
    private boolean nature(Player p, int slot) {
        if (slot == 1) { areaEffects(p, 5, PotionEffectType.SLOWNESS, 80, 3, Particle.COMPOSTER); areaDamage(p,5,3); return true; }
        if (slot == 2) { heal(p, 5); for (Entity e : p.getNearbyEntities(6,6,6)) if (e instanceof Player ally && (ally.equals(p) || plugin.getConfig().getBoolean("settings.friendly-fire", false))) { heal(ally, 4); ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 1)); } p.getWorld().spawnParticle(Particle.VILLAGER_HAPPY,p.getLocation().add(0,1,0),35,2,1,2,.1); p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,1,1.5f); return true; }
        heal(p, 8); p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 1)); areaDamage(p, 6, 4); areaEffects(p,6,PotionEffectType.SLOWNESS,80,1,Particle.VILLAGER_HAPPY); return true;
    }
    private boolean soul(Player p, int slot) {
        if (slot == 1) { LivingEntity t=lookTarget(p,16); if(t==null){p.sendMessage(ChatColor.RED+"No target in sight.");return false;} damage(p,t,6); heal(p,3); p.getWorld().spawnParticle(Particle.SOUL, t.getLocation().add(0,1,0),20,.4,.5,.4,.02); return true; }
        if (slot == 2) { Vector v=p.getLocation().getDirection().normalize().multiply(1.25); v.setY(Math.max(.15,v.getY()+.15)); p.setVelocity(v); p.getWorld().spawnParticle(Particle.SOUL,p.getLocation(),25,.5,.5,.5,.03); return true; }
        areaEffects(p,6,PotionEffectType.DARKNESS,80,0,Particle.SOUL); areaEffects(p,6,PotionEffectType.SLOWNESS,80,1,Particle.SOUL); areaDamage(p,6,5); p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,160,1)); return true;
    }
    private boolean celestial(Player p, int slot) {
        if (slot == 1) { Location center = p.getTargetBlockExact(18) == null ? p.getLocation().add(p.getLocation().getDirection().multiply(8)) : p.getTargetBlockExact(18).getLocation().add(.5,1,.5); p.getWorld().spawnParticle(Particle.END_ROD,center,50,2,2,2,.04); for(LivingEntity t: nearby(center,4,8,p)) damage(p,t,5); p.playSound(center,Sound.ENTITY_FIREWORK_ROCKET_BLAST,1,1.4f); return true; }
        if (slot == 2) { p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,160,3)); p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1,0),35,1,1,1,.1); p.playSound(p.getLocation(),Sound.ITEM_TOTEM_USE,1,1.4f); return true; }
        areaDamage(p,7,7); areaEffects(p,7,PotionEffectType.SLOWNESS,60,1,Particle.END_ROD); for(LivingEntity t: nearby(p,7,8)) t.setVelocity(t.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(.85).setY(.35)); p.playSound(p.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,1,1.5f); return true;
    }

    private void burst(Player p,double radius,double damage,boolean ignite,Particle particle,Sound sound){ for(LivingEntity t:nearby(p,radius,100)){damage(p,t,damage);if(ignite)t.setFireTicks(40);} p.getWorld().spawnParticle(particle,p.getLocation().add(0,1,0),80,radius/2,1.5,radius/2,.04);p.playSound(p.getLocation(),sound,1,1); }
    private void areaDamage(Player p,double radius,double amount){for(LivingEntity t:nearby(p,radius,100))damage(p,t,amount);}
    private void areaEffects(Player p,double radius,PotionEffectType type,int ticks,int amplifier,Particle particle){for(LivingEntity t:nearby(p,radius,100)){t.addPotionEffect(new PotionEffect(type,ticks,amplifier));t.getWorld().spawnParticle(particle,t.getLocation().add(0,1,0),8,.3,.5,.3,.02);}p.getWorld().spawnParticle(particle,p.getLocation().add(0,1,0),45,radius/2,1,radius/2,.02);}
    private void pullTargets(Player p,double radius,double strength){for(LivingEntity t:nearby(p,radius,100)){Vector v=p.getLocation().toVector().subtract(t.getLocation().toVector());if(v.lengthSquared()>0){v.normalize().multiply(Math.min(strength,1.25));v.setY(Math.min(.35,Math.max(-.1,v.getY())));t.setVelocity(v);}}}
    private List<LivingEntity> nearby(Player p,double radius,int max){return nearby(p.getLocation(),radius,max,p);}
    private List<LivingEntity> nearby(Location loc,double radius,int max,Player caster){List<LivingEntity> out=new ArrayList<>();for(Entity e:loc.getWorld().getNearbyEntities(loc,radius,radius,radius)){if(!(e instanceof LivingEntity t)||t.equals(caster)||t.isDead()||t instanceof ArmorStand)continue;if(t instanceof Player target && !canDamage(caster,target))continue;out.add(t);if(out.size()>=max)break;}return out;}
    private void damage(Player p,LivingEntity t,double amount){if(t instanceof Player target&&!canDamage(p,target))return;double mult=plugin.getConfig().getDouble("abilities.global-damage-multiplier",1.0);t.damage(Math.max(0,amount*mult),p);}
    private boolean canDamage(Player a,Player b){if(a.equals(b))return false;if(!plugin.getConfig().getBoolean("settings.pvp-enabled",true))return false;return true;}
    private LivingEntity lookTarget(Player p,double range){for(Entity e:p.getNearbyEntities(range,range,range)){if(!(e instanceof LivingEntity t)||t.equals(p)||t.isDead())continue;if(t instanceof Player target&&!canDamage(p,target))continue;Vector to=t.getEyeLocation().toVector().subtract(p.getEyeLocation().toVector());if(to.length()>range)continue;if(p.getEyeLocation().getDirection().normalize().dot(to.normalize())>.965 && p.hasLineOfSight(t))return t;}return null;}
    private boolean safeBlink(Player p,double distance){Location start=p.getLocation();Vector dir=start.getDirection().setY(0);if(dir.lengthSquared()<.01)dir=new Vector(0,0,1);dir.normalize();for(double d=distance;d>=1;d-=.5){Location test=start.clone().add(dir.clone().multiply(d));test.setYaw(start.getYaw());test.setPitch(start.getPitch());if(test.getWorld().getWorldBorder().isInside(test)&&test.getBlock().isPassable()&&test.clone().add(0,1,0).getBlock().isPassable()&&test.clone().subtract(0,1,0).getBlock().getType().isSolid()&&test.getBlock().getType()!=Material.LAVA){p.teleport(test);p.getWorld().spawnParticle(Particle.PORTAL,start.add(0,1,0),35,.4,.5,.4,.03);p.playSound(p.getLocation(),Sound.ENTITY_ENDERMAN_TELEPORT,1,1.2f);return true;}}p.sendMessage(ChatColor.RED+"No safe landing spot found.");return false;}
    private void lightning(Player p,LivingEntity t){t.getWorld().strikeLightningEffect(t.getLocation());t.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,t.getLocation().add(0,1,0),20,.3,.5,.3,.1);}
    private void heal(Player p,double amount){p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+amount));}
    private boolean isDisabledWorld(Player p){return plugin.getConfig().getStringList("settings.disabled-worlds").contains(p.getWorld().getName());}
    private void catchTarget(Player p){LivingEntity t=lookTarget(p,18);if(t==null){p.sendMessage(ChatColor.GRAY+"No creature or player in sight to mark.");return;}t.setGlowing(true);p.sendMessage(ChatColor.AQUA+"✦ Star Mark placed on "+t.getName()+" for 5 seconds.");UUID id=t.getUniqueId();Bukkit.getScheduler().runTaskLater(plugin,()->{Entity e=Bukkit.getEntity(id);if(e instanceof LivingEntity l&&!l.isDead())l.setGlowing(false);},100L);p.playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,1,1.5f);}
    @EventHandler public void onIncomingDamage(EntityDamageEvent event){if(!(event.getEntity() instanceof Player p))return;ItemStack hand=p.getInventory().getItemInMainHand();StarType s=plugin.items().starType(hand);if(s==null||!plugin.items().isBoundTo(hand,p.getUniqueId()))return;if(s==StarType.SOLAR&&event.getCause()==EntityDamageEvent.DamageCause.FIRE)event.setDamage(event.getDamage()*.25);if(s==StarType.FROST&&event.getCause()==EntityDamageEvent.DamageCause.FREEZE)event.setDamage(event.getDamage()*.5);if(s==StarType.LUNAR&&p.getWorld().getTime()>13000&&p.getWorld().getTime()<23000)event.setDamage(event.getDamage()*.9);if(s==StarType.VOID&&event.getCause()==EntityDamageEvent.DamageCause.FALL)event.setDamage(event.getDamage()*.5);if(s==StarType.CELESTIAL&&p.getWorld().getTime()>13000&&p.getWorld().getTime()<23000)event.setDamage(event.getDamage()*.9);if(s==StarType.NATURE&&event.getCause()!=EntityDamageEvent.DamageCause.VOID&&event.getDamage()<4&&passiveCooldown.add(p.getUniqueId())){p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,40,0));Bukkit.getScheduler().runTaskLater(plugin,()->passiveCooldown.remove(p.getUniqueId()),100L);}if(s==StarType.SOUL&&event.getDamage()>=6&&passiveCooldown.add(p.getUniqueId())){p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,100,1));Bukkit.getScheduler().runTaskLater(plugin,()->passiveCooldown.remove(p.getUniqueId()),200L);}}
    @EventHandler public void onCombat(EntityDamageByEntityEvent event){if(!(event.getEntity() instanceof Player p))return;ItemStack hand=p.getInventory().getItemInMainHand();StarType s=plugin.items().starType(hand);if(s==StarType.THUNDER&&event.getDamager() instanceof Player attacker&&passiveCooldown.add(p.getUniqueId())){p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,50,0));Bukkit.getScheduler().runTaskLater(plugin,()->passiveCooldown.remove(p.getUniqueId()),100L);}}
    public void shutdown(){cooldowns.clear();passiveCooldown.clear();}
}


// ===== Merged from altar/AltarHolder.java =====

final class AltarHolder implements InventoryHolder {
    @Override public Inventory getInventory() { return null; }
}


// ===== Merged from altar/StarAltarManager.java =====

final class StarAltarManager implements Listener {
    private final StarSMP plugin;
    private static final int[] STAR_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19};
    private static final int NETHER_STAR_SLOT = 21;
    private static final int DUST_SLOT = 22;
    private static final int FRAGMENT_SLOT = 23;
    private static final int CONFIRM_SLOT = 49;

    public StarAltarManager(StarSMP plugin) { this.plugin = plugin; }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new AltarHolder(), 54, ChatColor.DARK_PURPLE + "✦ Star Altar");
        ItemStack filler = button(Material.PURPLE_STAINED_GLASS_PANE, ChatColor.DARK_PURPLE + "Cosmic energy", "Place ingredients in the marked slots.");
        for (int i = 0; i < 54; i++) inv.setItem(i, filler.clone());
        for (int i = 0; i < STAR_SLOTS.length; i++) inv.setItem(STAR_SLOTS[i], button(Material.NETHER_STAR, ChatColor.GOLD + "Star slot " + (i + 1), "Place a different genuine Star here."));
        inv.setItem(NETHER_STAR_SLOT, button(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "Core catalyst", "Place 1 vanilla Nether Star here."));
        inv.setItem(DUST_SLOT, button(Material.GLOWSTONE_DUST, ChatColor.YELLOW + "Star Dust", "Place 8 genuine Star Dust here."));
        inv.setItem(FRAGMENT_SLOT, button(Material.PRISMARINE_CRYSTALS, ChatColor.AQUA + "Star Fragments", "Place 4 genuine Star Fragments here."));
        inv.setItem(25, button(Material.DRAGON_EGG, ChatColor.LIGHT_PURPLE + "STAR ECLIPSE", "Requires all 8 different Stars, 1 Nether Star, 8 Star Dust, and 4 Star Fragments."));
        inv.setItem(CONFIRM_SLOT, button(Material.LIME_CONCRETE, ChatColor.GREEN + "FORGE STAR ECLIPSE", "Click to validate and consume the ingredients."));
        player.openInventory(inv);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AltarHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int raw = event.getRawSlot();
        if (raw >= event.getView().getTopInventory().getSize()) return;
        if (raw == CONFIRM_SLOT) { forge(player, event.getView().getTopInventory()); return; }
        if (!isInput(raw)) return;
        Inventory inv = event.getView().getTopInventory();
        ItemStack cursor = event.getCursor();
        ItemStack current = inv.getItem(raw);
        if (isPlaceholder(current)) current = null;
        if (cursor == null || cursor.getType() == Material.AIR) {
            if (current != null) { inv.setItem(raw, placeholderFor(raw)); player.setItemOnCursor(current); }
            return;
        }
        if (!validForSlot(raw, cursor)) { player.sendMessage(ChatColor.RED + "That item does not belong in this altar slot."); return; }
        if (current != null && !isPlaceholder(current)) {
            inv.setItem(raw, cursor);
            player.setItemOnCursor(current);
        } else {
            inv.setItem(raw, cursor);
            player.setItemOnCursor(null);
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AltarHolder) event.setCancelled(true);
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof AltarHolder)) return;
        // Return all real ingredients to the owner on close; placeholders are not real items.
        if (event.getPlayer() instanceof Player player) {
            for (int slot : inputSlots()) {
                ItemStack item = event.getInventory().getItem(slot);
                if (item != null && item.getType() != Material.AIR && !isPlaceholder(item)) {
                    event.getInventory().setItem(slot, null);
                    player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                }
            }
        }
    }

    private void forge(Player player, Inventory inv) {
        boolean[] found = new boolean[StarType.values().length];
        for (int slot : STAR_SLOTS) {
            ItemStack item = inv.getItem(slot);
            StarType type = plugin.items().starType(item);
            if (type == null || found[type.ordinal()]) { player.sendMessage(ChatColor.RED + "The altar needs one of every different genuine Star."); return; }
            if (!plugin.items().isBoundTo(item, player.getUniqueId())) { player.sendMessage(ChatColor.RED + "One of those Stars belongs to another player."); return; }
            found[type.ordinal()] = true;
        }
        for (boolean has : found) if (!has) { player.sendMessage(ChatColor.RED + "One or more Stars are missing."); return; }
        ItemStack catalyst = inv.getItem(NETHER_STAR_SLOT), dust = inv.getItem(DUST_SLOT), fragments = inv.getItem(FRAGMENT_SLOT);
        if (catalyst == null || catalyst.getType() != Material.NETHER_STAR || catalyst.getAmount() < 1) { player.sendMessage(ChatColor.RED + "Missing 1 Nether Star catalyst."); return; }
        if (!plugin.items().is(dust, "star_dust") || dust.getAmount() < 8) { player.sendMessage(ChatColor.RED + "Missing 8 genuine Star Dust."); return; }
        if (!plugin.items().is(fragments, "star_fragment") || fragments.getAmount() < 4) { player.sendMessage(ChatColor.RED + "Missing 4 genuine Star Fragments."); return; }

        // Consume only after all checks pass. No asynchronous gap exists between validation and consumption.
        for (int slot : STAR_SLOTS) inv.setItem(slot, null);
        consume(inv, NETHER_STAR_SLOT, 1);
        consume(inv, DUST_SLOT, 8);
        consume(inv, FRAGMENT_SLOT, 4);
        inv.setItem(25, button(Material.DRAGON_EGG, ChatColor.LIGHT_PURPLE + "STAR ECLIPSE FORGED", "The cosmos bends to your will."));
        player.getInventory().addItem(plugin.items().starEclipse(1, player.getUniqueId())).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.sendTitle(ChatColor.LIGHT_PURPLE + "STAR ECLIPSE", ChatColor.DARK_PURPLE + "The End of the Cosmos", 10, 60, 20);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, .65f);
        player.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, player.getLocation().add(0, 1, 0), 120, 2, 1.5, 2, .05);
        if (plugin.getConfig().getBoolean("settings.broadcast-legendary-crafts", true)) Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + player.getName() + " forged the legendary STAR ECLIPSE!");
        // Clear all input placeholders so the same ingredients cannot be consumed twice.
        for (int slot : inputSlots()) inv.setItem(slot, null);
    }

    private void consume(Inventory inv, int slot, int amount) { ItemStack item = inv.getItem(slot); if (item.getAmount() <= amount) inv.setItem(slot, null); else item.setAmount(item.getAmount() - amount); }
    private boolean validForSlot(int slot, ItemStack item) {
        if (contains(STAR_SLOTS, slot)) return plugin.items().starType(item) != null;
        if (slot == NETHER_STAR_SLOT) return item.getType() == Material.NETHER_STAR && plugin.items().itemId(item) == null && !isPlaceholder(item);
        if (slot == DUST_SLOT) return plugin.items().is(item, "star_dust");
        if (slot == FRAGMENT_SLOT) return plugin.items().is(item, "star_fragment");
        return false;
    }
    private boolean isInput(int slot) { return contains(inputSlots(), slot); }
    private int[] inputSlots() { return new int[]{10,11,12,13,14,15,16,19,NETHER_STAR_SLOT,DUST_SLOT,FRAGMENT_SLOT}; }
    private boolean contains(int[] values, int v) { for (int x : values) if (x == v) return true; return false; }
    private boolean isPlaceholder(ItemStack item) { if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false; String n = ChatColor.stripColor(item.getItemMeta().getDisplayName()); return n.startsWith("Star slot") || n.equals("Core catalyst") || n.equals("Star Dust") || n.equals("Star Fragments"); }
    private ItemStack placeholderFor(int slot) {
        if (contains(STAR_SLOTS, slot)) return button(Material.NETHER_STAR, ChatColor.GOLD + "Star slot " + (indexOf(STAR_SLOTS, slot)+1), "Place a different genuine Star here.");
        if (slot == NETHER_STAR_SLOT) return button(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "Core catalyst", "Place 1 vanilla Nether Star here.");
        if (slot == DUST_SLOT) return button(Material.GLOWSTONE_DUST, ChatColor.YELLOW + "Star Dust", "Place 8 genuine Star Dust here.");
        return button(Material.PRISMARINE_CRYSTALS, ChatColor.AQUA + "Star Fragments", "Place 4 genuine Star Fragments here.");
    }
    private int indexOf(int[] a, int n) { for (int i=0;i<a.length;i++) if(a[i]==n)return i; return 0; }
    private ItemStack button(Material material, String name, String lore) { ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(List.of(ChatColor.GRAY + lore)); item.setItemMeta(meta); return item; }
}


// ===== Merged from commands/StarCommand.java =====

final class StarCommand implements CommandExecutor, TabCompleter {
    private final StarSMP plugin;
    public StarCommand(StarSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(sender); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> { if (playerOnly(sender)) plugin.guis().openMain((Player) sender); }
            case "stars" -> { for (StarType star : StarType.values()) sender.sendMessage(star.color() + "✦ " + star.displayName() + ChatColor.GRAY + " — " + star.artifactName() + ChatColor.DARK_GRAY + " (" + star.id() + ")"); }
            case "profile" -> { if (playerOnly(sender)) { Player p=(Player)sender; sender.sendMessage(ChatColor.AQUA + "Star Dust: " + countDust(p) + ChatColor.GRAY + " | Hold a Star to see its cooldown HUD."); } }
            case "recipe" -> sender.sendMessage(ChatColor.GOLD + "✦ Star progression: 9 Star Dust → Compressed Dust; 4 Compressed Dust → Star Fragment; 4 Fragments + rare materials → Star Core. Star recipes are documented in recipes.yml.");
            case "catch" -> { if (playerOnly(sender)) sender.sendMessage(ChatColor.GRAY + "The Star Catcher marks nearby enemies when you right-click."); }
            case "altar" -> { if (playerOnly(sender)) plugin.altar().open((Player) sender); }
            case "ability" -> sender.sendMessage(ChatColor.YELLOW + "Star controls: Right-click = Ability I; sneak + right-click = Ability II; sneak + swap-hand (F) = Ultimate. Star Eclipse: right-click = Cosmic Rift; sneak + right-click = Reality Collapse; sneak + F = Big Bang.");
            case "cooldowns" -> sender.sendMessage(ChatColor.AQUA + "Cooldown HUD: hold a Star or Star Eclipse to see each ability's remaining time and READY status on your action bar.");
            case "dust" -> { if (playerOnly(sender)) sender.sendMessage(ChatColor.YELLOW + "Genuine Star Dust in inventory: " + countDust((Player)sender)); }
            case "stats" -> sender.sendMessage(ChatColor.GOLD + "StarSMP " + plugin.getDescription().getVersion() + ChatColor.GRAY + " | 8 Stars | Star Eclipse | Star Altar");
            case "give" -> { if (!isAdmin(sender)) deny(sender); else giveCommand(sender, args, 1); }
            case "admin" -> { if (!isAdmin(sender)) deny(sender); else adminCommand(sender, args); }
            default -> { sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /" + label + " help."); }
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "✦ STAR SMP COMMANDS");
        sender.sendMessage(ChatColor.YELLOW + "/starsmp help" + ChatColor.GRAY + " - Show this command list");
        sender.sendMessage(ChatColor.YELLOW + "/starsmp menu" + ChatColor.GRAY + " - Open the Star menu");
        sender.sendMessage(ChatColor.YELLOW + "/starsmp stars" + ChatColor.GRAY + " - List all eight Stars");
        sender.sendMessage(ChatColor.YELLOW + "/starsmp profile" + ChatColor.GRAY + " - View your Star Dust count");
        sender.sendMessage(ChatColor.YELLOW + "/starsmp recipe" + ChatColor.GRAY + " - Show progression recipes");
        sender.sendMessage(ChatColor.YELLOW + "/starsmp catch" + ChatColor.GRAY + " - Star Catcher info");
        sender.sendMessage(ChatColor.YELLOW + "/starsmp altar" + ChatColor.GRAY + " - Open the Star Altar");
        sender.sendMessage(ChatColor.YELLOW + "/starsmp ability" + ChatColor.GRAY + " - Show ability controls");
        sender.sendMessage(ChatColor.YELLOW + "/starsmp cooldowns" + ChatColor.GRAY + " - Explain cooldown HUD");
        sender.sendMessage(ChatColor.YELLOW + "/starsmp dust" + ChatColor.GRAY + " - Count your genuine Star Dust");
        sender.sendMessage(ChatColor.YELLOW + "/starsmp stats" + ChatColor.GRAY + " - Show plugin summary");
        if (isAdmin(sender)) {
            sender.sendMessage(ChatColor.RED + "ADMIN COMMANDS");
            sender.sendMessage(ChatColor.YELLOW + "/starsmp give <player> <item|star-id> [amount]" + ChatColor.GRAY + " - Give an item");
            sender.sendMessage(ChatColor.YELLOW + "/starsmp admin giveall <player>" + ChatColor.GRAY + " - Give all eight Stars");
            sender.sendMessage(ChatColor.YELLOW + "/starsmp admin dust <player> <amount>" + ChatColor.GRAY + " - Give Star Dust");
            sender.sendMessage(ChatColor.YELLOW + "/starsmp admin removedust <player> <amount>" + ChatColor.GRAY + " - Remove Star Dust");
            sender.sendMessage(ChatColor.YELLOW + "/starsmp admin gui" + ChatColor.GRAY + " - Open admin GUI");
            sender.sendMessage(ChatColor.YELLOW + "/starsmp admin reload" + ChatColor.GRAY + " - Reload config");
            sender.sendMessage(ChatColor.GRAY + "Alias: /star works as /starsmp.");
        }
    }

    private void adminCommand(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW + "Admin: give <player> <item> [amount] | giveall <player> | dust <player> <amount> | removedust <player> <amount> | gui | reload"); return; }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> giveCommand(sender, args, 2);
            case "giveall" -> {
                if (args.length < 3) { sender.sendMessage(ChatColor.RED + "Usage: /starsmp admin giveall <player>"); return; }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) { sender.sendMessage(ChatColor.RED + "That player must be online."); return; }
                for (StarType star : StarType.values()) addOrDrop(target, plugin.items().star(star, 1, target.getUniqueId()));
                sender.sendMessage(ChatColor.GREEN + "Gave all eight bound Stars to " + target.getName() + ".");
            }
            case "dust" -> {
                if (args.length < 4) { sender.sendMessage(ChatColor.RED + "Usage: /starsmp admin dust <player> <amount>"); return; }
                Player target=Bukkit.getPlayerExact(args[2]); int amount=parseAmount(sender,args[3],1,2304); if(target==null){sender.sendMessage(ChatColor.RED+"That player must be online.");return;} if(amount<1)return;
                int left=amount; while(left>0){int stack=Math.min(64,left);addOrDrop(target,plugin.items().starDust(stack));left-=stack;} sender.sendMessage(ChatColor.GREEN+"Gave "+amount+" Star Dust to "+target.getName()+".");
            }
            case "removedust" -> {
                if (args.length < 4) { sender.sendMessage(ChatColor.RED + "Usage: /starsmp admin removedust <player> <amount>"); return; }
                Player target=Bukkit.getPlayerExact(args[2]); int amount=parseAmount(sender,args[3],1,2304); if(target==null){sender.sendMessage(ChatColor.RED+"That player must be online.");return;} if(amount<1)return;
                int removed=removeDust(target,amount); sender.sendMessage(ChatColor.GREEN+"Removed "+removed+" Star Dust from "+target.getName()+".");
            }
            case "gui" -> { if (sender instanceof Player p) plugin.guis().openAdmin(p); else sender.sendMessage("Players only."); }
            case "reload" -> { plugin.reloadConfig(); sender.sendMessage(ChatColor.GREEN+"StarSMP configuration reloaded."); }
            default -> sender.sendMessage(ChatColor.RED + "Unknown admin command. Use /starsmp help.");
        }
    }

    /** argsStart is 1 for /starsmp give and 2 for /starsmp admin give. */
    private void giveCommand(CommandSender sender, String[] args, int argsStart) {
        if (args.length < argsStart + 2) { sender.sendMessage(ChatColor.RED + "Usage: /starsmp give <player> <item|star-id> [amount]"); return; }
        Player target=Bukkit.getPlayerExact(args[argsStart]);
        if(target==null){sender.sendMessage(ChatColor.RED+"That player must be online.");return;}
        String id=args[argsStart+1].toLowerCase(Locale.ROOT);
        int amount=1;
        if(args.length>argsStart+2){amount=parseAmount(sender,args[argsStart+2],1,2304);if(amount<1)return;}
        StarType star=StarType.fromId(id);
        if(star!=null){ if(amount>1){sender.sendMessage(ChatColor.RED+"Stars are unique items; amount must be 1.");return;} addOrDrop(target,plugin.items().star(star,1,target.getUniqueId())); }
        else {
            if ((id.equals("eclipse") || id.equals("star_eclipse")) && amount > 1) { sender.sendMessage(ChatColor.RED+"Star Eclipse is unique; amount must be 1."); return; }
            int left=amount;
            while(left>0){int stack=Math.min(64,left); ItemStack item=switch(id){
                case "dust", "star_dust" -> plugin.items().starDust(stack);
                case "catcher", "star_catcher" -> plugin.items().starCatcher(stack);
                case "eclipse", "star_eclipse" -> plugin.items().starEclipse(stack,target.getUniqueId());
                case "compressed_dust" -> plugin.items().compressedDust(stack);
                case "fragment", "star_fragment" -> plugin.items().starFragment(stack);
                case "core", "star_core" -> plugin.items().starCore(stack);
                default -> null;
            };
            if(item==null){sender.sendMessage(ChatColor.RED+"Unknown item. Try dust, catcher, eclipse, compressed_dust, fragment, core, or one of the eight Star IDs.");return;}
            addOrDrop(target,item);left-=stack;
            }
        }
        sender.sendMessage(ChatColor.GREEN+"Gave "+amount+" × "+id+" to "+target.getName()+".");
    }

    private int parseAmount(CommandSender sender,String raw,int min,int max){try{int amount=Integer.parseInt(raw);if(amount<min||amount>max){sender.sendMessage(ChatColor.RED+"Amount must be between "+min+" and "+max+".");return -1;}return amount;}catch(NumberFormatException e){sender.sendMessage(ChatColor.RED+"Amount must be a whole number.");return -1;}}
    private void addOrDrop(Player target,ItemStack item){Map<Integer,ItemStack> leftovers=target.getInventory().addItem(item);leftovers.values().forEach(left->target.getWorld().dropItemNaturally(target.getLocation(),left));}
    private int removeDust(Player target,int amount){int remaining=amount;for(int slot=0;slot<target.getInventory().getSize()&&remaining>0;slot++){ItemStack item=target.getInventory().getItem(slot);if(plugin.items().is(item,"star_dust")){int take=Math.min(remaining,item.getAmount());remaining-=take;int next=item.getAmount()-take;if(next<=0)target.getInventory().setItem(slot,null);else item.setAmount(next);}}return amount-remaining;}
    private int countDust(Player p){int n=0;for(ItemStack item:p.getInventory().getContents())if(plugin.items().is(item,"star_dust"))n+=item.getAmount();return n;}
    private boolean playerOnly(CommandSender sender){if(sender instanceof Player)return true;sender.sendMessage(ChatColor.RED+"This command can only be used by a player.");return false;}
    private boolean isAdmin(CommandSender sender){return (sender.isOp()&&sender.hasPermission("starsmp.admin"))||(!(sender instanceof Player)&&plugin.getConfig().getBoolean("settings.admin-console-allowed",true));}
    private void deny(CommandSender sender){sender.sendMessage(ChatColor.RED+"You must be an operator to use admin commands.");}

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> choices=new ArrayList<>();
        boolean admin=isAdmin(sender);
        if(args.length==1){choices.addAll(List.of("help","menu","stars","profile","recipe","catch","altar","ability","cooldowns","dust","stats"));if(admin)choices.addAll(List.of("give","admin"));}
        else if(args.length==2&&args[0].equalsIgnoreCase("admin")&&admin)choices.addAll(List.of("give","giveall","dust","removedust","gui","reload"));
        else if(args.length==2&&args[0].equalsIgnoreCase("give")&&admin)choices.addAll(onlineNames());
        else if(args.length==3&&args[0].equalsIgnoreCase("give")&&admin)choices.addAll(itemIds());
        else if(args.length==4&&args[0].equalsIgnoreCase("give")&&admin)choices.add("1");
        else if(args.length==3&&args[0].equalsIgnoreCase("admin")&&admin&&List.of("give","giveall","dust","removedust").contains(args[1].toLowerCase(Locale.ROOT)))choices.addAll(onlineNames());
        else if(args.length==4&&args[0].equalsIgnoreCase("admin")&&admin&&args[1].equalsIgnoreCase("give"))choices.addAll(itemIds());
        else if(args.length==4&&args[0].equalsIgnoreCase("admin")&&admin&&List.of("dust","removedust").contains(args[1].toLowerCase(Locale.ROOT)))choices.add("64");
        else if(args.length==5&&args[0].equalsIgnoreCase("admin")&&admin&&args[1].equalsIgnoreCase("give"))choices.add("1");
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);
        return choices.stream().distinct().filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
    }
    private List<String> onlineNames(){return Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());}
    private List<String> itemIds(){List<String> out=new ArrayList<>(Arrays.asList("dust","star_dust","catcher","eclipse","compressed_dust","fragment","core"));for(StarType s:StarType.values())out.add(s.id());return out;}
}


// ===== Merged from gui/GUIManager.java =====

final class GUIManager implements Listener {
    private final StarSMP plugin;
    public GUIManager(StarSMP plugin) { this.plugin = plugin; }
    public void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(new StarMenuHolder("main"), 27, ChatColor.DARK_PURPLE + "✦ STAR SMP");
        inv.setItem(10, button(Material.NETHER_STAR, ChatColor.GOLD + "The Eight Stars", "View all eight cosmic artifacts"));
        inv.setItem(12, button(Material.GLOWSTONE_DUST, ChatColor.YELLOW + "Star Dust", "Collect dust from creature drops"));
        inv.setItem(14, button(Material.BOOK, ChatColor.AQUA + "Recipes", "Check recipes.yml for current recipes"));
        inv.setItem(16, button(Material.BLAZE_ROD, ChatColor.LIGHT_PURPLE + "Star Catcher", "Mark a target with cosmic energy"));
        p.openInventory(inv);
    }
    public void openAdmin(Player p) {
        if (!p.isOp() || !p.hasPermission("starsmp.admin")) { p.sendMessage(ChatColor.RED + "OP only."); return; }
        Inventory inv = Bukkit.createInventory(new StarMenuHolder("admin"), 27, ChatColor.RED + "StarSMP Admin");
        inv.setItem(10, button(Material.GLOWSTONE_DUST, ChatColor.YELLOW + "Give yourself 16 Star Dust", "Click to receive a test stack"));
        inv.setItem(12, button(Material.NETHER_STAR, ChatColor.GOLD + "Give all eight Stars", "Click to receive all eight Stars"));
        inv.setItem(14, button(Material.BLAZE_ROD, ChatColor.AQUA + "Give Star Catcher", "Click to receive a Star Catcher"));
        inv.setItem(16, button(Material.REDSTONE, ChatColor.RED + "Reload config", "Click to reload StarSMP config"));
        p.openInventory(inv);
    }
    private ItemStack button(Material material, String name, String lore) { ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(List.of(ChatColor.GRAY + lore)); item.setItemMeta(meta); return item; }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof StarMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (holder.kind().equals("main")) {
            if (event.getRawSlot() == 10) { p.closeInventory(); for (StarType s : StarType.values()) p.sendMessage(s.color() + "✦ " + s.displayName() + ChatColor.GRAY + " — " + s.artifactName()); }
            else if (event.getRawSlot() == 12) p.sendMessage(ChatColor.YELLOW + "Star Dust drops from creatures. Default rates are configurable in config.yml.");
            else if (event.getRawSlot() == 14) p.sendMessage(ChatColor.AQUA + "Open plugins/StarSMP/recipes.yml for the current recipe plan.");
            else if (event.getRawSlot() == 16) p.sendMessage(ChatColor.GRAY + "Craft or ask an operator for a Star Catcher.");
            return;
        }
        if (!p.isOp() || !p.hasPermission("starsmp.admin")) { p.closeInventory(); p.sendMessage(ChatColor.RED + "OP only."); return; }
        switch (event.getRawSlot()) {
            case 10 -> p.getInventory().addItem(plugin.items().starDust(16)).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
            case 12 -> { for (StarType s : StarType.values()) p.getInventory().addItem(plugin.items().star(s,1,p.getUniqueId())).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(),i)); }
            case 14 -> p.getInventory().addItem(plugin.items().starCatcher(1)).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
            case 16 -> { plugin.reloadConfig(); p.sendMessage(ChatColor.GREEN + "StarSMP config reloaded."); }
            default -> { }
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof StarMenuHolder) event.setCancelled(true); }
}


// ===== Merged from gui/StarMenuHolder.java =====

final class StarMenuHolder implements InventoryHolder {
    private final String kind;
    public StarMenuHolder(String kind) { this.kind = kind; }
    public String kind() { return kind; }
    @Override public Inventory getInventory() { return null; }
}


// ===== Merged from items/CustomItems.java =====

final class CustomItems {
    private final StarSMP plugin;
    public CustomItems(StarSMP plugin) { this.plugin = plugin; }

    public ItemStack starDust(int amount) { return tagged(Material.GLOWSTONE_DUST, "✦ Star Dust", List.of(ChatColor.GRAY + "A particle of a fallen star.", ChatColor.DARK_PURPLE + "Used to forge cosmic artifacts."), "star_dust", amount); }
    public ItemStack compressedDust(int amount) { return tagged(Material.AMETHYST_BLOCK, "✦ Compressed Star Dust", List.of(ChatColor.GRAY + "Nine measures of Star Dust, condensed."), "compressed_dust", amount); }
    public ItemStack starFragment(int amount) { return tagged(Material.PRISMARINE_CRYSTALS, "✦ Star Fragment", List.of(ChatColor.GRAY + "A shard of cosmic potential."), "star_fragment", amount); }
    public ItemStack starCore(int amount) { return tagged(Material.NETHER_STAR, "✦ Star Core", List.of(ChatColor.GRAY + "The heart of a legendary Star."), "star_core", amount); }
    public ItemStack starCatcher(int amount) { return tagged(Material.BLAZE_ROD, ChatColor.AQUA + "Star Catcher", List.of(ChatColor.GRAY + "Right-click to mark nearby enemies.", ChatColor.GRAY + "A tool for tracking cosmic energy."), "star_catcher", amount); }
    public ItemStack starEclipse(int amount) { return starEclipse(amount, null); }
    public ItemStack starEclipse(int amount, java.util.UUID owner) {
        ItemStack item = tagged(Material.DRAGON_EGG, ChatColor.LIGHT_PURPLE + "✦ STAR ECLIPSE", List.of(ChatColor.DARK_PURPLE + "THE END OF THE COSMOS", ChatColor.GRAY + "Right-click: Cosmic Rift", ChatColor.GRAY + "Sneak + right-click: Reality Collapse", ChatColor.GRAY + "Sneak + swap-hand: Big Bang"), "star_eclipse", amount);
        ItemMeta eclipseMeta = item.getItemMeta();
        eclipseMeta.setMaxStackSize(1);
        item.setItemMeta(eclipseMeta);
        if (owner != null && plugin.getConfig().getBoolean("settings.soulbound", true)) {
            ItemMeta meta = item.getItemMeta();
            meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(plugin.ownerKey(), PersistentDataType.STRING, owner.toString());
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack star(StarType type, int amount, java.util.UUID owner) {
        ItemStack item = tagged(type.icon(), type.color() + "✦ " + type.displayName(), List.of(ChatColor.GRAY + type.artifactName(), ChatColor.DARK_GRAY + "Legendary cosmic artifact", ChatColor.YELLOW + "Right-click: Active I", ChatColor.YELLOW + "Sneak + Right-click: Active II", ChatColor.YELLOW + "Swap hand: Ultimate"), "star", amount);
        ItemMeta meta = item.getItemMeta();
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(plugin.starTypeKey(), PersistentDataType.STRING, type.id());
        if (owner != null && plugin.getConfig().getBoolean("settings.soulbound", true)) meta.getPersistentDataContainer().set(plugin.ownerKey(), PersistentDataType.STRING, owner.toString());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack tagged(Material material, String name, List<String> lore, String id, int amount) {
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(amount, material.getMaxStackSize())));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(plugin.itemIdKey(), PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }
    public String itemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(plugin.itemIdKey(), PersistentDataType.STRING);
    }
    public StarType starType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(plugin.itemIdKey(), PersistentDataType.STRING);
        if (!"star".equals(id)) return null;
        return StarType.fromId(item.getItemMeta().getPersistentDataContainer().get(plugin.starTypeKey(), PersistentDataType.STRING));
    }
    public boolean is(ItemStack item, String id) { return id.equals(itemId(item)); }
    public boolean isBoundTo(ItemStack item, java.util.UUID player) {
        if (item == null || !item.hasItemMeta()) return true;
        String owner = item.getItemMeta().getPersistentDataContainer().get(plugin.ownerKey(), PersistentDataType.STRING);
        return owner == null || owner.equals(player.toString());
    }
}


// ===== Merged from items/RecipeManager.java =====

final class RecipeManager {
    private final StarSMP plugin;
    public RecipeManager(StarSMP plugin) { this.plugin = plugin; }
    public void registerAll() {
        register("compressed_dust", plugin.items().compressedDust(1), new String[]{"DDD","DDD","DDD"}, 'D', new RecipeChoice.ExactChoice(plugin.items().starDust(1)));
        register("star_fragment", plugin.items().starFragment(1), new String[]{"CC","CC"}, 'C', new RecipeChoice.ExactChoice(plugin.items().compressedDust(1)));
        register("star_core", plugin.items().starCore(1), new String[]{"DFD","FNF","DFD"}, 'D', new RecipeChoice.ExactChoice(plugin.items().starDust(1)), 'F', new RecipeChoice.ExactChoice(plugin.items().starFragment(1)), 'N', new RecipeChoice.MaterialChoice(Material.NETHER_STAR));
        Material[] ingredients = {Material.BLAZE_POWDER,Material.AMETHYST_SHARD,Material.LIGHTNING_ROD,Material.ECHO_SHARD,Material.BLUE_ICE,Material.MOSS_BLOCK,Material.GHAST_TEAR,Material.END_CRYSTAL};
        for (StarType star : StarType.values()) {
            ItemStack result = plugin.items().star(star, 1, null);
            ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, "star_" + star.id()), result);
            recipe.shape("DED","ECE","DED");
            recipe.setIngredient('D', new RecipeChoice.ExactChoice(plugin.items().starDust(1)));
            recipe.setIngredient('E', new RecipeChoice.MaterialChoice(ingredients[star.ordinal()]));
            recipe.setIngredient('C', new RecipeChoice.ExactChoice(plugin.items().starCore(1)));
            add(recipe);
        }
        ShapedRecipe catcher = new ShapedRecipe(new NamespacedKey(plugin, "star_catcher"), plugin.items().starCatcher(1));
        catcher.shape(" DE"," DR","  B"); catcher.setIngredient('D',new RecipeChoice.ExactChoice(plugin.items().starDust(1))); catcher.setIngredient('E',new RecipeChoice.MaterialChoice(Material.ENDER_EYE)); catcher.setIngredient('R',new RecipeChoice.MaterialChoice(Material.BLAZE_ROD)); catcher.setIngredient('B',new RecipeChoice.MaterialChoice(Material.NETHER_STAR)); add(catcher);
    }
    private void register(String id, ItemStack result, String[] shape, char key, RecipeChoice choice) { ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin,id), result); recipe.shape(shape); recipe.setIngredient(key,choice); add(recipe); }
    private void register(String id, ItemStack result, String[] shape, char k1, RecipeChoice c1, char k2, RecipeChoice c2, char k3, RecipeChoice c3) { ShapedRecipe recipe=new ShapedRecipe(new NamespacedKey(plugin,id),result);recipe.shape(shape);recipe.setIngredient(k1,c1);recipe.setIngredient(k2,c2);recipe.setIngredient(k3,c3);add(recipe); }
    private void add(ShapedRecipe recipe) { try { plugin.getServer().removeRecipe(recipe.getKey()); } catch (Exception ignored) {} plugin.getServer().addRecipe(recipe); }
}


// ===== Merged from listeners/EclipseListener.java =====

final class EclipseListener implements Listener {
    private final StarSMP plugin;
    private final Map<String, Long> cooldowns = new HashMap<>();
    public EclipseListener(StarSMP plugin) { this.plugin = plugin; }

    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (!plugin.items().is(p.getInventory().getItemInMainHand(), "star_eclipse")) return;
        event.setCancelled(true);
        if (!plugin.items().isBoundTo(p.getInventory().getItemInMainHand(), p.getUniqueId())) { p.sendMessage(ChatColor.RED + "This artifact belongs to another player."); return; }
        if (!allowed(p)) return;
        use(p, p.isSneaking() ? 2 : 1);
    }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (!plugin.items().is(p.getInventory().getItemInMainHand(), "star_eclipse") || !p.isSneaking()) return;
        event.setCancelled(true);
        if (!plugin.items().isBoundTo(p.getInventory().getItemInMainHand(), p.getUniqueId())) { p.sendMessage(ChatColor.RED + "This artifact belongs to another player."); return; }
        if (allowed(p)) use(p, 3);
    }
    @EventHandler public void passive(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (plugin.items().is(p.getInventory().getItemInMainHand(), "star_eclipse") && plugin.items().isBoundTo(p.getInventory().getItemInMainHand(), p.getUniqueId()) && p.getWorld().getTime() > 13000 && p.getWorld().getTime() < 23000) event.setDamage(event.getDamage() * (1.0 - plugin.getConfig().getDouble("eclipse.passive-night-damage-reduction", .10)));
    }
    private boolean allowed(Player p) {
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || plugin.getConfig().getStringList("settings.disabled-worlds").contains(p.getWorld().getName())) return false;
        return true;
    }
    private void use(Player p, int ability) {
        String key = p.getUniqueId() + ":eclipse:" + ability;
        long now = System.currentTimeMillis(); long end = cooldowns.getOrDefault(key, 0L);
        if (end > now) { p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(ChatColor.RED + "STAR ECLIPSE cooldown: " + ((end-now+999)/1000) + "s")); return; }
        boolean success;
        if (ability == 1) success = rift(p);
        else if (ability == 2) success = collapse(p);
        else success = bigBang(p);
        if (success) {
            int seconds = plugin.getConfig().getInt("eclipse.cooldowns." + ability, ability == 3 ? 600 : ability == 2 ? 120 : 45);
            cooldowns.put(key, now + Math.max(1, seconds) * 1000L);
            p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(ChatColor.LIGHT_PURPLE + "✦ STAR ECLIPSE activated · " + seconds + "s cooldown"));
        }
    }
    private boolean rift(Player p) {
        Location origin = p.getLocation(); Vector dir = origin.getDirection().setY(0).normalize(); if (dir.lengthSquared() < .01) dir = new Vector(0,0,1);
        for (double d=8; d>=1; d-=.5) { Location l=origin.clone().add(dir.clone().multiply(d)); if (l.getWorld().getWorldBorder().isInside(l) && l.getBlock().isPassable() && l.clone().add(0,1,0).getBlock().isPassable() && l.clone().subtract(0,1,0).getBlock().getType().isSolid() && l.getBlock().getType()!=org.bukkit.Material.LAVA && l.getBlock().getType()!=org.bukkit.Material.WATER) { p.teleport(l); p.getWorld().spawnParticle(Particle.PORTAL,origin.add(0,1,0),50,.5,.8,.5,.1); p.playSound(l,Sound.ENTITY_ENDERMAN_TELEPORT,1,1.3f); return true; } }
        p.sendMessage(ChatColor.RED+"No safe rift location found."); return false;
    }
    private boolean collapse(Player p) {
        Location center = p.getTargetBlockExact(20) == null ? p.getLocation().add(p.getLocation().getDirection().multiply(8)) : p.getTargetBlockExact(20).getLocation().add(.5,1,.5);
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL,center,100,2,2,2,.05); p.playSound(center,Sound.BLOCK_BEACON_DEACTIVATE,1,.7f);
        for (Entity e : center.getWorld().getNearbyEntities(center,7,7,7)) if (e instanceof LivingEntity t && !t.equals(p) && !(t instanceof Player target && !canDamage(p,target))) { Vector v=center.toVector().subtract(t.getLocation().toVector()); if (v.lengthSquared()>0) { v.normalize().multiply(.65); v.setY(Math.min(.2,v.getY())); t.setVelocity(v); } t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,50,1)); }
        return true;
    }
    private boolean bigBang(Player p) {
        Location loc = p.getLocation(); p.sendTitle(ChatColor.LIGHT_PURPLE+"BIG BANG",ChatColor.GRAY+"Cosmic energy collapsing...",5,25,5); p.playSound(loc,Sound.BLOCK_BEACON_POWER_SELECT,1,.5f);
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if (!p.isOnline() || p.isDead()) return;
            Location center = p.getLocation(); center.getWorld().spawnParticle(Particle.END_ROD,center.clone().add(0,1,0),180,4,2,4,.12); center.getWorld().spawnParticle(Particle.EXPLOSION,center.clone().add(0,1,0),2,1,1,1,.1); center.getWorld().playSound(center,Sound.ENTITY_GENERIC_EXPLODE,2,.65f);
            for(Entity e:center.getWorld().getNearbyEntities(center,8,6,8)) if(e instanceof LivingEntity t && !t.equals(p) && !(t instanceof Player target && !canDamage(p,target))) { t.damage(plugin.getConfig().getDouble("eclipse.big-bang-damage",10),p); Vector push=t.getLocation().toVector().subtract(center.toVector()); if(push.lengthSquared()>0){push.normalize().multiply(1.0);push.setY(.45);t.setVelocity(push);} t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,50,1)); }
        },30L); return true;
    }
    private boolean canDamage(Player a, Player b) { return !a.equals(b) && plugin.getConfig().getBoolean("settings.pvp-enabled",true); }
    public String cooldownStatus(Player player) {
        long now = System.currentTimeMillis();
        return ChatColor.LIGHT_PURPLE + "✦ STAR ECLIPSE" + ChatColor.DARK_GRAY + "  |  "
                + ChatColor.YELLOW + "Rift " + remaining(player.getUniqueId() + ":eclipse:1", now) + ChatColor.DARK_GRAY + "  "
                + ChatColor.AQUA + "Collapse " + remaining(player.getUniqueId() + ":eclipse:2", now) + ChatColor.DARK_GRAY + "  "
                + ChatColor.RED + "Big Bang " + remaining(player.getUniqueId() + ":eclipse:3", now);
    }

    private String remaining(String key, long now) {
        long end = cooldowns.getOrDefault(key, 0L);
        return end > now ? ChatColor.RED + (((end - now + 999) / 1000) + "s") : ChatColor.GREEN + "READY";
    }

    public void shutdown() { cooldowns.clear(); }
}


// ===== Merged from listeners/ItemProtectionListener.java =====

final class ItemProtectionListener implements Listener {
    private final StarSMP plugin;
    public ItemProtectionListener(StarSMP plugin) { this.plugin = plugin; }
    @EventHandler public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("settings.soulbound", true)) return;
        if (plugin.items().starType(event.getItemDrop().getItemStack()) == null && !plugin.items().is(event.getItemDrop().getItemStack(), "star_eclipse")) return;
        if (!plugin.items().isBoundTo(event.getItemDrop().getItemStack(), event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "This cosmic artifact belongs to another player.");
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "Legendary cosmic artifacts cannot be dropped.");
    }
}


// ===== Merged from listeners/MobDropListener.java =====

final class MobDropListener implements Listener {
    private final StarSMP plugin;
    public MobDropListener(StarSMP plugin) { this.plugin = plugin; }
    @EventHandler public void onDeath(EntityDeathEvent event) {
        if (!plugin.getConfig().getBoolean("settings.allow-star-drops", true)) return;
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof LivingEntity) || entity instanceof Player) return;
        Player killer = entity.getKiller();
        if (killer == null) return;
        String world = entity.getWorld().getName();
        if (plugin.getConfig().getStringList("settings.disabled-worlds").contains(world)) return;
        double chance;
        EntityType type = entity.getType();
        if (entity.isCustomNameVisible() || entity.getCustomName() != null) chance = plugin.getConfig().getDouble("star-dust.drops.elite", .12);
        else if (type == EntityType.ENDER_DRAGON || type == EntityType.WITHER || type == EntityType.WARDEN) chance = plugin.getConfig().getDouble("star-dust.drops.boss", .35);
        else if (entity instanceof org.bukkit.entity.Monster) chance = plugin.getConfig().getDouble("star-dust.drops.hostile", .05);
        else chance = plugin.getConfig().getDouble("star-dust.drops.passive", .01);
        int looting = killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
        chance += looting * plugin.getConfig().getDouble("star-dust.looting-bonus-per-level", .01);
        chance = Math.max(0, Math.min(chance, plugin.getConfig().getDouble("star-dust.maximum-chance", .75)));
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;
        int min = Math.max(1, plugin.getConfig().getInt("star-dust.amounts.min", 1));
        int max = Math.max(min, plugin.getConfig().getInt("star-dust.amounts.max", 2));
        int amount = ThreadLocalRandom.current().nextInt(min, max + 1);
        ItemStack dust = plugin.items().starDust(Math.min(amount, 64));
        entity.getWorld().dropItemNaturally(entity.getLocation(), dust);
        killer.sendActionBar(net.kyori.adventure.text.Component.text("✦ You found Star Dust!"));
    }
}

