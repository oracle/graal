/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.nodes.asserts;

import static org.graalvm.compiler.nodeinfo.InputType.State;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.graalvm.compiler.core.common.GraalBailoutException;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.util.GraphUtil;

@NodeInfo(cycles = CYCLES_0, size = SIZE_0)
public final class NeverPartOfCompilationNode extends ControlSinkNode implements StateSplit, IterableNodeType {

    public static final NodeClass<NeverPartOfCompilationNode> TYPE = NodeClass.create(NeverPartOfCompilationNode.class);
    protected final String message;
    @OptionalInput(State) protected FrameState stateAfter;

    public NeverPartOfCompilationNode(String message) {
        super(TYPE, StampFactory.forVoid());
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    public static void verifyNotFoundIn(final StructuredGraph graph) {
        for (NeverPartOfCompilationNode neverPartOfCompilationNode : graph.getNodes(NeverPartOfCompilationNode.TYPE)) {
            final NeverPartOfCompilationException neverPartOfCompilationException = new NeverPartOfCompilationException(neverPartOfCompilationNode.getMessage());
            neverPartOfCompilationException.setStackTrace(GraphUtil.approxSourceStackTraceElement(neverPartOfCompilationNode));
            throw neverPartOfCompilationException;
        }
    }

    @SuppressWarnings("serial")
    private static class NeverPartOfCompilationException extends GraalBailoutException {

        NeverPartOfCompilationException(String message) {
            super(null, message, new Object[]{});
        }

        @Override
        public boolean isCausedByCompilerAssert() {
            return true;
        }
    }
}
