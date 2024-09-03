/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import static jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createStandardInlineInfo;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.word.LocationIdentity;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.test.TestGraphBuilderPhase;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.CompanionObjectEncoder;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.InliningLog;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.replacements.CachingPEGraphDecoder;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class PEGraphDecoderTest extends GraalCompilerTest {

    /**
     * This method is intrinsified to a node with a guard dependency on the block it is in. The
     * various tests ensure that this guard is correctly updated when blocks are merged during
     * inlining.
     */
    private static native int readInt(Object obj, long offset);

    private static boolean flag;
    private static int value;

    private static void invokeSimple() {
        value = 111;
    }

    private static void invokeComplicated() {
        if (flag) {
            value = 0;
        } else {
            value = 42;
        }
    }

    private static int readInt1(Object obj) {
        return readInt(obj, 16);
    }

    private static int readInt2(Object obj) {
        invokeSimple();
        return readInt(obj, 16);
    }

    private static int readInt3(Object obj) {
        invokeComplicated();
        return readInt(obj, 16);
    }

    private static int readInt4(Object obj, int n) {
        if (n > 0) {
            invokeComplicated();
        }
        return readInt(obj, 16);
    }

    public static int doTest(Object obj) {
        int result = 0;
        result += readInt1(obj);
        result += readInt2(obj);
        result += readInt3(obj);
        result += readInt4(obj, 2);
        return result;
    }

    private static void registerPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, PEGraphDecoderTest.class);
        r.register(new InvocationPlugin("readInt", Object.class, long.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode obj, ValueNode offset) {
                AddressNode address = b.add(new OffsetAddressNode(obj, offset));
                ReadNode read = b.addPush(JavaKind.Int, new ReadNode(address, LocationIdentity.any(), StampFactory.forKind(JavaKind.Int), BarrierType.NONE, MemoryOrderMode.PLAIN));
                read.setGuard(AbstractBeginNode.prevBegin(read));
                return true;
            }
        });
    }

    class InlineAll implements InlineInvokePlugin {
        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
            return createStandardInlineInfo(method);
        }
    }

    public interface SingleInterface {
        SingleInterface increment(long offset);
    }

    static class SingleInterfaceImpl implements SingleInterface {

        int counter;

        @Override
        public SingleInterfaceImpl increment(long offset) {
            counter++;
            return this;
        }

        static void init() {
        }
    }

    @BytecodeParserNeverInline
    static SingleInterface doIncrement(SingleInterface ptr) {
        return ptr.increment(0);
    }

    static void testSingleImplementorDevirtualize(SingleInterface ptr) {
        doIncrement(ptr);
    }

    @Test
    public void testSingleImplementor() {
        EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCache = EconomicMap.create();
        // Parse and cache doIncrement before the single implementor is loaded
        test("doIncrement", graphCache, getInitialOptions());
        // Force loading of the single implementor
        SingleInterfaceImpl.init();
        StructuredGraph graph = test("testSingleImplementorDevirtualize", graphCache, getInitialOptions());
        Assert.assertEquals(0, graph.getNodes().filter(InvokeNode.class).count());
    }

    @Test
    @SuppressWarnings("try")
    public void test() {
        test("doTest", EconomicMap.create(), getInitialOptions());
    }

    /**
     * Tests that the optimization log and inlining log are decoded correctly. The inlining log is
     * enabled automatically by enabling the optimization log. The respective codecs
     * {@link CompanionObjectEncoder#verify verify} that the logs are correctly decoded, i.e., the
     * decoded logs match what was encoded in the first place.
     *
     * The test also checks that inlining decisions made in the decoder are properly logged. We
     * compare a decoder that inlines everything with a {@link #parseAndInlineAll parser that
     * inlines everything}. The resulting inlining logs should have
     * {@link #assertInlinedMethodsEqual the same methods inlined}.
     */
    @Test
    public void inliningLogAndOptimizationLogDecodedCorrectly() {
        EconomicSet<DebugOptions.OptimizationLogTarget> targets = EconomicSet.create();
        targets.add(DebugOptions.OptimizationLogTarget.Stdout);
        OptionValues optionValues = new OptionValues(getInitialOptions(), DebugOptions.OptimizationLog, targets);
        String methodName = "doTest";
        InliningLog actualInliningLog = test(methodName, EconomicMap.create(), optionValues).getInliningLog();
        InliningLog expectedInliningLog = parseAndInlineAll(methodName, optionValues).getInliningLog();
        assertInlinedMethodsEqual(expectedInliningLog.getRootCallsite(), actualInliningLog.getRootCallsite());
    }

    private StructuredGraph parseAndInlineAll(String methodName, OptionValues optionValues) {
        GraphBuilderConfiguration.Plugins plugins = getDefaultGraphBuilderPlugins();
        plugins.appendInlineInvokePlugin(new InlineAll());
        GraphBuilderConfiguration graphBuilderConfig = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true).withUnresolvedIsError(true);
        graphBuilderConfig = editGraphBuilderConfiguration(graphBuilderConfig);
        registerPlugins(graphBuilderConfig.getPlugins().getInvocationPlugins());
        return parse(builder(getResolvedJavaMethod(methodName), AllowAssumptions.YES, optionValues), getCustomGraphBuilderSuite(graphBuilderConfig));
    }

    private static void assertInlinedMethodsEqual(InliningLog.Callsite expected, InliningLog.Callsite actual) {
        Assert.assertEquals(expected.isInlined(), actual.isInlined());
        Assert.assertEquals(expected.getTarget(), actual.getTarget());
        Assert.assertEquals(expected.getChildren().size(), actual.getChildren().size());
        for (var pair : CollectionsUtil.zipLongest(expected.getChildren(), actual.getChildren())) {
            assertInlinedMethodsEqual(pair.getLeft(), pair.getRight());
        }
    }

    @SuppressWarnings("try")
    private StructuredGraph test(String methodName, EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCache, OptionValues optionValues) {
        ResolvedJavaMethod testMethod = getResolvedJavaMethod(methodName);
        StructuredGraph targetGraph = null;
        DebugContext debug = getDebugContext(optionValues, null, null);
        try (DebugContext.Scope scope = debug.scope("GraphPETest", testMethod)) {
            GraphBuilderConfiguration graphBuilderConfig = GraphBuilderConfiguration.getDefault(getDefaultGraphBuilderPlugins()).withEagerResolving(true).withUnresolvedIsError(true);
            graphBuilderConfig = editGraphBuilderConfiguration(graphBuilderConfig);
            registerPlugins(graphBuilderConfig.getPlugins().getInvocationPlugins());
            targetGraph = new StructuredGraph.Builder(debug.getOptions(), debug, AllowAssumptions.YES).method(testMethod).build();
            GraphBuilderPhase.Instance instance = new TestGraphBuilderPhase.Instance(getProviders(), graphBuilderConfig, OptimisticOptimizations.NONE, null);
            CachingPEGraphDecoder decoder = new CachingPEGraphDecoder(getTarget().arch, targetGraph, getProviders(), graphBuilderConfig,
                            null, null, new InlineInvokePlugin[]{new InlineAll()}, null, null, null, null, null, graphCache, () -> null, instance, false, false, true);

            decoder.decode(testMethod);
            debug.dump(DebugContext.BASIC_LEVEL, targetGraph, "Target Graph");
            targetGraph.verify();

            CoreProviders context = getProviders();
            createCanonicalizerPhase().apply(targetGraph, context);
            targetGraph.verify();
            return targetGraph;
        } catch (Throwable ex) {
            if (targetGraph != null) {
                debug.dump(DebugContext.BASIC_LEVEL, targetGraph, ex.toString());
            }
            throw debug.handle(ex);
        }
    }
}
