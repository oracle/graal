/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.phases;

import java.util.*;

import com.oracle.max.graal.compiler.graph.*;
import com.oracle.max.graal.graph.*;

/**
 * Duplicates every node in the graph to test the implementation of the {@link com.oracle.max.graal.graph.Node#copy()} method in node subclasses.
 */
public class DuplicationPhase extends Phase {

    @Override
    protected void run(Graph graph) {

        // Create duplicate graph.
        CompilerGraph duplicate = new CompilerGraph();
        Map<Node, Node> replacements = new HashMap<Node, Node>();
        replacements.put(graph.start(), duplicate.start());
        duplicate.addDuplicate(graph.getNodes(), replacements);

        // Delete nodes in original graph.
        for (Node n : graph.getNodes()) {
            if (n != null && n != graph.start()) {
                n.forceDelete();
            }
        }

        // Copy nodes from duplicate back to original graph.
        replacements.clear();
        replacements.put(duplicate.start(), graph.start());
        graph.addDuplicate(duplicate.getNodes(), replacements);
    }
}
