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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.instrumentation.AllocationEvent;
import com.oracle.truffle.api.instrumentation.AllocationEventFilter;
import com.oracle.truffle.api.instrumentation.AllocationListener;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.tools.profiler.impl.HeapMonitorInstrument;
import com.oracle.truffle.tools.profiler.impl.ProfilerToolFactory;

/**
 * Implementation of a heap allocation monitor for
 * {@linkplain com.oracle.truffle.api.TruffleLanguage Truffle languages} built on top of the
 * {@linkplain TruffleInstrument Truffle instrumentation framework}.
 * <p>
 * The {@link HeapMonitor} only tracks allocations while the heap monitor is
 * {@link #setCollecting(boolean) collecting} data. This means that allocations that were performed
 * while the heap monitor was not collecting data are not tracked.
 *
 * <p>
 * Usage example: {@link HeapMonitorSnippets#example}
 *
 * @see #takeSummary()
 * @see #takeMetaObjectSummary()
 * @since 1.0
 */
public final class HeapMonitor implements Closeable {

    private static final long CLEAN_INTERVAL = 200;
    private static final ThreadLocal<Boolean> RECURSIVE = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    private final TruffleInstrument.Env env;

    private final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();
    private final ConcurrentLinkedQueue<ObjectPhantomReference> references = new ConcurrentLinkedQueue<>();
    private final Map<LanguageInfo, Map<String, DeadObjectCounters>> deadObjectCounters = new LinkedHashMap<>();
    private Thread referenceThread;

    private volatile boolean closed;
    private boolean collecting;
    private EventBinding<?> activeBinding;

    private HeapMonitor(TruffleInstrument.Env env) {
        this.env = env;
    }

    private void resetMonitor() {
        assert Thread.holdsLock(this);
        if (activeBinding != null) {
            activeBinding.dispose();
            activeBinding = null;
        }
        if (closed && referenceThread != null && referenceThread.isAlive()) {
            referenceThread.interrupt();
            referenceThread = null;
        }
        if (!collecting || closed) {
            return;
        }
        clearData();
        if (referenceThread == null) {
            this.referenceThread = new Thread(new Runnable() {
                public void run() {
                    while (!closed) {
                        cleanReferenceQueue();
                        try {
                            Thread.sleep(CLEAN_INTERVAL);
                        } catch (InterruptedException e) {
                            // fallthrough might be closed now
                        }
                    }
                }
            });
            this.referenceThread.setName("HeapMonitor Cleanup");
            this.referenceThread.setDaemon(true);
        }
        referenceThread.start();
        this.activeBinding = env.getInstrumenter().attachAllocationListener(AllocationEventFilter.ANY, new Listener());
    }

    /**
     * Returns the {@link HeapMonitor} associated with a given engine.
     *
     * @param engine the engine to find debugger for
     * @return an instance of associated {@link HeapMonitor}
     * @since 1.0
     */
    public static HeapMonitor find(Engine engine) {
        return HeapMonitorInstrument.getMonitor(engine);
    }

    /**
     * Controls whether the {@link HeapMonitor} is collecting data or not.
     *
     * @param collecting the new state of the monitor.
     * @throws IllegalStateException if the heap monitor was already closed
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
     * Returns <code>true</code> if the heap monitor is collecting data, else <code>false</code>.
     *
     * @since 1.0
     */
    public synchronized boolean isCollecting() {
        return collecting;
    }

    /**
     * Returns a summary of the current state of the heap.
     * <p>
     * The {@link HeapMonitor} only tracks allocations while the heap monitor is
     * {@link #setCollecting(boolean) collecting} data. This means that allocations that were
     * performed while the heap monitor was not collecting data are not tracked.
     *
     * @throws IllegalStateException if the heap monitor was already closed
     * @since 1.0
     */
    public HeapSummary takeSummary() {
        if (closed) {
            throw new IllegalStateException("Heap Allocation Monitor is already closed.");
        }
        HeapSummary summary = new HeapSummary();
        computeSummaryImpl((l, m) -> summary);
        return summary;
    }

