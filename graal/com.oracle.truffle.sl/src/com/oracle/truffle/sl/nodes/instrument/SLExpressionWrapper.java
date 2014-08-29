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

import java.math.*;

import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * A Truffle node that can be inserted into a Simple AST (assumed not to have executed yet) to
 * enable "instrumentation" of an {@link SLExpressionNode}. Tools wishing to interact with AST
 * execution may attach {@link Instrument}s to the {@link Probe} uniquely associated with the
 * wrapper, and to which this wrapper routes {@link ExecutionEvents}.
 */

public final class SLExpressionWrapper extends SLExpressionNode implements Wrapper {
    @Child private SLExpressionNode child;

    private final Probe probe;

    /**
     * Constructor.
     *
     * @param context The current Simple execution context
     * @param child The {@link SLExpressionNode} that this wrapper is wrapping
     */
    public SLExpressionWrapper(SLContext context, SLExpressionNode child) {
        super(child.getSourceSection());
        assert !(child instanceof SLExpressionWrapper);
        this.probe = context.createProbe(child.getSourceSection());
        this.child = child;
        // The child should only be inserted after a replace, so we defer inserting the child to the
        // creator of the wrapper.
    }

    @Override
    public SLExpressionNode getNonWrapperNode() {
        return child;
    }

    @Override
    public Node getChild() {
        return child;
    }

    @Override
    public Probe getProbe() {
        return probe;
    }

    @SlowPath
    public void tagAs(SyntaxTag tag) {
        probe.tagAs(tag);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        this.tagAs(StandardSyntaxTag.STATEMENT);
        probe.enter(child, frame);
        Object result;

        try {
            result = child.executeGeneric(frame);
            probe.leave(child, frame, result);
        } catch (Exception e) {
            probe.leaveExceptional(child, frame, e);
            throw (e);
        }
        return result;
    }

    @Override
    public long executeLong(VirtualFrame frame) throws UnexpectedResultException {
        this.tagAs(StandardSyntaxTag.STATEMENT);
        return SLTypesGen.SLTYPES.expectLong(executeGeneric(frame));
    }

    @Override
    public BigInteger executeBigInteger(VirtualFrame frame) throws UnexpectedResultException {
        this.tagAs(StandardSyntaxTag.STATEMENT);
        return SLTypesGen.SLTYPES.expectBigInteger(executeGeneric(frame));
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
        this.tagAs(StandardSyntaxTag.STATEMENT);
        return SLTypesGen.SLTYPES.expectBoolean(executeGeneric(frame));
    }

    @Override
    public String executeString(VirtualFrame frame) throws UnexpectedResultException {
        this.tagAs(StandardSyntaxTag.STATEMENT);
        return SLTypesGen.SLTYPES.expectString(executeGeneric(frame));
    }

    @Override
    public SLFunction executeFunction(VirtualFrame frame) throws UnexpectedResultException {
        this.tagAs(StandardSyntaxTag.STATEMENT);
        probe.enter(child, frame);
        SLFunction result;

        try {
            result = child.executeFunction(frame);
            probe.leave(child, frame, result);
        } catch (Exception e) {
            probe.leaveExceptional(child, frame, e);
            throw (e);
        }
        return result;
    }

    @Override
    public SLNull executeNull(VirtualFrame frame) throws UnexpectedResultException {
        return SLTypesGen.SLTYPES.expectSLNull(executeGeneric(frame));
    }

}
