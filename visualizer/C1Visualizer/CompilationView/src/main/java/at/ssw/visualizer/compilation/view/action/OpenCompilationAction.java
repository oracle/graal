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
package at.ssw.visualizer.compilation.view.action;

import at.ssw.visualizer.compilation.view.icons.Icons;
import at.ssw.visualizer.model.CompilationModel;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.windows.WindowManager;

/**
 * Action opening a CFG file and loading it into the workspace.
 *
 * @author Bernhard Stiftner
 * @author Christian Wimmer
 */
public final class OpenCompilationAction extends AbstractAction {
    public OpenCompilationAction() {
        super("&Open Compiled Methods...", new ImageIcon(ImageUtilities.loadImage(Icons.OPEN)));
        putValue(Action.SHORT_DESCRIPTION, "Open Compiled Methods");
    }

    public void actionPerformed(ActionEvent event) {
        JFileChooser ch = new JFileChooser();
        ch.setCurrentDirectory(getLastDirectory());
        ch.setFileFilter(new FileNameExtensionFilter("Compiled methods (*.cfg)", "cfg"));
        if (ch.showOpenDialog(WindowManager.getDefault().getMainWindow()) != JFileChooser.APPROVE_OPTION || ch.getSelectedFile() == null) {
            return;
        }
        setLastDirectory(ch.getSelectedFile());

        final String fileName = ch.getSelectedFile().getAbsolutePath();
        RequestProcessor.getDefault().post(new Runnable() {
            public void run() {
                CompilationModel model = Lookup.getDefault().lookup(CompilationModel.class);
                String errorMsg = model.parseInputFile(fileName);
                if (errorMsg != null) {
                    NotifyDescriptor d = new NotifyDescriptor.Message("Errors while parsing input:\n" + errorMsg, NotifyDescriptor.ERROR_MESSAGE);
                    DialogDisplayer.getDefault().notify(d);
                }
            }
        });
    }

    private File getLastDirectory() {
        Preferences prefs = Preferences.userNodeForPackage(getClass());
        String fileName = prefs.get("lastDirectory", null);
        File file = null;
        if (fileName != null) {
            file = new File(fileName);
        }
        if (file == null || !file.exists()) {
            file = new File(System.getProperty("user.home"));
        }
        return file;
    }

    private void setLastDirectory(File file) {
        Preferences prefs = Preferences.userNodeForPackage(getClass());
        if (!file.isDirectory()) {
            file = file.getParentFile();
        }
        prefs.put("lastDirectory", file.getPath());
    }
}
