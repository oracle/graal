/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.impl;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

final class InsightHookNode extends ExecutionEventNode {
    @Node.Child private InteropLibrary checkApplicable;
    @Node.Child private InteropLibrary enterDispatch;
    @Node.Child private InteropLibrary exitDispatch;
    @Node.Child private NodeLibrary nodeDispatch;
    @Node.Child private InteropLibrary exceptionDispatch;
    private final InsightInstrument.Key key;
    private final EventContext ctx;
    private final Node instrumentedNode;
    private final InsightInstrument insight;
    private final String rootName;
    private final Source src;

    private InsightHookNode(InsightInstrument.Key key, InsightInstrument insight, EventContext ctx) {
        this.key = key;
        this.insight = insight;
        this.checkApplicable = InteropLibrary.getFactory().createDispatched(3);
        this.enterDispatch = InteropLibrary.getFactory().createDispatched(3);
        this.exitDispatch = InteropLibrary.getFactory().createDispatched(3);
        this.exceptionDispatch = InteropLibrary.getFactory().createDispatched(3);
        Node node = ctx.getInstrumentedNode();
        this.nodeDispatch = NodeLibrary.getFactory().create(node);
        this.ctx = ctx;
        this.instrumentedNode = node;
        this.rootName = node.getRootNode().getName();
        this.src = ctx.getInstrumentedSourceSection().getSource();
    }

    @Override
    protected void onEnter(VirtualFrame frame) {
        loopHooks(frame, AgentType.ENTER, null);
    }

    @Override
    protected void onReturnValue(VirtualFrame frame, Object returnValue) {
        loopHooks(frame, AgentType.RETURN, returnValue);
    }

    @Override
    protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
        loopHooks(frame, AgentType.RETURN, null);
    }

    @Override
    protected Object onUnwind(VirtualFrame frame, Object info) {
        return info;
    }

    private void loopHooks(VirtualFrame frame, final AgentType type, Object returnValue) throws RuntimeException, ThreadDeath {
        final int len = key.functionsMaxCount();
        CompilerAsserts.partialEvaluationConstant(len);
        final InsightPerContext ipc = insight.findCtx();
        ReturnNow returnNow = null;
        for (int i = 0; i < len; i++) {
            InsightFilter.Data data = (InsightFilter.Data) ipc.functionFor(key, i);
            if (!isApplicable(data, type)) {
                continue;
            }
            final EventContextObject eco = eventCtxObj();
            try {
                exitDispatch.execute(data.fn, eco, getVariables(frame, type == AgentType.ENTER, returnValue));
            } catch (ReturnNow ex) {
                if (returnNow == null) {
                    returnNow = ex;
                }
            } catch (InteropException ex) {
                throw EventContextObject.wrap(data.fn, 2, ex);
            } catch (RuntimeException ex) {
                throw EventContextObject.rethrow(ex, exceptionDispatch);
            }
        }
        if (returnNow != null) {
            throw ctx.createUnwind(NullObject.nullCheck(returnNow.returnValue));
        }
    }

    private static final class ReturnNow extends ControlFlowException {
        static final long serialVersionUID = 49092343L;
        final Object returnValue;

        ReturnNow(Object returnValue) {
            this.returnValue = returnValue;
        }
    }

    static ControlFlowException returnNow(Object[] args) throws ArityException {
        Object returnValue;
        switch (args.length) {
            case 0:
                returnValue = null;
                break;
            case 1:
                returnValue = args[0];
                break;
            default:
                throw ArityException.create(1, 1, args.length);
        }
        return new ReturnNow(returnValue);
    }

    private Object getVariables(VirtualFrame frame, boolean nodeEnter, Object returnValue) {
        if (nodeDispatch.hasScope(instrumentedNode, frame)) {
            try {
                Object scope = nodeDispatch.getScope(instrumentedNode, frame, nodeEnter);
                if (returnValue != null) {
                    return new VariablesObject(scope, returnValue);
                } else {
                    return scope;
                }
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        } else {
            return ArrayObject.array();
        }
    }

    private boolean isApplicable(InsightFilter.Data data, AgentType agentType) {
        if (data == null) {
            return false;
        }
        if (data.type != agentType) {
            return false;
        }
        if (data.rootNameFn != null && !RootNameFilter.rootNameCheck(checkApplicable, data, rootName)) {
            return false;
        }
        if (data.sourceFilterFn != null && !InsightSourceFilter.checkSource(checkApplicable, data, src)) {
            return false;
        }
        return true;
    }

    private EventContextObject eventCtxObj() {
        return new EventContextObject(ctx);
    }

    @Override
    public String toString() {
        return super.toString() + " at " + ctx.getInstrumentedNode().getSourceSection();
    }

    static ExecutionEventNodeFactory factory(InsightInstrument insight, InsightInstrument.Key key) {
        return new ExecutionEventNodeFactory() {
            @Override
            public ExecutionEventNode create(EventContext context) {
                return new InsightHookNode(key, insight, context);
            }
        };
    }
}
