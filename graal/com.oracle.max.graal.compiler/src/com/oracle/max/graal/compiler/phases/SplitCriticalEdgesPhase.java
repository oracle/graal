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

import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;


public class SplitCriticalEdgesPhase extends Phase {

    @Override
    protected void run(Graph graph) {
        List<Node> nodes = graph.getNodes();
        for (int i = 0; i < nodes.size(); ++i) {
            Node n = nodes.get(i);
            if (IdentifyBlocksPhase.trueSuccessorCount(n) > 1) {
                for (int j = 0; j < n.successors().size(); ++j) {
                    Node succ = n.successors().get(j);
                    if (IdentifyBlocksPhase.truePredecessorCount(succ) > 1) {
                        Anchor a = new Anchor(graph);
                        a.successors().setAndClear(Instruction.SUCCESSOR_NEXT, n, j);
                        n.successors().set(j, a);
                        n.successorTags()[j] = 1;
                    }
                }
            }
        }
    }
}
