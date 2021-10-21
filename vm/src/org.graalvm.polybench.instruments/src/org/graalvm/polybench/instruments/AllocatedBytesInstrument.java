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
import java.util.HashSet;
import java.util.Set;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.ThreadsListener;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.sun.management.ThreadMXBean;

@TruffleInstrument.Registration(id = AllocatedBytesInstrument.ID, name = "Polybench Allocated Bytes Instrument")
public final class AllocatedBytesInstrument extends TruffleInstrument {

    public static final String ID = "allocated-bytes";
    @Option(name = "", help = "Enable the Allocated Bytes Instrument (default: false).", category = OptionCategory.EXPERT) static final OptionKey<Boolean> enabled = new OptionKey<>(false);
    private static final ThreadMXBean threadBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    public final Set<Thread> threads = new HashSet<>();
    private ServerSocket serverSocket;
    private Thread socketThread;

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new AllocatedBytesInstrumentOptionDescriptors();
    }

    public synchronized double getAllocated() {
        CompilerAsserts.neverPartOfCompilation();
        double report = 0;
        for (Thread thread : this.threads) {
            report = report + threadBean.getThreadAllocatedBytes(thread.getId());
        }
        return report;
    }

    @Override
    protected synchronized void onCreate(Env env) {
        env.getInstrumenter().attachThreadsListener(new ThreadsListener() {
            @Override
            public void onThreadInitialized(TruffleContext context, Thread thread) {
                synchronized (AllocatedBytesInstrument.this) {
                    threads.add(thread);
                }
            }

            @Override
            public void onThreadDisposed(TruffleContext context, Thread thread) {
                synchronized (AllocatedBytesInstrument.this) {
                    threads.remove(thread);
                }
            }
        }, true);
        try {
            serverSocket = new ServerSocket(8877);
        } catch (IOException e) {
            throw new IllegalStateException("IO exception in socket use.", e);
        }

        socketThread = new Thread(() -> {
            try {
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
            serverSocket.close();
            socketThread.join(1000);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Exception when closing socket.", e);
        }
    }
}
