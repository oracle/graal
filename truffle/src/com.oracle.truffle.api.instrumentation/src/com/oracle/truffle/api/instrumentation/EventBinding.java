/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.instrumentation;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.AbstractInstrumenter;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.LanguageClientInstrumenter;
import com.oracle.truffle.api.instrumentation.NearestNodesCollector.NodeListSection;
import com.oracle.truffle.api.instrumentation.NearestNodesCollector.NodeSection;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

/**
 * An {@linkplain Instrumenter instrumentation} handle for a subscription to a
 * {@linkplain SourceSectionFilter filtered} stream of execution event notifications.
 * <p>
 * The subscription remains active until:
 * <ul>
 * <li>explicit {@linkplain #dispose() disposal} of the subscription; or</li>
 *
 * <li>the instrumented engine is {@linkplain org.graalvm.polyglot.Engine#close() closed}.</li>
 * </ul>
 * </p>
 *
 * @param <T> subscriber type: {@link ExecutionEventListener} or {@link ExecutionEventNodeFactory}.
 * @see Instrumenter#attachExecutionEventListener(SourceSectionFilter, ExecutionEventListener)
 * @see Instrumenter#attachExecutionEventFactory(SourceSectionFilter, ExecutionEventNodeFactory)
 *
 * @since 0.12
 */
public class EventBinding<T> {

    private final AbstractInstrumenter instrumenter;
    private final T element;

    private final AtomicReference<Boolean> attached;
    final Semaphore attachedSemaphore = new Semaphore(0);
    volatile boolean disposing;
    /* language bindings needs special treatment. */
    private volatile boolean disposed;

    EventBinding(AbstractInstrumenter instrumenter, T element) {
        this(instrumenter, element, true);
    }

    EventBinding(AbstractInstrumenter instrumenter, T element, boolean attached) {
        if (element == null) {
            throw new NullPointerException();
        }
        this.instrumenter = instrumenter;
        this.element = element;
        this.attached = new AtomicReference<>(attached);
        if (attached) {
            attachedSemaphore.release();
        }
    }

    final AbstractInstrumenter getInstrumenter() {
        return instrumenter;
    }

    /**
     * @return the subscriber: an {@link ExecutionEventNodeFactory} or
     *         {@link ExecutionEventListener}.
     *
     * @since 0.12
     */
    public T getElement() {
        return element;
    }

    /**
     * Test if this binding is attached.
     *
     * @since 21.1
     */
    public final boolean isAttached() {
        return Boolean.TRUE == attached.get();
    }

    /**
     * Attach this binding to receive the associated notifications by the {@link #getElement()
     * subscriber}. When notification about existing sources were requested in binding creation,
     * notifications will be performed in this call.
     * <p>
     * The binding is attached automatically, when one of the {@link Instrumenter} attach methods
     * were used. Use this for bindings created by {@link Instrumenter} create methods only.
     *
     * @throws IllegalStateException when the binding is {@link #isAttached() attached} already, or
     *             when it was {@link #dispose() disposed}.
     * @since 21.1
     */
    public final void attach() {
        Boolean wasAttached = attached.getAndSet(true);
        if (null == wasAttached) {
            throw new IllegalStateException("The binding is disposed. Create a new binding to attach.");
        }
        if (Boolean.TRUE == wasAttached) {
            throw new IllegalStateException("The binding is attached already.");
        }
        doAttach();
        attachedSemaphore.release();
    }

    /**
     * Try to attach this binding, if not disposed or attached already. Works the same as
     * {@link #attach()}, but returns <code>false</code> instead of throwing an exception when not
     * successful.
     * <p>
     * The binding is attached automatically, when one of the {@link Instrumenter} attach methods
     * were used. Use this for bindings created by {@link Instrumenter} create methods only.
     *
     * @return <code>true</code> when the binding was attached successfully, <code>false</code> when
     *         disposed or attached already.
     * @since 23.1
     */
    public final boolean tryAttach() {
        Boolean wasAttached = attached.getAndSet(true);
        if (Boolean.FALSE != wasAttached) {
            // Attached already, or disposed
            return false;
        }
        doAttach();
        attachedSemaphore.release();
        return true;
    }

