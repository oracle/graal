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
package org.graalvm.compiler.truffle.runtime.hotspot.libgraal;

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilerIdleDelay;
import static org.graalvm.libgraal.LibGraalScope.getIsolateThread;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompiler;
import org.graalvm.compiler.truffle.runtime.hotspot.AbstractHotSpotTruffleRuntime;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.libgraal.LibGraalObject;
import org.graalvm.libgraal.LibGraalScope;
import org.graalvm.libgraal.LibGraalScope.DetachAction;
import org.graalvm.util.OptionsEncoder;

import com.oracle.truffle.api.TruffleRuntime;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;

/**
 * A {@link TruffleRuntime} that uses libgraal for compilation.
 */
final class LibGraalTruffleRuntime extends AbstractHotSpotTruffleRuntime {

    /**
     * Handle to a HSTruffleCompilerRuntime object in an libgraal heap.
     */
    static final class Handle extends LibGraalObject {
        Handle(long handle) {
            super(handle);
        }
    }

    @SuppressWarnings("try")
    LibGraalTruffleRuntime() {
        runtime().registerNativeMethods(TruffleToLibGraalCalls.class);
    }

    long handle() {
        try (LibGraalScope scope = new LibGraalScope()) {
            return scope.getIsolate().getSingleton(Handle.class, () -> {
                MetaAccessProvider metaAccess = runtime().getHostJVMCIBackend().getMetaAccess();
                HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) metaAccess.lookupJavaType(getClass());
                long classLoaderDelegate = LibGraal.translate(type);
                return new Handle(TruffleToLibGraalCalls.initializeRuntime(getIsolateThread(), LibGraalTruffleRuntime.this, classLoaderDelegate));
            }).getHandle();
        }
    }

    @SuppressWarnings("try")
    @Override
    public HotSpotTruffleCompiler newTruffleCompiler() {
        try (LibGraalScope scope = new LibGraalScope()) {
            return new LibGraalHotSpotTruffleCompiler(this);
        }
    }

    @SuppressWarnings("try")
    @Override
    protected String initLazyCompilerConfigurationName() {
        try (LibGraalScope scope = new LibGraalScope(DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            return TruffleToLibGraalCalls.getCompilerConfigurationFactoryName(getIsolateThread(), handle());
        }
    }

    @Override
    protected AutoCloseable openCompilerThreadScope() {
        return new CompilerThreadScope();
    }

    @Override
    protected long getCompilerIdleDelay(OptimizedCallTarget callTarget) {
        return callTarget.getOptionValue(CompilerIdleDelay);
    }

    @SuppressWarnings("try")
    @Override
    protected Map<String, Object> createInitialOptions() {
        try (LibGraalScope scope = new LibGraalScope(DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
            byte[] serializedOptions = TruffleToLibGraalCalls.getInitialOptions(getIsolateThread(), handle());
            return OptionsEncoder.decode(serializedOptions);
        }
    }

    @Override
    protected OutputStream getDefaultLogStream() {
        return TTYStream.INSTANCE;
    }

    private static class CompilerThreadScope implements AutoCloseable {

        private static Set<Thread> enteredThreads = Collections.newSetFromMap(new ConcurrentHashMap<Thread, Boolean>());

        private final LibGraalScope libGraalScope;

        CompilerThreadScope() {
            this.libGraalScope = new LibGraalScope(DetachAction.DETACH_RUNTIME_AND_RELEASE);
            enteredThreads.add(Thread.currentThread());
        }

        @Override
        @SuppressWarnings("try")
        public void close() {
            try (LibGraalScope s = libGraalScope) {
                enteredThreads.remove(Thread.currentThread());
            }
        }

        static boolean isEntered() {
            return enteredThreads.contains(Thread.currentThread());
        }
    }

    /**
     * Gets an output stream that write data to a libgraal TTY stream.
     */
    static final class TTYStream extends OutputStream {

        static final TTYStream INSTANCE = new TTYStream();

        private TTYStream() {
        }

        @SuppressWarnings("try")
        @Override
        public void write(int b) throws IOException {
            if (CompilerThreadScope.isEntered()) {
                TruffleToLibGraalCalls.ttyWriteByte(LibGraalScope.current().getIsolateThreadAddress(), b);
            } else {
                TTYWriter.getInstance().write(b);
            }
        }

        @SuppressWarnings("try")
        @Override
        public void write(byte[] b, int off, int len) {
            if (CompilerThreadScope.isEntered()) {
                TruffleToLibGraalCalls.ttyWriteBytes(LibGraalScope.current().getIsolateThreadAddress(), b, off, len);
            } else {
                TTYWriter.getInstance().write(b, off, len);
            }
        }
    }

    private static final class TTYWriter extends Thread {

        private static final int TIMEOUT = 2500;    // milliseconds

        private static volatile TTYWriter instance;

        private final BlockingQueue<Runnable> requests;

        private TTYWriter() {
            requests = new LinkedBlockingQueue<>();
            setName("LibGraal TTY Writer thread.");
            setDaemon(true);
        }

        @Override
        @SuppressWarnings("try")
        public void run() {
            while (true) {
                try {
                    Runnable r = requests.take();
                    try (LibGraalScope scope = new LibGraalScope(DetachAction.DETACH_RUNTIME_AND_RELEASE)) {
                        do {
                            r.run();
                            r = requests.poll(TIMEOUT, TimeUnit.MILLISECONDS);
                        } while (r != null);
                    }
                } catch (InterruptedException ie) {
                }
            }
        }

        void write(int b) {
            scheduleRequest(() -> {
                TruffleToLibGraalCalls.ttyWriteByte(LibGraalScope.current().getIsolateThreadAddress(), b);
            });
        }

        void write(byte[] b, int offset, int len) {
            scheduleRequest(() -> {
                TruffleToLibGraalCalls.ttyWriteBytes(LibGraalScope.current().getIsolateThreadAddress(), b, offset, len);
            });
        }

        private void scheduleRequest(Runnable runnable) {
            RunnableFuture<Boolean> future = new FutureTask<>(runnable, true);
            requests.add(future);
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
            }
        }

        static TTYWriter getInstance() {
            TTYWriter res = instance;
            if (res == null) {
                synchronized (TTYWriter.class) {
                    res = instance;
                    if (res == null) {
                        res = new TTYWriter();
                        res.start();
                        instance = res;
                    }
                }
            }
            return res;
        }
    }
}
