/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graph;

import com.oracle.graal.graph.Node.Input;

/**
 * An iterator over the references to a given {@link Node}'s {@linkplain Input inputs}.
 *
 * An iterator of this type will return null values.
 */
public final class NodeAllRefsIterator extends NodeRefIterator {

    public NodeAllRefsIterator(Node node, int nodeFields, int nodeListFields, boolean isInputs) {
        super(node, nodeFields, nodeListFields, isInputs);
    }

    @Override
    protected void forward() {
        assert needsForward;
        needsForward = false;
        if (index < nodeFields) {
            index++;
            if (index < nodeFields) {
                nextElement = getNode(index);
                return;
            }
        } else {
            subIndex++;
        }

        while (index < allNodeRefFields) {
            if (subIndex == 0) {
                list = getNodeList(index - nodeFields);
            }
            if (subIndex < list.size()) {
                nextElement = list.get(subIndex);
                return;
            }
            subIndex = 0;
            index++;
        }
    }
}
