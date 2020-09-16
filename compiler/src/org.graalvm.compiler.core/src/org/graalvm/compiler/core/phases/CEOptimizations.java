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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.loop.phases.LoopFullUnrollPhase;
import org.graalvm.compiler.loop.phases.LoopPartialUnrollPhase;
import org.graalvm.compiler.loop.phases.LoopPeelingPhase;
import org.graalvm.compiler.loop.phases.LoopSafepointEliminationPhase;
import org.graalvm.compiler.loop.phases.LoopUnswitchingPhase;
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
import org.graalvm.compiler.virtual.phases.ea.EarlyReadEliminationPhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;

/**
 * This class summarizes the most important platform-independent optimizations in the GraalVM CE
 * compiler. It contains documentation summaries for the different optimizations and links to their
 * sources and the options to enabled them. JavaDoc of optimization phases typically contains
 * detailed examples and motivation.
 */
@SuppressWarnings("unused")
public enum CEOptimizations {

    /**
     * {@link CanonicalizerPhase} is a compound optimization phase grouping together several
     * independent optimizations that are mostly "local" to a node and its direct surroundings.
     * Optimizations include constant folding, strength reduction, algebraic simplifications and so
     * on.
     *
     * @see org.graalvm.compiler.graph.spi.Canonicalizable#canonical(CanonicalizerTool)
     * @see org.graalvm.compiler.graph.Node#simplify(SimplifierTool)
     * @see org.graalvm.compiler.graph.Node.ValueNumberable
     *
     *      The canonicalizer is unconditionally enabled for all compilations with Graal.
     */
    Canonicalization(null, CanonicalizerPhase.class),

    /**
     * {@link InliningPhase} is Graal CE's implementation of a traditional inlining algorithm.
     *
     * Inlining in GraalVM CE can be enabled/disabled with the option
     * {@link HighTier.Options#Inline}.
     */
    Inlining(HighTier.Options.Inline, InliningPhase.class),

    /**
     * {@link DeadCodeEliminationPhase} tries to remove unused, so called "dead" code from a program
     * during compilation. Program code is considered dead if it is proven to be never executed at
     * runtime. This often arises as the result of preceding optimizations.
     *
     * Dead Code elimination is unconditionally enabled for all compilations with Graal.
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
     * It rewrites such a pattern to a single IR <em>guard</em> node that can be optimized more
     * easily than fixed control flow. This can improve program performance since guards can move
     * more freely around in the program and be scheduled at points which have a lower execution
     * probability resulting in less instructions being executed at runtime.
     *
     * DeoptimzeToGuard can be enabled/disabled with the option
     * {@link GraalOptions#OptConvertDeoptsToGuards}.
     */
    DeoptimizeToGuard(GraalOptions.OptConvertDeoptsToGuards, ConvertDeoptimizeToGuardPhase.class),

    /**
     * {@link ConditionalEliminationPhase} is a control-flow sensitive implementation of a
     * conditional elimination algorithm. It combines flow-sensitive type and value information and
     * proves that conditional operations are always {@code true} or {@code false} and removes them,
     * and any related branches for a program.
     *
     * Conditional elimination in Graal can be enabled/disabled with the option
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
     * Scheduling in Graal is required for correct code generation and thus unconditionally enabled
     * during compilation.
     */
    InstructionScheduling(null, SchedulePhase.class),

    /**
     * {@link FloatingReadPhase} rewrites fixed memory read nodes to floating read nodes that can
     * move more freely (see {@link #InstructionScheduling}). It builds the memory graph for the
     * Graal IR which allows better optimization of memory related instructions.
     *
     * Floating reads in Graal can be enabled/disabled with the option
     * {@link GraalOptions#OptFloatingReads}.
     */
    FloatingReads(GraalOptions.OptFloatingReads, FloatingReadPhase.class),

    /**
     * {@link EarlyReadEliminationPhase} tries to remove redundant memory access operations, i.e.
     * Java field reads can be redundant if a previous instruction already reads the same memory
     * location. The algorithm behind the optimization is control-flow sensitive.
     *
     * Read elimination in Graal can be enabled/disabled with the option
     * {@link GraalOptions#OptReadElimination}.
     */
    ReadElimination(GraalOptions.OptReadElimination, EarlyReadEliminationPhase.class),

    /**
     * {@link PartialEscapePhase} is a control flow sensitive escape analysis algorithm that allows
     * the compiler to perform escape analysis and scalar replacement on individual branches. This
     * optimization has a non-trivial impact on compilation time and so might be worth disabling for
     * workloads that do not perform much allocation.
     *
     * Object allocations are expensive thus partial escape analysis can greatly improve performance
     * of an application by reducing the number of allocations which in return can reduce GC
     * pressure.
     *
     * Partial escape analysis can be enabled/disabled with the option
     * {@linkplain GraalOptions#PartialEscapeAnalysis}.
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
     * Lock elimination in Graal is unconditionally enabled for all compilations.
     */
    LockElimination(null, LockEliminationPhase.class),

