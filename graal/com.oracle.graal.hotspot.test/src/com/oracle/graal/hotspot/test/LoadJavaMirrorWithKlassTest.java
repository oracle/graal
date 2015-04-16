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
package com.oracle.graal.hotspot.test;

import java.util.*;

import org.junit.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.options.*;
import com.oracle.graal.options.OptionValue.OverrideScope;
import com.oracle.graal.phases.tiers.*;

public class LoadJavaMirrorWithKlassTest extends GraalCompilerTest {

    private static class Wrapper {
        private Class<?> clazz;

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Wrapper) {
                return Objects.equals(this.clazz, ((Wrapper) obj).clazz);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return clazz.hashCode();
        }
    }

    @Override
    protected Suites createSuites() {
        try (OverrideScope s = OptionValue.override(GraalOptions.ImmutableCode, true)) {
            return super.createSuites();
        }
    }

    @Override
    protected boolean checkLowTierGraph(StructuredGraph graph) {
        for (ConstantNode constantNode : graph.getNodes().filter(ConstantNode.class)) {
            assert constantNode.asJavaConstant() == null || constantNode.asJavaConstant().getKind() != Kind.Object : "Found unexpected object constant " + constantNode +
                            ", this should have been removed by the LoadJavaMirrorWithKlassPhase.";
        }
        return true;
    }

    public static Class<?> classConstant() {
        return Wrapper.class;
    }

    @Test
    public void testClassConstant() {
        test("classConstant");
    }

    public static Class<?> primitiveClassConstant() {
        return int.class;
    }

    @Test
    public void testPrimitiveClassConstant() {
        test("primitiveClassConstant");
    }

    public static Wrapper compressedClassConstant(Wrapper w) {
        w.clazz = Wrapper.class;
        return w;
    }

    @Test
    public void testCompressedClassConstant() {
        ArgSupplier arg = () -> new Wrapper();
        test("compressedClassConstant", arg);
    }

    public static Wrapper compressedPrimitiveClassConstant(Wrapper w) {
        w.clazz = int.class;
        return w;
    }

    @Test
    public void testCompressedPrimitiveClassConstant() {
        ArgSupplier arg = () -> new Wrapper();
        test("compressedPrimitiveClassConstant", arg);
    }
}
