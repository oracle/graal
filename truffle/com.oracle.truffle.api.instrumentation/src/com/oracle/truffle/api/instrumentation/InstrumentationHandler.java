/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

import com.oracle.truffle.api.LanguageInfo;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.Accessor.LanguageSupport;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.instrumentation.ProbeNode.EventChainNode;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Central coordinator class for the Truffle instrumentation framework. Allocated once per
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine engine}.
 */
final class InstrumentationHandler {

    /* Enable trace output to stdout. */
    private static final boolean TRACE = Boolean.getBoolean("truffle.instrumentation.trace");

    private final Object sourceVM;

    /*
     * The contract is the following: "sources" and "sourcesList" can only be accessed while
     * synchronized on "sources". both will only be lazily initialized from "loadedRoots" when the
     * first sourceBindings is added, by calling lazyInitializeSourcesList(). "sourcesList" will be
     * null as long as the sources haven't been initialized.
     */
    private final Map<Source, Void> sources = Collections.synchronizedMap(new WeakHashMap<Source, Void>());
    /* Load order needs to be preserved for sources, thats why we store sources again in a list. */
    private volatile Collection<Source> sourcesList;

    private final Collection<RootNode> loadedRoots = new WeakAsyncList<>(256);
    private final Collection<RootNode> executedRoots = new WeakAsyncList<>(64);

    private final Collection<EventBinding<?>> executionBindings = new EventBindingList(8);
    private final Collection<EventBinding<?>> sourceSectionBindings = new EventBindingList(8);
    private final Collection<EventBinding<?>> sourceBindings = new EventBindingList(8);
    private final Collection<EventBinding<?>> outputStdBindings = new EventBindingList(1);
    private final Collection<EventBinding<?>> outputErrBindings = new EventBindingList(1);

    /*
     * Fast lookup of instrumenter instances based on a key provided by the accessor.
     */
    private final ConcurrentHashMap<Object, AbstractInstrumenter> instrumenterMap = new ConcurrentHashMap<>();

    private final DispatchOutputStream out;
    private final DispatchOutputStream err;
    private final InputStream in;
    private final Map<Class<?>, Set<Class<?>>> cachedProvidedTags = new ConcurrentHashMap<>();

    private InstrumentationHandler(Object sourceVM, DispatchOutputStream out, DispatchOutputStream err, InputStream in) {
        this.sourceVM = sourceVM;
        this.out = out;
        this.err = err;
        this.in = in;
    }

    void onLoad(RootNode root) {
        if (!AccessorInstrumentHandler.nodesAccess().isInstrumentable(root)) {
            return;
        }
        assert root.getLanguageInfo() != null;
        Source source = null;
        synchronized (sources) {
            if (!sourceBindings.isEmpty()) {
                // we'll add to the sourcesList, so it needs to be initialized
                lazyInitializeSourcesList();

                SourceSection sourceSection = root.getSourceSection();
                if (sourceSection != null) {
                    // notify sources
                    source = sourceSection.getSource();
                    if (!sources.containsKey(source)) {
                        sources.put(source, null);
                        sourcesList.add(source);
                    } else {
                        // The source is not new
                        source = null;
                    }
                }
            }
            loadedRoots.add(root);
        }
        // we don't want to invoke foreign code while we are holding a lock to avoid
        // deadlocks.
        if (source != null) {
            notifySourceBindingsLoaded(sourceBindings, source);
        }

        // fast path no bindings attached
        if (!sourceSectionBindings.isEmpty()) {
            visitRoot(root, new NotifyLoadedListenerVisitor(sourceSectionBindings));
        }

    }

    void onFirstExecution(RootNode root) {
        if (!AccessorInstrumentHandler.nodesAccess().isInstrumentable(root)) {
            return;
        }
        assert root.getLanguageInfo() != null;
        executedRoots.add(root);

        // fast path no bindings attached
        if (executionBindings.isEmpty()) {
            return;
        }

        visitRoot(root, new InsertWrappersVisitor(executionBindings));
    }

    void addInstrument(Object key, Class<?> clazz) {
        addInstrumenter(key, new InstrumentClientInstrumenter(sourceVM, clazz, out, err, in));
    }

    void disposeInstrumenter(Object key, boolean cleanupRequired) {
        AbstractInstrumenter disposedInstrumenter = instrumenterMap.remove(key);
        if (disposedInstrumenter == null) {
            throw new AssertionError("Instrumenter already disposed.");
        }
        if (TRACE) {
            trace("BEGIN: Dispose instrumenter %n", key);
        }
        disposedInstrumenter.dispose();

        if (cleanupRequired) {
            Collection<EventBinding<?>> disposedExecutionBindings = filterBindingsForInstrumenter(executionBindings, disposedInstrumenter);
            if (!disposedExecutionBindings.isEmpty()) {
                visitRoots(executedRoots, new DisposeWrappersWithBindingVisitor(disposedExecutionBindings));
            }
            disposeBindingsBulk(disposedExecutionBindings);
            disposeBindingsBulk(filterBindingsForInstrumenter(sourceSectionBindings, disposedInstrumenter));
            disposeBindingsBulk(filterBindingsForInstrumenter(sourceBindings, disposedInstrumenter));
            disposeOutputBindingsBulk(out, outputStdBindings);
            disposeOutputBindingsBulk(err, outputErrBindings);
        }
        if (TRACE) {
            trace("END: Disposed instrumenter %n", key);
        }
    }

