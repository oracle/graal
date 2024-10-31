/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.view.widgets;

import jdk.graal.compiler.graphio.parsing.model.InputBlock;
import org.netbeans.api.visual.widget.Scene;
import org.netbeans.api.visual.widget.Widget;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;

public final class BlockWidget extends Widget {

    public static final Color BACKGROUND_COLOR = new Color(235, 235, 255);
    public static final Color CORRUPTED_COLOR = new Color(255, 200, 200);
    private static final Font TITLE_FONT = new Font("Serif", Font.PLAIN, 14).deriveFont(Font.BOLD);
    private final InputBlock blockNode;

    public BlockWidget(Scene scene, InputBlock blockNode) {
        super(scene);
        this.blockNode = blockNode;
        this.setBackground(blockNode.getNodes().isEmpty() ? CORRUPTED_COLOR : BACKGROUND_COLOR);
        this.setOpaque(true);
        this.setCheckClipping(true);
    }

    @Override
    protected void paintWidget() {
        super.paintWidget();
        Graphics2D g = this.getGraphics();
        Stroke old = g.getStroke();
        g.setColor(blockNode.getNodes().isEmpty() ? Color.RED : Color.BLUE);
        Rectangle r = this.getPreferredBounds();
        if (r.width > 0 && r.height > 0) {
            g.setStroke(new BasicStroke(2));
            g.drawRect(r.x, r.y, r.width, r.height);
        }

        Color titleColor = Color.BLACK;
        g.setColor(titleColor);
        g.setFont(TITLE_FONT);

        String s = "B" + blockNode.getName();
        Rectangle2D r1 = g.getFontMetrics().getStringBounds(s, g);
        g.drawString(s, r.x + 5, r.y + (int) r1.getHeight());
        g.setStroke(old);
    }
}
