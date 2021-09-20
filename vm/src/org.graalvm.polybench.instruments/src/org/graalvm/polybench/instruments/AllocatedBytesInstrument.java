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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.sun.management.ThreadMXBean;

@TruffleInstrument.Registration(id = AllocatedBytesInstrument.ID, name = "Polybench Allocated Bytes Instrument")
public final class AllocatedBytesInstrument extends TruffleInstrument {

    public static final String ID = "allocated-bytes";
    @Option(name = "", help = "Enable the Allocated Bytes Instrument (default: false).", category = OptionCategory.EXPERT) static final OptionKey<Boolean> enabled = new OptionKey<>(false);
    public final List<ThreadContext> threads = new ArrayList<>();
    final ContextThreadLocal<ThreadContext> sandboxThreadContext = createContextThreadLocal(this::createThreadContext);
    private ServerSocket serverSocket;

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new AllocatedBytesInstrumentOptionDescriptors();
    }

    @SuppressWarnings("unused")
    private synchronized ThreadContext createThreadContext(TruffleContext context, Thread thread) {
        ThreadContext threadContext = new ThreadContext(thread);
        threads.add(threadContext);
        return threadContext;
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
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private static final class ThreadContext {

        private static volatile ThreadMXBean threadBean;
        private final Thread thread;
        private long lastAllocatedBytesSnapshot;

        private ThreadContext(Thread thread) {
            this.thread = thread;
            lastAllocatedBytesSnapshot = getThreadAllocatedBytes();
        }

        private synchronized long getAllocatedBytes() {
            long threadAllocatedBytes = getThreadAllocatedBytes();
            long increase = threadAllocatedBytes - this.lastAllocatedBytesSnapshot;
            lastAllocatedBytesSnapshot = threadAllocatedBytes;
            return increase;
        }

        private long getThreadAllocatedBytes() {
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
