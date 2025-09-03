/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import java.util.EnumSet;
import java.util.Formatter;
import java.util.Locale;
import java.util.Objects;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Describes {@link StructuredGraph} state with respect to compilation.
 *
 * This state is defined by fields which represent
 * <ul>
 * <li>The progress made in the compilation (e.g. which stages have been reached (see
 * {@link StageFlag}), which verification is performed by {@link FrameState}s (see
 * {@link FrameStateVerification}))</li>
 * <li>The properties of the intermediate representation (e.g. if new {@link DeoptimizingNode}s can
 * be introduced (see {@link GuardsStage}), if the graph contains nodes that require a stage to be
 * applied on the graph (see {@link #getFutureRequiredStages()}))</li>
 * </ul>
 */
public final class GraphState {
    /**
     * These sets of {@link StageFlag}s represent the necessary stages that must be applied to a
     * {@link StructuredGraph} for a complete compilation.
     */
    private static final EnumSet<StageFlag> HIGH_TIER_MANDATORY_STAGES = EnumSet.of(StageFlag.LOOP_OVERFLOWS_CHECKED,
                    StageFlag.HIGH_TIER_LOWERING);
    private static final EnumSet<StageFlag> MID_TIER_MANDATORY_STAGES = EnumSet.of(
                    StageFlag.VALUE_PROXY_REMOVAL,
                    StageFlag.SAFEPOINTS_INSERTION,
                    StageFlag.GUARD_LOWERING,
                    StageFlag.MID_TIER_LOWERING,
                    StageFlag.FSA,
                    StageFlag.BARRIER_ADDITION);
    private static final EnumSet<StageFlag> LOW_TIER_MANDATORY_STAGES = EnumSet.of(
                    StageFlag.LOW_TIER_LOWERING,
                    StageFlag.EXPAND_LOGIC,
                    StageFlag.ADDRESS_LOWERING,
                    StageFlag.REMOVE_OPAQUE_VALUES,
                    StageFlag.FINAL_SCHEDULE);
    private static final EnumSet<StageFlag> ENTERPRISE_MID_TIER_MANDATORY_STAGES = EnumSet.of(
                    StageFlag.OPTIMISTIC_ALIASING,
                    StageFlag.GUARD_LOWERING,
                    StageFlag.VALUE_PROXY_REMOVAL,
                    StageFlag.SAFEPOINTS_INSERTION,
                    StageFlag.MID_TIER_LOWERING,
                    StageFlag.FSA,
                    StageFlag.NODE_VECTORIZATION,
                    StageFlag.BARRIER_ADDITION);

    /**
     * This set of {@link StageFlag}s represents the stages a {@link StructuredGraph} initially
     * requires to correctly pass all the other stages of the compilation. (See
     * {@link #getFutureRequiredStages()})
     */
    public static final EnumSet<StageFlag> INITIAL_REQUIRED_STAGES = EnumSet.of(StageFlag.CANONICALIZATION);

    /**
     * Indicates a stage is in progress.
     */
    private StageFlag currentStage;

    /**
     * Flag to indicate {@link #forceDisableFrameStateVerification()} was called.
     */
    private boolean disabledFrameStateVerification;

    /**
     * Represents the status of {@linkplain FrameState} verification of
     * {@linkplain AbstractStateSplit} state after.
     */
    private FrameStateVerification frameStateVerification;

    /**
     * Records the stages required by this graph. For example, if a stage introduces nodes that need
     * to be lowered in the graph, the graph will require a lowering stage to be in a correct state
     * after the compilation. After the lowering has been executed, the requirement will be
     * fulfilled. {@link GraphState#futureRequiredStages} should be empty after the compilation.
     */
    private EnumSet<StageFlag> futureRequiredStages;

    /**
     * Represents the state and properties of {@link DeoptimizingNode}s and {@link FrameState}s in
     * the graph.
     */
    private GuardsStage guardsStage;

    /**
     * Records which stages have been applied to the graph.
     */
    private EnumSet<StageFlag> stageFlags;

    /**
     * Contains the {@link SpeculationLog} used to perform speculative operations on this graph.
     */
    private final SpeculationLog speculationLog;

    /**
     * Creates a {@link GraphState} with the given fields.
     *
     * @param isSubstitution determines this {@linkplain #getFrameStateVerification() frame state
     *            verification}. {@link FrameStateVerification#NONE} is used if it is {@code true},
     *            otherwise it is {@link FrameStateVerification#ALL}. If it is {@code true},
     *            {@link #isFrameStateVerificationDisabled()} will be {@code true}.
     */
    public GraphState(StageFlag currentStage,
                    boolean disabledFrameStateVerification,
                    boolean isSubstitution,
                    EnumSet<StageFlag> futureRequiredStages,
                    GuardsStage guardsStage,
                    SpeculationLog speculationLog,
                    EnumSet<StageFlag> stageFlags) {
        this(currentStage,
                        disabledFrameStateVerification || isSubstitution,
                        isSubstitution ? FrameStateVerification.NONE : FrameStateVerification.ALL,
                        futureRequiredStages,
                        guardsStage,
                        speculationLog,
                        stageFlags);
    }

    /**
     * Creates a {@link GraphState} with the given fields.
     *
     * @param guardsStage the {@link GuardsStage} of this graph state,
     *            {@link GuardsStage#FLOATING_GUARDS} if it is {@code null}.
     */
    public GraphState(StageFlag currentStage,
                    boolean disabledFrameStateVerification,
                    FrameStateVerification frameStateVerification,
                    EnumSet<StageFlag> futureRequiredStages,
                    GuardsStage guardsStage,
                    SpeculationLog speculationLog,
                    EnumSet<StageFlag> stageFlags) {
        this.currentStage = currentStage;
        this.disabledFrameStateVerification = disabledFrameStateVerification;
        this.frameStateVerification = frameStateVerification;
        this.futureRequiredStages = futureRequiredStages == null ? EnumSet.noneOf(StageFlag.class) : futureRequiredStages;
        this.guardsStage = guardsStage == null ? GuardsStage.FLOATING_GUARDS : guardsStage;
        this.speculationLog = speculationLog;
        this.stageFlags = stageFlags == null ? EnumSet.noneOf(StageFlag.class) : stageFlags;
    }

    /**
     * Creates a {@link GraphState} with {@linkplain #getGuardsStage() guards stage} set to
     * {@link GuardsStage#FLOATING_GUARDS}, empty {@link EnumSet} for {@linkplain #getStageFlags()
     * stage flags} and {@linkplain #getFutureRequiredStages() future required stages},
     * {@linkplain #getFrameStateVerification() frame state verification} set to
     * {@link FrameStateVerification#ALL} and {@code null} for the other fields.
     */
    public static GraphState defaultGraphState() {
        return new GraphState(null, false, false, null, null, null, null);
    }

    /**
     * Creates a copy of this graph state. The copy's {@linkplain #getStageFlags() stage flags} and
     * {@linkplain #getFutureRequiredStages() future required stages} are deep copy of this graph
     * state's respective fields.
     */
    public GraphState copy() {
        return new GraphState(this.currentStage,
                        disabledFrameStateVerification,
                        this.frameStateVerification,
                        EnumSet.copyOf(this.futureRequiredStages),
                        this.guardsStage,
                        this.speculationLog,
                        EnumSet.copyOf(this.stageFlags));
    }

    /**
     * Creates a copy of this graph state with the given {@linkplain #getSpeculationLog()
     * speculation log}. The copy's {@linkplain #getStageFlags() stage flags} and
     * {@linkplain #getFutureRequiredStages() future required stages} are deep copy of this graph
     * state's respective fields.
     *
     * @param isSubstitution determines the copy's {@linkplain #getFrameStateVerification() frame
     *            state verification}. (See
     *            {@link #GraphState(StageFlag, boolean, boolean, EnumSet, GuardsStage, SpeculationLog, EnumSet)})
     */
    public GraphState copyWith(boolean isSubstitution, SpeculationLog speculationLogForCopy) {
        return new GraphState(this.currentStage,
                        disabledFrameStateVerification,
                        isSubstitution,
                        EnumSet.copyOf(this.futureRequiredStages),
                        this.guardsStage,
                        speculationLogForCopy,
                        EnumSet.copyOf(this.stageFlags));
    }

    @Override
    public String toString() {
        return toString("");
    }

    /**
     * Creates a {@link String} with this {@linkplain #getGuardsStage() guards stage},
     * {@linkplain #getStageFlags() stage flags}, {@linkplain #getFrameStateVerification() frame
     * state verification} and {@linkplain #getFutureRequiredStages() future required stages}.
     *
     * @param prefix the string inserted at the beginning of each line of the resulting string.
     */
    public String toString(String prefix) {
        Formatter formatter = new Formatter();
        formatter.format("%sGraphState:%n", prefix);
        formatter.format("%s\tGuards stage: %s%n", prefix, guardsStage.toString());
        formatter.format("%s\tStage flags:%n", prefix);
        for (StageFlag flag : stageFlags) {
            formatter.format("%s\t\t%s%n", prefix, flag.toString());
        }
        formatter.format("%s\tFrame state verification: %s%n", prefix, frameStateVerification.toString());
        formatter.format("%s\tFuture required stages: %s%n", prefix, futureRequiredStages.toString());
        return formatter.toString();
    }

    /**
     * Creates a {@link String} representing the differences between this
     * {@linkplain #getGuardsStage() guards stage}, {@linkplain #getStageFlags() stage flags},
     * {@linkplain #getFrameStateVerification() frame state verification} and
     * {@linkplain #getFutureRequiredStages() future required stages} and {@code previous}'s
     * respective fields. If {@code this} {@link #equals} {@code previous}, an empty string is
     * returned.
     */
    public String updateFromPreviousToString(GraphState previous) {
        if (this.equals(previous)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        builder.append(valueStringAsDiff(previous.guardsStage, this.guardsStage, "Guards stage: ", ", "));
        builder.append(newFlagsToString(previous.stageFlags, this.stageFlags, "+", "Stage flags: "));
        builder.append(valueStringAsDiff(previous.frameStateVerification, this.frameStateVerification, "Frame state verification: ", ", "));
        builder.append(newFlagsToString(previous.futureRequiredStages, this.futureRequiredStages, "+", "Future required stages: "));
        builder.append(newFlagsToString(this.futureRequiredStages, previous.futureRequiredStages, "-", ""));
        if (builder.length() > 1) {
            builder.setLength(builder.length() - 2);
        }
        builder.append('}');
        return builder.toString();
    }

    /**
     * @return the {@link String} representing the difference between {@code oldValue} and
     *         {@code newValue}, surrounded by {@code prefix} and {@code suffix}. If both values are
     *         equal, returns an empty string.
     */
    private static <T> String valueStringAsDiff(T oldValue, T newValue, String prefix, String suffix) {
        if (oldValue == newValue) {
            return "";
        }
        return String.format("%s%s -> %s%s", prefix, oldValue, newValue, suffix);
    }

    /**
     * @return a {@link String} representing the {@link StageFlag}s that differ between
     *         {@code oldSet} and {@code newSet}. If both sets are equal, returns an empty string.
     */
    private static String newFlagsToString(EnumSet<StageFlag> oldSet, EnumSet<StageFlag> newSet, String flagPrefix, String prefix) {
        Formatter formatter = new Formatter();
        EnumSet<StageFlag> newFlags = newSet.clone();
        newFlags.removeAll(oldSet);
        if (!newFlags.isEmpty()) {
            formatter.format(prefix);
            for (StageFlag flag : newFlags) {
                formatter.format("%s%s, ", flagPrefix, flag.toString());
            }
        }
        return formatter.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentStage, disabledFrameStateVerification, frameStateVerification, futureRequiredStages, guardsStage, speculationLog, stageFlags);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof GraphState)) {
            return false;
        }
        GraphState graphState = (GraphState) obj;
        return this.currentStage == graphState.currentStage &&
                        this.disabledFrameStateVerification == graphState.disabledFrameStateVerification &&
                        this.frameStateVerification == graphState.frameStateVerification &&
                        this.futureRequiredStages.equals(graphState.futureRequiredStages) &&
                        this.guardsStage == graphState.guardsStage &&
                        Objects.equals(this.speculationLog, graphState.speculationLog) &&
                        this.stageFlags.equals(graphState.stageFlags);
    }

    /**
     * @return the {@link SpeculationLog} used to perform speculative operations on this graph.
     */
    public SpeculationLog getSpeculationLog() {
        return speculationLog;
    }

    /**
     * Determines if {@linkplain #getFrameStateVerification() frame state verification} has been
     * forcefully disabled.
     *
     * @return {@code true} if {@link #forceDisableFrameStateVerification()} or
     *         {@link StructuredGraph#clearAllStateAfterForTestingOnly()} has been called or if this
     *         graph was build as a substitution (see
     *         {@link #GraphState(StageFlag, boolean, boolean, EnumSet, GuardsStage, SpeculationLog, EnumSet)}).
     */
    public boolean isFrameStateVerificationDisabled() {
        return disabledFrameStateVerification;
    }

    /**
     * Different node types verified during {@linkplain GraphState.FrameStateVerification}. See
     * {@linkplain GraphState.FrameStateVerification} for details.
     */
    public enum FrameStateVerificationFeature {
        STATE_SPLITS,
        MERGES,
        LOOP_BEGINS,
        LOOP_EXITS
    }

    /**
     * The different stages of the compilation of a {@link Graph} regarding the status of
     * {@linkplain FrameState} verification of {@linkplain AbstractStateSplit} state after.
     * Verification starts with the mode {@linkplain FrameStateVerification#ALL}, i.e., all state
     * splits with side-effects, merges and loop exits need a proper state after. The verification
     * mode progresses monotonously until the {@linkplain FrameStateVerification#NONE} mode is
     * reached. From there on, no further {@linkplain AbstractStateSplit#stateAfter} verification
     * happens.
     */
    public enum FrameStateVerification {
        /**
         * Verify all {@linkplain AbstractStateSplit} nodes that return {@code true} for
         * {@linkplain AbstractStateSplit#hasSideEffect()} have a
         * {@linkplain AbstractStateSplit#stateAfter} assigned. Additionally, verify
         * {@linkplain LoopExitNode} and {@linkplain AbstractMergeNode} have a valid
         * {@linkplain AbstractStateSplit#stateAfter}. This is necessary to avoid missing
         * {@linkplain FrameState} after optimizations. See {@link GraphUtil#mayRemoveSplit} for
         * more details.
         *
         * This stage is the initial verification stage for every graph.
         */
        ALL(EnumSet.allOf(FrameStateVerificationFeature.class)),
        /**
         * Same as {@linkplain #ALL} except that {@linkplain LoopExitNode} nodes are no longer
         * verified.
         */
        ALL_EXCEPT_LOOP_EXIT(EnumSet.complementOf(EnumSet.of(FrameStateVerificationFeature.LOOP_EXITS))),
        /**
         * Same as {@linkplain #ALL_EXCEPT_LOOP_EXIT} except that {@linkplain LoopBeginNode} are no
         * longer verified.
         */
        ALL_EXCEPT_LOOPS(EnumSet.complementOf(EnumSet.of(FrameStateVerificationFeature.LOOP_BEGINS, FrameStateVerificationFeature.LOOP_EXITS))),
        /**
         * Verification is disabled. Typically used after assigning {@linkplain FrameState} to
         * {@linkplain DeoptimizeNode} or for {@linkplain Snippet} compilations.
         */
        NONE(EnumSet.noneOf(FrameStateVerificationFeature.class));

        private EnumSet<FrameStateVerificationFeature> features;

        FrameStateVerification(EnumSet<FrameStateVerificationFeature> features) {
            this.features = features;
        }

        /**
         * Determines if the current verification mode implies this feature.
         *
         * @param feature the other verification feature to check
         * @return {@code true} if this verification mode implies the feature, {@code false}
         *         otherwise
         */
        boolean implies(FrameStateVerificationFeature feature) {
            return this.features.contains(feature);
        }

    }

    /**
     * @return the status of the {@link FrameState} verification of {@link AbstractStateSplit} state
     *         after.
     */
    public FrameStateVerification getFrameStateVerification() {
        return frameStateVerification;
    }

    /**
     * Checks if this {@linkplain #getFrameStateVerification() frame state verification} can be
     * weakened to the given {@link FrameStateVerification}. Verification can only be relaxed over
     * the course of compilation.
     */
    public boolean canWeakenFrameStateVerification(FrameStateVerification stage) {
        if (isFrameStateVerificationDisabled()) {
            assert frameStateVerification == FrameStateVerification.NONE : "Frame state verification is disabled, should be NONE but is " + frameStateVerification;
            return true;
        }
        return frameStateVerification.ordinal() <= stage.ordinal();
    }

    /**
     * Sets the given {@link FrameStateVerification} as this
     * {@linkplain #getFrameStateVerification() frame state verification}.
     */
    public void weakenFrameStateVerification(FrameStateVerification newFrameStateVerification) {
        if (isFrameStateVerificationDisabled()) {
            assert frameStateVerification == FrameStateVerification.NONE : "Frame state verification is disabled, should be NONE but is " + frameStateVerification;
            return;
        }
        assert canWeakenFrameStateVerification(newFrameStateVerification) : "Old verification " + frameStateVerification + " must imply new verification " + newFrameStateVerification +
                        ", i.e., verification can only be relaxed over the course of compilation";
        frameStateVerification = newFrameStateVerification;
    }

    /**
     * Forcefully disable {@linkplain #getFrameStateVerification() frame state verification} for the
     * rest of this compilation. This must only be used for stubs, snippets, and test code that
     * builds custom compilation pipelines.
     *
     * Normal compilations must use {@link #weakenFrameStateVerification(FrameStateVerification)} to
     * progress through the standard stages of frame state verification. Calling this method is
     * <em>not</em> equivalent to calling {@code weakenFrameStateVerification(NONE)}.
     */
    public void forceDisableFrameStateVerification() {
        weakenFrameStateVerification(FrameStateVerification.NONE);
        this.disabledFrameStateVerification = true;
    }

    /**
     * The different stages of the compilation of a {@link Graph} regarding the status of
     * {@link GuardNode}s, {@link DeoptimizingNode}s and {@link FrameState}s. The stage of a graph
     * progresses monotonously.
     */
    public enum GuardsStage {
        /**
         * During this stage, there can be {@link FloatingNode floating} {@link DeoptimizingNode}s
         * such as {@link GuardNode}s. New {@link DeoptimizingNode}s can be introduced without
         * constraints. {@link FrameState}s are associated with {@link StateSplit} nodes.
         */
        FLOATING_GUARDS,
        /**
         * During this stage, all {@link DeoptimizingNode}s must be {@link FixedNode fixed} but new
         * {@link DeoptimizingNode}s can still be introduced. {@link FrameState}s are still
         * associated with {@link StateSplit} nodes.
         */
        FIXED_DEOPTS,
        /**
         * During this stage, all {@link DeoptimizingNode}s must be {@link FixedNode fixed}. New
         * {@link DeoptimizingNode}s cannot be introduced. {@link FrameState}s are now associated
         * with {@link DeoptimizingNode}s.
         */
        AFTER_FSA;

        /**
         * Checks if this guards stage indicates that the graph may contain {@link FloatingNode
         * floating} {@link DeoptimizingNode}s such as {@link GuardNode}s.
         */
        public boolean allowsFloatingGuards() {
            return this == FLOATING_GUARDS;
        }

        /**
         * Checks if this guards stage indicates new {@link DeoptimizingNode}s can be introduced in
         * the graph.
         */
        public boolean allowsGuardInsertion() {
            return this.ordinal() <= FIXED_DEOPTS.ordinal();
        }

        /**
         * Checks if this guards stage indicates all {@link FrameState}s are associated with
         * {@link DeoptimizingNode}s.
         */
        public boolean areFrameStatesAtDeopts() {
            return this == AFTER_FSA;
        }

        /**
         * Checks if this guards stage indicates all {@link FrameState}s are associated with
         * {@link StateSplit} nodes.
         */
        public boolean areFrameStatesAtSideEffects() {
            return !this.areFrameStatesAtDeopts();
        }

        /**
         * Checks if this guards stage indicates all the {@link DeoptimizingNode}s are
         * {@link FixedNode fixed}.
         */
        public boolean areDeoptsFixed() {
            return this.ordinal() >= FIXED_DEOPTS.ordinal();
        }

        /**
         * Checks if this guards stage indicates a later or equivalent stage of the compilation than
         * the given stage.
         */
        public boolean reachedGuardsStage(GuardsStage stage) {
            return this.ordinal() >= stage.ordinal();
        }
    }

    /**
     * @return the current {@link GuardsStage} for this graph state.
     */
    public GuardsStage getGuardsStage() {
        return guardsStage;
    }

    /**
     * Sets the {@linkplain #getGuardsStage() guards stage} to {@link GuardsStage#FLOATING_GUARDS}.
     */
    public void initGuardsStage() {
        setGuardsStage(GuardsStage.FLOATING_GUARDS);
    }

    /**
     * Sets the {@linkplain #getGuardsStage() guards stage} of this graph state. The new
     * {@link GuardsStage} needs to indicate a progression in the compilation, not a regression.
     */
    public void setGuardsStage(GuardsStage guardsStage) {
        assert guardsStage.ordinal() >= this.guardsStage.ordinal() : Assertions.errorMessageContext("this", this.guardsStage, "other", guardsStage);
        this.guardsStage = guardsStage;
    }

    /**
     * Determines if this graph state is configured in a way it only allows explicit exception edges
     * and no floating guards which would be lowered to deoptimize nodes.
     */
    public boolean isExplicitExceptionsNoDeopt() {
        return guardsStage == GuardsStage.FIXED_DEOPTS && isAfterStage(StageFlag.GUARD_LOWERING);
    }

    /**
     * Determines if {@link jdk.graal.compiler.nodes.memory.FloatingReadNode FloatingReadNodes} are
     * allowed to be inserted. They should only be manually inserted if
     * {@link jdk.graal.compiler.phases.common.FloatingReadPhase} has been run and
     * {@link jdk.graal.compiler.phases.common.FixReadsPhase} has not.
     */
    public boolean allowsFloatingReads() {
        return isAfterStage(StageFlag.FLOATING_READS) && isBeforeStage(StageFlag.FIXED_READS);
    }

    /**
     * Configure the graph to only allow explicit exception edges without floating guard nodes. That
     * is the graph:
     *
     * <ul>
     * <li>has explicit exception edges on {@link WithExceptionNode#exceptionEdge} successors</li>
     * <li>the graph does not support floating {@link GuardNode} as they lower to
     * {@link DeoptimizeNode}</li>
     * <li>{@link GuardNode} nodes are never lowered since they are not part of the graph. The graph
     * is always {@link #isAfterStage(StageFlag)} {@link StageFlag#GUARD_LOWERING}</li>
     * </ul>
     *
     * Note that this operation is only possible on empty graphs, i.e., it must be called at the
     * beginning of a compilation when a graph is created since it influences how the parser and
     * other components build the graph and meta data.
     */
    public void configureExplicitExceptionsNoDeopt() {
        assert !isExplicitExceptionsNoDeopt();
        assert stageFlags.isEmpty() : "Must not have set a stage flag before";
        assert guardsStage.allowsFloatingGuards() : "Default guards stage is floating guards";
        setGuardsStage(GraphState.GuardsStage.FIXED_DEOPTS);
        setAfterStage(StageFlag.GUARD_LOWERING);
    }

    public void configureExplicitExceptionsNoDeoptIfNecessary() {
        if (!isExplicitExceptionsNoDeopt()) {
            configureExplicitExceptionsNoDeopt();
        }
    }

    /**
     * Indicates FSA has been applied to this graph. (See {@link #setGuardsStage(GuardsStage)} and
     * {@link #setAfterStage(StageFlag)})
     */
    public void setAfterFSA() {
        setGuardsStage(GuardsStage.AFTER_FSA);
        setAfterStage(StageFlag.FSA);
    }

    /**
     * Different stages of the compilation regarding the status of various graph properties. The
     * order used to defined theses stages corresponds to their order in a standard compilation.
     */
    public enum StageFlag {
        CANONICALIZATION,
        /* Stages applied by high tier. */
        LOOP_OVERFLOWS_CHECKED,
        PARTIAL_ESCAPE,
        FINAL_PARTIAL_ESCAPE,
        VECTOR_API_EXPANSION,
        HIGH_TIER_LOWERING,
        /* Stages applied by mid tier. */
        FLOATING_READS,
        GUARD_MOVEMENT,
        GUARD_LOWERING,
        STRIP_MINING,
        VALUE_PROXY_REMOVAL,
        SAFEPOINTS_INSERTION,
        MID_TIER_LOWERING,
        OPTIMISTIC_ALIASING,
        FSA,
        NODE_VECTORIZATION,
        VECTOR_MATERIALIZATION,
        OPTIMISTIC_GUARDS,
        BARRIER_ADDITION,
        BARRIER_ELIMINATION,
        /* Stages applied by low tier. */
        LOW_TIER_LOWERING,
        VECTOR_LOWERING,
        EXPAND_LOGIC,
        FIXED_READS,
        PARTIAL_REDUNDANCY_SCHEDULE,
        ADDRESS_LOWERING,
        FINAL_CANONICALIZATION,
        REMOVE_OPAQUE_VALUES,
        TARGET_VECTOR_LOWERING,
        FINAL_SCHEDULE
    }

    /**
     * Checks if this graph state is before a stage. This stage must not be in progress (see
     * {@link #isDuringStage(StageFlag)}) nor have been applied yet (see
     * {@link #isAfterStage(StageFlag)}).
     */
    public boolean isBeforeStage(StageFlag stage) {
        return !isDuringStage(stage) && !isAfterStage(stage);
    }

    /**
     * Phases may set this flag to indicate that a stage is in progress. This is optional:
     * {@link #isAfterStage(StageFlag)} may become true for a stage even if
     * {@link #isDuringStage(StageFlag)} was never set for that stage.
     */
    public boolean isDuringStage(StageFlag stage) {
        return currentStage == stage;
    }

    /**
     * Checks if a stage has already been applied to this graph.
     */
    public boolean isAfterStage(StageFlag stage) {
        return stageFlags.contains(stage);
    }

    /**
     * Checks if multiple stages have been already applied to this graph.
     */
    public boolean isAfterStages(EnumSet<StageFlag> stages) {
        return stageFlags.containsAll(stages);
    }

    /**
     * Sets this {@link #currentStage} to indicate that a stage is in progress. This stage must not
     * have been applied yet.
     */
    public void setDuringStage(StageFlag stage) {
        assert isBeforeStage(stage) : "Cannot set during stage " + stage + " since the graph is not before that stage";
        currentStage = stage;
    }

    /**
     * Adds the given stage to this {@linkplain #getStageFlags() stage flags} to indicate this stage
     * has been applied. This stage must not have been applied yet.
     */
    public void setAfterStage(StageFlag stage) {
        assert !isAfterStage(stage) : "Cannot set after stage " + stage + " since the graph is already in that state";
        stageFlags.add(stage);
        currentStage = null;
    }

    /**
     * @return the stages (see {@link StageFlag}) that were applied to this graph.
     */
    public EnumSet<StageFlag> getStageFlags() {
        return stageFlags;
    }

    /**
     * Checks if all the stages represented by the given {@link MandatoryStages} have been applied
     * to this graph.
     */
    public boolean hasAllMandatoryStages(MandatoryStages mandatoryStages) {
        return stageFlags.containsAll(mandatoryStages.highTier) && stageFlags.containsAll(mandatoryStages.midTier) && stageFlags.containsAll(mandatoryStages.lowTier);
    }

    /**
     * @return the number of stages that are in {@code targetStages} but not in the
     *         {@linkplain #getStageFlags() stage flags} of this graph state.
     */
    public int countMissingStages(EnumSet<StageFlag> targetStages) {
        EnumSet<StageFlag> target = EnumSet.copyOf(targetStages);
        target.removeAll(stageFlags);
        return target.size();
    }

    /**
     * Adds the given {@link StageFlag} to the {@linkplain #getFutureRequiredStages() future
     * required stages} of this graph state.
     */
    public void addFutureStageRequirement(StageFlag stage) {
        futureRequiredStages.add(stage);
    }

    /**
     * Removes the {@linkplain #getFutureRequiredStages() requirement} to the given
     * {@link StageFlag} from this graph state.
     */
    public void removeRequirementToStage(StageFlag stage) {
        futureRequiredStages.remove(stage);
    }

    /**
     * Checks if the given {@link StageFlag} is contained in this graph state's
     * {@linkplain #getFutureRequiredStages() future required stages}.
     */
    public boolean requiresFutureStage(StageFlag stage) {
        return futureRequiredStages.contains(stage);
    }

    /**
     * Checks if this graph state has remaining {@link StageFlag}s requirements in
     * {@linkplain #getFutureRequiredStages() future required stages}.
     */
    public boolean requiresFutureStages() {
        return !futureRequiredStages.isEmpty();
    }

    /**
     * @return which stages this graph state requires. These {@linkplain #getFutureRequiredStages()
     *         future required stages} might includes lowering phases for nodes that were introduced
     *         in the graph by previous stages for example.
     */
    public EnumSet<StageFlag> getFutureRequiredStages() {
        return futureRequiredStages;
    }

    /**
     * Represents the necessary stages that must be applied to a {@link StructuredGraph} for a
     * complete compilation depending on the compiler configuration chosen. There is a different
     * {@link EnumSet} of {@link StageFlag}s for each tier of the compilation.
     */
    public enum MandatoryStages {
        ECONOMY(HIGH_TIER_MANDATORY_STAGES, MID_TIER_MANDATORY_STAGES, LOW_TIER_MANDATORY_STAGES),
        COMMUNITY(HIGH_TIER_MANDATORY_STAGES, MID_TIER_MANDATORY_STAGES, LOW_TIER_MANDATORY_STAGES),
        ENTERPRISE(HIGH_TIER_MANDATORY_STAGES, ENTERPRISE_MID_TIER_MANDATORY_STAGES, LOW_TIER_MANDATORY_STAGES);

        private final EnumSet<StageFlag> highTier;
        private final EnumSet<StageFlag> midTier;
        private final EnumSet<StageFlag> lowTier;

        MandatoryStages(EnumSet<StageFlag> highTier, EnumSet<StageFlag> midTier, EnumSet<StageFlag> lowTier) {
            this.highTier = highTier;
            this.midTier = midTier;
            this.lowTier = lowTier;
        }

        /**
         * @return the {@link MandatoryStages} corresponding to the given string. If no such value
         *         is found, returns {@link #COMMUNITY}.
         */
        public static MandatoryStages getFromName(String name) {
            switch (name.toLowerCase(Locale.ROOT)) {
                case "economy":
                    return ECONOMY;
                case "community":
                    return COMMUNITY;
                case "enterprise":
                    return ENTERPRISE;
                default:
                    return COMMUNITY;
            }
        }

        /**
         * @return the {@link EnumSet} of {@link StageFlag}s that are mandatory for high tier.
         */
        public EnumSet<StageFlag> getHighTier() {
            return highTier;
        }

        /**
         * @return the {@link EnumSet} of {@link StageFlag}s that are mandatory for mid tier.
         */
        public EnumSet<StageFlag> getMidTier() {
            return midTier;
        }

        /**
         * @return the {@link EnumSet} of {@link StageFlag}s that are mandatory for low tier.
         */
        public EnumSet<StageFlag> getLowTier() {
            return lowTier;
        }
    }
}
