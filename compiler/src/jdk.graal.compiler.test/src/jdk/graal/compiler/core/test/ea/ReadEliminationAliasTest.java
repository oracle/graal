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
package jdk.graal.compiler.core.test.ea;

import java.io.Serializable;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationBlockState;
import jdk.vm.ci.meta.JavaKind;

/// Tests the array type overlap proof used by indexed read elimination.
public class ReadEliminationAliasTest extends GraalCompilerTest {

    /// Supplies distinct graph values for two potentially identical arrays and their index.
    public static Object arrayAccess(Object[] left, Object[] right, int index) {
        return index == 0 ? left[index] : right[index];
    }

    /// Checks overlapping interface arrays and disjoint concrete arrays with nullable stamps too.
    @Test
    public void testArrayTypeOverlap() {
        String[][] witness = {{"value"}};
        Cloneable[] left = witness;
        Serializable[] right = witness;
        Assert.assertSame(left, right);
        for (boolean nonNull : new boolean[]{false, true}) {
            assertAliasing(Cloneable[].class, Serializable[].class, nonNull, true);
            assertAliasing(Cloneable[][].class, Serializable[][].class, nonNull, true);
            assertAliasing(Object[].class, String[].class, nonNull, true);
            assertAliasing(String[].class, Integer[].class, nonNull, false);
        }
    }

    /// Checks the optional type proof while preserving the conservative overload's behavior.
    private void assertAliasing(Class<?> leftType, Class<?> rightType, boolean nonNull, boolean expected) {
        StructuredGraph graph = parseEager("arrayAccess", AllowAssumptions.YES);
        graph.getParameter(0).setStamp(new ObjectStamp(getMetaAccess().lookupJavaType(leftType), false, nonNull, false, true));
        graph.getParameter(1).setStamp(new ObjectStamp(getMetaAccess().lookupJavaType(rightType), false, nonNull, false, true));
        var location = NamedLocationIdentity.getArrayLocation(JavaKind.Object);
        assertTrue(ReadEliminationBlockState.potentiallyAliasingIndexedAccesses(location, location,
                        graph.getParameter(0), graph.getParameter(1), graph.getParameter(2), graph.getParameter(2)));
        Assert.assertEquals(expected, ReadEliminationBlockState.potentiallyAliasingIndexedAccesses(location, location,
                        graph.getParameter(0), graph.getParameter(1), graph.getParameter(2), graph.getParameter(2), getMetaAccess()));
    }
}
