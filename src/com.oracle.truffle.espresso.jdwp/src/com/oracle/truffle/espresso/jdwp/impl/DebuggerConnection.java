/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.jdwp.api.BreakpointInfo;
import com.oracle.truffle.espresso.jdwp.api.LineBreakpointInfo;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;

import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

public class DebuggerConnection implements JDWPCommands {

    private final JDWPDebuggerController controller;
    private final JDWPContext context;
    private final SocketConnection connection;
    private final BlockingQueue<DebuggerCommand> queue = new ArrayBlockingQueue<>(512);
    private Thread commandProcessor;
    private Thread jdwpTransport;

    public DebuggerConnection(SocketConnection connection, JDWPDebuggerController controller) {
        this.connection = connection;
        this.controller = controller;
        this.context = controller.getContext();
    }

    public void doProcessCommands(boolean suspend, Collection<Thread> activeThreads) {
        // fire up two threads, one for the low-level connection to receive packets
        // and one for processing the debugger commands from a queue
        commandProcessor = new Thread(new CommandProcessorThread(), "jdwp-command-processor");
        commandProcessor.setDaemon(true);
        commandProcessor.start();
        activeThreads.add(commandProcessor);

        jdwpTransport = new Thread(new JDWPTransportThread(suspend), "jdwp-transport");
        jdwpTransport.setDaemon(true);
        jdwpTransport.start();
        activeThreads.add(jdwpTransport);
    }

    public void close() {
        try {
            connection.close();
        } catch (IOException e) {
            throw new RuntimeException("Closing socket connection failed", e);
        }
    }

