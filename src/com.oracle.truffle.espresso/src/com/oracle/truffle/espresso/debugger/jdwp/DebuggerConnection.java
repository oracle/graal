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
package com.oracle.truffle.espresso.debugger.jdwp;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.debugger.BreakpointInfo;
import com.oracle.truffle.espresso.debugger.SourceLocation;
import com.oracle.truffle.espresso.descriptors.Symbol;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class DebuggerConnection implements JDWPCommands {

    private final JDWPDebuggerController controller;
    private final SocketConnection connection;
    private final BlockingQueue<DebuggerCommand> queue = new ArrayBlockingQueue<>(512);

    public DebuggerConnection(SocketConnection connection, JDWPDebuggerController controller) {
        this.connection = connection;
        this.controller = controller;
    }

    public void doProcessCommands(boolean suspend) {
        // fire up two threads, one for the low-level connection to receive packets
        // and one for processing the debugger commands from a queue
        Thread commandProcessor = new Thread(new CommandProcessorThread(), "jdwp-command-processor");
        commandProcessor.setDaemon(true);
        commandProcessor.start();

        Thread jdwpTransport = new Thread(new JDWPTransportThread(suspend), "jdwp-transport");
        jdwpTransport.setDaemon(true);
        jdwpTransport.start();
    }

    @Override
    public void stepInto(int requestId) {
        DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.STEP_INTO);
        controller.setCommandRequestId(requestId);
        queue.add(debuggerCommand);
    }

    @Override
    public void stepOver(int requestId) {
        DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.STEP_OVER);
        controller.setCommandRequestId(requestId);
        queue.add(debuggerCommand);
    }

    @Override
    public void stepOut(int requestId) {
        DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.STEP_OUT);
        controller.setCommandRequestId(requestId);
        queue.add(debuggerCommand);
    }

    @Override
    public void createLineBreakpointCommand(String slashClassName, int line, byte suspendPolicy, BreakpointInfo info) {
        DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.SUBMIT_BREAKPOINT);
        Symbol<Symbol.Type> type = controller.getContext().getTypes().fromClassGetName(slashClassName);
        debuggerCommand.setSourceLocation(new SourceLocation(type, line, controller.getContext()));
        debuggerCommand.setBreakpointInfo(info);
        queue.add(debuggerCommand);
    }
    @Override
    public void createStepIntoSpecificCommand(String slashClassName, int line, byte suspendPolicy) {
        DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.STEP_INTO_SPECIFIC);
        Symbol<Symbol.Type> type = controller.getContext().getTypes().fromClassGetName(slashClassName);
        debuggerCommand.setSourceLocation(new SourceLocation(type, line, controller.getContext()));
        queue.add(debuggerCommand);
    }

    private class CommandProcessorThread implements Runnable {

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                DebuggerCommand debuggerCommand = awaitNextCommand(); // blocking
                //System.out.println("got a " + debuggerCommand.kind + " command from debugger");

                if (debuggerCommand != null) {
                    switch (debuggerCommand.kind) {
                        case STEP_INTO: controller.stepInto(); break;
                        case STEP_OVER: controller.stepOver(); break;
                        case STEP_OUT: controller.stepOut(); break;
                        case SUBMIT_BREAKPOINT: controller.submitLineBreakpoint(debuggerCommand); break;
                        case STEP_INTO_SPECIFIC: controller.stepIntoSpecific(debuggerCommand.getSourceLocation()); break;
                        case RESUME: controller.resume(); break;
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
        private RequestedJDWPEvents requestedJDWPEvents = new RequestedJDWPEvents(connection);
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
                                synchronized (JDWP.suspenStartupLock) {
                                    JDWP.suspenStartupLock.notifyAll();
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
                    // blocking call
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void processPacket(Packet packet) {
            PacketStream reply = null;

            if (packet.flags == Packet.Reply) {
                // reply packet from debugger
                System.out.println("Reply packet from debugger");
            } else {
                // process a command packet from debugger
                //System.out.println("received command(" + packet.cmdSet + "." + packet.cmd + ")");
                switch (packet.cmdSet) {
                    case JDWP.VirtualMachine.ID: {
                        switch (packet.cmd) {
                            case JDWP.VirtualMachine.VERSION.ID:
                                reply = JDWP.VirtualMachine.VERSION.createReply(packet);
                                break;
                            case JDWP.VirtualMachine.CLASSES_BY_SIGNATURE.ID:
                                reply = JDWP.VirtualMachine.CLASSES_BY_SIGNATURE.createReply(packet, controller);
                                break;
                            case JDWP.VirtualMachine.ALL_THREADS.ID:
                                reply = JDWP.VirtualMachine.ALL_THREADS.createReply(packet, controller);
                                break;
                            case JDWP.VirtualMachine.IDSIZES.ID:
                                reply = JDWP.VirtualMachine.IDSIZES.createReply(packet);
                                break;
                            case JDWP.VirtualMachine.RESUME.ID:
                                reply = JDWP.VirtualMachine.RESUME.createReply(packet, controller);
                                break;
                            case JDWP.VirtualMachine.CREATE_STRING.ID:
                                reply = JDWP.VirtualMachine.CREATE_STRING.createReply(packet, controller);
                                break;
                            case JDWP.VirtualMachine.CAPABILITIES.ID:
                                reply = JDWP.VirtualMachine.CAPABILITIES.createReply(packet);
                                break;
                            case JDWP.VirtualMachine.CAPABILITIES_NEW.ID:
                                reply = JDWP.VirtualMachine.CAPABILITIES_NEW.createReply(packet);
                                break;
                            default:
                                break;
                        }
                        break;
                    }
                    case JDWP.ReferenceType.ID: {
                        switch (packet.cmd) {
                            case JDWP.ReferenceType.CLASSLOADER.ID:
                                reply = JDWP.ReferenceType.CLASSLOADER.createReply(packet);
                                break;
                            case JDWP.ReferenceType.GET_VALUES.ID:
                                reply = JDWP.ReferenceType.GET_VALUES.createReply(packet);
                                break;
                            case JDWP.ReferenceType.SOURCE_FILE.ID:
                                reply = JDWP.ReferenceType.SOURCE_FILE.createReply(packet);
                                break;
                            case JDWP.ReferenceType.INTERFACES.ID:
                                reply = JDWP.ReferenceType.INTERFACES.createReply(packet);
                                break;
                            case JDWP.ReferenceType.CLASS_OBJECT.ID:
                                reply = JDWP.ReferenceType.CLASS_OBJECT.createReply(packet);
                                break;
                            case JDWP.ReferenceType.SIGNATURE_WITH_GENERIC.ID:
                                reply = JDWP.ReferenceType.SIGNATURE_WITH_GENERIC.createReply(packet);
                                break;
                            case JDWP.ReferenceType.FIELDS_WITH_GENERIC.ID:
                                reply = JDWP.ReferenceType.FIELDS_WITH_GENERIC.createReply(packet);
                                break;
                            case JDWP.ReferenceType.METHODS_WITH_GENERIC.ID:
                                reply = JDWP.ReferenceType.METHODS_WITH_GENERIC.createReply(packet);
                                break;
                            //case JDWP.ReferenceType.CONSTANT_POOL.ID:
                            //    reply = JDWP.ReferenceType.CONSTANT_POOL.createReply(packet);
                            //    break;
                        }
                        break;
                    }
                    case JDWP.ClassType.ID: {
                        switch (packet.cmd) {
                            case JDWP.ClassType.SUPERCLASS.ID:
                                reply = JDWP.ClassType.SUPERCLASS.createReply(packet);
                                break;
                            case JDWP.ClassType.SET_VALUES.ID:
                                reply = JDWP.ClassType.SET_VALUES.createReply(packet);
                                break;
                        }
                        break;
                    }
                    case JDWP.METHOD.ID: {
                        switch (packet.cmd) {
                            case JDWP.METHOD.LINE_TABLE.ID:
                                reply = JDWP.METHOD.LINE_TABLE.createReply(packet);
                                break;
                            case JDWP.METHOD.BYTECODES.ID:
                                reply = JDWP.METHOD.BYTECODES.createReply(packet);
                                break;
                            case JDWP.METHOD.VARIABLE_TABLE_WITH_GENERIC.ID:
                                reply = JDWP.METHOD.VARIABLE_TABLE_WITH_GENERIC.createReply(packet);
                                break;
                        }
                        break;
                    }
                    case JDWP.ObjectReference.ID: {
                        switch (packet.cmd) {
                            case JDWP.ObjectReference.REFERENCE_TYPE.ID:
                                reply = JDWP.ObjectReference.REFERENCE_TYPE.createReply(packet);
                                break;
                            case JDWP.ObjectReference.GET_VALUES.ID:
                                reply = JDWP.ObjectReference.GET_VALUES.createReply(packet);
                                break;
                            case JDWP.ObjectReference.SET_VALUES.ID:
                                reply = JDWP.ObjectReference.SET_VALUES.createReply(packet);
                                break;
                            case JDWP.ObjectReference.INVOKE_METHOD.ID:
                                reply = JDWP.ObjectReference.INVOKE_METHOD.createReply(packet);
                                break;
                            case JDWP.ObjectReference.IS_COLLECTED.ID:
                                reply = JDWP.ObjectReference.IS_COLLECTED.createReply(packet);
                                break;
                        }
                        break;
                    }
                    case JDWP.STRING_REFERENCE.ID: {
                        switch (packet.cmd) {
                            case JDWP.STRING_REFERENCE.VALUE.ID:
                                reply = JDWP.STRING_REFERENCE.VALUE.createReply(packet, controller.getContext());
                                break;
                        }
                        break;
                    }
                    case JDWP.THREAD_REFERENCE.ID:
                        switch (packet.cmd) {
                            case JDWP.THREAD_REFERENCE.NAME.ID:
                                reply = JDWP.THREAD_REFERENCE.NAME.createReply(packet, controller.getContext());
                                break;
                            case JDWP.THREAD_REFERENCE.RESUME.ID:
                                reply = JDWP.THREAD_REFERENCE.RESUME.createReply(packet, controller);
                                break;
                            case JDWP.THREAD_REFERENCE.STATUS.ID:
                                reply = JDWP.THREAD_REFERENCE.STATUS.createReply(packet, controller.getContext());
                                break;
                            case JDWP.THREAD_REFERENCE.THREAD_GROUP.ID:
                                reply = JDWP.THREAD_REFERENCE.THREAD_GROUP.createReply(packet, controller.getContext());
                                break;
                            case JDWP.THREAD_REFERENCE.FRAMES.ID:
                                reply = JDWP.THREAD_REFERENCE.FRAMES.createReply(packet, controller);
                                break;
                            case JDWP.THREAD_REFERENCE.FRAME_COUNT.ID:
                                reply = JDWP.THREAD_REFERENCE.FRAME_COUNT.createReply(packet, controller);
                                break;
                            case JDWP.THREAD_REFERENCE.SUSPEND_COUNT.ID:
                                reply = JDWP.THREAD_REFERENCE.SUSPEND_COUNT.createReply(packet);
                                break;
                        }
                        break;
                    case JDWP.THREAD_GROUP_REFERENCE.ID:
                        switch (packet.cmd) {
                            case JDWP.THREAD_GROUP_REFERENCE.NAME.ID:
                                reply = JDWP.THREAD_GROUP_REFERENCE.NAME.createReply(packet, controller.getContext());
                        }
                        break;
                    case JDWP.ArrayReference.ID: {
                        switch (packet.cmd) {
                            case JDWP.ArrayReference.LENGTH.ID: {
                                reply = JDWP.ArrayReference.LENGTH.createReply(packet);
                                break;
                            }
                            case JDWP.ArrayReference.GET_VALUES.ID: {
                                reply = JDWP.ArrayReference.GET_VALUES.createReply(packet);
                                break;
                            }
                            case JDWP.ArrayReference.SET_VALUES.ID: {
                                reply = JDWP.ArrayReference.SET_VALUES.createReply(packet, controller.getContext().getMeta());
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
                                reply = JDWP.ClassLoaderReference.VISIBLE_CLASSES.createReply(packet, controller.getContext());
                                break;
                            }
                        }
                        break;
                    }
                    case JDWP.EventRequest.ID: {
                        switch (packet.cmd) {
                            case JDWP.EventRequest.SET.ID: {
                                reply = requestedJDWPEvents.registerEvent(packet, DebuggerConnection.this);
                                break;
                            }
                            case JDWP.EventRequest.CLEAR.ID: {
                                reply = requestedJDWPEvents.clearRequest(packet, DebuggerConnection.this);
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
                                reply = JDWP.StackFrame.GET_VALUES.createReply(packet);
                                break;
                            }
                            case JDWP.StackFrame.THIS_OBJECT.ID: {
                                reply = JDWP.StackFrame.THIS_OBJECT.createReply(packet);
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
                                reply = JDWP.ClassObjectReference.REFLECTED_TYPE.createReply(packet);
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
            if (reply != null) {
                //System.out.println("replying to command(" + packet.cmdSet + "." + packet.cmd + ")");
                connection.queuePacket(reply);
            } else {
                System.out.println("no reply for command(" + packet.cmdSet + "." + packet.cmd + ")");
            }
        }
    }
}
