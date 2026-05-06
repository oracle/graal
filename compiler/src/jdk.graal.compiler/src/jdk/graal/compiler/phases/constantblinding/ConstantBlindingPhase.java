/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.constantblinding;

import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PreLIRGraphVerification;
import jdk.graal.compiler.phases.tiers.LowTierContext;

/**
 * This phase replaces constant nodes fulfilling certain criteria with blinded constant nodes.
 * Blinded constants prevent user supplied constants from appearing verbatim in the emitted machine
 * code.
 * <p>
 * Without constant blinding, user supplied constants result in predictable byte sequences in
 * executable memory and can serve as a building block for code-reuse attacks. As an example,
 * consider the following Java code:
 *
 * <pre>
 * long evilConstant = 0x9090909090;
 * System.out.println(evilConstant);
 * </pre>
 * <p>
 * The constant would be compiled to the following machine code:
 *
 * <pre>
 *     movabs some-reg, 0x9090909090
 * </pre>
 * <p>
 * Since the constant is inlined with the machine code, the constant bytes reside in executable
 * memory. If an attacker managed to divert the control flow to the location of the constant bytes,
 * the CPU would interpret the bytes as a sequence of NOP (0x90) instructions. In a real attack, an
 * attacker would embed bytes resembling useful gadgets for a code-reuse attack.
 * <p>
 * This class explicitly avoids references to a concrete random number generator (e.g. SecureRandom)
 * to allow its use in contexts where another generator should be used and references to
 * SecureRandom might lead to an error.
 */
public abstract class ConstantBlindingPhase extends BasePhase<LowTierContext> {

    public static final class Options {
        //@formatter:off
        @Option(help = "Blinds constants in code with a random key.", type = OptionType.User, stability = OptionStability.STABLE)
        public static final OptionKey<Boolean> BlindConstants = new OptionKey<>(false);

        @Option(help = "Specifies the minimum size (in bytes) of constants to blind.", type = OptionType.User, stability = OptionStability.STABLE)
        public static final OptionKey<Integer> MinimumBlindedConstantSize = new OptionKey<>(4);
        //@formatter:on
    }

    /**
     * Records the last graph that processed by this phase on this thread. This allows the
     * verification to decide whether it should run or not.
     */
    private static final ThreadLocal<StructuredGraph> LAST_PROCESSED_GRAPH = new ThreadLocal<>();

    /**
     * Factory for random generator.
     */
    private final LongSupplier keyGenerator;
    /**
     * Specifies whether constants in frame states should be blinded.
     */
    private final boolean blindConstantsInStates;

    /**
     * Create a new constant blinding phase instance.
     *
     * @param keyGenerator a generator for blinding keys. Make sure to use a secure random number
     *            generator except for testing purposes.
     * @param blindConstantsInStates indicates whether the constants referred by frame states are
     *            applicable by this phase.
     */
    public ConstantBlindingPhase(LongSupplier keyGenerator, boolean blindConstantsInStates) {
        this.keyGenerator = keyGenerator;
        this.blindConstantsInStates = blindConstantsInStates;
    }

    /**
     * TODO: exists only for migration to new constructor interface, do not use. The class
     * essentially translates the old interface to the new one without calling into the Randomness
     * instance at build time.
     */
    static class LazyInitializer implements LongSupplier {
        private final Supplier<LongSupplier> supplierFactory;
        private LongSupplier supplier;

        LazyInitializer(Supplier<LongSupplier> supplier) {
            this.supplierFactory = supplier;
        }

        @Override
        public long getAsLong() {
            if (supplier == null) {
                supplier = supplierFactory.get();
            }

            return supplier.getAsLong();
        }
    }

    public ConstantBlindingPhase(Supplier<LongSupplier> keyGenFactory, boolean blindConstantsInStates) {
        this(new LazyInitializer(keyGenFactory), blindConstantsInStates);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        // Make sure that no canonicalization follows constant blinding. The canonicalizer folds
        // encrypted constants, leading to incorrect results.
        return NotApplicable.unlessRunAfter(this, StageFlag.FINAL_CANONICALIZATION, graphState);
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, LowTierContext context) {
        if (Assertions.assertionsEnabled()) {
            LAST_PROCESSED_GRAPH.set(graph);
        }
        new ConstantBlindingInstance(keyGenerator, blindConstantsInStates).run(graph, context);
    }

    public static PreLIRGraphVerification createVerifier() {
        return new Verification();
    }

    private static final class Verification extends ConstantBlindingPhase implements PreLIRGraphVerification {

        Verification() {
            /*
             * We pass a stub key generator here to avoid a SecureRandom instance that would end up
             * on the heap if the verification is called from SubstrateVM. The actual key used for
             * the verification does not matter because if new nodes are introduced (i.e., the key
             * is actually used), the verification fails anyway.
             */
            super(() -> 0xabcd, true);
        }

        /**
         * Verify that reapplying the constant blinding phase does not change the graph. Otherwise,
         * somebody introduced constants after the constant blinding phase which would end up
         * unblinded in the machine code.
         *
         * @param graph the graph to verify
         * @return true if no additional nodes were introduced during a second run of constant
         *         blinding
         */
        @Override
        public boolean verify(StructuredGraph graph) {
            if (LAST_PROCESSED_GRAPH.get() != null && LAST_PROCESSED_GRAPH.get().equals(graph)) {
                Graph.Mark before = graph.getMark();
                run(graph, null);
                Graph.Mark after = graph.getMark();
                assert before.equals(after) : graph + ": re-applying constant blinding introduced these new nodes: " + graph.getNewNodes(before).snapshot();
                LAST_PROCESSED_GRAPH.remove();
            }
            return true;
        }
    }
}
