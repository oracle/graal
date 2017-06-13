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

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Provides the capabilities to attach {@link ExecutionEventNodeFactory} and
 * {@link ExecutionEventListener} instances for a set of source locations specified by a
 * {@link SourceSectionFilter}. The result of an attachment is a {@link EventBinding binding}.
 *
 * @see #attachFactory(SourceSectionFilter, ExecutionEventNodeFactory)
 * @see #attachListener(SourceSectionFilter, ExecutionEventListener)
 * @since 0.12
 */
public abstract class Instrumenter {

    Instrumenter() {
    }

    /**
     * Starts event notification for a given {@link ExecutionEventNodeFactory factory} and returns a
     * {@link EventBinding binding} which represents a handle to dispose the notification.
     *
     * @since 0.12
     */
    public abstract <T extends ExecutionEventNodeFactory> EventBinding<T> attachFactory(SourceSectionFilter filter, T factory);

    /**
     * Starts event notification for a given {@link ExecutionEventListener listener} and returns a
     * {@link EventBinding binding} which represents a handle to dispose the notification.
     *
     * @since 0.12
     */
    public abstract <T extends ExecutionEventListener> EventBinding<T> attachListener(SourceSectionFilter filter, T listener);

    /**
     * Starts notifications for each newly loaded {@link Source} and returns a
     * {@linkplain EventBinding binding} that can be used to terminate notifications. Only
     * subsequent loads will be notified unless {@code includeExistingSources} is true, in which
     * case a notification for each previous load will be delivered before this method returns.
     * <p>
     * <strong>Note:</strong> the provided {@link SourceSectionFilter} must only contain filters on
     * {@link SourceSectionFilter.Builder#sourceIs(Source...) sources} or
     * {@link SourceSectionFilter.Builder#mimeTypeIs(String...) mime types}.
     *
     * @param filter a filter on which sources trigger events. Only filters are allowed.
     * @param listener a listener that gets notified if a source was loaded
     * @param includeExistingSources whether or not this listener should be notified for sources
     *            which were already loaded at the time when this listener was attached.
     * @return a handle for stopping the notification stream
     *
     * @see LoadSourceListener#onLoad(LoadSourceEvent)
     *
     * @since 0.15
     */
    public abstract <T extends LoadSourceListener> EventBinding<T> attachLoadSourceListener(SourceSectionFilter filter, T listener, boolean includeExistingSources);

    /**
     * Starts notifications for each {@link SourceSection} in every newly loaded {@link Source} and
     * returns a {@linkplain EventBinding binding} that can be used to terminate notifications. Only
     * subsequent loads will be notified unless {@code includeExistingSourceSections} is true, in
     * which case a notification for each previous load will be delivered before this method
     * returns.
     *
     * @param filter a filter on which sources sections trigger events
     * @param listener a listener that gets notified if a source section was loaded
     * @param includeExistingSourceSections whether or not this listener should be notified for
     *            sources which were already loaded at the time when this listener was attached.
     * @return a handle for stopping the notification stream
     *
     * @see LoadSourceSectionListener#onLoad(LoadSourceSectionEvent)
     *
     * @since 0.15
     */
    public abstract <T extends LoadSourceSectionListener> EventBinding<T> attachLoadSourceSectionListener(SourceSectionFilter filter, T listener, boolean includeExistingSourceSections);

    /**
     * Attach an output stream as a consumer of the {@link TruffleInstrument.Env#out() standard
     * output}. The consumer output stream receives all output that goes to
     * {@link TruffleInstrument.Env#out()} since this call, including output emitted by the
     * {@link com.oracle.truffle.api.vm.PolyglotEngine} this instrumenter is being executed in,
     * output from instruments (including this one), etc. Be sure to {@link EventBinding#dispose()
     * dispose} the binding when it's not used any more.
     *
     * @since 0.25
     */
    public abstract <T extends OutputStream> EventBinding<T> attachOutConsumer(T stream);

    /**
     * Attach an output stream as a consumer of the {@link TruffleInstrument.Env#err() error output}
     * . The consumer output stream receives all error output that goes to
     * {@link TruffleInstrument.Env#err()} since this call, including error output emitted by the
     * {@link com.oracle.truffle.api.vm.PolyglotEngine} this instrumenter is being executed in,
     * error output from instruments (including this one), etc. Be sure to
     * {@link EventBinding#dispose() dispose} the binding when it's not used any more.
     *
     * @since 0.25
     */
    public abstract <T extends OutputStream> EventBinding<T> attachErrConsumer(T stream);

    /**
     * Attach an {@link AllocationListener listener} to be notified about allocations of guest
     * language values. Be sure to {@link EventBinding#dispose() dispose} the binding when it's not
     * used any more.
     *
     * @since 0.27
     */
    public abstract <T extends AllocationListener> EventBinding<T> attachAllocationListener(AllocationEventFilter filter, T listener);

    /**
     * Returns a filtered list of loaded {@link SourceSection} instances.
     *
     * @param filter criterion for inclusion
     * @return unmodifiable list of instances that pass the filter
     *
     * @since 0.18
     */
    public final List<SourceSection> querySourceSections(SourceSectionFilter filter) {
        final List<SourceSection> sourceSectionList = new ArrayList<>();
        EventBinding<?> binding = attachLoadSourceSectionListener(filter, new LoadSourceSectionListener() {
            public void onLoad(LoadSourceSectionEvent event) {
                sourceSectionList.add(event.getSourceSection());
            }
        }, true);
        binding.dispose();
        return Collections.unmodifiableList(sourceSectionList);
    }

    /**
     * Returns an unmodifiable {@link Set} of tag classes which where associated with this node. If
     * the instrumenter is used as a {@link TruffleLanguage} then only nodes can be queried for tags
     * that are associated with the current language otherwise an {@link IllegalArgumentException}
     * is thrown. The given node must not be <code>null</code>. If the given node is not
     * instrumentable, the given node is not yet adopted by a {@link RootNode} or the given tag was
     * not {@link ProvidedTags provided} by the language then always an empty {@link Set} is
     * returned.
     *
     * @param node the node to query
     * @return an unmodifiable {@link Set} of tag classes which where associated with this node.
     * @since 0.12
     */
    public abstract Set<Class<?>> queryTags(Node node);

}
