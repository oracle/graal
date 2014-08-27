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
package com.oracle.truffle.sl.nodes.instrument;

import static com.oracle.truffle.api.instrument.StandardSyntaxTag.*;

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.nodes.controlflow.*;
import com.oracle.truffle.sl.nodes.local.*;

/**
 * A visitor which traverses a completely parsed Simple AST (presumed not yet executed) and
 * instruments some of them.
 */
public class SLInstrumenter implements NodeVisitor {

    public SLInstrumenter() {
    }

    /**
     * Instruments and tags all relevant {@link SLStatementNode}s and {@link SLExpressionNode}s.
     * Currently, only SLStatementNodes that are not SLExpressionNodes are tagged as statements.
     */
    public boolean visit(Node node) {
        // We have to distinguish between SLExpressionNode and SLStatementNode since some of the
        // generated factories have methods that require SLExpressionNodes as parameters. Since
        // SLExpressionNodes are a subclass of SLStatementNode, we check if something is an
        // SLExpressionNode first.
        if (node instanceof SLExpressionNode && node.getParent() != null) {
            SLExpressionNode expressionNode = (SLExpressionNode) node;
            if (expressionNode.getSourceSection() != null) {
                Probe probe = expressionNode.probe();
                // probe.tagAs(STATEMENT);

                if (node instanceof SLWriteLocalVariableNode)
                    probe.tagAs(ASSIGNMENT);
            }
        } else if (node instanceof SLStatementNode && node.getParent() != null) {

            SLStatementNode statementNode = (SLStatementNode) node;
            if (statementNode.getSourceSection() != null) {
                Probe probe = statementNode.probe();
                probe.tagAs(STATEMENT);

                if (node instanceof SLWhileNode)
                    probe.tagAs(START_LOOP);
            }
        }

        return true;
    }
}
