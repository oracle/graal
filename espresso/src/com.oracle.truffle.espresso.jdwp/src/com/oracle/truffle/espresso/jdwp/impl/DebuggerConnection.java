/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.espresso.jdwp.api.ErrorCodes;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;

public final class DebuggerConnection implements Commands {

    private final DebuggerController controller;
    private final JDWPContext context;
    private final SocketConnection connection;

    private DebuggerConnection(SocketConnection connection, DebuggerController controller) {
        this.connection = connection;
        this.controller = controller;
        this.context = controller.getContext();
    }

    static void establishDebuggerConnection(DebuggerController controller, DebuggerController.SetupState setupState, boolean isReconnect, CountDownLatch startupLatch) {
        Thread jdwpReceiver = controller.getContext().createSystemThread(new JDWPReceiver(controller, setupState, isReconnect, startupLatch));
        jdwpReceiver.setName("jdwp-receiver");
        controller.addDebuggerReceiverThread(jdwpReceiver);
        jdwpReceiver.setDaemon(true);
        jdwpReceiver.start();
    }

    public void dispose() {
        connection.dispose();
    }

    public void closeSocket() {
        connection.closeSocket();
    }

    @Override
    public void step(Object thread, RequestFilter filter, DebuggerCommand.Kind stepKind) {
        controller.setCommandRequestId(thread, filter.getRequestId(), filter.getSuspendPolicy(), false, false, stepKind);
    }

