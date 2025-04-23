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

import org.openide.util.Utilities;

import javax.swing.JComboBox;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.plaf.metal.MetalComboBoxUI;
import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * Special UI class which ensures that positions so that entire
 * its contents is visible, even if width with indented nodes is larger
 * than combo box selected line width.
 */
final class CompactComboUI extends MetalComboBoxUI {
    @Override
    protected ComboPopup createPopup() {
        return new WideComboPopup(comboBox);
    }

    static final class WideComboPopup extends BasicComboPopup {
        public WideComboPopup(JComboBox combo) {
            super(combo);
        }

        @Override
        protected Rectangle computePopupBounds(int px, int py, int pw, int ph) {
            Dimension d = list.getPreferredSize();
            Rectangle r = Utilities.getUsableScreenBounds();
            if (pw < d.width) {
                pw = Math.min(d.width, r.width - px);
            }

            if (ph < d.height) {
                ph = Math.min(r.height - py, d.height);
            }

            if ((px + pw) > (r.width - px)) {
                px -= (r.width - pw);
            }
            Rectangle result = new Rectangle(px, py, pw, ph);
            return result;
        }
    }
}
