/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import static org.junit.Assert.assertEquals;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import com.oracle.truffle.tools.chromeinspector.TruffleExecutionContext;
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
        RemoteObject.resetIDs();
        ExceptionDetails.resetIDs();
        TruffleExecutionContext.resetIDs();
        InspectExecThread exec = new InspectExecThread(suspend, inspectInternal, inspectInitialization);
        exec.start();
        exec.initialized.acquire();
        return new InspectorTester(exec);
    }

    public void finish() throws InterruptedException {
        finish(false);
    }

    public Throwable finishErr() throws InterruptedException {
        return finish(true);
    }

    private Throwable finish(boolean expectError) throws InterruptedException {
        synchronized (exec) {
            exec.done = true;
            exec.catchError = expectError;
            exec.notifyAll();
        }
        exec.join();
        RemoteObject.resetIDs();
        ExceptionDetails.resetIDs();
        TruffleExecutionContext.resetIDs();
        return exec.error;
    }

    public boolean shouldWaitForClose() {
        return exec.connectionWatcher.shouldWaitForClose();
    }

    public long getContextId() {
        return exec.contextId;
    }

    public Future<Value> eval(Source source) {
        return exec.eval(source);
    }

    public void sendMessage(String message) {
        exec.inspect.onMessage(message);
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

    private static class InspectExecThread extends Thread implements InspectServerSession.MessageListener {

        private final boolean suspend;
        private boolean inspectInternal = false;
        private boolean inspectInitialization = false;
        private Context context;
        private InspectServerSession inspect;
        private ConnectionWatcher connectionWatcher;
        private long contextId;
        private Source evalSource;
        private CompletableFuture<Value> evalValue;
        private boolean done = false;
        private final StringBuilder receivedMessages = new StringBuilder();
        private final Semaphore initialized = new Semaphore(0);
        private boolean catchError;
        private Throwable error;

        InspectExecThread(boolean suspend, final boolean inspectInternal, final boolean inspectInitialization) {
            super("Inspector Executor");
            this.suspend = suspend;
            this.inspectInternal = inspectInternal;
            this.inspectInitialization = inspectInitialization;
        }

        @Override
        public void run() {
            Engine engine = Engine.create();
            Instrument testInstrument = engine.getInstruments().get(InspectorTestInstrument.ID);
            InspectSessionInfoProvider sessionInfoProvider = testInstrument.lookup(InspectSessionInfoProvider.class);
            InspectSessionInfo sessionInfo = sessionInfoProvider.getSessionInfo(suspend, inspectInternal, inspectInitialization);
            inspect = sessionInfo.getInspectServerSession();
            try {
                connectionWatcher = sessionInfo.getConnectionWatcher();
                contextId = sessionInfo.getId();
                inspect.setMessageListener(this);
                context = Context.newBuilder().engine(engine).allowAllAccess(true).build();
                initialized.release();
                Source source = null;
                CompletableFuture<Value> valueFuture = null;
                do {
                    synchronized (this) {
                        if (evalSource != null) {
                            source = evalSource;
                            valueFuture = evalValue;
                            evalSource = null;
                            evalValue = null;
                        } else {
                            source = null;
                            valueFuture = null;
                            try {
                                wait();
                            } catch (InterruptedException ex) {
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
                if (catchError) {
                    error = t;
                } else {
                    throw t;
                }
            } finally {
                inspect.dispose();
            }
        }

        private Future<Value> eval(Source source) {
            Future<Value> valueFuture;
            synchronized (this) {
                evalSource = source;
                valueFuture = evalValue = new CompletableFuture<>();
                notifyAll();
            }
            return valueFuture;
        }

        @Override
        public void sendMessage(String message) {
            synchronized (receivedMessages) {
                receivedMessages.append(message);
                receivedMessages.append('\n');
                receivedMessages.notifyAll();
            }
        }

    }
}
