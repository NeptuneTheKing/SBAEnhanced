package io.github.pronze.sba.game.tasks;

import io.github.pronze.sba.config.SBAConfig;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.screamingsandals.bedwars.Main;

public class HealPoolTask extends BaseGameTask implements Listener {
  private final double radiusSquared;

  public HealPoolTask() {
    int range = SBAConfig.getInstance().node("upgrades", "heal-pool-range").getInt(7);
    this.radiusSquared = (double) range * range;

    try {
      JavaPlugin plugin = JavaPlugin.getProvidingPlugin(HealPoolTask.class);
      Bukkit.getPluginManager().registerEvents(this, plugin);
    } catch (Exception ignored) {}
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onAsyncNPCInteract(PlayerUseUnknownEntityEvent event) {
    if (event.isAsynchronous()) {
      return;
    }
  }

  @Override
  public void run() {
    if (!arena.getStorage().arePoolEnabled()) {
      return;
    }

    arena.getGame().getRunningTeams().forEach(team -> {
      if (!arena.getStorage().arePoolEnabled(team)) {
        return;
      }

      arena.getStorage().getTargetBlockLocation(team).ifPresent(targetLoc -> {
        team.getConnectedPlayers().stream()
        .filter(player -> !Main.getPlayerGameProfile(player).isSpectator)
        .forEach(player -> {
          if (targetLoc.distanceSquared(player.getLocation()) <= radiusSquared) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30, 1), true);
          }
        });
      });
    });
  }
}
