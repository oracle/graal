/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.instrument;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.sl.nodes.SLExpressionNode;
import com.oracle.truffle.sl.nodes.SLStatementNode;
import com.oracle.truffle.sl.nodes.controlflow.SLBlockNode;
import com.oracle.truffle.sl.nodes.controlflow.SLFunctionBodyNode;
import com.oracle.truffle.sl.nodes.controlflow.SLWhileNode;
import com.oracle.truffle.sl.nodes.local.SLReadArgumentNode;
import com.oracle.truffle.sl.nodes.local.SLWriteLocalVariableNode;

/**
 * A visitor which traverses a completely parsed Simple AST (presumed not yet executed) and enables
 * {@linkplain Instrumenter Instrumentation} at a few standard kinds of nodes.
 */
@Deprecated
@SuppressWarnings("deprecation")
public class SLStandardASTProber implements com.oracle.truffle.api.instrument.ASTProber {

    public void probeAST(final com.oracle.truffle.api.instrument.Instrumenter instrumenter, RootNode startNode) {
        startNode.accept(new NodeVisitor() {

            public boolean visit(Node node) {

                if (!(node instanceof com.oracle.truffle.api.instrument.InstrumentationNode) && node instanceof SLStatementNode && node.getParent() != null && node.getSourceSection() != null) {

                    // Skip nodes that don't really correspond to statements
                    if (node instanceof SLFunctionBodyNode || node instanceof SLBlockNode || node instanceof SLReadArgumentNode) {
                        return true;
                    }

                    // Skip synthetic statements at beginning of function body that assign locals
                    if (node instanceof SLWriteLocalVariableNode) {
                        final Node child = node.getChildren().iterator().next();
                        if (child instanceof SLReadArgumentNode) {
                            return true;
                        }
                    }
                    if (node instanceof SLExpressionNode) {
                        SLExpressionNode expressionNode = (SLExpressionNode) node;
                        final com.oracle.truffle.api.instrument.Probe probe = instrumenter.probe(expressionNode);
                        if (node instanceof SLWriteLocalVariableNode) {
                            probe.tagAs(com.oracle.truffle.api.instrument.StandardSyntaxTag.STATEMENT, null);
                            probe.tagAs(com.oracle.truffle.api.instrument.StandardSyntaxTag.ASSIGNMENT, null);
                        }
                    } else {
                        if (!(node.getParent() instanceof SLFunctionBodyNode)) {
                            SLStatementNode statementNode = (SLStatementNode) node;
                            final com.oracle.truffle.api.instrument.Probe probe = instrumenter.probe(statementNode);
                            probe.tagAs(com.oracle.truffle.api.instrument.StandardSyntaxTag.STATEMENT, null);
                            if (node instanceof SLWhileNode) {
                                probe.tagAs(com.oracle.truffle.api.instrument.StandardSyntaxTag.START_LOOP, null);
                            }
                        }
                    }
                }
                return true;
            }
        });
    }
}
