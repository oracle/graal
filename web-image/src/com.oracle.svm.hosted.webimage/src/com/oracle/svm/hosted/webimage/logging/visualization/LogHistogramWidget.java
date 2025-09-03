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

import static com.oracle.svm.hosted.webimage.logging.visualization.Widget.hardTrunc;
import static com.oracle.svm.hosted.webimage.logging.visualization.Widget.pad;
import static com.oracle.svm.hosted.webimage.logging.visualization.Widget.trunc;

import java.util.HashMap;
import java.util.Map;

/**
 * The log-histogram represents the distribution of the provided element-classes by separating them
 * into buckets, where each bucket with the index "i" contains the number of elements whose size is
 * more than 2^(i - 1) and at most 2^i.
 *
 * The buckets are displayed on the x-axis, and the element counts are represented on the y-axis.
 * Both axes are logarithmic.
 *
 * The log-histogram allows quickly seeing where the mean is, what the deviation is, and how many
 * outliers there are in the distribution. The logarithmic scale of the y-axis makes the histogram
 * sensitive to small counts, so outliers in the large buckets can be observed easily even if there
 * are only a few of them.
 */
public class LogHistogramWidget implements Widget {
    private static final char RIGHT_ARROW = '\u2b9e';
    private static final int MIN_WIDTH = 40;

    private final Map<Number, Number> classes;
    private final String unit;
    private final Color color;
    private final int height;

    public LogHistogramWidget(Map<Number, Number> classes, String unit, Color color, int height) {
        this.classes = classes;
        this.unit = unit;
        this.color = color;
        this.height = height;
        assert height > 4 : height;
    }

    @Override
    public void visualize(DrawKit kit) {
        if (kit.width() < MIN_WIDTH) {
            kit.println(trunc("(too narrow console)", kit.width()));
            kit.println();
            return;
        }

        final int yLabelWidth = 4;
        final int histogramWidth = kit.width() - yLabelWidth;
        final int tickDelta = 4;
        final int tickCount = (histogramWidth - 2 * tickDelta) / tickDelta;

        long maxCount = 1;
        long unaccounted = 0;
        Map<Number, Number> buckets = new HashMap<>();
        for (int i = 0; i <= tickCount; i++) {
            buckets.put(i, 0);
        }
        for (Map.Entry<Number, Number> entry : classes.entrySet()) {
            long sizeClass = entry.getKey().intValue();
            if (sizeClass <= 0) {
                // Only positive size classes are considered.
                continue;
            }
            long count = entry.getValue().longValue();
            maxCount = Math.max(count, maxCount);
            int bucket = (int) exponentUp(sizeClass);
            if (bucket >= tickCount) {
                unaccounted += count;
                continue;
            }
            buckets.put(bucket, buckets.get(bucket).longValue() + count);
        }

        long maxCountRounded = 1L << exponentUp(maxCount);
        int maxCountRoundedExponent = Long.numberOfTrailingZeros(maxCountRounded);
        int maxHeight = height - 3;
        Map<Integer, Double> yExponents = new HashMap<>();
        Map<Integer, Long> yIntExponents = new HashMap<>();
        Map<Integer, String> yLabels = new HashMap<>();
        yExponents.put(0, 0.0);
        yIntExponents.put(0, 0L);
        yLabels.put(0, "2^0");
        for (int y = 1; y <= maxHeight; y++) {
            long intExponent = maxCountRoundedExponent * y / maxHeight;
            yIntExponents.put(y, intExponent);
            if (intExponent > yIntExponents.get(y - 1)) {
                yLabels.put(y, "2^" + intExponent);
            } else {
                yLabels.put(y, "");
            }
            yExponents.put(y, ((double) maxCountRoundedExponent) * y / maxHeight);
        }

        // Draw the histogram body (y-axis and all the bars).
        for (int y = maxHeight; y >= 0; y--) {
            long countAtY = (long) Math.pow(2.0, yExponents.get(y));

            // Draw the y-axis label.
            kit.print(pad(yLabels.get(y), yLabelWidth, true));

            // Draw the y-axis
            kit.print('\u251c');

            // Draw the bars.
            kit.color(color);
            for (int x = 0; x < tickDelta - 2; x++) {
                kit.print(' ');
            }
            for (int x = tickDelta; x < histogramWidth - tickDelta; x += tickDelta) {
                int bucket = (x - tickDelta) / tickDelta;
                long count = buckets.get(bucket).longValue();
                if (count >= countAtY) {
                    kit.print("\u2590\u2588\u258c");
                    for (int i = 0; i < tickDelta - 3; i++) {
                        kit.print(' ');
                    }
                } else {
                    for (int i = 0; i < tickDelta; i++) {
                        kit.print(' ');
                    }
                }
            }
            kit.color(Color.RESET);

            kit.println();
        }

        // Draw x-axis.
        yLabelSpace(kit, yLabelWidth);
        kit.print('\u2514');
        for (int x = 1; x < histogramWidth - tickDelta; x++) {
            kit.print(x % tickDelta == 0 ? '\u2534' : '\u2500');
        }
        for (int x = 0; x < tickDelta - 1; x++) {
            kit.print('\u2500');
        }
        kit.print(RIGHT_ARROW);
        kit.println();
        yLabelSpace(kit, yLabelWidth);
        for (int x = 0; x < tickDelta - 1; x++) {
            kit.print(' ');
        }
        for (int x = tickDelta; x < histogramWidth - tickDelta; x += tickDelta) {
            kit.print('^');
            kit.print(pad(hardTrunc(String.valueOf((x - tickDelta) / tickDelta), tickDelta - 2), tickDelta - 2, false));
            kit.print(' ');
        }
        kit.print(hardTrunc(" " + unit, tickDelta));
        kit.println();

        if (unaccounted > 0) {
            System.out.println("Unaccounted (> 2^" + (tickCount - 1) + " " + unit + "): " + unaccounted);
        }
    }

    private static long exponentUp(long maxCount) {
        return 64 - Long.numberOfLeadingZeros(maxCount - 1);
    }

    private static void yLabelSpace(DrawKit kit, int yLabelWidth) {
        for (int x = 0; x < yLabelWidth; x++) {
            kit.print(' ');
        }
    }
}
