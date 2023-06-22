/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.debug;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.graalvm.compiler.truffle.common.TruffleCompilerListener.CompilationResultInfo;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener.GraphInfo;
import org.graalvm.compiler.truffle.runtime.AbstractCompilationTask;
import org.graalvm.compiler.truffle.runtime.AbstractGraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedRuntimeOptions;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;

/**
 * Traces all polymorphic and generic nodes after each successful Truffle compilation.
 */
public final class TraceASTCompilationListener extends AbstractGraalTruffleRuntimeListener {

    private TraceASTCompilationListener(GraalTruffleRuntime runtime) {
        super(runtime);
    }

    public static void install(GraalTruffleRuntime runtime) {
        runtime.addListener(new TraceASTCompilationListener(runtime));
    }

    @Override
    public void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, GraphInfo graphInfo, CompilationResultInfo compilationResultInfo) {
        if (target.getOptionValue(OptimizedRuntimeOptions.TraceCompilationAST)) {
            StringWriter logMessage = new StringWriter();
            try (PrintWriter out = new PrintWriter(logMessage)) {
                printCompactTree(out, target);
            }
            runtime.logEvent(target, 0, "opt AST", target.toString(), target.getDebugProperties(), logMessage.toString());
        }
    }

    private static void printCompactTree(PrintWriter out, OptimizedCallTarget target) {
        target.accept(new NodeVisitor() {
            private boolean newLine = false;

            @Override
            public boolean visit(Node node) {
                if (node == null) {
                    return true;
                }
                Node parent = node.getParent();
                if (newLine) {
                    out.println();
                } else {
                    newLine = true;
                }
                if (parent == null) {
                    out.printf("%s", node.getClass().getSimpleName());
                } else {
                    String fieldName = NodeUtil.findChildFieldName(parent, node);
                    out.printf("%s = %s", fieldName, node.getClass().getSimpleName());
                }
                return true;
            }

        });
    }
}
