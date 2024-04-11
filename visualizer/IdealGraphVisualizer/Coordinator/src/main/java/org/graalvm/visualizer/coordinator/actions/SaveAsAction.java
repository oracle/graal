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

import org.graalvm.visualizer.coordinator.impl.SessionManagerImpl;
import jdk.graal.compiler.graphio.parsing.model.Folder;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.actions.CookieAction;
import org.openide.util.actions.NodeAction;

import javax.swing.Action;
import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class SaveAsAction extends NodeAction {

    public SaveAsAction() {
        this(null);
    }

    @NbBundle.Messages("MSG_SavesSelectedGroupsToFile=Save selected groups to file...")
    private SaveAsAction(Lookup actionContext) {
        putValue(Action.SHORT_DESCRIPTION, Bundle.MSG_SavesSelectedGroupsToFile());
    }

    @Override
    protected void performAction(Node[] activatedNodes) {
        SaveOperation oper = new SaveOperation(true,
                Arrays.stream(activatedNodes).
                        map(n -> n.getLookup().lookup(Folder.class)).
                        filter(Objects::nonNull).
                        collect(Collectors.toList()),
                SessionManagerImpl.getInstance()
        );
        new SaveActionPerformer(oper).doSave();
    }


    @NbBundle.Messages("MSG_BIGV_Description=IGV binary files (*.bgv)")
    public static FileFilter getFileFilter() {
        return new FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                String fn = f.getName().toLowerCase(Locale.ENGLISH);
                return fn.endsWith(".bgv");
            }

            @Override
            public String getDescription() {
                return Bundle.MSG_BIGV_Description();
            }
        };
    }

    protected int mode() {
        return CookieAction.MODE_SOME;
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(SaveAsAction.class, "CTL_SaveAsAction");
    }

    @Override
    protected String iconResource() {
        return "org/graalvm/visualizer/coordinator/images/save.png";
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }

    @Override
    protected boolean enable(Node[] nodes) {

        int cnt = 0;
        for (Node n : nodes) {
            cnt += n.getLookup().lookupAll(Folder.class).size();
        }

        return cnt > 0;
    }
}
