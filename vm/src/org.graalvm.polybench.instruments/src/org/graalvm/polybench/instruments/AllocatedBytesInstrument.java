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
import java.util.ArrayList;
import java.util.List;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.sun.management.ThreadMXBean;

@TruffleInstrument.Registration(id = AllocatedBytesInstrument.ID, name = "Polybench Allocated Bytes Instrument")
public class AllocatedBytesInstrument extends TruffleInstrument {

    @Option(name = "", help = "Enable Simple Coverage (default: false).", category = OptionCategory.USER, stability = OptionStability.STABLE) static final OptionKey<Boolean> enabled = new OptionKey<>(
                    false);
    private Thread socketThread;
    private ServerSocket serverSocket;

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new AllocatedBytesInstrumentOptionDescriptors();
    }

    public static final String ID = "allocated-bytes";
    final ContextThreadLocal<ThreadContext> sandboxThreadContext = createContextThreadLocal(this::createThreadContext);
    private Env env;
    public final List<ThreadContext> threads = new ArrayList<>();
    public static List<AllocatedBytesInstrument> instruments = new ArrayList<>();

    private ThreadContext createThreadContext(TruffleContext context, Thread thread) {
        return createThreadContext(thread);
    }

    synchronized ThreadContext createThreadContext(Thread t) {
        ThreadContext threadContext = new ThreadContext(t);
        threads.add(threadContext);
        return threadContext;
    }

    public synchronized double getAllocated() {
        double report = 0;
        for (ThreadContext thread : threads) {
            report = report + thread.getAllocatedBytes();
        }
        return report;
    }

    @Override
    protected synchronized void onCreate(Env env) {
        this.env = env;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(6666);
                while (true) {
                    Socket socket = serverSocket.accept();
                    try (DataOutputStream stream = new DataOutputStream(socket.getOutputStream())) {
                        stream.writeDouble(getAllocated());
                    }
                }
            } catch (IOException ignored) {
            }
        }).start();
    }

    @Override
    protected synchronized void onDispose(Env env) {
        this.env = env;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public static class ThreadContext {

        private final Thread thread;
        private static volatile ThreadMXBean threadBean;
        volatile long lastAllocatedBytesSnapshot;
        volatile long bytesAllocated;

        ThreadContext(Thread thread) {
            this.thread = thread;
            lastAllocatedBytesSnapshot = getThreadAllocatedBytes();
        }

        public long getAllocatedBytes() {
            long threadAllocatedBytes = getThreadAllocatedBytes();
            long increase = threadAllocatedBytes - this.lastAllocatedBytesSnapshot;
            this.bytesAllocated += increase;
            lastAllocatedBytesSnapshot = threadAllocatedBytes;
            return increase;
        }

        @CompilerDirectives.TruffleBoundary
        long getThreadAllocatedBytes() {
            Thread t = thread;
            if (t == null) {
                return 0;
            }
            ThreadMXBean bean = threadBean;
            if (bean == null) {
                /*
                 * getThreadMXBean is synchronized so better cache in a local volatile field to
                 * avoid contention.
                 */
                threadBean = bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            }
            return bean.getThreadAllocatedBytes(t.getId());
        }
    }
}
