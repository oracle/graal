/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.asm;

import java.nio.ByteBuffer;

import org.graalvm.compiler.code.DataSection;
import org.graalvm.compiler.code.DataSection.Data;
import org.graalvm.compiler.code.DataSection.Patches;
import org.graalvm.compiler.code.DataSection.ZeroData;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.SerializableConstant;

public abstract class DataBuilder {
    public abstract Data createDataItem(Constant c);

    public Data createSerializableData(SerializableConstant constant, int alignment) {
        assert canForceAlignmentOf(alignment);
        return new DataSection.SerializableData(constant, alignment);
    }

    public Data createSerializableData(SerializableConstant constant) {
        return createSerializableData(constant, 1);
    }

    public Data createZeroData(int alignment, int size) {
        assert canForceAlignmentOf(alignment);
        switch (size) {
            case 1:
                return new ZeroData(alignment, size) {
                    @Override
                    protected void emit(ByteBuffer buffer, Patches patches) {
                        buffer.put((byte) 0);
                    }
                };

            case 2:
                return new ZeroData(alignment, size) {
                    @Override
                    protected void emit(ByteBuffer buffer, Patches patches) {
                        buffer.putShort((short) 0);
                    }
                };

            case 4:
                return new ZeroData(alignment, size) {
                    @Override
                    protected void emit(ByteBuffer buffer, Patches patches) {
                        buffer.putInt(0);
                    }
                };

            case 8:
                return new ZeroData(alignment, size) {
                    @Override
                    protected void emit(ByteBuffer buffer, Patches patches) {
                        buffer.putLong(0);
                    }
                };

            default:
                return new ZeroData(alignment, size);
        }
    }

    public Data createPackedDataItem(int alignment, int size, Data[] nested) {
        assert canForceAlignmentOf(alignment);
        return new DataSection.PackedData(alignment, size, nested);
    }

    public Data createPackedDataItem(Data[] nested) {
        int size = 0;
        int alignment = 1;
        for (int i = 0; i < nested.length; i++) {
            assert size % nested[i].getAlignment() == 0 : "invalid alignment in packed constants";
            alignment = DataSection.lcm(alignment, nested[i].getAlignment());
            size += nested[i].getSize();
        }
        return createPackedDataItem(alignment, size, nested);
    }

    public Data createMultiDataItem(Constant... constants) {
        assert constants.length > 0;
        if (constants.length == 1) {
            return createDataItem(constants[0]);
        } else {
            Data[] data = new Data[constants.length];
            for (int i = 0; i < constants.length; i++) {
                data[i] = createDataItem(constants[i]);
            }
            return createPackedDataItem(data);
        }
    }

    /**
     * Gets the alignment supported by the runtime for {@code requestedAlignment}. The returned
     * value will always be equal to or less than {@code requestedAlignment}.
     *
     * @param requestedAlignment The requested data alignment (in bytes)
     * @return an alignment that is supported by the runtime
     */
    public int ensureValidDataAlignment(int requestedAlignment) {
        return Math.min(requestedAlignment, getMaxSupportedAlignment());
    }

    /**
     * Updates the alignment of the given data element and ensures that it is actually supported by
     * the runtime.
     *
     * @param data The data
     * @param newAlignment The new alignment
     */
    public void updateAlignment(Data data, int newAlignment) {
        assert canForceAlignmentOf(newAlignment);
        data.updateAlignment(newAlignment);
    }

    /**
     * @return the maximum alignment that is supported by the runtime
     */
    public int getMaxSupportedAlignment() {
        return Integer.MAX_VALUE;
    }

    /**
     * Determines whether a data constant can be forced to be aligned by {@code sizeInBytes}.
     *
     * @param sizeInBytes The requested alignment
     */
    public boolean canForceAlignmentOf(int sizeInBytes) {
        return sizeInBytes <= getMaxSupportedAlignment();
    }
}
