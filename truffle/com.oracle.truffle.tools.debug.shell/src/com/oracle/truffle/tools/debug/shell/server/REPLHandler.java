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
import java.util.List;

import com.oracle.truffle.api.KillException;
import com.oracle.truffle.api.QuitException;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.PolyglotEngine.Language;
import com.oracle.truffle.tools.debug.shell.REPLMessage;
import com.oracle.truffle.tools.debug.shell.server.InstrumentationUtils.ASTPrinter;
import com.oracle.truffle.tools.debug.shell.server.InstrumentationUtils.LocationPrinter;
import com.oracle.truffle.tools.debug.shell.server.REPLServer.BreakpointInfo;
import com.oracle.truffle.tools.debug.shell.server.REPLServer.Context;
import com.oracle.truffle.tools.debug.shell.server.REPLServer.Visualizer;

/**
 * Server-side REPL implementation of an {@linkplain REPLMessage "op"}.
 * <p>
 * The language-agnostic handlers are implemented here.
 */
public abstract class REPLHandler {

    // TODO (mlvdv) add support for setting/using ignore count
    private static final int DEFAULT_IGNORE_COUNT = 0;

    private final String op;

    protected REPLHandler(String op) {
        this.op = op;
    }

    /**
     * Gets the "op" implemented by this handler.
     */
    final String getOp() {
        return op;
    }

    /**
     * Passes a request to this handler.
     */
    abstract REPLMessage[] receive(REPLMessage request, REPLServer replServer);

    /**
     * Creates skeleton for a reply message that identifies the operation currently being handled.
     */
    REPLMessage createReply() {
        return new REPLMessage(REPLMessage.OP, op);
    }

    /**
     * Completes a reply, reporting and explaining successful handling.
     */
    protected static final REPLMessage[] finishReplySucceeded(REPLMessage reply, String explanation) {
        reply.put(REPLMessage.STATUS, REPLMessage.SUCCEEDED);
        reply.put(REPLMessage.DISPLAY_MSG, explanation);
        final REPLMessage[] replies = new REPLMessage[]{reply};
        return replies;
    }

    /**
     * Completes a reply, reporting and explaining failed handling.
     */
    protected static final REPLMessage[] finishReplyFailed(REPLMessage reply, String explanation) {
        reply.put(REPLMessage.STATUS, REPLMessage.FAILED);
        reply.put(REPLMessage.DISPLAY_MSG, explanation);
        final REPLMessage[] replies = new REPLMessage[]{reply};
        return replies;
    }

    protected static final REPLMessage[] finishReplyFailed(REPLMessage reply, Exception ex) {
        reply.put(REPLMessage.STATUS, REPLMessage.FAILED);
        String message = ex.getMessage();
        reply.put(REPLMessage.DISPLAY_MSG, message == null ? ex.getClass().getSimpleName() : message);
        final REPLMessage[] replies = new REPLMessage[]{reply};
        return replies;
    }

    protected static final REPLMessage createBreakpointInfoMessage(BreakpointInfo info) {
        final REPLMessage infoMessage = new REPLMessage(REPLMessage.OP, REPLMessage.BREAKPOINT_INFO);
        infoMessage.put(REPLMessage.BREAKPOINT_ID, Integer.toString(info.getID()));
        infoMessage.put(REPLMessage.BREAKPOINT_STATE, info.describeState());
        infoMessage.put(REPLMessage.BREAKPOINT_HIT_COUNT, Integer.toString(info.getHitCount()));
        infoMessage.put(REPLMessage.BREAKPOINT_IGNORE_COUNT, Integer.toString(info.getIgnoreCount()));
        infoMessage.put(REPLMessage.INFO_VALUE, info.describeLocation());
        if (info.getCondition() != null) {
            infoMessage.put(REPLMessage.BREAKPOINT_CONDITION, info.getCondition());
        }
        infoMessage.put(REPLMessage.STATUS, REPLMessage.SUCCEEDED);
        return infoMessage;
    }

