package io.github.pronze.sba.listener;

import io.github.pronze.sba.SBA;
import io.github.pronze.sba.config.SBAConfig;
import io.github.pronze.sba.service.AntiCheatIntegration;
import io.github.pronze.sba.utils.Logger;
import org.bukkit.GameMode;
import org.bukkit.entity.Explosive;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.lib.impl.bukkit.utils.Version;
import org.screamingsandals.lib.utils.annotations.Service;
import org.screamingsandals.lib.utils.annotations.methods.OnPostEnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class ExplosionVelocityControlListener implements Listener {
  private final Map < Player, String > playerJumpType = new HashMap < > ();
  private final Map < Player, BukkitTask > explosionAffectedTasks = new HashMap < > ();

  @OnPostEnable
  public void postEnable() {
    if (SBA.isBroken()) return;
    SBA.getInstance().registerListener(this);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onPlayerDamage(EntityDamageEvent event) {
    if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
      final
      var entity = event.getEntity();
      if (entity instanceof Player) {
        final
        var player = (Player) entity;

        if (playerJumpType.containsKey(player)) {
          String configPath = playerJumpType.get(player);

          double damageValue = SBAConfig.getInstance()
            .node(configPath, "fall-damage")
            .getDouble(3.0D);

          event.setDamage(damageValue);

          Logger.trace("Landing " + configPath + " from fall damage", player);
          endTntJump(player);
        }
      }
    }
  }

  private void endTntJump(Player player) {
    BukkitTask potentialTask = explosionAffectedTasks.get(player);
    if (potentialTask != null) {
      potentialTask.cancel();
    }
    explosionAffectedTasks.remove(player);
    playerJumpType.remove(player);

    AntiCheatIntegration.getInstance().tntJumpLanding(player);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onExplode(EntityExplodeEvent event) {
    final
    var explodedEntity = event.getEntity();

    if (explodedEntity instanceof Explosive) {
      if (Version.isVersion(1, 20, 3)) {
        var entityKey = explodedEntity.getType().getKey();
        if ("minecraft".equals(entityKey.getNamespace()) && ("wind_charge".equals(entityKey.getKey()) || "breeze_wind_charge".equals(entityKey.getKey()))) {
          return;

        }
      }

      String configPath = (explodedEntity instanceof Fireball) ? "fireball-jumping" : "tnt-jumping";

      final
      var detectionDistance = SBAConfig.getInstance().node(configPath, "detection-distance")
        .getDouble(5.0D);

      explodedEntity.getWorld()
        .getNearbyEntities(explodedEntity.getLocation(), detectionDistance, detectionDistance, detectionDistance)
        .stream()
        .filter(entity -> !entity.equals(explodedEntity))
        .forEach(entity -> {
          Vector vector = entity.getLocation().clone().toVector()
          .subtract(explodedEntity.getLocation().clone()
            .add(0, SBAConfig.getInstance().node(configPath, "acceleration-y").getDouble(1.0), 0)
            .toVector())
          .normalize();

          vector.setY(vector.getY() / SBAConfig.getInstance().node(configPath, "reduce-y").getDouble(2.0));

          if (!Double.isFinite(vector.getY())) vector.setY(0);
          if (!Double.isFinite(vector.getX())) vector.setX(0);
          if (!Double.isFinite(vector.getZ())) vector.setZ(0);

          vector.multiply(SBAConfig.getInstance().node(configPath, "launch-multiplier").getDouble(4.0));

          if (entity instanceof Player) {
            final
            var player = (Player) entity;
            if (player.getGameMode() == GameMode.SPECTATOR || !Main.isPlayerInGame(player)) {
              return;
            }

            AntiCheatIntegration.getInstance().beginTntJump(player);
            player.setVelocity(vector.add(player.getVelocity()));

            playerJumpType.put(player, configPath);
            explosionAffectedTasks.put(player, startTask(player));
            return;
          }

          if (Main.getInstance().isEntityInGame(entity)) {
            entity.setVelocity(vector);
          }
        });
    }
  }

  public BukkitTask startTask(Player player) {
    BukkitTask previousTask = explosionAffectedTasks.get(player);
    if (previousTask != null)
      previousTask.cancel();
    return new BukkitRunnable() {
      boolean onGround = false;
      int count = 0;

      @Override
      public void run() {
        if (player.isOnGround()) {
          onGround = true;
        }
        if (onGround) {
          count++;
        }
        if (count > 3) {
          this.cancel();
          Logger.trace("Landing tnt jump from being on the ground for 1.5sec", player);

          endTntJump(player);
        }
      }
    }.runTaskTimer(SBA.getPluginInstance(), 20, 10);
  }
}
