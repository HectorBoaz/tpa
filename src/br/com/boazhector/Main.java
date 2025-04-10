package br.com.boazhector;

import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    @Override
    public void onEnable() {
        // Salvar config padrão se não existir
        saveDefaultConfig();

        // Registrar comandos
        getCommand("tpa").setExecutor(new Commands(this));
        getCommand("tpaccept").setExecutor(new Commands(this));
        getCommand("tpdeny").setExecutor(new Commands(this));

        // Registrar eventos
        getServer().getPluginManager().registerEvents(new Events(this), this);

        getLogger().info("TPAPlugin foi ativado com sucesso!");
    }

    @Override
    public void onDisable() {
        getLogger().info("TPAPlugin foi desativado!");
    }
}