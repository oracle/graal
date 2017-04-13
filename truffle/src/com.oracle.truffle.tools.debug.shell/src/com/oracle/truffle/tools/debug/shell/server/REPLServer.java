/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Language;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;
import com.oracle.truffle.tools.debug.shell.server.InstrumentationUtils.LocationPrinter;

/**
 * The server side of a simple message-based protocol for a possibly remote language
 * Read-Eval-Print-Loop.
 */
@SuppressWarnings("deprecation")
@Deprecated
public final class REPLServer {

    private static final String REPL_SERVER_INSTRUMENT = "REPLServer";

    private static int nextBreakpointUID = 0;

    // Language-agnostic
    private final PolyglotEngine engine;
    private final String statusPrefix;
    private final Map<String, REPLHandler> handlerMap = new HashMap<>();
    private final LocationPrinter locationPrinter = new InstrumentationUtils.LocationPrinter();
    private final REPLVisualizer visualizer = new REPLVisualizer();

    private Context currentServerContext;

    /** Languages sorted by name. */
    private final TreeSet<Language> engineLanguages = new TreeSet<>();

    /** MAP: language name => Language (case insensitive). */
    private final Map<String, Language> nameToLanguage = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    // TODO (mlvdv) Language-specific
    private final PolyglotEngine.Language defaultLanguage = null;

    private final Map<Integer, BreakpointInfo> breakpoints = new WeakHashMap<>();

    @SuppressWarnings("unused")
    public REPLServer(com.oracle.truffle.tools.debug.shell.client.SimpleREPLClient client) {
        this.engine = PolyglotEngine.newBuilder().build();
        this.engine.getRuntime().getInstruments().get(REPL_SERVER_INSTRUMENT).setEnabled(true);

        engineLanguages.addAll(engine.getLanguages().values());

        for (Language language : engineLanguages) {
            nameToLanguage.put(language.getName().toLowerCase(), language);
        }
        statusPrefix = "";
    }

    public void add(REPLHandler handler) {
        handlerMap.put(handler.getOp(), handler);
    }

    /**
     * Start sever: load commands, generate initial context.
     */
    public void start() {

        add(REPLHandler.BACKTRACE_HANDLER);
        add(REPLHandler.BREAK_AT_LINE_HANDLER);
        add(REPLHandler.BREAK_AT_LINE_ONCE_HANDLER);
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
        add(REPLHandler.SET_BREAK_CONDITION_HANDLER);
        add(REPLHandler.SET_LANGUAGE_HANDLER);
        add(REPLHandler.STEP_INTO_HANDLER);
        add(REPLHandler.STEP_OUT_HANDLER);
        add(REPLHandler.STEP_OVER_HANDLER);
        add(REPLHandler.UNSET_BREAK_CONDITION_HANDLER);

        this.currentServerContext = new Context(null, defaultLanguage);
    }

    @SuppressWarnings("static-method")
    public String getWelcome() {
        return "GraalVM Polyglot Debugger 0.9\n" + "Copyright (c) 2013-6, Oracle and/or its affiliates";
    }

    public LocationPrinter getLocationPrinter() {
        return locationPrinter;
    }

    /**
     * Execution context of a halted program, possibly nested.
     */
    public final class Context {

        private final int level;
        private Language currentLanguage;

        Context(Context predecessor, Language language) {
            this.level = predecessor == null ? 0 : predecessor.getLevel() + 1;
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
            return null;
        }

        /**
         * Get access to display methods appropriate to the language at halted node.
         */
        REPLVisualizer getVisualizer() {
            return visualizer;
        }

        @SuppressWarnings("unused")
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
            return symbol.execute(args.toArray(new Object[0])).get();
        }

