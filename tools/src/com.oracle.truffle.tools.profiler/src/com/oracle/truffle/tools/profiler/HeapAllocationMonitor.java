/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.profiler;

import java.io.Closeable;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.oracle.truffle.tools.profiler.impl.HeapAllocationMonitorInstrument;
import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.AllocationEvent;
import com.oracle.truffle.api.instrumentation.AllocationEventFilter;
import com.oracle.truffle.api.instrumentation.AllocationListener;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.tools.profiler.impl.ProfilerToolFactory;

/**
 * // TODO: Javadoc
 * 
 * @author Tomas Hurka
 */
public class HeapAllocationMonitor implements Closeable {

    private static final ThreadLocal<Boolean> inRuntime = new ThreadLocal<>();

    private volatile boolean nonInternalLanguageContextInitialized = false;

    private final TruffleInstrument.Env env;

    private boolean closed = false;

    private boolean collecting = false;

    private EventBinding<?> activeBinding;

    private Map<String, MetaObjInfo> heapInfo;

    private ReferenceQueue<Object> referenceQueue;

    private Map<Object, ObjLivenessWeakRef> objSet;

    private ReferenceManagerThread refThread;

    HeapAllocationMonitor(TruffleInstrument.Env env) {
        this.env = env;
        heapInfo = new HashMap<>();
        referenceQueue = new ReferenceQueue<>();
        objSet = new WeakHashMap<>();
        refThread = new ReferenceManagerThread();

        env.getInstrumenter().attachContextsListener(new ContextsListener() {
            @Override
            public void onContextCreated(TruffleContext context) {
                nonInternalLanguageContextInitialized = false;
            }

            @Override
            public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
            }

            @Override
            public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
                if (!language.isInternal()) {
                    nonInternalLanguageContextInitialized = true;
                }
            }

            @Override
            public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {

            }

            @Override
            public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {

            }

            @Override
            public void onContextClosed(TruffleContext context) {

            }
        }, true);
    }

    void resetMonitor() {
        assert Thread.holdsLock(this);
        if (activeBinding != null) {
            activeBinding.dispose();
            activeBinding = null;
        }
        if (!collecting || closed) {
            return;
        }
        clearData();
        refThread.start();
        this.activeBinding = env.getInstrumenter().attachAllocationListener(AllocationEventFilter.ANY, new Listener());
    }

    /**
     * Finds {@link HeapAllocationMonitor} associated with given engine.
     *
     * @param engine the engine to find debugger for
     * @return an instance of associated {@link HeapAllocationMonitor}
     * @since 1.0
     */
    public static HeapAllocationMonitor find(Engine engine) {
        return HeapAllocationMonitorInstrument.getMonitor(engine);
    }

    /**
     * Controls whether the {@link HeapAllocationMonitor} is collecting data or not.
     *
     * @param collecting the new state of the monitor.
     * @since 1.0
     */
    public synchronized void setCollecting(boolean collecting) {
        if (closed) {
            throw new IllegalStateException("Heap Allocation Monitor is already closed.");
        }
        if (this.collecting != collecting) {
            this.collecting = collecting;
            resetMonitor();
        }
    }

    /**
     * @return whether or not the {@link HeapAllocationMonitor} is currently collecting data.
     * @since 1.0
     */
    public synchronized boolean isCollecting() {
        return collecting;
    }

    /**
     * TODO
     * @return
     */
    public MetaObjInfo[] snapshot() {
        MetaObjInfo heap[];
        synchronized (heapInfo) {
            heap = heapInfo.values().toArray(new MetaObjInfo[0]);
        }
        return heap;
    }

    /**
     * Erases all the data gathered by the {@link HeapAllocationMonitor}.
     *
     * @since 1.0
     */
    public void clearData() {
        synchronized (heapInfo) {
            heapInfo = new HashMap<>();
            referenceQueue = new ReferenceQueue<>();
            objSet = new WeakHashMap<>();
        }
    }

    /**
     * @return whether or not the {@link HeapAllocationMonitor} has collected any data so far.
     * @since 1.0
     */
    public boolean hasData() {
        synchronized (heapInfo) {
            return !heapInfo.isEmpty();
        }
    }

    /**
     * Closes the {@link HeapAllocationMonitor} for further use, deleting all the gathered data.
     *
     * @since 1.0
     */
    @Override
    public void close() {
        assert Thread.holdsLock(this);
        closed = true;
        if (activeBinding != null) {
            activeBinding.dispose();
            activeBinding = null;
        }
        refThread.terminate();
        clearData();
    }

    private void verifyConfigAllowed() {
        assert Thread.holdsLock(this);
        if (closed) {
            throw new IllegalStateException("Heap Allocation Monitor is already closed.");
        } else if (collecting) {
            throw new IllegalStateException("Cannot change monitor configuration while collecting. Call setCollecting(false) to disable collection first.");
        }
    }

    void signalObjGC(ObjLivenessWeakRef ref) {
        if (!ref.removed) {
            MetaObjInfo info = ref.info;
            String key = info.language.concat(info.name);
            if (heapInfo.get(key) == info) {
                info.gcInstanceWithSize(ref.size);
            }
        }
    }

    private final class Listener implements AllocationListener {

        @Override
        @CompilerDirectives.TruffleBoundary
        public void onEnter(AllocationEvent event) {
            if (isRecursive()) {
                return;
            }
            try {
                if (nonInternalLanguageContextInitialized) {
                    if (event.getValue() != null) {
                        MetaObjInfo info = getMetaObjInfo(event);
                        if (info != null) {
                            long size = event.getOldSize();
                            if (size == AllocationReporter.SIZE_UNKNOWN) {
                                size = 0;
                            }
                            info.removeInstanceWithSize(event.getOldSize());
                            ObjLivenessWeakRef ref = objSet.remove(event.getValue());
                            ref.removed = true;
                        }
                    }
                }
            } finally {
                inRuntime.set(Boolean.FALSE);
            }
        }

        @Override
        @CompilerDirectives.TruffleBoundary
        public void onReturnValue(AllocationEvent event) {
            if (isRecursive()) {
                return;
            }
            try {
                if (nonInternalLanguageContextInitialized) {
                    MetaObjInfo info = getMetaObjInfo(event);
                    if (info != null) {
                        long size = getAddedSize(event);
                        info.addInstanceWithSize(size);
                        ObjLivenessWeakRef ref = new ObjLivenessWeakRef(event.getValue(), referenceQueue, info, size);
                        objSet.put(event.getValue(), ref);
                    }
                }
            } finally {
                inRuntime.set(Boolean.FALSE);
            }
        }

        private long getAddedSize(AllocationEvent event) {
            long newSize = event.getNewSize();
            long oldSize = event.getOldSize();

            if (oldSize == AllocationReporter.SIZE_UNKNOWN) {
                oldSize = 0;
            }
            if (newSize == AllocationReporter.SIZE_UNKNOWN) {
                newSize = 0;
            }
            return newSize - oldSize;
        }

        private MetaObjInfo getMetaObjInfo(AllocationEvent event) {
            String language = event.getLanguage().getName();
            String metaObjectString = getMetaObjectString(event);
            if (metaObjectString != null) {
                String key = language.concat(metaObjectString);

                synchronized (heapInfo) {
                    MetaObjInfo info = heapInfo.get(key);
                    if (info == null) {
                        info = new MetaObjInfo(language, metaObjectString);
                        heapInfo.put(key, info);
                    }
                    return info;
                }
            }
            return null;
        }

        private String getMetaObjectString(AllocationEvent event) {
            LanguageInfo languageInfo = event.getLanguage();
            Object metaObject = env.findMetaObject(languageInfo, event.getValue());
            if (metaObject != null) {
                return env.toString(languageInfo, metaObject);
            }
            return "null";
        }

        private boolean isRecursive() {
            Boolean r = inRuntime.get();
            if (r != null && r.booleanValue()) {
                return true;
            }
            inRuntime.set(Boolean.TRUE);
            return false;
        }

    }

    private static class ObjLivenessWeakRef extends PhantomReference<Object> {
        private MetaObjInfo info;
        private long size;
        private boolean removed;

        private ObjLivenessWeakRef(Object obj, ReferenceQueue<Object> rq, MetaObjInfo info, long size) {
            super(obj, rq);
            this.info = info;
            this.size = size;
        }
    }

    private class ReferenceManagerThread extends Thread {
        private volatile boolean terminated;

        public void run() {
            while (!terminated) {
                try {
                    ObjLivenessWeakRef wr = (ObjLivenessWeakRef) referenceQueue.remove(200);

                    if (wr != null && !terminated) {
                        signalObjGC(wr);
                    }
                } catch (InterruptedException ex) { /* Should not happen */
                }
            }
        }

        public void terminate() {
            terminated = true;
        }
    }

    static {
        HeapAllocationMonitorInstrument.setFactory(new ProfilerToolFactory<HeapAllocationMonitor>() {
            @Override
            public HeapAllocationMonitor create(TruffleInstrument.Env env) {
                return new HeapAllocationMonitor(env);
            }
        });
    }
}
