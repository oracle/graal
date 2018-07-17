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

import org.graalvm.compiler.truffle.pelang.PELangException;
import org.graalvm.compiler.truffle.pelang.PELangExpressionNode;
import org.graalvm.compiler.truffle.pelang.util.ArrayUtil;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;

@NodeChildren({@NodeChild("arrayNode"), @NodeChild("indicesNode")})
public abstract class PELangReadArrayNode extends PELangExpressionNode {

    @Specialization(guards = "array.getClass().isArray()")
    public Object readSingleArray(Object array, long index) {
        try {
            return ArrayUtil.readValue(array, (int) index);
        } catch (ArrayIndexOutOfBoundsException e) {
            CompilerDirectives.transferToInterpreter();
            throw new PELangException("index out of bounds", this);
        }
    }

    @Specialization(guards = "array.getClass().isArray()")
    public Object readMultiArray(Object array, long[] indices) {
        try {
            return ArrayUtil.unwrapArray(array, indices, indices.length);
        } catch (ArrayIndexOutOfBoundsException e) {
            CompilerDirectives.transferToInterpreter();
            throw new PELangException("index out of bounds", this);
        }
    }

    public static PELangReadArrayNode createNode(PELangExpressionNode arrayNode, PELangExpressionNode indicesNode) {
        return PELangReadArrayNodeGen.create(arrayNode, indicesNode);
    }

}
