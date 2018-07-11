/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.pelang.expr;

import java.lang.reflect.Array;

import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;

@NodeField(name = "arrayType", type = PELangArrayType.class)
@NodeChild("dimensionsNode")
public abstract class PELangNewArrayNode extends PELangExpressionNode {

    protected abstract PELangArrayType getArrayType();

    @Specialization
    public Object newSingleArray(long dimension) {
        return newArray(getArrayType().getJavaClass(), (int) dimension);
    }

    @Specialization
    public Object newMultiArray(long[] dimensions) {
        return newArray(getArrayType().getJavaClass(), toIntArray(dimensions));
    }

    private static int[] toIntArray(long[] longs) {
        int[] ints = new int[longs.length];

        for (int i = 0; i < longs.length; i++) {
            ints[i] = (int) longs[i];
        }
        return ints;
    }

    @TruffleBoundary
    private static Object newArray(Class<?> type, int... dimensions) {
        return Array.newInstance(type, dimensions);
    }

    public static PELangNewArrayNode createNode(PELangArrayType arrayType, PELangExpressionNode dimensionsNode) {
        return PELangNewArrayNodeGen.create(dimensionsNode, arrayType);
    }

}
