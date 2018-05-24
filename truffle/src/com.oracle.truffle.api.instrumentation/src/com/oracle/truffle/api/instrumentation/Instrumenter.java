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
 * Provides capabilities to attach listeners for execution, load, output and allocation events. The
 * instrumenter remains usable as long as the engine is not closed. The instrumenter methods are
 * safe to be used from arbitrary other threads.
 *
 * @since 0.12
 */
public abstract class Instrumenter {

    Instrumenter() {
    }

    /**
     * @since 0.12
     * @deprecated in 0.32 use
     *             {@link #attachExecutionEventFactory(SourceSectionFilter, ExecutionEventNodeFactory)
     *             instead (rename only)
     */
    @Deprecated
    public final <T extends ExecutionEventNodeFactory> EventBinding<T> attachFactory(SourceSectionFilter eventFilter, T factory) {
        return attachExecutionEventFactory(eventFilter, null, factory);
    }

    /**
     * @since 0.12
     * @deprecated in 0.32 use
     *             {@link #attachExecutionEventFactory(SourceSectionFilter, ExecutionEventNodeFactory)}
     *             instead (rename only)
     */
    @Deprecated
    public final <T extends ExecutionEventListener> EventBinding<T> attachListener(SourceSectionFilter eventFilter, T listener) {
        return attachExecutionEventListener(eventFilter, null, listener);
    }

    /**
     * Starts execution event notification for a given {@link SourceSectionFilter event filter} and
     * {@link ExecutionEventListener listener}. The execution events are delivered to the
     * {@link ExecutionEventListener}.
     * <p>
     * Returns a {@link EventBinding binding} which allows to dispose the attached execution event
     * binding. Disposing the binding removes all probes and wrappers from the AST that were created
     * for this instrument. The removal of probes and wrappers is performed lazily on the next
     * execution of the AST.
     * <p>
     * By default no
     * {@link ExecutionEventNode#onInputValue(com.oracle.truffle.api.frame.VirtualFrame, EventContext, int, Object)
     * input value events} are delivered to the listener. To deliver inputs events use
     * {@link #attachExecutionEventListener(SourceSectionFilter, SourceSectionFilter, ExecutionEventListener)}
     * instead.
     *
     * @param eventFilter filters the events that are reported to the given
     *            {@link ExecutionEventListener listener}
     * @param listener that listens to execution events.
     * @see ExecutionEventListener
     * @since 0.33
     */
    public final <T extends ExecutionEventListener> EventBinding<T> attachExecutionEventListener(SourceSectionFilter eventFilter, T listener) {
        return attachExecutionEventListener(eventFilter, null, listener);
    }

    /**
     * Starts execution event notification for a given {@link SourceSectionFilter event filter} and
     * {@link ExecutionEventNodeFactory factory}. Events are delivered to the
     * {@link ExecutionEventNode} instances created by the factory.
     * <p>
     * Returns a {@link EventBinding binding} which allows to dispose the attached execution event
     * binding. Disposing the binding removes all probes and wrappers from the AST that were created
     * for this instrument. The removal of probes and wrappers is performed lazily on the next
     * execution of the AST.
     * <p>
     * By default no
     * {@link ExecutionEventNode#onInputValue(com.oracle.truffle.api.frame.VirtualFrame, EventContext, int, Object)
     * input value events} are delivered to the created execution event nodes. To deliver inputs
     * events use
     * {@link #attachExecutionEventFactory(SourceSectionFilter, SourceSectionFilter, ExecutionEventNodeFactory)}
     * instead.
     *
     * @param eventFilter filters the events that are reported to the {@link ExecutionEventNode
     *            execution event nodes} created by the factory.
     * @param factory the factory that creates {@link ExecutionEventNode execution event nodes}.
     * @see ExecutionEventNodeFactory
     * @see #attachExecutionEventFactory(SourceSectionFilter, SourceSectionFilter,
     *      ExecutionEventNodeFactory)
     * @since 0.33
     */
    public final <T extends ExecutionEventNodeFactory> EventBinding<T> attachExecutionEventFactory(SourceSectionFilter eventFilter, T factory) {
        return attachExecutionEventFactory(eventFilter, null, factory);
    }

