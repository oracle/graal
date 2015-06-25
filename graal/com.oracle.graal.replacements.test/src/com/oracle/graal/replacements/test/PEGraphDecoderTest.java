/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.test;

import jdk.internal.jvmci.debug.*;
import jdk.internal.jvmci.meta.*;

import org.junit.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.memory.HeapAccess.BarrierType;
import com.oracle.graal.nodes.memory.*;
import com.oracle.graal.nodes.memory.address.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.replacements.*;

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
        r.register2("readInt", Object.class, long.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode obj, ValueNode offset) {
                AddressNode address = b.add(new OffsetAddressNode(obj, offset));
                ReadNode read = b.addPush(Kind.Int, new ReadNode(address, LocationIdentity.any(), StampFactory.forKind(Kind.Int), BarrierType.NONE));
                read.setGuard(AbstractBeginNode.prevBegin(read));
                return true;
            }
        });
    }

    class InlineAll implements InlineInvokePlugin {
        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args, JavaType returnType) {
            return new InlineInfo(method, false);
        }
    }

    @Test
    public void test() {
        ResolvedJavaMethod testMethod = getResolvedJavaMethod(PEGraphDecoderTest.class, "doTest", Object.class);
        StructuredGraph targetGraph = null;
        try (Debug.Scope scope = Debug.scope("GraphPETest", testMethod)) {
            GraphBuilderConfiguration graphBuilderConfig = GraphBuilderConfiguration.getEagerDefault(getDefaultGraphBuilderPlugins());
            registerPlugins(graphBuilderConfig.getPlugins().getInvocationPlugins());
            CachingPEGraphDecoder decoder = new CachingPEGraphDecoder(getProviders(), graphBuilderConfig, OptimisticOptimizations.NONE, AllowAssumptions.YES, getTarget().arch);

            targetGraph = new StructuredGraph(testMethod, AllowAssumptions.YES);
            decoder.decode(targetGraph, testMethod, null, null, new InlineInvokePlugin[]{new InlineAll()}, null);
            Debug.dump(targetGraph, "Target Graph");
            targetGraph.verify();

            PhaseContext context = new PhaseContext(getProviders());
            new CanonicalizerPhase().apply(targetGraph, context);
            targetGraph.verify();

        } catch (Throwable ex) {
            if (targetGraph != null) {
                Debug.dump(targetGraph, ex.toString());
            }
            Debug.handle(ex);
        }
    }
}
