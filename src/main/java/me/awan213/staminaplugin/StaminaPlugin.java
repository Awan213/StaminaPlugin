
package me.namamu.staminaplugin;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class StaminaPlugin extends JavaPlugin implements Listener {

    private final int MAX_STAMINA = 200;
    private final Map<UUID, Integer> stamina = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, Long> lastUsed = new HashMap<>();
    private final Map<UUID, Long> slideCooldown = new HashMap<>();
    private final Map<UUID, Long> wallRunCooldown = new HashMap<>();
    private final Map<UUID, Long> wallJumpCooldown = new HashMap<>();
    private final Map<UUID, Location> lastLocations = new HashMap<>();

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
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Location loc = player.getLocation();
        Location last = lastLocations.get(uuid);

        if (player.isSprinting() && player.isSneaking() && !player.isInWater()) {
            long lastSlide = slideCooldown.getOrDefault(uuid, 0L);
            if (System.currentTimeMillis() - lastSlide >= 2000 && stamina.get(uuid) >= 9) {
                Vector dir = loc.getDirection().normalize().multiply(1.5);
                dir.setY(0.1);
                player.setVelocity(dir);
                drainStamina(player, 9);
                slideCooldown.put(uuid, System.currentTimeMillis());
                player.getWorld().spawnParticle(Particle.CLOUD, loc, 20, 0.3, 0.1, 0.3, 0.01);
                player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
            }
        }

        // Wall run logic
        if (player.isSprinting() && player.isSneaking() && isNextToWall(player) && player.getVelocity().getY() < 0.1) {
            long lastRun = wallRunCooldown.getOrDefault(uuid, 0L);
            if (System.currentTimeMillis() - lastRun >= 1500 && stamina.get(uuid) >= MAX_STAMINA * 0.10) {
                Vector v = player.getLocation().getDirection().setY(0).normalize().multiply(0.7);
                v.setY(0.1);
                player.setVelocity(v);
                drainStamina(player, (int)(MAX_STAMINA * 0.10));
                wallRunCooldown.put(uuid, System.currentTimeMillis());
                player.getWorld().spawnParticle(Particle.SMOKE_NORMAL, player.getLocation(), 10, 0.2, 0.5, 0.2, 0.01);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 0.4f, 1.4f);
            }
        }

        lastLocations.put(uuid, loc);
    }

    @EventHandler
    public void onJump(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (isNextToWall(player) && !player.isOnGround()) {
            long lastJump = wallJumpCooldown.getOrDefault(uuid, 0L);
            if (System.currentTimeMillis() - lastJump >= 1000 && stamina.get(uuid) >= MAX_STAMINA * 0.03) {
                Vector away = player.getLocation().getDirection().multiply(-1).setY(0.8);
                player.setVelocity(away);
                drainStamina(player, (int)(MAX_STAMINA * 0.03));
                wallJumpCooldown.put(uuid, System.currentTimeMillis());
                player.getWorld().spawnParticle(Particle.CRIT, player.getLocation(), 10, 0.2, 0.2, 0.2, 0.01);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SLIME_JUMP, 1.0f, 1.2f);
                event.setCancelled(true);
            }
        }
    }

    private boolean isNextToWall(Player player) {
        Location loc = player.getLocation();
        for (Vector dir : Arrays.asList(new Vector(1, 0, 0), new Vector(-1, 0, 0), new Vector(0, 0, 1), new Vector(0, 0, -1))) {
            Block b = loc.clone().add(dir).getBlock();
            if (b.getType().isSolid()) return true;
        }
        return false;
    }

    private void drainStamina(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int current = stamina.getOrDefault(uuid, MAX_STAMINA);
        int newStamina = Math.max(0, current - amount);
        stamina.put(uuid, newStamina);
        lastUsed.put(uuid, System.currentTimeMillis());
    }
}
