/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.server;

import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.dap.types.Scope;
import com.oracle.truffle.tools.dap.types.StackFrame;
import com.oracle.truffle.tools.dap.types.Variable;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public final class StackFramesHandler {

    private final ExecutionContext context;
    private final DebuggerSession debuggerSession;

    public StackFramesHandler(ExecutionContext context, DebuggerSession debuggerSession) {
        this.context = context;
        this.debuggerSession = debuggerSession;
    }

    public List<StackFrame> getStackTrace(ThreadsHandler.SuspendedThreadInfo info) {
        List<StackFrame> sfs = new ArrayList<>();
        boolean top = true;
        for (DebugStackFrame frame : info.getSuspendedEvent().getStackFrames()) {
            SourceSection sourceSection = frame.getSourceSection();
            if (sourceSection == null || !sourceSection.isAvailable()) {
                continue;
            }
            if (!context.isInspectInternal() && frame.isInternal()) {
                continue;
            }
            Source source = sourceSection.getSource();
            if (!context.isInspectInternal() && source.isInternal()) {
                // should not be, double-check
                continue;
            }
            com.oracle.truffle.tools.dap.types.Source dapSource = context.getLoadedSourcesHandler().assureLoaded(source);
            SuspendAnchor anchor = SuspendAnchor.BEFORE;
            DebugValue returnValue = null;
            if (top) {
                anchor = info.getSuspendedEvent().getSuspendAnchor();
                if (info.getSuspendedEvent().hasSourceElement(SourceElement.ROOT)) {
                    // It is misleading to see return values on call exit,
                    // when we show it at function exit
                    returnValue = info.getSuspendedEvent().getReturnValue();
                }
            }
            if (anchor == SuspendAnchor.BEFORE) {
                sfs.add(StackFrame.create(info.getId(new FrameWrapper(frame, returnValue)), frame.getName(),
                                context.debuggerToClientLine(sourceSection.getStartLine()), context.debuggerToClientColumn(sourceSection.getStartColumn())).setSource(dapSource));
            } else {
                sfs.add(StackFrame.create(info.getId(new FrameWrapper(frame, returnValue)), frame.getName(),
                                context.debuggerToClientLine(sourceSection.getEndLine()), context.debuggerToClientColumn(sourceSection.getEndColumn() + 1)).setSource(dapSource));
            }
            top = false;
        }
        return sfs;
    }

    public List<Scope> getScopes(ThreadsHandler.SuspendedThreadInfo info, int frameId) {
        FrameWrapper frameWrapper = info.getById(FrameWrapper.class, frameId);
        DebugStackFrame frame = frameWrapper != null ? frameWrapper.getFrame() : null;
        if (frame != null) {
            List<Scope> scopes = new ArrayList<>();
            DebugScope dscope;
            try {
                dscope = frame.getScope();
            } catch (DebugException ex) {
                PrintWriter err = context.getErr();
                if (err != null) {
                    err.println("getScope() has caused " + ex);
                    ex.printStackTrace(err);
                }
                dscope = null;
            }
            String scopeName = "Block";
            boolean wasFunction = false;
            ScopeWrapper topScopeWrapper = null;
            DebugValue thisValue = null;
            while (dscope != null) {
                if (wasFunction) {
                    scopeName = "Closure";
                } else if (dscope.isFunctionScope()) {
                    scopeName = "Local";
                    thisValue = dscope.getReceiver();
                    wasFunction = true;
                }
                if (dscope.isFunctionScope() || dscope.getDeclaredValues().iterator().hasNext()) {
                    // provide only scopes that have some variables
                    if (scopes.isEmpty()) {
                        topScopeWrapper = new ScopeWrapper(frameWrapper, dscope);
                        scopes.add(Scope.create(scopeName, info.getId(topScopeWrapper), false));
                    } else {
                        scopes.add(Scope.create(scopeName, info.getId(new ScopeWrapper(frameWrapper, dscope)), false));
                    }
                }
                dscope = getParent(dscope);
            }
            if (thisValue != null && topScopeWrapper != null) {
                topScopeWrapper.thisValue = thisValue;
            }
            try {
                dscope = debuggerSession.getTopScope(frame.getSourceSection().getSource().getLanguage());
            } catch (DebugException ex) {
                PrintWriter err = context.getErr();
                if (err != null) {
                    err.println("getTopScope() has caused " + ex);
                    ex.printStackTrace(err);
                }
            }
            while (dscope != null) {
                if (dscope.isFunctionScope() || dscope.getDeclaredValues().iterator().hasNext()) {
                    // provide only scopes that have some variables
                    scopes.add(Scope.create("Global", info.getId(dscope), true));
                }
                dscope = getParent(dscope);
            }
            return scopes;
        }
        return null;
    }

    public static Variable evaluateOnStackFrame(ThreadsHandler.SuspendedThreadInfo info, int frameId, String expression) throws DebugException {
        FrameWrapper frameWrapper = info.getById(FrameWrapper.class, frameId);
        DebugStackFrame frame = frameWrapper != null ? frameWrapper.getFrame() : null;
        if (frame != null) {
            DebugValue value = VariablesHandler.getDebugValue(frame, expression);
            if (value != null) {
                return VariablesHandler.createVariable(info, value, "");
            }
        }
        return null;
    }

    private DebugScope getParent(DebugScope dscope) {
        DebugScope parentScope;
        try {
            parentScope = dscope.getParent();
        } catch (DebugException ex) {
            PrintWriter err = context.getErr();
            if (err != null) {
                err.println("Scope.getParent() has caused " + ex);
                ex.printStackTrace(err);
            }
            parentScope = null;
        }
        return parentScope;
    }

    static final class ScopeWrapper {

        private final FrameWrapper frame;
        private final DebugScope scope;
        private DebugValue thisValue;

        private ScopeWrapper(FrameWrapper frame, DebugScope scope) {
            this.frame = frame;
            this.scope = scope;
        }

        public DebugValue getThisValue() {
            return thisValue;
        }

        public DebugValue getReturnValue() {
            return frame.getReturnValue();
        }

        public DebugStackFrame getFrame() {
            return frame.getFrame();
        }

        public DebugScope getScope() {
            return scope;
        }
    }

    private static final class FrameWrapper {

        private final DebugStackFrame frame;
        private final DebugValue returnValue;

        private FrameWrapper(DebugStackFrame frame, DebugValue returnValue) {
            this.frame = frame;
            this.returnValue = returnValue;
        }

        public DebugValue getReturnValue() {
            return returnValue;
        }

        public DebugStackFrame getFrame() {
            return frame;
        }
    }
}
