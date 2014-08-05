/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.test.instrument;

import static com.oracle.truffle.api.instrument.StandardSyntaxTag.*;

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.nodes.controlflow.*;
import com.oracle.truffle.sl.nodes.instrument.*;
import com.oracle.truffle.sl.nodes.local.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * This sample AST Node Prober for simple is used to instrument the nodes that we are interested in
 * testing. This prober wraps return nodes and assignment nodes. For the purposes of this example,
 * this is appropriate, but ideally there would be only one node prober responsible for
 * instrumenting all the nodes of interest instead of a selective one like this one.
 *
 */
public final class SLInstrumentTestNodeProber implements SLNodeProber {
    private final SLContext slContext;

    public SLInstrumentTestNodeProber(SLContext slContext) {
        this.slContext = slContext;
    }

    /**
     * Not implemented, only returns the astNode that was passed in.
     */
    public Node probeAs(Node astNode, SyntaxTag tag, Object... args) {
        // TODO dp: Currently does nothing in the general case
        return astNode;
    }

    /**
     * If the passed in node is a {@link SLStatementWrapper}, then this simply tags it as a
     * statement. If the passed in node is a {@link SLReturnNode}, then it is instrumented and
     * tagged as a statement for testing. Only SLReturnNodes are wrapped.
     */
    public SLStatementNode probeAsStatement(SLStatementNode node) {
        assert node != null;

        SLStatementWrapper wrapper = null;
        if (node instanceof SLStatementWrapper) {
            wrapper = (SLStatementWrapper) node;
            tagStatementNode(wrapper);
            return wrapper;
        } else if (node instanceof SLReturnNode) {
            wrapper = new SLStatementWrapper(slContext, node);
            tagStatementNode(wrapper);
            return wrapper;
        }
        return node;
    }

    /**
     * Not implemented. Returns the passed in node.
     */
    public SLExpressionNode probeAsCall(SLExpressionNode node, String callName) {
        return node;
    }

    /**
     * If the passed in node is a {@link SLExpressionWrapper}, then this simply tags it as an
     * assignment. If the passed in node is a {@link SLWriteLocalVariableNode}, then it is
     * instrumented and tagged as a assignment for testing. Only SLWriteLocalVariableNode are
     * wrapped.
     */
    public SLExpressionNode probeAsLocalAssignment(SLExpressionNode node, String localName) {
        assert node != null;

        SLExpressionWrapper wrapper = null;
        if (node instanceof SLExpressionWrapper) {
            wrapper = (SLExpressionWrapper) node;
            tagAssignmentNode(wrapper);
            return wrapper;
        } else if (node instanceof SLWriteLocalVariableNode) {
            wrapper = new SLExpressionWrapper(slContext, node);
            tagAssignmentNode(wrapper);
            return wrapper;
        }
        return node;
    }

    private static void tagAssignmentNode(SLExpressionWrapper wrapper) {
        if (!wrapper.isTaggedAs(ASSIGNMENT)) {
            wrapper.tagAs(ASSIGNMENT);
        }
    }

    private static void tagStatementNode(SLStatementWrapper wrapper) {
        if (!wrapper.isTaggedAs(STATEMENT)) {
            wrapper.tagAs(STATEMENT);
        }
    }

}