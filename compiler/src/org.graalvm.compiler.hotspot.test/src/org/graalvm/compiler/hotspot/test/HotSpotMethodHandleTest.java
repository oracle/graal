/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.nodes.HotSpotDirectCallTargetNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HotSpotMethodHandleTest extends HotSpotGraalCompilerTest {

    @Before
    public void checkMHDeoptSupport() {
        Assume.assumeTrue(runtime().getVMConfig().supportsMethodHandleDeoptimizationEntry());
    }

    @BytecodeParserNeverInline
    public void doNothing() {
    }

    public static final MethodHandle DO_NOTHING;

    static {
        try {
            DO_NOTHING = MethodHandles.lookup().findVirtual(HotSpotMethodHandleTest.class, "doNothing", MethodType.methodType(void.class));
        } catch (Throwable e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    public void invokeDoNothing() throws Throwable {
        DO_NOTHING.invoke(this);
    }

    @Test
    public void testInvokeDoNothing() {
        ((HotSpotResolvedJavaMethod) getResolvedJavaMethod("doNothing")).setNotInlinableOrCompilable();
        test("invokeDoNothing");
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        assertTrue("Final graph should contain a call to MethodHandle.linkToVirtual", containsCallTo(graph, getMetaAccess().lookupJavaType(MethodHandle.class), "linkToVirtual"));
    }

    protected static boolean containsCallTo(StructuredGraph graph, JavaType enclosingClass, String methodName) {
        for (HotSpotDirectCallTargetNode callTargetNode : graph.getNodes().filter(HotSpotDirectCallTargetNode.class)) {
            ResolvedJavaMethod targetMethod = callTargetNode.targetMethod();
            if (enclosingClass.equals(targetMethod.getDeclaringClass()) && methodName.equals(targetMethod.getName())) {
                return true;
            }
        }
        return false;
    }
}
