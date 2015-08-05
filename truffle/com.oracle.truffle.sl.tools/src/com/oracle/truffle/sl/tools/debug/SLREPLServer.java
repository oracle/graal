/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.tools.debug;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.ExecutionEvent;
import com.oracle.truffle.api.debug.SuspendedEvent;

import java.util.*;

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.vm.*;
import com.oracle.truffle.api.vm.TruffleVM.Language;
import com.oracle.truffle.sl.*;
import com.oracle.truffle.sl.nodes.instrument.SLDefaultVisualizer;
import com.oracle.truffle.tools.debug.shell.*;
import com.oracle.truffle.tools.debug.shell.client.*;
import com.oracle.truffle.tools.debug.shell.server.*;

/**
 * Instantiation of the "server" side of the "REPL*" debugger for the Simple language.
 * <p>
 * The SL parser is not equipped to parse program fragments, so any debugging functions that depend
 * on this are not supported, for example "eval" and breakpoint conditions.
 *
 * @see SimpleREPLClient
 */
public final class SLREPLServer extends REPLServer {

    // TODO (mlvdv) remove when there's a better way to express this dependency
    @SuppressWarnings("unused") private static final Class<SLLanguage> DYNAMIC_DEPENDENCY = com.oracle.truffle.sl.SLLanguage.class;

    public static void main(String[] args) {
        // Cheating for the prototype: start from SL, rather than from the client.
        final SLREPLServer server = new SLREPLServer();
        final SimpleREPLClient client = new SimpleREPLClient(server.language.getShortName(), server);

        // Cheating for the prototype: allow server access to client for recursive debugging
        server.setClient(client);

        try {
            client.start();
        } catch (QuitException ex) {
        }
    }

    private final Language language;
    private final TruffleVM vm;
    private Debugger db;
    private final String statusPrefix;
    private final Map<String, REPLHandler> handlerMap = new HashMap<>();
    private SLServerContext currentServerContext;
    private SimpleREPLClient replClient = null;

    private void add(REPLHandler fileHandler) {
        handlerMap.put(fileHandler.getOp(), fileHandler);
    }

    public SLREPLServer() {
        add(REPLHandler.BACKTRACE_HANDLER);
        add(REPLHandler.BREAK_AT_LINE_HANDLER);
        add(REPLHandler.BREAK_AT_LINE_ONCE_HANDLER);
        add(REPLHandler.BREAK_AT_THROW_HANDLER);
        add(REPLHandler.BREAK_AT_THROW_ONCE_HANDLER);
        add(REPLHandler.BREAKPOINT_INFO_HANDLER);
        add(REPLHandler.CLEAR_BREAK_HANDLER);
        add(REPLHandler.CONTINUE_HANDLER);
        add(REPLHandler.DELETE_HANDLER);
        add(REPLHandler.DISABLE_BREAK_HANDLER);
        add(REPLHandler.ENABLE_BREAK_HANDLER);
        add(REPLHandler.FILE_HANDLER);
        add(REPLHandler.FRAME_HANDLER);
        add(SLREPLHandler.INFO_HANDLER);
        add(REPLHandler.KILL_HANDLER);
        add(SLREPLHandler.LOAD_RUN_SOURCE_HANDLER);
        add(SLREPLHandler.LOAD_STEP_SOURCE_HANDLER);
        add(REPLHandler.QUIT_HANDLER);
        add(REPLHandler.STEP_INTO_HANDLER);
        add(REPLHandler.STEP_OUT_HANDLER);
        add(REPLHandler.STEP_OVER_HANDLER);
        add(REPLHandler.TRUFFLE_HANDLER);
        add(REPLHandler.TRUFFLE_NODE_HANDLER);

        EventConsumer<SuspendedEvent> onHalted = new EventConsumer<SuspendedEvent>(SuspendedEvent.class) {
            @Override
            protected void on(SuspendedEvent ev) {
                SLREPLServer.this.haltedAt(ev);
            }
        };
        EventConsumer<ExecutionEvent> onExec = new EventConsumer<ExecutionEvent>(ExecutionEvent.class) {
            @Override
            protected void on(ExecutionEvent event) {
                event.prepareStepInto();
                db = event.getDebugger();
            }
        };

        TruffleVM newVM = TruffleVM.newVM().onEvent(onHalted).onEvent(onExec).build();
        this.language = newVM.getLanguages().get("application/x-sl");
        assert language != null;

        this.vm = newVM;
        this.statusPrefix = language.getShortName() + " REPL:";
    }

