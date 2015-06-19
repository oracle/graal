/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.loop.phases;

import com.oracle.graal.loop.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.jvmci.debug.*;

public class LoopPeelingPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        if (graph.hasLoops()) {
            LoopsData data = new LoopsData(graph);
            for (LoopEx loop : data.outerFirst()) {
                if (LoopPolicies.shouldPeel(loop, data.getCFG())) {
                    Debug.log("Peeling %s", loop);
                    LoopTransformations.peel(loop);
                    Debug.dump(graph, "Peeling %s", loop);
                }
            }
            data.deleteUnusedNodes();
        }
    }
}
