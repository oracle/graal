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
package com.oracle.truffle.tools.debug.shell.client;

import java.io.*;
import java.util.*;

import jline.console.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.tools.debug.shell.*;

/**
 * A very simple line-oriented, language-agnostic debugging client shell: the first step toward a
 * general, extensible debugging framework designed to be adapted for remote debugging.
 * <p>
 * The architecture of this debugging framework is modeled loosely on <a
 * href="https://github.com/clojure/tools.nrepl">nREPL</a>, a network REPL developed by the Clojure
 * community with a focus on generality:
 * <ul>
 * <li>Client and (possibly remote) server communicate via <em>messages</em> carried over some
 * <em>transport</em>;</li>
 * <li>A message is a <em>map</em> of key/value pairs;</li>
 * <li>Keys and values are <em>strings</em>;</li>
 * <li>The client sends messages as <em>requests</em> to a server;</li>
 * <li>A server dispatches each incoming request to an appropriate <em>handler</em> that takes
 * appropriate action and responds to the client with one or more messages; and</li>
 * <li>Many implementations of the <em>transport</em> are possible.</li>
 * </ul>
 * <p>
 * <strong>Compromises:</strong>
 * <p>
 * In order to get
 * <ol>
 * <li>A debugging session should start from this shell, but there is no machinery in place for
 * doing that; instead, an entry into the language implementation creates both the server and this
 * shell;</li>
 * <li>The current startup sequence is based on method calls, not messages;</li>
 * <li>Only a very few request types and keys are implemented, omitting for example request and
 * session ids;</li>
 * <li>Message passing is synchronous and "transported" via method calls;</li>
 * <li>Asynchrony is emulated by having each call to the server pass only a message, and by having
 * the server return only a list of messages.</li>
 * </ol>
 *
 * @see REPLServer
 * @see REPLMessage
 */
public class SimpleREPLClient implements REPLClient {

    private static final String REPLY_PREFIX = "==> ";
    private static final String FAIL_PREFIX = "**> ";
    private static final String WARNING_PREFIX = "!!> ";
    private static final String TRACE_PREFIX = ">>> ";
    private static final String[] NULL_ARGS = new String[0];

    static final String INFO_LINE_FORMAT = "    %s\n";
    static final String CODE_LINE_FORMAT = "    %3d  %s\n";
    static final String CODE_LINE_BREAK_FORMAT = "--> %3d  %s\n";

    private static final String STACK_FRAME_FORMAT = "    %3d: at %s in %s    line =\"%s\"\n";
    private static final String STACK_FRAME_SELECTED_FORMAT = "==> %3d: at %s in %s    line =\"%s\"\n";

    private final ExecutionContext executionContext;  // Language context

    // Top level commands
    private final Map<String, REPLCommand> commandMap = new HashMap<>();
    private final Collection<String> commandNames = new TreeSet<>();

    // Local options
    private final Map<String, LocalOption> localOptions = new HashMap<>();
    private final Collection<String> optionNames = new TreeSet<>();

    // Current local context
    ClientContextImpl clientContext;

    // Cheating for the prototype; prototype startup now happens from the language server.
    // So this isn't used.
    public static void main(String[] args) {
        final SimpleREPLClient repl = new SimpleREPLClient(null, null);
        repl.start();
    }

    private final ConsoleReader reader;

    private final PrintStream writer;

    private final REPLServer replServer;

    private final LocalOption astDepthOption = new IntegerOption(9, "astdepth", "default depth for AST display");

    private final LocalOption autoWhereOption = new BooleanOption(true, "autowhere", "run the \"where\" command after each navigation");

    private final LocalOption autoNodeOption = new BooleanOption(false, "autonode", "run the \"truffle node\" command after each navigation");

    private final LocalOption autoSubtreeOption = new BooleanOption(false, "autosubtree", "run the \"truffle subtree\" command after each navigation");

    private final LocalOption autoASTOption = new BooleanOption(false, "autoast", "run the \"truffle ast\" command after each navigation");

    private final LocalOption listSizeOption = new IntegerOption(25, "listsize", "default number of lines to list");

    private final LocalOption traceMessagesOption = new BooleanOption(false, "tracemessages", "trace REPL messages between client and server");

    private final LocalOption verboseBreakpointInfoOption = new BooleanOption(true, "verbosebreakpointinfo", "\"info breakpoint\" displays more info");

    private void addOption(LocalOption localOption) {
        final String optionName = localOption.getName();
        localOptions.put(optionName, localOption);
        optionNames.add(optionName);
    }

    /**
     * Non-null when the user has named a file other than where halted, providing context for
     * commands such as "break"; if no explicit selection, then defaults to where halted. This is
     * session state, so it persists across halting contexts.
     */
    private Source selectedSource = null;