    private void setClient(SimpleREPLClient replClient) {
        this.replClient = replClient;
    }

    @Override
    public REPLMessage start() {

        this.currentServerContext = new SLServerContext(null, null);

        // SL doesn't load modules (like other languages), so we just return a success
        final REPLMessage reply = new REPLMessage();
        reply.put(REPLMessage.STATUS, REPLMessage.SUCCEEDED);
        reply.put(REPLMessage.DISPLAY_MSG, language.getShortName() + " started");
        return reply;
    }

    @Override
    public REPLMessage[] receive(REPLMessage request) {
        if (currentServerContext == null) {
            final REPLMessage message = new REPLMessage();
            message.put(REPLMessage.STATUS, REPLMessage.FAILED);
            message.put(REPLMessage.DISPLAY_MSG, "server not started");
            final REPLMessage[] reply = new REPLMessage[]{message};
            return reply;
        }
        return currentServerContext.receive(request);
    }

    /**
     * Execution context of a halted SL program.
     */
    public final class SLServerContext extends REPLServerContext {

        private final SLServerContext predecessor;

        public SLServerContext(SLServerContext predecessor, SuspendedEvent event) {
            super(predecessor == null ? 0 : predecessor.getLevel() + 1, event);
            this.predecessor = predecessor;
        }

        @Override
        public REPLMessage[] receive(REPLMessage request) {
            final String command = request.get(REPLMessage.OP);
            final REPLHandler handler = handlerMap.get(command);

            if (handler == null) {
                final REPLMessage message = new REPLMessage();
                message.put(REPLMessage.OP, command);
                message.put(REPLMessage.STATUS, REPLMessage.FAILED);
                message.put(REPLMessage.DISPLAY_MSG, statusPrefix + " op \"" + command + "\" not supported");
                final REPLMessage[] reply = new REPLMessage[]{message};
                return reply;
            }
            return handler.receive(request, currentServerContext);
        }

        @Override
        public Language getLanguage() {
            return language;
        }

        @Override
        public Visualizer getVisualizer() {
            return new SLDefaultVisualizer();
        }

        @Override
        public TruffleVM vm() {
            return vm;
        }

        @Override
        protected Debugger db() {
            return db;
        }

        @Override
        public void registerBreakpoint(Breakpoint breakpoint) {
            SLREPLServer.this.registerBreakpoint(breakpoint);
        }

        @Override
        public Breakpoint findBreakpoint(int id) {
            return SLREPLServer.this.findBreakpoint(id);
        }

        @Override
        public int getBreakpointID(Breakpoint breakpoint) {
            return SLREPLServer.this.getBreakpointID(breakpoint);
        }

    }

    void haltedAt(SuspendedEvent event) {
        // Create and push a new debug context where execution is halted
        currentServerContext = new SLServerContext(currentServerContext, event);

        // Message the client that execution is halted and is in a new debugging context
        final REPLMessage message = new REPLMessage();
        message.put(REPLMessage.OP, REPLMessage.STOPPED);
        final SourceSection src = event.getNode().getSourceSection();
        final Source source = src.getSource();
        message.put(REPLMessage.SOURCE_NAME, source.getName());
        message.put(REPLMessage.FILE_PATH, source.getPath());
        message.put(REPLMessage.LINE_NUMBER, Integer.toString(src.getStartLine()));
        message.put(REPLMessage.STATUS, REPLMessage.SUCCEEDED);
        message.put(REPLMessage.DEBUG_LEVEL, Integer.toString(currentServerContext.getLevel()));
        List<String> warnings = event.getRecentWarnings();
        if (!warnings.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            for (String warning : warnings) {
                sb.append(warning + "\n");
            }
            message.put(REPLMessage.WARNINGS, sb.toString());
        }
        try {
            // Cheat with synchrony: call client directly about entering a nested debugging
            // context.
            replClient.halted(message);
        } finally {
            // Returns when "continue" is called in the new debugging context

            // Pop the debug context, and return so that the old context will continue
            currentServerContext = currentServerContext.predecessor;
        }
    }
}
