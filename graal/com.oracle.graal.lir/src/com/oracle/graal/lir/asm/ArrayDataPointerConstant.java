/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.asm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import com.oracle.graal.code.DataSection.Data;
import com.oracle.graal.code.DataSection.RawData;
import com.oracle.graal.compiler.common.type.DataPointerConstant;

import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.meta.Constant;

/**
 * Base class for {@link Constant constants} that represent a pointer to the data section.
 */
public class ArrayDataPointerConstant extends DataPointerConstant {

    public DataSectionReference dataRef;
    public CompilationResultBuilder crb;
    private int[] array;
    private ByteBuffer byteBuffer;

    // TODO: add other base type array support as needed
    public ArrayDataPointerConstant(int[] array, CompilationResultBuilder crb, int alignment) {
        super(alignment);
        this.crb = crb;
        this.array = array;
        this.byteBuffer = ByteBuffer.allocate(array.length * 4);
        serialize(byteBuffer);
    }

    @Override
    public boolean isDefaultForKind() {
        return false;
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        byteBuffer.order(ByteOrder.nativeOrder());
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(array);
        byte[] rawBytes = byteBuffer.array();
        Data data = new RawData(rawBytes, getAlignment());
        dataRef = crb.compilationResult.getDataSection().insertData(data);
    }

    @Override
    public int getSerializedSize() {
        return array.length * 4;
    }

    @Override
    public String toValueString() {
        return "no context value available";
    }
}
