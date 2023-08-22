/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.jdwp.impl;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

import com.oracle.truffle.espresso.jdwp.api.ErrorCodes;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;

public final class DebuggerConnection implements Commands {

    private final DebuggerController controller;
    private final JDWPContext context;
    private final SocketConnection connection;
    private final BlockingQueue<DebuggerCommand> queue = new ArrayBlockingQueue<>(512);
    private Thread commandProcessor;
    private Thread jdwpTransport;

    public DebuggerConnection(SocketConnection connection, DebuggerController controller) {
        this.connection = connection;
        this.controller = controller;
        this.context = controller.getContext();
    }

    public void doProcessCommands(boolean suspend, Collection<Thread> activeThreads, Callable<Void> job) {
        // fire up two threads, one for the low-level connection to receive packets
        // and one for processing the debugger commands from a queue
        commandProcessor = new Thread(new CommandProcessorThread(), "jdwp-command-processor");
        commandProcessor.setDaemon(true);
        commandProcessor.start();
        activeThreads.add(commandProcessor);

        jdwpTransport = new Thread(new JDWPTransportThread(), "jdwp-transport");
        jdwpTransport.setDaemon(true);
        jdwpTransport.start();
        activeThreads.add(jdwpTransport);

        if (suspend) {
            // check if this is called from a guest thread
            Object guestThread = context.asGuestThread(Thread.currentThread());
            if (guestThread == null) {
                // a reconnect, meaning no suspend
                return;
            }
            // only a JDWP resume/resumeAll command can resume this thread
            controller.suspend(context.asGuestThread(Thread.currentThread()), SuspendStrategy.EVENT_THREAD, Collections.singletonList(job), true);
        }
    }

    public void close() {
        try {
            connection.close(controller);
            controller.getEventListener().setConnection(null);
        } catch (IOException e) {
            throw new RuntimeException("Closing socket connection failed", e);
        }
    }

