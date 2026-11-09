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
package jdk.graal.compiler.core.test;

import org.junit.Test;

import jdk.graal.compiler.core.test.VerifyPhase.VerificationError;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PrimitiveConstant;

/// Tests rejection of raw shift constants while allowing the helper and unrelated constants.
public class VerifyShiftAmountsTest extends GraalCompilerTest {

    /// Supplies the direct extraction pattern that originally hid missing masks.
    public static int rawDistance(LeftShiftNode shift) {
        return shift.getY().asJavaConstant().asInt();
    }

    /// Supplies aliases of both the distance node and its Java constant.
    public static int aliasedDistance(ShiftNode<?> shift) {
        ValueNode distance = shift.getY();
        JavaConstant constant = distance.asJavaConstant();
        return constant.asInt();
    }

    /// Supplies extraction through the more general constant API and long arithmetic.
    public static long primitiveDistance(ShiftNode<?> shift) {
        return ((PrimitiveConstant) shift.getY().asConstant()).asLong();
    }

    /// Supplies a shift exposed by a type check on a generic value.
    public static int checkedDistance(ValueNode node) {
        if (node instanceof ShiftNode<?> shift) {
            return shift.getY().asJavaConstant().asInt();
        }
        return 0;
    }

    /// Supplies a distance read through the supported API.
    public static int maskedDistance(ShiftNode<?> shift) {
        return shift.getConstantShiftAmount();
    }

    /// Supplies a constant operand that does not have shift semantics.
    public static int addConstant(AddNode add) {
        return add.getY().asJavaConstant().asInt();
    }

    /// Supplies a constant shifted value, which must not be treated as the distance.
    public static long shiftedConstant(ShiftNode<?> shift) {
        return shift.getX().asJavaConstant().asLong();
    }

    /// Checks extraction before later optimizations can erase the calls inspected by the verifier.
    private void verify(String snippet) {
        StructuredGraph graph = parseEager(snippet, StructuredGraph.AllowAssumptions.NO);
        try (DebugCloseable _ = graph.getDebug().disableIntercept()) {
            new VerifyShiftAmounts().apply(graph, getDefaultHighTierContext());
        }
    }

    @Test(expected = VerificationError.class)
    public void testRawDistance() {
        verify("rawDistance");
    }

    @Test(expected = VerificationError.class)
    public void testAliasedDistance() {
        verify("aliasedDistance");
    }

    @Test(expected = VerificationError.class)
    public void testPrimitiveDistance() {
        verify("primitiveDistance");
    }

    @Test(expected = VerificationError.class)
    public void testCheckedDistance() {
        verify("checkedDistance");
    }

    /// Checks that unrelated operands do not trigger the verifier.
    @Test
    public void testAllowedConstants() {
        verify("maskedDistance");
        verify("addConstant");
        verify("shiftedConstant");
    }

    /// Checks the exemption for the implementation that applies the mask.
    @Test
    public void testHelperImplementation() {
        StructuredGraph graph = parseEager(getMetaAccess().lookupJavaMethod(getMethod(ShiftNode.class, "getConstantShiftAmount")), StructuredGraph.AllowAssumptions.NO);
        new VerifyShiftAmounts().apply(graph, getDefaultHighTierContext());
    }
}
