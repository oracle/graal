/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * Test handling of multiple engines by the Inspector.
 */
public class MultiEngineTest extends EnginesGCedTest {

    private static final int PORT = 9229;

    private static final String[] INITIAL_MESSAGES = {
                    "{\"id\":1,\"method\":\"Runtime.enable\"}",
                    "{\"id\":2,\"method\":\"Debugger.enable\"}",
                    "{\"id\":3,\"method\":\"Debugger.setPauseOnExceptions\",\"params\":{\"state\":\"none\"}}",
                    "{\"id\":4,\"method\":\"Runtime.runIfWaitingForDebugger\"}"
    };

    private static final String CODE1 = "function main() {\n" +
                    "  n = 3;\n" +
                    "  f = factorial(n);\n" +
                    "  return f;\n" +
                    "}\n" +
                    "function factorial(n) {\n" +
                    "  f = 1;\n" +
                    "  i = 2;\n" +
                    "  while (i <= n) {\n" +
                    "    f = f * i;\n" +
                    "    i = i + 1;\n" +
                    "  }\n" +
                    "  return f;\n" +
                    "}";
    private static final String CODE2 = "function main() {\n" +
                    "  n = 3;\n" +
                    "  f = fibonacci(n);\n" +
                    "  return f;\n" +
                    "}\n" +
                    "function fibonacci(n) {\n" +
                    "  if (n == 0) {\n" +
                    "    return 0;\n" +
                    "  }\n" +
                    "  n1 = 0;\n" +
                    "  i = 2;\n" +
                    "  f = 1;\n" +
                    "  while (i <= n) {\n" +
                    "    lastf = f;\n" +
                    "    f = f + n1;\n" +
                    "    n1 = lastf;\n" +
                    "    i = i + 1;\n" +
                    "  }\n" +
                    "  return f;\n" +
                    "}";

