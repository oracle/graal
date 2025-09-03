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

import com.oracle.svm.jdwp.bridge.Packet;
import com.oracle.svm.jdwp.bridge.UnmodifiablePacket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

// Checkstyle: allow Thread.isInterrupted
public final class SocketConnection implements Runnable {

    private static final Packet END = UnmodifiablePacket.parseAndWrap(new byte[]{0, 0, 0, 11, 0, 0, 0, 0, 0, 0, 0});

    private final Socket socket;
    private final OutputStream socketOutput;
    private final InputStream socketInput;
    private final Object receiveLock = new Object();
    private final Object sendLock = new Object();
    private final AtomicBoolean isOpen = new AtomicBoolean(true);

    private final BlockingQueue<Packet> queue = new ArrayBlockingQueue<>(4096);
    private Thread senderThread;

    SocketConnection(Socket socket) throws IOException {
        this.socket = socket;
        socket.setTcpNoDelay(true);
        socketInput = socket.getInputStream();
        socketOutput = socket.getOutputStream();
    }

    public void close() throws IOException {
        // send outstanding packets before closing
        if (!isOpen.getAndSet(false)) {
            return;
        }
        queue.add(END);
        try {
            senderThread.join();
        } catch (InterruptedException ex) {
            // Interrupted, proceed with closing of the socket
        }

        socketOutput.flush();
        ServerJDWP.LOGGER.log("closing socket now");
        socketOutput.close();
        socketInput.close();
        socket.close();
        queue.clear();
    }

    public boolean isOpen() {
        return isOpen.get();
    }

    public Thread startSenderThread() {
        Thread jdwpSender = new Thread(this, "jdwp-transmitter");
        jdwpSender.setDaemon(true);
        this.senderThread = jdwpSender;
        jdwpSender.start();
        return jdwpSender;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Packet take = queue.take();
                if (END == take) {
                    break;
                }
                byte[] shipment = take.toByteArray();
                writePacket(shipment);
            } catch (InterruptedException | ConnectionClosedException e) {
                break;
            } catch (IOException ex) {
                if (isOpen()) {
                    throw new RuntimeException("Failed sending packet to debugger instance", ex);
                } else {
                    break;
                }
            }
        }
    }

    public void queuePacket(Packet packet) {
        assert packet != null && packet != END;
        if (isOpen()) {
            queue.add(packet);
        }
    }

    public byte[] readPacket() throws IOException, ConnectionClosedException {
        if (!isOpen() || Thread.currentThread().isInterrupted()) {
            throw new ConnectionClosedException();
        }
        synchronized (receiveLock) {
            int b1;
            int b2;
            int b3;
            int b4;

            // length
            try {
                b1 = socketInput.read();
                b2 = socketInput.read();
                b3 = socketInput.read();
                b4 = socketInput.read();
            } catch (IOException ioe) {
                if (!isOpen() || Thread.currentThread().isInterrupted()) {
                    throw new ConnectionClosedException();
                } else {
                    throw ioe;
                }
            }

            // EOF
            if (b1 < 0) {
                return new byte[0];
            }

            if (b2 < 0 || b3 < 0 || b4 < 0) {
                throw new IOException("Protocol error - premature EOF");
            }

            int len = ((b1 << 24) | (b2 << 16) | (b3 << 8) | b4);

            if (len < 0) {
                throw new IOException("Protocol error - invalid length");
            }

            byte[] b = new byte[len];
            b[0] = (byte) b1;
            b[1] = (byte) b2;
            b[2] = (byte) b3;
            b[3] = (byte) b4;

            int off = 4;
            len -= off;

            while (len > 0) {
                int count;
                try {
                    count = socketInput.read(b, off, len);
                } catch (IOException ioe) {
                    if (!isOpen() || Thread.currentThread().isInterrupted()) {
                        throw new ConnectionClosedException();
                    } else {
                        throw ioe;
                    }
                }
                if (count < 0) {
                    throw new IOException("Protocol error - premature EOF");
                }
                len -= count;
                off += count;
            }

            return b;
        }
    }

    private void writePacket(byte[] b) throws IOException, ConnectionClosedException {
        /*
         * Check the packet size
         */
        if (b.length < 11) {
            throw new IllegalArgumentException("Packet is insufficient size");
        }
        int b0 = b[0] & 0xff;
        int b1 = b[1] & 0xff;
        int b2 = b[2] & 0xff;
        int b3 = b[3] & 0xff;
        int len = ((b0 << 24) | (b1 << 16) | (b2 << 8) | b3);
        if (len < 11) {
            throw new IllegalArgumentException("Packet is insufficient size");
        }

        /*
         * Check that the byte array contains the complete packet
         */
        if (len > b.length) {
            throw new IllegalArgumentException("Length mis-match");
        }

        synchronized (sendLock) {
            try {
                /*
                 * Send the packet (ignoring any bytes that follow the packet in the byte array).
                 */
                socketOutput.write(b, 0, len);
            } catch (IOException ioe) {
                if (!isOpen()) {
                    throw new ConnectionClosedException();
                } else {
                    throw ioe;
                }
            }
        }
    }

    public void sendVMDied(Packet stream) {
        byte[] shipment = stream.toByteArray();
        try {
            writePacket(shipment);
            socketOutput.flush();
        } catch (Exception e) {
            ServerJDWP.LOGGER.log("Sending VM_DEATH packet to client failed");
        }
    }
}
