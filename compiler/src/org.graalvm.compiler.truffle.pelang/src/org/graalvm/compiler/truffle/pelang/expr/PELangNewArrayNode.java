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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public final class PELangNewArrayNode extends PELangExpressionNode {

    private final Class<?> type;
    @Child private PELangExpressionNode dimensionsNode;

    public PELangNewArrayNode(Class<?> type, PELangExpressionNode dimensionsNode) {
        this.type = type;
        this.dimensionsNode = dimensionsNode;
    }

    public Class<?> getType() {
        return type;
    }

    public PELangExpressionNode getDimensionsNode() {
        return dimensionsNode;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        long[] longs = dimensionsNode.evaluateLongArray(frame);
        int[] dimensions = toIntArray(longs);
        return newArray(type, dimensions);
    }

    @Override
    public Object executeArray(VirtualFrame frame) throws UnexpectedResultException {
        return executeGeneric(frame);
    }

    private static int[] toIntArray(long[] longs) {
        int[] ints = new int[longs.length];

        for (int i = 0; i < longs.length; i++) {
            ints[i] = (int) longs[i];
        }
        return ints;
    }

    @TruffleBoundary
    private static Object newArray(Class<?> type, int[] dimensions) {
        return Array.newInstance(type, dimensions);
    }

}