    public SimpleREPLClient(ExecutionContext context, REPLServer replServer) {
        this.executionContext = context;
        this.replServer = replServer;
        this.writer = System.out;
        try {
            this.reader = new ConsoleReader();
        } catch (IOException e) {
            throw new RuntimeException("Unable to create console " + e);
        }

        addCommand(backtraceCommand);
        addCommand(REPLRemoteCommand.BREAK_AT_LINE_CMD);
        addCommand(REPLRemoteCommand.BREAK_AT_LINE_ONCE_CMD);
        addCommand(REPLRemoteCommand.BREAK_AT_THROW_CMD);
        addCommand(REPLRemoteCommand.BREAK_AT_THROW_ONCE_CMD);
        addCommand(REPLRemoteCommand.CLEAR_BREAK_CMD);
        addCommand(REPLRemoteCommand.CONDITION_BREAK_CMD);
        addCommand(REPLRemoteCommand.CONTINUE_CMD);
        addCommand(REPLRemoteCommand.DELETE_CMD);
        addCommand(REPLRemoteCommand.DISABLE_CMD);
        addCommand(REPLRemoteCommand.DOWN_CMD);
        addCommand(REPLRemoteCommand.ENABLE_CMD);
        addCommand(evalCommand);
        addCommand(fileCommand);
        addCommand(REPLRemoteCommand.FRAME_CMD);
        addCommand(helpCommand);
        addCommand(infoCommand);
        addCommand(REPLRemoteCommand.KILL_CMD);
        addCommand(listCommand);
        addCommand(REPLRemoteCommand.LOAD_RUN_CMD);
        addCommand(REPLRemoteCommand.LOAD_STEP_CMD);
        addCommand(quitCommand);
        addCommand(setCommand);
        addCommand(REPLRemoteCommand.STEP_INTO_CMD);
        addCommand(REPLRemoteCommand.STEP_OUT_CMD);
        addCommand(REPLRemoteCommand.STEP_OVER_CMD);
        addCommand(truffleCommand);
        addCommand(REPLRemoteCommand.UP_CMD);
        addCommand(whereCommand);

        infoCommand.addCommand(infoBreakCommand);
        infoCommand.addCommand(infoLanguageCommand);
        infoCommand.addCommand(infoSetCommand);

        truffleCommand.addCommand(truffleASTCommand);
        truffleCommand.addCommand(truffleNodeCommand);
        truffleCommand.addCommand(truffleSubtreeCommand);

        addOption(astDepthOption);
        addOption(autoASTOption);
        addOption(autoNodeOption);
        addOption(autoSubtreeOption);
        addOption(autoWhereOption);
        addOption(listSizeOption);
        addOption(traceMessagesOption);
        addOption(verboseBreakpointInfoOption);
    }

    public void start() {

        REPLMessage startReply = replServer.start();

        if (startReply.get(REPLMessage.STATUS).equals(REPLMessage.FAILED)) {
            clientContext.displayFailReply(startReply.get(REPLMessage.DISPLAY_MSG));
            throw new RuntimeException("Can't start REPL server");
        }

        this.clientContext = new ClientContextImpl(null, null);

        try {
            clientContext.startSession();
        } finally {
            clientContext.displayReply("Goodbye from " + executionContext.getLanguageShortName() + "/REPL");
        }

    }

    public void addCommand(REPLCommand replCommand) {
        final String commandName = replCommand.getCommand();
        final String abbreviation = replCommand.getAbbreviation();

        commandNames.add(commandName);
        commandMap.put(commandName, replCommand);
        if (abbreviation != null) {
            commandMap.put(abbreviation, replCommand);
        }
    }

    private class ClientContextImpl implements REPLClientContext {

        private final ClientContextImpl predecessor;
        private final int level;

        // Information about where the execution is halted
        /** The source where execution, if any, is halted; null if none. */
        private Source haltedSource = null;
        /** The line number where execution, if any, is halted; 0 if none. */
        private int haltedLineNumber = 0;
        /** The stack where execution, if any, is halted; null if none. Evaluated lazily. */
        private List<REPLFrame> frames = null;

        /** The frame number currently selected by user. */
        private int selectedFrameNumber = 0;

        private String currentPrompt;

        /**
         * Create a new context on the occasion of an execution halting.
         */
        public ClientContextImpl(ClientContextImpl predecessor, REPLMessage message) {
            this.predecessor = predecessor;
            this.level = predecessor == null ? 0 : predecessor.level + 1;

            if (message != null) {
                try {
                    this.haltedSource = Source.fromFileName(message.get(REPLMessage.SOURCE_NAME));
                    selectedSource = this.haltedSource;
                    try {
                        haltedLineNumber = Integer.parseInt(message.get(REPLMessage.LINE_NUMBER));
                    } catch (NumberFormatException e) {
                        haltedLineNumber = 0;
                    }
                } catch (IOException e1) {
                    this.haltedSource = null;
                    this.haltedLineNumber = 0;
                }
            }
            updatePrompt();
        }

        private void selectSource(String fileName) {
            try {
                selectedSource = Source.fromFileName(fileName);
            } catch (IOException e1) {
                selectedSource = null;
            }
            updatePrompt();
        }

        private void updatePrompt() {
            if (level == 0) {
                // 0-level context; no executions halted.
                if (selectedSource == null) {
                    final String languageName = executionContext.getLanguageShortName();
                    currentPrompt = languageName == null ? "() " : "(" + languageName + ") ";
                } else {
                    currentPrompt = "(" + selectedSource.getShortName() + ") ";
                }
            } else if (selectedSource != null && selectedSource != haltedSource) {
                // User is focusing somewhere else than the current locn; show no line number.
                final StringBuilder sb = new StringBuilder();
                sb.append("(<" + Integer.toString(level) + "> ");
                sb.append(selectedSource.getShortName());
                sb.append(") ");
                currentPrompt = sb.toString();
            } else {
                // Prompt reveals where currently halted.
                final StringBuilder sb = new StringBuilder();
                sb.append("(<" + Integer.toString(level) + "> ");
                sb.append(haltedSource == null ? "??" : haltedSource.getShortName());
                if (haltedLineNumber > 0) {
                    sb.append(":" + Integer.toString(haltedLineNumber));
                }
                sb.append(") ");
                currentPrompt = sb.toString();
            }

        }

