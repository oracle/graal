/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsParser;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.priorityinline.AbstractPriorityInliningPhase;
import jdk.graal.compiler.phases.common.priorityinline.DefaultInliningProvider;
import jdk.graal.compiler.phases.common.priorityinline.Expander;
import jdk.graal.compiler.phases.common.priorityinline.Inliner;
import jdk.graal.compiler.phases.common.priorityinline.InliningProvider;
import jdk.graal.compiler.phases.common.priorityinline.PolicyFactory;
import jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase;
import jdk.graal.compiler.phases.common.priorityinline.tuning.TuningPolicy;
import jdk.graal.compiler.phases.tiers.HighTierContext;

public class PriorityInliningPolicyFactoryTest extends PriorityInliningTest {
    private static final class CountingPolicyFactory implements PolicyFactory {
        private final PolicyFactory delegate;
        private int expanderPolicyCreations;
        private int inlinerPolicyCreations;
        private int tuningPolicyCreations;

        CountingPolicyFactory(PolicyFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public Expander.Policy createExpanderPolicy(OptionValues options, HighTierContext context) {
            expanderPolicyCreations++;
            return delegate.createExpanderPolicy(options, context);
        }

        @Override
        public Inliner.Policy createInlinerPolicy(OptionValues options) {
            inlinerPolicyCreations++;
            return delegate.createInlinerPolicy(options);
        }

        @Override
        public TuningPolicy createTuningPolicy(OptionValues options) {
            tuningPolicyCreations++;
            return delegate.createTuningPolicy(options);
        }
    }

    private static final class TestInliningProvider extends DefaultInliningProvider {
        private final PolicyFactory policyFactory;
        private int policyFactorySelections;

        TestInliningProvider(PolicyFactory policyFactory) {
            this.policyFactory = policyFactory;
        }

        @Override
        public PolicyFactory policy(OptionValues options) {
            policyFactorySelections++;
            return policyFactory;
        }
    }

    private static final class TestPriorityInliningPhase extends AbstractPriorityInliningPhase {
        TestPriorityInliningPhase(CanonicalizerPhase canonicalizer, OptionValues options, InliningProvider inliningProvider) {
            super(canonicalizer, options, inliningProvider);
        }
    }

    public static int snippet(int value) {
        return value + 1;
    }

    @Test
    public void instanceHasSingleConstructor() {
        Assert.assertEquals(AbstractPriorityInliningPhase.class, PriorityInliningPhase.class.getSuperclass());
        Assert.assertEquals(1, AbstractPriorityInliningPhase.Instance.class.getDeclaredConstructors().length);
    }

    @Test
    public void commonOptionsAreDiscovered() {
        Assert.assertSame(AbstractPriorityInliningPhase.Options.TrackInliningStatistics, optionDescriptor("TrackInliningStatistics").getOptionKey());
        Assert.assertSame(AbstractPriorityInliningPhase.Options.PriorityForceInline, optionDescriptor("PriorityForceInline").getOptionKey());
        Assert.assertSame(AbstractPriorityInliningPhase.Options.PriorityNeverInline, optionDescriptor("PriorityNeverInline").getOptionKey());
    }

    private static OptionDescriptor optionDescriptor(String name) {
        for (OptionDescriptors descriptors : OptionsParser.getOptionsLoader()) {
            OptionDescriptor descriptor = descriptors.get(name);
            if (descriptor != null) {
                return descriptor;
            }
        }
        throw new AssertionError("Missing option descriptor: " + name);
    }

    @Test
    public void policyFactoryIsCachedPerPhase() {
        OptionValues options = getInitialOptions();
        StructuredGraph graph = parseEager(getResolvedJavaMethod("snippet"), StructuredGraph.AllowAssumptions.YES);
        PhaseSuite<HighTierContext> graphBuilderSuite = getDefaultGraphBuilderSuite();
        HighTierContext context = new HighTierContext(getProviders(), graphBuilderSuite, OptimisticOptimizations.ALL);
        DefaultInliningProvider inliningProvider = new DefaultInliningProvider();
        CountingPolicyFactory policyFactory = new CountingPolicyFactory(inliningProvider.policy(options));

        TestInliningProvider testInliningProvider = new TestInliningProvider(policyFactory);
        AbstractPriorityInliningPhase phase = new TestPriorityInliningPhase(createCanonicalizerPhase(), options, testInliningProvider);
        phase.createInstance(graph, context);
        phase.createInstance(graph, context);

        Assert.assertEquals(1, testInliningProvider.policyFactorySelections);
        Assert.assertEquals(2, policyFactory.expanderPolicyCreations);
        Assert.assertEquals(2, policyFactory.inlinerPolicyCreations);
        Assert.assertEquals(2, policyFactory.tuningPolicyCreations);
    }
}
