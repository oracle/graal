/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.MessageEndpoint;

import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext;
import com.oracle.truffle.tools.chromeinspector.domains.DebuggerDomain;
import com.oracle.truffle.tools.chromeinspector.server.ConnectionWatcher;
import com.oracle.truffle.tools.chromeinspector.server.InspectServerSession;
import com.oracle.truffle.tools.chromeinspector.types.ExceptionDetails;
import com.oracle.truffle.tools.chromeinspector.types.RemoteObject;

public final class InspectorTester {

    private final InspectExecThread exec;

    private InspectorTester(InspectExecThread exec) {
        this.exec = exec;
    }

    public static InspectorTester start(boolean suspend) throws InterruptedException {
        return start(suspend, false, false);
    }

    public static InspectorTester start(boolean suspend, final boolean inspectInternal, final boolean inspectInitialization) throws InterruptedException {
        return start(suspend, inspectInternal, inspectInitialization, Collections.emptyList());
    }

    public static InspectorTester start(boolean suspend, final boolean inspectInternal, final boolean inspectInitialization, List<URI> sourcePath) throws InterruptedException {
        RemoteObject.resetIDs();
        ExceptionDetails.resetIDs();
        InspectorExecutionContext.resetIDs();
        InspectExecThread exec = new InspectExecThread(suspend, inspectInternal, inspectInitialization, sourcePath);
        exec.start();
        exec.initialized.acquire();
        return new InspectorTester(exec);
    }

    static String getStringURI(URI uri) {
        if ("truffle".equals(uri.getScheme())) {
            String ssp = uri.getSchemeSpecificPart();
            return ssp.substring(ssp.indexOf('/') + 1);
        } else {
            return uri.toString();
        }
    }

    public DebuggerDomain getDebugger() {
        return exec.inspect.getDebugger();
    }

    public void setErr(OutputStream err) {
        exec.err.delegate = err;
    }

    public void finishNoGC() throws InterruptedException {
        finish(false, false);
    }

    public void finish() throws InterruptedException {
        finish(false, true);
    }

    public String finishErr() throws InterruptedException {
        return finish(true, true);
    }

    private String finish(boolean expectError, boolean gcCheck) throws InterruptedException {
        synchronized (exec.lock) {
            exec.done = true;
            exec.lock.notifyAll();
        }
        exec.join();
        RemoteObject.resetIDs();
        ExceptionDetails.resetIDs();
        InspectorExecutionContext.resetIDs();
        String error = null;
        if (exec.error != null) {
            if (expectError) {
                error = exec.error.getMessage();
            } else {
                throw new AssertionError(error, exec.error);
            }
            exec.error = null;
        }
        if (gcCheck) {
            exec.gcCheck.checkCollected();
        }
        return error;
    }

    public boolean shouldWaitForClose() {
        return exec.connectionWatcher.shouldWaitForClose();
    }

    public long getContextId() {
        return exec.contextId;
    }

    public InspectorExecutionContext getInspectorContext() {
        return exec.inspectorContext;
    }

    public Future<Value> eval(Source source) {
        return exec.eval(source);
    }

    public void sendMessage(String message) {
        exec.inspect.sendText(message);
    }

    public String getMessages(boolean waitForSome) throws InterruptedException {
        return getMessages(waitForSome, 0);
    }

    private String getMessages(boolean waitForSome, int maxLength) throws InterruptedException {
        synchronized (exec.receivedMessages) {
            String messages;
            do {
                messages = exec.receivedMessages.toString();
                if (waitForSome && messages.isEmpty()) {
                    exec.receivedMessages.wait();
                } else {
                    break;
                }
            } while (true);
            if (maxLength > 0 && messages.length() > maxLength) {
                exec.receivedMessages.delete(0, maxLength);
                messages = messages.substring(0, maxLength);
            } else {
                exec.receivedMessages.delete(0, exec.receivedMessages.length());
            }
            return messages;
        }
    }

    public boolean compareReceivedMessages(String initialMessages) throws InterruptedException {
        String messages = initialMessages;
        int length = initialMessages.length();
        String msg = "";
        while (!messages.equals(msg)) {
            try {
                msg = getMessages(true, length);
            } catch (InterruptedException iex) {
                throw (InterruptedException) new InterruptedException("Interrupted while '" + messages + "' remains to be received.").initCause(iex);
            }
            if (!messages.startsWith(msg)) {
                assertEquals(messages, msg);
                return false;
            }
            length -= msg.length();
            messages = messages.substring(msg.length());
            msg = "";
        }
        return true;
    }

    public String receiveMessages(String... messageParts) throws InterruptedException {
        return receiveMessages(false, messageParts);
    }

