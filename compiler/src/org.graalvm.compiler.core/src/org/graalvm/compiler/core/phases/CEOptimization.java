/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.phases;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.loop.phases.LoopFullUnrollPhase;
import org.graalvm.compiler.loop.phases.LoopPartialUnrollPhase;
import org.graalvm.compiler.loop.phases.LoopPeelingPhase;
import org.graalvm.compiler.loop.phases.LoopSafepointEliminationPhase;
import org.graalvm.compiler.loop.phases.LoopUnswitchingPhase;
import org.graalvm.compiler.nodes.memory.MemoryMap;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.DeoptimizationGroupingPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.common.LockEliminationPhase;
import org.graalvm.compiler.phases.common.ReassociationPhase;
import org.graalvm.compiler.phases.common.UseTrappingNullChecksPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.graalvm.compiler.virtual.phases.ea.ReadEliminationPhase;

/**
 * This class enumerates the most important platform-independent optimizations in the GraalVM CE
 * compiler. It contains summaries of the optimizations with links to their sources and the options
 * to enable them. The linked sources typically contain detailed examples and motivation for the
 * optimizations.
 */
@SuppressWarnings("unused")
public enum CEOptimization {

    /**
     * {@link CanonicalizerPhase} is a compound optimization phase grouping together several
     * independent optimizations that are mostly "local" to a node and its directly connected nodes.
     * Optimizations include constant folding, strength reduction, algebraic simplifications and so
     * on.
     *
     * This phase is unconditionally enabled.
     *
     * @see org.graalvm.compiler.graph.spi.Canonicalizable#canonical(CanonicalizerTool)
     * @see org.graalvm.compiler.graph.Node#simplify(SimplifierTool)
     * @see org.graalvm.compiler.graph.Node.ValueNumberable
     *
     */
    Canonicalization(null, CanonicalizerPhase.class),

    /**
     * {@link InliningPhase} is Graal CE's implementation of a traditional inlining algorithm.
     *
     * This phase is enabled by default and can be disabled with {@link HighTier.Options#Inline}.
     */
    Inlining(HighTier.Options.Inline, InliningPhase.class),

    /**
     * {@link DeadCodeEliminationPhase} tries to remove unused (i.e., "dead") code from a program.
     * Program code is considered dead if it is proven to be never executed at runtime. This often
     * arises as the result of preceding optimizations.
     *
     * This phase is unconditionally enabled.
     */
    DeadCodeElimination(null, DeadCodeEliminationPhase.class),

    /**
     * {@link ConvertDeoptimizeToGuardPhase} analyzes the control flow graph of a program and tries
     * to find patterns of the form
     *
     * <pre>
     * if (...) deoptimize
     * </pre>
     *
     * It rewrites such a pattern to a single IR <em>guard</em> node that can be optimized better
     * than fixed control flow. This can improve program performance since guards can move freely
     * around in the program and be scheduled at points which have a lower execution probability
     * resulting in less instructions being executed at runtime.
     *
     * This phase is enabled by default and can be disabled with
     * {@link GraalOptions#OptConvertDeoptsToGuards}.
     */
    DeoptimizeToGuard(GraalOptions.OptConvertDeoptsToGuards, ConvertDeoptimizeToGuardPhase.class),

    /**
     * {@link ConditionalEliminationPhase} is a control-flow sensitive implementation of a
     * conditional elimination algorithm. It combines flow-sensitive type and value information and
     * removes conditional operations for which the associated condition can be proven {@code true}
     * or {@code false}.
     *
     * This phase is enabled by default and can be disabled with
     * {@link GraalOptions#ConditionalElimination}.
     */
    ConditionalElimination(GraalOptions.ConditionalElimination, ConditionalEliminationPhase.class),

