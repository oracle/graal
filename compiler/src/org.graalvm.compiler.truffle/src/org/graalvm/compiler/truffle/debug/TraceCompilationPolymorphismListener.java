/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.debug;

import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TraceTruffleCompilationPolymorphism;

import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.TruffleInlining;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeUtil;

public final class TraceCompilationPolymorphismListener extends AbstractDebugCompilationListener {

    private TraceCompilationPolymorphismListener() {
    }

    public static void install(GraalTruffleRuntime runtime) {
        if (TruffleCompilerOptions.getValue(TraceTruffleCompilationPolymorphism)) {
            runtime.addCompilationListener(new TraceCompilationPolymorphismListener());
        }
    }

    @Override
    public void notifyCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph, CompilationResult result) {
        super.notifyCompilationSuccess(target, inliningDecision, graph, result);

        for (Node node : target.nodeIterable(inliningDecision)) {
            if (node != null && (node.getCost() == NodeCost.MEGAMORPHIC || node.getCost() == NodeCost.POLYMORPHIC)) {
                NodeCost cost = node.getCost();
                Map<String, Object> props = new LinkedHashMap<>();
                props.put("simpleName", node.getClass().getSimpleName());
                props.put("subtree", "\n" + NodeUtil.printCompactTreeToString(node));
                String msg = cost == NodeCost.MEGAMORPHIC ? "megamorphic" : "polymorphic";
                log(0, msg, node.toString(), props);
            }
        }
    }

}