    void doAttach() {
        throw CompilerDirectives.shouldNotReachHere(this.toString() + ".doAttach()");
    }

    /**
     * @return whether the subscription has been permanently canceled.
     *
     * @since 0.12
     */
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Cancels the subscription permanently.
     *
     * @since 0.12
     */
    public synchronized void dispose() {
        CompilerAsserts.neverPartOfCompilation();
        if (!disposed) {
            disposing = true;
            Boolean wasSet = attached.getAndSet(null);
            if (Boolean.TRUE == wasSet) {
                // We must wait for attach to finish before we dispose the binding:
                try {
                    attachedSemaphore.acquire();
                } catch (InterruptedException ex) {
                }
            }
            instrumenter.disposeBinding(this);
            disposed = true;
        }
    }

    synchronized void setDisposingBulk() {
        this.disposing = true;
    }

    synchronized void disposeBulk() {
        disposed = true;
    }

    static final class SourceLoaded<T> extends LoadSource<T> {

        SourceLoaded(AbstractInstrumenter instrumenter, SourceSectionFilter filterSourceSection, SourceSectionFilter inputFilter, T element, boolean attached, boolean notifyLoaded) {
            super(instrumenter, filterSourceSection, inputFilter, element, attached, notifyLoaded);
        }

        @Override
        void doAttach() {
            getInstrumenter().attachSourceLoadedBinding(this);
        }
    }

    static final class SourceExecuted<T> extends LoadSource<T> {

        SourceExecuted(AbstractInstrumenter instrumenter, SourceSectionFilter filterSourceSection, SourceSectionFilter inputFilter, T element, boolean attached, boolean notifyLoaded) {
            super(instrumenter, filterSourceSection, inputFilter, element, attached, notifyLoaded);
        }

        @Override
        void doAttach() {
            getInstrumenter().attachSourceExecutedBinding(this);
        }
    }

    static final class SourceSectionLoaded<T> extends LoadSource<T> {

        SourceSectionLoaded(AbstractInstrumenter instrumenter, SourceSectionFilter filterSourceSection, SourceSectionFilter inputFilter, T element, boolean attached, boolean notifyLoaded) {
            super(instrumenter, filterSourceSection, inputFilter, element, attached, notifyLoaded);
        }

        @Override
        void doAttach() {
            getInstrumenter().attachSourceSectionBinding(this);
        }
    }

    interface LoadedNotifier {

        boolean isNotifyLoaded();
    }

    static final class LoadNearestSection<T> extends NearestSourceSection<T> implements LoadedNotifier {

        private final boolean notifyLoaded;

        LoadNearestSection(AbstractInstrumenter instrumenter, NearestSectionFilter nearestFilter, SourceSectionFilter filterSourceSection, T element, boolean attached, boolean notifyLoaded) {
            super(instrumenter, nearestFilter, filterSourceSection, element, attached);
            this.notifyLoaded = notifyLoaded;
        }

        @Override
        void doAttach() {
            getInstrumenter().attachSourceSectionBinding(this);
        }

        @Override
        public boolean isNotifyLoaded() {
            return notifyLoaded;
        }

        @Override
        boolean isExecutionEvent() {
            return false;
        }
    }

    abstract static class LoadSource<T> extends Source<T> implements LoadedNotifier {

        private final boolean notifyLoaded;

        LoadSource(AbstractInstrumenter instrumenter, SourceSectionFilter filterSourceSection, SourceSectionFilter inputFilter, T element, boolean attached, boolean notifyLoaded) {
            super(instrumenter, filterSourceSection, inputFilter, element, attached);
            this.notifyLoaded = notifyLoaded;
        }

        @Override
        public final boolean isNotifyLoaded() {
            return notifyLoaded;
        }

        @Override
        final boolean isExecutionEvent() {
            return false;
        }
    }

    static final class Execution<T> extends Source<T> {

        Execution(AbstractInstrumenter instrumenter, SourceSectionFilter filterSourceSection, SourceSectionFilter inputFilter, T element) {
            super(instrumenter, filterSourceSection, inputFilter, element);
        }

        @Override
        boolean isExecutionEvent() {
            return true;
        }
    }

    static final class NearestExecution<T> extends NearestSourceSection<T> {