    @Override
    public Callable<Void> createLineBreakpointCommand(BreakpointInfo info) {
        return () -> {
            LineBreakpointInfo lineInfo = (LineBreakpointInfo) info;
            DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.SUBMIT_LINE_BREAKPOINT, info.getFilter());
            debuggerCommand.setSourceLocation(new SourceLocation(lineInfo.getSlashName(), (int) lineInfo.getLine(), context));
            debuggerCommand.setBreakpointInfo(info);
            controller.submitLineBreakpoint(debuggerCommand);
            return null;
        };
    }

    @Override
    public Callable<Void> createExceptionBreakpoint(BreakpointInfo info) {
        return () -> {
            DebuggerCommand debuggerCommand = new DebuggerCommand(DebuggerCommand.Kind.SUBMIT_EXCEPTION_BREAKPOINT, null);
            debuggerCommand.setBreakpointInfo(info);
            controller.submitExceptionBreakpoint(debuggerCommand);
            return null;
        };
    }

    public boolean isOpen() {
        return connection.isOpen();
    }

    private static class JDWPSender implements Runnable {

        private final SocketConnection socketConnection;

        JDWPSender(SocketConnection socketConnection) {
            this.socketConnection = socketConnection;
        }

        @Override
        public void run() {
            socketConnection.sendPackets();
        }
    }

    private static class JDWPReceiver implements Runnable {

        private DebuggerController.SetupState setupState;
        private final DebuggerController controller;
        private final boolean isReconnect;
        private final CountDownLatch latch;

        JDWPReceiver(DebuggerController controller, DebuggerController.SetupState setupState, boolean isReconnect, CountDownLatch latch) {
            this.setupState = setupState;
            this.controller = controller;
            this.isReconnect = isReconnect;
            this.latch = latch;
        }

        @Override
        public void run() {
            // first, complete the connection setup which is potentially blocking
            DebuggerConnection debuggerConnection;
            try {
                Socket connectionSocket;
                if (setupState.socket != null) {
                    connectionSocket = setupState.socket;
                } else { // we know we have a server socket then
                    assert setupState.serverSocket != null;
                    // this blocks until a debugger connects
                    connectionSocket = setupState.serverSocket.accept();
                }
                // OK, ready to do the handshake with debugger
                if (!HandshakeController.handshake(connectionSocket)) {
                    throw new IOException("Unable to handshake with debugger");
                }
                try {
                    if (controller.isClosing()) {
                        return;
                    }
                    // The following block has to be synchronized with resetting, so that
                    // we can abandon further work in case we're told to tear down
                    controller.getResettingLock().lockInterruptibly();
                    // re-check to return immediately if closing
                    if (controller.isClosing()) {
                        return;
                    }
                    SocketConnection socketConnection = new SocketConnection(connectionSocket);
                    debuggerConnection = new DebuggerConnection(socketConnection, controller);
                    controller.setDebuggerConnection(debuggerConnection);
                    controller.getEventListener().setConnection(socketConnection);
                    if (!controller.isSuspend()) {
                        // Fire the vm started event for the suspend=n case.
                        // For suspend=y we have to synchronize the sending of VM started event with
                        // the thread suspension count. Therefore, in that case we postpone the
                        // sending until we can also suspend the main thread which is done in
                        // DebuggerController#onLanguageContextInitialized.
                        controller.getEventListener().vmStarted(false);
                    }

                    // OK, we're ready to fire up the JDWP transmitter thread too
                    Thread jdwpSender = controller.getContext().createSystemThread(new JDWPSender(socketConnection));
                    jdwpSender.setName("jdwp-transmitter");
                    controller.addDebuggerSenderThread(jdwpSender);
                    jdwpSender.setDaemon(true);
                    jdwpSender.start();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } finally {
                    controller.getResettingLock().unlock();
                }
            } catch (IOException ex) {
                if (isReconnect) {
                    // could be because we're closing down the context, so we should check that and
                    // silently abort the re-connecting attempt
                    if (controller.isClosing()) {
                        return;
                    } else {
                        System.err.println("ERROR: Debuggers will not be able to connect to this context again!");
                    }
                } else {
                    // on startup any connection error is treated as fatal
                    controller.markLateStartupError(ex);
                }
                return;
            } finally {
                setupState = null;
                latch.countDown();
            }
            // Now, begin processing packets when they start to flow from the debugger.
            final BlockingQueue<Packet> packetQueue = new LinkedBlockingQueue<>();
            final AtomicBoolean processorClose = new AtomicBoolean(false);
            Thread jdwpProcessor = controller.getContext().createPolyglotThread(new JDWPProcessor(controller, debuggerConnection, packetQueue, processorClose));
            jdwpProcessor.setName("jdwp-processor");
            controller.addDebuggerProcessorThread(jdwpProcessor);
            jdwpProcessor.setDaemon(true);
            jdwpProcessor.start();
            try {
                while (!Thread.currentThread().isInterrupted() && !controller.isClosing()) {
                    try {
                        Packet packet = Packet.fromByteArray(debuggerConnection.connection.readPacket());
                        packetQueue.add(packet);
                    } catch (IOException e) {
                        if (!debuggerConnection.isOpen()) {
                            // when the socket is closed, we're done
                            break;
                        }
                        if (!Thread.currentThread().isInterrupted()) {
                            controller.warning(() -> "Failed to process jdwp packet with message: " + e.getMessage());
                            Thread.currentThread().interrupt(); // And set the interrupt flag again
                        }
                    } catch (ConnectionClosedException e) {
                        break;
                    }
                }
            } finally {
                processorClose.set(true);
                jdwpProcessor.interrupt();
                controller.getEventListener().onDetach();
            }
        }
    }

    private static class JDWPProcessor implements Runnable {

        private final DebuggerController controller;
        private final DebuggerConnection debuggerConnection;
        private final RequestedJDWPEvents requestedJDWPEvents;
        private final BlockingQueue<Packet> packetQueue;
        private final AtomicBoolean close;

        private JDWPProcessor(DebuggerController controller, DebuggerConnection debuggerConnection,
                        BlockingQueue<Packet> packetQueue, AtomicBoolean close) {
            this.controller = controller;
            this.debuggerConnection = debuggerConnection;
            this.requestedJDWPEvents = new RequestedJDWPEvents(controller);
            this.packetQueue = packetQueue;
            this.close = close;
        }

        @Override
        public void run() {
            while (!close.get()) {
                Packet packet;
                try {
                    packet = TruffleSafepoint.getCurrent().setBlockedFunction(null, TruffleSafepoint.Interrupter.THREAD_INTERRUPT,
                                    BlockingQueue::take, packetQueue, () -> breakIfClosed(), null);
                } catch (ProcessorClosedException ex) {
                    break;
                }
                processPacket(packet);
            }
        }

        private void breakIfClosed() {
            if (close.get()) {
                throw new ProcessorClosedException();
            }
        }

        private void processPacket(Packet packet) {
            JDWPContext context = controller.getContext();
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
                                    result = JDWP.VirtualMachine.VERSION.createReply(packet, context);
                                    break;
                                case JDWP.VirtualMachine.CLASSES_BY_SIGNATURE.ID:
                                    result = JDWP.VirtualMachine.CLASSES_BY_SIGNATURE.createReply(packet, controller, context);
                                    break;
                                case JDWP.VirtualMachine.ALL_CLASSES.ID:
                                    result = JDWP.VirtualMachine.ALL_CLASSES.createReply(packet, context, controller);
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
                                    result = JDWP.VirtualMachine.IDSIZES.createReply(packet);
                                    break;
                                case JDWP.VirtualMachine.SUSPEND.ID:
                                    result = JDWP.VirtualMachine.SUSPEND.createReply(packet, controller);
                                    break;
                                case JDWP.VirtualMachine.RESUME.ID:
                                    result = JDWP.VirtualMachine.RESUME.createReply(packet, controller);
                                    break;
                                case JDWP.VirtualMachine.EXIT.ID:
                                    result = JDWP.VirtualMachine.EXIT.createReply(packet, context, controller);
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
                                    result = unknownCommand(packet, controller);
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
                                    result = unknownCommand(packet, controller);
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
                                    result = JDWP.ClassType.INVOKE_METHOD.createReply(packet, controller, debuggerConnection);
                                    break;
                                case JDWP.ClassType.NEW_INSTANCE.ID:
                                    result = JDWP.ClassType.NEW_INSTANCE.createReply(packet, controller);
                                    break;
                                default:
                                    result = unknownCommand(packet, controller);
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
                                    result = unknownCommand(packet, controller);
                                    break;
                            }
                            break;
                        }
                        case JDWP.InterfaceType.ID: {
                            switch (packet.cmd) {
                                case JDWP.InterfaceType.INVOKE_METHOD.ID:
                                    result = JDWP.InterfaceType.INVOKE_METHOD.createReply(packet, controller, debuggerConnection);
                                    break;
                                default:
                                    result = unknownCommand(packet, controller);
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
                                    result = unknownCommand(packet, controller);
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
                                    result = JDWP.ObjectReference.INVOKE_METHOD.createReply(packet, controller, debuggerConnection);
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
                                    result = unknownCommand(packet, controller);
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
                                    result = unknownCommand(packet, controller);
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
                                    result = unknownCommand(packet, controller);
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
                                    result = unknownCommand(packet, controller);
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
                                    result = unknownCommand(packet, controller);
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
                                    result = unknownCommand(packet, controller);
                                    break;
                            }
                            break;
                        }
                        case JDWP.EventRequest.ID: {
                            switch (packet.cmd) {
                                case JDWP.EventRequest.SET.ID:
                                    result = requestedJDWPEvents.registerEvent(packet, debuggerConnection);
                                    break;
                                case JDWP.EventRequest.CLEAR.ID:
                                    result = requestedJDWPEvents.clearRequest(packet);
                                    break;
                                case JDWP.EventRequest.CLEAR_ALL_BREAKPOINTS.ID:
                                    result = requestedJDWPEvents.clearAllBreakpointRequests(packet);
                                    break;
                                default:
                                    result = unknownCommand(packet, controller);
                                    break;
                            }
                            break;
                        }
                        case JDWP.StackFrame.ID: {
                            switch (packet.cmd) {
                                case JDWP.StackFrame.GET_VALUES.ID:
                                    result = JDWP.StackFrame.GET_VALUES.createReply(packet, context, controller);
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
                                    result = unknownCommand(packet, controller);
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
                                    result = unknownCommand(packet, controller);
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
                                    result = unknownCommand(packet, controller);
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
                                    result = unknownCommand(packet, controller);
                                    break;
                            }
                            break;
                        }
                        default:
                            result = unknownCommandSet(packet, controller);
                            break;
                    }
                }
                debuggerConnection.handleReply(packet, result);
            } catch (Throwable t) {
                controller.severe("Internal error while processing packet", t);
                PacketStream reply = new PacketStream().replyPacket().id(packet.id);
                reply.errorCode(ErrorCodes.INTERNAL);
                debuggerConnection.handleReply(packet, new CommandResult(reply));
            }
        }

        private static class ProcessorClosedException extends RuntimeException {

            private static final long serialVersionUID = 8467327507834079474L;
        }
    }

    private static CommandResult unknownCommandSet(Packet packet, DebuggerController controller) {
        controller.warning(() -> "Unknown command set: " + packet.cmdSet);
        return notImplemented(packet);
    }

    private static CommandResult unknownCommand(Packet packet, DebuggerController controller) {
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