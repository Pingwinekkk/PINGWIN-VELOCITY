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

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.proxy.VelocityServer;
import com.velocitypowered.proxy.plugin.virtual.VelocityVirtualPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Języki graczy w PINGWIN-VELOCITY.
 *
 * <p>Teksty na serwerze są po polsku ({@link #BASE}). Dla innych języków proxy podmienia frazy ze słownika
 * {@code pingwin-lang/<kod>.toml} we wszystkich wiadomościach wysyłanych do gracza. Wybór gracza jest zapisany w
 * {@code pingwin-lang/players.json}; bez wyboru język wynika z ustawień klienta (polski klient = pl, inny = en).
 * Aktualny język trafia też na serwer gracza kanałem {@value #CHANNEL_ID}.</p>
 */
public final class PingwinLanguages {

  /** Język, w którym są pisane teksty serwera. */
  public static final String BASE = "pl";
  /** Kanał plugin message, którym serwery dostają język gracza. */
  public static final String CHANNEL_ID = "pingwin:lang";

  private static final Logger LOGGER = LogManager.getLogger(PingwinLanguages.class);
  private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(CHANNEL_ID);
  private static final String FALLBACK_FOREIGN = "en";

  private final VelocityServer server;
  private final Path directory = Path.of("pingwin-lang");
  private final Path playersFile = directory.resolve("players.json");
  private final Gson gson = new Gson();
  private final Map<String, Dictionary> dictionaries = new ConcurrentHashMap<>();
  private final Map<UUID, String> chosen = new ConcurrentHashMap<>();
  private boolean listenersRegistered;

  public PingwinLanguages(VelocityServer server) {
    this.server = server;
  }

  /**
   * Wczytuje słowniki i zapisane wybory graczy. Przy pierwszym starcie tworzy domyślny słownik angielski.
   */
  public void load() {
    try {
      Files.createDirectories(directory);
      Path english = directory.resolve(FALLBACK_FOREIGN + ".toml");
      if (Files.notExists(english)) {
        try (InputStream in = PingwinLanguages.class.getResourceAsStream("/default-lang-en.toml")) {
          if (in != null) {
            Files.copy(in, english);
          }
        }
      }
      dictionaries.clear();
      try (Stream<Path> files = Files.list(directory)) {
        for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".toml")).toList()) {
          String code = file.getFileName().toString().replace(".toml", "").toLowerCase(Locale.ROOT);
          if (!code.equals(BASE)) {
            dictionaries.put(code, readDictionary(file));
          }
        }
      }
      chosen.clear();
      if (Files.exists(playersFile)) {
        try (Reader reader = Files.newBufferedReader(playersFile, StandardCharsets.UTF_8)) {
          Map<String, String> stored = gson.fromJson(reader, new TypeToken<Map<String, String>>() { }.getType());
          if (stored != null) {
            stored.forEach((uuid, code) -> chosen.put(UUID.fromString(uuid), code));
          }
        }
      }
      LOGGER.info("PINGWIN-VELOCITY: języki {} (słowniki: {})", availableLanguages(),
          dictionaries.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue().size()).toList());
    } catch (IOException | RuntimeException e) {
      LOGGER.error("PINGWIN-VELOCITY: nie udało się wczytać języków z folderu pingwin-lang/", e);
    }

    if (!listenersRegistered) {
      server.getChannelRegistrar().register(CHANNEL);
      server.getEventManager().register(VelocityVirtualPlugin.INSTANCE, ServerPostConnectEvent.class,
          event -> sendToServer(event.getPlayer()));
      listenersRegistered = true;
    }
  }

  private static Dictionary readDictionary(Path file) throws IOException {
    Map<String, String> phrases = new LinkedHashMap<>();
    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      Config config = new TomlParser().parse(reader);
      List<Config> entries = config.getOrElse("phrase", Collections.emptyList());
      for (Config entry : entries) {
        String from = entry.get("from");
        String to = entry.get("to");
        if (from != null && to != null && !from.isEmpty()) {
          phrases.put(from, to);
        }
      }
    }
    return new Dictionary(phrases);
  }

  /**
   * Zwraca kody dostępnych języków (zawsze z językiem bazowym).
   *
   * @return posortowane kody języków
   */
  public Set<String> availableLanguages() {
    Set<String> languages = new TreeSet<>(dictionaries.keySet());
    languages.add(BASE);
    return languages;
  }

  /**
   * Język gracza: jego wybór, a bez wyboru język klienta.
   *
   * @param player gracz
   * @return kod języka
   */
  public String language(Player player) {
    String code = chosen.get(player.getUniqueId());
    if (code != null && (code.equals(BASE) || dictionaries.containsKey(code))) {
      return code;
    }
    Locale locale = player.getEffectiveLocale();
    if (locale == null || locale.getLanguage().equals(BASE)) {
      return BASE;
    }
    String clientLanguage = locale.getLanguage().toLowerCase(Locale.ROOT);
    if (dictionaries.containsKey(clientLanguage)) {
      return clientLanguage;
    }
    return dictionaries.containsKey(FALLBACK_FOREIGN) ? FALLBACK_FOREIGN : BASE;
  }

  /**
   * Słownik dla gracza albo null, gdy gracz ma język bazowy.
   *
   * @param player gracz
   * @return słownik lub null
   */
  public @Nullable Dictionary dictionary(Player player) {
    String code = language(player);
    return code.equals(BASE) ? null : dictionaries.get(code);
  }

  /**
   * Zapisuje wybór języka gracza i wysyła go na jego serwer.
   *
   * @param player gracz
   * @param code kod języka (musi być w {@link #availableLanguages()})
   */
  public void choose(Player player, String code) {
    chosen.put(player.getUniqueId(), code);
    save();
    sendToServer(player);
  }

  private synchronized void save() {
    Map<String, String> stored = new LinkedHashMap<>();
    chosen.forEach((uuid, code) -> stored.put(uuid.toString(), code));
    try {
      Files.writeString(playersFile, gson.toJson(stored), StandardCharsets.UTF_8);
    } catch (IOException e) {
      LOGGER.warn("PINGWIN-VELOCITY: nie udało się zapisać pingwin-lang/players.json", e);
    }
  }

  private void sendToServer(Player player) {
    player.getCurrentServer().ifPresent(connection ->
        connection.sendPluginMessage(CHANNEL, language(player).getBytes(StandardCharsets.UTF_8)));
  }
}
