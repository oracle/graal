/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrument;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.instrument.ProbeInstrument.EvalInstrument;
import com.oracle.truffle.api.instrument.TagInstrument.AfterTagInstrument;
import com.oracle.truffle.api.instrument.TagInstrument.BeforeTagInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import java.util.Map;

/**
 * Client access to instrumentation services in a Truffle execution environment.
 * <p>
 * Services include:
 * <ul>
 * <li>A collection of {@linkplain Probe Probes}, each of which is {@linkplain #probe(Node) created}
 * by clients in permanent association with a particular {@linkplain SourceSection source code
 * location} in an AST executing in this environment. The Probe keeps tracks of all <em>clones</em>
 * of the AST and ensures that any instrumentation <em>attached</em> to the Probe is put into effect
 * in each AST clone at the {@link Node} that corresponds to the Probe's source code location.</li>
 * <p>
 * <li>A collection of {@linkplain ProbeListener listeners} that have registered to be notified
 * whenever a new {@link Probe} is created in this environment and whenever a new {@link SyntaxTag}
 * (or simply "tag") is newly added to an existing {@link Probe} in this environment.</li>
 * <p>
 * <li>The ability to {@linkplain #findProbesTaggedAs(SyntaxTag) enumerate} all existing
 * {@linkplain Probe Probes} in this environment, optionally filtered to include only those to which
 * a specific {@linkplain SyntaxTag tag} has been added.</li>
 * <p>
 * <li>The ability to <em>attach</em> a client-provided <em>event listener</em> to an existing
 * {@link Probe} in this environment. The listener subsequently receives notification of execution
 * events at the {@linkplain Node Nodes} corresponding to the Probe's {@linkplain SourceSection
 * source code location}. The <em>attachment</em> also produces a {@link ProbeInstrument} that
 * represents the binding, and which can be used to {@linkplain Instrument#dispose() detach} the
 * listener from the Probe and stop event notification. A listener can be attached to any number of
 * Probes, each time producing a new {@linkplain ProbeInstrument} that represents the binding.</li>
 * <p>
 * <li>The ability to <em>attach</em> a client-provided <em>event listener</em> to a specific
 * {@linkplain SyntaxTag tag} for all {@linkplain Probe Probes} in the environment. A maximum of
 * <em>one</em> listener may be attached to receive notification of "<em>before</em>" execution
 * events (i.e. the flow of execution is just about to enter a {@link Node}), and a maximum of
 * <em>one</em> listener may be attached to receive notification of "<em>after</em>" execution
 * events. The <em>attachment</em> also produces a {@link TagInstrument} that represents the
 * binding, and which can be used to {@linkplain Instrument#dispose() detach} the listener from the
 * Probes and stop event notification. This mechanism is designed for much lower runtime overhead
 * than other ways to accomplish the same thing, e.g. by attaching one listener individually to
 * every Probe with the desired tag.</li>
 * <p>
 * <li>A collection of {@linkplain Tool Tools}, possibly client-provided, that can be
 * {@linkplain #install(Tool) installed} for data collection, possibly providing their own services
 * with the resulting information.</li>
 * </ul>
 */
public final class Instrumenter {

    private static final boolean TRACE = false;
    private static final String TRACE_PREFIX = "Instrumenter: ";
    private static final PrintStream OUT = System.out;

    private static void trace(String format, Object... args) {
        if (TRACE) {
            OUT.println(TRACE_PREFIX + String.format(format, args));
        }
    }

    private enum ToolState {

        /** Not yet installed, inert. */
        UNINSTALLED,

        /** Installed, collecting data. */
        ENABLED,

        /** Installed, not collecting data. */
        DISABLED,

        /** Was installed, but now removed, inactive, and no longer usable. */
        DISPOSED;
    }

