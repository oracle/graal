/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.*;

/**
 * SLASTProber contains the collection of {@link SLNodeProber}s and methods to attach the probers to
 * nodes.
 */
public final class SLASTProber implements ASTProber, SLNodeProber {

    private ArrayList<SLNodeProber> nodeProbers = new ArrayList<>();

    public SLASTProber() {
    }

    /**
     * Adds a {@link SLNodeProber} to this SLASTProber. Probes must be of type {@link SLNodeProber}
     * and must not already have been added.
     *
     * @param nodeProber the {@link SLNodeProber} to add.
     */
    public void addNodeProber(ASTNodeProber nodeProber) {
        if (nodeProber instanceof SLNodeProber) {
            assert !nodeProbers.contains(nodeProber);
            nodeProbers.add((SLNodeProber) nodeProber);
        } else {
            throw new IllegalArgumentException("invalid prober for SL implementation");
        }
    }

    /**
     * Unimplemented, does nothing.
     */
    public Node probeAs(Node astNode, SyntaxTag tag, Object... args) {
        return astNode;
    }

    /**
     * Attaches the current probers to the given {@link SLStatementNode} as a statement.
     *
     * @param node The {@link SLStatementNode} to attach the stored set of probers to.
     */
    @Override
    public SLStatementNode probeAsStatement(SLStatementNode node) {
        SLStatementNode result = node;
        for (SLNodeProber nodeProber : nodeProbers) {
            result = nodeProber.probeAsStatement(result);
        }
        return result;
    }

    /**
     * Attaches the current probers to the given {@link SLExpressionNode} as a call. This will wrap
     * the passed in node in an {@link SLExpressionWrapper}, tag it as a call and attach an
     * instrument to it.
     *
     * @param node The {@link SLExpressionNode} to attach the stored set of probers to.
     * @param callName The name of the call ???
     *
     */
    @Override
    public SLExpressionNode probeAsCall(SLExpressionNode node, String callName) {
        SLExpressionNode result = node;
        for (SLNodeProber nodeProber : nodeProbers) {
            result = nodeProber.probeAsCall(node, callName);
        }
        return result;
    }

    /**
     * Attaches the current probers to the given {@link SLExpressionNode} as an assignment. This
     * will wrap the passed in node in an {@link SLExpressionWrapper}, tag it as an assignment and
     * attach an instrument to it.
     *
     * @param node The {@link SLExpressionNode} to attached the stored set of probers to.
     * @param localName The name of the assignment ???
     *
     */
    @Override
    public SLExpressionNode probeAsLocalAssignment(SLExpressionNode node, String localName) {
        SLExpressionNode result = node;
        for (SLNodeProber nodeProber : nodeProbers) {
            result = nodeProber.probeAsLocalAssignment(result, localName);
        }
        return result;
    }

    public ASTNodeProber getCombinedNodeProber() {
        return nodeProbers.isEmpty() ? null : this;
    }

}
