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

import java.util.LinkedHashMap;
import java.util.Map;

public class PanelGroupWidget implements Widget {
    private final String title;
    private final LinkedHashMap<String, Widget> contents;

    public PanelGroupWidget(String title, LinkedHashMap<String, Widget> contents) {
        this.title = title;
        this.contents = contents;
    }

    @Override
    public void visualize(DrawKit kit) {
        InnerDrawKit innerKit = new InnerDrawKit(kit);
        innerKit.printHeader();
        for (final Map.Entry<String, Widget> entry : contents.entrySet()) {
            innerKit.printSection(entry.getKey());
            Widget widget = entry.getValue();
            widget.visualize(innerKit);
        }
        innerKit.printFooter();
    }

    class InnerDrawKit implements DrawKit {
        private final DrawKit outer;
        private final Color panelColor;
        private final Color dividerColor;
        private final Color titleColor;
        private final Color sectionColor;
        private final Color defaultColor;
        private int column;

        InnerDrawKit(DrawKit outer) {
            this.outer = outer;
            this.panelColor = new Color(60, 110, 140);
            this.dividerColor = new Color(70, 70, 70);
            this.titleColor = new Color(100, 180, 255);
            this.sectionColor = new Color(240, 240, 240);
            this.defaultColor = new Color(200, 200, 200);
            this.column = 0;
        }

        @Override
        public int width() {
            return outer.width() - 2;
        }

        @Override
        public Color color() {
            return outer.color();
        }

        @Override
        public void color(Color c) {
            outer.color(c);
            if (c == Color.RESET) {
                // The first call was still necessary, as it resets e.g. the bold status.
                // The second call applies the custom default color for inner widgets.
                outer.color(defaultColor);
            }
        }

        @Override
        public void print(char c) {
            printBorderAtZero();
            if (c == NEWLINE) {
                finishLine();
                return;
            }
            if (column < width()) {
                column++;
            }
            outer.print(c);
        }

        @Override
        public void print(String s) {
            printBorderAtZero();
            final String[] parts = s.split(String.valueOf(NEWLINE));
            boolean first = true;
            for (final String part : parts) {
                if (first) {
                    first = false;
                } else {
                    finishLine();
                    printBorderAtZero();
                }
                outer.print(part);
                column += part.length();
            }
        }

        @Override
        public void println(String s) {
            print(s);
            finishLine();
        }

        public void printHeader() {
            outer.color(panelColor);
            printPanelHeading(title, titleColor, TOP_LEFT_CORNER, TOP_RIGHT_CORNER, panelColor, LEFT_T_BAR, RIGHT_T_BAR, panelColor);
            outer.color(Color.RESET);
        }

        public void printSection(String name) {
            printPanelHeading(name, sectionColor, RIGHT_T_BAR, LEFT_T_BAR, panelColor, HORIZONTAL_BORDER, HORIZONTAL_BORDER, dividerColor);
        }

        private void printPanelHeading(String label, Color labelColor, char leftSide, char rightSide, Color sideColor, char labelLeft, char labelRight, Color barColor) {
            int width = width();
            if (width < 2) {
                return;
            }
            outer.color(sideColor);
            outer.print(leftSide);
            outer.color(barColor);
            int afterTitle = 0;
            if (width > 4 + label.length()) {
                outer.print(HORIZONTAL_BORDER);
                outer.print(labelLeft);
                Color previousColor = outer.color();
                outer.color(labelColor);
                outer.color(Color.BOLD);
                outer.print(label);
                outer.color(Color.RESET);
                outer.color(previousColor);
                outer.print(labelRight);
                outer.print(HORIZONTAL_BORDER);
                afterTitle = 4 + label.length();
            }
            for (int i = afterTitle; i < width; i++) {
                outer.print(HORIZONTAL_BORDER);
            }
            outer.color(sideColor);
            outer.print(rightSide);
            outer.println();
        }

        private void printBorderAtZero() {
            if (column == 0) {
                outer.color(panelColor);
                outer.print(VERTICAL_BORDER);
                // The color must be reset to the default color of inner widgets.
                // We therefore call on this, instead of on outer.
                this.color(Color.RESET);
            }
        }

        private void finishLine() {
            int width = width();
            for (; column < width; column++) {
                outer.print(' ');
            }
            outer.color(panelColor);
            outer.print(VERTICAL_BORDER);
            outer.color(Color.RESET);
            outer.println();
            column = 0;
        }

        public void printFooter() {
            int width = width();
            if (width < 2) {
                return;
            }
            outer.color(panelColor);
            outer.print(BOTTOM_LEFT_CORNER);
            for (int i = 0; i < width; i++) {
                outer.print(HORIZONTAL_BORDER);
            }
            outer.print(BOTTOM_RIGHT_CORNER);
            outer.println();
            outer.color(Color.RESET);
        }
    }
}
