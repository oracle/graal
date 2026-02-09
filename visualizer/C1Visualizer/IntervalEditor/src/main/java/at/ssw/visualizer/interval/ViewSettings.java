/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package at.ssw.visualizer.interval;

import at.ssw.visualizer.model.interval.ChildInterval;
import at.ssw.visualizer.model.interval.UsePosition;
import java.awt.Color;
import java.awt.Font;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates display parameters for the interval visualization.
 *
 * @author Christian Wimmer
 */
public class ViewSettings {
    public static final int SMALL = 0;
    public static final int MEDIUM = 1;
    public static final int LARGE = 2;

    // horizontal size
    private final int[] _colWidth = {2, 4, 8};
    private final int[] _lightGridX = {-1, 10, 2};
    private final int[] _darkGridX = {-1, -1, 10};
    private final int[] _textGridX = {40, 20, 10};
    private final int[] _thickLineWidth = {2, 2, 3};

    // vertical size
    private final int[] _rowHeight = {3, 6, 14};
    private final int[] _lightGridY = {8, 4, 1};
    private final int[] _darkGridY = {-1, -1, 10};
    private final int[] _textGridY = {8, 4, 1};
    private final int[] _barSeparation = {0, 0, 1};

    private final boolean[][] _showIntervalText = {{false, false, false}, {false, false, false}, {false, true, true}};

    private static final Color DEFAULT_INT_COLOR = new Color(255, 255, 102);

    /** current size settings */
    public int hsize;
    public int vsize;


    public int colWidth;
    public int rowHeight;

    /** space above and below each interval bar */
    public int barSeparation;

    /** width of block-separator lines, use-positions and indentation of ranges */
    public int thickLineWidth;

    public int lightGridX;
    public int darkGridX;
    public int textGridX;
    public int lightGridY;
    public int darkGridY;
    public int textGridY;

    boolean showIntervalText;

    public Color lightGridColor;
    public Color darkGridColor;
    public Color blockGridColor;
    public Color textColor;

    public Font textFont;

    private Color usePosColorL;
    private Color usePosColorS;
    private Color usePosColorM;
    private Color usePosColorOther;
    private Map<String, Color> typeIntervalColors;
    private Color stackIntervalColor;


    public ViewSettings() {
        lightGridColor = Color.GRAY;
        darkGridColor = Color.DARK_GRAY;
        blockGridColor = Color.DARK_GRAY;
        textColor = Color.BLACK;

        usePosColorL = new Color(192, 64, 64);
        usePosColorS = new Color(255, 0, 192);
        usePosColorM = new Color(255, 0, 0);
        usePosColorOther = new Color(0, 255, 255);

        stackIntervalColor = new Color(255, 192, 64);
        typeIntervalColors = new HashMap<String, Color>();
        typeIntervalColors.put("fixed", new Color(128, 128, 128));
        typeIntervalColors.put("object", new Color(192, 64, 255));
        typeIntervalColors.put("int", new Color(64, 192, 255));
        typeIntervalColors.put("long", new Color(0, 128, 255));
        typeIntervalColors.put("float", new Color(192, 255, 64));
        typeIntervalColors.put("double", new Color(128, 255, 0));

        typeIntervalColors.put("byte", new Color(192, 64, 255));
        typeIntervalColors.put("word", new Color(192, 64, 255));
        typeIntervalColors.put("dword", new Color(64, 192, 255));
        typeIntervalColors.put("qword", new Color(0, 128, 255));
        typeIntervalColors.put("single", new Color(192, 255, 64));
        typeIntervalColors.put("double", new Color(128, 255, 0));

        textFont = new Font("Dialog", Font.PLAIN, 11);
    }

    public void setHorizontalSize(int hsize) {
        this.hsize = hsize;
        update();
    }

    public void setVerticalSize(int vsize) {
        this.vsize = vsize;
        update();
    }


    private void update() {
        colWidth = _colWidth[hsize];
        rowHeight = _rowHeight[vsize];
        lightGridX = _lightGridX[hsize];
        darkGridX = _darkGridX[hsize];
        textGridX = _textGridX[hsize];
        lightGridY = _lightGridY[vsize];
        darkGridY = _darkGridY[vsize];
        textGridY = _textGridY[vsize];
        thickLineWidth = _thickLineWidth[hsize];
        barSeparation = _barSeparation[vsize];
        showIntervalText = _showIntervalText[vsize][hsize];
    }

    public Color getIntervalColor(ChildInterval child) {
        if (child.getOperand().contains("stack")) {
            return stackIntervalColor;
        } else {
            final String typeName = child.getType().toLowerCase();
            final Color color = typeIntervalColors.get(typeName);
            if (color == null) {
                return DEFAULT_INT_COLOR;
            }
            return color;
        }
    }

    public Color getUsePosColor(UsePosition usePos) {
        switch (usePos.getKind()) {
            case 'L':
                return usePosColorL;
            case 'S':
                return usePosColorS;
            case 'M':
                return usePosColorM;
            default:
                //throw new Error("illegal use kind");
                return usePosColorOther;
        }
    }
}
