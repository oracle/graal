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
import com.oracle.truffle.api.nodes.Node;

final class AgentExecutionNode extends ExecutionEventNode {
    @Node.Child private InteropLibrary enterDispatch;
    @Node.Child private InteropLibrary exitDispatch;
    @Node.Child private NodeLibrary nodeDispatch;
    @Node.Child private InteropLibrary exceptionDispatch;
    private final Object enter;
    private final Object exit;
    private final EventContextObject ctx;
    private final Node instrumentedNode;

    AgentExecutionNode(Object enter, Object exit, EventContextObject ctx) {
        this.enter = enter;
        if (enter != null) {
            this.enterDispatch = InteropLibrary.getFactory().create(enter);
        }
        this.exit = exit;
        if (exit != null) {
            this.exitDispatch = InteropLibrary.getFactory().create(exit);
        }
        Node node = ctx.getInstrumentedNode();
        this.nodeDispatch = NodeLibrary.getFactory().create(node);
        if (enter != null || exit != null) {
            this.exceptionDispatch = InteropLibrary.getFactory().createDispatched(3);
        }
        this.ctx = ctx;
        this.instrumentedNode = node;
    }

    @Override
    protected void onEnter(VirtualFrame frame) {
        if (enter != null) {
            try {
                enterDispatch.execute(enter, ctx, getVariables(frame, true, null));
            } catch (InteropException ex) {
                throw ctx.wrap(enter, 2, ex);
            } catch (RuntimeException ex) {
                throw ctx.rethrow(ex, exceptionDispatch);
            }
        }
    }

    @Override
    protected void onReturnValue(VirtualFrame frame, Object returnValue) {
        if (exit != null) {
            try {
                exitDispatch.execute(exit, ctx, getVariables(frame, false, returnValue));
            } catch (InteropException ex) {
                throw ctx.wrap(exit, 2, ex);
            } catch (RuntimeException ex) {
                throw ctx.rethrow(ex, exceptionDispatch);
            }
        }
    }

    @Override
    protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
        if (exit != null) {
            try {
                exitDispatch.execute(exit, ctx, getVariables(frame, false, null));
            } catch (InteropException ex) {
                throw ctx.wrap(exit, 2, ex);
            } catch (RuntimeException ex) {
                throw ctx.rethrow(ex, exceptionDispatch);
            }
        }
    }

    @Override
    protected Object onUnwind(VirtualFrame frame, Object info) {
        return info;
    }

    static ThreadDeath returnNow(EventContext context, Object[] args) throws ArityException, ThreadDeath {
        Object returnValue;
        switch (args.length) {
            case 0:
                returnValue = null;
                break;
            case 1:
                returnValue = args[0];
                break;
            default:
                throw ArityException.create(1, args.length);
        }
        return context.createUnwind(NullObject.nullCheck(returnValue));
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

    static ExecutionEventNodeFactory factory(final Object enter, final Object exit) {
        return new ExecutionEventNodeFactory() {
            @Override
            public ExecutionEventNode create(EventContext context) {
                final EventContextObject ctx = new EventContextObject(context);
                return new AgentExecutionNode(enter, exit, ctx);
            }
        };
    }
}
