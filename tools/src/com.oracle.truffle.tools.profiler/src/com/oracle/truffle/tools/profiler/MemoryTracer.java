/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.instrumentation.AllocationEvent;
import com.oracle.truffle.api.instrumentation.AllocationEventFilter;
import com.oracle.truffle.api.instrumentation.AllocationListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tools.profiler.impl.CPUTracerInstrument;
import com.oracle.truffle.tools.profiler.impl.MemoryTracerInstrument;
import com.oracle.truffle.tools.profiler.impl.ProfilerToolFactory;

/**
 * Implementation of a memory tracing profiler for
 * {@linkplain com.oracle.truffle.api.TruffleLanguage Truffle languages} built on top of the
 * {@linkplain TruffleInstrument Truffle instrumentation framework}.
 * <p>
 * The tracer counts how many times each of the elements of interest (e.g. functions, statements,
 * etc.) allocates memory, as well as meta data about the allocated object. It keeps a shadow stack
 * during execution, and listens for {@link AllocationEvent allocation events}. On each event, the
 * allocation information is associated to the top of the stack.
 * <p>
 * NOTE: This profiler is still experimental with limited capabilities.
 * <p>
 * Usage example: {@codesnippet MemoryTracerSnippets#example}
 * </p>
 *
 * @since 0.30
 */
public final class MemoryTracer implements Closeable {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    MemoryTracer(TruffleInstrument.Env env) {
        this.env = env;
    }

    private SourceSectionFilter filter = null;

    private final TruffleInstrument.Env env;

    private boolean closed = false;

    private boolean collecting = false;

    private EventBinding<?> activeBinding;

    private int stackLimit = 1000;

    private ShadowStack shadowStack;

    private EventBinding<?> stacksBinding;

    private final Map<Thread, ProfilerNode<Payload>> rootNodes = new HashMap<>();

    private boolean stackOverflowed = false;

