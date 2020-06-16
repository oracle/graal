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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.AbstractInstrumenter;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.LanguageClientInstrumenter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

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

    volatile boolean disposing;
    /* language bindings needs special treatment. */
    private volatile boolean disposed;

    EventBinding(AbstractInstrumenter instrumenter, T element) {
        if (element == null) {
            throw new NullPointerException();
        }
        this.instrumenter = instrumenter;
        this.element = element;
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
     * @return whether the subscription has been permanently cancelled.
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

    static final class Source<T> extends EventBinding<T> {

        private final AbstractInstrumenter instrumenter;
        private final SourceSectionFilter filterSourceSection;
        private final SourceSectionFilter inputFilter;
        private final boolean isExecutionEvent;

        Source(AbstractInstrumenter instrumenter, SourceSectionFilter filterSourceSection, SourceSectionFilter inputFilter, T element, boolean isExecutionEvent) {
            super(instrumenter, element);
            this.instrumenter = instrumenter;
            this.inputFilter = inputFilter;
            this.filterSourceSection = filterSourceSection;
            this.isExecutionEvent = isExecutionEvent;
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

        boolean isExecutionEvent() {
            return isExecutionEvent;
        }

        boolean isLanguageBinding() {
            return instrumenter instanceof LanguageClientInstrumenter;
        }

        AbstractInstrumenter getInstrumenter() {
            return instrumenter;
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