    /**
     * {@link SchedulePhase} is Graal's implementation of an instruction scheduling algorithm for
     * the compiler IR. <a href="http://ssw.jku.at/General/Staff/GD/APPLC-2013-paper_12.pdf">Graal
     * IR</a> is a graph-based intermediate representation loosely based on the idea of the
     * "sea-of-nodes" IR. In the IR, side-effect free operations are represented with so-called
     * "floating" nodes that can freely move around in the compiler IR until code emission where the
     * final position of a node in the generated program is solely dependent on its input (data
     * dependencies) and usages.
     *
     * This phase is unconditionally enabled (it's required for correct code generation).
     */
    InstructionScheduling(null, SchedulePhase.class),

    /**
     * {@link FloatingReadPhase} rewrites fixed memory read nodes to floating read nodes that can
     * move more freely (see {@link #InstructionScheduling}). It builds a {@linkplain MemoryMap
     * memory graph} which allows better optimization of memory related instructions.
     *
     * This phase is enabled by default and can be disabled with
     * {@link GraalOptions#OptFloatingReads}.
     */
    FloatingReads(GraalOptions.OptFloatingReads, FloatingReadPhase.class),

    /**
     * {@link ReadEliminationPhase} tries to remove redundant memory access operations (e.g.,
     * successive reads of the same Java field are redundant). Its uses a control-flow sensitive
     * analysis.
     *
     * This phase is enabled by default and can be disabled with
     * {@link GraalOptions#OptReadElimination}.
     */
    ReadElimination(GraalOptions.OptReadElimination, ReadEliminationPhase.class),

    /**
     * {@link PartialEscapePhase} is a control flow sensitive algorithm that can replace object and
     * array allocation with use of stack slots and registers in the hot parts of a graph. Unlike
     * non-partial escape analysis, it can perform this transformation by allowing an object
     * allocation to be deferred to cold paths that exit the compilation unit.
     *
     * This optimization has a non-trivial impact on compilation time and so might be worth
     * disabling for workloads that do not perform much allocation.
     *
     * Object allocations are expensive thus partial escape analysis can greatly improve performance
     * of an application by reducing the number of allocations. This reduces interaction with the
     * memory manager on the allocation path and also reduces work done during garbage collection.
     *
     * This phase is enabled by default and can be disabled with
     * {@link GraalOptions#PartialEscapeAnalysis}.
     */
    PartialEscapeAnanylsis(GraalOptions.PartialEscapeAnalysis, PartialEscapePhase.class),

    /**
     * {@link LockEliminationPhase} tries to reduce Java monitor enter/exit overhead of an
     * application. Java {@code synchronized} blocks mark critical regions which can only be entered
     * if a thread acquires an object monitor (enter operation). A monitor is held until the region
     * is exited (monitor exit). Lock elimination (also known as lock coarsening) tries to merge
     * adjacent synchronized regions into larger ones by removing enters that are directly followed
     * by exits on the same locked object. It thus removes redundant unlock-lock operations.
     *
     * This phase is unconditionally enabled.
     */
    LockElimination(null, LockEliminationPhase.class),

    /**
     * {@link LoopSafepointEliminationPhase} tries to reduce the number of safepoint checks in the
     * generated machine code. Safepoints in Java are program locations where mutator threads
     * (application threads) are at a well defined point with respect to the Java heap. This means
     * at a safepoint the GC can safely manipulate the Java heap. Typical safepoint operations are
     * garbage collections, class redefinition, lock unbiasing, and monitor deflation. As the
     * safepoint protocol is a cooperative mechanism, it requires code generated by the compiler to
     * periodically poll (i.e., read) a well known memory location. These polls can incur
     * performance overheads in very tight loops. Thus, Graal removes safepoint polls in loops
     * heuristically to improve performance.
     *
     * This phase is unconditionally enabled.
     */
    SafepointElimination(null, LoopSafepointEliminationPhase.class),

    /**
     * {@link ReassociationPhase} implements expression reassociation. It re-orders operations and
     * their operands to create more potential for constant folding and loop invariant code motion.
     *
     * This phase is enabled by default and can be disabled with
     * {@link GraalOptions#ReassociateExpressions}.
     */
    ExpressionReassociation(GraalOptions.ReassociateExpressions, ReassociationPhase.class),

