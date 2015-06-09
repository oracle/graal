/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.tools.debug;

import java.util.*;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.vm.*;
import com.oracle.truffle.api.vm.TruffleVM.Language;
import com.oracle.truffle.tools.debug.engine.*;
import com.oracle.truffle.tools.debug.shell.*;
import com.oracle.truffle.tools.debug.shell.client.*;
import com.oracle.truffle.tools.debug.shell.server.*;

/**
 * Instantiation of the "server" side of the "REPL*" debugger for the Simple language.
 * <p>
 * The SL parser is not equipped to parse program fragments, so any debugging functions that depend
 * on this are not supported, for example {@link DebugEngine#eval(Source, Node, MaterializedFrame)}
 * and {@link Breakpoint#setCondition(String)}.
 *
 * @see SimpleREPLClient
 */
public final class SLREPLServer implements REPLServer {

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
    private final DebugEngine slDebugEngine;
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

        TruffleVM vm = TruffleVM.newVM().build();
        this.language = vm.getLanguages().get("application/x-sl");
        assert language != null;

        final SLREPLDebugClient slDebugClient = new SLREPLDebugClient(language);
        this.slDebugEngine = DebugEngine.create(slDebugClient, language);

        this.statusPrefix = language.getShortName() + " REPL:";
    }

    private void setClient(SimpleREPLClient replClient) {
        this.replClient = replClient;
    }

    public REPLMessage start() {

        this.currentServerContext = new SLServerContext(null, null, null);

        // SL doesn't load modules (like other languages), so we just return a success
        final REPLMessage reply = new REPLMessage();
        reply.put(REPLMessage.STATUS, REPLMessage.SUCCEEDED);
        reply.put(REPLMessage.DISPLAY_MSG, language.getShortName() + " started");
        return reply;
    }

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

        public SLServerContext(SLServerContext predecessor, Node astNode, MaterializedFrame mFrame) {
            super(predecessor == null ? 0 : predecessor.getLevel() + 1, astNode, mFrame);
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
        public DebugEngine getDebugEngine() {
            return slDebugEngine;
        }

    }

    /**
     * Specialize the standard SL debug context by notifying the REPL client when execution is
     * halted, e.g. at a breakpoint.
     * <p>
     * Before notification, the server creates a new context at the halted location, in which
     * subsequent evaluations take place until such time as the client says to "continue".
     * <p>
     * This implementation "cheats" the intended asynchronous architecture by calling back directly
     * to the client with the notification.
     */
    private final class SLREPLDebugClient implements DebugClient {

        private final Language language;

        SLREPLDebugClient(Language language) {
            this.language = language;
        }

        public void haltedAt(Node node, MaterializedFrame mFrame, List<String> warnings) {
            // Create and push a new debug context where execution is halted
            currentServerContext = new SLServerContext(currentServerContext, node, mFrame);

            // Message the client that execution is halted and is in a new debugging context
            final REPLMessage message = new REPLMessage();
            message.put(REPLMessage.OP, REPLMessage.STOPPED);
            final SourceSection src = node.getSourceSection();
            final Source source = src.getSource();
            message.put(REPLMessage.SOURCE_NAME, source.getName());
            message.put(REPLMessage.FILE_PATH, source.getPath());
            message.put(REPLMessage.LINE_NUMBER, Integer.toString(src.getStartLine()));
            message.put(REPLMessage.STATUS, REPLMessage.SUCCEEDED);
            message.put(REPLMessage.DEBUG_LEVEL, Integer.toString(currentServerContext.getLevel()));
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

        public Language getLanguage() {
            return language;
        }
    }

}