    /**
     * Starts execution event notification for a given {@link SourceSectionFilter event filter} and
     * {@link ExecutionEventListener listener}. The execution events are delivered to the
     * {@link ExecutionEventListener}.
     * <p>
     * Returns a {@link EventBinding binding} which allows to dispose the attached execution event
     * binding. Disposing the binding removes all probes and wrappers from the AST that were created
     * for this instrument. The removal of probes and wrappers is performed lazily on the next
     * execution of the AST.
     * <p>
     * The input filter argument filters which
     * {@link ExecutionEventListener#onInputValue(EventContext, com.oracle.truffle.api.frame.VirtualFrame, EventContext, int, Object)
     * input events} are delivered to the created execution event nodes.
     *
     * @param eventFilter filters the events that are reported to the given
     *            {@link ExecutionEventListener listener}
     * @param inputFilter filters input events, <code>null</code> for no input values
     * @param listener that listens to execution events.
     * @see ExecutionEventListener
     * @see ExecutionEventListener#onInputValue(EventContext,
     *      com.oracle.truffle.api.frame.VirtualFrame, EventContext, int, Object)
     * @since 0.33
     */
    public abstract <T extends ExecutionEventListener> EventBinding<T> attachExecutionEventListener(SourceSectionFilter eventFilter, SourceSectionFilter inputFilter, T listener);

