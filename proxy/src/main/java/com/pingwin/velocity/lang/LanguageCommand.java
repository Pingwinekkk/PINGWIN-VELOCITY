/*
 * Copyright (C) 2026 PINGWIN-VELOCITY Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.pingwin.velocity.lang;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.command.builtin.BuiltinCommandDefinition;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * Komenda {@code /jezyk [kod]} (aliasy {@code /language}, {@code /lang}): wybór języka gracza.
 *
 * <p>Teksty komendy są po polsku - dla innych języków tłumaczy je słownik, tak jak każdą inną wiadomość.</p>
 */
public final class LanguageCommand implements BuiltinCommandDefinition {

  private static final String ARG = "kod";
  private static final MiniMessage MINI = MiniMessage.miniMessage();

  private final VelocityServer server;

  public LanguageCommand(VelocityServer server) {
    this.server = server;
  }

  @Override
  public String label() {
    return "jezyk";
  }

  @Override
  public List<String> aliases() {
    return List.of("language", "lang");
  }

  @Override
  public BrigadierCommand build() {
    LiteralArgumentBuilder<CommandSource> root = BrigadierCommand.literalArgumentBuilder(label())
        .requires(source -> source instanceof Player)
        .executes(this::showMenu)
        .then(BrigadierCommand.requiredArgumentBuilder(ARG, StringArgumentType.word())
            .suggests((context, builder) -> {
              server.getLanguages().availableLanguages().forEach(builder::suggest);
              return builder.buildFuture();
            })
            .executes(this::choose));
    return new BrigadierCommand(root);
  }

  private int showMenu(CommandContext<CommandSource> context) {
    Player player = (Player) context.getSource();
    PingwinLanguages languages = server.getLanguages();
    String current = languages.language(player);
    Component menu = MINI.deserialize("<#FFFF00>➜ <dark_gray>⏸ <white>Twój język: <#FFFF00>" + name(current)
        + "<white>. Wybierz:");
    for (String code : languages.availableLanguages()) {
      String color = code.equals(current) ? "<#00C105>" : "<white>";
      menu = menu.append(MINI.deserialize(" <dark_gray>[" + "<click:run_command:'/jezyk " + code + "'>"
          + "<hover:show_text:'<gray>/jezyk " + code + "'>" + color + name(code) + "</hover></click><dark_gray>]"));
    }
    player.sendMessage(menu);
    return Command.SINGLE_SUCCESS;
  }

  private int choose(CommandContext<CommandSource> context) {
    Player player = (Player) context.getSource();
    PingwinLanguages languages = server.getLanguages();
    String code = StringArgumentType.getString(context, ARG).toLowerCase(Locale.ROOT);
    if (!languages.availableLanguages().contains(code)) {
      player.sendMessage(MINI.deserialize("<#C10017>✘ <dark_gray>⏸ <white>Nie ma takiego języka. Dostępne: <#C10017>"
          + String.join(", ", languages.availableLanguages())));
      return 0;
    }
    languages.choose(player, code);
    player.sendMessage(MINI.deserialize("<#00C105>✔ <dark_gray>⏸ <white>Ustawiono język: <#00C105>" + name(code)
        + "<white>."));
    return Command.SINGLE_SUCCESS;
  }

  private static String name(String code) {
    return switch (code) {
      case "pl" -> "Polski";
      case "en" -> "English";
      default -> code.toUpperCase(Locale.ROOT);
    };
  }
}