    @Override
    public void stepInto(Object thread, int requestId) {
        DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.STEP_INTO, thread);
        controller.setCommandRequestId(thread, requestId);
        queue.add(debuggerCommand);
    }

    @Override
    public void stepOver(Object thread, int requestId) {
        DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.STEP_OVER, thread);
        controller.setCommandRequestId(thread, requestId);
        queue.add(debuggerCommand);
    }

    @Override
    public void stepOut(Object thread, int requestId) {
        DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.STEP_OUT, thread);
        controller.setCommandRequestId(thread, requestId);
        queue.add(debuggerCommand);
    }

    @Override
    public Callable<Void> createLineBreakpointCommand(BreakpointInfo info) {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                LineBreakpointInfo lineInfo = (LineBreakpointInfo) info;
                DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.SUBMIT_BREAKPOINT, null);
                debuggerCommand.setSourceLocation(new SourceLocation(lineInfo.getSlashName(), (int) lineInfo.getLine(), context));
                debuggerCommand.setBreakpointInfo(info);
                queue.add(debuggerCommand);
                return null;
            }
        };
    }

    @Override
    public Callable<Void> createExceptionBreakpoint(BreakpointInfo info) {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.SUBMIT_EXCEPTION_BREAKPOINT, null);
                debuggerCommand.setBreakpointInfo(info);
                queue.add(debuggerCommand);
                return null;
            }
        };
    }

    private class CommandProcessorThread implements Runnable {

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                DebuggerCommand debuggerCommand = awaitNextCommand(); // blocking
                //System.out.println("got a " + debuggerCommand.kind + " command from debugger");

                if (debuggerCommand != null) {
                    Object thread = debuggerCommand.getThread();
                    switch (debuggerCommand.kind) {
                        case STEP_INTO: controller.stepInto(thread); break;
                        case STEP_OVER: controller.stepOver(thread); break;
                        case STEP_OUT: controller.stepOut(thread); break;
                        case SUBMIT_BREAKPOINT: controller.submitLineBreakpoint(debuggerCommand); break;
                        case SUBMIT_EXCEPTION_BREAKPOINT: controller.submitExceptionBreakpoint(debuggerCommand); break;
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

        @CompilerDirectives.CompilationFinal
        private boolean started;
        private RequestedJDWPEvents requestedJDWPEvents = new RequestedJDWPEvents(context, connection, controller);
        // constant used to allow for initial startup sequence debugger commands to occur before
        // waking up the main Espresso startup thread
        private static final int GRACE_PERIOD = 100;

        public JDWPTransportThread(boolean suspend) {
            this.started = !suspend;
        }

        @Override
        public void run() {
            long time = -1;
            long limit = 0;

            while(!Thread.currentThread().isInterrupted()) {
                try {
                    if (!started) {
                        // in startup sequence
                        if (time == -1) {
                            // first packet processed
                            processPacket(Packet.fromByteArray(connection.readPacket()));
                            time = System.currentTimeMillis();
                            limit = time + GRACE_PERIOD;
                        } else {
                            long currentTime = System.currentTimeMillis();
                            if (currentTime > limit) {
                                started = true;
                                // allow the main thread to continue starting up the program
                                synchronized (JDWPInstrument.suspendStartupLock) {
                                    JDWPInstrument.suspendStartupLock.notifyAll();
                                }
                                processPacket(Packet.fromByteArray(connection.readPacket()));
                            } else {
                                // check if a packet is available
                                if(connection.isAvailable()) {
                                    processPacket(Packet.fromByteArray(connection.readPacket()));
                                    time = System.currentTimeMillis();
                                    limit = time + GRACE_PERIOD;
                                } else {
                                    try {
                                        Thread.sleep(10);
                                    } catch (InterruptedException e) {
                                        Thread.interrupted();
                                    }
                                }
                            }
                        }
                    } else {
                        processPacket(Packet.fromByteArray(connection.readPacket()));
                    }
                } catch (IOException e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        throw new RuntimeException("Failed to process jdwp packet", e);
                    }
                } catch (ConnectionClosedException e) {
                    // we closed the session, so let the thread run dry
                }
            }
        }

        private void processPacket(Packet packet) {
            JDWPResult result = null;

            if (packet.flags == Packet.Reply) {
                // result packet from debugger!
                System.err.println("Should not get any reply packet from debugger");
            } else {
                // process a command packet from debugger
                if (JDWPDebuggerController.isDebug(JDWPDebuggerController.Debug.PACKET)) {
                    System.out.println("received command(" + packet.cmdSet + "." + packet.cmd + ")");
                }
                switch (packet.cmdSet) {
                    case JDWP.VirtualMachine.ID: {
                        switch (packet.cmd) {
                            case JDWP.VirtualMachine.VERSION.ID:
                                result = JDWP.VirtualMachine.VERSION.createReply(packet, context.getVirtualMachine());
                                break;
                            case JDWP.VirtualMachine.CLASSES_BY_SIGNATURE.ID:
                                result = JDWP.VirtualMachine.CLASSES_BY_SIGNATURE.createReply(packet, context);
                                break;
                            case JDWP.VirtualMachine.ALL_THREADS.ID:
                                result = JDWP.VirtualMachine.ALL_THREADS.createReply(packet, context);
                                break;
                            case JDWP.VirtualMachine.DISPOSE.ID:
                                result = JDWP.VirtualMachine.DISPOSE.createReply(packet, controller);
                                break;
                            case JDWP.VirtualMachine.IDSIZES.ID:
                                result = JDWP.VirtualMachine.IDSIZES.createReply(packet, context.getVirtualMachine());
                                break;
                            case JDWP.VirtualMachine.SUSPEND.ID:
                                result = JDWP.VirtualMachine.SUSPEND.createReply(packet, controller);
                                break;
                            case JDWP.VirtualMachine.RESUME.ID:
                                result = JDWP.VirtualMachine.RESUME.createReply(packet, controller);
                                break;
                            case JDWP.VirtualMachine.CREATE_STRING.ID:
                                result = JDWP.VirtualMachine.CREATE_STRING.createReply(packet, context);
                                break;
                            case JDWP.VirtualMachine.CAPABILITIES.ID:
                                result = JDWP.VirtualMachine.CAPABILITIES.createReply(packet);
                                break;
                            case JDWP.VirtualMachine.CAPABILITIES_NEW.ID:
                                result = JDWP.VirtualMachine.CAPABILITIES_NEW.createReply(packet);
                                break;
                            case JDWP.VirtualMachine.SET_DEFAULT_STRATUM.ID:
                                result = JDWP.VirtualMachine.SET_DEFAULT_STRATUM.createReply(packet);
                                break;
                            case JDWP.VirtualMachine.INSTANCE_COUNTS.ID:
                                result = JDWP.VirtualMachine.INSTANCE_COUNTS.createReply(packet);
                                break;
                            default:
                                break;
                        }
                        break;
                    }
                    case JDWP.ReferenceType.ID: {
                        switch (packet.cmd) {
                            case JDWP.ReferenceType.CLASSLOADER.ID:
                                result = JDWP.ReferenceType.CLASSLOADER.createReply(packet, context);
                                break;
                            case JDWP.ReferenceType.GET_VALUES.ID:
                                result = JDWP.ReferenceType.GET_VALUES.createReply(packet, context);
                                break;
                            case JDWP.ReferenceType.SOURCE_FILE.ID:
                                result = JDWP.ReferenceType.SOURCE_FILE.createReply(packet, context);
                                break;
                            case JDWP.ReferenceType.INTERFACES.ID:
                                result = JDWP.ReferenceType.INTERFACES.createReply(packet, context);
                                break;
                            case JDWP.ReferenceType.CLASS_OBJECT.ID:
                                result = JDWP.ReferenceType.CLASS_OBJECT.createReply(packet, context);
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
                            //case JDWP.ReferenceType.CONSTANT_POOL.ID:
                            //    result = JDWP.ReferenceType.CONSTANT_POOL.createReply(packet);
                            //    break;
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
                        }
                        break;
                    }
                    case JDWP.Methods.ID: {
                        switch (packet.cmd) {
                            case JDWP.Methods.LINE_TABLE.ID:
                                result = JDWP.Methods.LINE_TABLE.createReply(packet, context);
                                break;
                            case JDWP.Methods.BYTECODES.ID:
                                result = JDWP.Methods.BYTECODES.createReply(packet, context);
                                break;
                            case JDWP.Methods.VARIABLE_TABLE_WITH_GENERIC.ID:
                                result = JDWP.Methods.VARIABLE_TABLE_WITH_GENERIC.createReply(packet, context);
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
                            case JDWP.ObjectReference.INVOKE_METHOD.ID:
                                result = JDWP.ObjectReference.INVOKE_METHOD.createReply(packet, controller);
                                break;
                            case JDWP.ObjectReference.DISABLE_COLLECTION.ID:
                                result = JDWP.ObjectReference.DISABLE_COLLECTION.createReply(packet, context);
                                break;
                            case JDWP.ObjectReference.ENABLE_COLLECTION.ID:
                                result = JDWP.ObjectReference.ENABLE_COLLECTION.createReply(packet, context);
                                break;
                            case JDWP.ObjectReference.IS_COLLECTED.ID:
                                result = JDWP.ObjectReference.IS_COLLECTED.createReply(packet, context);
                                break;
                        }
                        break;
                    }
                    case JDWP.StringReference.ID: {
                        switch (packet.cmd) {
                            case JDWP.StringReference.VALUE.ID:
                                result = JDWP.StringReference.VALUE.createReply(packet, context);
                                break;
                        }
                        break;
                    }
                    case JDWP.ThreadReference.ID:
                        switch (packet.cmd) {
                            case JDWP.ThreadReference.NAME.ID:
                                result = JDWP.ThreadReference.NAME.createReply(packet, context);
                                break;
                            case JDWP.ThreadReference.SUSPEND.ID:
                                result = JDWP.ThreadReference.SUSPEND.createReply(packet, controller);
                                break;
                            case JDWP.ThreadReference.RESUME.ID:
                                result = JDWP.ThreadReference.RESUME.createReply(packet, controller);
                                break;
                            case JDWP.ThreadReference.STATUS.ID:
                                result = JDWP.ThreadReference.STATUS.createReply(packet, context);
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
                            case JDWP.ThreadReference.SUSPEND_COUNT.ID:
                                result = JDWP.ThreadReference.SUSPEND_COUNT.createReply(packet, context);
                                break;
                        }
                        break;
                    case JDWP.ThreadGroupReference.ID:
                        switch (packet.cmd) {
                            case JDWP.ThreadGroupReference.NAME.ID:
                                result = JDWP.ThreadGroupReference.NAME.createReply(packet, context);
                        }
                        break;
                    case JDWP.ArrayReference.ID: {
                        switch (packet.cmd) {
                            case JDWP.ArrayReference.LENGTH.ID: {
                                result = JDWP.ArrayReference.LENGTH.createReply(packet, context);
                                break;
                            }
                            case JDWP.ArrayReference.GET_VALUES.ID: {
                                result = JDWP.ArrayReference.GET_VALUES.createReply(packet, context);
                                break;
                            }
                            case JDWP.ArrayReference.SET_VALUES.ID: {
                                result = JDWP.ArrayReference.SET_VALUES.createReply(packet, context);
                                break;
                            }
                            default:
                                break;
                        }
                        break;
                    }
                    case JDWP.ClassLoaderReference.ID: {
                        switch (packet.cmd) {
                            case JDWP.ClassLoaderReference.VISIBLE_CLASSES.ID: {
                                result = JDWP.ClassLoaderReference.VISIBLE_CLASSES.createReply(packet, context);
                                break;
                            }
                        }
                        break;
                    }
                    case JDWP.EventRequest.ID: {
                        switch (packet.cmd) {
                            case JDWP.EventRequest.SET.ID: {
                                result = requestedJDWPEvents.registerEvent(packet, DebuggerConnection.this, context);
                                break;
                            }
                            case JDWP.EventRequest.CLEAR.ID: {
                                result = requestedJDWPEvents.clearRequest(packet);
                                break;
                            }
                            default:
                                break;
                        }
                        break;
                    }
                    case JDWP.StackFrame.ID: {
                        switch (packet.cmd) {
                            case JDWP.StackFrame.GET_VALUES.ID: {
                                result = JDWP.StackFrame.GET_VALUES.createReply(packet, context);
                                break;
                            }
                            case JDWP.StackFrame.SET_VALUES.ID: {
                                result = JDWP.StackFrame.SET_VALUES.createReply(packet, context);
                                break;
                            }
                            case JDWP.StackFrame.THIS_OBJECT.ID: {
                                result = JDWP.StackFrame.THIS_OBJECT.createReply(packet, context);
                                break;
                            }
                            default:
                                break;
                        }
                        break;
                    }
                    case JDWP.ClassObjectReference.ID: {
                        switch (packet.cmd) {
                            case JDWP.ClassObjectReference.REFLECTED_TYPE.ID: {
                                result = JDWP.ClassObjectReference.REFLECTED_TYPE.createReply(packet, context);
                                break;
                            }
                            default:
                                break;
                        }
                        break;
                    }
                    default:
                        break;
                }
            }
            if (result != null && result.getReply() != null) {
                if (JDWPDebuggerController.isDebug(JDWPDebuggerController.Debug.PACKET)) {
                    System.out.println("replying to command(" + packet.cmdSet + "." + packet.cmd + ")");
                }
                connection.queuePacket(result.getReply());
            } else {
                System.err.println("no result for command(" + packet.cmdSet + "." + packet.cmd + ")");
            }

            if (result != null && result.getFutures() != null) {
                try {
                    for (Callable<Void> future : result.getFutures()) {
                        if (future != null) {
                            future.call();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
