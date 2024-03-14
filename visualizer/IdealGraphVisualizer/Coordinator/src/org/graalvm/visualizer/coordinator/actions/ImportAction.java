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

package org.graalvm.visualizer.coordinator.actions;

import org.graalvm.visualizer.settings.graal.GraalSettings;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import org.openide.util.Exceptions;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.SystemAction;
import javax.swing.JOptionPane;
import org.graalvm.visualizer.coordinator.impl.FileImporter;
import org.openide.filesystems.FileUtil;

@ActionID(category = "File", id = "org.graalvm.visualizer.coordinator.actions.ImportAction")
@ActionRegistration(iconBase = "org/graalvm/visualizer/coordinator/images/import.png", displayName = "#CTL_ImportAction")
@ActionReferences({
    @ActionReference(path = "Menu/File", position = 0)
    ,@ActionReference(path = "Shortcuts", name = "C-O")
})
public final class ImportAction extends SystemAction {
    private static final Logger LOG = Logger.getLogger(ImportAction.class.getName());

    @NbBundle.Messages("MSG_BIGV_Description_ZIP=Compressed dumps (*.zip)")
    public static FileFilter getZipFilter() {
        return new FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                String fn = f.getName().toLowerCase(Locale.ENGLISH);
                return fn.endsWith(".zip");
            }

            @Override
            public String getDescription() {
                return Bundle.MSG_BIGV_Description_ZIP();
            }
        };
    }
    @NbBundle.Messages({
        "# {0} - file name",
        "# {1} - error message",
        "ERR_OpeningFile=Error importing from file {0}: {1}",
        "# {0} - file name",
        "WAR_WrongExtension=Wrong file extension: {0}",
        "WAR_PropperExtension=Extension has to be: bgv"
    })
    @Override
    public void actionPerformed(ActionEvent e) {
        final GraalSettings settings = GraalSettings.obtain();
        JFileChooser fc = new JFileChooser() {
            @Override
            public void approveSelection() {
                File selectedFile = getSelectedFile();
                if (selectedFile != null) {
                    File dir = FileUtil.normalizeFile(selectedFile);
                    if (dir.exists() && dir.isFile()) {
                        super.approveSelection();
                    }
                }
            }
        };
        fc.setAcceptAllFileFilterUsed(false);
        fc.addChoosableFileFilter(SaveAsAction.getFileFilter()); // also selects the filter
        fc.addChoosableFileFilter(getZipFilter());
        fc.setCurrentDirectory(new File(settings.getDirectory()));
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            for (final File file : fc.getSelectedFiles()) {
                File dir = file;
                if (!dir.isDirectory()) {
                    dir = dir.getParentFile();
                }
                settings.setDirectory(dir.getAbsolutePath());
                boolean ok = false;
                for (FileFilter ff : fc.getChoosableFileFilters()) {
                    if (ff.accept(file)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    String war = file.getName();
                    war = Bundle.WAR_WrongExtension(war.substring(war.lastIndexOf(".") + 1, war.length()));
                    JOptionPane.showMessageDialog(null, Bundle.WAR_PropperExtension(), war, JOptionPane.WARNING_MESSAGE);
                    return;
                }

                try {
                    FileImporter.asyncImportDocument(file.toPath(), true, true, null);
                } catch (IOException ex) {
                    Exceptions.printStackTrace(
                        Exceptions.attachLocalizedMessage(ex, Bundle.ERR_OpeningFile(file.toPath(), ex.toString()))
                    );
                }
            }
        }
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(ImportAction.class, "CTL_ImportAction");
    }

    @Override
    protected String iconResource() {
        return "org/graalvm/visualizer/coordinator/images/import.png";
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
