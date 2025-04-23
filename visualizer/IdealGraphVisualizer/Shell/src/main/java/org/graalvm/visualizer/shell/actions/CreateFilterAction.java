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

import org.graalvm.visualizer.shell.ShellUtils;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.cookies.InstanceCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataShadow;
import org.openide.loaders.InstanceDataObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.actions.ActionPresenterProvider;
import org.openide.util.actions.Presenter;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

/**
 * Action which promotes a script to a Filter. The action just makes a shadow into
 * the Filters folder, so that a new Filter appears in Filter window.
 *
 * @author sdedic
 */
@NbBundle.Messages({
        "ACTION_CreateFilter=Use as Filter"
})
@ActionID(category = "IGV", id = "org.graalvm.visualizer.shell.actions.createFilter")
@ActionReference(path = "Menu/View", position = 1850)
@ActionRegistration(
        displayName = "#ACTION_CreateFilter",
        key = "script-to-filter",
        iconBase = "org/graalvm/visualizer/shell/resources/filter.png",
        lazy = true
)
public class CreateFilterAction extends AbstractAction implements ActionListener, Presenter.Menu, Presenter.Toolbar {
    private final FileObject file;

    public CreateFilterAction(FileObject file) {
        this.file = file;
        setEnabled(ShellUtils.isScriptObject(file) &&
                ShellUtils.visibleScriptObjects().test(file));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!isEnabled()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        try {
            FileObject filterDir = FileUtil.getConfigFile("Filters");
            DataObject original = DataObject.find(file);
            DataShadow.create(DataFolder.findFolder(filterDir), original);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public JMenuItem getMenuPresenter() {
        return ActionPresenterProvider.getDefault().createMenuPresenter(this);
    }

    @Override
    public Component getToolbarPresenter() {
        return ActionPresenterProvider.getDefault().createToolbarPresenter(this);
    }

    public static Action createContextAction(Lookup lkp) {
        try {
            InstanceDataObject ido = InstanceDataObject.find(DataFolder.findFolder(FileUtil.getConfigFile("Actions/IGV")),
                    "org-graalvm-visualizer-shell-actions-createFilter", Action.class);
            if (ido == null) {
                return null;
            }
            Object o = ido.getLookup().lookup(InstanceCookie.class).instanceCreate();
            if (o instanceof ContextAwareAction) {
                return ((ContextAwareAction) o).createContextAwareInstance(lkp);
            }
        } catch (IOException | ClassNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        }
        return null;
    }
}
