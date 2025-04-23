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

package org.graalvm.visualizer.coordinator.impl;

import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames;
import jdk.graal.compiler.graphio.parsing.model.Properties;
import org.openide.actions.RenameAction;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.Lookup;

import javax.swing.Action;
import java.util.Arrays;

/**
 * @author sdedic
 */
public class AbstractOutlineNode extends AbstractNode {
    private final FolderElement item;

    public AbstractOutlineNode(FolderElement item, Children children, Lookup lookup) {
        super(children, lookup);
        this.item = item;
        super.setName(getUserName());
    }

    protected String getUserName() {
        if (!(item instanceof Properties.Entity)) {
            return item.getName();
        }
        Properties.Entity entity = (Properties.Entity) item;
        String p = entity.getProperties().get(KnownPropertyNames.PROPNAME_USER_LABEL, String.class);
        if (p == null) {
            return item.getName();
        }
        return p;
    }

    @Override
    public boolean canRename() {
        return (item instanceof Properties.MutableOwner);
    }

    @Override
    public void setName(String s) {
        super.setName(s);
        if (!canRename()) {
            throw new IllegalStateException();
        }
        Properties.MutableOwner mutableEntity = (Properties.MutableOwner) item;
        Properties props = mutableEntity.writableProperties();
        props.setProperty(KnownPropertyNames.PROPNAME_USER_LABEL, s);
        mutableEntity.updateProperties(props);
    }

    @Override
    public Action[] getActions(boolean context) {
        Action[] actions = super.getActions(context);
        actions = Arrays.copyOf(actions, actions.length + 1);
        actions[actions.length - 1] = RenameAction.get(RenameAction.class);
        return actions;
    }
}
