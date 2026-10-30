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

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.packet.BossBarPacket;
import com.velocitypowered.proxy.protocol.packet.DisconnectPacket;
import com.velocitypowered.proxy.protocol.packet.HeaderAndFooterPacket;
import com.velocitypowered.proxy.protocol.packet.chat.ComponentHolder;
import com.velocitypowered.proxy.protocol.packet.chat.SystemChatPacket;
import com.velocitypowered.proxy.protocol.packet.chat.legacy.LegacyChatPacket;
import com.velocitypowered.proxy.protocol.packet.title.GenericTitlePacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Tłumaczy teksty w pakietach wysyłanych do gracza (czat, actionbar, title, bossbar, tablista, kick)
 * według słownika jego języka. Stoi w kanale klienta tuż przed enkoderem pakietów, więc obejmuje
 * wiadomości z proxy i ze wszystkich serwerów.
 */
public final class TranslatingEncoder extends ChannelOutboundHandlerAdapter {

  /** Nazwa handlera w pipeline Netty. */
  public static final String NAME = "pingwin-translator";
  private static final Logger LOGGER = LogManager.getLogger(TranslatingEncoder.class);

  private final PingwinLanguages languages;
  private final ConnectedPlayer player;

  public TranslatingEncoder(PingwinLanguages languages, ConnectedPlayer player) {
    this.languages = languages;
    this.player = player;
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
    Object out = msg;
    try {
      Dictionary dictionary = languages.dictionary(player);
      if (dictionary != null) {
        out = translate(dictionary, msg);
      }
    } catch (RuntimeException e) {
      // Tłumaczenie nigdy nie może zgubić pakietu - w razie błędu idzie oryginał.
      LOGGER.debug("PINGWIN-VELOCITY: nie udało się przetłumaczyć {}", msg.getClass().getSimpleName(), e);
      out = msg;
    }
    ctx.write(out, promise);
  }

  private Object translate(Dictionary dictionary, Object msg) {
    ProtocolVersion version = player.getProtocolVersion();
    if (msg instanceof SystemChatPacket chat) {
      ComponentHolder holder = translate(dictionary, chat.getComponent(), version);
      return holder == chat.getComponent() ? msg : new SystemChatPacket(holder, chat.getType());
    }
    if (msg instanceof GenericTitlePacket title) {
      title.setComponent(translate(dictionary, title.getComponent(), version));
      return msg;
    }
    if (msg instanceof BossBarPacket bossBar) {
      bossBar.setName(translate(dictionary, bossBar.getName(), version));
      return msg;
    }
    if (msg instanceof HeaderAndFooterPacket tab) {
      ComponentHolder header = translate(dictionary, tab.getHeader(), version);
      ComponentHolder footer = translate(dictionary, tab.getFooter(), version);
      return header == tab.getHeader() && footer == tab.getFooter() ? msg : new HeaderAndFooterPacket(header, footer);
    }
    if (msg instanceof DisconnectPacket disconnect) {
      disconnect.setReason(translate(dictionary, disconnect.getReason(), version));
      return msg;
    }
    if (msg instanceof LegacyChatPacket legacy && legacy.getMessage() != null) {
      Component component = GsonComponentSerializer.gson().deserialize(legacy.getMessage());
      Component translated = dictionary.translate(component);
      if (translated != component) {
        legacy.setMessage(GsonComponentSerializer.gson().serialize(translated));
      }
      return msg;
    }
    return msg;
  }

  private static @Nullable ComponentHolder translate(Dictionary dictionary, @Nullable ComponentHolder holder,
                                                     ProtocolVersion version) {
    if (holder == null) {
      return null;
    }
    Component original = holder.getComponent();
    Component translated = dictionary.translate(original);
    return translated == original ? holder : new ComponentHolder(version, translated);
  }
}