        NearestExecution(AbstractInstrumenter instrumenter, NearestSectionFilter nearestFilter, SourceSectionFilter filterSourceSection, T element) {
            super(instrumenter, nearestFilter, filterSourceSection, element, true);
        }

        @Override
        boolean isExecutionEvent() {
            return true;
        }
    }

    abstract static class Source<T> extends EventBinding<T> {

        private final SourceSectionFilter filterSourceSection;
        private final SourceSectionFilter inputFilter;

        Source(AbstractInstrumenter instrumenter, SourceSectionFilter filterSourceSection, SourceSectionFilter inputFilter, T element) {
            this(instrumenter, filterSourceSection, inputFilter, element, true);
        }

        Source(AbstractInstrumenter instrumenter, SourceSectionFilter filterSourceSection, SourceSectionFilter inputFilter, T element, boolean attached) {
            super(instrumenter, element, attached);
            this.inputFilter = inputFilter;
            this.filterSourceSection = filterSourceSection;
        }

        final SourceSectionFilter getInputFilter() {
            return inputFilter;
        }

        final Set<Class<?>> getLimitedTags() {
            Set<Class<?>> tags = filterSourceSection.getLimitedTags();
            if (inputFilter != null) {
                Set<Class<?>> inputTags = inputFilter.getLimitedTags();
                if (tags == null) {
                    return inputTags;
                }
                if (inputTags == null) {
                    return tags;
                }
                if (inputTags.equals(tags)) {
                    return tags;
                } else {
                    Set<Class<?>> compoundTags = new HashSet<>();
                    compoundTags.addAll(tags);
                    compoundTags.addAll(inputTags);
                    return compoundTags;
                }
            } else {
                return tags;
            }

        }

        final SourceSectionFilter getFilter() {
            return filterSourceSection;
        }

        boolean isInstrumentedFull(Set<Class<?>> providedTags, RootNode rootNode, Node node, SourceSection nodeSourceSection, @SuppressWarnings("unused") boolean isProbe) {
            boolean instrumentedLeaf = isInstrumentedLeaf(providedTags, node, nodeSourceSection);
            if (!instrumentedLeaf || rootNode == null) {
                return false;
            }
            return isInstrumentedRoot(providedTags, rootNode, rootNode.getSourceSection(), 0);
        }

        /**
         * Parent must match {@link #filterSourceSection} and child must match {@link #inputFilter}.
         */
        boolean isChildInstrumentedFull(Set<Class<?>> providedTags, RootNode rootNode,
                        Node parent, SourceSection parentSourceSection,
                        Node current, SourceSection currentSourceSection) {
            if (inputFilter == null) {
                return false;
            } else if (rootNode == null) {
                return false;
            } else if (!InstrumentationHandler.isInstrumentableNode(parent)) {
                return false;
            }

            if (isInstrumentedLeaf(providedTags, parent, parentSourceSection) && isInstrumentedNodeWithInputFilter(providedTags, current, currentSourceSection)) {
                return isInstrumentedRoot(providedTags, rootNode, rootNode.getSourceSection(), 0);
            }
            return false;
        }

        /**
         * Parent must match {@link #filterSourceSection} and child must match {@link #inputFilter}.
         */
        boolean isChildInstrumentedLeaf(Set<Class<?>> providedTags, RootNode rootNode,
                        Node parent, SourceSection parentSourceSection,
                        Node current, SourceSection currentSourceSection) {
            if (inputFilter == null) {
                return false;
            } else if (rootNode == null) {
                return false;
            } else if (!InstrumentationHandler.isInstrumentableNode(parent)) {
                return false;
            }
            if (isInstrumentedLeaf(providedTags, parent, parentSourceSection) && isInstrumentedNodeWithInputFilter(providedTags, current, currentSourceSection)) {
                return true;
            }
            return false;
        }

        private boolean isInstrumentedNodeWithInputFilter(Set<Class<?>> providedTags, Node current, SourceSection currentSourceSection) {
            try {
                return inputFilter.isInstrumentedNode(providedTags, current, currentSourceSection);
            } catch (Throwable t) {
                if (isLanguageBinding()) {
                    throw t;
                } else {
                    ProbeNode.exceptionEventForClientInstrument(this, inputFilter.toString(), t);
                    return false;
                }
            }
        }

