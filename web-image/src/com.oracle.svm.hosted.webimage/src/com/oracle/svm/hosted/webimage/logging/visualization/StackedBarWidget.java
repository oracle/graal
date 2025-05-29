/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.hosted.webimage.logging.visualization;

import static com.oracle.svm.hosted.webimage.logging.visualization.Widget.center;
import static com.oracle.svm.hosted.webimage.logging.visualization.Widget.pad;
import static com.oracle.svm.hosted.webimage.logging.visualization.Widget.repeat;
import static com.oracle.svm.hosted.webimage.logging.visualization.Widget.trunc;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class StackedBarWidget implements Widget {
    private static final int MIN_WIDTH = 40;
    private static final Character[] MONOCHROME_BAR_GLYPH_WHEEL = {'\u2588', '\u2593', '\u2592', '\u2591'};

    private final Map<String, Number> breakdown;
    private final String unit;
    private final Number totalValue;
    private final int multiplier;
    private final Map<String, Color> colors;

    public StackedBarWidget(Map<String, Number> breakdown, Number totalValue, String unit, int multiplier, Map<String, Color> colors) {
        this.breakdown = breakdown;
        this.totalValue = totalValue;
        this.unit = unit;
        this.multiplier = multiplier;
        this.colors = colors;
    }

    @Override
    public void visualize(DrawKit kit) {
        if (kit.width() < MIN_WIDTH) {
            kit.println(trunc("(too narrow console)", kit.width()));
            kit.println();
            return;
        }

        if (breakdown.values().stream().allMatch(value -> value.doubleValue() == 0.0)) {
            kit.println(String.format("Total:  %.2f %s", totalValue.doubleValue() / multiplier, unit));
            return;
        }

        int barWidth = kit.width() - 4;

        if (breakdown.size() > barWidth) {
            kit.println(trunc("(too narrow console)", kit.width()));
            kit.println();
            return;
        }

        // Assign tile counts to categories.
        double valueSum = 0.0;
        for (Number value : breakdown.values()) {
            valueSum += value.doubleValue() / multiplier;
        }
        HashMap<String, Integer> tileCount = new HashMap<>();
        double remainingValueSum = valueSum;
        int remainingBarWidth = barWidth;
        List<Map.Entry<String, Number>> sortedEntries = breakdown.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.comparingDouble(Number::doubleValue))).collect(Collectors.toList());
        for (Map.Entry<String, Number> entry : sortedEntries) {
            double value = entry.getValue().doubleValue() / multiplier;
            int count = (int) (value / remainingValueSum * remainingBarWidth + 0.5);
            if (count == 0) {
                count = 1;
            }
            tileCount.put(entry.getKey(), count);
            remainingBarWidth -= count;
            remainingValueSum -= value;
        }

        // Draw axis.
        int tickLength = 16;
        kit.print(pad(trunc(" 0.00 " + unit, tickLength), tickLength, false));
        for (int i = tickLength; i < kit.width() - tickLength; i++) {
            kit.print(' ');
        }
        kit.print(pad(trunc(String.format("%.2f %s", valueSum, unit), tickLength), tickLength, true));
        kit.println();
        kit.print(" \u2570");
        for (int i = 0; i < kit.width() - 4; i++) {
            if (i % 4 == 0) {
                kit.print('\u252c');
            } else {
                kit.print('\u2500');
            }
        }
        kit.print("\u256f ");
        kit.println();

        // Draw stacked bar.
        if (VisualizationSupport.Options.CLIVisualizationMonochrome.getValue()) {
            visualizeBar(kit, tileCount, (entryIndex, c) -> monochromeCharacter(entryIndex));
        } else {
            visualizeBar(kit, tileCount, (entryIndex, c) -> '\u2585');
            visualizeBar(kit, tileCount, (entryIndex, c) -> '\u2580');
        }

        // Draw category labels.
        Map<String, String> legendEntries = new LinkedHashMap<>();
        visualizeCategoryLabels(kit, tileCount, legendEntries, true);
        visualizeCategoryLabels(kit, tileCount, legendEntries, false);

        // Draw legend for narrow categories.
        int entryIndex = 0;
        for (Map.Entry<String, String> entry : legendEntries.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            kit.print(' ');
            if (VisualizationSupport.Options.CLIVisualizationMonochrome.getValue()) {
                kit.print(monochromeCharacter(entryIndex));
            } else {
                kit.color(colors.get(key));
                kit.print('\u25fc');
                kit.color(Color.RESET);
            }
            kit.print(String.format(" %s (%s)", key, value));
            kit.println();
            entryIndex++;
        }

        kit.println(String.format("Total:  %.2f %s", totalValue.doubleValue() / multiplier, unit));
        kit.println(String.format("Unacc.: %.2f %s", totalValue.doubleValue() / multiplier - valueSum, unit));
    }

    private static char monochromeCharacter(Integer entryIndex) {
        return MONOCHROME_BAR_GLYPH_WHEEL[entryIndex % MONOCHROME_BAR_GLYPH_WHEEL.length];
    }

    private void visualizeCategoryLabels(DrawKit kit, HashMap<String, Integer> tileCount, Map<String, String> legendEntries, boolean drawKey) {
        for (Map.Entry<String, Number> entry : breakdown.entrySet()) {
            String key = entry.getKey();
            int count = tileCount.get(key);
            String value = String.format("%.2f %s", entry.getValue().doubleValue() / multiplier, unit);
            int minLength = Math.max(key.length(), value.length());
            if (count > minLength) {
                int remainingCount = count - minLength;
                repeat(kit, remainingCount / 2, ' ');
                kit.print(center(drawKey ? key : value, minLength));
                repeat(kit, remainingCount - remainingCount / 2, ' ');
            } else {
                repeat(kit, count, ' ');
                legendEntries.putIfAbsent(key, value);
            }
        }
        kit.println();
    }

    private void visualizeBar(DrawKit kit, HashMap<String, Integer> tileCount, BiFunction<Integer, Color, Character> barGlyph) {
        kit.print("  ");
        int entryIndex = 0;
        for (Map.Entry<String, Number> entry : breakdown.entrySet()) {
            int count = tileCount.get(entry.getKey());
            Color c = colors.get(entry.getKey());
            kit.color(c);
            for (int i = 0; i < count; i++) {
                kit.print(barGlyph.apply(entryIndex, c));
            }
            kit.color(Color.RESET);
            entryIndex++;
        }
        kit.print("  ");
        kit.println();
    }
}
