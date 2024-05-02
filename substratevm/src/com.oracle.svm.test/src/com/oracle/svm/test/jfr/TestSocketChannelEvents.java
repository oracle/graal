/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;

import org.junit.Test;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

public class TestSocketChannelEvents extends JfrRecordingTest {
    public static final String MESSAGE = "hello server";
    public static final int DEFAULT_SIZE = 1024;
    public static int PORT = 9876;
    public static String HOST = "127.0.0.1";

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{"jdk.SocketRead", "jdk.SocketWrite"};
        Recording recording = startRecording(events);

        Thread serverThread = new Thread(() -> {
            GreetServer server = new GreetServer();
            try {
                server.start(PORT);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        serverThread.start();
        GreetClient client = new GreetClient();
        client.startConnection(HOST, PORT);
        client.sendMessage(MESSAGE);
        serverThread.join();
        stopRecording(recording, TestSocketChannelEvents::validateEvents);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        boolean foundSocketRead = false;
        boolean foundSocketWrite = false;
        for (RecordedEvent e : events) {
            String name = e.getEventType().getName();
            String host = e.getString("host");
            if (host.equals(HOST) && name.equals("jdk.SocketRead") && e.getLong("bytesRead") == MESSAGE.getBytes().length) {
                foundSocketRead = true;
            } else if (host.equals(HOST) && name.equals("jdk.SocketWrite") && e.getLong("bytesWritten") == MESSAGE.getBytes().length &&
                            e.getLong("port") == PORT) {
                foundSocketWrite = true;
            }
        }
        assertTrue(foundSocketRead);
        assertTrue(foundSocketWrite);
    }

    static class GreetClient {
        SocketChannel socketChannel;

        public void startConnection(String ip, int port) throws InterruptedException {
            while (true) {
                try {
                    socketChannel = SocketChannel.open(new InetSocketAddress(ip, port));
                    break;
                } catch (Exception e) {
                    // Keep trying until server begins accepting connections.
                    Thread.sleep(100);
                }
            }
        }

        public void sendMessage(String msg) throws IOException {
            byte[] bytes = msg.getBytes();
            ByteBuffer buf = ByteBuffer.allocate(bytes.length);
            buf.put(bytes);
            buf.flip();
            while (buf.hasRemaining()) {
                socketChannel.write(buf);
            }
            socketChannel.close();
        }
    }

    static class GreetServer {
        public void start(int port) throws IOException {
            // Prepare server.
            ServerSocketChannel server = ServerSocketChannel.open();
            server.socket().bind(new InetSocketAddress(port));

            // Block waiting for connection from client.
            SocketChannel client = server.accept();

            // Read data from client.
            ByteBuffer readBuf = ByteBuffer.allocate(DEFAULT_SIZE);
            client.read(readBuf);
            String message = new String(readBuf.array(), 0, readBuf.position());
            assertTrue(MESSAGE.equals(message));
            client.close();
            server.close();
        }
    }
}