    /**
     * Starts execution event notification for a given {@link SourceSectionFilter event filter} and
     * {@link ExecutionEventNodeFactory factory}. Events are delivered to the
     * {@link ExecutionEventNode} instances created by the factory.
     * <p>
     * Returns a {@link EventBinding binding} which allows to dispose the attached execution event
     * binding. Disposing the binding removes all probes and wrappers from the AST that were created
     * for this instrument. The removal of probes and wrappers is performed lazily on the next
     * execution of the AST.
     * <p>
     * The input filter argument filters which
     * {@link ExecutionEventNode#onInputValue(com.oracle.truffle.api.frame.VirtualFrame, EventContext, int, Object)
     * input events} are delivered to the created execution event nodes.
     *
     * @param eventFilter filters the events that are reported to the {@link ExecutionEventNode
     *            execution event nodes} created by the factory.
     * @param inputFilter filters input events, <code>null</code> for no input values
     * @param factory the factory that creates {@link ExecutionEventNode execution event nodes}.
     * @see ExecutionEventNodeFactory
     * @see ExecutionEventNode#onInputValue(com.oracle.truffle.api.frame.VirtualFrame, EventContext,
     *      int, Object)
     * @since 0.33
     */
    public abstract <T extends ExecutionEventNodeFactory> EventBinding<T> attachExecutionEventFactory(SourceSectionFilter eventFilter, SourceSectionFilter inputFilter, T factory);

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
     * @param filter a filter on which sources trigger events. Only source filters are allowed.
     * @param listener a listener that gets notified if a source was loaded
     * @param includeExistingSources whether or not this listener should be notified for sources
     *            which were already loaded at the time when this listener was attached.
     * @return a handle for stopping the notification stream
     *
     * @see LoadSourceListener#onLoad(LoadSourceEvent)
     *
     * @since 0.15
     * @deprecated Use {@link #attachLoadSourceListener(SourceFilter, LoadSourceListener, boolean)}
     */
    @Deprecated
    public abstract <T extends LoadSourceListener> EventBinding<T> attachLoadSourceListener(SourceSectionFilter filter, T listener, boolean includeExistingSources);

    /**
     * Starts notifications for each newly loaded {@link Source} and returns a
     * {@linkplain EventBinding binding} that can be used to terminate notifications. Only
     * subsequent loads will be notified unless {@code includeExistingSources} is true, in which
     * case a notification for each previous load will be delivered before this method returns.
     *
     * @param filter a filter on which sources events are triggered.
     * @param listener a listener that gets notified if a source was loaded
     * @param includeExistingSources whether or not this listener should be notified for sources
     *            which were already loaded at the time when this listener was attached.
     * @return a handle for stopping the notification stream
     *
     * @see LoadSourceListener#onLoad(LoadSourceEvent)
     *
     * @since 0.33
     */
    public abstract <T extends LoadSourceListener> EventBinding<T> attachLoadSourceListener(SourceFilter filter, T listener, boolean includeExistingSources);

    /**
     * Starts notifications for each newly executed {@link Source} and returns a
     * {@linkplain EventBinding binding} that can be used to terminate notifications. Only
     * subsequent executions will be notified unless {@code includeExecutedSources} is true, in
     * which case a notification for each previously executed source will be delivered before this
     * method returns. A source is reported as executed if any of it's {@link RootNode}s start to be
     * executed.
     *
     * @param filter a filter on which source events are triggered.
     * @param listener a listener that gets notified if a source was loaded
     * @param includeExecutedSources whether or not this listener should be notified for sources
     *            which were already executed at the time when this listener was attached.
     * @return a handle for stopping the notification stream
     *
     * @see ExecuteSourceListener#onExecute(ExecuteSourceEvent)
     *
     * @since 0.33
     */
    public abstract <T extends ExecuteSourceListener> EventBinding<T> attachExecuteSourceListener(SourceFilter filter, T listener, boolean includeExecutedSources);

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
     * Notifies the listener for each {@link SourceSection} in every loaded {@link Source} that
     * corresponds to the filter. Only loaded sections are notified, synchronously.
     *
     * @param filter a filter on which source sections trigger events
     * @param listener a listener that gets notified with loaded source sections
     *
     * @see LoadSourceSectionListener#onLoad(LoadSourceSectionEvent)
     *
     * @since 1.0
     */
    public abstract void visitLoadedSourceSections(SourceSectionFilter filter, LoadSourceSectionListener listener);

    /**
     * Attach an output stream as a consumer of the {@link TruffleInstrument.Env#out() standard
     * output}. The consumer output stream receives all output that goes to
     * {@link TruffleInstrument.Env#out()} since this call, including output emitted by the
     * {@link org.graalvm.polyglot.Engine} this instrumenter is being executed in, output from
     * instruments (including this one), etc. Be sure to {@link EventBinding#dispose() dispose} the
     * binding when it's not used any more.
     *
     * @since 0.25
     */
    public abstract <T extends OutputStream> EventBinding<T> attachOutConsumer(T stream);

    /**
     * Attach an output stream as a consumer of the {@link TruffleInstrument.Env#err() error output}
     * . The consumer output stream receives all error output that goes to
     * {@link TruffleInstrument.Env#err()} since this call, including error output emitted by the
     * {@link org.graalvm.polyglot.Engine} this instrumenter is being executed in, error output from
     * instruments (including this one), etc. Be sure to {@link EventBinding#dispose() dispose} the
     * binding when it's not used any more.
     *
     * @since 0.25
     */
    public abstract <T extends OutputStream> EventBinding<T> attachErrConsumer(T stream);

    /**
     * Attach a {@link AllocationListener listener} to be notified about allocations of guest
     * language values. Be sure to {@link EventBinding#dispose() dispose} the binding when it's not
     * used any more.
     *
     * @since 0.27
     */
    public abstract <T extends AllocationListener> EventBinding<T> attachAllocationListener(AllocationEventFilter filter, T listener);

    /**
     * Attach a {@link ContextsListener listener} to be notified about changes in contexts in guest
     * language application. This is supported in {@link TruffleInstrument.Env#getInstrumenter()}
     * only.
     *
     * @param listener a listener to receive the context events
     * @param includeActiveContexts whether or not this listener should be notified for present
     *            active contexts
     * @return a handle for unregistering the listener
     * @since 0.30
     */
    public abstract <T extends ContextsListener> EventBinding<T> attachContextsListener(T listener, boolean includeActiveContexts);

    /**
     * Attach a {@link ThreadsListener listener} to be notified about changes in threads in guest
     * language application. This is supported in {@link TruffleInstrument.Env#getInstrumenter()}
     * only.
     *
     * @param listener a listener to receive the context events
     * @param includeInitializedThreads whether or not this listener should be notified for present
     *            initialized threads
     * @return a handle for unregistering the listener
     * @since 0.30
     */
    public abstract <T extends ThreadsListener> EventBinding<T> attachThreadsListener(T listener, boolean includeInitializedThreads);

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
        visitLoadedSourceSections(filter, new LoadSourceSectionListener() {
            @Override
            public void onLoad(LoadSourceSectionEvent event) {
                sourceSectionList.add(event.getSourceSection());
            }
        });
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
