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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEventSource;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Słownik jednego języka: pary "tekst po polsku" -&gt; "tłumaczenie".
 *
 * <p>Tłumaczenie działa na pojedynczych fragmentach tekstu komponentu, więc kolory i formatowanie
 * zostają. Dłuższe frazy mają pierwszeństwo przed krótszymi.</p>
 */
public final class Dictionary {

  private final Map<String, String> phrases;
  private final @Nullable Pattern pattern;

  /**
   * Tworzy słownik z par fraz.
   *
   * @param phrases frazy źródłowe i ich tłumaczenia
   */
  public Dictionary(Map<String, String> phrases) {
    this.phrases = new HashMap<>(phrases);
    List<String> keys = new ArrayList<>(phrases.keySet());
    keys.removeIf(String::isEmpty);
    keys.sort(Comparator.comparingInt(String::length).reversed());
    this.pattern = keys.isEmpty() ? null
        : Pattern.compile(keys.stream().map(Pattern::quote).collect(Collectors.joining("|")));
  }

  public int size() {
    return phrases.size();
  }

  /**
   * Tłumaczy komponent razem z dziećmi, argumentami i tekstem po najechaniu.
   *
   * @param component komponent do przetłumaczenia
   * @return przetłumaczony komponent (ten sam obiekt, gdy nic się nie zmieniło)
   */
  public Component translate(Component component) {
    if (pattern == null) {
      return component;
    }
    Component result = component;
    if (result instanceof TextComponent text) {
      String translated = translate(text.content());
      if (!translated.equals(text.content())) {
        result = text.content(translated);
      }
    } else if (result instanceof TranslatableComponent translatable && !translatable.arguments().isEmpty()) {
      result = translatable.arguments(translatable.arguments().stream()
          .map(argument -> (net.kyori.adventure.text.ComponentLike) translate(argument.asComponent()))
          .toList());
    }

    HoverEvent<?> hover = result.hoverEvent();
    if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT) {
      Component hoverText = (Component) hover.value();
      Component translatedHover = translate(hoverText);
      if (translatedHover != hoverText) {
        result = result.hoverEvent((HoverEventSource<Component>) HoverEvent.showText(translatedHover));
      }
    }

    List<Component> children = result.children();
    if (!children.isEmpty()) {
      List<Component> translatedChildren = new ArrayList<>(children.size());
      boolean changed = false;
      for (Component child : children) {
        Component translatedChild = translate(child);
        changed |= translatedChild != child;
        translatedChildren.add(translatedChild);
      }
      if (changed) {
        result = result.children(translatedChildren);
      }
    }
    return result;
  }

  /**
   * Tłumaczy zwykły tekst.
   *
   * @param text tekst
   * @return tekst z podmienionymi frazami
   */
  public String translate(String text) {
    if (pattern == null || text.isEmpty()) {
      return text;
    }
    Matcher matcher = pattern.matcher(text);
    if (!matcher.find()) {
      return text;
    }
    StringBuilder out = new StringBuilder(text.length() + 16);
    do {
      matcher.appendReplacement(out, Matcher.quoteReplacement(phrases.get(matcher.group())));
    } while (matcher.find());
    matcher.appendTail(out);
    return out.toString();
  }
}
