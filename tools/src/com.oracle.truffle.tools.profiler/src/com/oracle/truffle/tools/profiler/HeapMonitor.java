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
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.AllocationEvent;
import com.oracle.truffle.api.instrumentation.AllocationEventFilter;
import com.oracle.truffle.api.instrumentation.AllocationListener;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.tools.profiler.impl.HeapMonitorInstrument;

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
 * @since 19.0
 */
public final class HeapMonitor implements Closeable {

    private static final long CLEAN_INTERVAL = 200;
    private static final ThreadLocal<Boolean> RECURSIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    private final TruffleInstrument.Env env;

    private final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();
    private final ConcurrentLinkedQueue<ObjectWeakReference> newReferences = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<ObjectWeakReference> processedReferences = new ConcurrentLinkedQueue<>();
    private final Map<LanguageInfo, Map<String, HeapSummary>> summaryData = new LinkedHashMap<>();
    private Thread referenceThread;

    private volatile boolean closed;
    private boolean collecting;
    private EventBinding<?> activeBinding;
    private final Map<LanguageInfo, LanguageInfo> initializedLanguages = new ConcurrentHashMap<>();

    private HeapMonitor(TruffleInstrument.Env env) {
        this.env = env;
        env.getInstrumenter().attachContextsListener(new ContextsListener() {
            @Override
            public void onContextCreated(TruffleContext context) {

            }

            @Override
            public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {

            }

            @Override
            public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
                initializedLanguages.put(language, language);
            }

            @Override
            public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
                initializedLanguages.remove(language);
            }

            @Override
            public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {

            }

            @Override
            public void onContextClosed(TruffleContext context) {

            }
        }, true);
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
            this.referenceThread = new Thread(() -> {
                while (!closed) {
                    cleanReferenceQueue();
                    try {
                        Thread.sleep(CLEAN_INTERVAL);
                    } catch (InterruptedException e) {
                        // fallthrough might be closed now
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
     * @since 19.0
     */
    public static HeapMonitor find(Engine engine) {
        return HeapMonitorInstrument.getMonitor(engine);
    }

    /**
     * Controls whether the {@link HeapMonitor} is collecting data or not.
     *
     * @param collecting the new state of the monitor.
     * @throws IllegalStateException if the heap monitor was already closed
     * @since 19.0
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
     * @since 19.0
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
     * @since 19.0
     */
    public HeapSummary takeSummary() {
        if (closed) {
            throw new IllegalStateException("Heap Allocation Monitor is already closed.");
        }
        HeapSummary totalSummary = new HeapSummary();
        cleanReferenceQueue();
        processNewReferences();
        synchronized (summaryData) {
            for (Map<String, HeapSummary> languages : summaryData.values()) {
                for (HeapSummary summaryEntry : languages.values()) {
                    totalSummary.add(summaryEntry);
                }
            }
        }
        return totalSummary;
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
     * @since 19.0
     */
    public Map<LanguageInfo, Map<String, HeapSummary>> takeMetaObjectSummary() {
        cleanReferenceQueue();
        processNewReferences();
        synchronized (summaryData) {
            Map<LanguageInfo, Map<String, HeapSummary>> languageMap = new LinkedHashMap<>(summaryData);
            for (Entry<LanguageInfo, Map<String, HeapSummary>> languageEntry : languageMap.entrySet()) {
                Map<String, HeapSummary> copyLanguageMap = new LinkedHashMap<>(languageEntry.getValue());
                for (Entry<String, HeapSummary> summaryEntry : copyLanguageMap.entrySet()) {
                    summaryEntry.setValue(new HeapSummary(summaryEntry.getValue()));
                }
                languageEntry.setValue(Collections.unmodifiableMap(copyLanguageMap));
            }
            return Collections.unmodifiableMap(languageMap);
        }
    }

    private void processNewReferences() {
        synchronized (summaryData) {
            ObjectWeakReference reference;
            while ((reference = newReferences.poll()) != null) {
                HeapSummary summary = getSummary(summaryData, reference.language, reference.metaObject);
                summary.totalInstances++;
                summary.aliveInstances++;
                long bytesDiff = reference.computeBytesDiff();
                summary.totalBytes += bytesDiff;
                summary.aliveBytes += bytesDiff;
                reference.processed = true;
                processedReferences.add(reference);
            }
        }
    }

    private static HeapSummary getSummary(Map<LanguageInfo, Map<String, HeapSummary>> summaries, LanguageInfo language, String metaObject) {
        Map<String, HeapSummary> summaryMap = summaries.computeIfAbsent(language, k -> new LinkedHashMap<>());
        return summaryMap.computeIfAbsent(metaObject, k -> new HeapSummary());
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
     * @since 19.0
     */
    public void clearData() {
        synchronized (summaryData) {
            newReferences.clear();
            summaryData.clear();
        }
    }

    /**
     * Returns <code>true</code> if the {@link HeapMonitor} has collected any data, else
     * <code>false</code>.
     *
     * @since 19.0
     */
    public boolean hasData() {
        if (!newReferences.isEmpty()) {
            return true;
        }
        synchronized (summaryData) {
            if (!summaryData.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Closes the {@link HeapMonitor} for further use, deleting all the gathered data.
     *
     * @since 19.0
     */
    @Override
    public synchronized void close() {
        closed = true;
        resetMonitor();
        clearData();
    }

    private void cleanReferenceQueue() {
        ObjectWeakReference reference = (ObjectWeakReference) referenceQueue.poll();
        if (reference == null) {
            // nothing to do avoid locking
            return;
        }
        Set<ObjectWeakReference> collectedNewReferences = new HashSet<>();
        Set<ObjectWeakReference> collectedProcessedReferences = new HashSet<>();
        synchronized (summaryData) {
            do {
                HeapSummary counter = getSummary(summaryData, reference.language, reference.metaObject);
                long bytesDiff = reference.computeBytesDiff();
                if (reference.processed) {
                    counter.aliveInstances--;
                    counter.aliveBytes -= bytesDiff;
                    collectedProcessedReferences.add(reference);
                } else {
                    // object never was processed alive
                    counter.totalInstances++;
                    counter.totalBytes += bytesDiff;
                    collectedNewReferences.add(reference);
                }
            } while ((reference = (ObjectWeakReference) referenceQueue.poll()) != null);
            // note that ConcurrentLinkedQueue actually supports doing this
            // the iterator does not throw a ConcurrentModificationException
            newReferences.removeAll(collectedNewReferences);
            processedReferences.removeAll(collectedProcessedReferences);
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
            if (initializedLanguages.containsKey(language)) {
                String metaInfo = getMetaObjectString(language, object);
                if (metaInfo != null) {
                    newReferences.add(new ObjectWeakReference(object, referenceQueue, language, metaInfo.intern(), event.getOldSize(), event.getNewSize()));
                }
            }
        }

        private String getMetaObjectString(LanguageInfo language, Object value) {
            boolean recursive = RECURSIVE.get() == Boolean.TRUE;
            if (!recursive) { // recursive objects should still be registered
                RECURSIVE.set(Boolean.TRUE);
                try {
                    Object view = env.getLanguageView(language, value);
                    InteropLibrary viewLib = InteropLibrary.getFactory().getUncached(view);
                    String metaObjectString = "Unknown";
                    if (viewLib.hasMetaObject(view)) {
                        try {
                            metaObjectString = INTEROP.asString(INTEROP.getMetaQualifiedName(viewLib.getMetaObject(view)));
                        } catch (UnsupportedMessageException e) {
                            CompilerDirectives.transferToInterpreter();
                            throw new AssertionError(e);
                        }
                    }
                    return metaObjectString;
                } finally {
                    RECURSIVE.set(Boolean.FALSE);
                }
            }
            return null;
        }
    }

    private static final class ObjectWeakReference extends WeakReference<Object> {
        final String metaObject; // is NULL_NAME for null
        final LanguageInfo language;
        final long oldSize;
        final long newSize;

        boolean processed;

        ObjectWeakReference(Object obj, ReferenceQueue<Object> rq, LanguageInfo language, String metaObject, long oldSize, long newSize) {
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
        HeapMonitorInstrument.setFactory(HeapMonitor::new);
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
                final long aliveInstances = summary.getAliveInstances();
                final long totalInstances = summary.getTotalInstances();
                // ...
                Thread.sleep(100);
            }
            monitor.setCollecting(false);
        }
        // Print the number of live instances per meta object every 100ms.
        // END: HeapMonitorSnippets#example
        // @formatter:on
    }
}