        boolean isInstrumentedRoot(Set<Class<?>> providedTags, RootNode rootNode, SourceSection rootSourceSection, int rootNodeBits) {
            if (!getInstrumenter().isInstrumentableRoot(rootNode)) {
                return false;
            }
            try {
                return getFilter().isInstrumentedRoot(providedTags, rootSourceSection, rootNode, rootNodeBits);
            } catch (Throwable t) {
                if (isLanguageBinding()) {
                    throw t;
                } else {
                    ProbeNode.exceptionEventForClientInstrument(this, getFilter().toString(), t);
                    return false;
                }
            }
        }

        boolean isInstrumentedLeaf(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection section) {
            try {
                boolean instrumented = getFilter().isInstrumentedNode(providedTags, instrumentedNode, section);
                return instrumented;
            } catch (Throwable t) {
                if (isLanguageBinding()) {
                    throw t;
                } else {
                    ProbeNode.exceptionEventForClientInstrument(this, getFilter().toString(), t);
                    return false;
                }
            }
        }

        boolean isInstrumentedSource(com.oracle.truffle.api.source.Source source) {
            if (!getInstrumenter().isInstrumentableSource(source)) {
                return false;
            }
            try {
                return getFilter().isInstrumentedSource(source);
            } catch (Throwable t) {
                if (isLanguageBinding()) {
                    throw t;
                } else {
                    ProbeNode.exceptionEventForClientInstrument(this, getFilter().toString(), t);
                    return false;
                }
            }
        }

        abstract boolean isExecutionEvent();

        boolean isLanguageBinding() {
            return getInstrumenter() instanceof LanguageClientInstrumenter;
        }

    }

    abstract static class NearestSourceSection<T> extends EventBinding.Source<T> {

        private final NearestSectionFilter nearestFilter;
        private final EconomicMap<com.oracle.truffle.api.source.Source, NodeListSection> nearestSourceSections;

        NearestSourceSection(AbstractInstrumenter instrumenter, NearestSectionFilter nearestFilter, SourceSectionFilter filterSourceSection, T element, boolean attached) {
            super(instrumenter, filterSourceSection, null, element, attached);
            assert nearestFilter != null;
            this.nearestFilter = nearestFilter;
            this.nearestSourceSections = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        }

        @Override
        boolean isInstrumentedFull(Set<Class<?>> providedTags, RootNode rootNode, Node node, SourceSection nodeSourceSection, boolean isProbe) {
            boolean instrumentedLeaf = isInstrumentedLeaf(providedTags, node, nodeSourceSection);
            if (!instrumentedLeaf || rootNode == null) {
                return false;
            }
            if (!isProbe || nearestSourceSections == null || isProbe && isNearestSection(nodeSourceSection)) {
                return isInstrumentedRoot(providedTags, rootNode, rootNode.getSourceSection(), 0);
            }
            return false;
        }

        final NearestSectionFilter getNearestFilter() {
            return nearestFilter;
        }

        List<Node> getNearestNodes() {
            if (nearestSourceSections == null) {
                return null;
            }
            synchronized (nearestSourceSections) {
                if (nearestSourceSections.isEmpty()) {
                    return null;
                }
                if (nearestSourceSections.size() == 1) {
                    NodeListSection nearestNodes = nearestSourceSections.getValues().iterator().next();
                    return resolveReferences(nearestNodes.nodes);
                }
                List<Node> nodes = new ArrayList<>(nearestSourceSections.size());
                for (NodeListSection nearest : nearestSourceSections.getValues()) {
                    resolveReferences(nearest.nodes, nodes);
                }
                return nodes;
            }
        }

        private boolean isNearestSection(SourceSection section) {
            if (nearestSourceSections != null) {
                NodeListSection nearest;
                synchronized (nearestSourceSections) {
                    nearest = nearestSourceSections.get(section.getSource());
                }
                return nearest != null && section.equals(nearest.section);
            } else {
                return false;
            }
        }

