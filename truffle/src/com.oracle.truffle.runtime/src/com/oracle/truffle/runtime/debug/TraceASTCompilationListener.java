/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime.debug;

import java.io.PrintWriter;
import java.io.StringWriter;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.compiler.TruffleCompilerListener.CompilationResultInfo;
import com.oracle.truffle.compiler.TruffleCompilerListener.GraphInfo;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.AbstractGraalTruffleRuntimeListener;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedRuntimeOptions;

/**
 * Traces all polymorphic and generic nodes after each successful Truffle compilation.
 */
public final class TraceASTCompilationListener extends AbstractGraalTruffleRuntimeListener {

    private TraceASTCompilationListener(OptimizedTruffleRuntime runtime) {
        super(runtime);
    }

    public static void install(OptimizedTruffleRuntime runtime) {
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
