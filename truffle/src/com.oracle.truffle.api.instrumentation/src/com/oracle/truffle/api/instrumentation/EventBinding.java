/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.AbstractInstrumenter;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.LanguageClientInstrumenter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import java.util.concurrent.Semaphore;

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

    abstract static class LoadSource<T> extends Source<T> {

        private final boolean notifyLoaded;

        LoadSource(AbstractInstrumenter instrumenter, SourceSectionFilter filterSourceSection, SourceSectionFilter inputFilter, T element, boolean attached, boolean notifyLoaded) {
            super(instrumenter, filterSourceSection, inputFilter, element, attached);
            this.notifyLoaded = notifyLoaded;
        }

        final boolean isNotifyLoaded() {
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

        SourceSectionFilter getInputFilter() {
            return inputFilter;
        }

        Set<Class<?>> getLimitedTags() {
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

        @SuppressWarnings("deprecation")
        public SourceSectionFilter getFilter() {
            return filterSourceSection;
        }

        boolean isInstrumentedFull(Set<Class<?>> providedTags, RootNode rootNode, Node node, SourceSection nodeSourceSection) {
            if (isInstrumentedLeaf(providedTags, node, nodeSourceSection)) {
                if (rootNode == null) {
                    return false;
                }
                return isInstrumentedRoot(providedTags, rootNode, rootNode.getSourceSection(), 0);
            }
            return false;
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
                return getFilter().isInstrumentedNode(providedTags, instrumentedNode, section);
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
