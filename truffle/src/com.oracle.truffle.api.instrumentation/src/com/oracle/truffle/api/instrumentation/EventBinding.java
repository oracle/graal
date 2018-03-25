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

    /* language bindings needs special treatment. */
    private volatile boolean disposed;

    EventBinding(AbstractInstrumenter instrumenter, T element) {
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
     * @return the filter being applied to the subscription's stream of notifications
     *
     * @since 0.12
     * @deprecated
     */
    @Deprecated
    public SourceSectionFilter getFilter() {
        return null;
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
            instrumenter.disposeBinding(this);
            disposed = true;
        }
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

        @Override
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
            } else if (!InstrumentationHandler.isInstrumentableNode(parent, parentSourceSection)) {
                return false;
            }

            if (isInstrumentedLeaf(providedTags, parent, parentSourceSection) && inputFilter.isInstrumentedNode(providedTags, current, currentSourceSection)) {
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
            } else if (!InstrumentationHandler.isInstrumentableNode(parent, parentSourceSection)) {
                return false;
            }
            if (isInstrumentedLeaf(providedTags, parent, parentSourceSection) && inputFilter.isInstrumentedNode(providedTags, current, currentSourceSection)) {
                return true;
            }
            return false;
        }

        boolean isInstrumentedRoot(Set<Class<?>> providedTags, RootNode rootNode, SourceSection rootSourceSection, int rootNodeBits) {
            return getInstrumenter().isInstrumentableRoot(rootNode) && getFilter().isInstrumentedRoot(providedTags, rootSourceSection, rootNode, rootNodeBits);
        }

        boolean isInstrumentedLeaf(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection section) {
            return getFilter().isInstrumentedNode(providedTags, instrumentedNode, section);
        }

        boolean isInstrumentedSource(com.oracle.truffle.api.source.Source source) {
            return getInstrumenter().isInstrumentableSource(source) && getFilter().isInstrumentedSource(source);
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
