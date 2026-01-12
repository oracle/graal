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
package at.ssw.visualizer.dataflow.attributes;

import at.ssw.visualizer.dataflow.graph.InstructionNodeGraphScene;
import java.awt.event.ActionEvent;
import javax.swing.JMenuItem;

/**
 * Expand Attribute that contributes a menuitem to the popupmenu which enables the user to
 * deactivate the attribute itself.
 *
 * @author Stefan Loidl
 */
public class SelfSwitchingExpandAttribute implements IPopupContributorAttribute, IExpandNodeAttribute, IPathHighlightAttribute{

    private boolean state;
    private JMenuItem mitem;
    private InstructionNodeGraphScene scene;

    /** Creates a new instance of SelfSwitchingExpandAttribute */
    public SelfSwitchingExpandAttribute(String description, InstructionNodeGraphScene scene) {
        state=true;
        mitem=new JMenuItem(description);
        mitem.addActionListener(this);
        this.scene=scene;
    }

    public JMenuItem getMenuItem() {
        return mitem;
    }

    public boolean validate() {
        return state;
    }

    public boolean removeable() {
        return true;
    }

    public void actionPerformed(ActionEvent e) {
        state=false;
        scene.refreshAll();
        scene.validate();
        scene.autoLayout();
    }

    public boolean showBlock() {
        return false;
    }

    public boolean showInstruction() {
        return true;
    }

}
