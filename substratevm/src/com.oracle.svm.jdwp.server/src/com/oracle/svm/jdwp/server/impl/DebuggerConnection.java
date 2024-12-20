/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.server.impl;

import java.io.IOException;
import java.util.Collection;

import com.oracle.svm.jdwp.bridge.ErrorCode;
import com.oracle.svm.jdwp.bridge.JDWP;
import com.oracle.svm.jdwp.bridge.JDWPException;
import com.oracle.svm.jdwp.bridge.Packet;
import com.oracle.svm.jdwp.bridge.UnmodifiablePacket;
import com.oracle.svm.jdwp.bridge.WritablePacket;

// Checkstyle: allow Thread.isInterrupted
public final class DebuggerConnection {

    private final DebuggerController controller;

    private final SocketConnection connection;
    private final Collection<Thread> activeThreads;

    public DebuggerConnection(SocketConnection connection, DebuggerController controller, Collection<Thread> activeThreads) {
        this.connection = connection;
        this.controller = controller;
        this.activeThreads = activeThreads;
    }

    @SuppressWarnings("unused")
    public void doProcessCommands(boolean suspend, Runnable vmStartedJob) {
        controller.init();
        // fire up a thread for the low-level connection to receive packets
        Thread jdwpTransport = new Thread(new JDWPTransportThread(vmStartedJob), "jdwp-transport");
        jdwpTransport.setDaemon(true);
        jdwpTransport.start();
        activeThreads.add(jdwpTransport);
    }

    public void queuePacket(Packet packet) {
        connection.queuePacket(packet);
    }

    public void close() {
        try {
            connection.close();
            controller.getEventListener().setConnection(null);
        } catch (IOException e) {
            throw new RuntimeException("Closing socket connection failed", e);
        }
        // Interrupt and join the active threads
        for (Thread t : activeThreads) {
            t.interrupt();
            try {
                t.join();
            } catch (InterruptedException ex) {
                // Interrupted
            }
        }
    }

    private static Packet safeParseAndWrap(byte[] packetBytes) throws IOException, ConnectionClosedException {
        if (packetBytes.length < Packet.HEADER_SIZE) {
            if (Thread.currentThread().isInterrupted() || packetBytes.length == 0) {
                throw new ConnectionClosedException();
            }
            throw new IOException("Packet insufficient size");
        }
        try {
            return UnmodifiablePacket.parseAndWrap(packetBytes);
        } catch (IllegalArgumentException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw new ConnectionClosedException();
            }
            throw new IOException("Packet .length and size mis-match");
        }
    }

    private class JDWPTransportThread implements Runnable {

        private Runnable vmStartedJob;

        JDWPTransportThread(Runnable vmStartedJob) {
            this.vmStartedJob = vmStartedJob;
        }

        @Override
        public void run() {
            vmStartedJob.run();
            vmStartedJob = null;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Packet packet = safeParseAndWrap(connection.readPacket());
                    processPacket(packet); // no (soft) exception expected here
                } catch (IOException e) {
                    ServerJDWP.LOGGER.log(e, "Failed to read/parse JDWP packet");
                    break;
                } catch (ConnectionClosedException e) {
                    ServerJDWP.LOGGER.log(e);
                    break;
                }
            }
        }

        private void processPacket(Packet packet) {
            try {
                if (packet.isReply()) {
                    // result packet from debugger!
                    ServerJDWP.LOGGER.log(() -> "Should not get any reply packet from debugger");
                } else {
                    // process a command packet from debugger
                    ServerJDWP.LOGGER.log(() -> "received command " + JDWP.toString(packet.commandSet(), packet.command()));
                    Packet result = controller.getServerJDWP().dispatch(packet);
                    handleReply(packet, result);
                }
            } catch (JDWPException e) {
                ServerJDWP.LOGGER.log(e, "JDWP exception");
                WritablePacket reply = WritablePacket.newReplyTo(packet);
                reply.errorCode(e.getError());
                handleReply(packet, reply);
            } catch (VirtualMachineError vmError) {
                ServerJDWP.LOGGER.log(vmError);
                throw vmError; // stack-overflow or out-of-memory
            } catch (Throwable t) {
                ServerJDWP.LOGGER.log(t, "Internal error");
                WritablePacket reply = WritablePacket.newReplyTo(packet);
                reply.errorCode(ErrorCode.INTERNAL);
                handleReply(packet, reply);
            }
        }
    }

    void handleReply(Packet packet, Packet result) {
        if (result == null) {
            assert (packet.commandSet() == JDWP.ClassType && packet.command() == JDWP.ClassType_InvokeMethod) ||
                            (packet.commandSet() == JDWP.ObjectReference && packet.command() == JDWP.ObjectReference_InvokeMethod) ||
                            (packet.commandSet() == JDWP.InterfaceType && packet.command() == JDWP.InterfaceType_InvokeMethod) ||
                            (packet.commandSet() == JDWP.ClassType && packet.command() == JDWP.ClassType_NewInstance) : "Only JDWP invoke commands can have a null reply (asynchronous).";
            ServerJDWP.LOGGER.log(() -> "no result for command " + JDWP.toString(packet.commandSet(), packet.command()));
            return;
        }

        ServerJDWP.LOGGER.log(() -> "replying to command " + JDWP.toString(packet.commandSet(), packet.command()));
        connection.queuePacket(result);
    }
}