        public Source source() {
            return haltedSource;
        }

        public int lineNumber() {
            return haltedLineNumber;
        }

        public List<REPLFrame> frames() {
            if (frames == null) {
                final REPLMessage request = new REPLMessage(REPLMessage.OP, REPLMessage.BACKTRACE);
                final REPLMessage[] replies = sendToServer(request);
                if (replies[0].get(REPLMessage.STATUS).equals(REPLMessage.FAILED)) {
                    return null;
                }
                frames = new ArrayList<>();
                for (REPLMessage reply : replies) {
                    final int index = reply.getIntValue(REPLMessage.FRAME_NUMBER);
                    final String locationFilePath = reply.get(REPLMessage.FILE_PATH);
                    final Integer locationLineNumber = reply.getIntValue(REPLMessage.LINE_NUMBER);
                    final String locationDescription = reply.get(REPLMessage.SOURCE_LOCATION);
                    final String name = reply.get(REPLMessage.METHOD_NAME);
                    final String sourceLineText = reply.get(REPLMessage.SOURCE_LINE_TEXT);
                    frames.add(new REPLFrameImpl(index, locationFilePath, locationLineNumber, locationDescription, name, sourceLineText));
                }
                frames = Collections.unmodifiableList(frames);
            }
            return frames;
        }

        public int level() {
            return this.level;
        }

        public Source getSelectedSource() {
            return selectedSource == null ? haltedSource : selectedSource;
        }

        public int getSelectedFrameNumber() {
            return selectedFrameNumber;
        }

        public String stringQuery(String op) {
            assert op != null;
            REPLMessage request = null;
            switch (op) {
                case REPLMessage.TRUFFLE_AST:
                    request = truffleASTCommand.createRequest(clientContext, NULL_ARGS);
                    break;
                case REPLMessage.TRUFFLE_SUBTREE:
                    request = truffleSubtreeCommand.createRequest(clientContext, NULL_ARGS);
                    break;
                default:
                    request = new REPLMessage();
                    request.put(REPLMessage.OP, op);
            }
            if (request == null) {
                return null;
            }
            final REPLMessage[] replies = sendToServer(request);
            if (replies[0].get(REPLMessage.STATUS).equals(REPLMessage.FAILED)) {
                return null;
            }
            return replies[0].get(REPLMessage.DISPLAY_MSG);
        }

        public void selectFrameNumber(int frameNumber) {
            this.selectedFrameNumber = frameNumber;
        }

        void displayWhere() {
            if (level == 0) {
                displayFailReply("no active execution");
                return;
            }

            Source whereSource = null;
            int whereLineNumber = 0;

            if (selectedFrameNumber == 0) {
                whereSource = haltedSource;
                whereLineNumber = haltedLineNumber;
            } else {
                final REPLFrame frame = frames().get(selectedFrameNumber);
                final String locationFileName = frame.locationFilePath();
                if (locationFileName != null) {
                    try {
                        whereSource = Source.fromFileName(locationFileName);
                    } catch (IOException e) {
                    }
                }
                whereLineNumber = frame.locationLineNumber();
            }
            if (whereSource == null) {
                displayFailReply("Frame " + selectedFrameNumber + ": source unavailable");
                return;
            }
            final int listSize = listSizeOption.getInt();

            final int fileLineCount = whereSource.getLineCount();
            final String code = whereSource.getCode();

            writer.println("Frame " + selectedFrameNumber + ": " + whereSource.getShortName() + "\n");
            final int halfListSize = listSize / 2;
            final int startLineNumber = Math.max(1, whereLineNumber - halfListSize);
            final int lastLineNumber = Math.min(startLineNumber + listSize - 1, fileLineCount);
            for (int line = startLineNumber; line <= lastLineNumber; line++) {
                final int offset = whereSource.getLineStartOffset(line);
                final String lineText = code.substring(offset, offset + whereSource.getLineLength(line));
                if (line == whereLineNumber) {
                    writer.format(CODE_LINE_BREAK_FORMAT, line, lineText);
                } else {
                    writer.format(CODE_LINE_FORMAT, line, lineText);
                }
            }
        }

        public void displayStack() {
            final List<REPLFrame> frameList = frames();
            if (frameList == null) {
                writer.println("<empty stack>");
            } else {
                for (REPLFrame frame : frameList) {
                    String sourceLineText = frame.sourceLineText();
                    if (sourceLineText == null) {
                        sourceLineText = "<??>";
                    }
                    if (frame.index() == selectedFrameNumber) {
                        writer.format(STACK_FRAME_SELECTED_FORMAT, frame.index(), frame.locationDescription(), frame.name(), sourceLineText);
                    } else {
                        writer.format(STACK_FRAME_FORMAT, frame.index(), frame.locationDescription(), frame.name(), sourceLineText);
                    }
                }
            }
        }

        public void displayInfo(String message) {
            writer.format(INFO_LINE_FORMAT, message);
        }

