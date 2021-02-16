/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.test;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;

public final class DAPTester {

    private static final String CONTENT_LENGTH_HEADER = "Content-Length:";
    private final Executor executor = Executors.newSingleThreadExecutor((runnable) -> {
        Thread thr = new Thread(runnable);
        thr.setName("testRunner");
        return thr;
    });
    private final DAPSessionHandler handler;
    private final Context context;
    private Future<Value> lastValue;

    private DAPTester(DAPSessionHandler handler, Engine engine) {
        this.handler = handler;
        this.context = Context.newBuilder().engine(engine).allowAllAccess(true).build();
    }

    public static DAPTester start(boolean suspend) throws IOException {
        final ProxyOutputStream err = new ProxyOutputStream(System.err);
        Engine engine = Engine.newBuilder().err(err).build();
        Instrument testInstrument = engine.getInstruments().get(DAPTestInstrument.ID);
        DAPSessionHandlerProvider sessionHandlerProvider = testInstrument.lookup(DAPSessionHandlerProvider.class);
        DAPSessionHandler sessionHandler = sessionHandlerProvider.getSessionHandler(suspend, false, false);
        return new DAPTester(sessionHandler, engine);
    }

    public void finish() throws IOException {
        if (!lastValue.isDone()) {
            try {
                lastValue.get(1, TimeUnit.SECONDS);
            } catch (TimeoutException ex) {
                if (handler.getInputStream().available() > 0) {
                    Assert.fail("Additional message available: " + getMessage());
                }
                Assert.fail("Last eval(...) has not finished yet");
            } catch (InterruptedException | ExecutionException ex) {
            }
        }
        if (handler.getInputStream().available() > 0) {
            Assert.fail("Additional message available: " + getMessage());
        }
    }

    public Future<Value> eval(Source source) {
        lastValue = CompletableFuture.supplyAsync(() -> {
            return context.eval(source);
        }, executor);
        return lastValue;
    }

    public void sendMessage(String message) throws IOException {
        final byte[] bytes = message.getBytes();
        handler.getOutputStream().write((CONTENT_LENGTH_HEADER + bytes.length + "\n\n").getBytes());
        handler.getOutputStream().write(bytes);
        handler.getOutputStream().flush();
    }

    public String getMessage() throws IOException {
        return new String(readMessageBytes(handler.getInputStream()));
    }

    private static byte[] readMessageBytes(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int contentLength = -1;
        while (true) {
            int c = in.read();
            if (c == -1) {
                // End of input stream
                return null;
            } else if (c == '\n') {
                String header = line.toString().trim();
                if (header.length() > 0) {
                    if (header.startsWith(CONTENT_LENGTH_HEADER)) {
                        try {
                            contentLength = Integer.parseInt(header.substring(CONTENT_LENGTH_HEADER.length()).trim());
                        } catch (NumberFormatException nfe) {
                        }
                    }
                } else {
                    // Two consecutive newlines start the message content
                    if (contentLength < 0) {
                        throw new IOException("Error while processing an incomming message: Missing header " + CONTENT_LENGTH_HEADER + " in input.");
                    } else {
                        // Read the message
                        byte[] buffer = new byte[contentLength];
                        int bytesRead = 0;
                        while (bytesRead < contentLength) {
                            int read = in.read(buffer, bytesRead, contentLength - bytesRead);
                            if (read == -1) {
                                return null;
                            }
                            bytesRead += read;
                        }
                        return buffer;
                    }
                }
                line = new StringBuilder();
            } else if (c != '\r') {
                line.append((char) c);
            }
        }
    }

    public boolean compareReceivedMessages(String... messages) throws Exception {
        List<JSONObject> expectedObjects = Arrays.stream(messages).map(message -> new JSONObject(message)).collect(Collectors.toList());
        int size = expectedObjects.size();
        while (size > 0) {
            final String receivedMessage = getMessage();
            JSONObject receivedObject = new JSONObject(receivedMessage);
            for (Iterator<JSONObject> it = expectedObjects.iterator(); it.hasNext();) {
                JSONObject expectedObject = it.next();
                if (compare(expectedObject, receivedObject, false)) {
                    it.remove();
                    break;
                }
            }
            Assert.assertFalse("Unexpected message received: " + receivedMessage, expectedObjects.size() == size);
            size = expectedObjects.size();
        }
        return true;
    }

    private static boolean compare(Object expectedValue, Object receivedValue) {
        if (expectedValue.getClass() != receivedValue.getClass()) {
            return false;
        }
        if (expectedValue instanceof JSONObject) {
            return compare((JSONObject) expectedValue, (JSONObject) receivedValue, true);
        }
        if (expectedValue instanceof JSONArray) {
            return compare((JSONArray) expectedValue, (JSONArray) receivedValue);
        }
        return Objects.equals(expectedValue, receivedValue);
    }

    private static boolean compare(JSONArray expectedArray, JSONArray receivedArray) {
        if (expectedArray.length() != receivedArray.length()) {
            return false;
        }
        for (int i = 0; i < expectedArray.length(); i++) {
            if (!compare(expectedArray.get(i), receivedArray.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean compare(JSONObject expectedObject, JSONObject receivedObject, boolean exactMatch) {
        int expectedLength = expectedObject.length();
        int receivedLength = receivedObject.length();
        if (exactMatch || expectedObject.has("seq")) {
            if (expectedLength != receivedLength) {
                return false;
            }
        } else {
            if (expectedLength + 1 != receivedLength) {
                return false;
            }
        }
        for (Iterator<String> it = expectedObject.keys(); it.hasNext();) {
            String key = it.next();
            if (!receivedObject.has(key) || !compare(expectedObject.get(key), receivedObject.get(key))) {
                return false;
            }
        }
        return true;
    }

    private static final class ProxyOutputStream extends OutputStream {

        OutputStream delegate;

        ProxyOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