    private static void disposeBindingsBulk(Collection<EventBinding<?>> list) {
        for (EventBinding<?> binding : list) {
            binding.disposeBulk();
        }
    }

    private static void disposeOutputBindingsBulk(DispatchOutputStream dos, Collection<EventBinding<?>> list) {
        for (EventBinding<?> binding : list) {
            AccessorInstrumentHandler.engineAccess().detachOutputConsumer(dos, (OutputStream) binding.getElement());
            binding.disposeBulk();
        }
    }

    Instrumenter forLanguage(LanguageInfo info) {
        return new LanguageClientInstrumenter<>(info);
    }

    <T> EventBinding<T> addExecutionBinding(EventBinding<T> binding) {
        if (TRACE) {
            trace("BEGIN: Adding execution binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        this.executionBindings.add(binding);

        if (!executedRoots.isEmpty()) {
            visitRoots(executedRoots, new InsertWrappersWithBindingVisitor(binding));
        }

        if (TRACE) {
            trace("END: Added execution binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        return binding;
    }

    <T> EventBinding<T> addSourceSectionBinding(EventBinding<T> binding, boolean notifyLoaded) {
        if (TRACE) {
            trace("BEGIN: Adding binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        this.sourceSectionBindings.add(binding);
        if (notifyLoaded) {
            if (!loadedRoots.isEmpty()) {
                visitRoots(loadedRoots, new NotifyLoadedWithBindingVisitor(binding));
            }
        }

        if (TRACE) {
            trace("END: Added binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        return binding;
    }

    <T> EventBinding<T> addSourceBinding(EventBinding<T> binding, boolean notifyLoaded) {
        if (TRACE) {
            trace("BEGIN: Adding source binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        this.sourceBindings.add(binding);
        if (notifyLoaded) {
            synchronized (sources) {
                lazyInitializeSourcesList();
            }
            for (Source source : sourcesList) {
                notifySourceBindingLoaded(binding, source);
            }
        }

        if (TRACE) {
            trace("END: Added source binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        return binding;
    }

    <T extends OutputStream> EventBinding<T> addOutputBinding(EventBinding<T> binding, boolean errorOutput) {
        if (TRACE) {
            String kind = (errorOutput) ? "error" : "standard";
            trace("BEGIN: Adding " + kind + " output binding %s%n", binding.getElement());
        }

        if (errorOutput) {
            this.outputErrBindings.add(binding);
            AccessorInstrumentHandler.engineAccess().attachOutputConsumer(this.err, binding.getElement());
        } else {
            this.outputStdBindings.add(binding);
            AccessorInstrumentHandler.engineAccess().attachOutputConsumer(this.out, binding.getElement());
        }

        if (TRACE) {
            String kind = (errorOutput) ? "error" : "standard";
            trace("END: Added " + kind + " output binding %s%n", binding.getElement());
        }

        return binding;
    }

    /**
     * Initializes sources and sourcesList by populating them from loadedRoots.
     */
    private void lazyInitializeSourcesList() {
        assert Thread.holdsLock(sources);
        if (sourcesList == null) {
            // build the sourcesList, we need it now
            sourcesList = new WeakAsyncList<>(16);
            for (RootNode root : loadedRoots) {
                SourceSection sourceSection = root.getSourceSection();
                if (sourceSection != null) {
                    // notify sources
                    Source source = sourceSection.getSource();
                    if (!sources.containsKey(source)) {
                        sources.put(source, null);
                        sourcesList.add(source);
                    }
                }
            }
        }
    }

    private void visitRoots(Collection<RootNode> roots, AbstractNodeVisitor addBindingsVisitor) {
        for (RootNode root : roots) {
            visitRoot(root, addBindingsVisitor);
        }
    }

    void disposeBinding(EventBinding<?> binding) {
        if (TRACE) {
            trace("BEGIN: Dispose binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        if (binding.isExecutionEvent()) {
            visitRoots(executedRoots, new DisposeWrappersVisitor(binding));
        } else {
            Object elm = binding.getElement();
            if (elm instanceof OutputStream) {
                if (outputErrBindings.contains(binding)) {
                    AccessorInstrumentHandler.engineAccess().detachOutputConsumer(err, (OutputStream) elm);
                } else if (outputStdBindings.contains(binding)) {
                    AccessorInstrumentHandler.engineAccess().detachOutputConsumer(out, (OutputStream) elm);
                }
            }
        }

        if (TRACE) {
            trace("END: Disposed binding %s, %s%n", binding.getFilter(), binding.getElement());
        }
    }

    EventChainNode createBindings(ProbeNode probeNodeImpl) {
        EventContext context = probeNodeImpl.getContext();
        SourceSection sourceSection = context.getInstrumentedSourceSection();
        if (TRACE) {
            trace("BEGIN: Lazy update for %s%n", sourceSection);
        }

        RootNode rootNode = probeNodeImpl.getRootNode();
        Node instrumentedNode = probeNodeImpl.findWrapper().getDelegateNode();
        Set<Class<?>> providedTags = getProvidedTags(rootNode);
        EventChainNode root = null;
        EventChainNode parent = null;

        for (EventBinding<?> binding : executionBindings) {
            if (binding.isInstrumentedFull(providedTags, rootNode, instrumentedNode, sourceSection)) {
                if (TRACE) {
                    trace("  Found binding %s, %s%n", binding.getFilter(), binding.getElement());
                }
                EventChainNode next = probeNodeImpl.createEventChainCallback(binding);
                if (next == null) {
                    continue;
                }

                if (root == null) {
                    root = next;
                } else {
                    assert parent != null;
                    parent.setNext(next);
                }
                parent = next;
            }
        }

        if (TRACE) {
            trace("END: Lazy updated for %s%n", sourceSection);
        }
        return root;
    }

    private static void notifySourceBindingsLoaded(Collection<EventBinding<?>> bindings, Source source) {
        for (EventBinding<?> binding : bindings) {
            notifySourceBindingLoaded(binding, source);
        }
    }

    private static void notifySourceBindingLoaded(EventBinding<?> binding, Source source) {
        if (!binding.isDisposed() && binding.isInstrumentedSource(source)) {
            try {
                ((LoadSourceListener) binding.getElement()).onLoad(new LoadSourceEvent(source));
            } catch (Throwable t) {
                if (binding.isLanguageBinding()) {
                    throw t;
                } else {
                    ProbeNode.exceptionEventForClientInstrument(binding, "onLoad", t);
                }
            }
        }
    }

    static void notifySourceSectionLoaded(EventBinding<?> binding, Node node, SourceSection section) {
        LoadSourceSectionListener listener = (LoadSourceSectionListener) binding.getElement();
        try {
            listener.onLoad(new LoadSourceSectionEvent(section, node));
        } catch (Throwable t) {
            if (binding.isLanguageBinding()) {
                throw t;
            } else {
                ProbeNode.exceptionEventForClientInstrument(binding, "onLoad", t);
            }
        }
    }

    private void addInstrumenter(Object key, AbstractInstrumenter instrumenter) throws AssertionError {
        Object previousKey = instrumenterMap.putIfAbsent(key, instrumenter);
        if (previousKey != null) {
            throw new AssertionError("Instrumenter already present.");
        }
        instrumenter.initialize();
    }

    private static Collection<EventBinding<?>> filterBindingsForInstrumenter(Collection<EventBinding<?>> bindings, AbstractInstrumenter instrumenter) {
        if (bindings.isEmpty()) {
            return Collections.emptyList();
        }
        Collection<EventBinding<?>> newBindings = new ArrayList<>();
        for (EventBinding<?> binding : bindings) {
            if (binding.getInstrumenter() == instrumenter) {
                newBindings.add(binding);
            }
        }
        return newBindings;
    }

    @SuppressWarnings("unchecked")
    private void insertWrapper(Node instrumentableNode, SourceSection sourceSection) {
        final Node node = instrumentableNode;
        final Node parent = node.getParent();
        if (parent instanceof WrapperNode) {
            // already wrapped, need to invalidate the wrapper something changed
            invalidateWrapperImpl((WrapperNode) parent, node);
            return;
        }
        ProbeNode probe = new ProbeNode(InstrumentationHandler.this, sourceSection);
        WrapperNode wrapper;
        try {
            Class<?> factory = null;
            Class<?> currentClass = instrumentableNode.getClass();
            while (currentClass != null) {
                Instrumentable instrumentable = currentClass.getAnnotation(Instrumentable.class);
                if (instrumentable != null) {
                    factory = instrumentable.factory();
                    break;
                }
                currentClass = currentClass.getSuperclass();
            }

            if (factory == null) {
                if (TRACE) {
                    trace("No wrapper inserted for %s, section %s. Not annotated with @Instrumentable.%n", node, sourceSection);
                }
                // node or superclass is not annotated with @Instrumentable
                return;
            }

            if (TRACE) {
                trace("Insert wrapper for %s, section %s%n", node, sourceSection);
            }

            wrapper = ((InstrumentableFactory<Node>) factory.newInstance()).createWrapper(instrumentableNode, probe);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create wrapper node. ", e);
        }

        if (!(wrapper instanceof Node)) {
            throw new IllegalStateException(String.format("Implementation of %s must be a subclass of %s.", WrapperNode.class.getSimpleName(), Node.class.getSimpleName()));
        }

        final Node wrapperNode = (Node) wrapper;
        if (wrapperNode.getParent() != null) {
            throw new IllegalStateException(String.format("Instance of provided %s is already adopted by another parent.", WrapperNode.class.getSimpleName()));
        }
        if (parent == null) {
            throw new IllegalStateException(String.format("Instance of instrumentable %s is not adopted by a parent.", Node.class.getSimpleName()));
        }

        if (!NodeUtil.isReplacementSafe(parent, node, wrapperNode)) {
            throw new IllegalStateException(
                            String.format("WrapperNode implementation %s cannot be safely replaced in parent node class %s.", wrapperNode.getClass().getName(), parent.getClass().getName()));
        }

        node.replace(wrapperNode, "Insert instrumentation wrapper node.");
    }

    private <T extends ExecutionEventNodeFactory> EventBinding<T> attachFactory(AbstractInstrumenter instrumenter, SourceSectionFilter filter, T factory) {
        return addExecutionBinding(new EventBinding<>(instrumenter, filter, factory, true));
    }

    private <T extends ExecutionEventListener> EventBinding<T> attachListener(AbstractInstrumenter instrumenter, SourceSectionFilter filter, T listener) {
        return addExecutionBinding(new EventBinding<>(instrumenter, filter, listener, true));
    }

    private <T> EventBinding<T> attachSourceListener(AbstractInstrumenter abstractInstrumenter, SourceSectionFilter filter, T listener, boolean notifyLoaded) {
        return addSourceBinding(new EventBinding<>(abstractInstrumenter, filter, listener, false), notifyLoaded);
    }

    private <T> EventBinding<T> attachSourceSectionListener(AbstractInstrumenter abstractInstrumenter, SourceSectionFilter filter, T listener, boolean notifyLoaded) {
        return addSourceSectionBinding(new EventBinding<>(abstractInstrumenter, filter, listener, false), notifyLoaded);
    }

    private <T extends OutputStream> EventBinding<T> attachOutputConsumer(AbstractInstrumenter instrumenter, T stream, boolean errorOutput) {
        return addOutputBinding(new EventBinding<>(instrumenter, null, stream, false), errorOutput);
    }

    Set<Class<?>> getProvidedTags(LanguageInfo language) {
        LanguageSupport langAccess = AccessorInstrumentHandler.langAccess();
        TruffleLanguage<?> lang = langAccess.getLanguage(langAccess.getEnv(language));
        if (lang == null) {
            return Collections.emptySet();
        }
        Class<?> languageClass = lang.getClass();
        Set<Class<?>> tags = cachedProvidedTags.get(languageClass);
        if (tags == null) {
            ProvidedTags languageTags = languageClass.getAnnotation(ProvidedTags.class);
            List<Class<?>> languageTagsList = languageTags != null ? Arrays.asList(languageTags.value()) : Collections.<Class<?>> emptyList();
            tags = Collections.unmodifiableSet(new HashSet<>(languageTagsList));
            cachedProvidedTags.put(languageClass, tags);
        }
        return tags;
    }

    Set<Class<?>> getProvidedTags(RootNode root) {
        return getProvidedTags(root.getLanguageInfo());
    }

    private static boolean isInstrumentableNode(Node node, SourceSection sourceSection) {
        return !(node instanceof WrapperNode) && !(node instanceof RootNode) && sourceSection != null;
    }

    private static void trace(String message, Object... args) {
        PrintStream out = System.out;
        out.printf(message, args);
    }

    private void visitRoot(final RootNode root, final AbstractNodeVisitor visitor) {
        if (TRACE) {
            trace("BEGIN: Visit root %s for %s%n", root.toString(), visitor);
        }

        visitor.root = root;
        visitor.providedTags = getProvidedTags(root);

        if (visitor.shouldVisit()) {
            if (TRACE) {
                trace("BEGIN: Traverse root %s for %s%n", root.toString(), visitor);
            }
            root.accept(visitor);
            if (TRACE) {
                trace("END: Traverse root %s for %s%n", root.toString(), visitor);
            }
        }
        if (TRACE) {
            trace("END: Visited root %s for %s%n", root.toString(), visitor);
        }
    }

    static void removeWrapper(ProbeNode node) {
        if (TRACE) {
            trace("Remove wrapper for %s%n", node.getContext().getInstrumentedSourceSection());
        }
        WrapperNode wrapperNode = node.findWrapper();
        ((Node) wrapperNode).replace(wrapperNode.getDelegateNode());
    }

    private static void invalidateWrapper(Node node) {
        Node parent = node.getParent();
        if (!(parent instanceof WrapperNode)) {
            // not yet wrapped
            return;
        }
        invalidateWrapperImpl((WrapperNode) parent, node);
    }

    private static void invalidateWrapperImpl(WrapperNode parent, Node node) {
        ProbeNode probeNode = parent.getProbeNode();
        if (TRACE) {
            SourceSection section = probeNode.getContext().getInstrumentedSourceSection();
            trace("Invalidate wrapper for %s, section %s %n", node, section);
        }
        if (probeNode != null) {
            probeNode.invalidate();
        }
    }

    static boolean hasTagImpl(Set<Class<?>> providedTags, Node node, Class<?> tag) {
        if (providedTags.contains(tag)) {
            return AccessorInstrumentHandler.nodesAccess().isTaggedWith(node, tag);
        }
        return false;
    }

    static Instrumentable getInstrumentable(Node node) {
        Instrumentable instrumentable = node.getClass().getAnnotation(Instrumentable.class);
        if (instrumentable != null && !(node instanceof WrapperNode)) {
            return instrumentable;
        }
        return null;
    }

    private <T> T lookup(Object key, Class<T> type) {
        AbstractInstrumenter value = instrumenterMap.get(key);
        return value == null ? null : value.lookup(this, type);
    }

    private abstract static class AbstractNodeVisitor implements NodeVisitor {

        RootNode root;
        Set<Class<?>> providedTags;

        abstract boolean shouldVisit();

    }

    private abstract class AbstractBindingVisitor extends AbstractNodeVisitor {

        protected final EventBinding<?> binding;

        AbstractBindingVisitor(EventBinding<?> binding) {
            this.binding = binding;
        }

        @Override
        boolean shouldVisit() {
            return binding.isInstrumentedRoot(providedTags, root, root.getSourceSection());
        }

        public final boolean visit(Node node) {
            SourceSection sourceSection = node.getSourceSection();
            if (isInstrumentableNode(node, sourceSection)) {
                if (binding.isInstrumentedLeaf(providedTags, node, sourceSection)) {
                    if (TRACE) {
                        traceFilterCheck("hit", providedTags, binding, node, sourceSection);
                    }
                    visitInstrumented(node, sourceSection);
                } else {
                    if (TRACE) {
                        traceFilterCheck("miss", providedTags, binding, node, sourceSection);
                    }
                }
            }
            return true;
        }

        protected abstract void visitInstrumented(Node node, SourceSection section);
    }

    private static void traceFilterCheck(String result, Set<Class<?>> providedTags, EventBinding<?> binding, Node node, SourceSection sourceSection) {
        Set<Class<?>> tags = binding.getFilter().getReferencedTags();
        Set<Class<?>> containedTags = new HashSet<>();
        for (Class<?> tag : tags) {
            if (hasTagImpl(providedTags, node, tag)) {
                containedTags.add(tag);
            }
        }
        trace("  Filter %4s %s section:%s tags:%s%n", result, binding.getFilter(), sourceSection, containedTags);
    }

    private abstract class AbstractBindingsVisitor extends AbstractNodeVisitor {

        private final Collection<EventBinding<?>> bindings;
        private final boolean visitForEachBinding;

        AbstractBindingsVisitor(Collection<EventBinding<?>> bindings, boolean visitForEachBinding) {
            this.bindings = bindings;
            this.visitForEachBinding = visitForEachBinding;
        }

        @Override
        boolean shouldVisit() {
            if (bindings.isEmpty()) {
                return false;
            }
            final RootNode localRoot = root;
            if (localRoot == null) {
                return false;
            }
            SourceSection sourceSection = localRoot.getSourceSection();

            for (EventBinding<?> binding : bindings) {
                if (binding.isInstrumentedRoot(providedTags, localRoot, sourceSection)) {
                    return true;
                }
            }
            return false;
        }

        public final boolean visit(Node node) {
            SourceSection sourceSection = node.getSourceSection();
            if (isInstrumentableNode(node, sourceSection)) {
                // no locking required for these atomic reference arrays
                for (EventBinding<?> binding : bindings) {
                    if (binding.isInstrumentedFull(providedTags, root, node, sourceSection)) {
                        if (TRACE) {
                            traceFilterCheck("hit", providedTags, binding, node, sourceSection);
                        }
                        visitInstrumented(binding, node, sourceSection);
                        if (!visitForEachBinding) {
                            break;
                        }
                    } else {
                        if (TRACE) {
                            traceFilterCheck("miss", providedTags, binding, node, sourceSection);
                        }
                    }
                }
            }
            return true;
        }

        protected abstract void visitInstrumented(EventBinding<?> binding, Node node, SourceSection section);

    }

    /* Insert wrappers for a single bindings. */
    private final class InsertWrappersWithBindingVisitor extends AbstractBindingVisitor {

        InsertWrappersWithBindingVisitor(EventBinding<?> filter) {
            super(filter);
        }

        @Override
        protected void visitInstrumented(Node node, SourceSection section) {
            insertWrapper(node, section);
        }

    }

    private final class DisposeWrappersVisitor extends AbstractBindingVisitor {

        DisposeWrappersVisitor(EventBinding<?> binding) {
            super(binding);
        }

        @Override
        protected void visitInstrumented(Node node, SourceSection section) {
            invalidateWrapper(node);
        }
    }

    private final class InsertWrappersVisitor extends AbstractBindingsVisitor {

        InsertWrappersVisitor(Collection<EventBinding<?>> bindings) {
            super(bindings, false);
        }

        @Override
        protected void visitInstrumented(EventBinding<?> binding, Node node, SourceSection section) {
            insertWrapper(node, section);
        }
    }

    private final class DisposeWrappersWithBindingVisitor extends AbstractBindingsVisitor {

        DisposeWrappersWithBindingVisitor(Collection<EventBinding<?>> bindings) {
            super(bindings, false);
        }

        @Override
        protected void visitInstrumented(EventBinding<?> binding, Node node, SourceSection section) {
            invalidateWrapper(node);
        }

    }

    private final class NotifyLoadedWithBindingVisitor extends AbstractBindingVisitor {

        NotifyLoadedWithBindingVisitor(EventBinding<?> binding) {
            super(binding);
        }

        @Override
        protected void visitInstrumented(Node node, SourceSection section) {
            notifySourceSectionLoaded(binding, node, section);
        }

    }

    private final class NotifyLoadedListenerVisitor extends AbstractBindingsVisitor {

        NotifyLoadedListenerVisitor(Collection<EventBinding<?>> bindings) {
            super(bindings, true);
        }

        @Override
        protected void visitInstrumented(EventBinding<?> binding, Node node, SourceSection section) {
            notifySourceSectionLoaded(binding, node, section);
        }
    }

    /**
     * Provider of instrumentation services for {@linkplain TruffleInstrument external clients} of
     * instrumentation.
     */
    final class InstrumentClientInstrumenter extends AbstractInstrumenter {

        private final Class<?> instrumentClass;
        private Object[] services;
        private TruffleInstrument instrument;
        private final Env env;

        InstrumentClientInstrumenter(Object vm, Class<?> instrumentClass, OutputStream out, OutputStream err, InputStream in) {
            this.instrumentClass = instrumentClass;
            this.env = new Env(vm, this, out, err, in);
        }

        @Override
        boolean isInstrumentableSource(Source source) {
            return true;
        }

        @Override
        boolean isInstrumentableRoot(RootNode rootNode) {
            return true;
        }

        @Override
        public Set<Class<?>> queryTags(Node node) {
            return queryTagsImpl(node, null);
        }

        @Override
        void verifyFilter(SourceSectionFilter filter) {
        }

        Class<?> getInstrumentClass() {
            return instrumentClass;
        }

        Env getEnv() {
            return env;
        }

        @Override
        void initialize() {
            if (TRACE) {
                trace("Initialize instrument %s class %s %n", instrument, instrumentClass);
            }
            assert instrument == null;
            try {
                this.instrument = (TruffleInstrument) instrumentClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                failInstrumentInitialization(String.format("Failed to create new instrumenter class %s", instrumentClass.getName()), e);
                return;
            }
            try {
                services = env.onCreate(instrument);
            } catch (Throwable e) {
                failInstrumentInitialization(String.format("Failed calling onCreate of instrument class %s", instrumentClass.getName()), e);
                return;
            }
            if (TRACE) {
                trace("Initialized instrument %s class %s %n", instrument, instrumentClass);
            }
        }

        private void failInstrumentInitialization(String message, Throwable t) {
            Exception exception = new Exception(message, t);
            PrintStream stream = new PrintStream(env.err());
            exception.printStackTrace(stream);
        }

        boolean isInitialized() {
            return instrument != null;
        }

        TruffleInstrument getInstrument() {
            return instrument;
        }

        @Override
        void dispose() {
            instrument.onDispose(env);
        }

        @Override
        <T> T lookup(InstrumentationHandler handler, Class<T> type) {
            if (services != null) {
                for (Object service : services) {
                    if (type.isInstance(service)) {
                        return type.cast(service);
                    }
                }
            }
            return null;
        }

    }

    /**
     * Provider of instrumentation services for {@linkplain TruffleLanguage language
     * implementations}.
     */
    final class LanguageClientInstrumenter<T> extends AbstractInstrumenter {

        private final LanguageInfo languageInfo;

        LanguageClientInstrumenter(LanguageInfo info) {
            this.languageInfo = info;
        }

        @Override
        boolean isInstrumentableSource(Source source) {
            String mimeType = source.getMimeType();
            if (mimeType == null) {
                return false;
            }
            return languageInfo.getMimeTypes().contains(mimeType);
        }

        @Override
        boolean isInstrumentableRoot(RootNode node) {
            LanguageInfo langInfo = node.getLanguageInfo();
            if (langInfo == null) {
                return false;
            }
            if (langInfo != languageInfo) {
                return false;
            }
            return true;
        }

        @Override
        public Set<Class<?>> queryTags(Node node) {
            return queryTagsImpl(node, languageInfo);
        }

        @Override
        void verifyFilter(SourceSectionFilter filter) {
            Set<Class<?>> providedTags = getProvidedTags(languageInfo);
            // filters must not reference tags not declared in @RequiredTags
            Set<Class<?>> referencedTags = filter.getReferencedTags();
            if (!providedTags.containsAll(referencedTags)) {
                Set<Class<?>> missingTags = new HashSet<>(referencedTags);
                missingTags.removeAll(providedTags);
                Set<Class<?>> allTags = new LinkedHashSet<>(providedTags);
                allTags.addAll(missingTags);
                StringBuilder builder = new StringBuilder("{");
                String sep = "";
                for (Class<?> tag : allTags) {
                    builder.append(sep);
                    builder.append(tag.getSimpleName());
                    sep = ", ";
                }
                builder.append("}");
                LanguageSupport langAccess = AccessorInstrumentHandler.langAccess();
                TruffleLanguage<?> lang = langAccess.getLanguage(langAccess.getEnv(languageInfo));
                throw new IllegalArgumentException(String.format("The attached filter %s references the following tags %s which are not declared as provided by the language. " +
                                "To fix this annotate the language class %s with @%s(%s).",
                                filter, missingTags, lang.getClass().getName(), ProvidedTags.class.getSimpleName(), builder));
            }
        }

        @Override
        void initialize() {
            // nothing to do
        }

        @Override
        void dispose() {
            // nothing to do
        }

        @Override
        <S> S lookup(InstrumentationHandler handler, Class<S> type) {
            return null;
        }

    }

    /**
     * Shared implementation of instrumentation services for clients whose requirements and
     * privileges may vary.
     */
    abstract class AbstractInstrumenter extends Instrumenter {

        abstract void initialize();

        abstract void dispose();

        abstract <T> T lookup(InstrumentationHandler handler, Class<T> type);

        void disposeBinding(EventBinding<?> binding) {
            InstrumentationHandler.this.disposeBinding(binding);
        }

        abstract boolean isInstrumentableRoot(RootNode rootNode);

        abstract boolean isInstrumentableSource(Source source);

        final Set<Class<?>> queryTagsImpl(Node node, LanguageInfo onlyLanguage) {
            SourceSection sourceSection = node.getSourceSection();
            if (!InstrumentationHandler.isInstrumentableNode(node, sourceSection)) {
                return Collections.emptySet();
            }

            RootNode root = node.getRootNode();
            if (root == null) {
                return Collections.emptySet();
            }

            if (onlyLanguage != null && root.getLanguageInfo() != onlyLanguage) {
                throw new IllegalArgumentException("The language instrumenter cannot query tags of nodes of other languages.");
            }
            Set<Class<?>> providedTags = getProvidedTags(root);
            if (providedTags.isEmpty()) {
                return Collections.emptySet();
            }

            Set<Class<?>> tags = new HashSet<>();
            for (Class<?> providedTag : providedTags) {
                if (hasTagImpl(providedTags, node, providedTag)) {
                    tags.add(providedTag);
                }
            }
            return Collections.unmodifiableSet(tags);
        }

        @Override
        public final <T extends ExecutionEventNodeFactory> EventBinding<T> attachFactory(SourceSectionFilter filter, T factory) {
            verifyFilter(filter);
            return InstrumentationHandler.this.attachFactory(this, filter, factory);
        }

        @Override
        public final <T extends ExecutionEventListener> EventBinding<T> attachListener(SourceSectionFilter filter, T listener) {
            verifyFilter(filter);
            return InstrumentationHandler.this.attachListener(this, filter, listener);
        }

        @Override
        public <T extends LoadSourceListener> EventBinding<T> attachLoadSourceListener(SourceSectionFilter filter, T listener, boolean notifyLoaded) {
            verifySourceOnly(filter);
            verifyFilter(filter);
            return InstrumentationHandler.this.attachSourceListener(this, filter, listener, notifyLoaded);
        }

        @Override
        public <T extends LoadSourceSectionListener> EventBinding<T> attachLoadSourceSectionListener(SourceSectionFilter filter, T listener, boolean notifyLoaded) {
            verifyFilter(filter);
            return InstrumentationHandler.this.attachSourceSectionListener(this, filter, listener, notifyLoaded);
        }

        @Override
        public <T extends OutputStream> EventBinding<T> attachOutConsumer(T stream) {
            return InstrumentationHandler.this.attachOutputConsumer(this, stream, false);
        }

        @Override
        public <T extends OutputStream> EventBinding<T> attachErrConsumer(T stream) {
            return InstrumentationHandler.this.attachOutputConsumer(this, stream, true);
        }

        private void verifySourceOnly(SourceSectionFilter filter) {
            if (!filter.isSourceOnly()) {
                throw new IllegalArgumentException(String.format("The attached filter %s uses filters that require source sections to verifiy. " +
                                "Source listeners can only use filter critera based on Source objects like mimeTypeIs or sourceIs.", filter));
            }
        }

        abstract void verifyFilter(SourceSectionFilter filter);

    }

    /**
     * A list collection data structure that is optimized for fast non-blocking traversals. There is
     * adds and no explicit removal. Removals are based on a side effect of the element, by
     * returning <code>null</code> in {@link AbstractAsyncCollection#unwrap(Object)}. It is not
     * possible to reliably query the {@link AbstractAsyncCollection#size()} of the collection,
     * therefore it throws an {@link UnsupportedOperationException}.
     */
    private abstract static class AbstractAsyncCollection<T, R> extends AbstractCollection<R> {
        /*
         * We use an atomic reference list as we don't want to see holes in the array when appending
         * to it. This allows us to use null as a safe terminator for the array.
         */
        private volatile AtomicReferenceArray<T> values;

        /*
         * Size can be non volatile as it is not exposed or used for traversal.
         */
        private int nextInsertionIndex;

        AbstractAsyncCollection(int initialCapacity) {
            if (initialCapacity <= 0) {
                throw new IllegalArgumentException("Invalid initial capacity " + initialCapacity);
            }
            this.values = new AtomicReferenceArray<>(initialCapacity);
        }

        @Override
        public final synchronized boolean add(R reference) {
            T wrappedElement = wrap(reference);
            if (wrappedElement == null) {
                // fail early
                throw new NullPointerException();
            }
            if (nextInsertionIndex >= values.length()) {
                compact();
            }
            values.set(nextInsertionIndex++, wrappedElement);
            return true;
        }

        @Override
        public int size() {
            // size cannot be supported reliably
            throw new UnsupportedOperationException();
        }

        @Override
        public final boolean isEmpty() {
            return values.get(0) == null;
        }

        protected abstract T wrap(R element);

        protected abstract R unwrap(T element);

        private void compact() {
            AtomicReferenceArray<T> localValues = values;
            int liveElements = 0;
            /*
             * We count the still alive elements.
             */
            for (int i = 0; i < localValues.length(); i++) {
                T ref = localValues.get(i);
                if (ref == null) {
                    break;
                }
                if (unwrap(ref) != null) {
                    liveElements++;
                }
            }

            /*
             * We ensure that the capacity after compaction is always twice as big as the number of
             * live elements. This can make the array grow or shrink as needed.
             */
            AtomicReferenceArray<T> newValues = new AtomicReferenceArray<>(Math.max(liveElements * 2, 8));
            int index = 0;
            for (int i = 0; i < localValues.length(); i++) {
                T ref = localValues.get(i);
                if (ref == null) {
                    break;
                }
                if (unwrap(ref) != null) {
                    newValues.set(index++, ref);
                }
            }

            this.nextInsertionIndex = index;
            this.values = newValues;
        }

        /**
         * Returns an iterator which can be traversed without a lock. The iterator that is
         * constructed is not sequentially consistent. In other words, the user of the iterator may
         * observe values that were added after the iterator was created.
         */
        @Override
        public Iterator<R> iterator() {
            return new Iterator<R>() {

                /*
                 * We need to capture the values field in the iterator to have a consistent view on
                 * the data while iterating.
                 */
                private final AtomicReferenceArray<T> values = AbstractAsyncCollection.this.values;
                private int index;
                private R queuedNext;

                public boolean hasNext() {
                    R next = queuedNext;
                    if (next == null) {
                        next = queueNext();
                        queuedNext = next;
                    }
                    return next != null;
                }

                private R queueNext() {
                    int localIndex = index;
                    AtomicReferenceArray<T> array = values;
                    while (true) {
                        if (localIndex >= array.length()) {
                            return null;
                        }
                        T localValue = array.get(localIndex);
                        if (localValue == null) {
                            return null;
                        }
                        localIndex++;
                        R alive = unwrap(localValue);
                        if (alive == null) {
                            continue;
                        }
                        index = localIndex;
                        return alive;
                    }
                }

                public R next() {
                    R next = queuedNext;
                    if (next == null) {
                        next = queueNext();
                        if (next == null) {
                            throw new NoSuchElementException();
                        }
                    }
                    queuedNext = null;
                    return next;
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

    }

    /**
     * An async list implementation that removes elements whenever a binding was disposed.
     */
    private static final class EventBindingList extends AbstractAsyncCollection<EventBinding<?>, EventBinding<?>> {

        EventBindingList(int initialCapacity) {
            super(initialCapacity);
        }

        @Override
        protected EventBinding<?> wrap(EventBinding<?> element) {
            return element;
        }

        @Override
        protected EventBinding<?> unwrap(EventBinding<?> element) {
            if (element.isDisposed()) {
                return null;
            }
            return element;
        }

    }

    /**
     * An async list using weak references.
     */
    private static final class WeakAsyncList<T> extends AbstractAsyncCollection<WeakReference<T>, T> {

        WeakAsyncList(int initialCapacity) {
            super(initialCapacity);
        }

        @Override
        protected WeakReference<T> wrap(T element) {
            return new WeakReference<>(element);
        }

        @Override
        protected T unwrap(WeakReference<T> element) {
            return element.get();
        }

    }

    static final AccessorInstrumentHandler ACCESSOR = new AccessorInstrumentHandler();

    static final class AccessorInstrumentHandler extends Accessor {

        static Accessor.Nodes nodesAccess() {
            return ACCESSOR.nodes();
        }

        static Accessor.LanguageSupport langAccess() {
            return ACCESSOR.languageSupport();
        }

        static Accessor.EngineSupport engineAccess() {
            return ACCESSOR.engineSupport();
        }

        @Override
        protected InstrumentSupport instrumentSupport() {
            return new InstrumentImpl();
        }

        static final class InstrumentImpl extends InstrumentSupport {

            @Override
            public Object createInstrumentationHandler(Object vm, DispatchOutputStream out, DispatchOutputStream err, InputStream in) {
                return new InstrumentationHandler(vm, out, err, in);
            }

            @Override
            public void addInstrument(Object instrumentationHandler, Object key, Class<?> instrumentClass) {
                ((InstrumentationHandler) instrumentationHandler).addInstrument(key, instrumentClass);
            }

            @Override
            public void disposeInstrument(Object instrumentationHandler, Object key, boolean cleanupRequired) {
                ((InstrumentationHandler) instrumentationHandler).disposeInstrumenter(key, cleanupRequired);
            }

            @Override
            public void collectEnvServices(Set<Object> collectTo, Object languageShared, LanguageInfo info) {
                InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(languageShared);
                Instrumenter instrumenter = instrumentationHandler.forLanguage(info);
                collectTo.add(instrumenter);
            }

            @Override
            public <T> T getInstrumentationHandlerService(Object vm, Object key, Class<T> type) {
                InstrumentationHandler instrumentationHandler = (InstrumentationHandler) vm;
                return instrumentationHandler.lookup(key, type);
            }

            @Override
            public void onFirstExecution(RootNode rootNode) {
                InstrumentationHandler handler = getHandler(rootNode);
                if (handler != null) {
                    handler.onFirstExecution(rootNode);
                }
            }

            @Override
            public void onLoad(RootNode rootNode) {
                InstrumentationHandler handler = getHandler(rootNode);
                if (handler != null) {
                    handler.onLoad(rootNode);
                }
            }

            private static InstrumentationHandler getHandler(RootNode rootNode) {
                LanguageInfo info = rootNode.getLanguageInfo();
                if (info == null) {
                    return null;
                }
                Object languageShared = langAccess().getLanguageShared(info);
                if (languageShared == null) {
                    return null;
                }
                return (InstrumentationHandler) engineAccess().getInstrumentationHandler(languageShared);
            }
        }
    }

}
