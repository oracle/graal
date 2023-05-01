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

package org.graalvm.visualizer.source.impl.editor;

import static org.graalvm.visualizer.source.impl.editor.SelectOrExtractNodesAction.NAME_EXTRACT;
import org.netbeans.api.editor.EditorActionRegistration;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import javax.swing.Action;

/**
 *
 * @author sdedic
 */
/**
 * Selects or extract nodes in the graph on the current line
 */
@NbBundle.Messages({
    "graph-extract-nodes=Extract graph nodes",
    "hint_graph-extract-nodes=Extracts nodes that correspond to the current line or selection",
})
public class ExtractNodesAction extends SelectOrExtractNodesAction {
    public ExtractNodesAction() {
        this(Lookup.EMPTY);
    }

    public ExtractNodesAction(Lookup lkp) {
        super(false, NAME_EXTRACT, lkp);
    }
    
    @EditorActionRegistration(
        name = SelectOrExtractNodesAction.NAME_EXTRACT,
        mimeType = "",
        category = "Source View",
        popupText = "#hint_graph-extract-nodes",
        iconResource = "org/graalvm/visualizer/source/resources/extractNodes.gif"
    )
    public static Action extractNodesAction() {
        return new ExtractNodesAction();
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new ExtractNodesAction(lkp);
    }
}