        public void displayReply(String message) {
            writer.println(REPLY_PREFIX + message);
        }

        public void displayFailReply(String message) {
            writer.println(FAIL_PREFIX + message);
        }

        public void displayWarnings(String warnings) {
            for (String warning : warnings.split("\\n")) {
                writer.println(WARNING_PREFIX + warning);
            }
        }

        public void traceMessage(String message) {
            writer.println(TRACE_PREFIX + message);
        }

        public void startSession() {

            while (true) {
                try {
                    String[] args;
                    String line = reader.readLine(currentPrompt).trim();
                    if (line.startsWith("eval ")) {
                        args = new String[]{"eval", line.substring(5)};
                    } else {
                        args = line.split("[ \t]+");
                    }
                    if (args.length == 0) {
                        break;
                    }
                    final String cmd = args[0];

                    if (cmd.isEmpty()) {
                        continue;
                    }

                    REPLCommand command = commandMap.get(cmd);
                    while (command instanceof REPLIndirectCommand) {
                        if (traceMessagesOption.getBool()) {
                            traceMessage("Executing indirect: " + command.getCommand());
                        }
                        command = ((REPLIndirectCommand) command).getCommand(args);
                    }
                    if (command == null) {
                        clientContext.displayFailReply("Unrecognized command \"" + cmd + "\"");
                        continue;
                    }
                    if (command instanceof REPLLocalCommand) {
                        if (traceMessagesOption.getBool()) {
                            traceMessage("Executing local: " + command.getCommand());
                        }
                        ((REPLLocalCommand) command).execute(args);

                    } else if (command instanceof REPLRemoteCommand) {
                        final REPLRemoteCommand remoteCommand = (REPLRemoteCommand) command;

                        final REPLMessage request = remoteCommand.createRequest(clientContext, args);
                        if (request == null) {
                            continue;
                        }

                        REPLMessage[] replies = sendToServer(request);

                        remoteCommand.processReply(clientContext, replies);
                    } else {
                        assert false; // Should not happen.
                    }

                } catch (REPLContinueException ex) {
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }

        private REPLMessage[] sendToServer(REPLMessage request) {
            if (traceMessagesOption.getBool()) {
                clientContext.traceMessage("Sever request:");
                request.print(writer, "  ");
            }

            REPLMessage[] replies = replServer.receive(request);

            assert replies != null && replies.length > 0;
            if (traceMessagesOption.getBool()) {
                if (replies.length > 1) {
                    clientContext.traceMessage("Received " + replies.length + " server replies");
                    int replyCount = 0;
                    for (REPLMessage reply : replies) {
                        clientContext.traceMessage("Server Reply " + replyCount++ + ":");
                        reply.print(writer, "  ");
                    }
                } else {
                    clientContext.traceMessage("Received reply:");
                    replies[0].print(writer, "  ");
                }
            }
            return replies;
        }

        private final class REPLFrameImpl implements REPLFrame {

            private final int index;
            private final String locationFilePath;
            private final Integer locationLineNumber;
            private final String locationDescription;
            private final String name;
            private final String sourceLineText;

            REPLFrameImpl(int index, String locationFilePath, Integer locationLineNumber, String locationDescription, String name, String sourceLineText) {
                this.index = index;
                this.locationFilePath = locationFilePath;
                this.locationLineNumber = locationLineNumber;
                this.locationDescription = locationDescription;
                this.name = name;
                this.sourceLineText = sourceLineText;
            }

            public int index() {
                return index;
            }

            public String locationFilePath() {
                return locationFilePath;
            }

            public Integer locationLineNumber() {
                return locationLineNumber;
            }

            public String locationDescription() {
                return locationDescription;
            }

            public String name() {
                return name;
            }

            public String sourceLineText() {
                return sourceLineText;
            }

        }

    }

    // Cheating with synchrony: asynchronous replies should arrive here, but don't.
    @Override
    public REPLMessage receive(REPLMessage request) {
        final String result = request.get("result");
        clientContext.displayReply(result != null ? result : request.toString());
        return null;
    }

    /**
     * Cheating with synchrony: take a direct call from the server that execution has halted and
     * we've entered a nested debugging context.
     */
    public void halted(REPLMessage message) {

        // Push a new context for where we've stopped.
        clientContext = new ClientContextImpl(clientContext, message);
        final String warnings = message.get(REPLMessage.WARNINGS);
        if (warnings != null) {
            clientContext.displayWarnings(warnings);
        }
        if (autoWhereOption.getBool()) {
            clientContext.displayWhere();
        }
        if (autoNodeOption.getBool()) {
            final String result = clientContext.stringQuery(REPLMessage.TRUFFLE_NODE);
            if (result != null) {
                displayTruffleNode(result);
            }
        }
        if (autoASTOption.getBool()) {
            final String result = clientContext.stringQuery(REPLMessage.TRUFFLE_AST);
            if (result != null) {
                displayTruffleAST(result);
            }
        }
        if (autoSubtreeOption.getBool()) {
            final String result = clientContext.stringQuery(REPLMessage.TRUFFLE_SUBTREE);
            if (result != null) {
                displayTruffleSubtree(result);
            }
        }

        try {
            clientContext.startSession();
        } finally {

            // To continue execution, pop the context and return
            this.clientContext = clientContext.predecessor;
        }
    }

    /**
     * A command that can be executed without (direct) communication with the server; it may rely on
     * some other method that goes to the server for information.
     */
    private abstract class REPLLocalCommand extends REPLCommand {

        public REPLLocalCommand(String command, String abbreviation, String description) {
            super(command, abbreviation, description);
        }

        abstract void execute(String[] args);
    }

    /**
     * A command that redirects to other commands, based on arguments.
     */
    private abstract class REPLIndirectCommand extends REPLCommand {

        public REPLIndirectCommand(String command, String abbreviation, String description) {
            super(command, abbreviation, description);
        }

        abstract void addCommand(REPLCommand command);

        abstract REPLCommand getCommand(String[] args);
    }

    private final REPLCommand backtraceCommand = new REPLLocalCommand("backtrace", "bt", "Display current stack") {

        @Override
        void execute(String[] args) {
            if (clientContext.level == 0) {
                clientContext.displayFailReply("no active execution");
            } else {
                clientContext.displayStack();
            }
        }
    };

    private final REPLCommand evalCommand = new REPLRemoteCommand("eval", null, "Evaluate a string, in context of the current frame if any") {

        private int evalCounter = 0;

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (args.length > 1) {
                final String code = args[1];
                if (!code.isEmpty()) {
                    // Create a fake entry in the file maps and cache, based on this unique name
                    final String fakeFileName = "<eval" + ++evalCounter + ">";
                    Source.fromNamedText(fakeFileName, code);
                    final REPLMessage request = new REPLMessage();
                    request.put(REPLMessage.OP, REPLMessage.EVAL);
                    request.put(REPLMessage.CODE, code);
                    request.put(REPLMessage.SOURCE_NAME, fakeFileName);
                    if (clientContext.level > 0) {
                        // Specify a requested execution context, if one exists; otherwise top level
                        request.put(REPLMessage.FRAME_NUMBER, Integer.toString(context.getSelectedFrameNumber()));
                    }
                    return request;
                }
            }
            return null;
        }
    };