    private static final SourceSectionFilter DEFAULT_FILTER = SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).sourceIs(new SourceSectionFilter.SourcePredicate() {
        @Override
        public boolean test(Source source) {
            return !source.isInternal();
        }
    }).build();

    void resetTracer() {
        assert Thread.holdsLock(this);
        if (activeBinding != null) {
            activeBinding.dispose();
            activeBinding = null;
        }
        if (!collecting || closed) {
            return;
        }
        SourceSectionFilter f = this.filter;
        if (f == null) {
            f = DEFAULT_FILTER;
        }
        this.shadowStack = new ShadowStack(stackLimit, f, env.getInstrumenter(), TruffleLogger.getLogger(CPUTracerInstrument.ID));
        this.stacksBinding = this.shadowStack.install(env.getInstrumenter(), f, false);

        this.activeBinding = env.getInstrumenter().attachAllocationListener(AllocationEventFilter.ANY, new Listener());
    }

    /**
     * Finds {@link MemoryTracer} associated with given engine.
     *
     * @param engine the engine to find debugger for
     * @return an instance of associated {@link MemoryTracer}
     * @since 19.0
     */
    public static MemoryTracer find(Engine engine) {
        return MemoryTracerInstrument.getTracer(engine);
    }

    /**
     * Controls whether the tracer is collecting data or not.
     *
     * @param collecting the new state of the tracer.
     * @since 0.30
     */
    public synchronized void setCollecting(boolean collecting) {
        if (closed) {
            throw new IllegalStateException("Memory Tracer is already closed.");
        }
        if (this.collecting != collecting) {
            this.collecting = collecting;
            resetTracer();
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
    public synchronized Collection<ProfilerNode<Payload>> getRootNodes() {
        ProfilerNode<Payload> copy = new ProfilerNode<>();
        for (ProfilerNode<Payload> node : rootNodes.values()) {
            copy.deepMergeChildrenFrom(node, mergePayload, payloadFactory);
        }
        return copy.getChildren();
    }

    /**
     * @return The roots of the trees representing the profile of the execution per thread.
     * @since 19.0
     */
    public synchronized Map<Thread, Collection<ProfilerNode<Payload>>> getThreadToNodesMap() {
        Map<Thread, Collection<ProfilerNode<Payload>>> returnValue = new HashMap<>();
        for (Map.Entry<Thread, ProfilerNode<Payload>> entry : rootNodes.entrySet()) {
            ProfilerNode<Payload> copy = new ProfilerNode<>();
            copy.deepCopyChildrenFrom(entry.getValue(), copyPayload);
            returnValue.put(entry.getKey(), copy.getChildren());
        }
        return Collections.unmodifiableMap(returnValue);
    }

    Supplier<Payload> payloadFactory = new Supplier<Payload>() {
        @Override
        public Payload get() {
            return new Payload();
        }
    };

    Function<Payload, Payload> copyPayload = new Function<Payload, Payload>() {
        @Override
        public Payload apply(Payload payload) {
            Payload copy = new Payload();
            copy.totalAllocations = payload.totalAllocations;
            for (AllocationEventInfo info : payload.events) {
                copy.events.add(new AllocationEventInfo(info.language, info.allocated, info.reallocation, info.metaObjectString));
            }
            return copy;
        }
    };

    BiConsumer<Payload, Payload> mergePayload = new BiConsumer<Payload, Payload>() {
        @Override
        public void accept(Payload source, Payload dest) {
            dest.totalAllocations += source.totalAllocations;
            for (AllocationEventInfo info : source.events) {
                dest.events.add(new AllocationEventInfo(info.language, info.allocated, info.reallocation, info.metaObjectString));
            }
        }
    };

    /**
     * Erases all the data gathered by the tracer.
     *
     * @since 0.30
     */
    public synchronized void clearData() {
        for (ProfilerNode<Payload> node : rootNodes.values()) {
            Map<StackTraceEntry, ProfilerNode<Payload>> rootChildren = node.children;
            if (rootChildren != null) {
                rootChildren.clear();
            }
        }
    }

    /**
     * @return whether or not the sampler has collected any data so far.
     * @since 0.30
     */
    public synchronized boolean hasData() {
        boolean hasData = false;
        for (ProfilerNode<Payload> node : rootNodes.values()) {
            Map<StackTraceEntry, ProfilerNode<Payload>> rootChildren = node.children;
            hasData |= (rootChildren != null && !rootChildren.isEmpty());
        }
        return hasData;
    }

    /**
     * @return size of the shadow stack
     * @since 0.30
     */
    public synchronized int getStackLimit() {
        return stackLimit;
    }

    /**
     * Sets the size of the shadow stack. Whether or not the shadow stack grew more than the
     * provided size during execution can be checked with {@linkplain #hasStackOverflowed}
     *
     * @param stackLimit the new size of the shadow stack
     * @since 0.30
     */
    public synchronized void setStackLimit(int stackLimit) {
        verifyConfigAllowed();
        if (stackLimit < 1) {
            throw new IllegalArgumentException(String.format("Invalid stack limit %s.", stackLimit));
        }
        this.stackLimit = stackLimit;
    }

    /**
     * @return was the shadow stack size insufficient for the execution.
     * @since 0.30
     */
    public boolean hasStackOverflowed() {
        return stackOverflowed;
    }

    /**
     * Sets the {@link SourceSectionFilter filter} for the sampler. This allows the sampler to
     * observe only parts of the executed source code.
     *
     * @param filter The new filter describing which part of the source code to sample
     * @since 0.30
     */
    public synchronized void setFilter(SourceSectionFilter filter) {
        verifyConfigAllowed();
        this.filter = filter;
    }

    /**
     * Closes the tracer for fuhrer use, deleting all the gathered data.
     *
     * @since 0.30
     */
    @Override
    public synchronized void close() {
        assert Thread.holdsLock(this);
        if (stacksBinding != null) {
            stacksBinding.dispose();
            stacksBinding = null;
        }
        if (shadowStack != null) {
            shadowStack = null;
        }
    }

    private void verifyConfigAllowed() {
        assert Thread.holdsLock(this);
        if (closed) {
            throw new IllegalStateException("Memory Tracer is already closed.");
        } else if (collecting) {
            throw new IllegalStateException("Cannot change tracer configuration while collecting. Call setCollecting(false) to disable collection first.");
        }
    }

    private final class Listener implements AllocationListener {

        /**
         * Used to prevent infinite recursions in case a language does an allocation during meta
         * object lookup or toString call.
         */
        ThreadLocal<Boolean> gettingMetaObject = ThreadLocal.withInitial(() -> false);

        @Override
        public void onEnter(AllocationEvent event) {
        }

        @Override
        @TruffleBoundary
        public void onReturnValue(AllocationEvent event) {
            if (gettingMetaObject.get()) {
                return;
            }
            ShadowStack.ThreadLocalStack stack = shadowStack.getStack(Thread.currentThread());
            if (stack == null || stack.getStackIndex() == -1) {
                // nothing on the stack
                return;
            }
            if (stack.hasStackOverflowed()) {
                stackOverflowed = true;
                return;
            }
            LanguageInfo languageInfo = event.getLanguage();
            String metaObjectString;
            gettingMetaObject.set(true);
            Object view = env.getLanguageView(languageInfo, event.getValue());
            InteropLibrary viewLib = InteropLibrary.getFactory().getUncached(view);
            if (viewLib.hasMetaObject(view)) {
                try {
                    metaObjectString = INTEROP.asString(INTEROP.getMetaQualifiedName(viewLib.getMetaObject(view)));
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(e);
                }
            } else {
                metaObjectString = "null";
            }
            gettingMetaObject.set(false);
            AllocationEventInfo info = new AllocationEventInfo(languageInfo, event.getNewSize() - event.getOldSize(), event.getOldSize() != 0, metaObjectString);
            handleEvent(stack, info);
        }

        boolean handleEvent(ShadowStack.ThreadLocalStack stack, AllocationEventInfo info) {
            StackTraceEntry[] locations = stack.getStack();
            if (locations == null) {
                return false;
            }
            synchronized (MemoryTracer.this) {
                // now traverse the stack and reconstruct the call tree
                ProfilerNode<Payload> treeNode = rootNodes.computeIfAbsent(Thread.currentThread(), new Function<Thread, ProfilerNode<Payload>>() {
                    @Override
                    public ProfilerNode<Payload> apply(Thread thread) {
                        return new ProfilerNode<>();
                    }
                });
                for (int i = 0; i < locations.length; i++) {
                    StackTraceEntry location = locations[i];
                    ProfilerNode<Payload> child = treeNode.findChild(location);
                    if (child == null) {
                        child = new ProfilerNode<>(treeNode, location, new Payload());
                        treeNode.addChild(location, child);
                    }
                    treeNode = child;
                    treeNode.getPayload().incrementTotalAllocations();
                }
                // insert event at the top of the stack
                treeNode.getPayload().getEvents().add(info);
                return true;
            }
        }
    }

    /**
     * Used as a template parameter for {@link ProfilerNode}. Holds information about
     * {@link AllocationEventInfo allocation events}.
     *
     * @since 0.30
     */
    public static final class Payload {

        Payload() {
        }

        private final List<AllocationEventInfo> events = new ArrayList<>();

        private long totalAllocations = 0;

        /**
         * @return Total number of allocations recorded while the associated element was on the
         *         shadow stack
         * @since 0.30
         */
        public long getTotalAllocations() {
            return totalAllocations;
        }

        /**
         * Increases the number of total allocations recorded while the associated element was on
         * the shadow stack.
         *
         * @since 0.30
         */
        public void incrementTotalAllocations() {
            this.totalAllocations++;
        }

        /**
         * @return Information about all the {@link AllocationEventInfo allocation events} that
         *         happened while the associated element was at the top of the shadow stack.
         * @since 0.30
         */
        public List<AllocationEventInfo> getEvents() {
            return events;
        }
    }

    /**
     * Stores informatino about a single {@link AllocationEvent}.
     *
     * @since 0.30
     */
    public static final class AllocationEventInfo {
        private final LanguageInfo language;
        private final long allocated;
        private final boolean reallocation;
        private final String metaObjectString;

        AllocationEventInfo(LanguageInfo language, long allocated, boolean realocation, String metaObjectString) {
            this.language = language;
            this.allocated = allocated;
            this.reallocation = realocation;
            this.metaObjectString = metaObjectString;
        }

        /**
         * @return The {@link LanguageInfo language} from which the allocation originated
         * @since 0.30
         */
        public LanguageInfo getLanguage() {
            return language;
        }

        /**
         * @return the amount of memory that was allocated
         * @since 0.30
         */
        public long getAllocated() {
            return allocated;
        }

        /**
         * @return Whether the allocation was a re-allocation
         * @since 0.30
         */
        public boolean isReallocation() {
            return reallocation;
        }

        /**
         * @since 0.30
         */
        public String getMetaObjectString() {
            return metaObjectString;
        }
    }

    static {
        MemoryTracerInstrument.setFactory(new ProfilerToolFactory<MemoryTracer>() {
            @Override
            public MemoryTracer create(TruffleInstrument.Env env) {
                return new MemoryTracer(env);
            }
        });
    }
}

class MemoryTracerSnippets {

    @SuppressWarnings("unused")
    public void example() {
        // @formatter:off
        // BEGIN: MemoryTracerSnippets#example
        Context context = Context.create();

        MemoryTracer tracer = MemoryTracer.find(context.getEngine());
        tracer.setCollecting(true);
        context.eval("...", "...");
        tracer.setCollecting(false);
        // rootNodes is the recorded profile of the execution in tree form.

        tracer.close();
        // Prints information about the roots of the tree.
        for (ProfilerNode<MemoryTracer.Payload> node : tracer.getRootNodes()) {
            final String rootName = node.getRootName();
            final long allocCount = node.getPayload().getTotalAllocations();
        }
        // END: MemoryTracerSnippets#example
        // @formatter:on
    }
}
