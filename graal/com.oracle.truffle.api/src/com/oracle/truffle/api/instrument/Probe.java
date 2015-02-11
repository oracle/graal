/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.*;
import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.utilities.*;

//TODO (mlvdv) migrate some of this to external documentation.
/**
 * A binding between a particular <em>location</em> in the Truffle AST representation of a running
 * Guest Language (GL) program (i.e. a {@link Node}) and a dynamically managed collection of
 * "attached" {@linkplain Instrument instrumentation} for use by external tools. The instrumentation
 * is intended to persist at the location, even if the specific node instance is
 * {@linkplain Node#replace(Node) replaced}.
 * <p>
 * The effect of a binding is to intercept {@linkplain TruffleEventListener execution events}
 * arriving at the node and notify each attached {@link Instrument} before execution is allowed to
 * proceed to the child.
 * <p>
 * A Probe is "inserted" into a GL node via a call to {@link Node#probe()}. No more than one Probe
 * can be inserted at a node.
 * <p>
 * The "probing" of a Truffle AST must be done after it is complete (i.e. with parent pointers
 * correctly assigned), but before any executions. This is done by creating an instance of
 * {@link ASTProber} and registering it via {@link #registerASTProber(ASTProber)}, after which it
 * will be automatically applied to newly created ASTs.
 * <p>
 * Each Probe may also have assigned to it any number of {@link SyntaxTag}s, for example identifying
 * a node as a {@linkplain StandardSyntaxTag#STATEMENT STATEMENT}. Tags can be queried by tools to
 * configure behavior relevant to each probed node.
 * <p>
 * Instrumentation is implemented by modifying ASTs, both by inserting nodes into each AST at probed
 * locations and by attaching additional nodes that implement dynamically attached instruments.
 * Attached instrumentation code become, in effect, part of the GL program, and is subject to the
 * same levels of optimization as other GL code. This implementation accounts properly for the fact
 * that Truffle frequently <em>clones</em> ASTs, along with any attached instrumentation nodes. A
 * {@link Probe}, along with attached {@link Instrument}s, represents a <em>logical</em> binding
 * with a source code location, producing event notifications that are (mostly) independent of which
 * AST clone is executing.
 *
 * @see Instrument
 * @see ASTProber
 * @see ProbeListener
 */
public final class Probe implements SyntaxTagged {

    private static final List<ASTProber> astProbers = new ArrayList<>();

    private static final List<ProbeListener> probeListeners = new ArrayList<>();

    /**
     * All Probes that have been created.
     */
    private static final List<WeakReference<Probe>> probes = new ArrayList<>();

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

    /**
     * Walks an AST, looking for the first node with an assigned {@link SourceSection} and returning
     * the {@link Source}.
     */
    private static Source findSource(Node node) {
        final FindSourceVisitor visitor = new FindSourceVisitor();
        node.accept(visitor);
        return visitor.source;
    }

    /**
     * The tag trap is a global setting; it only affects {@linkplain Probe probes} with the
     * {@linkplain SyntaxTag tag} specified .
     */
    private static SyntaxTagTrap globalTagTrap = null;

    /**
     * Enables instrumentation at selected nodes in all subsequently constructed ASTs.
     */
    public static void registerASTProber(ASTProber prober) {
        astProbers.add(prober);
    }

    public static void unregisterASTProber(ASTProber prober) {
        astProbers.remove(prober);
    }

    /**
     * Enables instrumentation in a newly created AST by applying all registered instances of
     * {@link ASTProber}.
     */
    public static void applyASTProbers(Node node) {

        final Source source = findSource(node);

        for (ProbeListener listener : probeListeners) {
            listener.startASTProbing(source);
        }
        for (ASTProber prober : astProbers) {
            prober.probeAST(node);
        }
        for (ProbeListener listener : probeListeners) {
            listener.endASTProbing(source);
        }
    }

    /**
     * Adds a {@link ProbeListener} to receive events.
     */
    public static void addProbeListener(ProbeListener listener) {
        assert listener != null;
        probeListeners.add(listener);
    }

    /**
     * Removes a {@link ProbeListener}. Ignored if listener not found.
     */
    public static void removeProbeListener(ProbeListener listener) {
        probeListeners.remove(listener);
    }

