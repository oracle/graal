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
import static org.junit.Assume.assumeTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

public class TestSocketEvents extends JfrRecordingTest {
    public static final String MESSAGE = "hello server";
    public static int PORT = 9876;
    public static String HOST = "127.0.0.1";

    @BeforeClass
    public static void checkJavaVersion() {
        assumeTrue("skipping JFR socket test", JavaVersionUtil.JAVA_SPEC >= 22);
    }

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

        stopRecording(recording, TestSocketEvents::validateEvents);
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
        private Socket clientSocket;
        private PrintWriter out;

        public void startConnection(String ip, int port) throws IOException, InterruptedException {
            while (true) {
                try {
                    clientSocket = new Socket(ip, port);
                    break;
                } catch (Exception e) {
                    // Keep trying until server begins accepting connections.
                    Thread.sleep(100);
                }
            }
            out = new PrintWriter(clientSocket.getOutputStream(), true);
        }

        public void sendMessage(String msg) throws IOException {
            out.print(msg);
            out.close();
            clientSocket.close();
        }
    }

    static class GreetServer {
        public void start(int port) throws IOException {
            ServerSocket serverSocket = new ServerSocket(port);
            Socket clientSocket = serverSocket.accept();

            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            in.readLine();

            in.close();
            clientSocket.close();
            serverSocket.close();
        }
    }
}
