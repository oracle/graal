/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import static org.junit.Assume.assumeTrue;

import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

public class HotSpotCompressedKlassPointerTest extends HotSpotGraalCompilerTest {

    @Before
    public void setUp() {
        GraalHotSpotVMConfig config = runtime().getVMConfig();
        assumeTrue("compressed class pointers specific tests", config.useCompressedClassPointers && !config.useClassMetaspaceForAllClasses);
    }

    // Non-abstract class
    public static boolean instanceOfInteger(Object o) {
        return o instanceof Integer;
    }

    // Abstract class
    public static boolean instanceOfNumber(Object o) {
        return o instanceof Number;
    }

    // Interface
    public static boolean instanceOfComparable(Object o) {
        return o instanceof Comparable;
    }

    // Array of non-abstract class
    public static boolean instanceOfIntegerArray(Object o) {
        return o instanceof Integer[];
    }

    // Array of abstract class
    public static boolean instanceOfNumberArray(Object o) {
        return o instanceof Number[];
    }

    // Array of interface
    public static boolean instanceOfComparableArray(Object o) {
        return o instanceof Comparable[];
    }

    private void assertNoCompressedInterfaceOrAbstractClassConstant(String methodName) {
        StructuredGraph graph = getFinalGraph(methodName);
        for (ConstantNode constantNode : graph.getNodes().filter(ConstantNode.class)) {
            if (constantNode.asConstant() instanceof HotSpotMetaspaceConstant mc && mc.isCompressed()) {
                ResolvedJavaType type = mc.asResolvedJavaType();
                assertFalse(!type.isArray() && (type.isInterface() || type.isAbstract()), "As of JDK-8338526, interface and abstract types are not compressible.");
            }
        }
    }

    @Test
    public void testInstanceOf() {
        assertNoCompressedInterfaceOrAbstractClassConstant("instanceOfInteger");
        assertNoCompressedInterfaceOrAbstractClassConstant("instanceOfNumber");
        assertNoCompressedInterfaceOrAbstractClassConstant("instanceOfComparable");
        assertNoCompressedInterfaceOrAbstractClassConstant("instanceOfIntegerArray");
        assertNoCompressedInterfaceOrAbstractClassConstant("instanceOfNumberArray");
        assertNoCompressedInterfaceOrAbstractClassConstant("instanceOfComparableArray");
    }
}
