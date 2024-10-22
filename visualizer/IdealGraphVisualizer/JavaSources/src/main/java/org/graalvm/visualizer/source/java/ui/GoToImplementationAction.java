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
package org.graalvm.visualizer.source.java.ui;

import org.graalvm.visualizer.data.src.ImplementationClass;
import org.netbeans.api.jumpto.type.TypeBrowser;
import org.netbeans.spi.jumpto.type.TypeDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@NbBundle.Messages({
        "LBL_GoToClass=Go to &Implementation",
})
@ActionID(category = "IGV", id = "org.graalvm.visualizer.source.java.ui.GoToImplementationAction")
@ActionRegistration(displayName = "#LBL_GoToClass")
@ActionReference(path = "NodeGraphViewer/SelectionActions", position = 99900, separatorBefore = 99500)
public final class GoToImplementationAction implements ActionListener {
    private final ImplementationClass clazz;

    public GoToImplementationAction(ImplementationClass impl) {
        this.clazz = impl;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TypeDescriptor result = TypeBrowser.browse(Bundle.LBL_GoToClass(), clazz.getName(), null);
        if (result != null) {
            result.open();
        }
    }

}