    /**
     * Returns all {@link Probe}s holding a particular {@link SyntaxTag}, or the whole collection if
     * the specified tag is {@code null}.
     *
     * @return A collection of probes containing the given tag.
     */
    public static Collection<Probe> findProbesTaggedAs(SyntaxTag tag) {
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
     * Sets the current "tag trap". This causes a callback to be triggered whenever execution
     * reaches a {@link Probe} (either existing or subsequently created) with the specified tag.
     * There can only be one tag trap set at a time.
     *
     * @param newTagTrap The {@link SyntaxTagTrap} to set.
     * @throws IllegalStateException if a trap is currently set.
     */
    public static void setTagTrap(SyntaxTagTrap newTagTrap) throws IllegalStateException {
        assert newTagTrap != null;
        if (globalTagTrap != null) {
            throw new IllegalStateException("trap already set");
        }
        globalTagTrap = newTagTrap;

        final SyntaxTag newTag = newTagTrap.getTag();
        for (WeakReference<Probe> ref : probes) {
            final Probe probe = ref.get();
            if (probe != null && probe.tags.contains(newTag)) {
                probe.trapActive = true;
                probe.probeStateUnchanged.invalidate();
            }
        }
    }

    /**
     * Clears the current {@link SyntaxTagTrap}.
     *
     * @throws IllegalStateException if no trap is currently set.
     */
    public static void clearTagTrap() {
        if (globalTagTrap == null) {
            throw new IllegalStateException("no trap set");
        }
        globalTagTrap = null;

        for (WeakReference<Probe> ref : probes) {
            final Probe probe = ref.get();
            if (probe != null && probe.trapActive) {
                probe.trapActive = false;
                probe.probeStateUnchanged.invalidate();
            }
        }
    }

    private final SourceSection sourceSection;
    private final ArrayList<SyntaxTag> tags = new ArrayList<>();
    private final List<WeakReference<ProbeNode>> probeNodeClones = new ArrayList<>();
    private final CyclicAssumption probeStateUnchanged = new CyclicAssumption("Probe state unchanged");

    /**
     * {@code true} iff the global trap is set and this probe has the matching tag.
     */
    private boolean trapActive = false;

    /**
     * Intended for use only by {@link ProbeNode}.
     */
    Probe(ProbeNode probeNode, SourceSection sourceSection) {
        this.sourceSection = sourceSection;
        probes.add(new WeakReference<>(this));
        registerProbeNodeClone(probeNode);
        for (ProbeListener listener : probeListeners) {
            listener.newProbeInserted(this);
        }
    }

    public boolean isTaggedAs(SyntaxTag tag) {
        assert tag != null;
        return tags.contains(tag);
    }

    public Collection<SyntaxTag> getSyntaxTags() {
        return Collections.unmodifiableCollection(tags);
    }

    /**
     * Adds a {@linkplain SyntaxTag tag} to the set of tags associated with this {@link Probe};
     * {@code no-op} if already in the set.
     */
    public void tagAs(SyntaxTag tag, Object tagValue) {
        assert tag != null;
        if (!tags.contains(tag)) {
            tags.add(tag);
            for (ProbeListener listener : probeListeners) {
                listener.probeTaggedAs(this, tag, tagValue);
            }
            if (globalTagTrap != null && tag == globalTagTrap.getTag()) {
                this.trapActive = true;
            }
            probeStateUnchanged.invalidate();
        }
    }

    /**
     * Adds instrumentation at this Probe.
     *
     * @param instrument an instrument not yet attached to a probe
     * @throws IllegalStateException if the instrument has ever been attached before
     */
    public void attach(Instrument instrument) throws IllegalStateException {
        if (instrument.isDisposed()) {
            throw new IllegalStateException("Attempt to attach disposed instrument");
        }
        if (instrument.getProbe() != null) {
            throw new IllegalStateException("Attampt to attach an already attached instrument");
        }
        instrument.setAttachedTo(this);
        for (WeakReference<ProbeNode> ref : probeNodeClones) {
            final ProbeNode probeNode = ref.get();
            if (probeNode != null) {
                probeNode.addInstrument(instrument);
            }
        }
        probeStateUnchanged.invalidate();
    }

    /**
     * Gets the {@link SourceSection} associated with the Guest Language AST node being
     * instrumented, possibly {@code null}.
     */
    public SourceSection getProbedSourceSection() {
        return sourceSection;
    }

    public String getShortDescription() {
        final String location = sourceSection == null ? "<unknown>" : sourceSection.getShortDescription();
        return "Probe@" + location + getTagsDescription();
    }

    /**
     * Receives notification that a new clone of the instrument chain associated with this
     * {@link Probe} has been created as a side-effect of AST cloning.
     */
    void registerProbeNodeClone(ProbeNode probeNode) {
        probeNodeClones.add(new WeakReference<>(probeNode));
    }

    /**
     * Gets the currently active {@linkplain SyntaxTagTrap tagTrap}; {@code null} if not set.
     */
    SyntaxTagTrap getTrap() {
        return trapActive ? globalTagTrap : null;
    }

    /**
     * Gets the {@link Assumption} that the instrumentation-related state of this {@link Probe} has
     * not changed since this method was last called.
     */
    Assumption getUnchangedAssumption() {
        return probeStateUnchanged.getAssumption();
    }

    /**
     * Internal method for removing and rendering inert a specific instrument previously attached at
     * this Probe.
     *
     * @param instrument an instrument already attached
     * @throws IllegalStateException if instrument not attached at this Probe
     * @see Instrument#dispose()
     */
    void disposeInstrument(Instrument instrument) throws IllegalStateException {
        for (WeakReference<ProbeNode> ref : probeNodeClones) {
            final ProbeNode probeNode = ref.get();
            if (probeNode != null) {
                probeNode.removeInstrument(instrument);
            }
        }
        probeStateUnchanged.invalidate();
    }

    private String getTagsDescription() {
        final StringBuilder sb = new StringBuilder();
        sb.append("[");
        String prefix = "";
        for (SyntaxTag tag : tags) {
            sb.append(prefix);
            prefix = ",";
            sb.append(tag.toString());
        }
        sb.append("]");
        return sb.toString();
    }
}
