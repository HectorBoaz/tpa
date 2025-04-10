package br.com.boazhector;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class Commands implements CommandExecutor {

    private Main plugin;
    private Map<UUID, UUID> tpaRequests = new HashMap<>(); // Armazena quem pediu TPA para quem
    private Map<UUID, Instant> cooldowns = new HashMap<>(); // Cooldown para pedidos de TPA
    private Map<UUID, Location> playerLocations = new HashMap<>(); // Localização para verificar movimento
    private Map<UUID, BukkitTask> countdownTasks = new HashMap<>(); // Tasks de contagem regressiva

    private final int COOLDOWN_SECONDS = 60;
    private final int TELEPORT_DELAY_SECONDS = 5;

    public Commands(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Este comando só pode ser executado por jogadores.");
            return true;
        }

        Player player = (Player) sender;
        String commandName = cmd.getName().toLowerCase();

        switch (commandName) {
            case "tpa":
                return handleTpaCommand(player, args);
            case "tpaccept":
                return handleTpAcceptCommand(player);
            case "tpdeny":
                return handleTpDenyCommand(player);
            default:
                return false;
        }
    }

    private boolean handleTpaCommand(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Use /tpa <jogador>.");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);

        if (target == null) {
            player.sendMessage(ChatColor.RED + "Jogador não encontrado ou offline.");
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(ChatColor.RED + "Você não pode mandar tpa para si mesmo...");
            return true;
        }

        // Verificar cooldown
        if (cooldowns.containsKey(player.getUniqueId())) {
            Instant lastRequest = cooldowns.get(player.getUniqueId());
            long secondsLeft = COOLDOWN_SECONDS - Duration.between(lastRequest, Instant.now()).getSeconds();

            if (secondsLeft > 0) {
                player.sendMessage(ChatColor.RED + "Espere um pouco antes de tentar novamente " +
                        ChatColor.DARK_RED + "Cooldown " + secondsLeft + "s.");
                return true;
            }
        }

        // Enviar pedido de TPA
        player.sendMessage(ChatColor.GREEN + "Pedido de teleporte enviado!");

        target.sendMessage("");
        target.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.GREEN + " quer teletransportar para você!");
        target.sendMessage("");

        // Criar botões clicáveis
        TextComponent acceptButton = new TextComponent("[Aceitar] ");
        acceptButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        acceptButton.setBold(true);
        acceptButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept"));
        acceptButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("Clique para aceitar o pedido")));

        TextComponent denyButton = new TextComponent("[Negar]");
        denyButton.setColor(net.md_5.bungee.api.ChatColor.RED);
        denyButton.setBold(true);
        denyButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny"));
        denyButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("Clique para negar o pedido")));

        TextComponent message = new TextComponent("           ");
        message.addExtra(acceptButton);
        message.addExtra("");
        message.addExtra(denyButton);

        target.spigot().sendMessage(message);
        target.sendMessage("");

        // Registrar pedido e cooldown
        tpaRequests.put(target.getUniqueId(), player.getUniqueId());
        cooldowns.put(player.getUniqueId(), Instant.now());

        return true;
    }

    private boolean handleTpAcceptCommand(Player player) {
        if (!tpaRequests.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Você não tem pedidos de tpa no momento.");
            return true;
        }

        UUID requesterUUID = tpaRequests.get(player.getUniqueId());
        Player requester = Bukkit.getPlayer(requesterUUID);

        if (requester == null || !requester.isOnline()) {
            player.sendMessage(ChatColor.RED + "O jogador que solicitou o TPA não está mais online.");
            tpaRequests.remove(player.getUniqueId());
            return true;
        }

        // Armazenar localização inicial do jogador
        playerLocations.put(requesterUUID, requester.getLocation());

        // Informar aos jogadores sobre o tempo de espera
        player.sendMessage(ChatColor.GREEN + "Você aceitou o pedido de teleporte! " +
                ChatColor.YELLOW + "Aguarde " + TELEPORT_DELAY_SECONDS + " segundos sem se mover...");
        requester.sendMessage(ChatColor.GREEN + player.getName() + " aceitou seu pedido de teleporte! " +
                ChatColor.YELLOW + "Aguardando " + TELEPORT_DELAY_SECONDS + " segundos, não se mova...");

        // Iniciar contagem regressiva
        BukkitTask task = new BukkitRunnable() {
            private int secondsLeft = TELEPORT_DELAY_SECONDS;

            @Override
            public void run() {
                // Verificar se o jogador ainda está online
                if (!requester.isOnline()) {
                    player.sendMessage(ChatColor.RED + "O jogador que solicitou o TPA saiu do servidor. Teleporte cancelado.");
                    cleanup(player, requester);
                    this.cancel();
                    return;
                }

                // Verificar se o jogador se moveu
                if (!requester.getLocation().getWorld().equals(playerLocations.get(requesterUUID).getWorld()) ||
                        requester.getLocation().distanceSquared(playerLocations.get(requesterUUID)) > 0.1) {
                    requester.sendMessage(ChatColor.RED + "Você se moveu! Teleporte cancelado.");
                    player.sendMessage(ChatColor.RED + requester.getName() + " se moveu! Teleporte cancelado.");
                    cleanup(player, requester);
                    this.cancel();
                    return;
                }

                // Atualizar o jogador sobre o tempo restante
                // Usando método compatível com várias versões do Spigot
                requester.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(ChatColor.GREEN + "Teleportando em " + secondsLeft + " segundos..."));

                if (secondsLeft <= 0) {
                    // Teleportar o jogador
                    requester.teleport(player);
                    player.sendMessage(ChatColor.GREEN + "Teleporte realizado com sucesso!");
                    requester.sendMessage(ChatColor.GREEN + "Você foi teleportado para " + player.getName() + "!");
                    cleanup(player, requester);
                    this.cancel();
                }

                secondsLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L); // Executar a cada segundo (20 ticks)

        countdownTasks.put(requesterUUID, task);

        return true;
    }

    private boolean handleTpDenyCommand(Player player) {
        if (!tpaRequests.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Você não tem pedidos de tpa no momento.");
            return true;
        }

        UUID requesterUUID = tpaRequests.get(player.getUniqueId());
        Player requester = Bukkit.getPlayer(requesterUUID);

        player.sendMessage(ChatColor.RED + "Pedido de teleporte negado.");

        if (requester != null && requester.isOnline()) {
            requester.sendMessage(ChatColor.RED + player.getName() + " negou seu pedido de teleporte.");
        }

        tpaRequests.remove(player.getUniqueId());

        return true;
    }

    private void cleanup(Player target, Player requester) {
        UUID targetUUID = target.getUniqueId();
        UUID requesterUUID = requester.getUniqueId();

        tpaRequests.remove(targetUUID);
        playerLocations.remove(requesterUUID);

        if (countdownTasks.containsKey(requesterUUID)) {
            BukkitTask task = countdownTasks.get(requesterUUID);
            if (task != null) {
                task.cancel();
            }
            countdownTasks.remove(requesterUUID);
        }
    }
}