    @Test
    public void testMultipleEnginesSerial() {
        Source[] sources = new Source[]{
                        Source.newBuilder("sl", CODE1, "MTest1.sl").buildLiteral(),
                        Source.newBuilder("sl", CODE2, "MTest2.sl").buildLiteral(),
                        Source.newBuilder("sl", CODE1, "MTest3.sl").buildLiteral(),
                        Source.newBuilder("sl", CODE2, "MTest4.sl").buildLiteral(),
        };
        CountDownLatch[] isUp = new CountDownLatch[sources.length];
        for (int i = 0; i < sources.length; i++) {
            isUp[i] = new CountDownLatch(1);
        }
        AtomicReference<Throwable> error = new AtomicReference<>();
        verifySerialDebug(isUp, error);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < sources.length; i++) {
            runEngine(sources[i], out, isUp[i]);
        }
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        String output = new String(out.toByteArray());
        for (int i = 0; i < sources.length; i++) {
            assertTrue(output, output.contains(PORT + "/" + sources[i].getName()));
        }
    }

    private static void verifySerialDebug(CountDownLatch[] isUp, AtomicReference<Throwable> error) {
        new Thread(() -> {
            try {
                for (int i = 0; i < isUp.length; i++) {
                    isUp[i].await();
                    String path = "MTest" + (i + 1) + ".sl." + SecureInspectorPathGenerator.getToken();
                    checkInfo(path);
                    checkSuspendAndResume(path);
                }
            } catch (Throwable thr) {
                thr.printStackTrace();
                error.set(thr);
            }
        }).start();
    }

    @Test
    public void testMultipleEnginesParallel() throws InterruptedException {
        Source[] sources = new Source[]{
                        Source.newBuilder("sl", CODE1, "MTest1.sl").buildLiteral(),
                        Source.newBuilder("sl", CODE2, "MTest2.sl").buildLiteral(),
                        Source.newBuilder("sl", CODE1, "MTest3.sl").buildLiteral(),
                        Source.newBuilder("sl", CODE2, "MTest4.sl").buildLiteral(),
        };
        CountDownLatch[] isUp = new CountDownLatch[sources.length];
        for (int i = 0; i < sources.length; i++) {
            isUp[i] = new CountDownLatch(1);
        }
        List<Throwable> errors = Collections.synchronizedList(new LinkedList<>());
        verifyParallelDebug(isUp, errors);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread[] threads = new Thread[sources.length];
        for (int i = 0; i < sources.length; i++) {
            int index = i;
            Thread t = new Thread(() -> {
                try {
                    runEngine(sources[index], out, isUp[index]);
                } catch (Throwable thr) {
                    thr.printStackTrace();
                    errors.add(thr);
                }
            }, sources[i].getName());
            t.start();
            threads[i] = t;
        }
        for (int i = 0; i < sources.length; i++) {
            threads[i].join();
        }
        if (!errors.isEmpty()) {
            AssertionError err = new AssertionError();
            for (Throwable thr : errors) {
                err.addSuppressed(thr);
            }
            throw err;
        }
        String output = new String(out.toByteArray());
        for (int i = 0; i < sources.length; i++) {
            assertTrue(output, output.contains(PORT + "/" + sources[i].getName()));
        }
    }

    @Test
    public void testMultipleEnginesSamePath() throws Exception {
        Source[] sources = new Source[]{
                        Source.newBuilder("sl", CODE1, "MTest1.sl").buildLiteral(),
                        Source.newBuilder("sl", CODE2, "MTest2.sl").buildLiteral(),
        };
        List<Throwable> errors = Collections.synchronizedList(new LinkedList<>());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CountDownLatch isUp = new CountDownLatch(1);
        final String samePath = "samePath" + SecureInspectorPathGenerator.getToken();
        Thread t = new Thread(() -> {
            try {
                runEngine(sources[0], samePath, out, isUp);
            } catch (Throwable thr) {
                errors.add(thr);
                isUp.countDown();
            }
        }, sources[0].getName());
        t.start();
        isUp.await();
        try {
            runEngine(sources[1], samePath, out, isUp);
            fail();
        } catch (Throwable thr) {
            String message = thr.getMessage();
            assertTrue(message, message.contains("Inspector session with the same path exists already"));
        }
        checkSuspendAndResume(samePath);
        t.join();
        if (!errors.isEmpty()) {
            AssertionError err = new AssertionError();
            for (Throwable thr : errors) {
                err.addSuppressed(thr);
            }
            throw err;
        }
    }

    private static void verifyParallelDebug(CountDownLatch[] isUp, List<Throwable> errors) {
        new Thread(() -> {
            try {
                for (int i = 0; i < isUp.length; i++) {
                    isUp[i].await();
                }
                String[] paths = new String[isUp.length];
                for (int i = 0; i < paths.length; i++) {
                    paths[i] = "MTest" + (i + 1) + ".sl." + SecureInspectorPathGenerator.getToken();
                }
                checkInfo(paths);
                for (int i = 0; i < paths.length; i++) {
                    int index = i;
                    new Thread(() -> {
                        try {
                            checkSuspendAndResume(paths[index]);
                        } catch (Throwable thr) {
                            thr.printStackTrace();
                            errors.add(thr);
                        }
                    }).start();
                }
            } catch (Throwable thr) {
                thr.printStackTrace();
                errors.add(thr);
            }
        }).start();
    }

    private String runEngine(Source src, OutputStream out, CountDownLatch isUp) {
        return runEngine(src, src.getName() + "." + SecureInspectorPathGenerator.getToken(), out, isUp);
    }

    private String runEngine(Source src, String path, OutputStream out, CountDownLatch isUp) {
        try (Engine e = Engine.newBuilder().option("inspect.Path", path).err(out).build()) {
            addEngineReference(e);
            Context c = Context.newBuilder().engine(e).allowAllAccess(true).build();
            isUp.countDown();
            Value result = c.eval(src);
            if (result.fitsInLong()) {
                return String.valueOf(result.asLong());
            } else {
                return result.as(String.class);
            }
        }
    }

    private static void checkInfo(String... paths) throws MalformedURLException, IOException {
        URL url = new URL("http", InetAddress.getLoopbackAddress().getHostAddress(), PORT, "/json");
        HttpURLConnection connection = ((HttpURLConnection) url.openConnection());
        assertEquals("application/json; charset=UTF-8", connection.getContentType());
        StringWriter out = new StringWriter(connection.getContentLength());
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                out.write(inputLine);
            }
        }
        String message = out.toString();
        JSONArray infos = new JSONArray(message);
        assertEquals(message, paths.length, infos.length());
        Set<String> endWs = new HashSet<>();
        for (int i = 0; i < paths.length; i++) {
            endWs.add(PORT + "/" + paths[i]);
        }
        for (int i = 0; i < paths.length; i++) {
            JSONObject info = (JSONObject) infos.get(i);
            final String ws = info.getString("webSocketDebuggerUrl");
            for (String end : endWs) {
                if (ws.endsWith(end)) {
                    endWs.remove(end);
                    break;
                }
            }
            if (endWs.size() > paths.length - i - 1) {
                throw new AssertionError(ws + " does not end with any of " + endWs);
            }
        }
        assertTrue(endWs.isEmpty());
    }

    private static void checkSuspendAndResume(String path) throws Exception {
        CountDownLatch closed = new CountDownLatch(1);
        AtomicBoolean paused = new AtomicBoolean(false);
        AtomicReference<Exception> exception = new AtomicReference<>(null);
        final String url = "ws://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + PORT + "/" + path;
        WebSocketClient wsc = new WebSocketClient(new URI(url)) {
            @Override
            public void onOpen(ServerHandshake sh) {
            }

            @Override
            public void onMessage(String message) {
                JSONObject msg = new JSONObject(message);
                if ("Debugger.paused".equals(msg.opt("method"))) {
                    paused.set(true);
                    send("{\"id\":5,\"method\":\"Debugger.resume\"}");
                }
            }

            @Override
            public void onClose(int i, String message, boolean bln) {
                closed.countDown();
            }

            @Override
            public void onError(Exception excptn) {
                excptn.printStackTrace();
                exception.set(excptn);
            }
        };
        final boolean connectionSucceeded = wsc.connectBlocking();
        assertTrue("Connection has not succeeded: " + url, connectionSucceeded);
        for (String message : INITIAL_MESSAGES) {
            wsc.send(message);
        }
        closed.await();
        wsc.close();
        if (exception.get() != null) {
            throw exception.get();
        }
    }
}
