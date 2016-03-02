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

import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.instrument.TagInstrument.AfterTagInstrument;
import com.oracle.truffle.api.instrument.TagInstrument.BeforeTagInstrument;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.CyclicAssumption;

/**
 * A <em>binding</em> between:
 * <ol>
 * <li>a <em>guest language program location</em> in an executing Truffle AST (corresponding to a
 * {@link SourceSection}), and</li>
 * <li>a dynamically managed collection of <em>attached</em> {@linkplain Instrument Instruments}
 * that receive event notifications on behalf of external clients.</li>
 * </ol>
 * <strong>Note</strong>:The relationship for {@link ProbeInstrument} must be with an AST
 * <em>location</em>, not a specific {@link Node}, because ASTs are routinely <em>cloned</em> at
 * runtime. An AST <em>location</em> is best represented as the {@link SourceSection} from which the
 * original AST Node was created.
 * <p>
 * Client-oriented documentation for the use of Probes is available online at <a
 * HREF="https://wiki.openjdk.java.net/display/Graal/Finding+Probes" >https://wiki.openjdk.java.
 * net/display/Graal/Finding+Probes</a>
 * <p>
 *
 * @see Instrumenter
 * @see ProbeInstrument
 * @see ASTProber
 * @see ProbeListener
 * @see SyntaxTag
 * @since 0.8 or earlier
 */
@SuppressWarnings("rawtypes")
public final class Probe {
    private final Class<? extends TruffleLanguage> language;

    private static final boolean TRACE = false;
    private static final String TRACE_PREFIX = "PROBE: ";
    private static final PrintStream OUT = System.out;

    private static void trace(String format, Object... args) {
        if (TRACE) {
            OUT.println(TRACE_PREFIX + String.format(format, args));
        }
    }

    private final Instrumenter instrumenter;
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
    @CompilationFinal private boolean isBeforeTagInstrumentActive = false;

    // Must invalidate whenever changed
    @CompilationFinal private boolean isAfterTagInstrumentActive = false;

    /**
     * Constructor for use only by {@link ProbeNode}.
     * <p>
     * <h4>Probe Implementation notes:</h4>
     * <p>
     * <ul>
     * <li>A Probe must be permanently associated with a <em>program location</em>, defined by a
     * particular {@link SourceSection}, even though:
     * <ul>
     * <li>that location is represented in an AST as a {@link Node}, which might be replaced through
     * optimizations such as specialization, and</li>
     * <li>Truffle may <em>clone</em> the AST so that the location is actually represented by
     * multiple Nodes in multiple ASTs.</li>
     * </ul>
     * </li>
     * <li>The effect of the binding is to intercept {@linkplain EventHandlerNode execution events}
     * arriving at the "probed" AST Node and notify each attached {@link ProbeInstrument} before
     * execution is allowed to proceed to the child and again after execution completes.</li>
     *
     * <li>The method {@link Instrumenter#probe(Node)} creates a Probe on an AST Node; redundant
     * calls return the same Probe.</li>
     *
     * <li>The "probing" of a Truffle AST must be done after the AST is complete (i.e. parent
     * pointers correctly assigned), but before any cloning or executions. This is done by applying
     * instances of {@link ASTProber} provided by each language implementation, combined with any
     * instances registered by tools via {@link Instrumenter#registerASTProber(ASTProber)}. Once
     * registered, these will be applied automatically to every newly created AST.</li>
     *
     * <li>The "probing" of an AST Node is implemented by insertion of a
     * {@link ProbeNode.WrapperNode} into the AST (as new parent of the Node being probed), together
     * with an associated {@link ProbeNode} that routes execution events at the probed Node to all
     * the {@linkplain ProbeInstrument Instruments} attached to the Probe's
     * <em>instrument chain</em>.</li>
     *
     * <li>When Truffle clones an AST, any attached WrapperNodes and ProbeNodes are cloned as well,
     * together with their attached instrument chains. Each Probe instance intercepts cloning events
     * and keeps track of all AST copies.</li>
     *
     * <li>All attached {@link InstrumentationNode}s effectively become part of the running program:
     * <ul>
     * <li>Good News: instrumentation code implicitly benefits from every kind of Truffle
     * optimization.</li>
     * <li>Bad News: instrumentation code must be implemented carefully to avoid interfering with
     * any Truffle optimizations.</li>
     * </ul>
     * </li>
     * </ul>
     */
    Probe(Instrumenter instrumenter, Class<? extends TruffleLanguage> l, ProbeNode probeNode, SourceSection sourceSection) {
        this.instrumenter = instrumenter;
        this.sourceSection = sourceSection;
        registerProbeNodeClone(probeNode);
        this.language = l;
    }

