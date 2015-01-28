/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.math.*;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.ProbeNode.WrapperNode;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * A Truffle node that can be inserted into a Simple AST (assumed not to have executed yet) to
 * enable "instrumentation" of an {@link SLExpressionNode}. Tools wishing to interact with AST
 * execution may attach {@link Instrument}s to the {@link Probe} uniquely associated with the
 * wrapper, and to which this wrapper routes execution events.
 */
@NodeInfo(cost = NodeCost.NONE)
public final class SLExpressionWrapperNode extends SLExpressionNode implements WrapperNode {
    @Child private SLExpressionNode child;
    @Child private ProbeNode probeNode;

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
    public boolean isInstrumentable() {
        return false;
    }

    @Override
    public SLExpressionNode getNonWrapperNode() {
        return child;
    }

    public void insertProbe(ProbeNode newProbeNode) {
        this.probeNode = newProbeNode;
    }

    public Probe getProbe() {
        try {
            return probeNode.getProbe();
        } catch (IllegalStateException e) {
            throw new IllegalStateException("A lite-Probed wrapper has no explicit Probe");
        }
    }

    public Node getChild() {
        return child;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {

        probeNode.enter(child, frame);
        Object result;

        try {
            result = child.executeGeneric(frame);
            probeNode.returnValue(child, frame, result);
        } catch (Exception e) {
            probeNode.returnExceptional(child, frame, e);
            throw (e);
        }
        return result;
    }

    @Override
    public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
        return SLTypesGen.expectLong(executeGeneric(frame));
    }

    @Override
    public BigInteger executeBigInteger(VirtualFrame frame) throws UnexpectedResultException {
        return SLTypesGen.expectBigInteger(executeGeneric(frame));
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
        return SLTypesGen.expectBoolean(executeGeneric(frame));
    }

    @Override
    public String executeString(VirtualFrame frame) throws UnexpectedResultException {
        return SLTypesGen.expectString(executeGeneric(frame));
    }

    @Override
    public SLFunction executeFunction(VirtualFrame frame) throws UnexpectedResultException {
        probeNode.enter(child, frame);
        SLFunction result;

        try {
            result = child.executeFunction(frame);
            probeNode.returnValue(child, frame, result);
        } catch (Exception e) {
            probeNode.returnExceptional(child, frame, e);
            throw (e);
        }
        return result;
    }

    @Override
    public SLNull executeNull(VirtualFrame frame) throws UnexpectedResultException {
        return SLTypesGen.expectSLNull(executeGeneric(frame));
    }
}
