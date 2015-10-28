/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.debug.shell.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.ExecutionEvent;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrument.StandardSyntaxTag;
import com.oracle.truffle.api.instrument.Visualizer;
import com.oracle.truffle.api.instrument.impl.DefaultVisualizer;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.LineLocation;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.EventConsumer;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Language;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;
import com.oracle.truffle.tools.debug.shell.REPLMessage;
import com.oracle.truffle.tools.debug.shell.client.SimpleREPLClient;

/**
 * The server side of a simple message-based protocol for a possibly remote language
 * Read-Eval-Print-Loop.
 */
public final class REPLServer {

    // Language-agnostic
    private final PolyglotEngine engine;
    private Debugger db;
    private Context currentServerContext;
    private SimpleREPLClient replClient = null;
    private String statusPrefix;
    private final Map<String, REPLHandler> handlerMap = new HashMap<>();

    // TODO (mlvdv) Language-specific
    private final PolyglotEngine.Language language;
    private final Visualizer visualizer;

    // Breakpoints registered with the debugger
    private int breakpointCounter;
    private Map<Breakpoint, Integer> breakpoints = new WeakHashMap<>();

    /**
     * Create a single-language server.
     */
    public REPLServer(String mimeType, Visualizer visualizer) {
        this.visualizer = visualizer == null ? new DefaultVisualizer() : visualizer;
        EventConsumer<SuspendedEvent> onHalted = new EventConsumer<SuspendedEvent>(SuspendedEvent.class) {
            @Override
            protected void on(SuspendedEvent ev) {
                REPLServer.this.haltedAt(ev);
            }
        };
        EventConsumer<ExecutionEvent> onExec = new EventConsumer<ExecutionEvent>(ExecutionEvent.class) {
            @Override
            protected void on(ExecutionEvent event) {
                db = event.getDebugger();
                event.prepareStepInto();
            }
        };
        engine = PolyglotEngine.buildNew().onEvent(onHalted).onEvent(onExec).build();
        this.language = engine.getLanguages().get(mimeType);
        if (language == null) {
            throw new RuntimeException("Implementation not found for \"" + mimeType + "\"");
        }
        statusPrefix = languageName(language);
    }

    public void add(REPLHandler handler) {
        handlerMap.put(handler.getOp(), handler);
    }

    /**
     * Starts up a server; status returned in a message.
     */
    public void start() {

        addHandlers();
        this.replClient = new SimpleREPLClient(this);
        this.currentServerContext = new Context(null, null);
        replClient.start();
    }

    protected void addHandlers() {
        add(REPLHandler.BACKTRACE_HANDLER);
        add(REPLHandler.BREAK_AT_LINE_HANDLER);
        add(REPLHandler.BREAK_AT_LINE_ONCE_HANDLER);
        add(REPLHandler.BREAK_AT_THROW_HANDLER);
        add(REPLHandler.BREAK_AT_THROW_ONCE_HANDLER);
        add(REPLHandler.BREAKPOINT_INFO_HANDLER);
        add(REPLHandler.CALL_HANDLER);
        add(REPLHandler.CLEAR_BREAK_HANDLER);
        add(REPLHandler.CONTINUE_HANDLER);
        add(REPLHandler.DELETE_HANDLER);
        add(REPLHandler.DISABLE_BREAK_HANDLER);
        add(REPLHandler.ENABLE_BREAK_HANDLER);
        add(REPLHandler.EVAL_HANDLER);
        add(REPLHandler.FILE_HANDLER);
        add(REPLHandler.FRAME_HANDLER);
        add(REPLHandler.INFO_HANDLER);
        add(REPLHandler.KILL_HANDLER);
        add(REPLHandler.LOAD_HANDLER);
        add(REPLHandler.QUIT_HANDLER);
        add(REPLHandler.SET_BREAK_CONDITION_HANDLER);
        add(REPLHandler.STEP_INTO_HANDLER);
        add(REPLHandler.STEP_OUT_HANDLER);
        add(REPLHandler.STEP_OVER_HANDLER);
        add(REPLHandler.TRUFFLE_HANDLER);
        add(REPLHandler.TRUFFLE_NODE_HANDLER);
        add(REPLHandler.UNSET_BREAK_CONDITION_HANDLER);
    }

