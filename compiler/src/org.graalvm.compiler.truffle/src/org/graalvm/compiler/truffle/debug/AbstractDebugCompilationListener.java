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

import java.util.Map;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.GraalTruffleCompilationListener;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.TruffleInlining;

import com.oracle.truffle.api.frame.Frame;

public abstract class AbstractDebugCompilationListener implements GraalTruffleCompilationListener {

    @Override
    public void notifyCompilationQueued(OptimizedCallTarget target) {
    }

    @Override
    public void notifyCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason) {
    }

    @Override
    public void notifyCompilationFailed(OptimizedCallTarget target, StructuredGraph graph, Throwable t) {
    }

    @Override
    public void notifyCompilationStarted(OptimizedCallTarget target) {
    }

    @Override
    public void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph) {
    }

    @Override
    public void notifyCompilationGraalTierFinished(OptimizedCallTarget target, StructuredGraph graph) {
    }

    @Override
    public void notifyCompilationSplit(OptimizedDirectCallNode callNode) {
    }

    @Override
    public void notifyCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph, CompilationResult result) {
    }

    @Override
    public void notifyCompilationDeoptimized(OptimizedCallTarget target, Frame frame) {
    }

    @Override
    public void notifyCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
    }

    @Override
    public void notifyShutdown(GraalTruffleRuntime runtime) {
    }

    @Override
    public void notifyStartup(GraalTruffleRuntime runtime) {
    }

    public static void log(int indent, String msg, String details, Map<String, Object> properties) {
        int spaceIndent = indent * 2;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[truffle] %-16s ", msg));
        for (int i = 0; i < spaceIndent; i++) {
            sb.append(' ');
        }
        sb.append(String.format("%-" + (60 - spaceIndent) + "s", details));
        if (properties != null) {
            for (String property : properties.keySet()) {
                Object value = properties.get(property);
                if (value == null) {
                    continue;
                }
                sb.append('|');
                sb.append(property);

                StringBuilder propertyBuilder = new StringBuilder();
                if (value instanceof Integer) {
                    propertyBuilder.append(String.format("%6d", value));
                } else if (value instanceof Double) {
                    propertyBuilder.append(String.format("%8.2f", value));
                } else {
                    propertyBuilder.append(value);
                }

                int length = Math.max(1, 20 - property.length());
                sb.append(String.format(" %" + length + "s ", propertyBuilder.toString()));
            }
        }
        OptimizedCallTarget.log(sb.toString());
    }

    public static void addASTSizeProperty(OptimizedCallTarget target, TruffleInlining inliningDecision, Map<String, Object> properties) {
        int nodeCount = target.getNonTrivialNodeCount();
        int deepNodeCount = nodeCount;
        if (inliningDecision != null) {
            deepNodeCount += inliningDecision.getInlinedNodeCount();
        }
        properties.put("ASTSize", String.format("%5d/%5d", nodeCount, deepNodeCount));

    }

}
