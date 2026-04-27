package io.github.pronze.sba;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.screamingsandals.lib.plugin.ServiceManager;
import org.screamingsandals.lib.utils.annotations.Service;
import org.screamingsandals.lib.utils.annotations.ServiceDependencies;
import org.screamingsandals.lib.utils.annotations.methods.OnPostEnable;
import org.screamingsandals.lib.utils.reflect.Reflect;

import io.github.pronze.sba.config.SBAConfig;
import io.github.pronze.sba.utils.Logger;
import lombok.Getter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

@Service
@ServiceDependencies(dependsOn = {
  SBAConfig.class
})
public class UpdateChecker {

  private static final String GITEA_API = "https://gitea.moyskleytech.com/api/v1/repos/boiscljo/SBA/releases";
  private String version;
  @Getter
  private boolean isPendingUpdate = false;
  private JavaPlugin plugin;
  private String downloadUrl;

  public UpdateChecker(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public static UpdateChecker getInstance() {
    return ServiceManager.get(UpdateChecker.class);
  }

  @OnPostEnable
  public void checkForUpdates() {
    if (SBA.isBroken()) return;
    if (SBA.getInstance().isSnapshot()) return;
    if (!SBAConfig.getInstance().shouldCheckUpdate()) return;

    new Thread(() -> {
      try {
        HttpURLConnection connection = (HttpURLConnection) new URL(GITEA_API).openConnection();
        connection.setRequestProperty("User-Agent", "SBA-Updater");
        connection.setRequestMethod("GET");

        StringBuilder jsonBuilder = new StringBuilder();
        try (Scanner scanner = new Scanner(connection.getInputStream())) {
          while (scanner.hasNextLine()) {
            jsonBuilder.append(scanner.nextLine());
          }
        }

        String json = jsonBuilder.toString();
        String[] entries = json.split("\\{");

        for (String entry: entries) {
          if (entry.contains("\"prerelease\":false") && entry.contains("\"draft\":false")) {
            int tagStart = entry.indexOf("\"tag_name\":\"") + 12;
            int tagEnd = entry.indexOf("\"", tagStart);
            version = entry.substring(tagStart, tagEnd);

            int urlStart = entry.indexOf("\"browser_download_url\":\"") + 25;
            int urlEnd = entry.indexOf("\"", urlStart);
            downloadUrl = entry.substring(urlStart, urlEnd);
            break;
          }
        }

        if (version != null && !version.equalsIgnoreCase(SBA.getInstance().getVersion())) {
          promptUpdate(version);
        } else {
          Bukkit.getLogger().info("No updates found");
        }

      } catch (Exception e) {
        Logger.error("Failed to check for updates: {}", e);
      }
    }).start();
  }

  private void promptUpdate(@NotNull String version) {
    this.version = version;
    isPendingUpdate = true;
    if (SBAConfig.getInstance().shouldWarnConsoleAboutUpdate()) {
      Bukkit.getLogger().info("§e§lTHERE IS A NEW UPDATE AVAILABLE Version: " + version);
      Bukkit.getLogger().info("Download it from here: " + downloadUrl + " or run §e/sba updateplugin");
    }
  }

  public void sendToUser(@NotNull Player player) {
    player.sendMessage("[SBA] §eTHERE IS A NEW UPDATE AVAILABLE Version: " + version);
    player.sendMessage("Download it from here: " + downloadUrl + " or run §e/sba updateplugin");
  }

  public void update(@NotNull CommandSender sender) {
    if (!isPendingUpdate || downloadUrl == null) {
      sender.sendMessage("No update available.");
      return;
    }

    new BukkitRunnable() {
      @Override
      public void run() {
        try {
          HttpURLConnection httpConnection = (HttpURLConnection) new URL(downloadUrl).openConnection();
          httpConnection.setRequestProperty("User-Agent", "SBA-Updater");

          int grabSize = 2048;
          BufferedInputStream in = new BufferedInputStream(httpConnection.getInputStream());

          File pluginFile = (File) Reflect.fastInvoke(plugin, "getFile");
          FileOutputStream fos = new FileOutputStream(pluginFile);
          BufferedOutputStream bout = new BufferedOutputStream(fos, grabSize);

          byte[] data = new byte[grabSize];
          int grab;
          while ((grab = in.read(data, 0, grabSize)) >= 0) {
            bout.write(data, 0, grab);
          }

          bout.close();
          in.close();
          fos.close();

          sender.sendMessage("Plugin JAR updated, please restart your server to receive the update.");
          Bukkit.getServer().shutdown();

        } catch (Exception ex) {
          Logger.error("Error occurred while updating: {}", ex);
          sender.sendMessage("Update failed: " + ex.getMessage());
        }
      }
    }.runTaskAsynchronously(plugin);
  }
}
