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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Breakpoint.State;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.ExecutionEvent;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrument.StandardSyntaxTag;
import com.oracle.truffle.api.instrument.SyntaxTag;
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

    enum BreakpointKind {
        LINE,
        TAG
    }

    private static int nextBreakpointUID = 0;

    // Language-agnostic
    private final PolyglotEngine engine;
    private Debugger db;
    private Context currentServerContext;
    private SimpleREPLClient replClient = null;
    private String statusPrefix;
    private final Map<String, REPLHandler> handlerMap = new HashMap<>();

    /** Languages sorted by name. */
    private final TreeSet<Language> engineLanguages = new TreeSet<>(new Comparator<Language>() {

        public int compare(Language lang1, Language lang2) {
            return lang1.getName().compareTo(lang2.getName());
        }
    });

    /** MAP: language name => Language. */
    private final Map<String, Language> nameToLanguage = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    // TODO (mlvdv) Language-specific
    private PolyglotEngine.Language defaultLanguage;
    private final Visualizer visualizer;

    private Map<Integer, BreakpointInfo> breakpoints = new WeakHashMap<>();

    public REPLServer(String defaultMIMEType, Visualizer visualizer) {
        this.visualizer = visualizer == null ? new DefaultVisualizer() : visualizer;
        this.engine = PolyglotEngine.newBuilder().onEvent(onHalted).onEvent(onExec).build();
        engineLanguages.addAll(engine.getLanguages().values());
        if (engineLanguages.size() == 0) {
            throw new RuntimeException("No language implementations installed");
        }
        for (Language language : engineLanguages) {
            nameToLanguage.put(language.getName(), language);
        }

        if (defaultMIMEType == null) {
            defaultLanguage = engineLanguages.iterator().next();
        } else {
            this.defaultLanguage = engine.getLanguages().get(defaultMIMEType);
            if (defaultLanguage == null) {
                throw new RuntimeException("Implementation not found for \"" + defaultMIMEType + "\"");
            }
        }
        statusPrefix = languageName(defaultLanguage);
    }

    private final EventConsumer<SuspendedEvent> onHalted = new EventConsumer<SuspendedEvent>(SuspendedEvent.class) {
        @Override
        protected void on(SuspendedEvent ev) {
            REPLServer.this.haltedAt(ev);
        }
    };

    private final EventConsumer<ExecutionEvent> onExec = new EventConsumer<ExecutionEvent>(ExecutionEvent.class) {
        @Override
        protected void on(ExecutionEvent event) {
            if (db == null) {
                db = event.getDebugger();
                for (BreakpointInfo breakpointInfo : breakpoints.values()) {
                    breakpointInfo.activate(db);
                }
            }
            if (currentServerContext.steppingInto) {
                event.prepareStepInto();
            }
        }
    };

    public void add(REPLHandler handler) {
        handlerMap.put(handler.getOp(), handler);
    }

    /**
     * Starts up a server; status returned in a message.
     */
    public void start() {

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
        add(REPLHandler.SET_LANGUAGE_HANDLER);
        add(REPLHandler.STEP_INTO_HANDLER);
        add(REPLHandler.STEP_OUT_HANDLER);
        add(REPLHandler.STEP_OVER_HANDLER);
        add(REPLHandler.TRUFFLE_HANDLER);
        add(REPLHandler.TRUFFLE_NODE_HANDLER);
        add(REPLHandler.UNSET_BREAK_CONDITION_HANDLER);
        this.replClient = new SimpleREPLClient(this);
        this.currentServerContext = new Context(null, null, defaultLanguage);
        replClient.start();
    }

    @SuppressWarnings("static-method")
    public String getWelcome() {
        return "GraalVM MultiLanguage Debugger 0.9\n" + "Copyright (c) 2013-5, Oracle and/or its affiliates";
    }

    void haltedAt(SuspendedEvent event) {
        // Message the client that execution is halted and is in a new debugging context
        final REPLMessage message = new REPLMessage();
        message.put(REPLMessage.OP, REPLMessage.STOPPED);

        // Identify language execution where halted; default to previous context
        Language haltedLanguage = currentServerContext.currentLanguage;
        final String mimeType = findMime(event.getNode());
        if (mimeType == null) {
            message.put(REPLMessage.WARNINGS, "unable to detect language at halt");
        } else {
            final Language language = engine.getLanguages().get(mimeType);
            if (language == null) {
                message.put(REPLMessage.WARNINGS, "no language installed for MIME type \"" + mimeType + "\"");
            } else {
                haltedLanguage = language;
            }
        }

        // Create and push a new debug context where execution is halted
        currentServerContext = new Context(currentServerContext, event, haltedLanguage);

        message.put(REPLMessage.LANG_NAME, haltedLanguage.getName());
        final SourceSection src = event.getNode().getSourceSection();
        final Source source = src.getSource();
        message.put(REPLMessage.SOURCE_NAME, source.getName());
        final String path = source.getPath();
        if (path == null) {
            message.put(REPLMessage.SOURCE_TEXT, source.getCode());
        } else {
            message.put(REPLMessage.FILE_PATH, path);
        }
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

    @SuppressWarnings("static-method")
    private String findMime(Node node) {
        String result = null;
        final SourceSection section = node.getEncapsulatingSourceSection();
        if (section != null) {
            final Source source = section.getSource();
            if (source != null) {
                result = source.getMimeType();
            }
        }
        return result;
    }

    /**
     * Execution context of a halted program, possibly nested.
     */
    public final class Context {

        private final Context predecessor;
        private final int level;
        private final SuspendedEvent event;
        private Language currentLanguage;
        private boolean steppingInto = false;  // Only true during a "stepInto" engine call

        Context(Context predecessor, SuspendedEvent event, Language language) {
            assert language != null;
            this.level = predecessor == null ? 0 : predecessor.getLevel() + 1;
            this.predecessor = predecessor;
            this.event = event;
            this.currentLanguage = language;
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

        Object call(String name, boolean stepInto, List<String> argList) throws IOException {
            Value symbol = engine.findGlobalSymbol(name);
            if (symbol == null) {
                throw new IOException("symbol \"" + name + "\" not found");
            }
            final List<Object> args = new ArrayList<>();
            for (String stringArg : argList) {
                Integer intArg = null;
                try {
                    intArg = Integer.valueOf(stringArg);
                    args.add(intArg);
                } catch (NumberFormatException e) {
                    args.add(stringArg);
                }
            }
            this.steppingInto = stepInto;
            try {
                return symbol.execute(args.toArray(new Object[0])).get();
            } finally {
                this.steppingInto = false;
            }
        }

        void eval(Source source, boolean stepInto) throws IOException {
            this.steppingInto = stepInto;
            try {
                engine.eval(source);
            } finally {
                this.steppingInto = false;
            }
        }

        /**
         * Evaluates a code snippet in the context of a selected frame in the currently suspended
         * execution, if any; otherwise a top level (new) evaluation.
         *
         * @param code the snippet to evaluate
         * @param frameNumber index of the stack frame in which to evaluate, 0 = current frame where
         *            halted, null = top level eval
         * @return result of the evaluation
         * @throws IOException if something goes wrong
         */
        Object eval(String code, Integer frameNumber, boolean stepInto) throws IOException {
            if (event == null) {
                if (frameNumber != null) {
                    throw new IllegalStateException("Frame number requires a halted execution");
                }
                this.steppingInto = stepInto;
                final String mimeType = defaultMIME(currentLanguage);
                try {
                    return engine.eval(Source.fromText(code, "eval(\"" + code + "\")").withMimeType(mimeType)).get();
                } finally {
                    this.steppingInto = false;
                }
            } else {
                if (frameNumber == null) {
                    throw new IllegalStateException("Eval in halted context requires a frame number");
                }
                if (stepInto) {
                    event.prepareStepInto(1);
                }
                try {
                    FrameInstance frame = frameNumber == 0 ? null : event.getStack().get(frameNumber - 1);
                    final Object result = event.eval(code, frame);
                    return (result instanceof Value) ? ((Value) result).get() : result;
                } finally {
                    event.prepareContinue();
                }
            }
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
         * @return Node where halted
         */
        Node getNode() {
            return event.getNode();
        }

        /**
         * @return Frame where halted
         */
        MaterializedFrame getFrame() {
            return event.getFrame();
        }

        /**
         * Provides access to the execution stack, not counting the node/frame where halted.
         *
         * @return immutable list of stack elements
         */
        List<FrameInstance> getStack() {
            return event.getStack();
        }

        public String getLanguageName() {
            return currentLanguage.getName();
        }

        /**
         * Case-insensitive; returns actual language name set.
         *
         * @throws IOException if fails
         */
        String setLanguage(String name) throws IOException {
            assert name != null;
            final Language language = nameToLanguage.get(name);
            if (language == null) {
                throw new IOException("Language \" + name + \" not supported");
            }
            if (language == currentLanguage) {
                return currentLanguage.getName();
            }
            if (event != null) {
                throw new IOException("Only supported at top level");
            }
            this.currentLanguage = language;
            return language.getName();
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
        return defaultLanguage;
    }

    TreeSet<Language> getLanguages() {
        return engineLanguages;
    }

    // TODO (mlvdv) language-specific
    public String getLanguageName() {
        return languageName(this.defaultLanguage);
    }

    private static String languageName(Language lang) {
        return lang.getName() + "(" + lang.getVersion() + ")";
    }

    @SuppressWarnings("static-method")
    private String defaultMIME(Language language) {
        return language.getMimeTypes().iterator().next();
    }

    BreakpointInfo setLineBreakpoint(int ignoreCount, LineLocation lineLocation, boolean oneShot) {
        return new BreakpointInfo(db, lineLocation, ignoreCount, oneShot);
    }

    BreakpointInfo setTagBreakpoint(int ignoreCount, StandardSyntaxTag tag, boolean oneShot) {
        return new BreakpointInfo(db, tag, ignoreCount, oneShot);
    }

    synchronized BreakpointInfo findBreakpoint(int id) {
        return breakpoints.get(id);
    }

    /**
     * Gets a list of the currently existing breakpoints.
     */
    Collection<BreakpointInfo> getBreakpoints() {
        return new ArrayList<>(breakpoints.values());
    }

    final class BreakpointInfo {

        private final BreakpointKind kind;

        /** Null before created in debugger or after disposal. */
        private Breakpoint breakpoint;

        /** Non-null only when breakpoint == null. */
        private State state; // non-null iff haven't "activated" yet

        private final int uid;

        private boolean oneShot;

        private int ignoreCount;

        private Source conditionSource;

        private final LineLocation lineLocation;

        private final SyntaxTag tag;

        private BreakpointInfo(Debugger debugger, LineLocation lineLocation, int ignoreCount, boolean oneShot) {
            this.kind = BreakpointKind.LINE;
            this.lineLocation = lineLocation;
            this.tag = null;
            this.ignoreCount = ignoreCount;
            this.oneShot = oneShot;
            this.uid = nextBreakpointUID++;
            if (debugger == null) {
                this.state = State.ENABLED_UNRESOLVED;
            } else {
                activate(debugger);
            }
            breakpoints.put(uid, this);
        }

        private BreakpointInfo(Debugger debugger, SyntaxTag tag, int ignoreCount, boolean oneShot) {
            this.kind = BreakpointKind.TAG;
            this.lineLocation = null;
            this.tag = tag;
            this.ignoreCount = ignoreCount;
            this.oneShot = oneShot;
            this.uid = nextBreakpointUID++;
            if (debugger == null) {
                this.state = State.ENABLED_UNRESOLVED;
            } else {
                activate(debugger);
            }
            breakpoints.put(uid, this);
        }

        private void activate(Debugger debugger) {
            if (breakpoint != null) {
                throw new IllegalStateException("Breakpoint already activated");
            }
            if (state == State.DISPOSED) {
                throw new IllegalStateException("Breakpoint already disposed");
            }
            try {
                switch (kind) {
                    case LINE:
                        breakpoint = debugger.setLineBreakpoint(ignoreCount, lineLocation, oneShot);
                        break;
                    case TAG:
                        breakpoint = debugger.setTagBreakpoint(ignoreCount, tag, oneShot);
                        break;
                    default:
                        throw new IllegalStateException("Unexpected breakpoint kind");
                }
                if (conditionSource != null) {
                    breakpoint.setCondition(conditionSource.getCode());
                    conditionSource = null;
                }
                if (state == State.DISABLED_UNRESOLVED) {
                    breakpoint.setEnabled(false);
                }
                state = null;
            } catch (IOException ex) {
                throw new IllegalStateException("Failure to activate breakpoint " + uid + ":  " + ex.getMessage());
            }
        }

        int getID() {
            return uid;
        }

        String describeState() {
            return (breakpoint == null ? state : breakpoint.getState()).getName();
        }

        String describeLocation() {
            if (breakpoint == null) {
                switch (kind) {
                    case LINE:
                        return "Line: " + lineLocation.getShortDescription();
                    case TAG:
                        return "Tag " + tag.name();
                    default:
                        throw new IllegalStateException("Unexpected breakpoint state");
                }
            }
            return breakpoint.getLocationDescription();
        }

        void setEnabled(boolean enabled) {
            if (breakpoint == null) {
                switch (state) {
                    case ENABLED_UNRESOLVED:
                        if (!enabled) {
                            state = State.DISABLED_UNRESOLVED;
                        }
                        break;
                    case DISABLED_UNRESOLVED:
                        if (enabled) {
                            state = State.ENABLED_UNRESOLVED;
                        }
                        break;
                    case DISPOSED:
                        throw new IllegalStateException("Disposed breakpoints must stay disposed");
                    default:
                        throw new IllegalStateException("Unexpected breakpoint state");
                }
            } else {
                breakpoint.setEnabled(enabled);
            }
        }

        boolean isEnabled() {
            return breakpoint == null ? (state == State.ENABLED_UNRESOLVED) : breakpoint.isEnabled();
        }

        void setCondition(String expr) throws IOException {
            if (breakpoint == null) {
                conditionSource = expr == null ? null : Source.fromText(expr, "breakpoint condition from text: " + expr);
            } else {
                breakpoint.setCondition(expr);
            }
        }

        String getCondition() {
            final Source source = breakpoint == null ? conditionSource : breakpoint.getCondition();
            return source == null ? null : source.getCode();
        }

        int getIgnoreCount() {
            return breakpoint == null ? ignoreCount : breakpoint.getIgnoreCount();
        }

        int getHitCount() {
            return breakpoint == null ? 0 : breakpoint.getHitCount();
        }

        void dispose() {
            if (breakpoint == null) {
                if (state == State.DISPOSED) {
                    throw new IllegalStateException("Breakpoint already disposed");
                }
            } else {
                breakpoint.dispose();
                breakpoint = null;
            }
            state = State.DISPOSED;
            breakpoints.remove(uid);
            conditionSource = null;
        }
    }
}