    private final REPLCommand fileCommand = new REPLRemoteCommand("file", null, "Set/display current file for viewing") {

        final String[] help = {"file:  display current file path", "file <filename>: Set file to be current file for viewing"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (args.length == 1) {
                final Source source = clientContext.getSelectedSource();
                if (source == null) {
                    clientContext.displayFailReply("no file currently selected");
                } else {
                    clientContext.displayReply(source.getPath());
                }
                return null;
            }
            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, REPLMessage.FILE);
            request.put(REPLMessage.SOURCE_NAME, args[1]);
            return request;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            REPLMessage firstReply = replies[0];

            if (firstReply.get(REPLMessage.STATUS).equals(REPLMessage.FAILED)) {
                final String result = firstReply.get(REPLMessage.DISPLAY_MSG);
                clientContext.displayFailReply(result != null ? result : firstReply.toString());
                return;
            }
            final String fileName = firstReply.get(REPLMessage.SOURCE_NAME);
            final String path = firstReply.get(REPLMessage.FILE_PATH);
            clientContext.selectSource(path == null ? fileName : path);
            clientContext.displayReply(clientContext.getSelectedSource().getPath());

            for (int i = 1; i < replies.length; i++) {
                REPLMessage reply = replies[i];
                final String result = reply.get(REPLMessage.DISPLAY_MSG);
                clientContext.displayInfo(result != null ? result : reply.toString());
            }
        }

    };

