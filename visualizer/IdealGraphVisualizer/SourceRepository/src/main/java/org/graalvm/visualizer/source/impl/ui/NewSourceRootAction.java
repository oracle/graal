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

import org.graalvm.visualizer.source.impl.FileGroup;
import org.graalvm.visualizer.source.impl.SourceRepositoryImpl;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.prefs.Preferences;

/**
 *
 */
@NbBundle.Messages({
        "ACTION_NewSourceRoot=Add source root..."
})
public class NewSourceRootAction extends AbstractAction {
    final SourceRepositoryImpl repository;
    FileGroup defaultGroup;

    public NewSourceRootAction(SourceRepositoryImpl repository, FileGroup defaultGroup) {
        super(Bundle.ACTION_NewSourceRoot());
        this.repository = repository;
        this.defaultGroup = defaultGroup;
    }

    @NbBundle.Messages({
            "TITLE_AddSourceRoot=Add source root",
            "BTN_Add=&Add",
            "BTN_Cancel=Cancel",
            "# The following BTN_Command* must be translated exactly the same as the button captions, but without mnemonics",
            "BTN_CommandAdd=Add",
            "BTN_CommandCancel=Cancel"
    })
    @Override
    public void actionPerformed(ActionEvent e) {
        Preferences pref = NbPreferences.forModule(getClass());
        String dirUrl = pref.get("sourceroot.lastdir", null);
        FileObject dir = null;
        if (dirUrl != null) {
            try {
                dir = URLMapper.findFileObject(new URL(dirUrl));
            } catch (MalformedURLException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        if (dir == null) {
            dir = FileUtil.toFileObject(new File(System.getProperty("user.home")));
        }

        CreateRootPanel crp;
        try {
            crp = new CreateRootPanel(dir, repository);
            crp.setFileGroup(defaultGroup);
        } catch (FileStateInvalidException ex) {
            Exceptions.printStackTrace(ex);
            return;
        }
        String sAdd = Bundle.BTN_Add();
        String cmdAdd = Bundle.BTN_CommandAdd();
        String sCancel = Bundle.BTN_Cancel();
        String cmdCancel = Bundle.BTN_CommandCancel();

        // Note: the action command is routed to DD's ActionListener, then
        // to the JFileChooser's original approve/cancel listeners, which also handle
        // typed directories.
        // If the JDK approve/cancel listeners really approve/cancel the chooser,
        // the event is caught by the {@link CreateRootPanel#addActionListener} registered
        // below and turned into dialog OK/cancel.

        DialogDescriptor nd = new DialogDescriptor(
                crp, Bundle.TITLE_AddSourceRoot(), true,
                new Object[]{
                        sAdd, sCancel
                }, sAdd,
                DialogDescriptor.DEFAULT_ALIGN,
                null, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String ac = e.getActionCommand();
                if (ac == null) {
                    return;
                }
                if (ac.equals(cmdAdd)) {
                    crp.invokeApproveListener(e);
                } else if (ac.equals(cmdCancel)) {
                    crp.invokeCancelListener(e);
                }
            }
        }
        );

        nd.setNoDefaultClose(false);
        Dialog dlg = DialogDisplayer.getDefault().createDialog(nd);
        crp.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (JFileChooser.CANCEL_SELECTION.equals(e.getActionCommand())) {
                    nd.setValue(NotifyDescriptor.CANCEL_OPTION);
                    dlg.setVisible(false);
                } else if (JFileChooser.APPROVE_SELECTION.equals(e.getActionCommand())) {
                    nd.setValue(NotifyDescriptor.OK_OPTION);
                    dlg.setVisible(false);
                }
            }
        });
        dlg.setVisible(true);
        Object result = nd.getValue();
        if (result != NotifyDescriptor.OK_OPTION) {
            return;
        }
        FileObject root = crp.getSelectedRoot();
        String disp = crp.getDescription();
        FileGroup group = crp.getParentGroup();
        this.defaultGroup = group;
        if (group == null) {
            group = repository.getDefaultGroup();
        }
        try {
            repository.addLocation(root, disp, group);
        } catch (IOException ex) {
        }
    }
}
