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
import com.oracle.truffle.tools.profiler.impl.HeapHistogramInstrument;
import com.oracle.truffle.tools.profiler.impl.ProfilerToolFactory;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.graalvm.polyglot.Engine;

/**
 *
 * @author Tomas Hurka
 */
public class HeapHistogram implements Closeable {

    private static final boolean DEBUG = false;

    private static final ThreadLocal<Boolean> inRuntime = new ThreadLocal<>();

    private volatile boolean nonInternalLanguageContextInitialized = false;

    private final TruffleInstrument.Env env;

    private boolean closed = false;

    private boolean collecting = false;

    private EventBinding<?> activeBinding;

    private final Map<String, MetaObjInfo> heapInfo;

    HeapHistogram(TruffleInstrument.Env env) {
        this.env = env;
        heapInfo = new HashMap<>();
        env.getInstrumenter().attachContextsListener(new ContextsListener() {
            @Override
            public void onContextCreated(TruffleContext context) {
                if (DEBUG) System.out.println("onContextCreated " + context);
                nonInternalLanguageContextInitialized = false;
            }

            @Override
            public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
                if (DEBUG) System.out.println("onLanguageContextCreated " + context + " " + language.getName());
            }

            @Override
            public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
                if (DEBUG) System.out.println("onLanguageContextInitialized " + context + " " + language.getName());
                if (!language.isInternal()) {
                    nonInternalLanguageContextInitialized = true;
                }
            }

            @Override
            public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
                if (DEBUG) System.out.println("onLanguageContextFinalized " + context + " " + language.getName());

            }

            @Override
            public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
                if (DEBUG) System.out.println("onLanguageContextDisposed " + context + " " + language.getName());

            }

            @Override
            public void onContextClosed(TruffleContext context) {
                if (DEBUG) System.out.println("onContextClosed " + context);

            }
        }, true);
    }

    void resetHistogram() {
        assert Thread.holdsLock(this);
        if (activeBinding != null) {
            activeBinding.dispose();
            activeBinding = null;
        }
        if (!collecting || closed) {
            return;
        }
        synchronized (heapInfo) {
            heapInfo.clear();
        }
        this.activeBinding = env.getInstrumenter().attachAllocationListener(AllocationEventFilter.ANY, new Listener());
    }

    /**
     * Finds {@link MemoryTracer} associated with given engine.
     *
     * @param engine the engine to find debugger for
     * @return an instance of associated {@link MemoryTracer}
     * @since 1.0
     */
    public static HeapHistogram find(Engine engine) {
        return HeapHistogramInstrument.getHistogram(engine);
    }

    public static Set<HeapHistogram> getAllHeapHistogramInstances() {
        return HeapHistogramInstrument.getAllHeapHistograms();
    }

    /**
     * Controls whether the tracer is collecting data or not.
     *
     * @param collecting the new state of the histogram.
     * @since 0.30
     */
    public synchronized void setCollecting(boolean collecting) {
        if (closed) {
            throw new IllegalStateException("Heap Histogram is already closed.");
        }
        if (this.collecting != collecting) {
            this.collecting = collecting;
            resetHistogram();
        }
    }

    /**
     * @return whether or not the sampler is currently collecting data.
     * @since 0.30
     */
    public synchronized boolean isCollecting() {
        return collecting;
    }

    /**
     * @return The roots of the trees representing the profile of the execution.
     * @since 0.30
     */
    public Map<String, Object>[] getHeapHistogram() {
        MetaObjInfo heap[];

        synchronized (heapInfo) {
            heap = heapInfo.values().toArray(new MetaObjInfo[0]);
        }
        List<Map<String, Object>> heapHisto = new ArrayList<>(heap.length);
        for (MetaObjInfo mi : heap) {
            Map<String, Object> metaObjMap = new HashMap<>();
            metaObjMap.put("language", mi.getLanguage());
            metaObjMap.put("name", mi.getName());
            metaObjMap.put("instancesCount", mi.getInstancesCount());
            metaObjMap.put("bytes", mi.getBytes());
            heapHisto.add(metaObjMap);
        }
        return heapHisto.toArray(new Map[0]);
    }

    /**
     * Erases all the data gathered by the heap histogram.
     *
     * @since 0.30
     */
    public void clearData() {
        synchronized (heapInfo) {
            heapInfo.clear();
        }
    }

    /**
     * @return whether or not the sampler has collected any data so far.
     * @since 0.30
     */
    public boolean hasData() {
        synchronized (heapInfo) {
            return !heapInfo.isEmpty();
        }
    }

    /**
     * Closes the heap histogram for further use, deleting all the gathered
     * data.
     *
     * @since 0.30
     */
    @Override
    public void close() {
        assert Thread.holdsLock(this);
        closed = true;
        if (activeBinding != null) {
            activeBinding.dispose();
            activeBinding = null;
        }
        clearData();
    }

    private void verifyConfigAllowed() {
        assert Thread.holdsLock(this);
        if (closed) {
            throw new IllegalStateException("Heap Histogram is already closed.");
        } else if (collecting) {
            throw new IllegalStateException("Cannot change histogram configuration while collecting. Call setCollecting(false) to disable collection first.");
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
                        info.addInstanceWithSize(getAddedSize(event));
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
            try {
                LanguageInfo languageInfo = event.getLanguage();
                Object metaObject = env.findMetaObject(languageInfo, event.getValue());
                if (metaObject != null) {
                    return env.toString(languageInfo, metaObject);
                }
                return "null";
            } catch (RuntimeException ex) {
                System.out.print(".");
            }
            return null;
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

    static class MetaObjInfo {

        private long instances;
        private long bytes;
        final private String name;
        final private String language;

        MetaObjInfo(String l, String n) {
            language = l;
            name = n;
        }

        String getName() {
            return name;
        }

        String getLanguage() {
            return language;
        }

        long getInstancesCount() {
            return instances;
        }

        long getBytes() {
            return bytes;
        }

        @Override
        public int hashCode() {
            return getName().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MetaObjInfo) {
                MetaObjInfo info = (MetaObjInfo) obj;
                return getName().equals(info.getName()) && getLanguage().equals(info.getLanguage());
            }
            return false;
        }

        private void addInstanceWithSize(long addedSize) {
            instances++;
            bytes += addedSize;
        }

        private void removeInstanceWithSize(long oldSize) {
            instances--;
            bytes -= oldSize;
        }
    }

    static {
        HeapHistogramInstrument.setFactory(new ProfilerToolFactory<HeapHistogram>() {
            @Override
            public HeapHistogram create(TruffleInstrument.Env env) {
                return new HeapHistogram(env);
            }
        });
    }
}
