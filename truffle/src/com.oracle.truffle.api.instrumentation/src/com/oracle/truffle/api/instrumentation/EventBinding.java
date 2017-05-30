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

import java.util.Set;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.AbstractInstrumenter;
import com.oracle.truffle.api.instrumentation.InstrumentationHandler.LanguageClientInstrumenter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * An {@linkplain Instrumenter instrumentation} handle for a subscription to a
 * {@linkplain SourceSectionFilter filtered} stream of execution event notifications.
 * <p>
 * The subscription remains active until:
 * <ul>
 * <li>explicit {@linkplain #dispose() disposal} of the subscription; or</li>
 *
 * <li>the instrument that created the subscription is
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Instrument#setEnabled(boolean) disabled}; or
 *
 * <li>the instrumented engine is {@linkplain com.oracle.truffle.api.vm.PolyglotEngine#dispose()
 * disposed}.</li>
 * </ul>
 * </p>
 *
 * @param <T> subscriber type: {@link ExecutionEventListener} or {@link ExecutionEventNodeFactory}.
 * @see Instrumenter#attachListener(SourceSectionFilter, ExecutionEventListener)
 * @see Instrumenter#attachFactory(SourceSectionFilter, ExecutionEventNodeFactory)
 *
 * @since 0.12
 */
public final class EventBinding<T> {

    private final AbstractInstrumenter instrumenter;
    private final SourceSectionFilter filter;
    private final T element;
    private final boolean isExecutionEvent;

    /* language bindings needs special treatment. */
    private volatile boolean disposed;

    EventBinding(AbstractInstrumenter instrumenter, SourceSectionFilter query, T element, boolean isExecutionEvent) {
        this.instrumenter = instrumenter;
        this.filter = query;
        this.element = element;
        this.isExecutionEvent = isExecutionEvent;
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
     */
    public SourceSectionFilter getFilter() {
        return filter;
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
    public synchronized void dispose() throws IllegalStateException {
        CompilerAsserts.neverPartOfCompilation();
        if (!disposed) {
            instrumenter.disposeBinding(this);
            disposed = true;
        }
    }

    boolean isInstrumentedFull(Set<Class<?>> providedTags, RootNode rootNode, Node node, SourceSection nodeSourceSection) {
        if (isInstrumentedLeaf(providedTags, node, nodeSourceSection)) {
            if (rootNode == null) {
                return false;
            }
            return isInstrumentedRoot(providedTags, rootNode, rootNode.getSourceSection());
        }
        return false;
    }

    boolean isInstrumentedRoot(Set<Class<?>> providedTags, RootNode rootNode, SourceSection rootSourceSection) {
        return getInstrumenter().isInstrumentableRoot(rootNode) && getFilter().isInstrumentedRoot(providedTags, rootSourceSection, rootNode);
    }

    boolean isInstrumentedLeaf(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection section) {
        return getFilter().isInstrumentedNode(providedTags, instrumentedNode, section);
    }

    boolean isInstrumentedSource(Source source) {
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

    synchronized void disposeBulk() {
        disposed = true;
    }

}
