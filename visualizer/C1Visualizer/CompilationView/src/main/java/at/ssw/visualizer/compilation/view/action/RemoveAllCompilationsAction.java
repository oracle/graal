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
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;

/**
 * Action for removing all compilations from the workspace.
 *
 * @author Bernhard Stiftner
 * @author Christian Wimmer
 */
public final class RemoveAllCompilationsAction extends AbstractAction {
    public RemoveAllCompilationsAction() {
        super("Remove All Methods", new ImageIcon(ImageUtilities.loadImage(Icons.REMOVE_ALL)));
        putValue(Action.SHORT_DESCRIPTION, "Remove All Methods");
    }

    public void actionPerformed(ActionEvent event) {
        CompilationModel model = Lookup.getDefault().lookup(CompilationModel.class);
        model.clear();
    }
}
