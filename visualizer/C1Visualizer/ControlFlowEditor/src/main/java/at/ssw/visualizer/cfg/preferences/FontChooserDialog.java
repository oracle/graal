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

import java.awt.Font;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;

/**
 *
 * @author Bernhard Stiftner
 */
public class FontChooserDialog {

    /*
     * Displays a font selection dialog.
     * @return The selected font, or the initial font if the user chose
     * to bail out
     */
    public static Font show(Font initialFont) {
        PropertyEditor pe = PropertyEditorManager.findEditor(Font.class);
        if (pe == null) {
            throw new RuntimeException("Could not find font editor component.");
        }
        pe.setValue(initialFont);
        DialogDescriptor dd = new DialogDescriptor(
                pe.getCustomEditor(),
                "Choose Font");
        DialogDisplayer.getDefault().createDialog(dd).setVisible(true);
        if (dd.getValue() == DialogDescriptor.OK_OPTION) {
            Font f = (Font)pe.getValue();
            return f;
        }
        return initialFont;
    }

}