    /**
     * Returns a summary of the current state of the heap grouped by language and meta object name.
     * <p>
     * The {@link HeapMonitor} only tracks allocations while the heap monitor is
     * {@link #setCollecting(boolean) collecting} data. This means that allocations that were
     * performed while the heap monitor was not collecting are ignored. In other words the
     * {@link HeapMonitor} reports snapshots as if the heap was completely empty when it was
     * "enabled".
     *
     * @throws IllegalStateException if the heap monitor was already closed
     * @since 1.0
     */
    public Map<LanguageInfo, Map<String, HeapSummary>> takeMetaObjectSummary() {
        Map<LanguageInfo, Map<String, HeapSummary>> summaries = new LinkedHashMap<>();
        computeSummaryImpl((l, m) -> getSummary(summaries, l, m));

        // make read-only
        for (Entry<LanguageInfo, Map<String, HeapSummary>> summary : summaries.entrySet()) {
            summaries.put(summary.getKey(), Collections.unmodifiableMap(summary.getValue()));
        }
        return Collections.unmodifiableMap(summaries);
    }

    private void computeSummaryImpl(BiFunction<LanguageInfo, String, HeapSummary> groupFunction) {
        cleanReferenceQueue();
        synchronized (deadObjectCounters) {
            for (Entry<LanguageInfo, Map<String, DeadObjectCounters>> objectsByLanguage : deadObjectCounters.entrySet()) {
                LanguageInfo language = objectsByLanguage.getKey();
                for (Entry<String, DeadObjectCounters> objectsByMetaObject : objectsByLanguage.getValue().entrySet()) {
                    HeapSummary summary = groupFunction.apply(language, objectsByMetaObject.getKey());
                    DeadObjectCounters deadObjects = objectsByMetaObject.getValue();
                    summary.totalInstances += deadObjects.instances;
                    summary.totalBytes += deadObjects.bytes;
                }
            }
            for (ObjectPhantomReference reference : references) {
                HeapSummary summary = groupFunction.apply(reference.language, reference.metaObject);
                long sizeDiff = reference.computeBytesDiff();
                summary.totalBytes += sizeDiff;
                summary.aliveBytes += sizeDiff;
                if (reference.oldSize == 0) { // if not a reallocation
                    summary.aliveInstances++;
                    summary.totalInstances++;
                }
            }
        }
    }

    private static HeapSummary getSummary(Map<LanguageInfo, Map<String, HeapSummary>> summaries, LanguageInfo language, String metaObject) {
        Map<String, HeapSummary> summaryMap = summaries.get(language);
        if (summaryMap == null) {
            summaryMap = new LinkedHashMap<>();
            summaries.put(language, summaryMap);
        }
        HeapSummary summary = summaryMap.get(metaObject);
        if (summary == null) {
            summary = new HeapSummary();
            summaryMap.put(metaObject, summary);
        }
        return summary;
    }

    /*
     * This is used reflectively by some tools.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Map<String, Object>[] toMap(Map<LanguageInfo, Map<String, HeapSummary>> summaries) {
        List<Map<String, Object>> heapHisto = new ArrayList<>(summaries.size());
        for (Entry<LanguageInfo, Map<String, HeapSummary>> objectsByLanguage : summaries.entrySet()) {
            LanguageInfo language = objectsByLanguage.getKey();
            for (Entry<String, HeapSummary> objectsByMetaObject : objectsByLanguage.getValue().entrySet()) {
                HeapSummary mi = objectsByMetaObject.getValue();
                Map<String, Object> metaObjMap = new HashMap<>();
                metaObjMap.put("language", language.getId());
                metaObjMap.put("name", objectsByMetaObject.getKey());
                metaObjMap.put("totalInstances", mi.getTotalInstances());
                metaObjMap.put("totalBytes", mi.getTotalBytes());
                metaObjMap.put("aliveInstances", mi.getAliveInstances());
                metaObjMap.put("aliveBytes", mi.getAliveBytes());
                heapHisto.add(metaObjMap);
            }
        }
        return heapHisto.toArray(new Map[0]);
    }

    /**
     * Erases all the data gathered by the {@link HeapMonitor}.
     *
     * @since 1.0
     */
    public void clearData() {
        synchronized (deadObjectCounters) {
            references.clear();
            deadObjectCounters.clear();
        }
    }