        @SuppressWarnings("unused")
        void eval(Source source, boolean stepInto) {
            engine.eval(source);
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
        @SuppressWarnings("unused")
        Object eval(String code, Integer frameNumber, boolean stepInto) throws IOException {
            return null;
        }

        @SuppressWarnings("unused")
        public String displayValue(Integer frameNumber, Object value, int trim) {
            return null;
        }

        /**
         * The frame where execution is halted in this context.
         */
        MaterializedFrame getFrameAtHalt() {
            return null;
        }

        /**
         * Dispatches a REPL request to the appropriate handler.
         */
        com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request) {
            final String command = request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.OP);
            final REPLHandler handler = handlerMap.get(command);

            if (handler == null) {
                final com.oracle.truffle.tools.debug.shell.REPLMessage message = new com.oracle.truffle.tools.debug.shell.REPLMessage();
                message.put(com.oracle.truffle.tools.debug.shell.REPLMessage.OP, command);
                message.put(com.oracle.truffle.tools.debug.shell.REPLMessage.STATUS, com.oracle.truffle.tools.debug.shell.REPLMessage.FAILED);
                message.put(com.oracle.truffle.tools.debug.shell.REPLMessage.DISPLAY_MSG, statusPrefix + " op \"" + command + "\" not supported");
                final com.oracle.truffle.tools.debug.shell.REPLMessage[] reply = new com.oracle.truffle.tools.debug.shell.REPLMessage[]{message};
                return reply;
            }
            return handler.receive(request, REPLServer.this);
        }

        /**
         * @return Node where halted
         */
        Node getNode() {
            return null;
        }

        /**
         * @return Frame where halted
         */
        MaterializedFrame getFrame() {
            return null;
        }

        /**
         * Access to the execution stack.
         *
         * @return immutable list of stack elements
         */
        List<FrameInstance> getStack() {
            return null;
        }

        public String getLanguageName() {
            return currentLanguage == null ? null : currentLanguage.getName();
        }

        /**
         * Case-insensitive; returns actual language name set.
         *
         * @throws IOException if fails
         */
        String setLanguage(String name) throws IOException {
            assert name != null;
            final Language language = nameToLanguage.get(name.toLowerCase());
            if (language == null) {
                throw new IOException("Language \"" + name + "\" not supported");
            }
            if (language == currentLanguage) {
                return currentLanguage.getName();
            }
            this.currentLanguage = language;
            return language.getName();
        }

        void prepareStepOut() {
        }

        @SuppressWarnings("unused")
        void prepareStepInto(int repeat) {
        }

        @SuppressWarnings("unused")
        void prepareStepOver(int repeat) {
        }

        void prepareContinue() {
        }

        void kill() {
        }

    }

    /**
     * Ask the server to handle a request. Return a non-empty array of messages to simulate remote
     * operation where the protocol has possibly multiple messages being returned asynchronously in
     * response to each request.
     */
    public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request) {
        if (currentServerContext == null) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage message = new com.oracle.truffle.tools.debug.shell.REPLMessage();
            message.put(com.oracle.truffle.tools.debug.shell.REPLMessage.STATUS, com.oracle.truffle.tools.debug.shell.REPLMessage.FAILED);
            message.put(com.oracle.truffle.tools.debug.shell.REPLMessage.DISPLAY_MSG, "server not started");
            final com.oracle.truffle.tools.debug.shell.REPLMessage[] reply = new com.oracle.truffle.tools.debug.shell.REPLMessage[]{message};
            return reply;
        }
        return currentServerContext.receive(request);
    }

    Context getCurrentContext() {
        return currentServerContext;
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

    BreakpointInfo setLineBreakpoint(int ignoreCount, com.oracle.truffle.api.source.LineLocation lineLocation, boolean oneShot) throws IOException {
        final BreakpointInfo info = new LineBreakpointInfo(lineLocation, ignoreCount, oneShot);
        info.activate();
        return info;
    }

    synchronized BreakpointInfo findBreakpoint(int id) {
        return breakpoints.get(id);
    }

    /**
     * Gets a list of the currently existing breakpoints.
     */
    Collection<BreakpointInfo> getBreakpoints() {
        // TODO (mlvdv) check if each is currently resolved
        return new ArrayList<>(breakpoints.values());
    }

    @Registration(id = "REPLServer")
    public static final class REPLServerInstrument extends TruffleInstrument {

        @Override
        protected void onCreate(Env env) {
            env.registerService(env.getInstrumenter());
        }
    }

    final class LineBreakpointInfo extends BreakpointInfo {

        @SuppressWarnings("unused")
        private LineBreakpointInfo(com.oracle.truffle.api.source.LineLocation lineLocation, int ignoreCount, boolean oneShot) {
            super(ignoreCount, oneShot);
        }

        @Override
        protected void activate() throws IOException {
        }

        @Override
        String describeLocation() {
            return null;
        }

    }

    abstract class BreakpointInfo {

        protected final int uid;
        protected final boolean oneShot;
        protected final int ignoreCount;

        protected Source conditionSource;

        protected BreakpointInfo(int ignoreCount, boolean oneShot) {
            this.ignoreCount = ignoreCount;
            this.oneShot = oneShot;
            this.uid = nextBreakpointUID++;
        }

        protected abstract void activate() throws IOException;

        abstract String describeLocation();

        int getID() {
            return uid;
        }

        String describeState() {
            return null;
        }

        void setEnabled(@SuppressWarnings("unused") boolean enabled) {
        }

        boolean isEnabled() {
            return false;
        }

        @SuppressWarnings("unused")
        void setCondition(String expr) throws IOException {
        }

        String getCondition() {
            return null;
        }

        int getIgnoreCount() {
            return 0;
        }

        int getHitCount() {
            return 0;
        }

        void dispose() {
        }

        String summarize() {
            final StringBuilder sb = new StringBuilder("Breakpoint");
            sb.append(" id=" + uid);
            sb.append(" locn=(" + describeLocation());
            sb.append(") " + describeState());
            return sb.toString();
        }
    }

    static class REPLVisualizer {

        /**
         * A short description of a source location in terms of source + line number.
         */
        String displaySourceLocation(Node node) {
            if (node == null) {
                return "<unknown>";
            }
            SourceSection section = node.getSourceSection();
            boolean estimated = false;
            if (section == null) {
                section = node.getEncapsulatingSourceSection();
                estimated = true;
            }
            if (section == null) {
                return "<error: source location>";
            }
            return InstrumentationUtils.getShortDescription(section) + (estimated ? "~" : "");
        }

        /**
         * Describes the name of the method containing a node.
         */
        String displayMethodName(Node node) {
            if (node == null) {
                return null;
            }
            RootNode root = node.getRootNode();
            if (root != null && root.getName() != null) {
                return root.getName();
            }
            return "??";
        }

        /**
         * The name of the method.
         */
        String displayCallTargetName(CallTarget callTarget) {
            return callTarget.toString();
        }

        /**
         * Converts a value in the guest language to a display string. If
         *
         * @param trim if {@code > 0}, them limit size of String to either the value of trim or the
         *            number of characters in the first line, whichever is lower.
         */
        String displayValue(Object value, int trim) {
            if (value == null) {
                return "<empty>";
            }
            return trim(value.toString(), trim);
        }

        /**
         * Converts a slot identifier in the guest language to a display string.
         */
        String displayIdentifier(FrameSlot slot) {
            return slot.getIdentifier().toString();
        }
    }

    /**
     * Trims text if {@code trim > 0} to the shorter of {@code trim} or the length of the first line
     * of test. Identity if {@code trim <= 0}.
     */
    protected static String trim(String text, int trim) {
        if (trim == 0) {
            return text;
        }
        final String[] lines = text.split("\n");
        String result = lines[0];
        if (lines.length == 1) {
            if (result.length() <= trim) {
                return result;
            }
            if (trim <= 3) {
                return result.substring(0, Math.min(result.length() - 1, trim - 1));
            } else {
                return result.substring(0, trim - 4) + "...";
            }
        }
        return (result.length() < trim - 3 ? result : result.substring(0, trim - 4)) + "...";
    }
}