    protected static final REPLMessage createFrameInfoMessage(final REPLServer replServer, int number, Node node) {
        final REPLMessage infoMessage = new REPLMessage(REPLMessage.OP, REPLMessage.FRAME_INFO);
        infoMessage.put(REPLMessage.FRAME_NUMBER, Integer.toString(number));
        infoMessage.put(REPLMessage.SOURCE_LOCATION, replServer.getLocationPrinter().displaySourceLocation(node));
        infoMessage.put(REPLMessage.METHOD_NAME, replServer.getCurrentContext().getVisualizer().displayMethodName(node));

        if (node != null) {
            SourceSection section = node.getSourceSection();
            if (section == null) {
                section = node.getEncapsulatingSourceSection();
            }
            if (section != null && section.getSource() != null) {
                infoMessage.put(REPLMessage.FILE_PATH, section.getSource().getPath());
                infoMessage.put(REPLMessage.LINE_NUMBER, Integer.toString(section.getStartLine()));
                infoMessage.put(REPLMessage.SOURCE_LINE_TEXT, section.getSource().getCode(section.getStartLine()));
            }
        }
        infoMessage.put(REPLMessage.STATUS, REPLMessage.SUCCEEDED);
        return infoMessage;
    }

    public static final REPLHandler BACKTRACE_HANDLER = new REPLHandler(REPLMessage.BACKTRACE) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final Visualizer visualizer = replServer.getCurrentContext().getVisualizer();
            final ArrayList<REPLMessage> replies = new ArrayList<>();
            final Context currentContext = replServer.getCurrentContext();
            final List<FrameInstance> stack = currentContext.getStack();
            replies.add(btMessage(0, currentContext.getNode(), visualizer, replServer.getLocationPrinter()));
            for (int i = 1; i <= stack.size(); i++) {
                replies.add(btMessage(i, stack.get(i - 1).getCallNode(), visualizer, replServer.getLocationPrinter()));
            }
            if (replies.size() > 0) {
                return replies.toArray(new REPLMessage[0]);
            }
            return finishReplyFailed(new REPLMessage(REPLMessage.OP, REPLMessage.BACKTRACE), "No stack");
        }
    };

    private static REPLMessage btMessage(int index, Node node, Visualizer visualizer, LocationPrinter locationPrinter) {
        final REPLMessage btMessage = new REPLMessage(REPLMessage.OP, REPLMessage.BACKTRACE);
        btMessage.put(REPLMessage.FRAME_NUMBER, Integer.toString(index));
        if (node != null) {
            btMessage.put(REPLMessage.SOURCE_LOCATION, locationPrinter.displaySourceLocation(node));
            btMessage.put(REPLMessage.METHOD_NAME, visualizer.displayMethodName(node));
            SourceSection section = node.getSourceSection();
            if (section == null) {
                section = node.getEncapsulatingSourceSection();
            }
            if (section != null && section.getSource() != null) {
                btMessage.put(REPLMessage.FILE_PATH, section.getSource().getPath());
                btMessage.put(REPLMessage.LINE_NUMBER, Integer.toString(section.getStartLine()));
                btMessage.put(REPLMessage.SOURCE_LINE_TEXT, section.getSource().getCode(section.getStartLine()));
            }
            btMessage.put(REPLMessage.STATUS, REPLMessage.SUCCEEDED);
        }
        return btMessage;
    }

    public static final REPLHandler BREAK_AT_LINE_HANDLER = new REPLHandler(REPLMessage.BREAK_AT_LINE) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = createReply();
            final String path = request.get(REPLMessage.FILE_PATH);
            final String fileName = request.get(REPLMessage.SOURCE_NAME);
            final String lookupFile = (path == null || path.isEmpty()) ? fileName : path;
            Source source = null;
            try {
                source = Source.fromFileName(lookupFile, true);
            } catch (Exception ex) {
                return finishReplyFailed(reply, ex);
            }
            if (source == null) {
                return finishReplyFailed(reply, fileName + " not found");
            }
            final Integer lineNumber = request.getIntValue(REPLMessage.LINE_NUMBER);
            if (lineNumber == null) {
                return finishReplyFailed(reply, "missing line number");
            }
            Integer ignoreCount = request.getIntValue(REPLMessage.BREAKPOINT_IGNORE_COUNT);
            if (ignoreCount == null) {
                ignoreCount = 0;
            }
            BreakpointInfo breakpointInfo;
            try {
                breakpointInfo = replServer.setLineBreakpoint(DEFAULT_IGNORE_COUNT, source.createLineLocation(lineNumber), false);
            } catch (IOException ex) {
                return finishReplyFailed(reply, ex.getMessage());
            }
            reply.put(REPLMessage.SOURCE_NAME, fileName);
            reply.put(REPLMessage.FILE_PATH, source.getPath());
            reply.put(REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointInfo.getID()));
            reply.put(REPLMessage.LINE_NUMBER, Integer.toString(lineNumber));
            reply.put(REPLMessage.BREAKPOINT_IGNORE_COUNT, ignoreCount.toString());
            return finishReplySucceeded(reply, "Breakpoint set");
        }
    };

    public static final REPLHandler BREAK_AT_LINE_ONCE_HANDLER = new REPLHandler(REPLMessage.BREAK_AT_LINE_ONCE) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = createReply();
            final String path = request.get(REPLMessage.FILE_PATH);
            final String fileName = request.get(REPLMessage.SOURCE_NAME);
            final String lookupFile = (path == null || path.isEmpty()) ? fileName : path;
            Source source = null;
            try {
                source = Source.fromFileName(lookupFile, true);
            } catch (Exception ex) {
                return finishReplyFailed(reply, ex);
            }
            if (source == null) {
                return finishReplyFailed(reply, fileName + " not found");
            }
            final Integer lineNumber = request.getIntValue(REPLMessage.LINE_NUMBER);
            if (lineNumber == null) {
                return finishReplyFailed(reply, "missing line number");
            }
            BreakpointInfo breakpointInfo;
            try {
                breakpointInfo = replServer.setLineBreakpoint(DEFAULT_IGNORE_COUNT, source.createLineLocation(lineNumber), true);
            } catch (IOException ex) {
                return finishReplyFailed(reply, ex.getMessage());
            }
            reply.put(REPLMessage.SOURCE_NAME, fileName);
            reply.put(REPLMessage.FILE_PATH, source.getPath());
            reply.put(REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointInfo.getID()));
            reply.put(REPLMessage.LINE_NUMBER, Integer.toString(lineNumber));
            return finishReplySucceeded(reply, "One-shot line breakpoint set");
        }
    };

    public static final REPLHandler BREAKPOINT_INFO_HANDLER = new REPLHandler(REPLMessage.BREAKPOINT_INFO) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = createReply();
            final ArrayList<REPLMessage> infoMessages = new ArrayList<>();
            for (BreakpointInfo breakpointInfo : replServer.getBreakpoints()) {
                infoMessages.add(createBreakpointInfoMessage(breakpointInfo));
            }
            if (infoMessages.size() > 0) {
                return infoMessages.toArray(new REPLMessage[0]);
            }
            return finishReplyFailed(reply, "No breakpoints");
        }
    };

    public static final REPLHandler CALL_HANDLER = new REPLHandler(REPLMessage.CALL) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = new REPLMessage(REPLMessage.OP, REPLMessage.CALL);
            final String callName = request.get(REPLMessage.CALL_NAME);
            if (callName == null) {
                return finishReplyFailed(reply, "no name specified");
            }
            final ArrayList<String> argList = new ArrayList<>();
            for (int argCount = 0; argCount < REPLMessage.ARG_NAMES.length; argCount++) {
                final String arg = request.get(REPLMessage.ARG_NAMES[argCount]);
                if (arg == null) {
                    break;
                }
                argList.add(arg);
            }
            final boolean stepInto = REPLMessage.TRUE.equals(request.get(REPLMessage.STEP_INTO));
            try {
                final Object result = replServer.getCurrentContext().call(callName, stepInto, argList);
                reply.put(REPLMessage.VALUE, result == null ? "<void>" : result.toString());
            } catch (QuitException ex) {
                throw ex;
            } catch (KillException ex) {
                return finishReplySucceeded(reply, callName + " killed");
            } catch (Exception ex) {
                return finishReplyFailed(reply, ex);
            }
            return finishReplySucceeded(reply, callName + " returned");
        }
    };

    public static final REPLHandler CLEAR_BREAK_HANDLER = new REPLHandler(REPLMessage.CLEAR_BREAK) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = createReply();
            final Integer breakpointNumber = request.getIntValue(REPLMessage.BREAKPOINT_ID);
            if (breakpointNumber == null) {
                return finishReplyFailed(reply, "missing breakpoint number");
            }
            final BreakpointInfo breakpointInfo = replServer.findBreakpoint(breakpointNumber);
            if (breakpointInfo == null) {
                return finishReplyFailed(reply, "no breakpoint number " + breakpointNumber);
            }
            breakpointInfo.dispose();
            reply.put(REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointNumber));
            return finishReplySucceeded(reply, "Breakpoint " + breakpointNumber + " cleared");
        }
    };

    public static final REPLHandler CONTINUE_HANDLER = new REPLHandler(REPLMessage.CONTINUE) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = createReply();
            replServer.getCurrentContext().prepareContinue();
            return finishReplySucceeded(reply, "Continue mode entered");
        }
    };

    public static final REPLHandler DELETE_HANDLER = new REPLHandler(REPLMessage.DELETE_BREAK) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = createReply();
            final Collection<BreakpointInfo> breakpoints = replServer.getBreakpoints();
            if (breakpoints.isEmpty()) {
                return finishReplyFailed(reply, "no breakpoints to delete");
            }
            for (BreakpointInfo breakpointInfo : breakpoints) {
                breakpointInfo.dispose();
            }
            return finishReplySucceeded(reply, Integer.toString(breakpoints.size()) + " breakpoints deleted");
        }
    };

    public static final REPLHandler DISABLE_BREAK_HANDLER = new REPLHandler(REPLMessage.DISABLE_BREAK) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = createReply();
            Integer breakpointNumber = request.getIntValue(REPLMessage.BREAKPOINT_ID);
            if (breakpointNumber == null) {
                return finishReplyFailed(reply, "missing breakpoint number");
            }
            final BreakpointInfo breakpointInfo = replServer.findBreakpoint(breakpointNumber);
            if (breakpointInfo == null) {
                return finishReplyFailed(reply, "no breakpoint number " + breakpointNumber);
            }
            breakpointInfo.setEnabled(false);
            reply.put(REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointNumber));
            return finishReplySucceeded(reply, "Breakpoint " + breakpointNumber + " disabled");
        }
    };

    public static final REPLHandler ENABLE_BREAK_HANDLER = new REPLHandler(REPLMessage.ENABLE_BREAK) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = createReply();
            Integer breakpointNumber = request.getIntValue(REPLMessage.BREAKPOINT_ID);
            if (breakpointNumber == null) {
                return finishReplyFailed(reply, "missing breakpoint number");
            }
            final BreakpointInfo breakpointInfo = replServer.findBreakpoint(breakpointNumber);
            if (breakpointInfo == null) {
                return finishReplyFailed(reply, "no breakpoint number " + breakpointNumber);
            }
            breakpointInfo.setEnabled(true);
            reply.put(REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointNumber));
            return finishReplySucceeded(reply, "Breakpoint " + breakpointNumber + " enabled");
        }
    };
    public static final REPLHandler EVAL_HANDLER = new REPLHandler(REPLMessage.EVAL) {
        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = createReply();
            final String sourceName = request.get(REPLMessage.SOURCE_NAME);
            reply.put(REPLMessage.SOURCE_NAME, sourceName);
            final Context serverContext = replServer.getCurrentContext();
            reply.put(REPLMessage.DEBUG_LEVEL, Integer.toString(serverContext.getLevel()));

            final String source = request.get(REPLMessage.CODE);
            final Visualizer visualizer = replServer.getCurrentContext().getVisualizer();
            final Integer frameNumber = request.getIntValue(REPLMessage.FRAME_NUMBER);
            final boolean stepInto = REPLMessage.TRUE.equals(request.get(REPLMessage.STEP_INTO));
            try {
                Object returnValue = serverContext.eval(source, frameNumber, stepInto);
                return finishReplySucceeded(reply, visualizer.displayValue(returnValue, 0));
            } catch (QuitException ex) {
                throw ex;
            } catch (KillException ex) {
                return finishReplySucceeded(reply, "eval (" + sourceName + ") killed");
            } catch (Exception ex) {
                return finishReplyFailed(reply, ex);
            }
        }
    };
    public static final REPLHandler FILE_HANDLER = new REPLHandler(REPLMessage.FILE) {
        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = createReply();
            final String fileName = request.get(REPLMessage.SOURCE_NAME);
            if (fileName == null) {
                return finishReplyFailed(reply, "no file specified");
            }
            reply.put(REPLMessage.SOURCE_NAME, fileName);
            try {
                Source source = Source.fromFileName(fileName);
                if (source == null) {
                    return finishReplyFailed(reply, "file \"" + fileName + "\" not found");
                } else {
                    reply.put(REPLMessage.FILE_PATH, source.getPath());
                    reply.put(REPLMessage.CODE, source.getCode());
                    return finishReplySucceeded(reply, "file found");
                }
            } catch (IOException ex) {
                return finishReplyFailed(reply, "can't read file \"" + fileName + "\"");
            } catch (Exception ex) {
                return finishReplyFailed(reply, ex);
            }
        }
    };

    // TODO (mlvdv) deal with slot locals explicitly
    /**
     * Returns a general description of the frame, plus a textual summary of the slot values: one
     * per line.
     */
    public static final REPLHandler FRAME_HANDLER = new REPLHandler(REPLMessage.FRAME) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final Integer frameNumber = request.getIntValue(REPLMessage.FRAME_NUMBER);
            if (frameNumber == null) {
                return finishReplyFailed(createReply(), "no frame number specified");
            }
            final Context currentContext = replServer.getCurrentContext();
            final List<FrameInstance> stack = currentContext.getStack();
            if (frameNumber < 0 || frameNumber > stack.size()) {
                return finishReplyFailed(createReply(), "frame number " + frameNumber + " out of range");
            }
            final Visualizer visualizer = replServer.getCurrentContext().getVisualizer();

            MaterializedFrame frame;
            Node node;
            if (frameNumber == 0) {
                frame = currentContext.getFrame();
                node = currentContext.getNode();
            } else {
                final FrameInstance instance = stack.get(frameNumber - 1);
                frame = instance.getFrame(FrameAccess.MATERIALIZE, true).materialize();
                node = instance.getCallNode();
            }
            List<? extends FrameSlot> slots = frame.getFrameDescriptor().getSlots();
            if (slots.size() == 0) {
                final REPLMessage emptyFrameMessage = createFrameInfoMessage(replServer, frameNumber, node);
                return finishReplySucceeded(emptyFrameMessage, "empty frame");
            }
            final ArrayList<REPLMessage> replies = new ArrayList<>();

            for (FrameSlot slot : slots) {
                final REPLMessage slotMessage = createFrameInfoMessage(replServer, frameNumber, node);
                slotMessage.put(REPLMessage.SLOT_INDEX, Integer.toString(slot.getIndex()));
                slotMessage.put(REPLMessage.SLOT_ID, visualizer.displayIdentifier(slot));
                slotMessage.put(REPLMessage.SLOT_VALUE, visualizer.displayValue(frame.getValue(slot), 0));
                slotMessage.put(REPLMessage.STATUS, REPLMessage.SUCCEEDED);
                replies.add(slotMessage);
            }
            return replies.toArray(new REPLMessage[0]);
        }
    };

    public static final REPLHandler INFO_HANDLER = new REPLHandler(REPLMessage.INFO) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final String topic = request.get(REPLMessage.TOPIC);

            if (topic == null || topic.isEmpty()) {
                final REPLMessage message = new REPLMessage(REPLMessage.OP, REPLMessage.INFO);
                return finishReplyFailed(message, "No info topic specified");
            }

            switch (topic) {

                case REPLMessage.INFO_SUPPORTED_LANGUAGES:
                    final ArrayList<REPLMessage> langMessages = new ArrayList<>();

                    for (Language language : replServer.getLanguages()) {
                        final REPLMessage infoMessage = new REPLMessage(REPLMessage.OP, REPLMessage.INFO);
                        infoMessage.put(REPLMessage.TOPIC, REPLMessage.INFO_SUPPORTED_LANGUAGES);
                        infoMessage.put(REPLMessage.LANG_NAME, language.getName());
                        infoMessage.put(REPLMessage.LANG_VER, language.getVersion());
                        infoMessage.put(REPLMessage.STATUS, REPLMessage.SUCCEEDED);
                        langMessages.add(infoMessage);
                    }
                    return langMessages.toArray(new REPLMessage[0]);

                case REPLMessage.INFO_CURRENT_LANGUAGE:
                    final REPLMessage reply = new REPLMessage(REPLMessage.OP, REPLMessage.INFO);
                    reply.put(REPLMessage.TOPIC, REPLMessage.INFO_CURRENT_LANGUAGE);
                    final String languageName = replServer.getCurrentContext().getLanguageName();
                    reply.put(REPLMessage.LANG_NAME, languageName);
                    return finishReplySucceeded(reply, languageName);

                case REPLMessage.WELCOME_MESSAGE:
                    final REPLMessage infoMessage = new REPLMessage(REPLMessage.OP, REPLMessage.INFO);
                    infoMessage.put(REPLMessage.TOPIC, REPLMessage.WELCOME_MESSAGE);
                    infoMessage.put(REPLMessage.INFO_VALUE, replServer.getWelcome());
                    infoMessage.put(REPLMessage.STATUS, REPLMessage.SUCCEEDED);
                    return finishReplySucceeded(infoMessage, "welcome");

                default:
                    final REPLMessage message = new REPLMessage(REPLMessage.OP, REPLMessage.INFO);
                    return finishReplyFailed(message, "No info about topic \"" + topic + "\"");
            }
        }
    };
    public static final REPLHandler KILL_HANDLER = new REPLHandler(REPLMessage.KILL) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            if (replServer.getCurrentContext().getLevel() == 0) {
                return finishReplyFailed(createReply(), "nothing to kill");
            }
            throw new KillException();
        }
    };

    public static final REPLHandler LOAD_HANDLER = new REPLHandler(REPLMessage.LOAD_SOURCE) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = new REPLMessage(REPLMessage.OP, REPLMessage.LOAD_SOURCE);
            final String fileName = request.get(REPLMessage.SOURCE_NAME);
            final boolean stepInto = REPLMessage.TRUE.equals(request.get(REPLMessage.STEP_INTO));
            try {
                final Source fileSource = Source.fromFileName(fileName);
                replServer.getCurrentContext().eval(fileSource, stepInto);
                reply.put(REPLMessage.FILE_PATH, fileName);
                return finishReplySucceeded(reply, fileName + "  loaded");
            } catch (QuitException ex) {
                throw ex;
            } catch (KillException ex) {
                return finishReplySucceeded(reply, fileName + " killed");
            } catch (Exception ex) {
                return finishReplyFailed(reply, ex);
            }
        }
    };

    public static final REPLHandler QUIT_HANDLER = new REPLHandler(REPLMessage.QUIT) {
        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            throw new QuitException();
        }
    };

    public static final REPLHandler SET_LANGUAGE_HANDLER = new REPLHandler(REPLMessage.SET_LANGUAGE) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = new REPLMessage(REPLMessage.OP, REPLMessage.SET_LANGUAGE);
            String languageName = request.get(REPLMessage.LANG_NAME);
            if (languageName == null) {
                final String oldLanguageName = replServer.getCurrentContext().getLanguageName();
                reply.put(REPLMessage.LANG_NAME, reply.put(REPLMessage.LANG_NAME, oldLanguageName));
                return finishReplySucceeded(reply, "Language set to " + oldLanguageName);
            }
            reply.put(REPLMessage.LANG_NAME, languageName);
            try {
                final String newLanguageName = replServer.getCurrentContext().setLanguage(languageName);
                if (newLanguageName != null) {
                    return finishReplySucceeded(reply, "Language set to " + newLanguageName);
                }
            } catch (Exception ex) {
                return finishReplyFailed(reply, ex);
            }
            return finishReplyFailed(reply, "Language \"" + languageName + "\" not supported");
        }
    };

    public static final REPLHandler SET_BREAK_CONDITION_HANDLER = new REPLHandler(REPLMessage.SET_BREAK_CONDITION) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage message = new REPLMessage(REPLMessage.OP, REPLMessage.SET_BREAK_CONDITION);
            Integer breakpointNumber = request.getIntValue(REPLMessage.BREAKPOINT_ID);
            if (breakpointNumber == null) {
                return finishReplyFailed(message, "missing breakpoint number");
            }
            message.put(REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointNumber));
            final BreakpointInfo breakpointInfo = replServer.findBreakpoint(breakpointNumber);
            if (breakpointInfo == null) {
                return finishReplyFailed(message, "no breakpoint number " + breakpointNumber);
            }
            final String expr = request.get(REPLMessage.BREAKPOINT_CONDITION);
            if (expr == null || expr.isEmpty()) {
                return finishReplyFailed(message, "missing condition for " + breakpointNumber);
            }
            try {
                breakpointInfo.setCondition(expr);
            } catch (IOException ex) {
                return finishReplyFailed(message, "invalid condition for " + breakpointNumber);
            } catch (UnsupportedOperationException ex) {
                return finishReplyFailed(message, "conditions not supported by breakpoint " + breakpointNumber);
            } catch (Exception ex) {
                return finishReplyFailed(message, ex);
            }
            message.put(REPLMessage.BREAKPOINT_CONDITION, expr);
            return finishReplySucceeded(message, "Breakpoint " + breakpointNumber + " condition=\"" + expr + "\"");
        }
    };

    public static final REPLHandler STEP_INTO_HANDLER = new REPLHandler(REPLMessage.STEP_INTO) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = createReply();
            Integer repeat = request.getIntValue(REPLMessage.REPEAT);
            if (repeat == null) {
                repeat = 1;
            }
            final String countMessage = repeat == 1 ? "" : "<" + repeat + ">";
            replServer.getCurrentContext().prepareStepInto(repeat);
            return finishReplySucceeded(reply, "StepInto " + countMessage + " enabled");
        }
    };

    public static final REPLHandler STEP_OUT_HANDLER = new REPLHandler(REPLMessage.STEP_OUT) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = createReply();
            replServer.getCurrentContext().prepareStepOut();
            return finishReplySucceeded(reply, "StepOut enabled");
        }
    };

    public static final REPLHandler STEP_OVER_HANDLER = new REPLHandler(REPLMessage.STEP_OVER) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = createReply();
            Integer repeat = request.getIntValue(REPLMessage.REPEAT);
            if (repeat == null) {
                repeat = 1;
            }
            final String countMessage = repeat == 1 ? "" : "<" + repeat + ">";
            replServer.getCurrentContext().prepareStepOver(repeat);
            return finishReplySucceeded(reply, "StepOver " + countMessage + " enabled");
        }
    };

    public static final REPLHandler TRUFFLE_HANDLER = new REPLHandler(REPLMessage.TRUFFLE) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = createReply();
            final ASTPrinter astPrinter = replServer.getASTPrinter();
            final String topic = request.get(REPLMessage.TOPIC);
            reply.put(REPLMessage.TOPIC, topic);
            Node node = replServer.getCurrentContext().getNodeAtHalt();
            if (node == null) {
                return finishReplyFailed(reply, "no current AST node");
            }
            final Integer depth = request.getIntValue(REPLMessage.AST_DEPTH);
            if (depth == null) {
                return finishReplyFailed(reply, "missing AST depth");
            }
            try {
                switch (topic) {
                    case REPLMessage.AST:
                        while (node.getParent() != null) {
                            node = node.getParent();
                        }
                        final String astText = astPrinter.displayAST(node, depth, replServer.getCurrentContext().getNodeAtHalt());
                        return finishReplySucceeded(reply, astText);
                    case REPLMessage.SUBTREE:
                    case REPLMessage.SUB:
                        final String subTreeText = astPrinter.displayAST(node, depth);
                        return finishReplySucceeded(reply, subTreeText);
                    default:
                        return finishReplyFailed(reply, "Unknown \"" + REPLMessage.TRUFFLE.toString() + "\" topic");
                }

            } catch (Exception ex) {
                return finishReplyFailed(reply, ex);
            }
        }
    };

    public static final REPLHandler UNSET_BREAK_CONDITION_HANDLER = new REPLHandler(REPLMessage.UNSET_BREAK_CONDITION) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage message = new REPLMessage(REPLMessage.OP, REPLMessage.UNSET_BREAK_CONDITION);
            Integer breakpointNumber = request.getIntValue(REPLMessage.BREAKPOINT_ID);
            if (breakpointNumber == null) {
                return finishReplyFailed(message, "missing breakpoint number");
            }
            message.put(REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointNumber));
            final BreakpointInfo breakpointInfo = replServer.findBreakpoint(breakpointNumber);
            if (breakpointInfo == null) {
                return finishReplyFailed(message, "no breakpoint number " + breakpointNumber);
            }
            try {
                breakpointInfo.setCondition(null);
            } catch (Exception ex) {
                return finishReplyFailed(message, ex);
            }
            return finishReplySucceeded(message, "Breakpoint " + breakpointNumber + " condition cleared");
        }
    };

    public static final REPLHandler TRUFFLE_NODE_HANDLER = new REPLHandler(REPLMessage.TRUFFLE_NODE) {

        @Override
        public REPLMessage[] receive(REPLMessage request, REPLServer replServer) {
            final REPLMessage reply = createReply();
            final Node node = replServer.getCurrentContext().getNodeAtHalt();
            if (node == null) {
                return finishReplyFailed(reply, "no current AST node");
            }

            try {
                final StringBuilder sb = new StringBuilder();
                sb.append(replServer.getASTPrinter().displayNodeWithInstrumentation(node));

                final SourceSection sourceSection = node.getSourceSection();
                if (sourceSection != null) {
                    final String code = sourceSection.getCode();
                    sb.append(" \"");
                    sb.append(code.substring(0, Math.min(code.length(), 15)));
                    sb.append("...\"");
                }
                return finishReplySucceeded(reply, sb.toString());
            } catch (Exception ex) {
                return finishReplyFailed(reply, ex);
            }
        }
    };
}
