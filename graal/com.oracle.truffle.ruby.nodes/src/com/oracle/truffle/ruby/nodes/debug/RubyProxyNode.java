/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.debug;

import java.math.*;
import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.instrument.*;
import com.oracle.truffle.api.nodes.instrument.InstrumentationProbeNode.ProbeChain;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.core.array.*;

/**
 * An <strong>instrumentation proxy node</strong> that forwards all Ruby execution calls through to
 * a child node and returns results back to the parent, but which also sends notifications to an
 * attached {@linkplain ProbeChain chain} of {@linkplain InstrumentationProbeNode probes}.
 */
public class RubyProxyNode extends RubyNode implements InstrumentationProxyNode {

    @Child private RubyNode child;

    private final ProbeChain probeChain;

    public RubyProxyNode(RubyContext context, RubyNode child) {
        super(context, SourceSection.NULL);
        assert !(child instanceof RubyProxyNode);
        this.child = adoptChild(child);
        this.probeChain = context.getDebugManager().getProbeChain(child.getSourceSection());
    }

    public RubyProxyNode(RubyContext context, RubyNode child, ProbeChain probeChain) {
        super(context, SourceSection.NULL);
        assert !(child instanceof RubyProxyNode);
        this.child = adoptChild(child);
        this.probeChain = probeChain;
    }

    @Override
    public RubyNode getNonProxyNode() {
        return child;
    }

    public RubyNode getChild() {
        return child;
    }

    public ProbeChain getProbeChain() {
        return probeChain;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        probeChain.notifyEnter(child, frame);

        Object result;

        try {
            result = child.execute(frame);
            probeChain.notifyLeave(child, frame, result);
        } catch (BreakShellException e) {
            throw (e);
        } catch (Exception e) {
            probeChain.notifyLeaveExceptional(child, frame, e);
            throw (e);
        }

        return result;
    }

    @Override
    public RubyArray executeArray(VirtualFrame frame) throws UnexpectedResultException {
        probeChain.notifyEnter(child, frame);

        RubyArray result;

        try {
            result = child.executeArray(frame);
            probeChain.notifyLeave(child, frame, result);
        } catch (BreakShellException e) {
            throw (e);
        } catch (Exception e) {
            probeChain.notifyLeaveExceptional(child, frame, e);
            throw (e);
        }

        return result;
    }

    @Override
    public BigInteger executeBignum(VirtualFrame frame) throws UnexpectedResultException {
        probeChain.notifyEnter(child, frame);

        BigInteger result;

        try {
            result = child.executeBignum(frame);
            probeChain.notifyLeave(child, frame, result);
        } catch (BreakShellException e) {
            throw (e);
        } catch (Exception e) {
            probeChain.notifyLeaveExceptional(child, frame, e);
            throw (e);
        }

        return result;
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException {
        probeChain.notifyEnter(child, frame);

        boolean result;

        try {
            result = child.executeBoolean(frame);
            probeChain.notifyLeave(child, frame, result);
        } catch (BreakShellException e) {
            throw (e);
        } catch (Exception e) {
            probeChain.notifyLeaveExceptional(child, frame, e);
            throw (e);
        }

        return result;
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        return child.isDefined(frame);
    }

    @Override
    public int executeFixnum(VirtualFrame frame) throws UnexpectedResultException {
        probeChain.notifyEnter(child, frame);

        int result;

        try {
            result = child.executeFixnum(frame);
            probeChain.notifyLeave(child, frame, result);
        } catch (BreakShellException e) {
            throw (e);
        } catch (Exception e) {
            probeChain.notifyLeaveExceptional(child, frame, e);
            throw (e);
        }

        return result;
    }

    @Override
    public double executeFloat(VirtualFrame frame) throws UnexpectedResultException {
        probeChain.notifyEnter(child, frame);

        double result;

        try {
            result = child.executeFloat(frame);
            probeChain.notifyLeave(child, frame, result);
        } catch (BreakShellException e) {
            throw (e);
        } catch (Exception e) {
            probeChain.notifyLeaveExceptional(child, frame, e);
            throw (e);
        }

        return result;
    }

    @Override
    public RubyString executeString(VirtualFrame frame) throws UnexpectedResultException {
        probeChain.notifyEnter(child, frame);

        RubyString result;

        try {
            result = child.executeString(frame);
            probeChain.notifyLeave(child, frame, result);
        } catch (BreakShellException e) {
            throw (e);
        } catch (Exception e) {
            probeChain.notifyLeaveExceptional(child, frame, e);
            throw (e);
        }

        return result;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        probeChain.notifyEnter(child, frame);

        try {
            child.executeVoid(frame);
            probeChain.notifyLeave(child, frame);
        } catch (BreakShellException e) {
            throw (e);
        } catch (Exception e) {
            probeChain.notifyLeaveExceptional(child, frame, e);
            throw (e);
        }
    }

    public boolean isMarkedAs(NodePhylum phylum) {
        return probeChain.isMarkedAs(phylum);
    }

    public Set<NodePhylum> getPhylumMarks() {
        return probeChain.getPhylumMarks();
    }

    public void markAs(NodePhylum phylum) {
        probeChain.markAs(phylum);
    }

}
