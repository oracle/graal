/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench.instruments;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.api.instrumentation.ThreadsListener;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.sun.management.ThreadMXBean;

@TruffleInstrument.Registration(id = AllocatedBytesInstrument.ID, name = "Polybench Allocated Bytes Instrument")
public final class AllocatedBytesInstrument extends TruffleInstrument {

    public static final String ID = "allocated-bytes";
    @Option(name = "", help = "Enable the Allocated Bytes Instrument (default: false).", category = OptionCategory.EXPERT) static final OptionKey<Boolean> enabled = new OptionKey<>(false);
    public final List<ThreadContext> threads = new ArrayList<>();
    private ServerSocket serverSocket;
    private Thread socketThread;

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new AllocatedBytesInstrumentOptionDescriptors();
    }

    public synchronized double getAllocated() {
        CompilerAsserts.neverPartOfCompilation();
        double report = 0;
        for (ThreadContext thread : threads) {
            report = report + thread.getAllocatedBytes();
        }
        return report;
    }

    @Override
    protected synchronized void onCreate(Env env) {
        env.getInstrumenter().attachThreadsListener(new ThreadsListener() {
            @Override
            public void onThreadInitialized(TruffleContext context, Thread thread) {
                threads.add(new ThreadContext(thread));
            }

            @Override
            public void onThreadDisposed(TruffleContext context, Thread thread) {
                threads.removeIf(e -> e.thread.equals(thread));
            }
        }, true);
        socketThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(8877);
                while (true) {
                    Socket socket = serverSocket.accept();
                    try (DataOutputStream stream = new DataOutputStream(socket.getOutputStream())) {
                        stream.writeDouble(getAllocated());
                    }
                }
            } catch (SocketException ignored) {
                // Thrown when socket is closed
            } catch (IOException e) {
                throw new IllegalStateException("IO exception in socket use.", e);
            }
        });
        socketThread.start();
    }

    @Override
    protected synchronized void onDispose(Env env) {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            socketThread.join(1000);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Exception when closing socket.", e);
        }
    }

    private static final class ThreadContext {

        private static volatile ThreadMXBean threadBean;
        private final Thread thread;

        private ThreadContext(Thread thread) {
            Objects.requireNonNull(thread);
            this.thread = thread;
        }

        private synchronized long getAllocatedBytes() {
            if (threadBean == null) {
                threadBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            }
            return threadBean.getThreadAllocatedBytes(thread.getId());
        }
    }
}
