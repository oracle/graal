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

package org.graalvm.visualizer.source.impl.ui;

import org.openide.awt.HtmlRenderer;
import org.openide.explorer.view.NodeRenderer;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.lang.reflect.Field;

/**
 * Hacks the ChoiceView node rendering, so that it renders node unindented in the
 * main combobox area, but leaves it properly indented in the popup list.
 */
final class CompactComboRenderer extends NodeRenderer {
    private static Field refRendererField;

    private HtmlRenderer.Renderer myRenderer;

    public CompactComboRenderer() {
        myRenderer = getRendererHack(this);
    }


    private static HtmlRenderer.Renderer getRendererHack(NodeRenderer instance) {
        if (refRendererField == null) {
            try {
                refRendererField = NodeRenderer.class.getDeclaredField("renderer"); // NOI18N
                refRendererField.setAccessible(true);
            } catch (ReflectiveOperationException | SecurityException ex) {
                return HtmlRenderer.createRenderer();
            }
        }
        try {
            return (HtmlRenderer.Renderer) refRendererField.get(instance);
        } catch (ReflectiveOperationException ex) {
            return HtmlRenderer.createRenderer();
        }
    }

    /**
     * Wrapper panel, which declares just think preferred width.
     * Preferred height is not affected, is computed from contained components.
     */
    private static final class ThinPanel extends JPanel {
        private Component lastRenderer;


        public ThinPanel() {
            setLayout(new BorderLayout());
            setBorder(null);
        }

        public Dimension getPreferredSize() {
            if (lastRenderer == null) {
                return super.getPreferredSize();
            }
            // temporarily reset all borders, so the prefsize can
            // be measured well.
            JComponent jc = null;
            Border b = null;
            if (lastRenderer instanceof JComponent) {
                jc = (JComponent) lastRenderer;
                b = jc.getBorder();
                jc.setBorder(null);
            }
            Dimension dim = lastRenderer.getPreferredSize();
            if (b != null && jc != null) {
                jc.setBorder(b);
            }
            dim.width = 5;
            return new Dimension(5, dim.height);
        }

        public Component setRenderer(Component c) {
            if (lastRenderer != c || c.getParent() != this) {
                if (lastRenderer != null) {
                    remove(lastRenderer);
                }
                lastRenderer = c;
                add(c, BorderLayout.CENTER);
            }
            return this;
        }
    }

    private final ThinPanel rendererPanel = new ThinPanel();

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean sel, boolean cellHasFocus) {
        if (index != -1) {
            // rendering the popup
            Component c = super.getListCellRendererComponent(list, value, index, sel, cellHasFocus);
            return c;
        } else {
            Component c = rendererPanel.setRenderer(super.getListCellRendererComponent(list, value, index, sel, cellHasFocus));
            myRenderer.setIndent(0);
            return c;
        }
    }

}