    /**
     * Returns <code>true</code> if the {@link HeapMonitor} has collected any data, else
     * <code>false</code>.
     *
     * @since 1.0
     */
    public boolean hasData() {
        if (!references.isEmpty()) {
            return true;
        }
        synchronized (deadObjectCounters) {
            if (!deadObjectCounters.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Closes the {@link HeapMonitor} for further use, deleting all the gathered data.
     *
     * @since 1.0
     */
    @Override
    public synchronized void close() {
        closed = true;
        resetMonitor();
        clearData();
    }

    private void cleanReferenceQueue() {
        ObjectPhantomReference reference = (ObjectPhantomReference) referenceQueue.poll();
        if (reference == null) {
            // nothing to do avoid locking
            return;
        }
        Set<ObjectPhantomReference> collectedReferences = new HashSet<>();
        synchronized (deadObjectCounters) {
            do {
                Map<String, DeadObjectCounters> counters = deadObjectCounters.get(reference.language);
                if (counters == null) {
                    counters = new LinkedHashMap<>();
                    deadObjectCounters.put(reference.language, counters);
                }
                DeadObjectCounters counter = counters.get(reference.metaObject);
                if (counter == null) {
                    counter = new DeadObjectCounters();
                    counters.put(reference.metaObject, counter);
                }
                counter.bytes += reference.computeBytesDiff();
                if (reference.oldSize == 0) { // if not a reallocation
                    counter.instances++;
                }
                collectedReferences.add(reference);
            } while ((reference = (ObjectPhantomReference) referenceQueue.poll()) != null);
            // note that ConcurrentLinkedQueue actually supports doing this
            // the iterator does not throw a ConcurrentModificationException
            references.removeAll(collectedReferences);
        }
    }

    private class Listener implements AllocationListener {

        public void onEnter(AllocationEvent event) {
            // nothing to do
        }

        @TruffleBoundary
        public void onReturnValue(AllocationEvent event) {
            Object object = event.getValue();
            if (object == null) {
                return;
            }
            LanguageInfo language = event.getLanguage();
            references.add(new ObjectPhantomReference(object, referenceQueue, language, getMetaObjectString(language, object), event.getOldSize(), event.getNewSize()));
        }

        private String getMetaObjectString(LanguageInfo language, Object value) {
            boolean recursive = RECURSIVE.get() == Boolean.TRUE;
            if (!recursive) { // recursive objects should still be registered
                RECURSIVE.set(Boolean.TRUE);
                try {
                    Object metaObject = env.findMetaObject(language, value);
                    if (metaObject != null) {
                        String toString = env.toString(language, metaObject);
                        if (toString != null) {
                            return toString;
                        }
                    }
                } finally {
                    RECURSIVE.set(Boolean.FALSE);
                }
            }
            return "Unknown";
        }

    }

    private final class DeadObjectCounters {

        long instances;
        long bytes;

    }

    private static final class ObjectPhantomReference extends PhantomReference<Object> {
        final String metaObject; // is NULL_NAME for null
        final LanguageInfo language;
        final long oldSize;
        final long newSize;

        ObjectPhantomReference(Object obj, ReferenceQueue<Object> rq, LanguageInfo language, String metaObject, long oldSize, long newSize) {
            super(obj, rq);
            this.language = language;
            this.metaObject = metaObject;
            this.oldSize = oldSize;
            this.newSize = newSize;
        }

        @SuppressWarnings("hiding")
        long computeBytesDiff() {
            long newSize = this.newSize == AllocationReporter.SIZE_UNKNOWN ? 0 : this.newSize;
            long oldSize = this.oldSize == AllocationReporter.SIZE_UNKNOWN ? 0 : this.oldSize;
            return newSize - oldSize;
        }

    }

    static {
        HeapMonitorInstrument.setFactory(new ProfilerToolFactory<HeapMonitor>() {
            @Override
            public HeapMonitor create(TruffleInstrument.Env env) {
                return new HeapMonitor(env);
            }
        });
    }

}

class HeapMonitorSnippets {

    @SuppressWarnings("unused")
    public void example() throws InterruptedException {
        // @formatter:off
        // BEGIN: HeapMonitorSnippets#example
        try (Context context = Context.create()) {
            HeapMonitor monitor = HeapMonitor.find(context.getEngine());
            monitor.setCollecting(true);
            final Thread thread = new Thread(() -> {
                context.eval("...", "...");
            });
            thread.start();
            for (int i = 0; i < 10; i++) {
                final HeapSummary summary = monitor.takeSummary();
                System.out.println(summary);
                Thread.sleep(100);
            }
            monitor.setCollecting(false);
        }
        // Print the number of live instances per meta object every 100ms.
        // END: HeapMonitorSnippets#example
        // @formatter:on
    }
}
