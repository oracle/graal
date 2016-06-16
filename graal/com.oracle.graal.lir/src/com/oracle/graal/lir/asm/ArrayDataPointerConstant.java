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
import java.nio.FloatBuffer;
import java.nio.DoubleBuffer;
import java.nio.LongBuffer;

import com.oracle.graal.code.DataSection.Data;
import com.oracle.graal.code.DataSection.RawData;
import com.oracle.graal.compiler.common.type.DataPointerConstant;
import com.oracle.graal.debug.GraalError;

import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.meta.Constant;

/**
 * Base class for {@link Constant constants} that represent a pointer to the data section.
 */
public class ArrayDataPointerConstant extends DataPointerConstant {

    public DataSectionReference dataRef;
    private CompilationResultBuilder crb;
    private int[] intArray;
    private float[] floatArray;
    private double[] doubleArray;
    private long[] longArray;
    private ByteBuffer byteBuffer;

    public enum ArrayType {
        INT_ARRAY,
        FLOAT_ARRAY,
        DOUBLE_ARRAY,
        LONG_ARRAY
    }

    private final ArrayType arrayType;

    public ArrayDataPointerConstant(int[] array, ArrayType arrayType, CompilationResultBuilder crb, int alignment) {
        super(alignment);
        this.crb = crb;
        this.arrayType = arrayType;
        this.intArray = array;
        this.byteBuffer = ByteBuffer.allocate(array.length * 4);
        serialize(byteBuffer);
    }

    public ArrayDataPointerConstant(float[] array, ArrayType arrayType, CompilationResultBuilder crb, int alignment) {
        super(alignment);
        this.crb = crb;
        this.arrayType = arrayType;
        this.floatArray = array;
        this.byteBuffer = ByteBuffer.allocate(array.length * 4);
        serialize(byteBuffer);
    }

    public ArrayDataPointerConstant(double[] array, ArrayType arrayType, CompilationResultBuilder crb, int alignment) {
        super(alignment);
        this.crb = crb;
        this.arrayType = arrayType;
        this.doubleArray = array;
        this.byteBuffer = ByteBuffer.allocate(array.length * 8);
        serialize(byteBuffer);
    }

    public ArrayDataPointerConstant(long[] array, ArrayType arrayType, CompilationResultBuilder crb, int alignment) {
        super(alignment);
        this.crb = crb;
        this.arrayType = arrayType;
        this.longArray = array;
        this.byteBuffer = ByteBuffer.allocate(array.length * 8);
        serialize(byteBuffer);
    }

    @Override
    public boolean isDefaultForKind() {
        return false;
    }

    @Override
    public void serialize(ByteBuffer buffer) {
        byteBuffer.order(ByteOrder.nativeOrder());
        switch(arrayType) {
            case INT_ARRAY:
                IntBuffer intBuffer = byteBuffer.asIntBuffer();
                intBuffer.put(intArray);
                break;
            case FLOAT_ARRAY:
                FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
                floatBuffer.put(floatArray);
                break;
            case DOUBLE_ARRAY:
                DoubleBuffer doubleBuffer = byteBuffer.asDoubleBuffer();
                doubleBuffer.put(doubleArray);
                break;
            case LONG_ARRAY:
                LongBuffer longBuffer = byteBuffer.asLongBuffer();
                longBuffer.put(longArray);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        byte[] rawBytes = byteBuffer.array();
        Data data = new RawData(rawBytes, getAlignment());
        dataRef = crb.compilationResult.getDataSection().insertData(data);
    }

    @Override
    public int getSerializedSize() {
        switch(arrayType) {
            case INT_ARRAY:
                return intArray.length * 4;
            case FLOAT_ARRAY:
                return floatArray.length * 4;
            case DOUBLE_ARRAY:
                return doubleArray.length * 8;
            case LONG_ARRAY:
                return longArray.length * 8;   
            default:
                throw GraalError.shouldNotReachHere();
        }
    }

    @Override
    public String toValueString() {
        return "no context value available";
    }
}
