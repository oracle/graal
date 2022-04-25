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

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.ThreadsListener;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.sun.management.ThreadMXBean;

@TruffleInstrument.Registration(id = MemoryUsageInstrument.ID, name = "Polybench Memory Usage Instrument")
public final class MemoryUsageInstrument extends TruffleInstrument {

    public static final String ID = "memory-usage";

    @Option(name = "", help = "Enable the Memory Usge Instrument (default: false).", category = OptionCategory.EXPERT) //
    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);

    private static final ThreadMXBean THREAD_BEAN = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private final Set<Thread> threads = new HashSet<>();
    private final ConcurrentHashMap<TruffleContext, MemoryTracking> memoryTrackedContexts = new ConcurrentHashMap<>();

    private Env currentEnv;

    final Map<String, TruffleObject> functions = new HashMap<>();
    {
        functions.put("getAllocatedBytes", new GetAllocatedBytesFunction());
        functions.put("getContextHeapSize", new GetContextHeapSize());
        functions.put("startContextMemoryTracking", new StartContextMemoryTrackingFunction());
        functions.put("stopContextMemoryTracking", new StopContextMemoryTrackingFunction());
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new MemoryUsageInstrumentOptionDescriptors();
    }

    @TruffleBoundary
    public long getContextHeapSize() {
        TruffleContext context = currentEnv.getEnteredContext();
        AtomicBoolean b = new AtomicBoolean();
        return currentEnv.calculateContextHeapSize(context, Long.MAX_VALUE, b);
    }

    @Override
    protected synchronized void onCreate(Env env) {
        this.currentEnv = env;
        env.getInstrumenter().attachContextsListener(new ContextsListener() {
            @Override
            public void onContextCreated(TruffleContext c) {
                try {
                    Object polyglotBindings;
                    Object prev = c.enter(null);
                    try {
                        polyglotBindings = env.getPolyglotBindings();
                    } finally {
                        c.leave(null, prev);
                    }
                    InteropLibrary interop = InteropLibrary.getUncached(polyglotBindings);
                    for (Map.Entry<String, TruffleObject> function : functions.entrySet()) {
                        String key = function.getKey();
                        if (!interop.isMemberExisting(polyglotBindings, key)) {
                            interop.writeMember(polyglotBindings, key, function.getValue());
                        }
                    }

                } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException e) {
                    throw CompilerDirectives.shouldNotReachHere("Exception during interop.");
                }
            }

            @Override
            public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
            }

            @Override
            public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
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

        env.getInstrumenter().attachThreadsListener(new ThreadsListener() {
            @Override
            public void onThreadInitialized(TruffleContext context, Thread thread) {
                synchronized (MemoryUsageInstrument.this) {
                    threads.add(thread);
                }
            }

            @Override
            public void onThreadDisposed(TruffleContext context, Thread thread) {
                synchronized (MemoryUsageInstrument.this) {
                    threads.remove(thread);
                }
            }
        }, true);
    }

    final class GetAllocatedBytesFunction extends BaseFunction {

        @Override
        Object call() {
            long report = 0;
            synchronized (MemoryUsageInstrument.this) {
                for (Thread thread : threads) {
                    report = report + THREAD_BEAN.getThreadAllocatedBytes(thread.getId());
                }
            }
            return report;
        }
    }

    final class GetContextHeapSize extends BaseFunction {

        @Override
        Object call() {
            return currentEnv.calculateContextHeapSize(currentEnv.getEnteredContext(), Long.MAX_VALUE, new AtomicBoolean());
        }
    }

    final class StartContextMemoryTrackingFunction extends BaseFunction {

        @Override
        Object call() {
            TruffleContext context = currentEnv.getEnteredContext();
            MemoryTracking tracking = memoryTrackedContexts.computeIfAbsent(context, (c) -> new MemoryTracking(c));
            synchronized (tracking) {
                if (tracking.actionFuture != null) {
                    throw new IllegalStateException("still running");
                }
                tracking.previousProperties = null;
                tracking.action = new ContextHeapSizeThreadLocalAction();
                // force update on start
                tracking.action.computeUpdate(true);
                tracking.actionFuture = currentEnv.submitThreadLocal(context, null, tracking.action);

                return NullValue.NULL;
            }
        }
    }

    final class StopContextMemoryTrackingFunction extends BaseFunction {

        @Override
        Object call() {
            TruffleContext context = currentEnv.getEnteredContext();
            MemoryTracking tracking = memoryTrackedContexts.computeIfAbsent(context, (c) -> new MemoryTracking(c));
            synchronized (tracking) {
                if (tracking.previousProperties != null) {
                    return tracking.previousProperties;
                }
                if (tracking.actionFuture == null) {
                    return NullValue.NULL;
                }

                tracking.actionFuture.cancel(true);
                tracking.actionFuture = null;

                Map<String, Object> properties = new HashMap<>();

                // force update on stop
                tracking.action.computeUpdate(true);

                LongSummaryStatistics statistics = tracking.action.statistics;
                properties.put("contextHeapCount", statistics.getCount());
                properties.put("contextHeapAverage", statistics.getAverage());
                properties.put("contextHeapMin", statistics.getMin());
                properties.put("contextHeapMax", statistics.getMax());

                // stop running actions for other threads
                tracking.action.cancelled.set(true);
                tracking.previousProperties = new ReadOnlyProperties(properties);
                return tracking.previousProperties;
            }
        }
    }

    static class MemoryTracking {

        final TruffleContext context;

        Future<Void> actionFuture;
        ContextHeapSizeThreadLocalAction action;
        ReadOnlyProperties previousProperties;

        MemoryTracking(TruffleContext context) {
            this.context = context;
        }

    }

    final class ContextHeapSizeThreadLocalAction extends ThreadLocalAction {

        final LongSummaryStatistics statistics = new LongSummaryStatistics();
        final AtomicBoolean cancelled = new AtomicBoolean();
        final ConcurrentHashMap<Thread, Thread> seenThreads = new ConcurrentHashMap<>();

        volatile Thread[] threadsArray;

        volatile long previousSize;
        volatile long previousMax;
        final AtomicLong previousThreadAllocatedBytes = new AtomicLong();

        long totalAllocatedMemory;

        protected ContextHeapSizeThreadLocalAction() {
            super(true, false, true);
        }

        @Override
        protected void perform(Access access) {
            computeUpdate(false);
        }

        long computeUpdate(boolean force) {
            if (needsUpdate() || force) {
                TruffleContext context = currentEnv.getEnteredContext();
                synchronized (this) {
                    long heapSize = currentEnv.calculateContextHeapSize(context, Long.MAX_VALUE, cancelled);
                    this.statistics.accept(heapSize);
                    this.previousSize = heapSize;
                    this.previousMax = statistics.getMax();
                    this.previousThreadAllocatedBytes.set(getThreadAllocatedBytes());
                    return heapSize;
                }
            }
            return this.previousSize;
        }

        private boolean needsUpdate() {
            /*
             * This action is called recurring (every safepoint). We therefore need to make sure we
             * keep the overhead reasonable, e.g. use lock free data structures here.
             */
            Thread prev = seenThreads.putIfAbsent(Thread.currentThread(), Thread.currentThread());
            if (prev == null || threadsArray == null) {
                threadsArray = seenThreads.keySet().toArray(new Thread[0]);
            }

            long threadAllocatedBytes = getThreadAllocatedBytes();
            /*
             * The idea is that this scales with the maximum retained memory. We recompute the total
             * consumption only if at least a 4th of the heap was freshly allocated. The idea is to
             * strike a trade off between overhead and precision as computation may be quite
             * expensive.
             */
            long allocationUpdateDiffBytes = previousMax / 4L;
            long previousAllocatedBytes;
            boolean update = false;
            do {
                previousAllocatedBytes = this.previousThreadAllocatedBytes.get();
                update = (threadAllocatedBytes - previousAllocatedBytes) > allocationUpdateDiffBytes;
            } while (update && !this.previousThreadAllocatedBytes.compareAndSet(previousAllocatedBytes, threadAllocatedBytes));
            return update;
        }

        private long getThreadAllocatedBytes() {
            long threadAllocatedBytes = 0;
            for (Thread thread : threadsArray) {
                threadAllocatedBytes += THREAD_BEAN.getThreadAllocatedBytes(thread.getId());
            }
            return threadAllocatedBytes;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class ReadOnlyProperties implements TruffleObject {

        private final Map<String, Object> map;

        ReadOnlyProperties(Map<String, Object> map) {
            this.map = map;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @SuppressWarnings("unused")
        @ExportMessage
        @TruffleBoundary
        Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
            return new ReadonlyStringArray(map.keySet().toArray(new String[0]));
        }

        @ExportMessage
        @TruffleBoundary
        Object readMember(String member) throws UnknownIdentifierException {
            if (!map.containsKey(member)) {
                throw UnknownIdentifierException.create(member);
            }
            return map.get(member);
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberReadable(String member) {
            return map.containsKey(member);
        }

    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class ReadonlyStringArray implements TruffleObject {

        private final String[] members;

        ReadonlyStringArray(String[] members) {
            this.members = members;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return members[(int) index];
        }

        @ExportMessage
        long getArraySize() {
            return members.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index < members.length;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class NullValue implements TruffleObject {

        static final NullValue NULL = new NullValue();

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isNull() {
            return true;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public abstract class BaseFunction implements TruffleObject {

        @ExportMessage
        final boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object execute(Object... args) throws ArityException {
            if (args.length != 0) {
                throw ArityException.create(0, 0, args.length);
            }
            return call();
        }

        abstract Object call();
    }

}