    /**
     * {@linkplain Instrumenter Instrumentation}-based collectors of data during Guest Language
     * program execution.
     * <p>
     * Tools share a common <em>life cycle</em>:
     * <ul>
     * <li>A newly created tool is "UNINSTALLED"; it does nothing until
     * {@linkplain Instrumenter#install(Tool) installed} .</li>
     * <li>An installed tool becomes "ENABLED" and immediately begins attaching
     * {@linkplain ProbeInstrument instrumentation} to ASTs and collecting execution data.</li>
     * <li>A tool may only be installed once.</li>
     * <li>It is possible to install multiple instances of a tool, possibly (but not necessarily)
     * configured differently with respect to what data is being collected.</li>
     * <li>Once installed, a tool can be {@linkplain #setEnabled(boolean) "ENABLED" and "DISABLED"}
     * arbitrarily.</li>
     * <li>A disabled tool:
     * <ul>
     * <li>Collects no data;</li>
     * <li>Retains existing AST instrumentation;</li>
     * <li>Continues to instrument newly created ASTs; and</li>
     * <li>Retains previously collected data.</li>
     * </ul>
     * </li>
     * <li>An installed tool may be {@linkplain #reset() reset} at any time, which leaves the tool
     * installed but with all previously collected data removed.</li>
     * <li>A {@linkplain #dispose() disposed} tool removes all instrumentation (but not
     * {@linkplain Probe probes}) and becomes permanently disabled; previously collected data
     * persists.</li>
     * </ul>
     * <p>
     * Tool-specific methods that access data collected by the tool should:
     * <ul>
     * <li>Return modification-safe representations of the data; and</li>
     * <li>Not change the state of the data.</li>
     * </ul>
     */
    public abstract static class Tool {

        private ToolState toolState = ToolState.UNINSTALLED;

        private Instrumenter instrumenter;

        protected Tool() {
        }

        final void install(Instrumenter inst) {
            checkUninstalled();
            this.instrumenter = inst;

            if (internalInstall()) {
                toolState = ToolState.ENABLED;
            }
            instrumenter.tools.add(this);
        }

        /**
         * @return whether the tool is currently collecting data.
         */
        public final boolean isEnabled() {
            return toolState == ToolState.ENABLED;
        }

        /**
         * Switches tool state between <em>enabled</em> (collecting data) and <em>disabled</em> (not
         * collecting data, but keeping data already collected).
         *
         * @throws IllegalStateException if not yet installed or disposed.
         */
        public final void setEnabled(boolean isEnabled) {
            checkInstalled();
            internalSetEnabled(isEnabled);
            toolState = isEnabled ? ToolState.ENABLED : ToolState.DISABLED;
        }

        /**
         * Clears any data already collected, but otherwise does not change the state of the tool.
         *
         * @throws IllegalStateException if not yet installed or disposed.
         */
        public final void reset() {
            checkInstalled();
            internalReset();
        }

        /**
         * Makes the tool permanently <em>disabled</em>, removes instrumentation, but keeps data
         * already collected.
         *
         * @throws IllegalStateException if not yet installed or disposed.
         */
        public final void dispose() {
            checkInstalled();
            internalDispose();
            toolState = ToolState.DISPOSED;
            instrumenter.tools.remove(this);
        }

        /**
         * @return whether the installation succeeded.
         */
        protected abstract boolean internalInstall();

        /**
         * No subclass action required.
         *
         * @param isEnabled
         */
        protected void internalSetEnabled(boolean isEnabled) {
        }

        protected abstract void internalReset();

        protected abstract void internalDispose();

        protected final Instrumenter getInstrumenter() {
            return instrumenter;
        }

        /**
         * Ensure that the tool is currently installed.
         *
         * @throws IllegalStateException
         */
        private void checkInstalled() throws IllegalStateException {
            if (toolState == ToolState.UNINSTALLED) {
                throw new IllegalStateException("Tool " + getClass().getSimpleName() + " not yet installed");
            }
            if (toolState == ToolState.DISPOSED) {
                throw new IllegalStateException("Tool " + getClass().getSimpleName() + " has been disposed");
            }
        }

        /**
         * Ensure that the tool has not yet been installed.
         *
         * @throws IllegalStateException
         */
        private void checkUninstalled() {
            if (toolState != ToolState.UNINSTALLED) {
                throw new IllegalStateException("Tool " + getClass().getSimpleName() + " has already been installed");
            }
        }
    }

    private final Object vm;

    /** Tools that have been created, but not yet disposed. */
    Set<Tool> tools = new HashSet<>();

    private final Set<ASTProber> astProbers = Collections.synchronizedSet(new LinkedHashSet<ASTProber>());

    private final List<ProbeListener> probeListeners = new ArrayList<>();

    /**
     * All Probes that have been created.
     */
    private final List<WeakReference<Probe>> probes = new ArrayList<>();

    /**
     * A global instrument that triggers notification just before executing any Node that is Probed
     * with a matching tag.
     */
    @CompilationFinal private BeforeTagInstrument beforeTagInstrument = null;

