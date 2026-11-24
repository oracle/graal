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

import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewArrayWithExceptionNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.priorityinline.CallTree;
import jdk.graal.compiler.phases.common.priorityinline.GraphCache;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class PriorityInliningMutationTest extends PriorityInliningTest {
    private static boolean isMinMax(ResolvedJavaMethod method) {
        return method.getDeclaringClass().getName().equals("Ljava/lang/Math;") && (method.getName().equals("min") || method.getName().equals("max"));
    }

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins plugins = super.getDefaultGraphBuilderPlugins();
        // Defer min/max intrinsification to the inliner so it specializes the intrinsic graph.
        InvocationPlugins invocations = new InvocationPlugins(null, plugins.getInvocationPlugins()) {
            @Override
            public InvocationPlugin lookupInvocation(ResolvedJavaMethod method, boolean allowDecorators, boolean allowDisable, OptionValues options) {
                return isMinMax(method) ? null : super.lookupInvocation(method, allowDecorators, allowDisable, options);
            }
        };
        return new Plugins(plugins, invocations);
    }

    @Override
    protected InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        return isMinMax(method) ? InlineInfo.DO_NOT_INLINE_NO_EXCEPTION : super.bytecodeParserShouldInlineInvoke(b, method, args);
    }

    public static double minCaller() {
        return Math.min(Double.NaN, 7.0);
    }

    public static double maxCaller() {
        return Math.max(Double.NaN, 7.0);
    }

    @Test
    public void testMinNaNInlining() {
        checkNaNIntrinsic("minCaller");
    }

    @Test
    public void testMaxNaNInlining() {
        checkNaNIntrinsic("maxCaller");
    }

    private void checkNaNIntrinsic(String snippet) {
        StructuredGraph graph = assertInlined(getGraph(snippet, getInitialOptions()));
        ReturnNode ret = graph.getNodes(ReturnNode.TYPE).first();
        Assert.assertTrue(ret.result().isJavaConstant());
        Assert.assertTrue(Double.isNaN(ret.result().asJavaConstant().asDouble()));
        Assert.assertTrue(graph.verify());
        Assert.assertTrue(Double.isNaN((double) executeVarargsSafe(getCode(getResolvedJavaMethod(snippet), graph))));
    }

    @BytecodeParserNeverInline(invokeWithException = true)
    public static byte[] allocatingCallee(int length) {
        return new byte[length];
    }

    public static byte[] allocatingCaller(int length, boolean catchOOME) {
        if (catchOOME) {
            try {
                return allocatingCallee(length);
            } catch (OutOfMemoryError e) {
                return null;
            }
        }
        return allocatingCallee(length);
    }

    @Test
    public void testGraphCacheRespectsOOMEContext() {
        checkOOMEContext(false);
        checkOOMEContext(true);
    }

    @Test
    public void testInliningWithOOMEHandler() {
        StructuredGraph graph = assertInlined(getGraph("allocatingCaller", getInitialOptions()));
        Assert.assertEquals(1, graph.getNodes().filter(NewArrayWithExceptionNode.class).count());
        Assert.assertEquals(1, graph.getNodes().filter(NewArrayNode.class).count());
        Assert.assertTrue(graph.verify());
        InstalledCode code = getCode(getResolvedJavaMethod("allocatingCaller"), graph);
        Assert.assertArrayEquals(new byte[8], (byte[]) executeVarargsSafe(code, 8, false));
        Assert.assertArrayEquals(new byte[8], (byte[]) executeVarargsSafe(code, 8, true));
        // HotSpot rejects this array length before trying to exhaust the Java heap.
        Assert.assertNull(executeVarargsSafe(code, Integer.MAX_VALUE, true));
        Assert.assertThrows(OutOfMemoryError.class, () -> executeVarargsSafe(code, Integer.MAX_VALUE, false));
    }

    private void checkOOMEContext(boolean firstCatchesOOME) {
        CallTree tree = getCallTreeBeforeInlining("allocatingCaller");
        Invoke catchingInvoke = null;
        Invoke normalInvoke = null;
        for (MethodCallTargetNode target : tree.root().getReadonlySubgraph().getNodes(MethodCallTargetNode.TYPE)) {
            if (target.targetMethod().equals(getResolvedJavaMethod("allocatingCallee"))) {
                if (target.invoke().isInOOMETry()) {
                    catchingInvoke = target.invoke();
                } else {
                    normalInvoke = target.invoke();
                }
            }
        }
        Assert.assertNotNull(catchingInvoke);
        Assert.assertNotNull(normalInvoke);
        ResolvedJavaMethod method = normalInvoke.getTargetMethod();
        GraphCache.Ref<ResolvedJavaMethod, StructuredGraph> first = tree.createGraph(method, firstCatchesOOME ? catchingInvoke : normalInvoke, false, null);
        GraphCache.Ref<ResolvedJavaMethod, StructuredGraph> second = tree.createGraph(method, firstCatchesOOME ? normalInvoke : catchingInvoke, false, null);
        try {
            Assert.assertNotSame(first.readonly(), second.readonly());
            StructuredGraph catching = firstCatchesOOME ? first.readonly() : second.readonly();
            StructuredGraph nonCatching = firstCatchesOOME ? second.readonly() : first.readonly();
            Assert.assertFalse(catching.getNodes().filter(NewArrayWithExceptionNode.class).isEmpty());
            Assert.assertTrue(nonCatching.getNodes().filter(NewArrayWithExceptionNode.class).isEmpty());
            Assert.assertFalse(nonCatching.getNodes().filter(NewArrayNode.class).isEmpty());
            Assert.assertTrue(catching.verify());
            Assert.assertTrue(nonCatching.verify());
            GraphCache.Ref<ResolvedJavaMethod, StructuredGraph> normal = tree.createGraph(method, normalInvoke, false, null);
            try {
                Assert.assertSame(firstCatchesOOME ? second.readonly() : first.readonly(), normal.readonly());
            } finally {
                normal.release();
            }
        } finally {
            first.release();
            second.release();
        }
    }
}
