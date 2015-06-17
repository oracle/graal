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

import java.io.*;
import java.lang.ref.*;
import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.instrument.InstrumentationNode.TruffleEvents;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.utilities.*;

//TODO (mlvdv) these statics should not be global.  Move them to some kind of context.

/**
 * A <em>binding</em> between:
 * <ol>
 * <li>A program location in an executing Truffle AST (corresponding to a {@link SourceSection}),
 * and</li>
 * <li>A dynamically managed collection of "attached" {@linkplain Instrument Instruments} that
 * receive event notifications on behalf of external clients.</li>
 * </ol>
 * <p>
 * Client-oriented documentation for the use of Probes is available online at <a
 * HREF="https://wiki.openjdk.java.net/display/Graal/Finding+Probes"
 * >https://wiki.openjdk.java.net/display/Graal/Finding+Probes</a>
 * <p>
 * <h4>Implementation notes:</h4>
 * <p>
 * <ul>
 * <li>A Probe must be permanently associated with a <em>program location</em>, defined by a
 * particular {@link SourceSection}, even though:
 * <ul>
 * <li>that location is represented in an AST as a {@link Node}, which might be replaced through
 * optimizations such as specialization, and</li>
 * <li>Truffle may <em>clone</em> the AST so that the location is actually represented by multiple
 * Nodes in multiple ASTs.</li>
 * </ul>
 * </li>
 *
 * <li>The effect of the binding is to intercept {@linkplain TruffleEvents execution events}
 * arriving at the "probed" AST Node and notify each attached {@link Instrument} before execution is
 * allowed to proceed to the child and again after execution completes.</li>
 *
 * <li>The method {@link Node#probe()} creates a Probe on an AST Node; redundant calls return the
 * same Probe.</li>
 *
 * <li>The "probing" of a Truffle AST must be done after the AST is complete (i.e. parent pointers
 * correctly assigned), but before any cloning or executions. This is done by creating an instance
 * of {@link ASTProber} and registering it via {@link #registerASTProber(ASTProber)}. Once
 * registered, it will be applied automatically to every newly created AST.</li>
 *
 * <li>The "probing" of an AST Node is implemented by insertion of a {@link ProbeNode.WrapperNode}
 * into the AST (as new parent of the Node being probed), together with an associated
 * {@link ProbeNode} that routes execution events at the probed Node to all the
 * {@linkplain Instrument Instruments} attached to the Probe's <em>instrument chain</em>.</li>
 *
 * <li>When Truffle clones an AST, any attached WrapperNodes and ProbeNodes are cloned as well,
 * together with their attached instrument chains. Each Probe instance intercepts cloning events and
 * keeps track of all AST copies.</li>
 *
 * <li>All attached {@link InstrumentationNode}s effectively become part of the running program:
 * <ul>
 * <li>Good News: instrumentation code implicitly benefits from every kind of Truffle optimization.</li>
 * <li>Bad News: instrumentation code must be implemented carefully to avoid interfering with any
 * Truffle optimizations.</li>
 * </ul>
 * </li>
 *
 * </ul>
 *
 * @see Instrument
 * @see ASTProber
 * @see ProbeListener
 * @see SyntaxTag
 */
public final class Probe {

    private static final boolean TRACE = false;
    private static final String TRACE_PREFIX = "PROBE: ";
    private static final PrintStream OUT = System.out;

    private static void trace(String format, Object... args) {
        if (TRACE) {
            OUT.println(TRACE_PREFIX + String.format(format, args));
        }
    }

    private static final List<ASTProber> astProbers = new ArrayList<>();

    private static final List<ProbeListener> probeListeners = new ArrayList<>();

    /**
     * All Probes that have been created.
     */
    private static final List<WeakReference<Probe>> probes = new ArrayList<>();

    /**
     * A global trap that triggers notification just before executing any Node that is Probed with a
     * matching tag.
     */
    @CompilationFinal private static SyntaxTagTrap beforeTagTrap = null;