    /**
     * {@link DeoptimizationGroupingPhase} tries to reduce the meta-data the compiler needs to
     * preserve in the generated machine code for deoptimization purposes. This optimization can
     * reduce the size of the generated machine code at runtime.
     *
     * This phase is enabled by default and can be disabled with
     * {@link GraalOptions#OptDeoptimizationGrouping}.
     */
    DeoptimizationGrouping(GraalOptions.OptDeoptimizationGrouping, DeoptimizationGroupingPhase.class),

    /**
     * {@link UseTrappingNullChecksPhase} exploits modern processors abilities to throw signals for
     * invalid memory accesses to remove explicit null check operations and replace them with
     * implicit checks. This optimization removes explicit null checks. If a null memory location is
     * accessed by the generated code, hardware memory protection will raise a SIGSEGV signal. The
     * VM catches this signal, maps it to the program location of an implicit null check and throws
     * a regular {@link NullPointerException} at that location. This optimization can improve the
     * performance of generated code by removing an explicit null check before memory reads.
     *
     * This phase is unconditionally enabled.
     */
    TrappingNullChecks(null, UseTrappingNullChecksPhase.class),

    /**
     * {@link LoopFullUnrollPhase} is a special form of loop unrolling that processes loops with a
     * constant number of iterations. Based on heuristics which include but are not exclusively
     * based on the number of iterations, this phase will either completely unroll a loop or not
     * transform it at all. That is, it is an "all or nothing" phase.
     *
     * Unrolling a loop can improve performance by removing the loop control and jump overhead. It
     * also produces larger basic blocks and thus more scope for other optimizations to apply. Full
     * loop unrolling will generally increase code size which can decrease performance. For this
     * reason, the decision of whether or not it is applied for a given loop takes into account
     * factors beyond the constant iteration count such as current code size.
     *
     * This phase is enabled by default and can be disabled with {@link GraalOptions#FullUnroll}.
     */
    FullLoopUnrolling(GraalOptions.FullUnroll, LoopFullUnrollPhase.class),

    /**
     * {@link LoopPeelingPhase} is an optimization that moves first or last loop iterations outside
     * the loop. This process of moving loop iterations is called "peeling". This can improve
     * performance of the generated code when the peeled iterations have complex logic that is
     * absent from the unpeeled iterations.
     *
     * This phase is enabled by default and can be disabled with {@link GraalOptions#LoopPeeling}.
     *
     * @see "https://en.wikipedia.org/wiki/Loop_splitting#Loop_peeling"
     */
    LoopPeeling(GraalOptions.LoopPeeling, LoopPeelingPhase.class),

    /**
     * {@link LoopUnswitchingPhase} is a traditional compiler optimization dealing with loops with
     * loop invariant control flow (conditions) inside a loop's body. It "unswitches" a loop, ie.
     * pulls the invariant condition outside and duplicates a loop for the respective branches of
     * the invariant condition. This can improve performance since less instructions are evaluated
     * inside the loop's body.
     *
     * This phase is enabled by default and can be disabled with {@link GraalOptions#LoopUnswitch}.
     *
     * @see "https://en.wikipedia.org/wiki/Loop_unswitching"
     */
    LoopUnswitching(GraalOptions.LoopUnswitch, LoopUnswitchingPhase.class),

    /**
     * {@link LoopPartialUnrollPhase} is a compiler optimization unrolling the body of a loop
     * multiple times to improve instruction-level parallelism, reduce the loop control overhead and
     * enable other optimizations.
     *
     * This phase is enabled by default and can be disabled with {@link GraalOptions#PartialUnroll}.
     */
    PartialLoopUnrolling(GraalOptions.PartialUnroll, LoopPartialUnrollPhase.class);

    private final OptionKey<?> option;
    private final Class<? extends BasePhase<?>> optimization;

    CEOptimization(OptionKey<?> option, Class<? extends BasePhase<?>> optimization) {
        this.option = option;
        this.optimization = optimization;
    }
}
