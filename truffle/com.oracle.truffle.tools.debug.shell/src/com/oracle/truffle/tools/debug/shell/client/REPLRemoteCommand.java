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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tools.debug.shell.REPLMessage;

// TODO (mlvdv)  write a real command line parser
public abstract class REPLRemoteCommand extends REPLCommand {

    public REPLRemoteCommand(String command, String abbreviation, String description) {
        super(command, abbreviation, description);
    }

    protected abstract REPLMessage createRequest(REPLClientContext context, String[] args);

    void processReply(REPLClientContext context, REPLMessage[] replies) {
        REPLMessage firstReply = replies[0];

        if (firstReply.get(REPLMessage.STATUS).equals(REPLMessage.FAILED)) {
            final String result = firstReply.get(REPLMessage.DISPLAY_MSG);
            context.displayFailReply(result != null ? result : firstReply.toString());
        } else {
            final String result = firstReply.get(REPLMessage.DISPLAY_MSG);
            context.displayReply(result != null ? result : firstReply.toString());
        }

        for (int i = 1; i < replies.length; i++) {
            REPLMessage reply = replies[i];
            final String result = reply.get(REPLMessage.DISPLAY_MSG);
            context.displayInfo(result != null ? result : reply.toString());
        }
    }

    public static final REPLRemoteCommand BREAK_AT_LINE_CMD = new REPLRemoteCommand("break-at-line", "break", "Set a breakpoint") {

        private final String[] help = {"break <n> [ignore=<n>] : set breakpoint at line <n> in current file", "break <filename>:<n> [ignore=<n>] : set breakpoint at line <n> in <filename>",
                        " optionally ignore first <n> hits (default 0)"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            try {
                final REPLineLocation lineLocation = REPLineLocation.parse(context, args);
                final REPLMessage requestMessage = lineLocation.createMessage(REPLMessage.BREAK_AT_LINE);
                int ignoreCount = 0;
                if (args.length > 2) {
                    final String ignoreText = args[2];
                    if (ignoreText.equals("ignore")) {
                        throw new IllegalArgumentException("No ignore count specified");
                    }
                    final String[] split = ignoreText.split("=");
                    if (split.length == 2 && split[0].equals("ignore")) {
                        try {
                            ignoreCount = Integer.parseInt(split[1]);
                            if (ignoreCount < 0) {
                                throw new IllegalArgumentException("Illegal ignore count: " + split[1]);
                            }
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("No ignore count specified");
                        }
                    } else {
                        throw new IllegalArgumentException("Unrecognized argument \"" + ignoreText + "\"");
                    }
                }
                requestMessage.put(REPLMessage.BREAKPOINT_IGNORE_COUNT, Integer.toString(ignoreCount));
                return requestMessage;
            } catch (IllegalArgumentException ex) {
                context.displayFailReply(ex.getMessage());
            }
            return null;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            REPLMessage firstReply = replies[0];

            if (firstReply.get(REPLMessage.STATUS).equals(REPLMessage.SUCCEEDED)) {
                final String number = firstReply.get(REPLMessage.BREAKPOINT_ID);
                final String fileName = firstReply.get(REPLMessage.SOURCE_NAME);
                final String lineNumber = firstReply.get(REPLMessage.LINE_NUMBER);
                firstReply.put(REPLMessage.DISPLAY_MSG, "breakpoint " + number + " set at " + fileName + ":" + lineNumber);
            }
            super.processReply(context, replies);
        }
    };

    public static final REPLRemoteCommand BREAK_AT_LINE_ONCE_CMD = new REPLRemoteCommand("break-at-line-once", "break1", "Set a one-shot breakpoint") {

        private final String[] help = {"break <n>: set one-shot breakpoint at line <n> in current file", "break <filename>:<n>: set one-shot breakpoint at line <n> in current file"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            try {
                return REPLineLocation.parse(context, args).createMessage(REPLMessage.BREAK_AT_LINE_ONCE);
            } catch (IllegalArgumentException ex) {
                context.displayFailReply(ex.getMessage());
            }
            return null;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            REPLMessage firstReply = replies[0];

            if (firstReply.get(REPLMessage.STATUS).equals(REPLMessage.SUCCEEDED)) {
                final String fileName = firstReply.get(REPLMessage.SOURCE_NAME);
                final String lineNumber = firstReply.get(REPLMessage.LINE_NUMBER);
                firstReply.put(REPLMessage.DISPLAY_MSG, "one-shot breakpoint set at " + fileName + ":" + lineNumber);
            }
            super.processReply(context, replies);
        }
    };

