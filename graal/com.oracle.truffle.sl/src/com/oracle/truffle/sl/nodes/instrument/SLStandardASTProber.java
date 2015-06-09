/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.instrument;

import static com.oracle.truffle.api.instrument.StandardSyntaxTag.*;

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.nodes.controlflow.*;
import com.oracle.truffle.sl.nodes.local.*;

/**
 * A visitor which traverses a completely parsed Simple AST (presumed not yet executed) and enables
 * instrumentation at a few standard kinds of nodes.
 */
public class SLStandardASTProber implements NodeVisitor, ASTProber {

    /**
     * {@inheritDoc}
     * <p>
     * Instruments and tags all relevant {@link SLStatementNode}s and {@link SLExpressionNode}s.
     * Currently, only SLStatementNodes that are not SLExpressionNodes are tagged as statements.
     */
    public boolean visit(Node node) {

        if (!(node instanceof InstrumentationNode) && node instanceof SLStatementNode && node.getParent() != null && node.getSourceSection() != null) {
            // All SL nodes are instrumentable, but treat expressions specially

            if (node instanceof SLExpressionNode) {
                SLExpressionNode expressionNode = (SLExpressionNode) node;
                Probe probe = expressionNode.probe();
                if (node instanceof SLWriteLocalVariableNode) {
                    probe.tagAs(STATEMENT, null);
                    probe.tagAs(ASSIGNMENT, null);
                }
            } else {
                SLStatementNode statementNode = (SLStatementNode) node;
                Probe probe = statementNode.probe();
                probe.tagAs(STATEMENT, null);
                if (node instanceof SLWhileNode) {
                    probe.tagAs(START_LOOP, null);
                }
            }
        }
        return true;
    }

    public void probeAST(Node node) {
        node.accept(this);
    }
}
