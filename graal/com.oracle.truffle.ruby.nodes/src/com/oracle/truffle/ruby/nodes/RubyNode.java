/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes;

import java.math.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.call.*;
import com.oracle.truffle.ruby.nodes.debug.*;
import com.oracle.truffle.ruby.nodes.yield.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.core.array.*;
import com.oracle.truffle.ruby.runtime.core.range.*;
import com.oracle.truffle.ruby.runtime.methods.*;
import com.oracle.truffle.ruby.runtime.objects.*;

/**
 * Base class for most nodes in Ruby.
 * 
 * @see DispatchNode
 * @see YieldDispatchNode
 */
@TypeSystemReference(RubyTypes.class)
public abstract class RubyNode extends Node {

    private final RubyContext context;

    public RubyNode(RubyContext context, SourceSection sourceSection) {
        super(sourceSection);

        assert context != null;
        assert sourceSection != null;

        this.context = context;
    }

    public RubyNode(RubyNode prev) {
        this(prev.context, prev.getSourceSection());
    }

    public abstract Object execute(VirtualFrame frame);

    /**
     * Ruby's parallel semantic path.
     * 
     * @see DefinedNode
     */
    public Object isDefined(@SuppressWarnings("unused") VirtualFrame frame) {
        throw new UnsupportedOperationException("no definition for " + getClass().getName());
    }

    public RubyArray executeArray(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyArray(execute(frame));
    }

    public BigInteger executeBignum(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectBigInteger(execute(frame));
    }

    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectBoolean(execute(frame));
    }

    public RubyBignum executeBoxedBignum(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyBignum(execute(frame));
    }

    public RubyFixnum executeBoxedFixnum(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyFixnum(execute(frame));
    }

    public RubyFloat executeBoxedFloat(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyFloat(execute(frame));
    }

    public int executeFixnum(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectInteger(execute(frame));
    }

    public FixnumRange executeFixnumRange(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectFixnumRange(execute(frame));
    }

    public double executeFloat(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectDouble(execute(frame));
    }

    public NilPlaceholder executeNilPlaceholder(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectNilPlaceholder(execute(frame));
    }

    public Node executeNode(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectNode(execute(frame));
    }

    public Object[] executeObjectArray(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectObjectArray(execute(frame));
    }

    public ObjectRange executeObjectRange(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectObjectRange(execute(frame));
    }

    public RubyBasicObject executeRubyBasicObject(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyBasicObject(execute(frame));
    }

    public RubyBinding executeRubyBinding(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyBinding(execute(frame));
    }

    public RubyClass executeRubyClass(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyClass(execute(frame));
    }

    public RubyContinuation executeRubyContinuation(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyContinuation(execute(frame));
    }

    public RubyException executeRubyException(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyException(execute(frame));
    }

    public RubyFiber executeRubyFiber(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyFiber(execute(frame));
    }

    public RubyFile executeRubyFile(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyFile(execute(frame));
    }

    public RubyHash executeRubyHash(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyHash(execute(frame));
    }

    public RubyMatchData executeRubyMatchData(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyMatchData(execute(frame));
    }

    public RubyMethod executeRubyMethod(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyMethod(execute(frame));
    }

    public RubyModule executeRubyModule(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyModule(execute(frame));
    }

    public RubyNilClass executeRubyNilClass(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyNilClass(execute(frame));
    }

    public RubyObject executeRubyObject(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyObject(execute(frame));
    }

    public RubyProc executeRubyProc(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyProc(execute(frame));
    }

    public RubyRange executeRubyRange(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyRange(execute(frame));
    }

    public RubyRegexp executeRubyRegexp(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyRegexp(execute(frame));
    }

    public RubySymbol executeRubySymbol(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubySymbol(execute(frame));
    }

    public RubyThread executeRubyThread(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyThread(execute(frame));
    }

    public RubyTime executeRubyTime(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyTime(execute(frame));
    }

    public RubyString executeString(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectRubyString(execute(frame));
    }

    public UndefinedPlaceholder executeUndefinedPlaceholder(VirtualFrame frame) throws UnexpectedResultException {
        return RubyTypesGen.RUBYTYPES.expectUndefinedPlaceholder(execute(frame));
    }

    public void executeVoid(VirtualFrame frame) {
        execute(frame);
    }

    /**
     * If you aren't sure whether you have a normal {@link RubyNode} or a {@link RubyProxyNode},
     * this method will return the real node, whether that is this node, or whether this node is a
     * proxy and you actually need the child.
     */
    public RubyNode getNonProxyNode() {
        return this;
    }

    public RubyContext getContext() {
        return context;
    }

}