    /**
     * A global instrument that triggers notification just after executing any Node that is Probed
     * with a matching tag.
     */
    @CompilationFinal private AfterTagInstrument afterTagInstrument = null;

    Instrumenter(Object vm) {
        this.vm = vm;
    }

    /**
     * Prepares an AST node for {@linkplain ProbeInstrument instrumentation}, where the node is
     * presumed to be part of a well-formed Truffle AST that has not yet been executed.
     * <p>
     * <em>Probing</em> a node is idempotent:
     * <ul>
     * <li>If the node has not been Probed, modifies the AST by first inserting a
     * {@linkplain #createWrapperNode(Node) wrapper node} between the node and its parent and then
     * returning the newly created Probe associated with the wrapper.</li>
     * <li>If the node has been Probed, returns the Probe associated with its existing wrapper.</li>
     * <li>No more than one {@link Probe} may be associated with a node, so a wrapper may not wrap
     * another wrapper.</li>
     * </ul>
     * It is a runtime error to attempt Probing an AST node with no parent.
     *
     * @return a (possibly newly created) {@link Probe} associated with this node.
     * @throws ProbeException (unchecked) when a Probe cannot be created, leaving the AST unchanged
     */
    @SuppressWarnings("rawtypes")
    public Probe probe(Node node) {

        final Node parent = node.getParent();

        if (node instanceof WrapperNode) {
            throw new ProbeException(ProbeFailure.Reason.WRAPPER_NODE, null, node, null);
        }

        if (parent == null) {
            throw new ProbeException(ProbeFailure.Reason.NO_PARENT, null, node, null);
        }

        if (parent instanceof WrapperNode) {
            final WrapperNode wrapper = (WrapperNode) parent;
            if (TRACE) {
                final Probe probe = wrapper.getProbe();
                final SourceSection sourceSection = wrapper.getChild().getSourceSection();
                final String location = sourceSection == null ? "<unknown>" : sourceSection.getShortDescription();
                trace("PROBE FOUND %s %s %s", "Probe@", location, probe.getTagsDescription());
            }
            return wrapper.getProbe();
        }

        if (!ACCESSOR.isInstrumentable(vm, node)) {
            throw new ProbeException(ProbeFailure.Reason.NOT_INSTRUMENTABLE, parent, node, null);
        }

        // Create a new wrapper/Probe with this node as its child.
        final WrapperNode wrapper = createWrapperNode(node);

        if (wrapper == null || !(wrapper instanceof Node)) {
            throw new ProbeException(ProbeFailure.Reason.NO_WRAPPER, parent, node, wrapper);
        }

        final Node wrapperNode = (Node) wrapper;

        if (!node.isSafelyReplaceableBy(wrapperNode)) {
            throw new ProbeException(ProbeFailure.Reason.WRAPPER_TYPE, parent, node, wrapper);
        }

        final SourceSection sourceSection = wrapper.getChild().getSourceSection();
        final ProbeNode probeNode = new ProbeNode();
        Class<? extends TruffleLanguage> l = ACCESSOR.findLanguage(wrapper.getChild().getRootNode());
        final Probe probe = new Probe(this, l, probeNode, sourceSection);
        probes.add(new WeakReference<>(probe));
        probeNode.probe = probe;  // package private access
        wrapper.insertEventHandlerNode(probeNode);
        node.replace(wrapperNode);
        if (TRACE) {
            final String location = sourceSection == null ? "<unknown>" : sourceSection.getShortDescription();
            trace("PROBED %s %s %s", "Probe@", location, probe.getTagsDescription());
        }
        for (ProbeListener listener : probeListeners) {
            listener.newProbeInserted(probe);
        }
        return probe;
    }

    /**
     * Adds a {@link ProbeListener} to receive events.
     */
    public void addProbeListener(ProbeListener listener) {
        assert listener != null;
        probeListeners.add(listener);
    }

    /**
     * Removes a {@link ProbeListener}. Ignored if listener not found.
     */
    public void removeProbeListener(ProbeListener listener) {
        probeListeners.remove(listener);
    }