    public static final REPLRemoteCommand CALL_CMD = new REPLRemoteCommand("call", null, "call a method/function") {

        private final String[] help = {"call <name> <args>: calls a function by name"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (args.length == 1) {
                context.displayFailReply("name to call not speciified");
                return null;
            }
            final int maxArgs = REPLMessage.ARG_NAMES.length;
            if (args.length > maxArgs + 2) {
                context.displayFailReply("too many call arguments; no more than " + maxArgs + " supported");
                return null;
            }
            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, REPLMessage.CALL);
            request.put(REPLMessage.CALL_NAME, args[1]);
            for (int argIn = 2, argOut = 0; argIn < args.length; argIn++, argOut++) {
                request.put(REPLMessage.ARG_NAMES[argOut], args[argIn]);
            }
            return request;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            REPLMessage firstReply = replies[0];
            if (firstReply.get(REPLMessage.STATUS).equals(REPLMessage.FAILED)) {
                final String result = firstReply.get(REPLMessage.DISPLAY_MSG);
                context.displayFailReply(result != null ? result : firstReply.toString());
            } else {
                context.displayReply(firstReply.get(REPLMessage.VALUE));
            }
        }
    };

    public static final REPLRemoteCommand CALL_STEP_INTO_CMD = new REPLRemoteCommand("call-step-into", "calls", "Call a method/function and step into") {

        private final String[] help = {"call <name> <args>: calls function by name and step into"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            final REPLMessage request = CALL_CMD.createRequest(context, args);
            if (request != null) {
                request.put(REPLMessage.STEP_INTO, REPLMessage.TRUE);
            }
            return request;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            CALL_CMD.processReply(context, replies);
        }
    };

    public static final REPLRemoteCommand CLEAR_BREAK_CMD = new REPLRemoteCommand("clear", null, "Clear a breakpoint") {

        private final String[] help = {"clear <n>: clear breakpoint number <n>"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (args.length == 1) {
                context.displayFailReply("breakpoint number not speciified:  \"break <n>\"");
            } else if (args.length > 2) {
                context.displayFailReply("breakpoint number not understood:  \"break <n>\"");
            } else {
                try {
                    final int breakpointNumber = Integer.parseInt(args[1]);
                    final REPLMessage request = new REPLMessage();
                    request.put(REPLMessage.OP, REPLMessage.CLEAR_BREAK);
                    request.put(REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointNumber));
                    return request;
                } catch (IllegalArgumentException ex) {
                    context.displayFailReply(ex.getMessage());
                }
            }
            return null;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            REPLMessage firstReply = replies[0];

            if (firstReply.get(REPLMessage.STATUS).equals(REPLMessage.SUCCEEDED)) {
                final int breakpointNumber = firstReply.getIntValue(REPLMessage.BREAKPOINT_ID);
                firstReply.put(REPLMessage.DISPLAY_MSG, "breakpoint " + breakpointNumber + " cleared");
            }
            super.processReply(context, replies);
        }
    };

    public static final REPLRemoteCommand CONDITION_BREAK_CMD = new REPLRemoteCommand("cond", null, "Set new condition on a breakpoint") {

        private final String[] help = {"cond <n> [expr]: sets new condition on breakpoint number <n>; make unconditional if no [expr]"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (args.length == 1) {
                context.displayFailReply("breakpoint number not speciified:  \"cond <n>\"");
            } else {
                try {
                    final int breakpointNumber = Integer.parseInt(args[1]);
                    final REPLMessage request = new REPLMessage();
                    request.put(REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointNumber));
                    if (args.length == 2) {
                        request.put(REPLMessage.OP, REPLMessage.UNSET_BREAK_CONDITION);
                    } else {
                        final StringBuilder exprBuilder = new StringBuilder();
                        for (int i = 2; i < args.length; i++) {
                            exprBuilder.append(args[i]).append(" ");
                        }
                        request.put(REPLMessage.BREAKPOINT_CONDITION, exprBuilder.toString().trim());
                        request.put(REPLMessage.OP, REPLMessage.SET_BREAK_CONDITION);
                    }
                    return request;
                } catch (IllegalArgumentException ex) {
                    context.displayFailReply(ex.getMessage());
                }
            }
            return null;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            REPLMessage firstReply = replies[0];
            if (firstReply.get(REPLMessage.STATUS).equals(REPLMessage.SUCCEEDED)) {
                final int breakpointNumber = firstReply.getIntValue(REPLMessage.BREAKPOINT_ID);
                final String condition = firstReply.get(REPLMessage.BREAKPOINT_CONDITION);
                if (condition == null) {
                    firstReply.put(REPLMessage.DISPLAY_MSG, "breakpoint " + breakpointNumber + " condition cleared");
                } else {
                    firstReply.put(REPLMessage.DISPLAY_MSG, "breakpoint " + breakpointNumber + " condition=\"" + condition + "\"");
                }
            }
            super.processReply(context, replies);
        }
    };

    public static final REPLRemoteCommand CONTINUE_CMD = new REPLRemoteCommand("continue", "c", "Continue execution") {

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (context.level() == 0) {
                context.displayFailReply("no active execution");
                return null;
            }
            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, REPLMessage.CONTINUE);
            return request;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {

            throw new REPLContinueException();

        }
    };

    public static final REPLRemoteCommand DELETE_CMD = new REPLRemoteCommand("delete", "d", "Delete all breakpoints") {

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, REPLMessage.DELETE_BREAK);
            return request;
        }
    };

    public static final REPLRemoteCommand DISABLE_CMD = new REPLRemoteCommand("disable", null, "Disable a breakpoint") {

        private final String[] help = {"disable <n>: disable breakpoint number <n>"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (args.length == 1) {
                context.displayFailReply("breakpoint number not speciified:  \"disable <n>\"");
            } else if (args.length > 2) {
                context.displayFailReply("breakpoint number not understood:  \"disable <n>\"");
            } else {
                try {
                    final int breakpointNumber = Integer.parseInt(args[1]);
                    final REPLMessage request = new REPLMessage();
                    request.put(REPLMessage.OP, REPLMessage.DISABLE_BREAK);
                    request.put(REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointNumber));
                    return request;
                } catch (IllegalArgumentException ex) {
                    context.displayFailReply(ex.getMessage());
                }
            }
            return null;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            REPLMessage firstReply = replies[0];
            if (firstReply.get(REPLMessage.STATUS).equals(REPLMessage.SUCCEEDED)) {
                final int breakpointNumber = firstReply.getIntValue(REPLMessage.BREAKPOINT_ID);
                firstReply.put(REPLMessage.DISPLAY_MSG, "breakpoint " + breakpointNumber + " disabled");
            }
            super.processReply(context, replies);
        }
    };

    public static final REPLRemoteCommand DOWN_CMD = new REPLRemoteCommand("down", null, "Move down a stack frame") {

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (context.level() == 0) {
                context.displayFailReply("no active execution");
                return null;
            }
            final List<REPLFrame> frames = context.frames();
            final int newFrameSelection = context.getSelectedFrameNumber() + 1;
            if (newFrameSelection > frames.size() - 1) {
                context.displayFailReply("at bottom of stack");
                return null;
            }
            context.selectFrameNumber(newFrameSelection);
            return FRAME_CMD.createRequest(context, Arrays.copyOfRange(args, 0, 0));
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            REPLMessage firstReply = replies[0];

            if (firstReply.get(REPLMessage.STATUS).equals(REPLMessage.FAILED)) {
                final String result = firstReply.get(REPLMessage.DISPLAY_MSG);
                context.displayFailReply(result != null ? result : firstReply.toString());
            } else {
                context.displayStack();
            }
        }
    };

    public static final REPLRemoteCommand ENABLE_CMD = new REPLRemoteCommand("enable", null, "Enable a breakpoint") {

        private final String[] help = {"enable <n>: enable breakpoint number <n>"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (args.length == 1) {
                context.displayFailReply("breakpoint number not speciified:  \"enable <n>\"");
            } else if (args.length > 2) {
                context.displayFailReply("breakpoint number not understood:  \"enable <n>\"");
            } else {
                try {
                    final int breakpointNumber = Integer.parseInt(args[1]);
                    final REPLMessage request = new REPLMessage();
                    request.put(REPLMessage.OP, REPLMessage.ENABLE_BREAK);
                    request.put(REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointNumber));
                    return request;
                } catch (IllegalArgumentException ex) {
                    context.displayFailReply(ex.getMessage());
                }
            }
            return null;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            REPLMessage firstReply = replies[0];

            if (firstReply.get(REPLMessage.STATUS).equals(REPLMessage.SUCCEEDED)) {
                final int breakpointNumber = firstReply.getIntValue(REPLMessage.BREAKPOINT_ID);
                firstReply.put(REPLMessage.DISPLAY_MSG, "breakpoint " + breakpointNumber + " enabled");
            }
            super.processReply(context, replies);
        }
    };

    public static final REPLRemoteCommand EVAL_CMD = new REPLRemoteCommand("eval", null, "Evaluate a string, in context of the current frame if any") {

        private int evalCounter = 0;

        private final String[] help = {"eval <string>: evaluate <string> in context of the current frame if any"};

        @Override
        public String[] getHelp() {
            return help;
        }

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
                    if (context.level() > 0) {
                        // Specify a requested execution context, if one exists; otherwise top level
                        request.put(REPLMessage.FRAME_NUMBER, Integer.toString(context.getSelectedFrameNumber()));
                    }
                    return request;
                }
            }
            return null;
        }
    };

    public static final REPLRemoteCommand EVAL_STEP_INTO_CMD = new REPLRemoteCommand("eval-step-into", "evals", "Evaluate and step into a string, in context of the current frame if any") {

        private final String[] help = {"eval-step-into <string>: evaluate <string> in context of the current frame if any, and step into"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            final REPLMessage request = EVAL_CMD.createRequest(context, args);
            if (request != null) {
                request.put(REPLMessage.STEP_INTO, REPLMessage.TRUE);
            }
            return request;
        }
    };

    public static final REPLRemoteCommand FRAME_CMD = new REPLRemoteCommand("frame", null, "Display a stack frame") {

        private final String[] help = {"frame : display currently selected frame", "frame <n> : display frame <n>"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (context.level() == 0) {
                context.displayFailReply("no active execution");
                return null;
            }
            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, REPLMessage.FRAME);

            int frameNumber = context.getSelectedFrameNumber();
            if (args.length > 1) {
                if (args.length == 2) {
                    try {
                        frameNumber = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Unrecognized argument \"" + args[1] + "\"");
                    }
                } else {
                    throw new IllegalArgumentException("Unrecognized argument \"" + args[2] + "\"");
                }
            }
            request.put(REPLMessage.FRAME_NUMBER, Integer.toString(frameNumber));
            return request;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            if (replies[0].get(REPLMessage.STATUS).equals(REPLMessage.FAILED)) {
                context.displayFailReply(replies[0].get(REPLMessage.DISPLAY_MSG));
            } else {
                Integer frameNumber = replies[0].getIntValue(REPLMessage.FRAME_NUMBER);
                context.selectFrameNumber(frameNumber);
                context.displayReply("Frame " + frameNumber + ":");
                for (REPLMessage message : replies) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("#" + message.get(REPLMessage.SLOT_INDEX) + ": ");
                    sb.append(message.get(REPLMessage.SLOT_ID) + " = ");
                    sb.append(message.get(REPLMessage.SLOT_VALUE));
                    context.displayInfo(sb.toString());
                }
            }
        }
    };

    public static final REPLRemoteCommand KILL_CMD = new REPLRemoteCommand("kill", null, "Stop program execution") {

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (context.level() == 0) {
                context.displayFailReply("no active execution");
                return null;
            }
            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, "kill");
            return request;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            if (replies[0].get(REPLMessage.STATUS).equals(REPLMessage.SUCCEEDED)) {
                context.displayReply(replies[0].get(REPLMessage.DISPLAY_MSG));
            } else {
                context.displayFailReply(replies[0].get(REPLMessage.DISPLAY_MSG));
            }
        }
    };

    public static final REPLRemoteCommand LOAD_CMD = new REPLRemoteCommand("load", null, "Load source") {

        private final String[] help = new String[]{"load : load currently selected file source", "load <file name> : load file <file name>"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            Source runSource = null;
            if (args.length == 1) {
                runSource = context.getSelectedSource();
                if (runSource == null) {
                    context.displayFailReply("No file selected");
                    return null;
                }
            } else {
                try {
                    runSource = Source.fromFileName(args[1]);
                } catch (IOException e) {
                    context.displayFailReply("Can't find file: " + args[1]);
                    return null;
                }
            }
            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, REPLMessage.LOAD_SOURCE);
            request.put(REPLMessage.SOURCE_NAME, runSource.getPath());
            return request;
        }
    };

    public static final REPLRemoteCommand LOAD_STEP_INTO_CMD = new REPLRemoteCommand("load-step-into", "loads", "Load source and step in") {

        private final String[] help = new String[]{"load : load currently selected file source and step in", "load <file name> : load file <file name> and step in"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            final REPLMessage request = LOAD_CMD.createRequest(context, args);
            if (request != null) {
                request.put(REPLMessage.STEP_INTO, REPLMessage.TRUE);
            }
            return request;
        }
    };

    public static final REPLRemoteCommand SET_LANG_CMD = new REPLRemoteCommand("language", "lang", "Set current language") {

        private final String[] help = new String[]{"lang <language short name>:  set default language, \"info lang\" displays choices"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {

            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, REPLMessage.SET_LANGUAGE);
            if (args.length > 1) {
                request.put(REPLMessage.LANG_NAME, args[1]);
            }
            return request;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            context.updatePrompt();
            super.processReply(context, replies);
        }

    };

    public static final REPLRemoteCommand STEP_INTO_CMD = new REPLRemoteCommand("step", "s", "(StepInto) next statement, going into functions.") {

        private final String[] help = new String[]{"step:  (StepInto) next statement (into calls)", "step <n>: (StepInto) nth next statement (into calls)"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (context.level() == 0) {
                context.displayFailReply("no active execution");
                return null;
            }
            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, REPLMessage.STEP_INTO);

            if (args.length >= 2) {
                final String nText = args[1];
                try {
                    final int nSteps = Integer.parseInt(nText);
                    if (nSteps > 0) {
                        request.put(REPLMessage.REPEAT, Integer.toString(nSteps));
                    } else {
                        return null;
                    }
                } catch (NumberFormatException e) {
                    context.displayFailReply("Count \"" + nText + "\" not recognized");
                    return null;
                }
            }
            return request;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {

            throw new REPLContinueException();
        }
    };

    public static final REPLRemoteCommand STEP_OUT_CMD = new REPLRemoteCommand("finish", null, "(StepOut) return from function") {

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (context.level() == 0) {
                context.displayFailReply("no active execution");
                return null;
            }
            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, REPLMessage.STEP_OUT);
            return request;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {

            throw new REPLContinueException();
        }
    };

    public static final REPLRemoteCommand STEP_OVER_CMD = new REPLRemoteCommand("next", "n", "(StepOver) execute next line of code, not into functions.") {

        private final String[] help = new String[]{"next:  (StepOver) execute next line of code, not into functions.",
                        "next <n>: (StepOver) execute to nth next statement (not counting into functions)"};

        @Override
        public String[] getHelp() {
            return help;
        }

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (context.level() == 0) {
                context.displayFailReply("no active execution");
                return null;
            }
            final REPLMessage request = new REPLMessage();
            request.put(REPLMessage.OP, REPLMessage.STEP_OVER);

            if (args.length >= 2) {
                final String nText = args[1];
                try {
                    final int nSteps = Integer.parseInt(nText);
                    if (nSteps > 0) {
                        request.put(REPLMessage.REPEAT, Integer.toString(nSteps));
                    } else {
                        return null;
                    }
                } catch (NumberFormatException e) {
                    context.displayFailReply("Next count \"" + nText + "\" not recognized");
                    return null;
                }
            }
            return request;
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {

            throw new REPLContinueException();
        }
    };

    public static final REPLRemoteCommand UP_CMD = new REPLRemoteCommand("up", null, "Move up a stack frame") {

        @Override
        public REPLMessage createRequest(REPLClientContext context, String[] args) {
            if (context.level() == 0) {
                context.displayFailReply("no active execution");
                return null;
            }
            final int newFrameSelection = context.getSelectedFrameNumber() - 1;
            if (newFrameSelection < 0) {
                context.displayFailReply("at top of stack");
                return null;
            }
            context.selectFrameNumber(newFrameSelection);
            return FRAME_CMD.createRequest(context, Arrays.copyOfRange(args, 0, 0));
        }

        @Override
        void processReply(REPLClientContext context, REPLMessage[] replies) {
            REPLMessage firstReply = replies[0];

            if (firstReply.get(REPLMessage.STATUS).equals(REPLMessage.FAILED)) {
                final String result = firstReply.get(REPLMessage.DISPLAY_MSG);
                context.displayFailReply(result != null ? result : firstReply.toString());
            } else {
                context.displayStack();
            }
        }
    };

}
