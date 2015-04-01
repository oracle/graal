/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.debug;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.io.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.truffle.*;
import com.oracle.graal.truffle.TruffleInlining.CallTreeNodeVisitor;
import com.oracle.truffle.api.nodes.*;

public final class TraceCompilationASTListener extends AbstractDebugCompilationListener {

    public static void install(GraalTruffleRuntime runtime) {
        if (TraceTruffleCompilationAST.getValue()) {
            runtime.addCompilationListener(new TraceCompilationASTListener());
        }
    }

    @Override
    public void notifyCompilationSuccess(OptimizedCallTarget target, StructuredGraph graph, CompilationResult result) {
        log(0, "opt AST", target.toString(), target.getDebugProperties());
        printCompactTree(new PrintWriter(OUT), target);
    }

    private static void printCompactTree(PrintWriter p, OptimizedCallTarget target) {
        target.accept(new CallTreeNodeVisitor() {

            public boolean visit(List<TruffleInlining> decisionStack, Node node) {
                if (node == null) {
                    return true;
                }
                int level = CallTreeNodeVisitor.getNodeDepth(decisionStack, node);
                for (int i = 0; i < level; i++) {
                    p.print("  ");
                }
                Node parent = node.getParent();

                if (parent == null) {
                    p.println(node.getClass().getSimpleName());
                } else {
                    String fieldName = "unknownField";
                    NodeFieldAccessor[] fields = NodeClass.get(parent.getClass()).getFields();
                    for (NodeFieldAccessor field : fields) {
                        Object value = field.loadValue(parent);
                        if (value == node) {
                            fieldName = field.getName();
                            break;
                        } else if (value instanceof Node[]) {
                            int index = 0;
                            for (Node arrayNode : (Node[]) value) {
                                if (arrayNode == node) {
                                    fieldName = field.getName() + "[" + index + "]";
                                    break;
                                }
                                index++;
                            }
                        }
                    }
                    p.print(fieldName);
                    p.print(" = ");
                    p.println(node.getClass().getSimpleName());
                }
                p.flush();
                return true;
            }

        }, true);
    }

}
