
package me.namamu.staminaplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.UUID;

public class StaminaPlugin extends JavaPlugin implements Listener {

    private final int MAX_STAMINA = 200;
    private final HashMap<UUID, Integer> stamina = new HashMap<>();
    private final HashMap<UUID, BossBar> bossBars = new HashMap<>();
    private final HashMap<UUID, Long> lastUsed = new HashMap<>();
    private final HashMap<UUID, Long> lastSprintDrain = new HashMap<>();
    private final HashMap<UUID, Location> lastLocations = new HashMap<>();
    private final HashMap<UUID, Long> slideCooldown = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    stamina.putIfAbsent(uuid, MAX_STAMINA);
                    bossBars.putIfAbsent(uuid, Bukkit.createBossBar("Stamina", BarColor.GREEN, BarStyle.SEGMENTED_20));

                    BossBar bar = bossBars.get(uuid);
                    bar.addPlayer(player);

                    long last = lastUsed.getOrDefault(uuid, 0L);
                    if (System.currentTimeMillis() - last >= 2000) {
                        int current = stamina.get(uuid);
                        if (current < MAX_STAMINA) {
                            stamina.put(uuid, Math.min(current + 2, MAX_STAMINA));
                        }
                    }

                    int currentStamina = stamina.get(uuid);
                    double percent = (double) currentStamina / MAX_STAMINA;
                    bar.setProgress(percent);
                }
            }
        }.runTaskTimer(this, 0, 5);
    }

    @EventHandler
    public void onSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (event.isSprinting()) {
            lastSprintDrain.put(uuid, System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        stamina.putIfAbsent(uuid, MAX_STAMINA);

        // Detect jump
        Location lastLoc = lastLocations.get(uuid);
        if (lastLoc != null && player.getLocation().getY() > lastLoc.getY() && !player.isOnGround()) {
            drainStamina(player, 5);
        }
        lastLocations.put(uuid, player.getLocation());

        // Sprint cost (every 2s)
        if (player.isSprinting()) {
            long last = lastSprintDrain.getOrDefault(uuid, 0L);
            if (System.currentTimeMillis() - last >= 2000) {
                drainStamina(player, 3);
                lastSprintDrain.put(uuid, System.currentTimeMillis());
            }
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Detect slide (only if sprinting)
        if (event.isSneaking() && player.isSprinting()) {
            long lastSlide = slideCooldown.getOrDefault(uuid, 0L);
            if (System.currentTimeMillis() - lastSlide >= 2000) {
                int current = stamina.getOrDefault(uuid, MAX_STAMINA);

                // Check if player is in water or touching water block
                Block feetBlock = player.getLocation().getBlock();
                if (player.isInWater() || feetBlock.getType().toString().contains("WATER")) {
                    player.sendMessage("§cYou can't slide in water!");
                    return;
                }

                if (current >= 9) {
                    // Do slide
                    Vector direction = player.getLocation().getDirection().normalize().multiply(1.5);
                    direction.setY(0.1);
                    player.setVelocity(direction);
                    drainStamina(player, 9);
                    slideCooldown.put(uuid, System.currentTimeMillis());

                    // Add particle and sound
                    player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 20, 0.3, 0.1, 0.3, 0.01);
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
                } else {
                    player.sendMessage("§cNot enough stamina to slide!");
                }
            }
        }
    }

    private void drainStamina(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int current = stamina.getOrDefault(uuid, MAX_STAMINA);
        int newStamina = Math.max(current - amount, 0);
        stamina.put(uuid, newStamina);
        lastUsed.put(uuid, System.currentTimeMillis());

        if (newStamina <= 0) {
            player.setSprinting(false);
        }
    }
}
