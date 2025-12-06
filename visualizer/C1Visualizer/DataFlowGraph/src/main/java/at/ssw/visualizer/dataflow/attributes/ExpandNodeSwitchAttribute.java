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
import java.awt.event.ActionListener;
import javax.swing.JMenuItem;

/**
 *
 * @author Stefan Loidl
 */
public class ExpandNodeSwitchAttribute implements ISwitchAttribute, IPopupContributorAttribute, IExpandNodeAttribute, IPathHighlightAttribute{

    private boolean value, remove;
    private String description;
    private JMenuItem mitem;
    private InstructionNodeGraphScene scene;

    /** Creates a new instance of ExpandInfluenceSwitchAttribute */
    public ExpandNodeSwitchAttribute(boolean initialValue, String desc, boolean remove, InstructionNodeGraphScene scene) {
        value=initialValue;
        description=desc;
        this.remove=remove;
        mitem=new JMenuItem(description);
        mitem.addActionListener(this);
        this.scene=scene;
    }

    public void setSwitch(boolean s) {
        value=s;
    }

    public boolean getSwitch() {
        return value;
    }

    public String getSwitchString() {
        return description;
    }

    public boolean validate() {
        return value;
    }

    public boolean removeable() {
        return remove;
    }

    public JMenuItem getMenuItem() {
        return mitem;
    }

    public void actionPerformed(ActionEvent e) {
        value=!value;
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
