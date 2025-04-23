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
package org.graalvm.visualizer.shell.actions;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.shell.ShellUtils;
import org.graalvm.visualizer.shell.impl.ScrapEditorController;
import org.graalvm.visualizer.shell.ui.ScriptNavigatorTopComponent;
import org.netbeans.api.actions.Openable;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.Position;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Optional;

/**
 * The action executes a new shell, creating a new file.
 *
 * @author sdedic
 */
@ActionID(
        category = "IGV",
        id = "org.graalvm.visualizer.shell.ExexuteIGVShell"
)
@ActionRegistration(
        displayName = "#CTL_ExexuteIGVShell",
        key = "execute-shell"
)
@ActionReferences({
        @ActionReference(path = "Menu/View", position = 1800, separatorBefore = 1750),
        @ActionReference(path = "Shortcuts", name = "OS-S")
})
@Messages("CTL_ExexuteIGVShell=Scripting shell")
public final class ExecuteIGVShell implements ActionListener {
    // the parameter only ensures this action is enabled if some graph is selected.
    public ExecuteIGVShell(InputGraph context) {
    }

    private static FileObject getScriptFile(TopComponent tc) {
        if (tc == null) {
            return null;
        }
        FileObject f = tc.getLookup().lookup(FileObject.class);
        return ShellUtils.isScriptObject(f) ? f : null;
    }

    private static boolean containsEditor(TopComponent tc) {
        if (tc == null) {
            return false;
        }
        EditorCookie cake = tc.getLookup().lookup(EditorCookie.class);
        if (cake != null) {
            JEditorPane[] panes = cake.getOpenedPanes();
            if (panes != null) {
                if (SwingUtilities.isDescendingFrom(panes[0], tc)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void actionPerformed(ActionEvent ev) {

        // first try to find a suitable script editor active in some Mode
        Optional<TopComponent> existing = WindowManager.getDefault().getModes().stream()
                .map((m) -> m.getSelectedTopComponent())
                .filter((tc) -> containsEditor(tc) && getScriptFile(tc) != null).findAny();
        if (existing.isPresent()) {
            TopComponent target = existing.get();
            target.requestActive();
            target.requestFocus();
            return;
        }
        // no script exists, create one
        try {
            DataFolder templates = ShellUtils.getTemplatesFolder();
            DataObject selected = null;

            for (DataObject tmpl : templates.getChildren()) {
                if (tmpl.getPrimaryFile().getAttribute("scrapTemplate") != null) {
                    selected = tmpl;
                    break;
                }
            }

            if (selected == null) {
                // FIXME report error, no template or whatever
                return;
            }
            DataObject scrap = ShellUtils.createScrapScript(selected);
            Openable o = scrap.getLookup().lookup(Openable.class);
            if (o != null) {
                ShellUtils.onScrapMaterialize(scrap.getPrimaryFile(), (f) -> {
                    TopComponent tc = WindowManager.getDefault().findTopComponent(ScriptNavigatorTopComponent.ID);
                    if (tc != null) {
                        tc.open();
                    }
                });
                // attach a listener and place the caret at the end, presumably after the comment:
                EditorCookie.Observable ecake = scrap.getLookup().lookup(EditorCookie.Observable.class);
                ecake.addPropertyChangeListener(new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        if (!EditorCookie.Observable.PROP_OPENED_PANES.equals(evt.getPropertyName())) {
                            return;
                        }
                        JEditorPane[] panes = ecake.getOpenedPanes();
                        if (panes != null) {
                            JEditorPane tPane = panes[0];
                            Position pos = tPane.getDocument().getEndPosition();
                            tPane.getCaret().setDot(Math.max(0, pos.getOffset() - 1));
                        }

                        ecake.removePropertyChangeListener(this);
                    }
                });
                o.open();
                new ScrapEditorController(scrap).attach();
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

    }
}
