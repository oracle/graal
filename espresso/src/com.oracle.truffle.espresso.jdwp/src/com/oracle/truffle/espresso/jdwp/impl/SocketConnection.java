/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class SocketConnection {
    private static final PacketStream DISPOSE_PACKET = new PacketStream();
    private final Socket socket;
    private final OutputStream socketOutput;
    private final InputStream socketInput;
    private final BlockingQueue<PacketStream> queue = new ArrayBlockingQueue<>(4096);

    SocketConnection(Socket socket) throws IOException {
        this.socket = socket;
        socket.setTcpNoDelay(true);
        socketInput = socket.getInputStream();
        socketOutput = socket.getOutputStream();
    }

    public void dispose() {
        // queue the dispose packet
        queue.add(DISPOSE_PACKET);
    }

    public void closeSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            // nothing more we can do at this point really
        }
    }

    public boolean isOpen() {
        return !socket.isClosed();
    }

    public void sendPackets() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                PacketStream stream = queue.take();
                if (stream == DISPOSE_PACKET) {
                    break;
                } else {
                    byte[] shipment = stream.prepareForShipment();
                    writePacket(shipment);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException ex) {
                if (isOpen()) {
                    throw new RuntimeException("Failed sending packet to debugger instance", ex);
                } else {
                    break;
                }
            }
        }
    }

    public void queuePacket(PacketStream stream) {
        if (isOpen()) {
            queue.add(stream);
        }
    }

    public byte[] readPacket() throws IOException {
        int b1;
        int b2;
        int b3;
        int b4;

        // length
        b1 = socketInput.read();
        b2 = socketInput.read();
        b3 = socketInput.read();
        b4 = socketInput.read();

        // EOF
        if (b1 < 0) {
            return new byte[0];
        }

        if (b2 < 0 || b3 < 0 || b4 < 0) {
            throw new IOException("protocol error - premature EOF");
        }

        int len = ((b1 << 24) | (b2 << 16) | (b3 << 8) | (b4 << 0));

        if (len < 0) {
            throw new IOException("protocol error - invalid length");
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
            count = socketInput.read(b, off, len);

            if (count < 0) {
                throw new IOException("protocol error - premature EOF");
            }
            len -= count;
            off += count;
        }
        return b;
    }

    public void writePacket(byte[] b) throws IOException {
        /*
         * Check the packet size
         */
        if (b.length < 11) {
            throw new IllegalArgumentException("packet is insufficient size");
        }
        int b0 = b[0] & 0xff;
        int b1 = b[1] & 0xff;
        int b2 = b[2] & 0xff;
        int b3 = b[3] & 0xff;
        int len = ((b0 << 24) | (b1 << 16) | (b2 << 8) | (b3 << 0));
        if (len < 11) {
            throw new IllegalArgumentException("packet is insufficient size");
        }

        /*
         * Check that the byte array contains the complete packet
         */
        if (len > b.length) {
            throw new IllegalArgumentException("length mis-match");
        }
        /*
         * Send the packet (ignoring any bytes that follow the packet in the byte array).
         */
        socketOutput.write(b, 0, len);
    }
}
