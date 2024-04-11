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
package org.graalvm.visualizer.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.graalvm.visualizer.data.src.ImplementationClass;
import org.junit.Test;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;

public class DiagramSceneTest {
    @Test
    public void testComputeSelectionContent() {
        InputGraph graph = InputGraph.createTestGraph("empty");
        final InputNode in = new InputNode(33);
        in.getProperties().setProperty("class", "java.lang.Long");
        Set<Object> selectedObjects = Collections.singleton(in);
        Collection<Object> fillToLookup = new HashSet<>();
        Set<InputNode> nodeSelection = null;
        Set<Integer> nodeSelectionIds = null;
        DiagramScene.computeSelectionContent(graph, selectedObjects, fillToLookup, nodeSelection, nodeSelectionIds);

        boolean found = false;
        for (Object object : fillToLookup) {
            if (object instanceof ImplementationClass) {
                assertEquals("java.lang.Long", ((ImplementationClass) object).getName());
                found = true;
            }
        }
        assertTrue("No ImplementationClass object in " + fillToLookup, found);
    }
}
