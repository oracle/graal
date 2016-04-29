/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler.test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.Test;

import com.oracle.graal.api.test.Graal;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugConfigScope;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.nodes.java.ArrayLengthNode;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.VerifyPhase.VerificationError;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.phases.verify.VerifyVirtualizableUsage;
import com.oracle.graal.runtime.RuntimeProvider;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class VerifyVirtualizableTest {

    @NodeInfo
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

    @NodeInfo
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

    @NodeInfo
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

    @NodeInfo
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

    @NodeInfo
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

    @NodeInfo
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

    @NodeInfo
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

    @NodeInfo
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

    @NodeInfo
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

    @NodeInfo
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
        Plugins plugins = new Plugins(new InvocationPlugins(metaAccess));
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true);
        graphBuilderSuite.appendPhase(new GraphBuilderPhase(config));
        HighTierContext context = new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.NONE);
        for (Method m : c.getDeclaredMethods()) {
            if (!Modifier.isNative(m.getModifiers()) && !Modifier.isAbstract(m.getModifiers())) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                StructuredGraph graph = new StructuredGraph(method, AllowAssumptions.NO);
                graphBuilderSuite.apply(graph, context);
                try (DebugConfigScope s = Debug.disableIntercept()) {
                    new VerifyVirtualizableUsage().apply(graph, context);
                }
            }
        }
    }
}
