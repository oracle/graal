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

import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.PolyglotEngine.Language;
import com.oracle.truffle.tools.debug.shell.server.InstrumentationUtils.LocationPrinter;
import com.oracle.truffle.tools.debug.shell.server.REPLServer.BreakpointInfo;
import com.oracle.truffle.tools.debug.shell.server.REPLServer.Context;
import com.oracle.truffle.tools.debug.shell.server.REPLServer.REPLVisualizer;
import java.io.File;

/**
 * Server-side REPL implementation of an
 * {@linkplain com.oracle.truffle.tools.debug.shell.REPLMessage "op"}.
 * <p>
 * The language-agnostic handlers are implemented here.
 */
@SuppressWarnings("deprecation")
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
    abstract com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer);

    /**
     * Creates skeleton for a reply message that identifies the operation currently being handled.
     */
    com.oracle.truffle.tools.debug.shell.REPLMessage createReply() {
        return new com.oracle.truffle.tools.debug.shell.REPLMessage(com.oracle.truffle.tools.debug.shell.REPLMessage.OP, op);
    }

    /**
     * Completes a reply, reporting and explaining successful handling.
     */
    protected static final com.oracle.truffle.tools.debug.shell.REPLMessage[] finishReplySucceeded(com.oracle.truffle.tools.debug.shell.REPLMessage reply, String explanation) {
        reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.STATUS, com.oracle.truffle.tools.debug.shell.REPLMessage.SUCCEEDED);
        reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.DISPLAY_MSG, explanation);
        final com.oracle.truffle.tools.debug.shell.REPLMessage[] replies = new com.oracle.truffle.tools.debug.shell.REPLMessage[]{reply};
        return replies;
    }

    /**
     * Completes a reply, reporting and explaining failed handling.
     */
    protected static final com.oracle.truffle.tools.debug.shell.REPLMessage[] finishReplyFailed(com.oracle.truffle.tools.debug.shell.REPLMessage reply, String explanation) {
        reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.STATUS, com.oracle.truffle.tools.debug.shell.REPLMessage.FAILED);
        reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.DISPLAY_MSG, explanation);
        final com.oracle.truffle.tools.debug.shell.REPLMessage[] replies = new com.oracle.truffle.tools.debug.shell.REPLMessage[]{reply};
        return replies;
    }

    protected static final com.oracle.truffle.tools.debug.shell.REPLMessage[] finishReplyFailed(com.oracle.truffle.tools.debug.shell.REPLMessage reply, Exception ex) {
        reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.STATUS, com.oracle.truffle.tools.debug.shell.REPLMessage.FAILED);
        String message = ex.getMessage();
        reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.DISPLAY_MSG, message == null ? ex.getClass().getSimpleName() : message);
        final com.oracle.truffle.tools.debug.shell.REPLMessage[] replies = new com.oracle.truffle.tools.debug.shell.REPLMessage[]{reply};
        return replies;
    }

    protected static final com.oracle.truffle.tools.debug.shell.REPLMessage createBreakpointInfoMessage(BreakpointInfo info) {
        final com.oracle.truffle.tools.debug.shell.REPLMessage infoMessage = new com.oracle.truffle.tools.debug.shell.REPLMessage(com.oracle.truffle.tools.debug.shell.REPLMessage.OP,
                        com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_INFO);
        infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_ID, Integer.toString(info.getID()));
        infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_STATE, info.describeState());
        infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_HIT_COUNT, Integer.toString(info.getHitCount()));
        infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_IGNORE_COUNT, Integer.toString(info.getIgnoreCount()));
        infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.INFO_VALUE, info.describeLocation());
        if (info.getCondition() != null) {
            infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_CONDITION, info.getCondition());
        }
        infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.STATUS, com.oracle.truffle.tools.debug.shell.REPLMessage.SUCCEEDED);
        return infoMessage;
    }

    protected static final com.oracle.truffle.tools.debug.shell.REPLMessage createFrameInfoMessage(final REPLServer replServer, int number, Node node) {
        final com.oracle.truffle.tools.debug.shell.REPLMessage infoMessage = new com.oracle.truffle.tools.debug.shell.REPLMessage(com.oracle.truffle.tools.debug.shell.REPLMessage.OP,
                        com.oracle.truffle.tools.debug.shell.REPLMessage.FRAME_INFO);
        infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.FRAME_NUMBER, Integer.toString(number));
        infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.SOURCE_LOCATION, replServer.getLocationPrinter().displaySourceLocation(node));
        infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.METHOD_NAME, replServer.getCurrentContext().getVisualizer().displayMethodName(node));

        if (node != null) {
            SourceSection section = node.getSourceSection();
            if (section == null) {
                section = node.getEncapsulatingSourceSection();
            }
            if (section != null && section.getSource() != null) {
                infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.FILE_PATH, section.getSource().getPath());
                infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.LINE_NUMBER, Integer.toString(section.getStartLine()));
                infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.SOURCE_LINE_TEXT, section.getSource().getCode(section.getStartLine()));
            }
        }
        infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.STATUS, com.oracle.truffle.tools.debug.shell.REPLMessage.SUCCEEDED);
        return infoMessage;
    }

    public static final REPLHandler BACKTRACE_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.BACKTRACE) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final REPLVisualizer visualizer = replServer.getCurrentContext().getVisualizer();
            final ArrayList<com.oracle.truffle.tools.debug.shell.REPLMessage> replies = new ArrayList<>();
            final Context currentContext = replServer.getCurrentContext();
            final List<FrameInstance> stack = currentContext.getStack();
            int frameIndex = 0; // Index into list of displayed frames
            // Iterate the real stack for the current execution
            for (int stackIndex = 0; stackIndex < stack.size(); stackIndex++) {
                final Node callNode = stackIndex == 0 ? currentContext.getNode() : stack.get(stackIndex).getCallNode();
                if (callNode != null) {
                    replies.add(btMessage(frameIndex++, callNode, visualizer, replServer.getLocationPrinter()));
                }
            }
            if (replies.size() > 0) {
                return replies.toArray(new com.oracle.truffle.tools.debug.shell.REPLMessage[0]);
            }
            return finishReplyFailed(new com.oracle.truffle.tools.debug.shell.REPLMessage(com.oracle.truffle.tools.debug.shell.REPLMessage.OP,
                            com.oracle.truffle.tools.debug.shell.REPLMessage.BACKTRACE), "No stack");
        }
    };

    private static com.oracle.truffle.tools.debug.shell.REPLMessage btMessage(int index, Node node, REPLVisualizer visualizer, LocationPrinter locationPrinter) {
        final com.oracle.truffle.tools.debug.shell.REPLMessage btMessage = new com.oracle.truffle.tools.debug.shell.REPLMessage(com.oracle.truffle.tools.debug.shell.REPLMessage.OP,
                        com.oracle.truffle.tools.debug.shell.REPLMessage.BACKTRACE);
        btMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.FRAME_NUMBER, Integer.toString(index));
        if (node != null) {
            btMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.SOURCE_LOCATION, locationPrinter.displaySourceLocation(node));
            btMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.METHOD_NAME, visualizer.displayMethodName(node));
            SourceSection section = node.getSourceSection();
            if (section == null) {
                section = node.getEncapsulatingSourceSection();
            }
            if (section != null && section.getSource() != null) {
                btMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.FILE_PATH, section.getSource().getPath());
                btMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.LINE_NUMBER, Integer.toString(section.getStartLine()));
                btMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.SOURCE_LINE_TEXT, section.getSource().getCode(section.getStartLine()));
            }
            btMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.STATUS, com.oracle.truffle.tools.debug.shell.REPLMessage.SUCCEEDED);
        }
        return btMessage;
    }

    public static final REPLHandler BREAK_AT_LINE_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAK_AT_LINE) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = createReply();
            final String path = request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.FILE_PATH);
            final String fileName = request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.SOURCE_NAME);
            final String lookupFile = (path == null || path.isEmpty()) ? fileName : path;
            Source source = null;
            try {
                source = Source.newBuilder(new File(lookupFile)).build();
            } catch (Exception ex) {
                return finishReplyFailed(reply, ex);
            }
            if (source == null) {
                return finishReplyFailed(reply, fileName + " not found");
            }
            final Integer lineNumber = request.getIntValue(com.oracle.truffle.tools.debug.shell.REPLMessage.LINE_NUMBER);
            if (lineNumber == null) {
                return finishReplyFailed(reply, "missing line number");
            }
            Integer ignoreCount = request.getIntValue(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_IGNORE_COUNT);
            if (ignoreCount == null) {
                ignoreCount = 0;
            }
            BreakpointInfo breakpointInfo;
            try {
                breakpointInfo = replServer.setLineBreakpoint(DEFAULT_IGNORE_COUNT, source.createLineLocation(lineNumber), false);
            } catch (IOException ex) {
                return finishReplyFailed(reply, ex.getMessage());
            }
            reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.SOURCE_NAME, fileName);
            reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.FILE_PATH, source.getPath());
            reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointInfo.getID()));
            reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.LINE_NUMBER, Integer.toString(lineNumber));
            reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_IGNORE_COUNT, ignoreCount.toString());
            return finishReplySucceeded(reply, "Breakpoint set");
        }
    };

    public static final REPLHandler BREAK_AT_LINE_ONCE_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAK_AT_LINE_ONCE) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = createReply();
            final String path = request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.FILE_PATH);
            final String fileName = request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.SOURCE_NAME);
            final String lookupFile = (path == null || path.isEmpty()) ? fileName : path;
            Source source = null;
            try {
                source = Source.newBuilder(new File(lookupFile)).build();
            } catch (Exception ex) {
                return finishReplyFailed(reply, ex);
            }
            if (source == null) {
                return finishReplyFailed(reply, fileName + " not found");
            }
            final Integer lineNumber = request.getIntValue(com.oracle.truffle.tools.debug.shell.REPLMessage.LINE_NUMBER);
            if (lineNumber == null) {
                return finishReplyFailed(reply, "missing line number");
            }
            BreakpointInfo breakpointInfo;
            try {
                breakpointInfo = replServer.setLineBreakpoint(DEFAULT_IGNORE_COUNT, source.createLineLocation(lineNumber), true);
            } catch (IOException ex) {
                return finishReplyFailed(reply, ex.getMessage());
            }
            reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.SOURCE_NAME, fileName);
            reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.FILE_PATH, source.getPath());
            reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointInfo.getID()));
            reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.LINE_NUMBER, Integer.toString(lineNumber));
            return finishReplySucceeded(reply, "One-shot line breakpoint set");
        }
    };

    public static final REPLHandler BREAKPOINT_INFO_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_INFO) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = createReply();
            final ArrayList<com.oracle.truffle.tools.debug.shell.REPLMessage> infoMessages = new ArrayList<>();
            for (BreakpointInfo breakpointInfo : replServer.getBreakpoints()) {
                infoMessages.add(createBreakpointInfoMessage(breakpointInfo));
            }
            if (infoMessages.size() > 0) {
                return infoMessages.toArray(new com.oracle.truffle.tools.debug.shell.REPLMessage[0]);
            }
            return finishReplyFailed(reply, "No breakpoints");
        }
    };

    public static final REPLHandler RUN_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.RUN) {

        @Override
        com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            replServer.run();
            com.oracle.truffle.tools.debug.shell.REPLMessage reply = createReply();
            return finishReplySucceeded(reply, "run completed");
        }
    };

    public static final REPLHandler CALL_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.CALL) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = new com.oracle.truffle.tools.debug.shell.REPLMessage(com.oracle.truffle.tools.debug.shell.REPLMessage.OP,
                            com.oracle.truffle.tools.debug.shell.REPLMessage.CALL);
            final String callName = request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.CALL_NAME);
            if (callName == null) {
                return finishReplyFailed(reply, "no name specified");
            }
            final ArrayList<String> argList = new ArrayList<>();
            for (int argCount = 0; argCount < com.oracle.truffle.tools.debug.shell.REPLMessage.ARG_NAMES.length; argCount++) {
                final String arg = request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.ARG_NAMES[argCount]);
                if (arg == null) {
                    break;
                }
                argList.add(arg);
            }
            final boolean stepInto = com.oracle.truffle.tools.debug.shell.REPLMessage.TRUE.equals(request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.STEP_INTO));
            try {
                final Object result = replServer.getCurrentContext().call(callName, stepInto, argList);
                reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.VALUE, result == null ? "<void>" : result.toString());
            } catch (Exception ex) {
                return finishReplyFailed(reply, ex);
            }
            return finishReplySucceeded(reply, callName + " returned");
        }
    };

    public static final REPLHandler CLEAR_BREAK_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.CLEAR_BREAK) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = createReply();
            final Integer breakpointNumber = request.getIntValue(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_ID);
            if (breakpointNumber == null) {
                return finishReplyFailed(reply, "missing breakpoint number");
            }
            final BreakpointInfo breakpointInfo = replServer.findBreakpoint(breakpointNumber);
            if (breakpointInfo == null) {
                return finishReplyFailed(reply, "no breakpoint number " + breakpointNumber);
            }
            breakpointInfo.dispose();
            reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointNumber));
            return finishReplySucceeded(reply, "Breakpoint " + breakpointNumber + " cleared");
        }
    };

    public static final REPLHandler CONTINUE_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.CONTINUE) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = createReply();
            replServer.getCurrentContext().prepareContinue();
            return finishReplySucceeded(reply, "Continue mode entered");
        }
    };

    public static final REPLHandler DELETE_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.DELETE_BREAK) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = createReply();
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

    public static final REPLHandler DISABLE_BREAK_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.DISABLE_BREAK) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = createReply();
            Integer breakpointNumber = request.getIntValue(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_ID);
            if (breakpointNumber == null) {
                return finishReplyFailed(reply, "missing breakpoint number");
            }
            final BreakpointInfo breakpointInfo = replServer.findBreakpoint(breakpointNumber);
            if (breakpointInfo == null) {
                return finishReplyFailed(reply, "no breakpoint number " + breakpointNumber);
            }
            breakpointInfo.setEnabled(false);
            reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointNumber));
            return finishReplySucceeded(reply, "Breakpoint " + breakpointNumber + " disabled");
        }
    };

    public static final REPLHandler ENABLE_BREAK_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.ENABLE_BREAK) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = createReply();
            Integer breakpointNumber = request.getIntValue(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_ID);
            if (breakpointNumber == null) {
                return finishReplyFailed(reply, "missing breakpoint number");
            }
            final BreakpointInfo breakpointInfo = replServer.findBreakpoint(breakpointNumber);
            if (breakpointInfo == null) {
                return finishReplyFailed(reply, "no breakpoint number " + breakpointNumber);
            }
            breakpointInfo.setEnabled(true);
            reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointNumber));
            return finishReplySucceeded(reply, "Breakpoint " + breakpointNumber + " enabled");
        }
    };
    public static final REPLHandler EVAL_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.EVAL) {
        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = createReply();
            final String sourceName = request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.SOURCE_NAME);
            reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.SOURCE_NAME, sourceName);
            final Context serverContext = replServer.getCurrentContext();
            reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.DEBUG_LEVEL, Integer.toString(serverContext.getLevel()));

            final String source = request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.CODE);
            final Integer frameNumber = request.getIntValue(com.oracle.truffle.tools.debug.shell.REPLMessage.FRAME_NUMBER);
            final boolean stepInto = com.oracle.truffle.tools.debug.shell.REPLMessage.TRUE.equals(request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.STEP_INTO));
            try {
                Object returnValue = serverContext.eval(source, frameNumber, stepInto);
                return finishReplySucceeded(reply, serverContext.displayValue(frameNumber, returnValue, 0));
            } catch (Exception ex) {
                return finishReplyFailed(reply, ex);
            }
        }
    };
    public static final REPLHandler FILE_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.FILE) {
        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = createReply();
            final String fileName = request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.SOURCE_NAME);
            if (fileName == null) {
                return finishReplyFailed(reply, "no file specified");
            }
            reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.SOURCE_NAME, fileName);
            try {
                Source source = Source.newBuilder(new File(fileName)).build();
                if (source == null) {
                    return finishReplyFailed(reply, "file \"" + fileName + "\" not found");
                } else {
                    reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.FILE_PATH, source.getPath());
                    reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.CODE, source.getCode());
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
    public static final REPLHandler FRAME_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.FRAME) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final Integer frameNumber = request.getIntValue(com.oracle.truffle.tools.debug.shell.REPLMessage.FRAME_NUMBER);
            if (frameNumber == null) {
                return finishReplyFailed(createReply(), "no frame number specified");
            }
            final Context currentContext = replServer.getCurrentContext();
            final List<FrameInstance> stack = currentContext.getStack();
            if (frameNumber < 0 || frameNumber > stack.size()) {
                return finishReplyFailed(createReply(), "frame number " + frameNumber + " out of range");
            }
            final REPLVisualizer visualizer = replServer.getCurrentContext().getVisualizer();

            MaterializedFrame frame;
            Node node;
            if (frameNumber == 0) {
                frame = currentContext.getFrame();
                node = currentContext.getNode();
            } else {
                final FrameInstance instance = stack.get(frameNumber);
                frame = instance.getFrame(FrameAccess.MATERIALIZE, true).materialize();
                node = instance.getCallNode();
            }
            List<? extends FrameSlot> slots = frame.getFrameDescriptor().getSlots();
            if (slots.size() == 0) {
                final com.oracle.truffle.tools.debug.shell.REPLMessage emptyFrameMessage = createFrameInfoMessage(replServer, frameNumber, node);
                return finishReplySucceeded(emptyFrameMessage, "empty frame");
            }
            final ArrayList<com.oracle.truffle.tools.debug.shell.REPLMessage> replies = new ArrayList<>();

            for (FrameSlot slot : slots) {
                final com.oracle.truffle.tools.debug.shell.REPLMessage slotMessage = createFrameInfoMessage(replServer, frameNumber, node);
                slotMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.SLOT_INDEX, Integer.toString(slot.getIndex()));
                slotMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.SLOT_ID, visualizer.displayIdentifier(slot));
                slotMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.SLOT_VALUE, currentContext.displayValue(frameNumber, frame.getValue(slot), 0));
                slotMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.STATUS, com.oracle.truffle.tools.debug.shell.REPLMessage.SUCCEEDED);
                replies.add(slotMessage);
            }
            return replies.toArray(new com.oracle.truffle.tools.debug.shell.REPLMessage[0]);
        }
    };

    public static final REPLHandler INFO_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.INFO) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final String topic = request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.TOPIC);

            if (topic == null || topic.isEmpty()) {
                final com.oracle.truffle.tools.debug.shell.REPLMessage message = new com.oracle.truffle.tools.debug.shell.REPLMessage(com.oracle.truffle.tools.debug.shell.REPLMessage.OP,
                                com.oracle.truffle.tools.debug.shell.REPLMessage.INFO);
                return finishReplyFailed(message, "No info topic specified");
            }

            switch (topic) {

                case com.oracle.truffle.tools.debug.shell.REPLMessage.INFO_SUPPORTED_LANGUAGES:
                    final ArrayList<com.oracle.truffle.tools.debug.shell.REPLMessage> langMessages = new ArrayList<>();

                    for (Language language : replServer.getLanguages()) {
                        final com.oracle.truffle.tools.debug.shell.REPLMessage infoMessage = new com.oracle.truffle.tools.debug.shell.REPLMessage(com.oracle.truffle.tools.debug.shell.REPLMessage.OP,
                                        com.oracle.truffle.tools.debug.shell.REPLMessage.INFO);
                        infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.TOPIC, com.oracle.truffle.tools.debug.shell.REPLMessage.INFO_SUPPORTED_LANGUAGES);
                        infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.LANG_NAME, language.getName());
                        infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.LANG_VER, language.getVersion());
                        infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.STATUS, com.oracle.truffle.tools.debug.shell.REPLMessage.SUCCEEDED);
                        langMessages.add(infoMessage);
                    }
                    return langMessages.toArray(new com.oracle.truffle.tools.debug.shell.REPLMessage[0]);

                case com.oracle.truffle.tools.debug.shell.REPLMessage.INFO_CURRENT_LANGUAGE:
                    final com.oracle.truffle.tools.debug.shell.REPLMessage reply = new com.oracle.truffle.tools.debug.shell.REPLMessage(com.oracle.truffle.tools.debug.shell.REPLMessage.OP,
                                    com.oracle.truffle.tools.debug.shell.REPLMessage.INFO);
                    reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.TOPIC, com.oracle.truffle.tools.debug.shell.REPLMessage.INFO_CURRENT_LANGUAGE);
                    final String languageName = replServer.getCurrentContext().getLanguageName();
                    reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.LANG_NAME, languageName);
                    return finishReplySucceeded(reply, languageName);

                case com.oracle.truffle.tools.debug.shell.REPLMessage.WELCOME_MESSAGE:
                    final com.oracle.truffle.tools.debug.shell.REPLMessage infoMessage = new com.oracle.truffle.tools.debug.shell.REPLMessage(com.oracle.truffle.tools.debug.shell.REPLMessage.OP,
                                    com.oracle.truffle.tools.debug.shell.REPLMessage.INFO);
                    infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.TOPIC, com.oracle.truffle.tools.debug.shell.REPLMessage.WELCOME_MESSAGE);
                    infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.INFO_VALUE, replServer.getWelcome());
                    infoMessage.put(com.oracle.truffle.tools.debug.shell.REPLMessage.STATUS, com.oracle.truffle.tools.debug.shell.REPLMessage.SUCCEEDED);
                    return finishReplySucceeded(infoMessage, "welcome");

                default:
                    final com.oracle.truffle.tools.debug.shell.REPLMessage message = new com.oracle.truffle.tools.debug.shell.REPLMessage(com.oracle.truffle.tools.debug.shell.REPLMessage.OP,
                                    com.oracle.truffle.tools.debug.shell.REPLMessage.INFO);
                    return finishReplyFailed(message, "No info about topic \"" + topic + "\"");
            }
        }
    };
    public static final REPLHandler KILL_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.KILL) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = new com.oracle.truffle.tools.debug.shell.REPLMessage(com.oracle.truffle.tools.debug.shell.REPLMessage.OP,
                            com.oracle.truffle.tools.debug.shell.REPLMessage.KILL);
            if (replServer.getCurrentContext().getLevel() == 0) {
                return finishReplyFailed(reply, "nothing to kill");
            }
            replServer.getCurrentContext().kill();
            return finishReplySucceeded(reply, "execution killed");

        }
    };

    public static final REPLHandler LOAD_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.LOAD_SOURCE) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = new com.oracle.truffle.tools.debug.shell.REPLMessage(com.oracle.truffle.tools.debug.shell.REPLMessage.OP,
                            com.oracle.truffle.tools.debug.shell.REPLMessage.LOAD_SOURCE);
            final String fileName = request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.SOURCE_NAME);
            final boolean stepInto = com.oracle.truffle.tools.debug.shell.REPLMessage.TRUE.equals(request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.STEP_INTO));
            try {
                final Source fileSource = Source.newBuilder(new File(fileName)).build();
                replServer.getCurrentContext().eval(fileSource, stepInto);
                reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.FILE_PATH, fileName);
                return finishReplySucceeded(reply, fileName + "  loaded");
            } catch (Exception ex) {
                return finishReplyFailed(reply, ex);
            }
        }
    };

    public static final REPLHandler SET_LANGUAGE_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.SET_LANGUAGE) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = new com.oracle.truffle.tools.debug.shell.REPLMessage(com.oracle.truffle.tools.debug.shell.REPLMessage.OP,
                            com.oracle.truffle.tools.debug.shell.REPLMessage.SET_LANGUAGE);
            String languageName = request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.LANG_NAME);
            if (languageName == null) {
                final String oldLanguageName = replServer.getCurrentContext().getLanguageName();
                reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.LANG_NAME, reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.LANG_NAME, oldLanguageName));
                return finishReplySucceeded(reply, "Language set to " + oldLanguageName);
            }
            reply.put(com.oracle.truffle.tools.debug.shell.REPLMessage.LANG_NAME, languageName);
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

    public static final REPLHandler SET_BREAK_CONDITION_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.SET_BREAK_CONDITION) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage message = new com.oracle.truffle.tools.debug.shell.REPLMessage(com.oracle.truffle.tools.debug.shell.REPLMessage.OP,
                            com.oracle.truffle.tools.debug.shell.REPLMessage.SET_BREAK_CONDITION);
            Integer breakpointNumber = request.getIntValue(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_ID);
            if (breakpointNumber == null) {
                return finishReplyFailed(message, "missing breakpoint number");
            }
            message.put(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointNumber));
            final BreakpointInfo breakpointInfo = replServer.findBreakpoint(breakpointNumber);
            if (breakpointInfo == null) {
                return finishReplyFailed(message, "no breakpoint number " + breakpointNumber);
            }
            final String expr = request.get(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_CONDITION);
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
            message.put(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_CONDITION, expr);
            return finishReplySucceeded(message, "Breakpoint " + breakpointNumber + " condition=\"" + expr + "\"");
        }
    };

    public static final REPLHandler STEP_INTO_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.STEP_INTO) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = createReply();
            Integer repeat = request.getIntValue(com.oracle.truffle.tools.debug.shell.REPLMessage.REPEAT);
            if (repeat == null) {
                repeat = 1;
            }
            final String countMessage = repeat == 1 ? "" : "<" + repeat + ">";
            replServer.getCurrentContext().prepareStepInto(repeat);
            return finishReplySucceeded(reply, "StepInto " + countMessage + " enabled");
        }
    };

    public static final REPLHandler STEP_OUT_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.STEP_OUT) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = createReply();
            replServer.getCurrentContext().prepareStepOut();
            return finishReplySucceeded(reply, "StepOut enabled");
        }
    };

    public static final REPLHandler STEP_OVER_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.STEP_OVER) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage reply = createReply();
            Integer repeat = request.getIntValue(com.oracle.truffle.tools.debug.shell.REPLMessage.REPEAT);
            if (repeat == null) {
                repeat = 1;
            }
            final String countMessage = repeat == 1 ? "" : "<" + repeat + ">";
            replServer.getCurrentContext().prepareStepOver(repeat);
            return finishReplySucceeded(reply, "StepOver " + countMessage + " enabled");
        }
    };

    public static final REPLHandler UNSET_BREAK_CONDITION_HANDLER = new REPLHandler(com.oracle.truffle.tools.debug.shell.REPLMessage.UNSET_BREAK_CONDITION) {

        @Override
        public com.oracle.truffle.tools.debug.shell.REPLMessage[] receive(com.oracle.truffle.tools.debug.shell.REPLMessage request, REPLServer replServer) {
            final com.oracle.truffle.tools.debug.shell.REPLMessage message = new com.oracle.truffle.tools.debug.shell.REPLMessage(com.oracle.truffle.tools.debug.shell.REPLMessage.OP,
                            com.oracle.truffle.tools.debug.shell.REPLMessage.UNSET_BREAK_CONDITION);
            Integer breakpointNumber = request.getIntValue(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_ID);
            if (breakpointNumber == null) {
                return finishReplyFailed(message, "missing breakpoint number");
            }
            message.put(com.oracle.truffle.tools.debug.shell.REPLMessage.BREAKPOINT_ID, Integer.toString(breakpointNumber));
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

}
