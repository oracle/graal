/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames;
import org.graalvm.visualizer.settings.graal.GraalSettings;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author sdedic
 */
@NbBundle.Messages({
        "STATUS_AllDocumentsSaved=All sessions were saved",
        "STATUS_NoSessionModified=No sessions were modified."
})
class SaveActionPerformer {

    final SaveOperation oper;
    final GraalSettings settings = GraalSettings.obtain();
    final FileFilter ff = SaveAsAction.getFileFilter();

    public SaveActionPerformer(SaveOperation operation) {
        this.oper = operation;
    }

    void doSave() {
        oper.prepare();
        oper.saveFileBoundSessions();
        if (oper.isFinished()) {
            StatusDisplayer.getDefault().setStatusText(oper.getDocumentsSaved() == 0 ? Bundle.STATUS_NoSessionModified() : Bundle.STATUS_AllDocumentsSaved());
            return;
        }
        SaveOptions sopts = new SaveOptions();
        sopts.configure(oper);

        GraphDocument first = oper.firstDocumentToSave();
        Path p = promptDocumentPath(sopts, null, first);
        if (p == null) {
            return;
        }
        File file = p.toFile();
        oper.setSaveStyle(sopts.getStyle());
        oper.setUserPath(oper.getUsableFileName(file.toPath(), false));
        oper.setUserTitle(sopts.getFileComment());
        if (sopts.isPromptEachFile()) {
            oper.setPathProvider(this::promptDocumentPath);
        }
        oper.execute();
    }

    private Path promptDocumentPath(Path suggested, GraphDocument doc) {
        SaveOptions sopts = new SaveOptions();
        sopts.makeFilePrompt();
        return promptDocumentPath(sopts, suggested, doc);
    }


    @NbBundle.Messages({
            "TITLE_SaveAs=Save as",
            "TITLE_SaveAsMultiple=Save Multiple Dumps As",
            "TITLE_Save=Save",
            "TITLE_SaveMultiple=Save Multiple Dumps",
            "TITLE_SaveSingleAs=Save Dump As",
            "TITLE_SaveSingle=Save Dump"
    })
    private Path promptDocumentPath(SaveOptions sopts, Path suggested, GraphDocument doc) {
        JFileChooser fc = newFileChooser();
        String t;
        if (suggested != null) {
            if (oper.isSaveAs()) {
                t = Bundle.TITLE_SaveSingleAs();
            } else {
                t = Bundle.TITLE_SaveSingle();
            }
            fc.setSelectedFile(suggested.toFile());
        } else {
            if (oper.isSaveAs()) {
                if (oper.hasMultipleSessions()) {
                    t = Bundle.TITLE_SaveAsMultiple();
                } else {
                    t = Bundle.TITLE_SaveAs();
                }
            } else {
                if (oper.hasMultipleSessions()) {
                    t = Bundle.TITLE_SaveMultiple();
                } else {
                    t = Bundle.TITLE_Save();
                }
            }
        }
        fc.setDialogTitle(t);
        if (doc != null) {
            String n = doc.getName();
            String l = doc.getProperties().getString(KnownPropertyNames.PROPNAME_USER_LABEL, null);

            if (oper.getFile(doc) == null) {
                fc.setSelectedFile(oper.getUsableFileName(Paths.get(n), true).toFile());
            } else if (suggested == null) {
                fc.setSelectedFile(oper.getUsableFileName(Paths.get(n), oper.isSaveAs()).toFile());
            }
            if (l != null) {
                sopts.setFileComment(l);
            }
        }
        fc.setAccessory(sopts);

        if (fc.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File file = fc.getSelectedFile();
        settings.setDirectory(fc.getCurrentDirectory().getAbsolutePath());
        if (!ff.accept(file)) {
            //wrong file extension, create usable filename
            file = oper.getUsableFileName(file);
        }
        return file.toPath();
    }

    private JFileChooser newFileChooser() {
        JFileChooser fc = new JFileChooser();
        fc.setAcceptAllFileFilterUsed(false);
        fc.setFileFilter(ff);
        fc.setCurrentDirectory(new File(settings.getDirectory()));
        return fc;
    }

}
