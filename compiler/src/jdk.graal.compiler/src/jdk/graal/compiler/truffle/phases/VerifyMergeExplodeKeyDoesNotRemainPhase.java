/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.phases;

import jdk.graal.compiler.graph.GraalGraphError;
import jdk.graal.compiler.nodes.LoopExplosionKeyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.truffle.TruffleTierContext;

/**
 * Compiler phase for verifying that nodes created with `CompilerDirectives.mergeExplodeKey` get
 * fully processed and don't remain in the graph.
 */
public class VerifyMergeExplodeKeyDoesNotRemainPhase extends BasePhase<TruffleTierContext> {
    @Override
    protected void run(StructuredGraph graph, TruffleTierContext context) {
        graph.checkCancellation();
        for (LoopExplosionKeyNode node : graph.getNodes(LoopExplosionKeyNode.TYPE)) {
            Throwable exception = new GraalGraphError("`CompilerDirectives.mergeExplodeKey` must only be used with a merge exploded loop.");
            throw GraphUtil.approxSourceException(node, exception);
        }
    }
}