    /**
     * A global trap that triggers notification just after executing any Node that is Probed with a
     * matching tag.
     */
    @CompilationFinal private static SyntaxTagTrap afterTagTrap = null;

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
            prober.probeAST(node);
        }
        for (ProbeListener listener : probeListeners) {
            listener.endASTProbing(source);
        }
        trace("FINISHED %s", name);
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
     * Returns all {@link Probe}s holding a particular {@link SyntaxTag}, or the whole collection of
     * probes if the specified tag is {@code null}.
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
    public static void setBeforeTagTrap(SyntaxTagTrap newBeforeTagTrap) {
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
    public static void setAfterTagTrap(SyntaxTagTrap newAfterTagTrap) {
        afterTagTrap = newAfterTagTrap;
        for (WeakReference<Probe> ref : probes) {
            final Probe probe = ref.get();
            if (probe != null) {
                probe.notifyTrapsChanged();
            }
        }
    }

    private final SourceSection sourceSection;
    private final ArrayList<SyntaxTag> tags = new ArrayList<>();
    private final List<WeakReference<ProbeNode>> probeNodeClones = new ArrayList<>();

    /*
     * Invalidated whenever something changes in the Probe and its Instrument chain, so need deopt
     */
    private final CyclicAssumption probeStateUnchangedCyclic = new CyclicAssumption("Probe state unchanged");

    /*
     * The assumption that nothing had changed in this probe, the last time anybody checked (when
     * there may have been a deopt). Every time a check fails, gets replaced by a new unchanged
     * assumption.
     */
    @CompilationFinal private Assumption probeStateUnchangedAssumption = probeStateUnchangedCyclic.getAssumption();

    // Must invalidate whenever changed
    @CompilationFinal private boolean isBeforeTrapActive = false;

    // Must invalidate whenever changed
    @CompilationFinal private boolean isAfterTrapActive = false;

    /**
     * Intended for use only by {@link ProbeNode}.
     */
    Probe(ProbeNode probeNode, SourceSection sourceSection) {
        this.sourceSection = sourceSection;
        probes.add(new WeakReference<>(this));
        registerProbeNodeClone(probeNode);
        if (TRACE) {
            final String location = this.sourceSection == null ? "<unknown>" : sourceSection.getShortDescription();
            trace("ADDED %s %s %s", "Probe@", location, getTagsDescription());
        }
        for (ProbeListener listener : probeListeners) {
            listener.newProbeInserted(this);
        }
    }

    /**
     * Is this node tagged as belonging to a particular human-sensible category of language
     * constructs?
     */
    public boolean isTaggedAs(SyntaxTag tag) {
        assert tag != null;
        return tags.contains(tag);
    }

    /**
     * In which user-sensible categories has this node been tagged (<em>empty set</em> if none).
     */
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

            // Update the status of this Probe with respect to global tag traps
            boolean tagTrapsChanged = false;
            if (beforeTagTrap != null && tag == beforeTagTrap.getTag()) {
                this.isBeforeTrapActive = true;
                tagTrapsChanged = true;
            }
            if (afterTagTrap != null && tag == afterTagTrap.getTag()) {
                this.isAfterTrapActive = true;
                tagTrapsChanged = true;
            }
            if (tagTrapsChanged) {
                invalidateProbeUnchanged();
            }
            if (TRACE) {
                trace("TAGGED as %s: %s", tag, getShortDescription());
            }
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
        invalidateProbeUnchanged();
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
        invalidateProbeUnchanged();
    }

    /**
     * Receives notification that a new clone of the instrument chain associated with this
     * {@link Probe} has been created as a side-effect of AST cloning.
     */
    void registerProbeNodeClone(ProbeNode probeNode) {
        probeNodeClones.add(new WeakReference<>(probeNode));
    }

    /**
     * Gets the currently active <strong><em>before</em></strong> {@linkplain SyntaxTagTrap Tag
     * Trap} at this Probe. Non{@code -null} if the global
     * {@linkplain Probe#setBeforeTagTrap(SyntaxTagTrap) Before Tag Trap} is set and if this Probe
     * holds the {@link SyntaxTag} specified in the trap.
     */
    SyntaxTagTrap getBeforeTrap() {
        checkProbeUnchanged();
        return isBeforeTrapActive ? beforeTagTrap : null;
    }

    /**
     * Gets the currently active <strong><em>after</em></strong> {@linkplain SyntaxTagTrap Tag Trap}
     * at this Probe. Non{@code -null} if the global
     * {@linkplain Probe#setAfterTagTrap(SyntaxTagTrap) After Tag Trap} is set and if this Probe
     * holds the {@link SyntaxTag} specified in the trap.
     */
    SyntaxTagTrap getAfterTrap() {
        checkProbeUnchanged();
        return isAfterTrapActive ? afterTagTrap : null;
    }

    /**
     * To be called wherever in the Probe/Instrument chain there are dependencies on the probe
     * state's @CompilatonFinal fields.
     */
    void checkProbeUnchanged() {
        try {
            probeStateUnchangedAssumption.check();
        } catch (InvalidAssumptionException ex) {
            // Failure creates an implicit deoptimization
            // Get the assumption associated with the new probe state
            this.probeStateUnchangedAssumption = probeStateUnchangedCyclic.getAssumption();
        }
    }

    void invalidateProbeUnchanged() {
        probeStateUnchangedCyclic.invalidate();
    }

    private void notifyTrapsChanged() {
        this.isBeforeTrapActive = beforeTagTrap != null && this.isTaggedAs(beforeTagTrap.getTag());
        this.isAfterTrapActive = afterTagTrap != null && this.isTaggedAs(afterTagTrap.getTag());
        invalidateProbeUnchanged();
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