    @Override
    public void stepInto(Object thread, RequestFilter filter) {
        DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.STEP_INTO, filter);
        controller.setCommandRequestId(thread, filter.getRequestId(), filter.getSuspendPolicy(), false, false, DebuggerCommand.Kind.STEP_INTO);
        addBlocking(debuggerCommand);
    }

    @Override
    public void stepOver(Object thread, RequestFilter filter) {
        DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.STEP_OVER, filter);
        controller.setCommandRequestId(thread, filter.getRequestId(), filter.getSuspendPolicy(), false, false, DebuggerCommand.Kind.STEP_OVER);
        addBlocking(debuggerCommand);
    }

    @Override
    public void stepOut(Object thread, RequestFilter filter) {
        DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.STEP_OUT, filter);
        controller.setCommandRequestId(thread, filter.getRequestId(), filter.getSuspendPolicy(), false, false, DebuggerCommand.Kind.STEP_OUT);
        addBlocking(debuggerCommand);
    }

    // the suspended event instance is only valid while suspended, so
    // to avoid a race, we have to block until we're sure that the debugger
    // command was prepared on the suspended event instance
    private void addBlocking(DebuggerCommand command) {
        queue.add(command);
        synchronized (command) {
            while (!command.isSubmitted()) {
                try {
                    command.wait();
                } catch (InterruptedException e) {
                    controller.warning(() -> "could not submit debugger command due to " + e.getMessage());
                }
            }
        }
    }

    @Override
    public Callable<Void> createLineBreakpointCommand(BreakpointInfo info) {
        return new Callable<>() {
            @Override
            public Void call() {
                LineBreakpointInfo lineInfo = (LineBreakpointInfo) info;
                DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.SUBMIT_LINE_BREAKPOINT, info.getFilter());
                debuggerCommand.setSourceLocation(new SourceLocation(lineInfo.getSlashName(), (int) lineInfo.getLine(), context));
                debuggerCommand.setBreakpointInfo(info);
                addBlocking(debuggerCommand);
                return null;
            }
        };
    }

    @Override
    public Callable<Void> createExceptionBreakpoint(BreakpointInfo info) {
        return new Callable<>() {
            @Override
            public Void call() {
                DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.SUBMIT_EXCEPTION_BREAKPOINT, null);
                debuggerCommand.setBreakpointInfo(info);
                addBlocking(debuggerCommand);
                return null;
            }
        };
    }

    boolean isDebuggerThread(Thread thread) {
        return thread == jdwpTransport || thread == commandProcessor;
    }

    private class CommandProcessorThread implements Runnable {

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                DebuggerCommand debuggerCommand = awaitNextCommand(); // blocking

                if (debuggerCommand != null) {
                    switch (debuggerCommand.kind) {
                        case SUBMIT_LINE_BREAKPOINT:
                            controller.submitLineBreakpoint(debuggerCommand);
                            break;
                        case SUBMIT_EXCEPTION_BREAKPOINT:
                            controller.submitExceptionBreakpoint(debuggerCommand);
                            break;
                        case STEP_OUT:
                            controller.stepOut(debuggerCommand.getRequestFilter());
                            break;
                        default:
                            break;
                    }
                    synchronized (debuggerCommand) {
                        debuggerCommand.markSubmitted();
                        debuggerCommand.notifyAll();
                    }
                }
            }
        }

        private DebuggerCommand awaitNextCommand() {
            DebuggerCommand debuggerCommand = null;
            try {
                debuggerCommand = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return debuggerCommand;
        }
    }

    private class JDWPTransportThread implements Runnable {
        private RequestedJDWPEvents requestedJDWPEvents = new RequestedJDWPEvents(controller);

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    processPacket(Packet.fromByteArray(connection.readPacket()));
                } catch (IOException e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        controller.warning(() -> "Failed to process jdwp packet with message: " + e.getMessage());
                    }
                } catch (ConnectionClosedException e) {
                    // we closed the session, so let the thread run dry
                }
            }
        }

        private void processPacket(Packet packet) {
            CommandResult result = null;
            try {
                if (packet.flags == Packet.Reply) {
                    // result packet from debugger!
                    controller.warning(() -> "Should not get any reply packet from debugger");
                } else {
                    // process a command packet from debugger
                    controller.fine(() -> "received command(" + packet.cmdSet + "." + packet.cmd + ")");

                    switch (packet.cmdSet) {
                        case JDWP.VirtualMachine.ID: {
                            switch (packet.cmd) {
                                case JDWP.VirtualMachine.VERSION.ID:
                                    result = JDWP.VirtualMachine.VERSION.createReply(packet, controller.getVirtualMachine());
                                    break;
                                case JDWP.VirtualMachine.CLASSES_BY_SIGNATURE.ID:
                                    result = JDWP.VirtualMachine.CLASSES_BY_SIGNATURE.createReply(packet, controller, context);
                                    break;
                                case JDWP.VirtualMachine.ALL_CLASSES.ID:
                                    result = JDWP.VirtualMachine.ALL_CLASSES.createReply(packet, context);
                                    break;
                                case JDWP.VirtualMachine.ALL_THREADS.ID:
                                    result = JDWP.VirtualMachine.ALL_THREADS.createReply(packet, context, controller);
                                    break;
                                case JDWP.VirtualMachine.TOP_LEVEL_THREAD_GROUPS.ID:
                                    result = JDWP.VirtualMachine.TOP_LEVEL_THREAD_GROUPS.createReply(packet, context);
                                    break;
                                case JDWP.VirtualMachine.DISPOSE.ID:
                                    result = JDWP.VirtualMachine.DISPOSE.createReply(packet, controller);
                                    break;
                                case JDWP.VirtualMachine.IDSIZES.ID:
                                    result = JDWP.VirtualMachine.IDSIZES.createReply(packet, controller.getVirtualMachine());
                                    break;
                                case JDWP.VirtualMachine.SUSPEND.ID:
                                    result = JDWP.VirtualMachine.SUSPEND.createReply(packet, controller);
                                    break;
                                case JDWP.VirtualMachine.RESUME.ID:
                                    result = JDWP.VirtualMachine.RESUME.createReply(packet, controller);
                                    break;
                                case JDWP.VirtualMachine.EXIT.ID:
                                    result = JDWP.VirtualMachine.EXIT.createReply(packet, context);
                                    break;
                                case JDWP.VirtualMachine.CREATE_STRING.ID:
                                    result = JDWP.VirtualMachine.CREATE_STRING.createReply(packet, context);
                                    break;
                                case JDWP.VirtualMachine.CAPABILITIES.ID:
                                    result = JDWP.VirtualMachine.CAPABILITIES.createReply(packet);
                                    break;
                                case JDWP.VirtualMachine.CLASS_PATHS.ID:
                                    result = JDWP.VirtualMachine.CLASS_PATHS.createReply(packet, context);
                                    break;
                                case JDWP.VirtualMachine.DISPOSE_OBJECTS.ID:
                                    result = JDWP.VirtualMachine.DISPOSE_OBJECTS.createReply(packet);
                                    break;
                                case JDWP.VirtualMachine.HOLD_EVENTS.ID:
                                    result = JDWP.VirtualMachine.HOLD_EVENTS.createReply(packet, controller);
                                    break;
                                case JDWP.VirtualMachine.RELEASE_EVENTS.ID:
                                    result = JDWP.VirtualMachine.RELEASE_EVENTS.createReply(packet, controller);
                                    break;
                                case JDWP.VirtualMachine.CAPABILITIES_NEW.ID:
                                    result = JDWP.VirtualMachine.CAPABILITIES_NEW.createReply(packet);
                                    break;
                                case JDWP.VirtualMachine.REDEFINE_CLASSES.ID:
                                    result = JDWP.VirtualMachine.REDEFINE_CLASSES.createReply(packet, controller);
                                    break;
                                case JDWP.VirtualMachine.SET_DEFAULT_STRATUM.ID:
                                    result = JDWP.VirtualMachine.SET_DEFAULT_STRATUM.createReply(packet);
                                    break;
                                case JDWP.VirtualMachine.ALL_CLASSES_WITH_GENERIC.ID:
                                    result = JDWP.VirtualMachine.ALL_CLASSES_WITH_GENERIC.createReply(packet, context);
                                    break;
                                case JDWP.VirtualMachine.INSTANCE_COUNTS.ID:
                                    result = JDWP.VirtualMachine.INSTANCE_COUNTS.createReply(packet);
                                    break;
                                case JDWP.VirtualMachine.ALL_MODULES.ID:
                                    result = JDWP.VirtualMachine.ALL_MODULES.createReply(packet, context);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        }
                        case JDWP.ReferenceType.ID: {
                            switch (packet.cmd) {
                                case JDWP.ReferenceType.SIGNATURE.ID:
                                    result = JDWP.ReferenceType.SIGNATURE.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.CLASSLOADER.ID:
                                    result = JDWP.ReferenceType.CLASSLOADER.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.MODIFIERS.ID:
                                    result = JDWP.ReferenceType.MODIFIERS.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.FIELDS.ID:
                                    result = JDWP.ReferenceType.FIELDS.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.METHODS.ID:
                                    result = JDWP.ReferenceType.METHODS.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.GET_VALUES.ID:
                                    result = JDWP.ReferenceType.GET_VALUES.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.SOURCE_FILE.ID:
                                    result = JDWP.ReferenceType.SOURCE_FILE.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.NESTED_TYPES.ID:
                                    result = JDWP.ReferenceType.NESTED_TYPES.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.STATUS.ID:
                                    result = JDWP.ReferenceType.STATUS.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.INTERFACES.ID:
                                    result = JDWP.ReferenceType.INTERFACES.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.CLASS_OBJECT.ID:
                                    result = JDWP.ReferenceType.CLASS_OBJECT.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.SOURCE_DEBUG_EXTENSION.ID:
                                    result = JDWP.ReferenceType.SOURCE_DEBUG_EXTENSION.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.SIGNATURE_WITH_GENERIC.ID:
                                    result = JDWP.ReferenceType.SIGNATURE_WITH_GENERIC.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.FIELDS_WITH_GENERIC.ID:
                                    result = JDWP.ReferenceType.FIELDS_WITH_GENERIC.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.METHODS_WITH_GENERIC.ID:
                                    result = JDWP.ReferenceType.METHODS_WITH_GENERIC.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.INSTANCES.ID:
                                    result = JDWP.ReferenceType.INSTANCES.createReply(packet);
                                    break;
                                case JDWP.ReferenceType.CLASS_FILE_VERSION.ID:
                                    result = JDWP.ReferenceType.CLASS_FILE_VERSION.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.CONSTANT_POOL.ID:
                                    result = JDWP.ReferenceType.CONSTANT_POOL.createReply(packet, context);
                                    break;
                                case JDWP.ReferenceType.MODULE.ID:
                                    result = JDWP.ReferenceType.MODULE.createReply(packet, context);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        }
                        case JDWP.ClassType.ID: {
                            switch (packet.cmd) {
                                case JDWP.ClassType.SUPERCLASS.ID:
                                    result = JDWP.ClassType.SUPERCLASS.createReply(packet, context);
                                    break;
                                case JDWP.ClassType.SET_VALUES.ID:
                                    result = JDWP.ClassType.SET_VALUES.createReply(packet, context);
                                    break;
                                case JDWP.ClassType.INVOKE_METHOD.ID:
                                    result = JDWP.ClassType.INVOKE_METHOD.createReply(packet, controller, DebuggerConnection.this);
                                    break;
                                case JDWP.ClassType.NEW_INSTANCE.ID:
                                    result = JDWP.ClassType.NEW_INSTANCE.createReply(packet, controller);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        }
                        case JDWP.ArrayType.ID: {
                            switch (packet.cmd) {
                                case JDWP.ArrayType.NEW_INSTANCE.ID:
                                    result = JDWP.ArrayType.NEW_INSTANCE.createReply(packet, context);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        }
                        case JDWP.InterfaceType.ID: {
                            switch (packet.cmd) {
                                case JDWP.InterfaceType.INVOKE_METHOD.ID:
                                    result = JDWP.InterfaceType.INVOKE_METHOD.createReply(packet, controller, DebuggerConnection.this);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        }
                        case JDWP.Methods.ID: {
                            switch (packet.cmd) {
                                case JDWP.Methods.LINE_TABLE.ID:
                                    result = JDWP.Methods.LINE_TABLE.createReply(packet, context);
                                    break;
                                case JDWP.Methods.VARIABLE_TABLE.ID:
                                    result = JDWP.Methods.VARIABLE_TABLE.createReply(packet, context);
                                    break;
                                case JDWP.Methods.BYTECODES.ID:
                                    result = JDWP.Methods.BYTECODES.createReply(packet, context);
                                    break;
                                case JDWP.Methods.IS_OBSOLETE.ID:
                                    result = JDWP.Methods.IS_OBSOLETE.createReply(packet, context);
                                    break;
                                case JDWP.Methods.VARIABLE_TABLE_WITH_GENERIC.ID:
                                    result = JDWP.Methods.VARIABLE_TABLE_WITH_GENERIC.createReply(packet, context);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        }
                        case JDWP.ObjectReference.ID: {
                            switch (packet.cmd) {
                                case JDWP.ObjectReference.REFERENCE_TYPE.ID:
                                    result = JDWP.ObjectReference.REFERENCE_TYPE.createReply(packet, context);
                                    break;
                                case JDWP.ObjectReference.GET_VALUES.ID:
                                    result = JDWP.ObjectReference.GET_VALUES.createReply(packet, context);
                                    break;
                                case JDWP.ObjectReference.SET_VALUES.ID:
                                    result = JDWP.ObjectReference.SET_VALUES.createReply(packet, context);
                                    break;
                                case JDWP.ObjectReference.MONITOR_INFO.ID:
                                    result = JDWP.ObjectReference.MONITOR_INFO.createReply(packet, controller);
                                    break;
                                case JDWP.ObjectReference.INVOKE_METHOD.ID:
                                    result = JDWP.ObjectReference.INVOKE_METHOD.createReply(packet, controller, DebuggerConnection.this);
                                    break;
                                case JDWP.ObjectReference.DISABLE_COLLECTION.ID:
                                    result = JDWP.ObjectReference.DISABLE_COLLECTION.createReply(packet, controller);
                                    break;
                                case JDWP.ObjectReference.ENABLE_COLLECTION.ID:
                                    result = JDWP.ObjectReference.ENABLE_COLLECTION.createReply(packet, controller);
                                    break;
                                case JDWP.ObjectReference.IS_COLLECTED.ID:
                                    result = JDWP.ObjectReference.IS_COLLECTED.createReply(packet, context);
                                    break;
                                case JDWP.ObjectReference.REFERRING_OBJECTS.ID:
                                    result = JDWP.ObjectReference.REFERRING_OBJECTS.createReply(packet);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        }
                        case JDWP.StringReference.ID: {
                            switch (packet.cmd) {
                                case JDWP.StringReference.VALUE.ID:
                                    result = JDWP.StringReference.VALUE.createReply(packet, context);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        }
                        case JDWP.ThreadReference.ID:
                            switch (packet.cmd) {
                                case JDWP.ThreadReference.NAME.ID:
                                    result = JDWP.ThreadReference.NAME.createReply(packet, controller, context);
                                    break;
                                case JDWP.ThreadReference.SUSPEND.ID:
                                    result = JDWP.ThreadReference.SUSPEND.createReply(packet, controller);
                                    break;
                                case JDWP.ThreadReference.RESUME.ID:
                                    result = JDWP.ThreadReference.RESUME.createReply(packet, controller);
                                    break;
                                case JDWP.ThreadReference.STATUS.ID:
                                    result = JDWP.ThreadReference.STATUS.createReply(packet, controller);
                                    break;
                                case JDWP.ThreadReference.THREAD_GROUP.ID:
                                    result = JDWP.ThreadReference.THREAD_GROUP.createReply(packet, context);
                                    break;
                                case JDWP.ThreadReference.FRAMES.ID:
                                    result = JDWP.ThreadReference.FRAMES.createReply(packet, controller);
                                    break;
                                case JDWP.ThreadReference.FRAME_COUNT.ID:
                                    result = JDWP.ThreadReference.FRAME_COUNT.createReply(packet, controller);
                                    break;
                                case JDWP.ThreadReference.OWNED_MONITORS.ID:
                                    result = JDWP.ThreadReference.OWNED_MONITORS.createReply(packet, controller);
                                    break;
                                case JDWP.ThreadReference.CURRENT_CONTENDED_MONITOR.ID:
                                    result = JDWP.ThreadReference.CURRENT_CONTENDED_MONITOR.createReply(packet, controller);
                                    break;
                                case JDWP.ThreadReference.STOP.ID:
                                    result = JDWP.ThreadReference.STOP.createReply(packet, context);
                                    break;
                                case JDWP.ThreadReference.INTERRUPT.ID:
                                    result = JDWP.ThreadReference.INTERRUPT.createReply(packet, context);
                                    break;
                                case JDWP.ThreadReference.SUSPEND_COUNT.ID:
                                    result = JDWP.ThreadReference.SUSPEND_COUNT.createReply(packet, controller);
                                    break;
                                case JDWP.ThreadReference.OWNED_MONITORS_STACK_DEPTH_INFO.ID:
                                    result = JDWP.ThreadReference.OWNED_MONITORS_STACK_DEPTH_INFO.createReply(packet, controller);
                                    break;
                                case JDWP.ThreadReference.FORCE_EARLY_RETURN.ID:
                                    result = JDWP.ThreadReference.FORCE_EARLY_RETURN.createReply(packet, controller);
                                    break;
                                case JDWP.ThreadReference.IS_VIRTUAL.ID:
                                    result = JDWP.ThreadReference.IS_VIRTUAL.createReply(packet, controller);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        case JDWP.ThreadGroupReference.ID:
                            switch (packet.cmd) {
                                case JDWP.ThreadGroupReference.NAME.ID:
                                    result = JDWP.ThreadGroupReference.NAME.createReply(packet, context);
                                    break;
                                case JDWP.ThreadGroupReference.PARENT.ID:
                                    result = JDWP.ThreadGroupReference.PARENT.createReply(packet, context);
                                    break;
                                case JDWP.ThreadGroupReference.CHILDREN.ID:
                                    result = JDWP.ThreadGroupReference.CHILDREN.createReply(packet, context, controller);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        case JDWP.ArrayReference.ID: {
                            switch (packet.cmd) {
                                case JDWP.ArrayReference.LENGTH.ID:
                                    result = JDWP.ArrayReference.LENGTH.createReply(packet, context);
                                    break;
                                case JDWP.ArrayReference.GET_VALUES.ID:
                                    result = JDWP.ArrayReference.GET_VALUES.createReply(packet, context);
                                    break;
                                case JDWP.ArrayReference.SET_VALUES.ID:
                                    result = JDWP.ArrayReference.SET_VALUES.createReply(packet, context);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        }
                        case JDWP.ClassLoaderReference.ID: {
                            switch (packet.cmd) {
                                case JDWP.ClassLoaderReference.VISIBLE_CLASSES.ID:
                                    result = JDWP.ClassLoaderReference.VISIBLE_CLASSES.createReply(packet, context);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        }
                        case JDWP.EventRequest.ID: {
                            switch (packet.cmd) {
                                case JDWP.EventRequest.SET.ID:
                                    result = requestedJDWPEvents.registerEvent(packet, DebuggerConnection.this);
                                    break;
                                case JDWP.EventRequest.CLEAR.ID:
                                    result = requestedJDWPEvents.clearRequest(packet);
                                    break;
                                case JDWP.EventRequest.CLEAR_ALL_BREAKPOINTS.ID:
                                    result = requestedJDWPEvents.clearAllRequests(packet);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        }
                        case JDWP.StackFrame.ID: {
                            switch (packet.cmd) {
                                case JDWP.StackFrame.GET_VALUES.ID:
                                    result = JDWP.StackFrame.GET_VALUES.createReply(packet, context);
                                    break;
                                case JDWP.StackFrame.SET_VALUES.ID:
                                    result = JDWP.StackFrame.SET_VALUES.createReply(packet, context);
                                    break;
                                case JDWP.StackFrame.THIS_OBJECT.ID:
                                    result = JDWP.StackFrame.THIS_OBJECT.createReply(packet, controller);
                                    break;
                                case JDWP.StackFrame.POP_FRAMES.ID:
                                    result = JDWP.StackFrame.POP_FRAMES.createReply(packet, controller);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        }
                        case JDWP.ClassObjectReference.ID: {
                            switch (packet.cmd) {
                                case JDWP.ClassObjectReference.REFLECTED_TYPE.ID:
                                    result = JDWP.ClassObjectReference.REFLECTED_TYPE.createReply(packet, context);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        }
                        case JDWP.ModuleReference.ID: {
                            switch (packet.cmd) {
                                case JDWP.ModuleReference.NAME.ID:
                                    result = JDWP.ModuleReference.NAME.createReply(packet, context);
                                    break;
                                case JDWP.ModuleReference.CLASSLOADER.ID:
                                    result = JDWP.ModuleReference.CLASSLOADER.createReply(packet, context);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        }
                        case JDWP.Event.ID: {
                            switch (packet.cmd) {
                                case JDWP.Event.COMPOSITE.ID:
                                    result = JDWP.Event.COMPOSITE.createReply(packet);
                                    break;
                                default:
                                    result = unknownCommand(packet);
                                    break;
                            }
                            break;
                        }
                        default:
                            result = unknownCommandSet(packet);
                            break;
                    }
                }
                handleReply(packet, result);
            } catch (Throwable t) {
                controller.severe("Internal error while processing packet", t);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.errorCode(ErrorCodes.INTERNAL);
                handleReply(packet, new CommandResult(reply));
            }
        }
    }

    private CommandResult unknownCommandSet(Packet packet) {
        controller.warning(() -> "Unknown command set: " + packet.cmdSet);
        return notImplemented(packet);
    }

    private CommandResult unknownCommand(Packet packet) {
        controller.warning(() -> "Unknown command " + packet.cmd + " in command set " + packet.cmdSet);
        return notImplemented(packet);
    }

    private static CommandResult notImplemented(Packet packet) {
        PacketStream reply = new PacketStream().replyPacket().id(packet.id);
        reply.errorCode(ErrorCodes.NOT_IMPLEMENTED);
        return new CommandResult(reply);
    }

    void handleReply(Packet packet, CommandResult result) {
        if (result == null) {
            return;
        }
        // run pre futures before sending the reply
        if (result.getPreFutures() != null) {
            try {
                for (Callable<Void> future : result.getPreFutures()) {
                    if (future != null) {
                        future.call();
                    }
                }
            } catch (Exception e) {
                controller.warning(() -> "Failed to run future for command(" + packet.cmdSet + "." + packet.cmd + ")");
            }
        }
        if (result.getReply() != null) {
            controller.fine(() -> "replying to command(" + packet.cmdSet + "." + packet.cmd + ")");
            connection.queuePacket(result.getReply());
        } else {
            controller.warning(() -> "no result for command(" + packet.cmdSet + "." + packet.cmd + ")");
        }
        // run post futures after sending the reply
        if (result.getPostFutures() != null) {
            try {
                for (Callable<Void> future : result.getPostFutures()) {
                    if (future != null) {
                        future.call();
                    }
                }
            } catch (Exception e) {
                controller.severe(() -> "Failed to run future for command(" + packet.cmdSet + "." + packet.cmd + ")");
            }
        }
    }
}
