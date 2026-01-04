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
package at.ssw.visualizer.texteditor.view;

import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.fold.FoldUtilities;
import org.netbeans.spi.editor.SideBarFactory;

/**
 * This is a workaround for NETBEANS-6232. NB platform wrongly computes sidebar
 * (line numbers, code folding) after fold expansion - until the bug is fixed, this
 * component will force the sidebar's height to the text view's height.
 * It is safe to remove this class + layer.xml registration after NETBEANS-6232 is fixed and
 * the fixed platform is consumed in C1V.
 * @author sdedic
 */
public class PaintBugWorkaroundBar extends JComponent {
    private final JTextComponent target;

    PaintBugWorkaroundBar(JTextComponent target) {
        this.target = target;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = new Dimension();
        d.height = target.getPreferredScrollableViewportSize().height;
        return d;
    }
    
    /**
     * Factory for the sidebars. Use {@link FoldUtilities#getFoldingSidebarFactory()} to
     * obtain an instance.
     */
    public static class Factory implements SideBarFactory {
        @Override
        public JComponent createSideBar(JTextComponent target) {
            return new PaintBugWorkaroundBar(target);
        }
    }
}