    /**
     * {@link LoopSafepointEliminationPhase} tries to reduce the number of safepoint checks in the
     * generated machine code. Safepoints in Java are program locations where mutator threads
     * (application threads) are at a well defined point with their interaction to the Java heap.
     * This means at a safepoint the GC can safely manipulate the Java heap. Typical safepoint
     * operations are garbage collections, class redefinition, lock unbiasing, and monitor
     * deflation. As the safepoint protocol is collaborative, it forces the code generated by the
     * JIT compilers to periodically poll the safepoint state (the interpreter polls it after every
     * interpreted bytecode).
     *
     * Safepoint polls are memory reads that can incur performance overheads in very tight loops.
     * Thus, Graal removes safepoint operations inside loops heuristically to improve performance.
     *
     * Safepoint eliminiation is unconditionally enabled for all compilations.
     */
    SafepointElimination(null, LoopSafepointEliminationPhase.class),

    /**
     * {@link ReassociationPhase} implements expression reassociation. It re-orders operations and
     * their operands to create more potential for constant folding and loop invariant code motion.
     *
     * Reassociation can be enabled/disabled with the option
     * {@link GraalOptions#ReassociateExpressions}.
     */
    ExpressionReassociation(GraalOptions.ReassociateExpressions, ReassociationPhase.class),

    /**
     * {@link DeoptimizationGroupingPhase} tries to reduce the meta-data the compiler needs to
     * preserve in the generated machine code for deoptimization purposes. This optimization can
     * reduce the size of the generated machine code at runtime.
     *
     * Deoptimization grouping can be enabled/disabled with the option
     * {@link GraalOptions#OptDeoptimizationGrouping}.
     */
    DeoptimizationGrouping(GraalOptions.OptDeoptimizationGrouping, DeoptimizationGroupingPhase.class),

    /**
     * {@link UseTrappingNullChecksPhase} exploits modern processors abilities to throw signals for
     * invalid memory accesses to remove explicit null check operations and replace them with
     * implicit checks. This optimization removes explicit null checks and replaces them with
     * regular memory access. If the accessed memory location is null and accessed by the generated
     * code, hardware memory protection will throw a SIGSEGV error that is caught by the VM and
     * mapped back to the program location of the implicit null check and a regular
     * {@link NullPointerException} is thrown. This optimization can improve the performance of
     * generated code as often no explicit null check operations are necessary.
     *
     * Trapping null checks are unconditionally enabled for all compilations.
     */
    TrappingNullChecks(null, UseTrappingNullChecksPhase.class),

    /**
     * {@link LoopFullUnrollPhase} is a special form of loop unrolling that processes loops with
     * constant loop trip counts. It tries to unwrap the body of a loop multiple times to reduce the
     * loop control and jump overhead. Constant trip count loops are unrolled exhaustively (for
     * small constants) to remove the loop completely. This can improve the performance of the
     * generated code since larger basic blocks for optimization are created and all loop-iteration
     * jumps are removed.
     *
     * Full unrolling can be enabled/disabled with the option {@link GraalOptions#FullUnroll}.
     */
    FullLoopUnrolling(GraalOptions.FullUnroll, LoopFullUnrollPhase.class),

    /**
     * {@link LoopPeelingPhase} is an optimization that cuts off interesting first or last
     * iterations of a loop. This process of cutting off a loop iteration is called "peeling" off an
     * iteration. This can improve performance of the generated code since the body of the remaining
     * loop can often be optimized after a peel operation.
     *
     * Loop peeling can be enabled/disabled with the option {@link GraalOptions#LoopPeeling}.
     */

    LoopPeeling(GraalOptions.LoopPeeling, LoopPeelingPhase.class),

    /**
     * {@link LoopUnswitchingPhase} is a traditional compiler optimization dealing with loops with
     * loop invariant control flow (conditions) inside a loop's body. It "unswitches" a loop, ie.
     * pulls the invariant condition outside and duplicates a loop for the respective branches of
     * the invariant condition. This can improve performance since less instructions are evaluated
     * inside a the loop's body.
     *
     * Loop unswitchting can be enabled/disabled with the option {@link GraalOptions#LoopUnswitch}.
     */
    LoopUnswitchting(GraalOptions.LoopUnswitch, LoopUnswitchingPhase.class),

    /**
     * {@link LoopPartialUnrollPhase} is a compiler optimization unrolling the body of a loop
     * multiple times to improve instruction-level parallelism, reduce the loop control overhead and
     * enable other optimizations.
     *
     * Partial unrolling can be enabled/disabled with the option {@link GraalOptions#PartialUnroll}.
     */
    PartialLoopUnrolling(GraalOptions.PartialUnroll, LoopPartialUnrollPhase.class);

    private final OptionKey<?> option;
    private final Class<? extends BasePhase<?>> optimization;

    CEOptimizations(OptionKey<?> option, Class<? extends BasePhase<?>> optimization) {
        this.option = option;
        this.optimization = optimization;
    }

}
