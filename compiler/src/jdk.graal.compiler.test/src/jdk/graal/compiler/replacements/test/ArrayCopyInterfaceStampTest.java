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
package jdk.graal.compiler.replacements.test;

import java.io.Serializable;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.hotspot.nodes.VMErrorNode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

public class ArrayCopyInterfaceStampTest extends GraalCompilerTest {

    public static void copySerializable(Serializable source, Object destination) {
        System.arraycopy(source, 0, destination, 0, 1);
    }

    public static void copyCloneable(Cloneable source, Object destination) {
        System.arraycopy(source, 0, destination, 0, 1);
    }

    public static void copyAfterSerializableCheck(Object source, Object destination) {
        if (source instanceof Serializable) {
            System.arraycopy(source, 0, destination, 0, 1);
        }
    }

    public static void copyAfterCloneableCheck(Object source, Object destination) {
        if (source instanceof Cloneable) {
            System.arraycopy(source, 0, destination, 0, 1);
        }
    }

    public static void copyAfterSerializableDestinationCheck(Object source, Object destination) {
        if (destination instanceof Serializable) {
            System.arraycopy(source, 0, destination, 0, 1);
        }
    }

    public static void copyAfterCloneableDestinationCheck(Object source, Object destination) {
        if (destination instanceof Cloneable) {
            System.arraycopy(source, 0, destination, 0, 1);
        }
    }

    private void assertCopies(String method) throws InvalidInstalledCodeException {
        InstalledCode code = getCode(getResolvedJavaMethod(method), null, true);
        // Reject the incorrect always-throwing lowering before executing its unreachable return.
        Assert.assertEquals(0, lastCompiledGraph.getNodes().filter(VMErrorNode.class).count());
        Object[] destination = new Object[1];
        code.executeVarargs(new String[]{"copied"}, destination);
        Assert.assertArrayEquals(new Object[]{"copied"}, destination);
        int[] primitiveDestination = new int[1];
        code.executeVarargs(new int[]{42}, primitiveDestination);
        Assert.assertArrayEquals(new int[]{42}, primitiveDestination);
    }

    @Test
    public void testInterfaceParameters() throws InvalidInstalledCodeException {
        assertCopies("copySerializable");
        assertCopies("copyCloneable");
    }

    @Test
    public void testSourceRefinement() throws InvalidInstalledCodeException {
        assertCopies("copyAfterSerializableCheck");
        assertCopies("copyAfterCloneableCheck");
    }

    @Test
    public void testDestinationRefinement() throws InvalidInstalledCodeException {
        assertCopies("copyAfterSerializableDestinationCheck");
        assertCopies("copyAfterCloneableDestinationCheck");
    }

    @Test
    public void testInvalidInputs() {
        test("copyAfterSerializableCheck", "not an array", new Object[1]);
        test("copyAfterCloneableCheck", new Cloneable() {
        }, new Object[1]);
        test("copyAfterSerializableDestinationCheck", new Object[1], "not an array");
        test("copyAfterCloneableDestinationCheck", new Object[1], new Cloneable() {
        });
        test("copyAfterSerializableCheck", new int[1], new Object[1]);
        test("copyAfterCloneableCheck", new Object[1], new int[1]);
        test("copyAfterSerializableCheck", new Object[1], null);
        test("copyAfterCloneableDestinationCheck", null, new Object[1]);
    }
}
