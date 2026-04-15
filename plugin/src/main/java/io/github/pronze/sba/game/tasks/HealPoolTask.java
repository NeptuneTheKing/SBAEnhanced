package io.github.pronze.sba.game.tasks;

import io.github.pronze.sba.config.SBAConfig;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.screamingsandals.bedwars.Main;

public class HealPoolTask extends BaseGameTask {
  private final double radiusSquared;

  public HealPoolTask() {

    int range = SBAConfig.getInstance().node("upgrades", "heal-pool-range").getInt(15);
    this.radiusSquared = (double) range * range;
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
