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
package org.graalvm.visualizer.filterwindow;

import org.openide.explorer.view.NodeRenderer;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;

/**
 * @author sdedic
 */
public class CheckRenderer extends NodeRenderer {
    private final Wrap wrapComponent;

    static class P extends JPanel {
        void doValidate() {
            super.validateTree();
        }
    }

    Rectangle getCheckRect() {
        return wrapComponent.check.getBounds();
    }

    public CheckRenderer() {
        wrapComponent = new Wrap();
        setShowIcons(true);
    }

    Dimension getPreferredSize() {
        return wrapComponent.getPreferredSize();
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean sel, boolean cellHasFocus) {
        JComponent c = (JComponent) super.getListCellRendererComponent(list, value, index, sel, cellHasFocus);
        CheckNode node = ((CheckNodeListModel) list.getModel()).getCheckNodeAt(index);
        wrapComponent.setSelected(list.isEnabled(), node.isSelected());
        wrapComponent.setPainted(c);
        return wrapComponent;
    }

    private static class Wrap extends JComponent {
        private final JCheckBox check = new JCheckBox();
        JComponent toPaint;

        Wrap() {
            add(check, null);
        }

        void setPainted(JComponent painted) {
            if (this.toPaint != painted) {
                if (this.toPaint != null) {
                    remove(toPaint);
                }
                this.toPaint = painted;
                add(toPaint, null);
                check.setBackground(toPaint.getBackground());
            }
            doLayout();
        }

        @Override
        public void doLayout() {
            Dimension cDim = check.getPreferredSize();
            Dimension size = getPreferredSize();
            Dimension pDim = toPaint.getPreferredSize();
            Insets ins = getInsets();

            size.height -= (ins.top + ins.bottom);
            int x = ins.left;
            int y = cDim.height >= size.height ? ins.top : ins.top + ((size.height - cDim.height) / 2);

            check.setBounds(x, y, cDim.width, cDim.height);

            x += cDim.width;
            if (toPaint != null) {
                y = pDim.height >= size.height ? ins.top : ins.top + ((size.height - pDim.height) / 2);
                toPaint.setBounds(x, y, pDim.width, pDim.height);
            }
        }

        @Override
        protected void paintChildren(Graphics g) {
        }

        @Override
        public void paintComponent(Graphics g) {
            Dimension dCheck = check.getSize();
            Dimension dLabel = toPaint.getPreferredSize();
            Insets insets = getInsets();
            int x = insets.left;
            int y = insets.top;
            check.setBounds(x, y, dCheck.width, dCheck.height);
            check.paint(g);
            int yLabel = y;
            if (dCheck.height >= dLabel.height) {
                yLabel = (dCheck.height - dLabel.height) / 2;
            }
            x += dCheck.width;
            g.translate(x, yLabel);
            toPaint.paint(g);
            g.translate(-x, -yLabel);
        }

        public Dimension getPreferredSize() {
            Dimension cSize = check.getPreferredSize();
            Dimension pSize = toPaint == null ? new Dimension(0, 0) : toPaint.getPreferredSize();
            Insets ins = getInsets();
            return new Dimension(
                    ins.left + ins.right + cSize.width + pSize.width,
                    ins.top + ins.bottom + Math.max(cSize.height, pSize.height)
            );
        }

        void setSelected(boolean enabled, boolean s) {
            check.setEnabled(enabled);
            check.setSelected(s);
        }
    }
}
