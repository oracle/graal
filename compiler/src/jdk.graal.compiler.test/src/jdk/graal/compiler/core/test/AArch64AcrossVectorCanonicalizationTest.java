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

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.vector.nodes.aarch64.AArch64AcrossVectorNode;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/** Tests constant folding without generating AArch64 machine code, on any host architecture. */
public class AArch64AcrossVectorCanonicalizationTest extends GraalCompilerTest {
    /** Checks the unsigned ordering and identity for every supported element width. */
    @Test
    public void unsignedMaximumBoundaries() {
        for (JavaKind kind : new JavaKind[]{JavaKind.Byte, JavaKind.Short, JavaKind.Int, JavaKind.Long}) {
            int bits = kind.getBitCount();
            long signedMin = 1L << (bits - 1);
            long signedMax = signedMin - 1;
            long unsignedMax = bits == Long.SIZE ? -1L : (1L << bits) - 1;
            assertUnsignedMaximum(kind, 0, 0, 0);
            assertUnsignedMaximum(kind, 4, 1, 4);
            assertUnsignedMaximum(kind, 4, 4, 1);
            assertUnsignedMaximum(kind, signedMax, 0, signedMax);
            assertUnsignedMaximum(kind, signedMin, signedMax, signedMin);
            assertUnsignedMaximum(kind, unsignedMax, signedMin, unsignedMax);
            assertUnsignedMaximum(kind, unsignedMax, unsignedMax, 0);
        }
    }

    /** Uses the scalar result representation chosen by the reduction node. */
    private static void assertUnsignedMaximum(JavaKind kind, long expected, long first, long second) {
        ConstantNode vector = SimdConstant.constantNodeForConstants(new JavaConstant[]{JavaConstant.forIntegerKind(kind, first), JavaConstant.forIntegerKind(kind, second)});
        AArch64AcrossVectorNode reduction = new AArch64AcrossVectorNode(AArch64AcrossVectorNode.Operation.UNSIGNED_MAX, kind.getBitCount(), vector);
        ValueNode folded = (ValueNode) reduction.canonical(null, vector);
        Assert.assertTrue(folded.isConstant());
        Assert.assertEquals(kind.toString(), kind == JavaKind.Long ? expected : (int) expected, folded.asJavaConstant().asLong());
    }
}
