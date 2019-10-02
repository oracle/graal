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

    public void doProcessCommands() {
        // fire up two threads, one for the low-level connection to receive packets
        // and one for processing the debugger commands from a queue
        Thread commandProcessor = new Thread(new CommandProcessorThread(), "jdwp-command-processor");
        commandProcessor.setDaemon(true);
        commandProcessor.start();

        Thread jdwpTransport = new Thread(new JDWPTransportThread(), "jdwp-transport");
        jdwpTransport.setDaemon(true);
        jdwpTransport.start();
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

        private RequestedJDWPEvents requestedJDWPEvents = new RequestedJDWPEvents(connection);

        @Override
        public void run() {
            while(!Thread.currentThread().isInterrupted()) {
                try {
                    // blocking call
                    Packet packet = Packet.fromByteArray(connection.readPacket());
                    processPacket(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void processPacket(Packet packet) {
            //packet.dump(true);

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
                                reply = JDWP.VirtualMachine.RESUME.createReply(packet);
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
                            case JDWP.ReferenceType.SIGNATURE_WITH_GENERIC.ID:
                                reply = JDWP.ReferenceType.SIGNATURE_WITH_GENERIC.createReply(packet);
                                break;
                            case JDWP.ReferenceType.METHODS_WITH_GENERIC.ID:
                                reply = JDWP.ReferenceType.METHODS_WITH_GENERIC.createReply(packet);
                                break;
                        }
                     break;
                    }
                    case JDWP.METHOD.ID: {
                        switch (packet.cmd) {
                            case JDWP.METHOD.LINE_TABLE.ID:
                                reply = JDWP.METHOD.LINE_TABLE.createReply(packet);
                                break;
                        }
                        break;
                    }
                    case JDWP.ObjectReference.ID: {
                        switch (packet.cmd) {
                            case JDWP.ObjectReference.REFERENCE_TYPE.ID:
                                reply = JDWP.ObjectReference.REFERENCE_TYPE.createReply(packet);
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
                                reply = JDWP.THREAD_REFERENCE.RESUME.createReply(packet, controller.getContext());
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
                    case JDWP.EventRequest.ID: {
                        switch (packet.cmd) {
                            case JDWP.EventRequest.SET.ID: {
                                reply = requestedJDWPEvents.registerEvent(packet, DebuggerConnection.this);
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