    /**
     * Returns all {@link Probe}s holding a particular {@link SyntaxTag}, or the whole collection of
     * probes if the specified tag is {@code null}.
     *
     * @return A collection of probes containing the given tag.
     */
    public Collection<Probe> findProbesTaggedAs(SyntaxTag tag) {
        final List<Probe> taggedProbes = new ArrayList<>();
        for (WeakReference<Probe> ref : probes) {
            Probe probe = ref.get();
            if (probe != null) {
                if (tag == null || probe.isTaggedAs(tag)) {
                    taggedProbes.add(ref.get());
                }
            }
        }
        return taggedProbes;
    }

    /**
     * Enables instrumentation at selected nodes in all subsequently constructed ASTs. Ignored if
     * the argument is already registered, runtime error if argument is {@code null}.
     */
    public void registerASTProber(ASTProber prober) {
        if (prober == null) {
            throw new IllegalArgumentException("Register non-null ASTProbers");
        }
        astProbers.add(prober);
    }

    public void unregisterASTProber(ASTProber prober) {
        astProbers.remove(prober);
    }

    /**
     * <em>Attaches</em> a {@link SimpleInstrumentListener listener} to a {@link Probe}, creating a
     * <em>binding</em> called an {@link ProbeInstrument}. Until the Instrument is
     * {@linkplain ProbeInstrument#dispose() disposed}, it routes synchronous notification of
     * {@linkplain EventHandlerNode execution events} taking place at the Probe's AST location to
     * the listener.
     *
     * @param probe source of AST execution events, non-null
     * @param listener receiver of execution events
     * @param instrumentInfo optional documentation about the Instrument
     * @return a handle for access to the binding
     */
    public ProbeInstrument attach(Probe probe, SimpleInstrumentListener listener, String instrumentInfo) {
        assert probe.getInstrumenter() == this;
        final ProbeInstrument instrument = new ProbeInstrument.SimpleInstrument(listener, instrumentInfo);
        probe.attach(instrument);
        return instrument;
    }

    /**
     * <em>Attaches</em> a {@link StandardInstrumentListener listener} to a {@link Probe}, creating
     * a <em>binding</em> called an {@link ProbeInstrument}. Until the Instrument is
     * {@linkplain ProbeInstrument#dispose() disposed}, it routes synchronous notification of
     * {@linkplain EventHandlerNode execution events} taking place at the Probe's AST location to
     * the listener.
     *
     * @param probe source of AST execution events, non-null
     * @param listener receiver of execution events
     * @param instrumentInfo optional documentation about the Instrument
     * @return a handle for access to the binding
     */
    public ProbeInstrument attach(Probe probe, StandardInstrumentListener listener, String instrumentInfo) {
        assert probe.getInstrumenter() == this;
        final ProbeInstrument instrument = new ProbeInstrument.StandardInstrument(listener, instrumentInfo);
        probe.attach(instrument);
        return instrument;
    }

    /**
     * <em>Attaches</em> a fragment of source text that is to be evaluated just before execution
     * enters the location of a {@link Probe}, creating a <em>binding</em> called an
     * {@link ProbeInstrument}. The outcome of the evaluation is reported to an optional
     * {@link EvalInstrumentListener listener}, but the outcome does not affect the flow of guest
     * language execution, even if the evaluation produces an exception.
     * <p>
     * The source text is assumed to be expressed in the language identified by its associated
     * {@linkplain Source#getMimeType() MIME type}, if specified, otherwise by the language
     * associated with the AST location associated with the {@link Probe}.
     * <p>
     * The source text is parsed in the lexical context of the AST location associated with the
     * {@link Probe}.
     * <p>
     * The source text executes subject to full Truffle optimization.
     *
     * @param probe source of AST execution events, non-null
     * @param languageClass the language in which the source text is to be executed
     * @param source the source code to be evaluated, non-null and non-empty
     * @param listener optional client callback for results/failure notification
     * @param instrumentInfo instrumentInfo optional documentation about the Instrument
     * @return a handle for access to the binding
     * @deprecated
     */
    @Deprecated
    @SuppressWarnings("rawtypes")
    public ProbeInstrument attach(Probe probe, Class<? extends TruffleLanguage> languageClass, Source source, EvalInstrumentListener listener, String instrumentInfo) {
        return attach(probe, languageClass, source, listener, instrumentInfo, new String[0], new Object[0]);
    }

