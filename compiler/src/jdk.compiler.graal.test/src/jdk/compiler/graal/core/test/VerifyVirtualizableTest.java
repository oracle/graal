/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.core.test;

import static jdk.compiler.graal.core.test.GraalCompilerTest.getInitialOptions;
import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_IGNORED;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import jdk.compiler.graal.api.test.Graal;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.debug.DebugCloseable;
import jdk.compiler.graal.debug.DebugContext;
import jdk.compiler.graal.debug.DebugContext.Builder;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.java.GraphBuilderPhase;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.compiler.graal.nodes.graphbuilderconf.InvocationPlugins;
import jdk.compiler.graal.nodes.java.ArrayLengthNode;
import jdk.compiler.graal.nodes.spi.Virtualizable;
import jdk.compiler.graal.nodes.spi.VirtualizerTool;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.phases.OptimisticOptimizations;
import jdk.compiler.graal.phases.PhaseSuite;
import jdk.compiler.graal.phases.VerifyPhase.VerificationError;
import jdk.compiler.graal.phases.tiers.HighTierContext;
import jdk.compiler.graal.phases.util.Providers;
import jdk.compiler.graal.runtime.RuntimeProvider;
import org.junit.Test;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class VerifyVirtualizableTest {

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static class InvalidEffectNodeAdd extends ValueNode implements Virtualizable {

        public static final NodeClass<InvalidEffectNodeAdd> TYPE = NodeClass.create(InvalidEffectNodeAdd.class);

        protected InvalidEffectNodeAdd() {
            super(TYPE, StampFactory.forVoid());
        }

        @Override
        public void virtualize(VirtualizerTool tool) {
            graph().add(new ArrayLengthNode(null));
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static class InvalidEffectNodeAddWithoutUnique extends ValueNode implements Virtualizable {

        public static final NodeClass<InvalidEffectNodeAddWithoutUnique> TYPE = NodeClass.create(InvalidEffectNodeAddWithoutUnique.class);

        protected InvalidEffectNodeAddWithoutUnique() {
            super(TYPE, StampFactory.forVoid());
        }

        @Override
        public void virtualize(VirtualizerTool tool) {
            graph().addWithoutUnique(new ArrayLengthNode(null));
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static class InvalidEffectNodeAddOrUnique extends ValueNode implements Virtualizable {

        public static final NodeClass<InvalidEffectNodeAddOrUnique> TYPE = NodeClass.create(InvalidEffectNodeAddOrUnique.class);

        protected InvalidEffectNodeAddOrUnique() {
            super(TYPE, StampFactory.forVoid());
        }

        @Override
        public void virtualize(VirtualizerTool tool) {
            graph().addOrUnique(new ArrayLengthNode(null));
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static class InvalidEffectNodeAddWithoutUniqueWithInputs extends ValueNode implements Virtualizable {

        public static final NodeClass<InvalidEffectNodeAddWithoutUniqueWithInputs> TYPE = NodeClass.create(InvalidEffectNodeAddWithoutUniqueWithInputs.class);

        protected InvalidEffectNodeAddWithoutUniqueWithInputs() {
            super(TYPE, StampFactory.forVoid());
        }

        @Override
        public void virtualize(VirtualizerTool tool) {
            graph().addOrUnique(new ArrayLengthNode(null));
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static class InvalidEffectNodeAddOrUniqueWithInputs extends ValueNode implements Virtualizable {

        public static final NodeClass<InvalidEffectNodeAddOrUniqueWithInputs> TYPE = NodeClass.create(InvalidEffectNodeAddOrUniqueWithInputs.class);

        protected InvalidEffectNodeAddOrUniqueWithInputs() {
            super(TYPE, StampFactory.forVoid());
        }

        @Override
        public void virtualize(VirtualizerTool tool) {
            graph().addOrUnique(new ArrayLengthNode(null));
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static class ValidEffectNodeAdd extends ValueNode implements Virtualizable {

        public static final NodeClass<ValidEffectNodeAdd> TYPE = NodeClass.create(ValidEffectNodeAdd.class);

        protected ValidEffectNodeAdd() {
            super(TYPE, StampFactory.forVoid());
        }

        @Override
        public void virtualize(VirtualizerTool tool) {
            graph().add(ConstantNode.forBoolean(false));
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static class ValidEffectNodeAddWithoutUnique extends ValueNode implements Virtualizable {

        public static final NodeClass<ValidEffectNodeAddWithoutUnique> TYPE = NodeClass.create(ValidEffectNodeAddWithoutUnique.class);

        protected ValidEffectNodeAddWithoutUnique() {
            super(TYPE, StampFactory.forVoid());
        }

        @Override
        public void virtualize(VirtualizerTool tool) {
            graph().addWithoutUnique(ConstantNode.forBoolean(false));
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static class ValidEffectNodeAddOrUnique extends ValueNode implements Virtualizable {

        public static final NodeClass<ValidEffectNodeAddOrUnique> TYPE = NodeClass.create(ValidEffectNodeAddOrUnique.class);

        protected ValidEffectNodeAddOrUnique() {
            super(TYPE, StampFactory.forVoid());
        }

        @Override
        public void virtualize(VirtualizerTool tool) {
            graph().addOrUnique(ConstantNode.forBoolean(false));
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static class ValidEffectNodeAddWithoutUniqueWithInputs extends ValueNode implements Virtualizable {

        public static final NodeClass<ValidEffectNodeAddWithoutUniqueWithInputs> TYPE = NodeClass.create(ValidEffectNodeAddWithoutUniqueWithInputs.class);

        protected ValidEffectNodeAddWithoutUniqueWithInputs() {
            super(TYPE, StampFactory.forVoid());
        }

        @Override
        public void virtualize(VirtualizerTool tool) {
            graph().addOrUnique(ConstantNode.forBoolean(false));
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static class ValidEffectNodeAddOrUniqueWithInputs extends ValueNode implements Virtualizable {

        public static final NodeClass<ValidEffectNodeAddOrUniqueWithInputs> TYPE = NodeClass.create(ValidEffectNodeAddOrUniqueWithInputs.class);

        protected ValidEffectNodeAddOrUniqueWithInputs() {
            super(TYPE, StampFactory.forVoid());
        }

        @Override
        public void virtualize(VirtualizerTool tool) {
            graph().addOrUnique(ConstantNode.forBoolean(false));
        }
    }

    @Test(expected = VerificationError.class)
    public void testInvalidAdd() {
        testVirtualizableEffects(InvalidEffectNodeAdd.class);
    }

    @Test(expected = VerificationError.class)
    public void testInvalidAddWithoutUnique() {
        testVirtualizableEffects(InvalidEffectNodeAddWithoutUnique.class);
    }

    @Test(expected = VerificationError.class)
    public void testInvalidAddOrUnique() {
        testVirtualizableEffects(InvalidEffectNodeAddOrUnique.class);
    }

    @Test(expected = VerificationError.class)
    public void testInvalidAddWithoutUniqueWithInputs() {
        testVirtualizableEffects(InvalidEffectNodeAddWithoutUniqueWithInputs.class);
    }

    @Test(expected = VerificationError.class)
    public void testInvalidAddOrUniqueWithInputs() {
        testVirtualizableEffects(InvalidEffectNodeAddOrUniqueWithInputs.class);
    }

    @Test
    public void testValidAdd() {
        testVirtualizableEffects(ValidEffectNodeAdd.class);
    }

    @Test
    public void testValidAddWithoutUnique() {
        testVirtualizableEffects(ValidEffectNodeAddWithoutUnique.class);
    }

    @Test
    public void testValidAddOrUnique() {
        testVirtualizableEffects(ValidEffectNodeAddOrUnique.class);
    }

    @Test
    public void testValidAddWithoutUniqueWithInputs() {
        testVirtualizableEffects(ValidEffectNodeAddWithoutUniqueWithInputs.class);
    }

    @Test
    public void testValidAddOrUniqueWithInputs() {
        testVirtualizableEffects(ValidEffectNodeAddOrUniqueWithInputs.class);
    }

    @SuppressWarnings("try")
    private static void testVirtualizableEffects(Class<?> c) {
        RuntimeProvider rt = Graal.getRequiredCapability(RuntimeProvider.class);
        Providers providers = rt.getHostBackend().getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        Plugins plugins = new Plugins(new InvocationPlugins());
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true).withUnresolvedIsError(true);
        graphBuilderSuite.appendPhase(new GraphBuilderPhase(config));
        HighTierContext context = new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.NONE);
        OptionValues options = getInitialOptions();
        DebugContext debug = new Builder(options).build();
        for (Method m : c.getDeclaredMethods()) {
            if (!Modifier.isNative(m.getModifiers()) && !Modifier.isAbstract(m.getModifiers())) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(method).build();
                graphBuilderSuite.apply(graph, context);
                try (DebugCloseable s = debug.disableIntercept()) {
                    new VerifyVirtualizableUsage().apply(graph, context);
                }
            }
        }
    }
}
