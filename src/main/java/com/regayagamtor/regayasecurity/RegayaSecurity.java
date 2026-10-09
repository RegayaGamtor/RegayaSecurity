package com.regayagamtor.regayasecurity;

import com.regayagamtor.regayasecurity.command.ForceSearchCommand;
import com.regayagamtor.regayasecurity.command.RegayaSecurityCommand;
import com.regayagamtor.regayasecurity.command.SearchCommand;
import com.regayagamtor.regayasecurity.config.ConfigManager;
import com.regayagamtor.regayasecurity.config.MessageManager;
import com.regayagamtor.regayasecurity.listener.CombatListener;
import com.regayagamtor.regayasecurity.listener.InventoryListener;
import com.regayagamtor.regayasecurity.listener.PlayerConnectionListener;
import com.regayagamtor.regayasecurity.listener.PlayerMovementListener;
import com.regayagamtor.regayasecurity.manager.ForceTargetManager;
import com.regayagamtor.regayasecurity.manager.InventorySyncManager;
import com.regayagamtor.regayasecurity.manager.PermissionManager;
import com.regayagamtor.regayasecurity.manager.SearchSessionManager;
import com.regayagamtor.regayasecurity.storage.AuditLogger;
import com.regayagamtor.regayasecurity.storage.ForceTargetStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RegayaSecurity - sistem penggeledahan inventory yang aman untuk server roleplay.
 * Dikembangkan oleh RegayaGamtor. Kelas ini hanya merangkai komponen.
 */
public final class RegayaSecurity extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private AuditLogger auditLogger;
    private ForceTargetManager forceTargetManager;
    private PermissionManager permissionManager;
    private SearchSessionManager sessionManager;
    private InventorySyncManager syncManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();

        messageManager = new MessageManager(this);
        messageManager.load();

        auditLogger = new AuditLogger(this);

        forceTargetManager = new ForceTargetManager(this, new ForceTargetStorage(this));
        forceTargetManager.load();

        permissionManager = new PermissionManager(forceTargetManager);
        sessionManager = new SearchSessionManager(this);
        syncManager = new InventorySyncManager(this);

        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerMovementListener(this), this);

        register("geledah", new SearchCommand(this));
        register("geledahpaksa", new ForceSearchCommand(this));
        register("regayasecurity", new RegayaSecurityCommand(this));

        sessionManager.start();
        syncManager.start();

        getLogger().info("RegayaSecurity " + getPluginMeta().getVersion() + " aktif (dev: RegayaGamtor).");
    }

    @Override
    public void onDisable() {
        if (syncManager != null) {
            syncManager.stop();
        }
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        if (auditLogger != null) {
            auditLogger.close();
        }
    }

    /** Muat ulang config.yml, messages.yml, dan data.yml tanpa memutus sesi yang sah. */
    public void reloadAll() {
        configManager.load();
        messageManager.load();
        forceTargetManager.load();
        sessionManager.restartCheckTask();
        syncManager.start();
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Perintah '" + name + "' tidak ada di plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    public ConfigManager config() {
        return configManager;
    }

    public MessageManager messages() {
        return messageManager;
    }

    public AuditLogger audit() {
        return auditLogger;
    }

    public ForceTargetManager forceTargets() {
        return forceTargetManager;
    }

    public PermissionManager permissions() {
        return permissionManager;
    }

    public SearchSessionManager sessions() {
        return sessionManager;
    }

    public InventorySyncManager sync() {
        return syncManager;
    }
}
