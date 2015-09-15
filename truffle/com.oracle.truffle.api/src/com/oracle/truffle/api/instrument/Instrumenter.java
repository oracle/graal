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

import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.instrument.ProbeNode.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Access to instrumentation services in an instance of {@link TruffleVM}.
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

    /**
     * Walks an AST, looking for the first node with an assigned {@link SourceSection} and returning
     * the {@link Source}.
     */
    private static Source findSource(Node node) {
        final FindSourceVisitor visitor = new FindSourceVisitor();
        node.accept(visitor);
        return visitor.source;
    }

    private final List<ASTProber> astProbers = new ArrayList<>();

    private final List<ProbeListener> probeListeners = new ArrayList<>();

    /**
     * All Probes that have been created.
     */
    private final List<WeakReference<Probe>> probes = new ArrayList<>();

    /**
     * A global trap that triggers notification just before executing any Node that is Probed with a
     * matching tag.
     */
    @CompilationFinal private SyntaxTagTrap beforeTagTrap = null;

    /**
     * A global trap that triggers notification just after executing any Node that is Probed with a
     * matching tag.
     */
    @CompilationFinal private SyntaxTagTrap afterTagTrap = null;

    private static final class FindSourceVisitor implements NodeVisitor {

        Source source = null;

        public boolean visit(Node node) {
            final SourceSection sourceSection = node.getSourceSection();
            if (sourceSection != null) {
                source = sourceSection.getSource();
                return false;
            }
            return true;
        }
    }

    Instrumenter() {
    }

    /**
     * Enables {@linkplain Instrument instrumentation} of a node, where the node is presumed to be
     * part of a well-formed Truffle AST that is not being executed. If this node has not already
     * been probed, modifies the AST by inserting a {@linkplain WrapperNode wrapper node} between
     * the node and its parent; the wrapper node must be provided by implementations of
     * {@link Node#createWrapperNode()}. No more than one {@link Probe} may be associated with a
     * node, so a {@linkplain WrapperNode wrapper} may not wrap another {@linkplain WrapperNode
     * wrapper}.
     *
     * @return a (possibly newly created) {@link Probe} associated with this node.
     * @throws ProbeException (unchecked) when a probe cannot be created, leaving the AST unchanged
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

        if (!(node.isInstrumentable())) {
            throw new ProbeException(ProbeFailure.Reason.NOT_INSTRUMENTABLE, parent, node, null);
        }

        // Create a new wrapper/probe with this node as its child.
        final WrapperNode wrapper = node.createWrapperNode();

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
        wrapper.insertProbe(probeNode);
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

    // TODO (mlvdv) generalize to permit multiple "before traps" without a performance hit?
    /**
     * Sets the current "<em>before</em> tag trap"; there can be no more than one in effect.
     * <ul>
     * <li>The before-trap triggers a callback just <strong><em>before</em></strong> execution
     * reaches <strong><em>any</em></strong> {@link Probe} (either existing or subsequently created)
     * with the specified {@link SyntaxTag}.</li>
     * <li>Setting the before-trap to {@code null} clears an existing before-trap.</li>
     * <li>Setting a non{@code -null} before-trap when one is already set clears the previously set
     * before-trap.</li>
     * </ul>
     *
     * @param newBeforeTagTrap The new "before" {@link SyntaxTagTrap} to set.
     */
    public void setBeforeTagTrap(SyntaxTagTrap newBeforeTagTrap) {
        beforeTagTrap = newBeforeTagTrap;
        for (WeakReference<Probe> ref : probes) {
            final Probe probe = ref.get();
            if (probe != null) {
                probe.notifyTrapsChanged();
            }
        }
    }

    // TODO (mlvdv) generalize to permit multiple "after traps" without a performance hit?
    /**
     * Sets the current "<em>after</em> tag trap"; there can be no more than one in effect.
     * <ul>
     * <li>The after-trap triggers a callback just <strong><em>after</em></strong> execution leaves
     * <strong><em>any</em></strong> {@link Probe} (either existing or subsequently created) with
     * the specified {@link SyntaxTag}.</li>
     * <li>Setting the after-trap to {@code null} clears an existing after-trap.</li>
     * <li>Setting a non{@code -null} after-trap when one is already set clears the previously set
     * after-trap.</li>
     * </ul>
     *
     * @param newAfterTagTrap The new "after" {@link SyntaxTagTrap} to set.
     */
    public void setAfterTagTrap(SyntaxTagTrap newAfterTagTrap) {
        afterTagTrap = newAfterTagTrap;
        for (WeakReference<Probe> ref : probes) {
            final Probe probe = ref.get();
            if (probe != null) {
                probe.notifyTrapsChanged();
            }
        }
    }

    /**
     * Enables instrumentation at selected nodes in all subsequently constructed ASTs.
     */
    public void registerASTProber(ASTProber prober) {
        astProbers.add(prober);
    }

    public void unregisterASTProber(ASTProber prober) {
        astProbers.remove(prober);
    }

    @SuppressWarnings("unused")
    void executionStarted(Source s) {
    }

    void executionEnded() {
    }

    void tagAdded(Probe probe, SyntaxTag tag, Object tagValue) {
        for (ProbeListener listener : probeListeners) {
            listener.probeTaggedAs(probe, tag, tagValue);
        }
    }

    SyntaxTagTrap getBeforeTagTrap() {
        return beforeTagTrap;
    }

    SyntaxTagTrap getAfterTagTrap() {
        return afterTagTrap;
    }

    /**
     * Enables instrumentation in a newly created AST by applying all registered instances of
     * {@link ASTProber}.
     */
    private void applyInstrumentation(Node node) {

        String name = "<?>";
        final Source source = findSource(node);
        if (source != null) {
            name = source.getShortName();
        } else {
            final SourceSection sourceSection = node.getEncapsulatingSourceSection();
            if (sourceSection != null) {
                name = sourceSection.getShortDescription();
            }
        }
        trace("START %s", name);
        for (ProbeListener listener : probeListeners) {
            listener.startASTProbing(source);
        }
        for (ASTProber prober : astProbers) {
            prober.probeAST(this, node);  // TODO (mlvdv)
        }
        for (ProbeListener listener : probeListeners) {
            listener.endASTProbing(source);
        }
        trace("FINISHED %s", name);
    }

    static final class AccessorInstrument extends Accessor {

        @Override
        protected Instrumenter createInstrumenter() {
            return new Instrumenter();
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

        @Override
        protected void applyInstrumentation(Node node) {
            super.getInstrumenter(null).applyInstrumentation(node);
        }
    }

    static final AccessorInstrument ACCESSOR = new AccessorInstrument();

}
