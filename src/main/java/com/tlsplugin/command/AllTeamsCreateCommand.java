package com.tlsplugin.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.LinkedHashMap;
import java.util.Map;

public class AllTeamsCreateCommand implements CommandExecutor {

    private final Map<String, String> teams = new LinkedHashMap<>();

    public AllTeamsCreateCommand() {

        teams.put("red", "󰀁");
        teams.put("blue", "󰀂");
        teams.put("green", "󰀃");
        teams.put("yellow", "󰀄");
        teams.put("admin", "󰀅");
        teams.put("pink", "󰀆");
        teams.put("grey", "󰀇");
        teams.put("purple", "󰀈");
        teams.put("orange", "󰀉");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.isOp()) {
            sender.sendMessage("§cSem permissão.");
            return true;
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        int created = 0;
        int skipped = 0;

        for (Map.Entry<String, String> entry : teams.entrySet()) {

            String teamName = entry.getKey();
            String symbol = entry.getValue();

            // verifica se já existe
            Team existingTeam = scoreboard.getTeam(teamName);

            if (existingTeam != null) {
                skipped++;
                continue;
            }

            // cria apenas se não existir
            Team newTeam = scoreboard.registerNewTeam(teamName);

            // define símbolo
            newTeam.prefix(Component.text(symbol + " "));

            created++;
        }

        sender.sendMessage("§aCriadas: §f" + created);
        sender.sendMessage("§eIgnoradas (já existiam): §f" + skipped);

        return true;
    }
}