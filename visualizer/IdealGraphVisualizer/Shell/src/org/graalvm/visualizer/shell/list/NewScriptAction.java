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
package org.graalvm.visualizer.shell.list;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import org.graalvm.visualizer.shell.ui.ScriptNavigatorTopComponent;
import org.graalvm.visualizer.shell.ShellUtils;
import org.openide.DialogDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataShadow;
import org.openide.loaders.TemplateWizard;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Creates a new script; uses new from template Wizard to select the template.
 * 
 * PENDING: Maybe should limit the folder to the script folder. Should
 * display most-used (or all) templates on popup menu for convenience
 * 
 * PENDING; Should be present in window's toolbar
 * 
 * @author sdedic
 */
@NbBundle.Messages({
    "LBL_NewScript=&New Script...",
    "# {0} - localized error message",
    "ERR_CannotCreateScripts=Could not create Scripts folder: {0}"
})
public class NewScriptAction extends AbstractAction {
    private final FileObject    folder;

    public NewScriptAction(FileObject folder) {
        super(Bundle.LBL_NewScript());
        this.folder = folder;
    }
    @Override
    public void actionPerformed(ActionEvent e) {
        TemplateWizard wiz = new TemplateWizard();
        FileObject scriptRoot;
        
        try {
            scriptRoot = ShellUtils.ensureScriptRoot();
        } catch (IOException ex) {
            Exceptions.printStackTrace(
                    Exceptions.attachMessage(ex, Bundle.ERR_CannotCreateScripts(ex.toString())));
            return;
        }
        DataFolder scriptFolder = DataFolder.findFolder(folder);
        wiz.setTargetFolder(scriptFolder);
        try {
            wiz.setTemplate(ShellUtils.getLastScriptTemplate());
            wiz.setTemplatesFolder(ShellUtils.getTemplatesFolder());
            Set<DataObject> files = wiz.instantiate();
            if (files != null && !files.isEmpty()) {
                DataObject x = createScriptFolderShadow(files.iterator().next().getPrimaryFile(), scriptRoot);
                // wait on materialize && show the script list window
                ShellUtils.onScrapMaterialize(x.getPrimaryFile(), (f) -> {
                    TopComponent tc = WindowManager.getDefault().findTopComponent(ScriptNavigatorTopComponent.ID);
                    if (tc != null) {
                        tc.open();
                    }
                });
                ShellUtils.setLastScriptTemplate(wiz.getTemplate());
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
    
    private DataObject createScriptFolderShadow(FileObject script, FileObject rootScriptFolder) throws IOException {
        if (FileUtil.isParentOf(rootScriptFolder, script)) {
            return DataObject.find(script);
        }
        return DataShadow.create(DataFolder.findFolder(folder), DataObject.find(script));
    }
}
