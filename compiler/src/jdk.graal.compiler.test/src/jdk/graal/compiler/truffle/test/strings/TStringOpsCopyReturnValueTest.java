/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test.strings;

import java.lang.reflect.InvocationTargetException;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.replacements.nodes.ArrayCopyWithConversionsNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class TStringOpsCopyReturnValueTest extends TStringOpsTest<ArrayCopyWithConversionsNode> {

    public TStringOpsCopyReturnValueTest() {
        super(ArrayCopyWithConversionsNode.class);
    }

    @Test
    public void testMethodReturnValues() throws InvocationTargetException, IllegalAccessException, InstantiationException {
        ResolvedJavaMethod method = getArrayCopyWithStride();
        byte[] arrayA = new byte[4];
        byte[] arrayB = new byte[4];
        for (int strideA = 0; strideA < 3; strideA++) {
            for (int strideB = 0; strideB < 3; strideB++) {
                Assert.assertSame(arrayB, invoke(method, null, DUMMY_LOCATION, arrayA, 0, strideA, 0, arrayB, 0, strideB, 0, 1));
            }
        }
        ResolvedJavaMethod methodCB = getArrayCopyWithStrideCB();
        for (int strideB = 0; strideB < 3; strideB++) {
            Assert.assertSame(arrayB, invoke(methodCB, null, DUMMY_LOCATION, new char[1], 0, arrayB, 0, strideB, 1));
        }
        ResolvedJavaMethod methodIB = getArrayCopyWithStrideIB();
        for (int strideB = 0; strideB < 3; strideB++) {
            Assert.assertSame(arrayB, invoke(methodIB, null, DUMMY_LOCATION, new int[1], 0, arrayB, 0, strideB, 1));
        }
    }
}