    /**
     * <em>Attaches</em> a fragment of source text that is to be evaluated just before execution
     * enters the location of a {@link Probe}, creating a <em>binding</em> called an
     * {@link ProbeInstrument}. The outcome of the evaluation is reported to an optional
     * {@link EvalInstrumentListener listener}, but the outcome does not affect the flow of guest
     * language execution, even if the evaluation produces an exception.
     * <p>
     * The source text is assumed to be expressed in the language identified by its associated
     * {@linkplain Source#getMimeType() MIME type}, if specified, otherwise by the language
     * associated with the AST location associated with the {@link Probe}.
     * <p>
     * The source text is parsed in the lexical context of the AST location associated with the
     * {@link Probe}.
     * <p>
     * The source text executes subject to full Truffle optimization.
     *
     * @param probe source of AST execution events, non-null
     * @param source the source code to be evaluated, non-null and non-empty, preferably with
     *            {@link Source#withMimeType(java.lang.String) specified mime type} that determines
     *            the {@link TruffleLanguage} to use when processing the source
     * @param listener optional client callback for results/failure notification
     * @param instrumentInfo instrumentInfo optional documentation about the Instrument
     * @param parameters keys are the parameter names to pass to
     *            {@link TruffleLanguage#parse(com.oracle.truffle.api.source.Source, com.oracle.truffle.api.nodes.Node, java.lang.String...)
     *            parse} method; values will be passed to
     *            {@link CallTarget#call(java.lang.Object...)} returned from the <code>parse</code>
     *            method; the value can be <code>null</code>
     * @return a handle for access to the binding
     */
    public ProbeInstrument attach(Probe probe, Source source, EvalInstrumentListener listener, String instrumentInfo, Map<String, Object> parameters) {
        final int size = parameters == null ? 0 : parameters.size();
        String[] names = new String[size];
        Object[] params = new Object[size];
        if (parameters != null) {
            int index = 0;
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                names[index] = entry.getKey();
                params[index] = entry.getValue();
                index++;
            }
        }
        return attach(probe, null, source, listener, instrumentInfo, names, params);
    }

    @SuppressWarnings("rawtypes")
    private ProbeInstrument attach(Probe probe, Class<? extends TruffleLanguage> languageClass, Source source, EvalInstrumentListener listener, String instrumentInfo, String[] argumentNames,
                    Object[] parameters) {
        assert probe.getInstrumenter() == this;
        Class<? extends TruffleLanguage> foundLanguageClass = null;
        if (languageClass == null) {
            if (source.getMimeType() == null) {
                foundLanguageClass = ACCESSOR.findLanguage(probe);
            }
        } else {
            foundLanguageClass = languageClass;
        }
        final EvalInstrument instrument = new EvalInstrument(foundLanguageClass, source, listener, instrumentInfo, argumentNames, parameters);
        probe.attach(instrument);
        return instrument;
    }

    /**
     * Sets the current "<em>before</em>" TagInstrument; there can be no more than one in effect.
     * <ul>
     * <li>The Instrument triggers a callback just <strong><em>before</em></strong> execution
     * reaches <strong><em>any</em></strong> {@link Probe} (either existing or subsequently created)
     * with the specified {@link SyntaxTag}.</li>
     * <li>Calling {@link TagInstrument#dispose()} removes the instrument.</li>
     * </ul>
     *
     * @param tag identifies the nodes to be instrumented
     * @param listener receiver of <em>before</em> execution events
     * @param instrumentInfo optional, mainly for debugging.
     * @return a newly created, active Instrument
     * @throws IllegalStateException if called when a <em>before</em> Instrument is active.
     */
    public TagInstrument attach(SyntaxTag tag, StandardBeforeInstrumentListener listener, String instrumentInfo) {
        if (beforeTagInstrument != null) {
            throw new IllegalStateException("Only one 'before' TagInstrument at a time");
        }
        this.beforeTagInstrument = new TagInstrument.BeforeTagInstrument(this, tag, listener, instrumentInfo);
        notifyTagInstrumentChange();
        return beforeTagInstrument;
    }

    /**
     * Sets the current "<em>after</em>" TagInstrument; there can be no more than one in effect.
     * <ul>
     * <li>The Instrument triggers a callback just <strong><em>after</em></strong> execution reaches
     * <strong><em>any</em></strong> {@link Probe} (either existing or subsequently created) with
     * the specified {@link SyntaxTag}.</li>
     * <li>Calling {@link TagInstrument#dispose()} removes the instrument.</li>
     * </ul>
     *
     * @param tag identifies the nodes to be instrumented
     * @param listener receiver of <em>after</em> execution events
     * @param instrumentInfo optional, mainly for debugging.
     * @return a newly created, active Instrument
     * @throws IllegalStateException if called when a <em>after</em> Instrument is active.
     */
    public TagInstrument attach(SyntaxTag tag, StandardAfterInstrumentListener listener, String instrumentInfo) {
        if (afterTagInstrument != null) {
            throw new IllegalStateException("Only one 'afater' TagInstrument at a time");
        }
        this.afterTagInstrument = new TagInstrument.AfterTagInstrument(this, tag, listener, instrumentInfo);
        notifyTagInstrumentChange();
        return afterTagInstrument;
    }

    /**
     * Connects the tool to some part of the Truffle runtime, and enable data collection to start.
     *
     * @return the tool
     * @throws IllegalStateException if the tool has previously been installed or has been disposed.
     */
    public Tool install(Tool tool) {
        tool.install(this);
        return tool;
    }

    @SuppressWarnings("unused")
    void executionStarted(Source s) {
    }

    void executionEnded() {
    }

    WrapperNode createWrapperNode(Node node) {
        return ACCESSOR.createWrapperNode(vm, node);
    }

    void tagAdded(Probe probe, SyntaxTag tag, Object tagValue) {
        for (ProbeListener listener : probeListeners) {
            listener.probeTaggedAs(probe, tag, tagValue);
        }
    }

    BeforeTagInstrument getBeforeTagInstrument() {
        return beforeTagInstrument;
    }

    AfterTagInstrument getAfterTagInstrument() {
        return afterTagInstrument;
    }

    void disposeBeforeTagInstrument() {
        beforeTagInstrument = null;
        notifyTagInstrumentChange();
    }

    void disposeAfterTagInstrument() {
        afterTagInstrument = null;
        notifyTagInstrumentChange();
    }

    private void notifyTagInstrumentChange() {
        for (WeakReference<Probe> ref : probes) {
            final Probe probe = ref.get();
            if (probe != null) {
                probe.notifyTagInstrumentsChanged();
            }
        }
    }

    /**
     * Enables instrumentation in a newly created AST by applying all registered instances of
     * {@link ASTProber}.
     */
    private void probeAST(RootNode rootNode) {
        if (!astProbers.isEmpty()) {

            String name = "<?>";
            final SourceSection sourceSection = rootNode.getSourceSection();
            if (sourceSection != null) {
                final Source source = sourceSection.getSource();
                if (source != null) {
                    name = source.getShortName();
                } else {
                    name = sourceSection.getShortDescription();
                }
            }
            trace("START %s", name);
            for (ProbeListener listener : probeListeners) {
                listener.startASTProbing(rootNode);
            }
            for (ASTProber prober : astProbers) {
                prober.probeAST(this, rootNode);
            }
            for (ProbeListener listener : probeListeners) {
                listener.endASTProbing(rootNode);
            }
            trace("FINISHED %s", name);
        }
    }

    static final class AccessorInstrument extends Accessor {

        @Override
        protected Instrumenter createInstrumenter(Object vm) {
            return new Instrumenter(vm);
        }

        @Override
        protected boolean isInstrumentable(Object vm, Node node) {
            return super.isInstrumentable(vm, node);
        }

        @Override
        protected WrapperNode createWrapperNode(Object vm, Node node) {
            return super.createWrapperNode(vm, node);
        }

        @SuppressWarnings("rawtypes")
        @Override
        protected Class<? extends TruffleLanguage> findLanguage(RootNode n) {
            return super.findLanguage(n);
        }

        @SuppressWarnings("rawtypes")
        @Override
        protected Class<? extends TruffleLanguage> findLanguage(Probe probe) {
            return probe.getLanguage();
        }

        @SuppressWarnings("rawtypes")
        @Override
        protected CallTarget parse(Class<? extends TruffleLanguage> languageClass, Source code, Node context, String... argumentNames) throws IOException {
            return super.parse(languageClass, code, context, argumentNames);
        }

        @Override
        protected void probeAST(RootNode rootNode) {
            // Normally null vm argument; can be reflectively set for testing
            Instrumenter instrumenter = super.getInstrumenter(testVM);
            if (instrumenter != null) {
                instrumenter.probeAST(rootNode);
            }
        }
    }

    static final AccessorInstrument ACCESSOR = new AccessorInstrument();

    // Normally null; set for testing where the Accessor hasn't been fully initialized
    private static Object testVM = null;

}