    /**
     * Adds a {@linkplain SyntaxTag tag} to the set of tags associated with this {@link Probe};
     * {@code no-op} if already in the set.
     * 
     * @since 0.8 or earlier
     */
    public void tagAs(SyntaxTag tag, Object tagValue) {
        assert tag != null;
        if (!tags.contains(tag)) {
            tags.add(tag);
            instrumenter.tagAdded(this, tag, tagValue);

            // Update the status of this Probe with respect to global TagInstruments
            boolean tagInstrumentsChanged = false;
            final BeforeTagInstrument beforeTagInstrument = instrumenter.getBeforeTagInstrument();
            if (beforeTagInstrument != null && tag == beforeTagInstrument.getTag()) {
                this.isBeforeTagInstrumentActive = true;
                tagInstrumentsChanged = true;
            }
            final AfterTagInstrument afterTagInstrument = instrumenter.getAfterTagInstrument();
            if (afterTagInstrument != null && tag == afterTagInstrument.getTag()) {
                this.isAfterTagInstrumentActive = true;
                tagInstrumentsChanged = true;
            }
            if (tagInstrumentsChanged) {
                invalidateProbeUnchanged();
            }
            if (TRACE) {
                trace("TAGGED as %s: %s", tag, getShortDescription());
            }
        }
    }

    /**
     * Is the <em>Probed node</em> tagged as belonging to a particular human-sensible category of
     * language constructs?
     * 
     * @since 0.8 or earlier
     */
    public boolean isTaggedAs(SyntaxTag tag) {
        assert tag != null;
        return tags.contains(tag);
    }

    /**
     * In which user-sensible categories has the <em>Probed node</em> been tagged (
     * <em>empty set</em> if none).
     * 
     * @since 0.8 or earlier
     */
    public Collection<SyntaxTag> getSyntaxTags() {
        return Collections.unmodifiableCollection(tags);
    }

    /**
     * Adds instrumentation at this Probe.
     *
     * @param instrument an instrument not yet attached to a probe
     * @throws IllegalStateException if the instrument has ever been attached before
     */
    void attach(ProbeInstrument instrument) throws IllegalStateException {
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
     * Gets the {@link SourceSection} associated with the <en>Probed AST node</em>, possibly
     * {@code null}.
     * 
     * @since 0.8 or earlier
     */
    public SourceSection getProbedSourceSection() {
        return sourceSection;
    }

    /** @since 0.8 or earlier */
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
     * @see ProbeInstrument#dispose()
     */
    void disposeInstrument(ProbeInstrument instrument) throws IllegalStateException {
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
     * Gets the currently active {@linkplain BeforeTagInstrument} at this Probe. Non{@code -null} if
     * the global {@linkplain BeforeTagInstrument} is set and if this Probe holds the
     * {@link SyntaxTag} specified in the instrument.
     */
    BeforeTagInstrument getBeforeTagInstrument() {
        checkProbeUnchanged();
        return isBeforeTagInstrumentActive ? instrumenter.getBeforeTagInstrument() : null;
    }

    /**
     * Gets the currently active {@linkplain AfterTagInstrument} at this Probe. Non{@code -null} if
     * the global {@linkplain BeforeTagInstrument} is set and if this Probe holds the
     * {@link SyntaxTag} specified in the instrument.
     */
    AfterTagInstrument getAfterTagInstrument() {
        checkProbeUnchanged();
        return isAfterTagInstrumentActive ? instrumenter.getAfterTagInstrument() : null;
    }

    Class<? extends TruffleLanguage> getLanguage() {
        return language;
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

    void notifyTagInstrumentsChanged() {
        final BeforeTagInstrument beforeTagInstrument = instrumenter.getBeforeTagInstrument();
        this.isBeforeTagInstrumentActive = beforeTagInstrument != null && this.isTaggedAs(beforeTagInstrument.getTag());
        final AfterTagInstrument afterTagInstrument = instrumenter.getAfterTagInstrument();
        this.isAfterTagInstrumentActive = afterTagInstrument != null && this.isTaggedAs(afterTagInstrument.getTag());
        invalidateProbeUnchanged();
    }

    String getTagsDescription() {
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

    Instrumenter getInstrumenter() {
        return instrumenter;
    }
}