    void haltedAt(SuspendedEvent event) {
        // Create and push a new debug context where execution is halted
        currentServerContext = new Context(currentServerContext, event);

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
            // Returns when "continue" or "kill" is called in the new debugging context

            // Pop the debug context, and return so that the old context will continue
            currentServerContext = currentServerContext.predecessor;
        }
    }

    /**
     * Execution context of a halted program, possibly nested.
     */
    public final class Context {

        private final Context predecessor;
        private final int level;
        private final SuspendedEvent event;

        Context(Context predecessor, SuspendedEvent event) {
            this.level = predecessor == null ? 0 : predecessor.getLevel() + 1;
            this.predecessor = predecessor;
            this.event = event;
        }

        /**
         * The nesting depth of this context in the current session.
         */
        int getLevel() {
            return level;
        }

        /**
         * The AST node where execution is halted in this context.
         */
        Node getNodeAtHalt() {
            return event.getNode();
        }

        /**
         * Evaluates given code snippet in the context of currently suspended execution.
         *
         * @param code the snippet to evaluate
         * @param frame <code>null</code> in case the evaluation should happen in top most frame,
         *            non-null value
         * @return result of the evaluation
         * @throws IOException if something goes wrong
         */
        Object eval(String code, FrameInstance frame) throws IOException {
            if (event == null) {
                throw new IOException("top level \"eval\" not yet supported");
            }
            return event.eval(code, frame);
        }

        /**
         * The frame where execution is halted in this context.
         */
        MaterializedFrame getFrameAtHalt() {
            return event.getFrame();
        }

        /**
         * Dispatches a REPL request to the appropriate handler.
         */
        REPLMessage[] receive(REPLMessage request) {
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
            return handler.receive(request, REPLServer.this);
        }

        /**
         * Provides access to the execution stack.
         *
         * @return immutable list of stack elements
         */
        List<FrameDebugDescription> getStack() {
            List<FrameDebugDescription> frames = new ArrayList<>();
            int frameCount = 1;
            for (FrameInstance frameInstance : event.getStack()) {
                if (frameCount == 1) {
                    frames.add(new FrameDebugDescription(frameCount, event.getNode(), frameInstance));
                } else {
                    frames.add(new FrameDebugDescription(frameCount, frameInstance.getCallNode(), frameInstance));
                }
                frameCount++;
            }
            return Collections.unmodifiableList(frames);
        }

        void prepareStepOut() {
            event.prepareStepOut();
        }

        void prepareStepInto(int repeat) {
            event.prepareStepInto(repeat);
        }

        void prepareStepOver(int repeat) {
            event.prepareStepOver(repeat);
        }

        void prepareContinue() {
            event.prepareContinue();
        }
    }

    /**
     * Ask the server to handle a request. Return a non-empty array of messages to simulate remote
     * operation where the protocol has possibly multiple messages being returned asynchronously in
     * response to each request.
     */
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

    Context getCurrentContext() {
        return currentServerContext;
    }

    Visualizer getVisualizer() {
        return visualizer;
    }

    // TODO (mlvdv) language-specific
    Language getLanguage() {
        return language;
    }

    // TODO (mlvdv) language-specific
    public String getLanguageName() {
        return languageName(this.language);
    }

    private static String languageName(Language lang) {
        return lang.getName() + "(" + lang.getVersion() + ")";
    }

    void eval(Source source) throws IOException {
        engine.eval(source);
    }

    Breakpoint setLineBreakpoint(int ignoreCount, LineLocation lineLocation, boolean oneShot) throws IOException {
        final Breakpoint breakpoint = db.setLineBreakpoint(ignoreCount, lineLocation, oneShot);
        registerBreakpoint(breakpoint);
        return breakpoint;
    }

    Breakpoint setTagBreakpoint(int ignoreCount, StandardSyntaxTag tag, boolean oneShot) throws IOException {
        final Breakpoint breakpoint = db.setTagBreakpoint(ignoreCount, tag, oneShot);
        registerBreakpoint(breakpoint);
        return breakpoint;
    }

    private synchronized void registerBreakpoint(Breakpoint breakpoint) {
        breakpoints.put(breakpoint, breakpointCounter++);
    }

    synchronized Breakpoint findBreakpoint(int id) {
        for (Map.Entry<Breakpoint, Integer> entrySet : breakpoints.entrySet()) {
            if (id == entrySet.getValue()) {
                return entrySet.getKey();
            }
        }
        return null;
    }

    Collection<Breakpoint> getBreakpoints() {
        return db.getBreakpoints();
    }

    synchronized int getBreakpointID(Breakpoint breakpoint) {
        final Integer id = breakpoints.get(breakpoint);
        return id == null ? -1 : id;
    }

    void call(String name) throws IOException {
        Value symbol = engine.findGlobalSymbol(name);
        if (symbol == null) {
            throw new IOException("symboleval f \"" + name + "\" not found");
        }
        symbol.invoke(null);
    }

}