    private final REPLCommand helpCommand = new REPLLocalCommand("help", null, "Describe commands") {

        final String[] help = {"help:  list available commands", "help <command>: additional information about <command>"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public void execute(String[] args) {

            if (args.length == 1) {
                clientContext.displayReply("Available commands:");
                for (String commandName : commandNames) {
                    final REPLCommand command = commandMap.get(commandName);
                    if (command == null) {
                        clientContext.displayInfo(commandName + ": Error, no implementation for command");
                    } else {
                        final String abbrev = command.getAbbreviation();
                        if (abbrev == null) {
                            clientContext.displayInfo(commandName + ": " + command.getDescription());
                        } else {
                            clientContext.displayInfo(commandName + "(" + abbrev + "): " + command.getDescription());
                        }
                    }
                }
            } else {
                final String cmdName = args[1];
                final REPLCommand cmd = commandMap.get(cmdName);
                if (cmd == null) {
                    clientContext.displayReply("command \"" + cmdName + "\" not recognized");
                } else {
                    final String[] helpLines = cmd.getHelp();
                    if (helpLines == null) {
                        clientContext.displayReply("\"" + cmdName + "\":");
                    } else if (helpLines.length == 1) {
                        clientContext.displayInfo(helpLines[0]);
                    } else {
                        clientContext.displayReply("\"" + cmdName + "\":");
                        for (String line : helpLines) {
                            clientContext.displayInfo(line);
                        }
                    }
                }
            }
        }
    };

    private final REPLIndirectCommand infoCommand = new REPLIndirectCommand(REPLMessage.INFO, null, "Additional information on topics") {

        // "Info" commands
        private final Map<String, REPLCommand> infoCommandMap = new HashMap<>();
        private final Collection<String> infoCommandNames = new TreeSet<>();

        @Override
        public String[] getHelp() {
            final ArrayList<String> lines = new ArrayList<>();
            for (String infoCommandName : infoCommandNames) {
                final REPLCommand cmd = infoCommandMap.get(infoCommandName);
                if (cmd == null) {
                    lines.add("\"" + REPLMessage.INFO + " " + infoCommandName + "\" not implemented");
                } else {
                    lines.add("\"" + REPLMessage.INFO + " " + infoCommandName + "\": " + cmd.getDescription());
                }
            }
            return lines.toArray(new String[0]);
        }

        @Override
        void addCommand(REPLCommand replCommand) {
            final String commandName = replCommand.getCommand();
            final String abbreviation = replCommand.getAbbreviation();

            infoCommandNames.add(commandName);
            infoCommandMap.put(commandName, replCommand);
            if (abbreviation != null) {
                infoCommandMap.put(abbreviation, replCommand);
            }
        }

        @Override
        REPLCommand getCommand(String[] args) {
            if (args.length == 1) {
                clientContext.displayFailReply("info topic not specified; try \"help info\"");
                return null;
            }
            final String topic = args[1];
            REPLCommand command = infoCommandMap.get(topic);
            if (command == null) {
                clientContext.displayFailReply("topic \"" + topic + "\" not recognized");
                return null;
            }

            return command;
        }

    };

    private final REPLCommand infoBreakCommand = new REPLRemoteCommand("breakpoint", "break", "info about breakpoints") {

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, REPLMessage.BREAKPOINT_INFO);
            return request;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            if (replies[0].get(REPLMessage.STATUS).equals(REPLMessage.FAILED)) {
                clientContext.displayFailReply(replies[0].get(REPLMessage.DISPLAY_MSG));
            } else {
                Arrays.sort(replies, new Comparator<REPLMessage>() {

                    public int compare(REPLMessage o1, REPLMessage o2) {
                        try {
                            final int n1 = Integer.parseInt(o1.get(REPLMessage.BREAKPOINT_ID));
                            final int n2 = Integer.parseInt(o2.get(REPLMessage.BREAKPOINT_ID));
                            return Integer.compare(n1, n2);
                        } catch (Exception ex) {
                        }
                        return 0;
                    }

                });
                clientContext.displayReply("Breakpoints set:");
                for (REPLMessage message : replies) {
                    final StringBuilder sb = new StringBuilder();

                    sb.append(Integer.parseInt(message.get(REPLMessage.BREAKPOINT_ID)) + ": ");
                    sb.append("@" + message.get(REPLMessage.INFO_VALUE));
                    sb.append(" (state=" + message.get(REPLMessage.BREAKPOINT_STATE));
                    if (verboseBreakpointInfoOption.getBool()) {
                        sb.append(", group=" + Integer.parseInt(message.get(REPLMessage.BREAKPOINT_GROUP_ID)));
                        sb.append(", hits=" + Integer.parseInt(message.get(REPLMessage.BREAKPOINT_HIT_COUNT)));
                        sb.append(", ignore=" + Integer.parseInt(message.get(REPLMessage.BREAKPOINT_IGNORE_COUNT)));
                    }
                    final String condition = message.get(REPLMessage.BREAKPOINT_CONDITION);
                    if (condition != null) {
                        sb.append(", condition=\"" + condition + "\"");
                    }
                    sb.append(")");
                    clientContext.displayInfo(sb.toString());
                }
            }
        }
    };

    private final REPLCommand infoLanguageCommand = new REPLRemoteCommand("language", "lang", "language and implementation details") {

        final String[] help = {"info language:  list details about the language implementation"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, REPLMessage.INFO);
            request.put(REPLMessage.TOPIC, REPLMessage.LANGUAGE);
            return request;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            if (replies[0].get(REPLMessage.STATUS).equals(REPLMessage.FAILED)) {
                clientContext.displayFailReply(replies[0].get(REPLMessage.DISPLAY_MSG));
            } else {
                clientContext.displayReply("Language info:");
                for (REPLMessage message : replies) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append(message.get(REPLMessage.INFO_KEY));
                    sb.append(": ");
                    sb.append(message.get(REPLMessage.INFO_VALUE));
                    clientContext.displayInfo(sb.toString());
                }
            }
        }
    };

    private final REPLCommand infoSetCommand = new REPLLocalCommand("set", null, "info about settings") {

        final String[] help = {"info sets:  list local options that can be set"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public void execute(String[] args) {

            clientContext.displayReply("Settable options:");

            for (String optionName : optionNames) {
                final LocalOption localOption = localOptions.get(optionName);
                if (localOption == null) {
                    clientContext.displayInfo(localOption + ": Error, no implementation for option");
                } else {
                    clientContext.displayInfo(optionName + "=" + localOption.getValue() + ": " + localOption.getDescription());
                }
            }
        }
    };

    private final REPLCommand listCommand = new REPLLocalCommand("list", null, "Display selected source file") {

        final String[] help = {"list:  list <listsize> lines of selected file (see option \"listsize\")", "list all: list all lines", "list <n>: list <listsize> lines centered around line <n>"};

        private Source lastListedSource = null;

        private int nextLineToList = 1;

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public void execute(String[] args) {
            final Source source = clientContext.getSelectedSource();
            if (source == null) {
                clientContext.displayFailReply("No selected file");
                reset();
                return;
            }
            final int listSize = listSizeOption.getInt();

            if (args.length == 1) {
                if (!source.equals(lastListedSource)) {
                    reset();
                } else if (nextLineToList > source.getLineCount()) {
                    reset();
                }
                final int lastListedLine = printLines(source, nextLineToList, listSize);
                lastListedSource = source;
                nextLineToList = lastListedLine > source.getLineCount() ? 1 : lastListedLine + 1;
            } else if (args.length == 2) {
                reset();
                if (args[1].equals("all")) {
                    printLines(source, 1, source.getLineCount());
                } else {
                    try {
                        final int line = Integer.parseInt(args[1]);
                        final int halfListSize = listSize / 2;
                        final int start = Math.max(1, line - halfListSize);
                        final int count = Math.min(source.getLineCount() + 1 - start, listSize);
                        printLines(source, start, count);
                    } catch (NumberFormatException e) {
                        clientContext.displayFailReply("\"" + args[1] + "\" not recognized");
                    }

                }
            }
        }

        private int printLines(Source printSource, int start, int listSize) {

            clientContext.displayReply(printSource.getShortName() + ":");
            final int lastLineNumber = Math.min(start + listSize - 1, printSource.getLineCount());
            for (int line = start; line <= lastLineNumber; line++) {
                writer.format(CODE_LINE_FORMAT, line, printSource.getCode(line));
            }
            return lastLineNumber;
        }

        /**
         * Forget where we were in a sequence of list commands with no arguments
         */
        private void reset() {
            lastListedSource = clientContext.getSelectedSource();
            nextLineToList = 1;
        }
    };

    private final REPLCommand quitCommand = new REPLRemoteCommand("quit", "q", "Quit execution and REPL") {

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, REPLMessage.QUIT);
            return request;
        }

    };

    private final REPLCommand setCommand = new REPLLocalCommand("set", null, "set <option>=<value>") {

        @Override
        public String[] getHelp() {
            return new String[]{"Sets an option \"set <option-name>=<value>\";  see also \"info set\""};
        }

        @Override
        public void execute(String[] args) {
            REPLMessage request = null;
            if (args.length == 1) {
                clientContext.displayFailReply("No option specified, try \"help set\"");
            } else if (args.length == 2) {
                String[] split = new String[0];
                try {
                    split = args[1].split("=");
                } catch (Exception ex) {
                }
                if (split.length == 0) {
                    clientContext.displayFailReply("Arguments not understood, try \"help set\"");
                } else if (split.length == 1) {
                    clientContext.displayFailReply("No option value specified, try \"help set\"");
                } else if (split.length > 2) {
                    clientContext.displayFailReply("Arguments not understood, try \"help set\"");
                } else {
                    final String optionName = split[0];
                    final String newValue = split[1];
                    final LocalOption localOption = localOptions.get(optionName);
                    if (localOption != null) {
                        if (!localOption.setValue(newValue)) {
                            clientContext.displayFailReply("Invalid option value \"" + newValue + "\"");
                        }
                        clientContext.displayInfo(localOption.name + " = " + localOption.getValue());
                    } else {
                        request = new REPLMessage();
                        request.put(REPLMessage.OP, REPLMessage.SET);
                        request.put(REPLMessage.OPTION, optionName);
                        request.put(REPLMessage.VALUE, newValue);
                    }
                }
            } else {
                clientContext.displayFailReply("Arguments not understood, try \"help set\"");
            }
        }
    };

    private final REPLIndirectCommand truffleCommand = new REPLIndirectCommand(REPLMessage.TRUFFLE, "t", "Access to Truffle internals") {

        // "Truffle" commands
        private final Map<String, REPLCommand> truffleCommandMap = new HashMap<>();
        private final Collection<String> truffleCommandNames = new TreeSet<>();

        @Override
        public String[] getHelp() {
            final ArrayList<String> lines = new ArrayList<>();
            for (String truffleCommandName : truffleCommandNames) {
                final REPLCommand cmd = truffleCommandMap.get(truffleCommandName);
                if (cmd == null) {
                    lines.add("\"" + REPLMessage.TRUFFLE + " " + truffleCommandName + "\" not implemented");
                } else {
                    for (String line : cmd.getHelp()) {
                        lines.add(line);
                    }
                }
            }
            return lines.toArray(new String[0]);
        }

        @Override
        void addCommand(REPLCommand replCommand) {
            final String commandName = replCommand.getCommand();
            final String abbreviation = replCommand.getAbbreviation();

            truffleCommandNames.add(commandName);
            truffleCommandMap.put(commandName, replCommand);
            if (abbreviation != null) {
                truffleCommandMap.put(abbreviation, replCommand);
            }
        }

        @Override
        REPLCommand getCommand(String[] args) {
            if (args.length == 1) {
                clientContext.displayFailReply("truffle request not specified; try \"help truffle\"");
                return null;
            }
            final String topic = args[1];
            REPLCommand command = truffleCommandMap.get(topic);
            if (command == null) {
                clientContext.displayFailReply("truffle request \"" + topic + "\" not recognized");
                return null;
            }
            return command;
        }
    };

    private final REPLRemoteCommand truffleASTCommand = new REPLRemoteCommand("ast", null, "print the AST that contains the current node") {

        final String[] help = {"truffle ast:  print the AST subtree that contains current node (see \"set treedepth\")",
                        "truffle ast <n>:  print the AST subtree that contains current node to a maximum depth of <n>"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (clientContext.level() == 0) {
                context.displayFailReply("no active execution");
                return null;
            }

            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, REPLMessage.TRUFFLE);
            request.put(REPLMessage.TOPIC, REPLMessage.AST);

            int astDepth = astDepthOption.getInt();
            if (args.length > 2) {
                final String depthText = args[2];
                try {
                    astDepth = Integer.parseInt(depthText);
                } catch (NumberFormatException e) {
                }
            }
            request.put(REPLMessage.AST_DEPTH, Integer.toString(astDepth));
            return request;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            if (replies[0].get(REPLMessage.STATUS).equals(REPLMessage.FAILED)) {
                clientContext.displayFailReply(replies[0].get(REPLMessage.DISPLAY_MSG));
            } else {
                clientContext.displayReply("AST containing the Current Node:");
                for (REPLMessage message : replies) {
                    for (String line : message.get(REPLMessage.DISPLAY_MSG).split("\n")) {
                        clientContext.displayInfo(line);
                    }
                }
            }
        }
    };

    private void displayTruffleAST(String text) {
        clientContext.displayReply("AST containing Current Node:");
        for (String line : text.split("\n")) {
            clientContext.displayInfo(line);
        }
    }

    private final REPLRemoteCommand truffleNodeCommand = new REPLRemoteCommand("node", null, "describe current AST node") {

        final String[] help = {"truffle node:  describe the AST node at the current execution context"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (clientContext.level() == 0) {
                context.displayFailReply("no active execution");
                return null;
            }
            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, REPLMessage.TRUFFLE_NODE);
            return request;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            if (replies[0].get(REPLMessage.STATUS).equals(REPLMessage.FAILED)) {
                clientContext.displayFailReply(replies[0].get(REPLMessage.DISPLAY_MSG));
            } else {
                displayTruffleNode(replies[0].get(REPLMessage.DISPLAY_MSG));
            }
        }
    };

    private void displayTruffleNode(String nodeString) {
        clientContext.displayReply("Current Node: " + nodeString);
    }

    private final REPLRemoteCommand truffleSubtreeCommand = new REPLRemoteCommand("subtree", "sub", "print the AST subtree rooted at the current node") {

        final String[] help = {"truffle sub:  print the AST subtree at the current node (see \"set treedepth\")", "truffle sub <n>:  print the AST subtree at the current node to maximum depth <n>",
                        "truffle subtree:   print the AST subtree at the current node (see \"set treedepth\")", "truffle sub <n>:  print the AST subtree at the current node to maximum depth <n>"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (clientContext.level() == 0) {
                context.displayFailReply("no active execution");
                return null;
            }

            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, REPLMessage.TRUFFLE);
            request.put(REPLMessage.TOPIC, REPLMessage.SUBTREE);

            int astDepth = astDepthOption.getInt();
            if (args.length > 2) {
                final String depthText = args[2];
                try {
                    astDepth = Integer.parseInt(depthText);
                } catch (NumberFormatException e) {
                }
            }
            request.put(REPLMessage.AST_DEPTH, Integer.toString(astDepth));
            return request;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            if (replies[0].get(REPLMessage.STATUS).equals(REPLMessage.FAILED)) {
                clientContext.displayFailReply(replies[0].get(REPLMessage.DISPLAY_MSG));
            } else {
                clientContext.displayReply("AST subtree at Current Node:");
                for (REPLMessage message : replies) {
                    for (String line : message.get(REPLMessage.DISPLAY_MSG).split("\n")) {
                        clientContext.displayInfo(line);
                    }
                }
            }
        }
    };

    private void displayTruffleSubtree(String text) {
        clientContext.displayReply("AST subtree at Current Node:");
        for (String line : text.split("\n")) {
            clientContext.displayInfo(line);
        }
    }

    private final REPLCommand whereCommand = new REPLLocalCommand("where", null, "Show code around current break location") {

        @Override
        public void execute(String[] args) {
            clientContext.displayWhere();
        }
    };

    private abstract static class LocalOption {
        private final String name;
        private final String description;

        protected LocalOption(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public abstract boolean setValue(String newValue);

        public boolean getBool() {
            assert false;
            return false;
        }

        public int getInt() {
            assert false;
            return 0;
        }

        public abstract String getValue();
    }

    private static final class BooleanOption extends LocalOption {

        private Boolean value;

        public BooleanOption(boolean value, String name, String description) {
            super(name, description);
            this.value = value;
        }

        @Override
        public boolean setValue(String newValue) {
            final Boolean valueOf = Boolean.valueOf(newValue);
            if (valueOf == null) {
                return false;
            }
            value = valueOf;
            return true;
        }

        @Override
        public boolean getBool() {
            return value;
        }

        @Override
        public String getValue() {
            return value.toString();
        }
    }

    private static final class IntegerOption extends LocalOption {

        private Integer value;

        public IntegerOption(int value, String name, String description) {
            super(name, description);
            this.value = value;
        }

        @Override
        public boolean setValue(String newValue) {
            Integer valueOf;
            try {
                valueOf = Integer.valueOf(newValue);
            } catch (NumberFormatException e) {
                return false;
            }
            value = valueOf;
            return true;
        }

        @Override
        public int getInt() {
            return value;
        }

        @Override
        public String getValue() {
            return value.toString();
        }

    }

}
