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
package at.ssw.visualizer.cfg.preferences;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JColorChooser;

/**
 * A color selection button. It will preview the currently selected color as
 * an icon. Clicking the button will bring up the JColorChooser dialog.
 *
 * @author Bernhard Stiftner
 */
public class ColorChooserButton extends JButton implements ActionListener {

    public static final Dimension ICON_SIZE = new Dimension(24, 8);

    Color color;
    boolean colorChooserEnabled = true; // bring up dialog when clicked?


    public ColorChooserButton() {
        this(Color.black);
    }

    public ColorChooserButton(Color defaultColor) {
        setIcon(new ColorBoxIcon());
        addActionListener(this);
        color = defaultColor;
        Dimension size = new Dimension(ICON_SIZE);
        size.width += getInsets().left + getInsets().right;
        size.height += getInsets().top + getInsets().bottom;
        setPreferredSize(size);
    }

    public void setColor(Color newColor) {
        color = newColor;
        repaint();
    }

    public Color getColor() {
        return color;
    }

    public boolean isColorChooserEnabled() {
        return colorChooserEnabled;
    }

    public void setColorChooserEnabled(boolean enabled) {
        this.colorChooserEnabled = enabled;
    }

    public void actionPerformed(ActionEvent e) {
        if (!colorChooserEnabled) {
            return;
        }

        Color c = JColorChooser.showDialog(this, "Choose color", color);
        if (c != null) {
            setColor(c);
        }
    }

    class ColorBoxIcon implements Icon {

        public int getIconWidth() {
            return ICON_SIZE.width;
        }

        public int getIconHeight() {
            return ICON_SIZE.height;
        }

        public void paintIcon(Component c, Graphics g, int x, int y) {
            Color oldColor = g.getColor();
            g.translate(x, y);

            g.setColor(color);
            g.fillRect(0, 0, ICON_SIZE.width, ICON_SIZE.height);

            g.setColor(Color.black);
            g.drawRect(0, 0, ICON_SIZE.width, ICON_SIZE.height);

            g.translate(-x, -y);
            g.setColor(oldColor);
        }
    }
}

