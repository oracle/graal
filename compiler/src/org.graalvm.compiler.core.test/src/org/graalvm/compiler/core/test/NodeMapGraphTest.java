/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.core.test;

import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.junit.Test;

public class NodeMapGraphTest extends GraalCompilerTest {

    private NodeMap<Object> getMap() {
        StructuredGraph g = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
        NodeMap<Object> map = new NodeMap<>(g);

        Node lastAdded = null;
        // grow the graph
        for (int i = 0; i < 1024; i++) {
            lastAdded = g.addWithoutUnique(ConstantNode.forInt(i));
        }

        map.setAndGrow(lastAdded, map);

        return map;
    }

    @Test
    public void testGetValues() {
        for (Object val : getMap().getValues()) {
            assert val != null;
        }
    }

    @Test
    public void testGetEntries() {
        MapCursor<Node, Object> entries = getMap().getEntries();
        while (entries.advance()) {
            assert entries.getKey() != null;
            assert entries.getValue() != null;
        }
    }

    @Test
    public void testGetKeys() {
        for (Object val : getMap().getKeys()) {
            assert val != null;
        }
    }

}
