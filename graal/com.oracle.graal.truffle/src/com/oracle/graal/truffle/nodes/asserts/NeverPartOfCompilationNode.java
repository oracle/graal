/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.nodes.asserts;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;

@NodeInfo
public final class NeverPartOfCompilationNode extends ControlSinkNode implements StateSplit, IterableNodeType {

    public static final NodeClass<NeverPartOfCompilationNode> TYPE = NodeClass.create(NeverPartOfCompilationNode.class);
    protected final String message;
    @OptionalInput(InputType.State) protected FrameState stateAfter;

    public NeverPartOfCompilationNode(String message) {
        super(TYPE, StampFactory.forVoid());
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public FrameState stateAfter() {
        return stateAfter;
    }

    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    public boolean hasSideEffect() {
        return true;
    }

    public static void verifyNotFoundIn(final StructuredGraph graph) {
        for (NeverPartOfCompilationNode neverPartOfCompilationNode : graph.getNodes(NeverPartOfCompilationNode.TYPE)) {
            Throwable exception = new VerificationError(neverPartOfCompilationNode.getMessage());
            throw GraphUtil.approxSourceException(neverPartOfCompilationNode, exception);
        }
    }
}
