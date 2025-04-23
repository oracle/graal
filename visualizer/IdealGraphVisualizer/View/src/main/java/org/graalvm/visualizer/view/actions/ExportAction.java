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
package org.graalvm.visualizer.view.actions;

import org.graalvm.visualizer.settings.graal.GraalSettings;
import org.graalvm.visualizer.view.ExportCookie;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

@NbBundle.Messages({
        "CTL_ExportAction=Export as SVG..."
})
@ActionID(category = "Diagram", id = ExportAction.ID)
@ActionRegistration(displayName = "#CTL_ExportAction", lazy = true,
        iconInMenu = true,
        iconBase = "org/graalvm/visualizer/view/images/export.png",
        key = "export-svg",
        surviveFocusChange = true
)
@ActionReferences({
        @ActionReference(path = "Menu/File", position = 1600, separatorBefore = 1550),
        @ActionReference(path = "Shortcuts", name = "D-E")
})
public final class ExportAction implements ActionListener {
    public static final String ID = "org.graalvm.visualizer.view.actions.ExportAction"; // NOI18N

    private final ExportCookie cake;

    public ExportAction(ExportCookie cake) {
        this.cake = cake;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final GraalSettings settings = GraalSettings.obtain();
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileFilter() {

            @Override
            public boolean accept(File f) {
                return true;
            }

            @Override
            public String getDescription() {
                return "SVG files (*.svg)";
            }
        });
        fc.setCurrentDirectory(new File(settings.getDirectory()));

        if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (!file.getName().contains(".")) {
                file = new File(file.getAbsolutePath() + ".svg");
            }

            File dir = file;
            if (!dir.isDirectory()) {
                dir = dir.getParentFile();
            }

            settings.setDirectory(dir.getAbsolutePath());
            cake.export(file);
        }
    }
}
