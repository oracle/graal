/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;

final class AgentExecutionNode extends ExecutionEventNode {
    @Node.Child private InteropLibrary enterDispatch;
    @Node.Child private InteropLibrary exitDispatch;
    private final TruffleInstrument.Env env;
    private final Object enter;
    private final Object exit;
    private final EventContextObject ctx;

    AgentExecutionNode(TruffleInstrument.Env env, Object enter, Object exit, EventContextObject ctx) {
        this.env = env;
        this.enter = enter;
        if (enter != null) {
            this.enterDispatch = InteropLibrary.getFactory().createDispatched(3);
        }
        this.exit = exit;
        if (exit != null) {
            this.exitDispatch = InteropLibrary.getFactory().createDispatched(3);
        }
        this.ctx = ctx;
    }

    @Override
    protected void onEnter(VirtualFrame frame) {
        if (enter != null) {
            try {
                enterDispatch.execute(enter, ctx, new VariablesObject(env, this, frame));
            } catch (InteropException ex) {
                throw ctx.wrap(enter, 2, ex);
            } catch (RuntimeException ex) {
                throw ctx.rethrow(ex);
            }
        }
    }

    @Override
    protected void onReturnValue(VirtualFrame frame, Object result) {
        if (exit != null) {
            try {
                exitDispatch.execute(exit, ctx, new VariablesObject(env, this, frame));
            } catch (InteropException ex) {
                throw ctx.wrap(exit, 2, ex);
            } catch (RuntimeException ex) {
                throw ctx.rethrow(ex);
            }
        }
    }

    @Override
    protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
        if (exit != null) {
            try {
                exitDispatch.execute(exit, ctx, new VariablesObject(env, this, frame));
            } catch (InteropException ex) {
                throw ctx.wrap(exit, 2, ex);
            } catch (RuntimeException ex) {
                throw ctx.rethrow(ex);
            }
        }
    }

    static ExecutionEventNodeFactory factory(TruffleInstrument.Env env, final Object enter, final Object exit) {
        return new ExecutionEventNodeFactory() {
            @Override
            public ExecutionEventNode create(EventContext context) {
                final EventContextObject ctx = new EventContextObject(context);
                return new AgentExecutionNode(env, enter, exit, ctx);
            }
        };
    }
}