        // Returns a list of old nodes when the new one is accepted as the new nearest,
        // returns null otherwise.
        NodeListSection setTheNearest(NodeSection nearest, SourceSection visitingRootSourceSection, Set<Class<? extends Tag>> allTags) {
            assert nearestSourceSections != null;
            SourceSection newSection = nearest.section;
            com.oracle.truffle.api.source.Source source = newSection.getSource();
            synchronized (nearestSourceSections) {
                NodeListSection oldNearest = nearestSourceSections.get(source);
                if (oldNearest == null) {
                    return insertNearest(nearest);
                }
                SourceSection oldSection = oldNearest.section;
                NodeListSection newNearest;
                if (!newSection.equals(oldSection)) {
                    newNearest = updateNearest(nearest, visitingRootSourceSection, allTags, oldNearest);
                } else {
                    newNearest = mergeNearest(nearest, oldNearest);
                }
                return newNearest;
            }
        }

        private NodeListSection insertNearest(NodeSection nearest) {
            List<WeakReference<Node>> list = new LinkedList<>();
            list.add(new WeakReference<>(nearest.node));
            SourceSection newSection = nearest.section;
            com.oracle.truffle.api.source.Source source = newSection.getSource();
            nearestSourceSections.put(source, new NodeListSection(list, newSection));
            return new NodeListSection(Collections.emptyList(), null);
        }

        private NodeListSection updateNearest(NodeSection nearest, SourceSection visitingRootSourceSection, Set<Class<? extends Tag>> allTags, NodeListSection oldNearest) {
            // We need to compute which one is closer
            List<WeakReference<Node>> oldNodes = oldNearest.nodes;
            SourceSection oldSection = oldNearest.section;
            Node oldNode = getFirstReferencedNode(oldNodes);
            if (oldNode == null || NearestNodesCollector.isCloser(nearest, visitingRootSourceSection, oldNode, oldSection, nearestFilter, allTags)) {
                // all nodes were disposed, or the new one is closer
                List<WeakReference<Node>> oldNodesCopy = new ArrayList<>(oldNodes);
                oldNodes.clear();
                oldNodes.add(new WeakReference<>(nearest.node));
                SourceSection newSection = nearest.section;
                com.oracle.truffle.api.source.Source source = newSection.getSource();
                nearestSourceSections.put(source, new NodeListSection(oldNodes, newSection));
                return new NodeListSection(oldNodesCopy, oldSection);
            } else {
                // the new node is not closer
                return null;
            }
        }

        private static NodeListSection mergeNearest(NodeSection nearest, NodeListSection oldNearest) {
            // the new nearest has the same source section
            List<WeakReference<Node>> oldNodes = oldNearest.nodes;
            Node newNode = nearest.node;
            boolean contains = false;
            for (WeakReference<Node> nodeRef : oldNodes) {
                if (newNode == nodeRef.get()) {
                    contains = true;
                    break;
                }
            }
            if (!contains) {
                oldNodes.add(new WeakReference<>(newNode));
            }
            return new NodeListSection(Collections.emptyList(), oldNearest.section);
        }

        private static Node getFirstReferencedNode(List<WeakReference<Node>> nodes) {
            Node node = null;
            Iterator<WeakReference<Node>> nodesIt = nodes.iterator();
            while (nodesIt.hasNext()) {
                WeakReference<Node> nodeRef = nodesIt.next();
                Node n = nodeRef.get();
                if (n == null) {
                    nodesIt.remove();
                } else {
                    node = n;
                }
            }
            return node;
        }

        private static List<Node> resolveReferences(List<WeakReference<Node>> nodes) {
            List<Node> resolved = new ArrayList<>(nodes.size());
            resolveReferences(nodes, resolved);
            return resolved;
        }

        private static void resolveReferences(List<WeakReference<Node>> nodes, List<Node> resolved) {
            for (WeakReference<Node> ref : nodes) {
                Node n = ref.get();
                if (n != null) {
                    resolved.add(n);
                }
            }
        }

    }

    static final class Allocation<T> extends EventBinding<T> {

        private final AllocationEventFilter filterAllocation;

        Allocation(AbstractInstrumenter instrumenter, AllocationEventFilter filter, T listener) {
            super(instrumenter, listener);
            this.filterAllocation = filter;
        }

        AllocationEventFilter getAllocationFilter() {
            return filterAllocation;
        }

    }

}
