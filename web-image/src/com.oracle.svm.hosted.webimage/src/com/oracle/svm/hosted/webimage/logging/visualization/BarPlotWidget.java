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

import static com.oracle.svm.hosted.webimage.logging.visualization.Widget.pad;
import static com.oracle.svm.hosted.webimage.logging.visualization.Widget.trunc;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class BarPlotWidget implements Widget {
    private static final int MIN_WIDTH = 40;

    private final Map<String, Number> categories;
    private final Optional<Number> totalValue;
    private final String unit;
    private final Function<String, Color> coloring;

    public BarPlotWidget(Map<String, Number> categories, Optional<Number> totalValue, String unit, Function<String, Color> coloring) {
        this.coloring = coloring;
        this.categories = categories;
        this.totalValue = totalValue;
        this.unit = unit;
    }

    @Override
    public void visualize(DrawKit kit) {
        if (kit.width() < MIN_WIDTH) {
            kit.println(trunc("(too narrow console)", kit.width()));
            kit.println();
            return;
        }

        int maxNumberWidth = 0;
        double valueSum = 0.0;
        double maxValue = 0.0;
        for (Number value : categories.values()) {
            valueSum += value.doubleValue();
            maxValue = Math.max(maxValue, value.doubleValue());
            String s = asString(value.doubleValue());
            maxNumberWidth = Math.max(maxNumberWidth, s.length());
        }

        int maxLabelWidth = 0;
        for (String s : categories.keySet()) {
            maxLabelWidth = Math.max(maxLabelWidth, s.length());
        }

        // Take as much as you need for the labels, but not more than 1/3 width.
        maxLabelWidth = Math.min(maxLabelWidth, kit.width() / 3);

        // Same for numbers, but 1/5.
        maxNumberWidth += 1 + unit.length();
        maxNumberWidth = Math.min(maxNumberWidth, kit.width() / 5);

        // Remainder (minus axis and spacing) is the plot area.
        int plotWidth = kit.width() - maxLabelWidth - maxNumberWidth - 4;

        // The plot -- each line is a label, axis chunk, and bar.
        for (Map.Entry<String, Number> entry : categories.entrySet()) {
            String label = entry.getKey();
            double value = entry.getValue().doubleValue();
            kit.print(pad(trunc(label, maxLabelWidth), maxLabelWidth, true));
            kit.print(" \u251c ");
            int barWidth = (int) (value / maxValue * plotWidth);
            kit.color(coloring.apply(label));
            if (barWidth > 0) {
                for (int i = 0; i < barWidth; i++) {
                    kit.print('\u2585');
                }
            } else {
                kit.print('\u2596');
            }
            kit.color(Color.RESET);
            kit.print(' ');
            kit.print(pad(trunc(asString(value), maxNumberWidth), maxNumberWidth, false));
            kit.println();
        }

        if (totalValue.isPresent()) {
            kit.println("Total:  " + trunc(asString(totalValue.get().doubleValue()), maxNumberWidth));
            kit.println("Unacc.: " + trunc(asString(totalValue.get().doubleValue() - valueSum), maxNumberWidth));
        }
    }

    private String asString(double value) {
        return String.format("%.2f %s", value, unit);
    }
}
