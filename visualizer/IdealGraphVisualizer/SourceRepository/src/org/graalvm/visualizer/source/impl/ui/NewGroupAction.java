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

import org.openide.*;
import org.openide.util.NbBundle;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.IOException;
import javax.swing.AbstractAction;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.graalvm.visualizer.data.SuppressFBWarnings;
import org.graalvm.visualizer.source.impl.FileGroup;
import org.graalvm.visualizer.source.impl.SourceRepositoryImpl;

/**
 *
 */
@NbBundle.Messages("ACTION_NewSourceGroup=&Add Source Group")
public class NewGroupAction extends AbstractAction {
    private final SourceRepositoryImpl repository;
    
    public NewGroupAction(SourceRepositoryImpl repository) {
        super(Bundle.ACTION_NewSourceGroup());
        this.repository = repository;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @NbBundle.Messages({
        "TITLE_AddSourceGroup=Add source group",
        "LBL_Name=Name:",
        "ERR_GroupWithSameName=The name is already used",
        "# {0} - the failure message",
        "ERR_CreateGroup=File group creation failed: {0}"
    })
    @Override
    public void actionPerformed(ActionEvent e) {
        final NotificationLineSupport[] supp =  new NotificationLineSupport[1];
        NotifyDescriptor.InputLine input = new NotifyDescriptor.InputLine(Bundle.LBL_Name(), Bundle.TITLE_AddSourceGroup(),
                NotifyDescriptor.OK_CANCEL_OPTION, NotifyDescriptor.QUESTION_MESSAGE) {
            @Override
            @SuppressFBWarnings(value = "NP_UNWRITTEN_PUBLIC_OR_PROTECTED_FIELD", justification = "The field is written by parent JComponent initialization")
            protected Component createDesign(String text) {
                Component c = super.createDesign(text);
                textField.getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        changed();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        changed();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                    }
                    
                    private void changed() {
                        for (FileGroup g : repository.getGroups()) {
                            if (g.getDisplayName().equalsIgnoreCase(textField.getText())) {
                                supp[0].setErrorMessage(Bundle.ERR_GroupWithSameName());
                                return;
                            }
                        }
                        supp[0].setErrorMessage(null);
                    }
                });
                return c;
            }
        };
        supp[0] = input.createNotificationLineSupport();
        
        if (DialogDisplayer.getDefault().notify(input) == NotifyDescriptor.OK_OPTION) {
            try {
                repository.createGroup(input.getInputText());
            } catch (IOException ex) {
                NotifyDescriptor.Message msg = new NotifyDescriptor.Message(Bundle.ERR_CreateGroup(ex.toString()), 
                    NotifyDescriptor.ERROR_MESSAGE);
                DialogDisplayer.getDefault().notifyLater(msg);
            }
        }
    }
}
