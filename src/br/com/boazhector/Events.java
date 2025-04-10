package br.com.boazhector;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class Events implements Listener {

    private Main plugin;

    public Events(Main plugin) {
        this.plugin = plugin;
    }

    // Evento para limpar dados de jogadores quando eles saem do servidor
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Qualquer limpeza necessária pode ser adicionada aqui
        // Se precisar acessar dados da classe Commands, seria necessário implementar um sistema
        // para compartilhar acesso a esses dados
    }
}