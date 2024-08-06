/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.MessageEndpoint;

import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
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
        return start(new Options(suspend, inspectInternal, inspectInitialization));
    }

    public static InspectorTester start(Options options)
                    throws InterruptedException {
        RemoteObject.resetIDs();
        ExceptionDetails.resetIDs();
        InspectorExecutionContext.resetIDs();
        InspectExecThread exec = new InspectExecThread(options.isSuspend(), options.isInspectInternal(), options.isInspectInitialization(), options.getSourcePath(), options.getProlog(),
                        options.getSuspensionTimeout());
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
        try {
            exec.inspect.sendText(message);
        } catch (IOException e) {
            fail(e.getLocalizedMessage());
        }
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

    public static final class Options {

        private boolean suspend;
        private boolean inspectInternal;
        private boolean inspectInitialization;
        private List<URI> sourcePath = Collections.emptyList();
        private Consumer<Context> prolog;
        private Long suspensionTimeout;

        public Options(boolean suspend) {
            this.suspend = suspend;
        }

        public Options(boolean suspend, final boolean inspectInternal, final boolean inspectInitialization) {
            this.suspend = suspend;
            this.inspectInternal = inspectInternal;
            this.inspectInitialization = inspectInitialization;
        }

        public boolean isSuspend() {
            return suspend;
        }

        public Options setSuspend(boolean suspend) {
            this.suspend = suspend;
            return this;
        }

        public boolean isInspectInternal() {
            return inspectInternal;
        }

        public Options setInspectInternal(boolean inspectInternal) {
            this.inspectInternal = inspectInternal;
            return this;
        }

        public boolean isInspectInitialization() {
            return inspectInitialization;
        }

        public Options setInspectInitialization(boolean inspectInitialization) {
            this.inspectInitialization = inspectInitialization;
            return this;
        }

        public List<URI> getSourcePath() {
            return sourcePath;
        }

        public Options setSourcePath(List<URI> sourcePath) {
            this.sourcePath = sourcePath;
            return this;
        }

        public Consumer<Context> getProlog() {
            return prolog;
        }

        public Options setProlog(Consumer<Context> prolog) {
            this.prolog = prolog;
            return this;
        }

        public Long getSuspensionTimeout() {
            return suspensionTimeout;
        }

        public Options setSuspensionTimeout(Long suspensionTimeout) {
            this.suspensionTimeout = suspensionTimeout;
            return this;
        }
    }

    private static class InspectExecThread extends Thread implements MessageEndpoint {

        private final boolean suspend;
        private final boolean inspectInternal;
        private final boolean inspectInitialization;
        private final List<URI> sourcePath;
        private final Consumer<Context> prolog;
        private final Long suspensionTimeout;
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

        InspectExecThread(boolean suspend, final boolean inspectInternal, final boolean inspectInitialization, List<URI> sourcePath, Consumer<Context> prolog, Long suspensionTimeout) {
            super("Inspector Executor");
            this.suspend = suspend;
            this.inspectInternal = inspectInternal;
            this.inspectInitialization = inspectInitialization;
            this.sourcePath = sourcePath;
            this.prolog = prolog;
            this.suspensionTimeout = suspensionTimeout;
            this.gcCheck = new EnginesGCedTest.GCCheck();
        }

        @Override
        public void run() {
            Engine engine = Engine.newBuilder().err(err).build();
            gcCheck.addReference(engine);
            Context context = Context.newBuilder().engine(engine).allowAllAccess(true).build();
            if (prolog != null) {
                prolog.accept(context);
            }
            Instrument testInstrument = engine.getInstruments().get(InspectorTestInstrument.ID);
            InspectSessionInfoProvider sessionInfoProvider = testInstrument.lookup(InspectSessionInfoProvider.class);
            InspectSessionInfo sessionInfo = sessionInfoProvider.getSessionInfo(suspend, inspectInternal, inspectInitialization, sourcePath, suspensionTimeout);
            inspect = sessionInfo.getInspectServerSession();
            try {
                connectionWatcher = sessionInfo.getConnectionWatcher();
                contextId = sessionInfo.getId();
                inspectorContext = sessionInfo.getInspectorContext();
                inspect.open(this);
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
                        inspectorContext.waitForRunPermission();
                        Value value = context.eval(source);
                        valueFuture.complete(value);
                    }
                } while (!done);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t) {
                error = t;
                sendText("\nERROR: " + t.getClass().getName() + ": " + t.getLocalizedMessage());
            } finally {
                engine.close();
                // To cleanup any references to the closed context, we need to set a new instance
                // of ProxyLanguage.
                ProxyLanguage.setDelegate(new ProxyLanguage());
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
