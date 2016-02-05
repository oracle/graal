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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrument.EventHandlerNode;
import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.instrument.Probe;
import com.oracle.truffle.api.instrument.ProbeInstrument;
import com.oracle.truffle.api.instrument.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.sl.nodes.SLExpressionNode;
import com.oracle.truffle.sl.nodes.SLTypesGen;
import com.oracle.truffle.sl.runtime.SLFunction;

/**
 * A Truffle node that can be inserted into a Simple AST (assumed not to have executed yet) to
 * enable {@linkplain Instrumenter Instrumentation} of an {@link SLExpressionNode}. Tools wishing to
 * interact with AST execution may attach {@link ProbeInstrument}s to the {@link Probe} uniquely
 * associated with the wrapper, and to which this wrapper routes execution events.
 */
@NodeInfo(cost = NodeCost.NONE)
public final class SLExpressionWrapperNode extends SLExpressionNode implements WrapperNode {
    @Child private SLExpressionNode child;
    @Child private EventHandlerNode eventHandlerNode;

    /**
     * Constructor.
     *
     * @param child The {@link SLExpressionNode} that this wrapper is wrapping
     */
    public SLExpressionWrapperNode(SLExpressionNode child) {
        super(child.getSourceSection());
        assert !(child instanceof SLExpressionWrapperNode);
        this.child = child;
    }

    public String instrumentationInfo() {
        return "Wrapper node for SL Expressions";
    }

    @Override
    public SLExpressionNode getNonWrapperNode() {
        return child;
    }

    public void insertEventHandlerNode(EventHandlerNode eventHandler) {
        this.eventHandlerNode = eventHandler;
    }

    public Probe getProbe() {
        return eventHandlerNode.getProbe();
    }

    public Node getChild() {
        return child;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {

        eventHandlerNode.enter(child, frame);
        Object result;

        try {
            result = child.executeGeneric(frame);
            eventHandlerNode.returnValue(child, frame, result);
        } catch (Exception e) {
            eventHandlerNode.returnExceptional(child, frame, e);
            throw (e);
        }
        return result;
    }

    @Override
    public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
        return SLTypesGen.expectLong(executeGeneric(frame));
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
        return SLTypesGen.expectBoolean(executeGeneric(frame));
    }

    @Override
    public SLFunction executeFunction(VirtualFrame frame) throws UnexpectedResultException {
        eventHandlerNode.enter(child, frame);
        SLFunction result;

        try {
            result = child.executeFunction(frame);
            eventHandlerNode.returnValue(child, frame, result);
        } catch (Exception e) {
            eventHandlerNode.returnExceptional(child, frame, e);
            throw (e);
        }
        return result;
    }

}