    public String receiveMessages(boolean ignoreNotMatched, String... messageParts) throws InterruptedException {
        int part = 0;
        int pos = 0;
        StringBuilder allMessages = new StringBuilder();
        synchronized (exec.receivedMessages) {
            do {
                String messages;
                do {
                    messages = exec.receivedMessages.toString();
                    if (messages.isEmpty()) {
                        exec.receivedMessages.wait();
                    } else {
                        break;
                    }
                } while (true);
                allMessages.append(messages);
                if (part == 0) {
                    int l = messageParts[0].length();
                    if (ignoreNotMatched) {
                        int minl = Math.min(l, allMessages.length());
                        if (!messageParts[0].substring(0, minl).equals(allMessages.substring(0, minl))) {
                            return null;
                        }
                    }
                    if (allMessages.length() < l) {
                        continue;
                    }
                    assertEquals(messageParts[0], allMessages.substring(0, l));
                    pos = l;
                    part++;
                }
                while (part < messageParts.length) {
                    int index = allMessages.indexOf(messageParts[part], pos);
                    if (index >= pos) {
                        pos = index + messageParts[part].length();
                        part++;
                    } else {
                        break;
                    }
                }
                if (part < messageParts.length) {
                    continue;
                }
                int end = pos - allMessages.length() + messages.length();
                exec.receivedMessages.delete(0, end);
                allMessages.delete(pos, allMessages.length());
                break;
            } while (exec.receivedMessages.delete(0, exec.receivedMessages.length()) != null);
        }
        return allMessages.toString();
    }

    private static class InspectExecThread extends Thread implements MessageEndpoint {

        private final boolean suspend;
        private final boolean inspectInternal;
        private final boolean inspectInitialization;
        private final List<URI> sourcePath;
        private InspectServerSession inspect;
        private ConnectionWatcher connectionWatcher;
        private long contextId;
        private InspectorExecutionContext inspectorContext;
        private Source evalSource;
        private CompletableFuture<Value> evalValue;
        private boolean done = false;
        private final StringBuilder receivedMessages = new StringBuilder();
        private final Semaphore initialized = new Semaphore(0);
        private Throwable error;
        final Object lock = new Object();
        final ProxyOutputStream err = new ProxyOutputStream(System.err);
        private final EnginesGCedTest.GCCheck gcCheck;

        InspectExecThread(boolean suspend, final boolean inspectInternal, final boolean inspectInitialization, List<URI> sourcePath) {
            super("Inspector Executor");
            this.suspend = suspend;
            this.inspectInternal = inspectInternal;
            this.inspectInitialization = inspectInitialization;
            this.sourcePath = sourcePath;
            this.gcCheck = new EnginesGCedTest.GCCheck();
        }

        @Override
        public void run() {
            Engine engine = Engine.newBuilder().err(err).build();
            gcCheck.addEngineReference(engine);
            Instrument testInstrument = engine.getInstruments().get(InspectorTestInstrument.ID);
            InspectSessionInfoProvider sessionInfoProvider = testInstrument.lookup(InspectSessionInfoProvider.class);
            InspectSessionInfo sessionInfo = sessionInfoProvider.getSessionInfo(suspend, inspectInternal, inspectInitialization, sourcePath);
            inspect = sessionInfo.getInspectServerSession();
            try {
                connectionWatcher = sessionInfo.getConnectionWatcher();
                contextId = sessionInfo.getId();
                inspectorContext = sessionInfo.getInspectorContext();
                inspect.setMessageListener(this);
                Context context = Context.newBuilder().engine(engine).allowAllAccess(true).build();
                initialized.release();
                Source source = null;
                CompletableFuture<Value> valueFuture = null;
                do {
                    synchronized (lock) {
                        if (evalSource != null) {
                            source = evalSource;
                            valueFuture = evalValue;
                            evalSource = null;
                            evalValue = null;
                        } else {
                            source = null;
                            valueFuture = null;
                            if (!done) {
                                try {
                                    lock.wait();
                                } catch (InterruptedException ex) {
                                }
                            }
                        }
                    }
                    if (source != null) {
                        Value value = context.eval(source);
                        valueFuture.complete(value);
                    }
                } while (!done);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t) {
                error = t;
            } finally {
                inspect.sendClose();
                inspect = null;
                inspectorContext = null;
                evalValue = null;
            }
        }

        private Future<Value> eval(Source source) {
            Future<Value> valueFuture;
            synchronized (lock) {
                evalSource = source;
                valueFuture = evalValue = new CompletableFuture<>();
                lock.notifyAll();
            }
            return valueFuture;
        }

        @Override
        public void sendText(String message) {
            synchronized (receivedMessages) {
                receivedMessages.append(message);
                receivedMessages.append('\n');
                receivedMessages.notifyAll();
            }
        }

        @Override
        public void sendBinary(ByteBuffer data) throws IOException {
            fail("Unexpected binary message");
        }

        @Override
        public void sendPing(ByteBuffer data) throws IOException {
        }

        @Override
        public void sendPong(ByteBuffer data) throws IOException {
        }

        @Override
        public void sendClose() throws IOException {
        }

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
