/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.hotspot.ri;

import sun.misc.*;

import com.oracle.max.cri.ri.*;
import com.oracle.max.graal.hotspot.*;
import com.oracle.max.graal.hotspot.Compiler;


public final class HotSpotMethodData extends CompilerObject {

    /**
     *
     */
    private static final long serialVersionUID = -8873133496591225071L;
    // TODO (ch) use same logic as in NodeClass?
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final HotSpotMethodDataAccessor NO_DATA_ACCESSOR = new NoDataAccessor();
    private static final HotSpotMethodDataAccessor[] PROFILE_DATA_ACCESSORS = {
        null, new BitData(), new CounterData(), new JumpData(),
        new TypeCheckData(), new VirtualCallData(), new RetData(),
        new BranchData(), new MultiBranchData(), new ArgInfoData()
    };

    private Object javaMirror;
    private int normalDataSize;
    private int extraDataSize;

    // TODO (ch) how are we going to handle methodData->is_mature()

    private HotSpotMethodData(Compiler compiler) {
        super(compiler);
        throw new IllegalStateException("this constructor is never actually called, because the objects are allocated from within the VM");
    }

    public boolean hasNormalData() {
        return normalDataSize > 0;
    }

    public boolean hasExtraData() {
        return extraDataSize > 0;
    }

    public boolean isWithinData(int position) {
        return position >= 0 && position < normalDataSize + extraDataSize;
    }

    public HotSpotMethodDataAccessor getNormalData(int position) {
        if (position >= normalDataSize) {
            return null;
        }

        HotSpotMethodDataAccessor result = getData(position, 0);
        assert result != null : "NO_DATA tag is not allowed";
        return result;
    }

    public HotSpotMethodDataAccessor getExtraData(int position) {
        if (position >= extraDataSize) {
            return null;
        }
        return getData(position, normalDataSize);
    }

    public static HotSpotMethodDataAccessor getNoDataAccessor() {
        return NO_DATA_ACCESSOR;
    }

    private HotSpotMethodDataAccessor getData(int position, int displacement) {
        assert position >= 0 : "out of bounds";
        int tag = AbstractMethodDataAccessor.readTag(this, displacement + position);
        assert tag >= 0 && tag < PROFILE_DATA_ACCESSORS.length : "illegal tag";
        return PROFILE_DATA_ACCESSORS[tag];
    }

    private int readUnsignedByte(int position, int offsetInCells) {
        long offsetInBytes = computeOffsetInBytes(position, offsetInCells);
        return unsafe.getByte(javaMirror, offsetInBytes) & 0xFF;
    }

    private int readUnsignedShort(int position, int offsetInCells) {
        long offsetInBytes = computeOffsetInBytes(position, offsetInCells);
        return unsafe.getShort(javaMirror, offsetInBytes) & 0xFFFF;
    }

    private long readUnsignedInt(int position, int offsetInCells) {
        long offsetInBytes = computeOffsetInBytes(position, offsetInCells);
        return unsafe.getInt(javaMirror, offsetInBytes) & 0xFFFFFFFFL;
    }

    private int readUnsignedIntAsSignedInt(int position, int offsetInCells) {
        long value = readUnsignedInt(position, offsetInCells);
        return truncateLongToInt(value);
    }

    private int readInt(int position, int offsetInCells) {
        long offsetInBytes = computeOffsetInBytes(position, offsetInCells);
        return unsafe.getInt(javaMirror, offsetInBytes);
    }

    private Object readObject(int position, int offsetInCells) {
        long offsetInBytes = computeOffsetInBytes(position, offsetInCells);
        return unsafe.getObject(javaMirror, offsetInBytes);
    }

    private static int truncateLongToInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static int computeOffsetInBytes(int position, int offsetInCells) {
        HotSpotVMConfig config = getHotSpotVMConfig();
        return config.methodDataOopDataOffset + position + offsetInCells * config.dataLayoutCellSize;
    }

    private static HotSpotVMConfig getHotSpotVMConfig() {
        return CompilerImpl.getInstance().getConfig();
    }

    private abstract static class AbstractMethodDataAccessor implements HotSpotMethodDataAccessor {
        private static final int IMPLICIT_EXCEPTIONS_MASK = 0x0E;

        private final int tag;
        private final int staticCellCount;

        protected AbstractMethodDataAccessor(int tag, int staticCellCount) {
            this.tag = tag;
            this.staticCellCount = staticCellCount;
        }

        @Override
        public int getTag() {
            return tag;
        }

        public static int readTag(HotSpotMethodData data, int position) {
            HotSpotVMConfig config = getHotSpotVMConfig();
            return data.readUnsignedByte(position, config.dataLayoutTagOffset);
        }

        @Override
        public int getBCI(HotSpotMethodData data, int position) {
            HotSpotVMConfig config = getHotSpotVMConfig();
            return data.readUnsignedShort(position, config.dataLayoutBCIOffset);
        }

        @Override
        public int getSize(HotSpotMethodData data, int position) {
            HotSpotVMConfig config = getHotSpotVMConfig();
            return config.dataLayoutHeaderSize + (staticCellCount + getDynamicCellCount(data, position)) * config.dataLayoutCellSize;
        }

        @Override
        public boolean getImplicitExceptionSeen(HotSpotMethodData data, int position) {
            // TODO (ch) might return true too often because flags are also used for deoptimization reasons
            return (getFlags(data, position) & IMPLICIT_EXCEPTIONS_MASK) != 0;
        }

        @Override
        public RiTypeProfile getTypeProfile(HotSpotMethodData data, int position) {
            return null;
        }

        @Override
        public double getBranchTakenProbability(HotSpotMethodData data, int position) {
            return -1;
        }

        @Override
        public double[] getSwitchProbabilities(HotSpotMethodData data, int position) {
            return null;
        }

        @Override
        public int getExecutionCount(HotSpotMethodData data, int position) {
            return -1;
        }

        protected int getFlags(HotSpotMethodData data, int position) {
            HotSpotVMConfig config = getHotSpotVMConfig();
            return data.readUnsignedByte(position, config.dataLayoutFlagsOffset);
        }

        protected int getDynamicCellCount(@SuppressWarnings("unused") HotSpotMethodData data, @SuppressWarnings("unused") int position) {
            return 0;
        }
    }

    private static class NoDataAccessor extends AbstractMethodDataAccessor {
        private static final int NO_DATA_TAG = 0;
        private static final int NO_DATA_SIZE = 0;

        protected NoDataAccessor() {
            super(NO_DATA_TAG, NO_DATA_SIZE);
        }

        @Override
        public int getBCI(HotSpotMethodData data, int position) {
            return -1;
        }


        @Override
        public boolean getImplicitExceptionSeen(HotSpotMethodData data, int position) {
            return false;
        }
    }

    private static class BitData extends AbstractMethodDataAccessor {
        private static final int BIT_DATA_TAG = 1;
        private static final int BIT_DATA_CELLS = 0;
        private static final int BIT_DATA_NULL_SEEN_FLAG = 0x01;

        private BitData() {
            super(BIT_DATA_TAG, BIT_DATA_CELLS);
        }

        protected BitData(int tag, int staticCellCount) {
            super(tag, staticCellCount);
        }

        public boolean getNullSeen(HotSpotMethodData data, int position) {
            return (getFlags(data, position) & BIT_DATA_NULL_SEEN_FLAG) != 0;
        }
    }

    private static class CounterData extends BitData {
        private static final int COUNTER_DATA_TAG = 2;
        private static final int COUNTER_DATA_CELLS = 1;
        private static final int COUNTER_DATA_COUNT_OFFSET = 0;

        public CounterData() {
            super(COUNTER_DATA_TAG, COUNTER_DATA_CELLS);
        }

        protected CounterData(int tag, int staticCellCount) {
            super(tag, staticCellCount);
        }

        @Override
        public int getExecutionCount(HotSpotMethodData data, int position) {
            return getCounterValue(data, position);
        }

        protected int getCounterValue(HotSpotMethodData data, int position) {
            return data.readUnsignedIntAsSignedInt(position, COUNTER_DATA_COUNT_OFFSET);
        }
    }

    private static class JumpData extends AbstractMethodDataAccessor {
        private static final int JUMP_DATA_TAG = 3;
        private static final int JUMP_DATA_CELLS = 2;
        protected static final int TAKEN_COUNT_OFFSET = 0;
        protected static final int TAKEN_DISPLACEMENT_OFFSET = 1;

        public JumpData() {
            super(JUMP_DATA_TAG, JUMP_DATA_CELLS);
        }

        protected JumpData(int tag, int staticCellCount) {
            super(tag, staticCellCount);
        }

        @Override
        public double getBranchTakenProbability(HotSpotMethodData data, int position) {
            return 1;
        }

        @Override
        public int getExecutionCount(HotSpotMethodData data, int position) {
            return data.readUnsignedIntAsSignedInt(position, TAKEN_COUNT_OFFSET);
        }

        public int getDisplacement(HotSpotMethodData data, int position) {
            return data.readInt(position, TAKEN_DISPLACEMENT_OFFSET);
        }
    }

    private static class AbstractTypeData extends CounterData {
        private static final int RECEIVER_TYPE_DATA_ROW_CELL_COUNT = 2;
        private static final int RECEIVER_TYPE_DATA_FIRST_RECEIVER_OFFSET = 1;
        private static final int RECEIVER_TYPE_DATA_FIRST_COUNT_OFFSET = 2;

        protected AbstractTypeData(int tag, int staticCellCount) {
            super(tag, staticCellCount);
        }

        @Override
        public RiTypeProfile getTypeProfile(HotSpotMethodData data, int position) {
            HotSpotVMConfig config = getHotSpotVMConfig();
            int typeProfileWidth = config.typeProfileWidth;

            RiResolvedType[] sparseTypes = new RiResolvedType[typeProfileWidth];
            double[] counts = new double[typeProfileWidth];
            long totalCount = 0;
            int entries = 0;

            for (int i = 0; i < typeProfileWidth; i++) {
                Object receiverKlassOop = data.readObject(position, getReceiverOffset(i));
                if (receiverKlassOop != null) {
                    Object graalMirror = unsafe.getObject(receiverKlassOop, (long) config.klassOopGraalMirrorOffset);
                    if (graalMirror == null) {
                        Class<?> javaClass = (Class<?>) unsafe.getObject(receiverKlassOop, (long) config.classMirrorOffset);
                        graalMirror = CompilerImpl.getInstance().getVMEntries().getType(javaClass);
                        assert graalMirror != null : "must not return null";
                    }

                    long count = data.readUnsignedInt(position, getCountOffset(i));
                    if (count > 0) {
                        totalCount += count;
                        counts[entries] = count;
                        entries++;
                    }

                    sparseTypes[i] = (RiResolvedType) graalMirror;
                }
            }

            RiResolvedType[] types;
            double[] probabilities;

            if (entries <= 0) {
                return null;
            } else if (entries < typeProfileWidth) {
                RiResolvedType[] compactedTypes = new RiResolvedType[entries];
                System.arraycopy(sparseTypes, 0, compactedTypes, 0, entries);
                types = compactedTypes;
                probabilities = new double[entries];
            } else {
                types = sparseTypes;
                probabilities = counts;
            }

            for (int i = 0; i < typeProfileWidth; i++) {
                probabilities[i] = counts[i] / totalCount;
            }
            return new RiTypeProfile(types, probabilities);
        }

        @Override
        protected int getDynamicCellCount(HotSpotMethodData data, int position) {
            HotSpotVMConfig config = getHotSpotVMConfig();
            return config.typeProfileWidth * RECEIVER_TYPE_DATA_ROW_CELL_COUNT;
        }

        private static int getReceiverOffset(int row) {
            return RECEIVER_TYPE_DATA_FIRST_RECEIVER_OFFSET + row * RECEIVER_TYPE_DATA_ROW_CELL_COUNT;
        }

        protected static int getCountOffset(int row) {
            return RECEIVER_TYPE_DATA_FIRST_COUNT_OFFSET + row * RECEIVER_TYPE_DATA_ROW_CELL_COUNT;
        }
    }

    private static class TypeCheckData extends AbstractTypeData {
        private static final int RECEIVER_TYPE_DATA_TAG = 4;
        private static final int RECEIVER_TYPE_DATA_CELLS = 1;

        public TypeCheckData() {
            super(RECEIVER_TYPE_DATA_TAG, RECEIVER_TYPE_DATA_CELLS);
        }

        @Override
        public int getExecutionCount(HotSpotMethodData data, int position) {
            return -1;
        }
    }

    private static class VirtualCallData extends AbstractTypeData {
        private static final int VIRTUAL_CALL_DATA_TAG = 5;
        private static final int VIRTUAL_CALL_DATA_CELLS = 1;

        public VirtualCallData() {
            super(VIRTUAL_CALL_DATA_TAG, VIRTUAL_CALL_DATA_CELLS);
        }

        @Override
        public int getExecutionCount(HotSpotMethodData data, int position) {
            HotSpotVMConfig config = getHotSpotVMConfig();
            int typeProfileWidth = config.typeProfileWidth;

            long total = 0;
            for (int i = 0; i < typeProfileWidth; i++) {
                total += data.readUnsignedInt(position, getCountOffset(i));
            }

            total += getCounterValue(data, position);
            return truncateLongToInt(total);
        }
    }

    private static class RetData extends CounterData {
        private static final int RET_DATA_TAG = 6;
        private static final int RET_DATA_CELLS = 1;
        private static final int RET_DATA_ROW_CELL_COUNT = 3;

        public RetData() {
            super(RET_DATA_TAG, RET_DATA_CELLS);
        }

        @Override
        protected int getDynamicCellCount(HotSpotMethodData data, int position) {
            HotSpotVMConfig config = getHotSpotVMConfig();
            return config.bciProfileWidth * RET_DATA_ROW_CELL_COUNT;
        }
    }

    private static class BranchData extends JumpData {
        private static final int BRANCH_DATA_TAG = 7;
        private static final int BRANCH_DATA_CELLS = 3;
        private static final int NOT_TAKEN_COUNT_OFFSET = 2;

        public BranchData() {
            super(BRANCH_DATA_TAG, BRANCH_DATA_CELLS);
        }

        @Override
        public double getBranchTakenProbability(HotSpotMethodData data, int position) {
            long takenCount = data.readUnsignedInt(position, TAKEN_COUNT_OFFSET);
            long notTakenCount = data.readUnsignedInt(position, NOT_TAKEN_COUNT_OFFSET);
            long total = takenCount + notTakenCount;

            if (total < 40) {
                return -1;
            } else {
                return takenCount / (double) total;
            }
        }
    }

    private static class ArrayData extends AbstractMethodDataAccessor {
        private static final int ARRAY_DATA_LENGTH_OFFSET = 0;
        private static final int ARRAY_DATA_START_OFFSET = 1;

        public ArrayData(int tag, int staticCellCount) {
            super(tag, staticCellCount);
        }

        @Override
        protected int getDynamicCellCount(HotSpotMethodData data, int position) {
            return getLength(data, position);
        }

        protected static int getLength(HotSpotMethodData data, int position) {
            return data.readInt(position, ARRAY_DATA_LENGTH_OFFSET);
        }

        protected static int getElementOffset(int index) {
            return ARRAY_DATA_START_OFFSET + index;
        }
    }

    private static class MultiBranchData extends ArrayData {
        private static final int MULTI_BRANCH_DATA_TAG = 8;
        private static final int MULTI_BRANCH_DATA_CELLS = 1;
        private static final int MULTI_BRANCH_DATA_COUNT_OFFSET = 0;
        private static final int MULTI_BRANCH_DATA_DISPLACEMENT_OFFSET = 1;

        public MultiBranchData() {
            super(MULTI_BRANCH_DATA_TAG, MULTI_BRANCH_DATA_CELLS);
        }

        @Override
        public double[] getSwitchProbabilities(HotSpotMethodData data, int position) {
            int length = getLength(data, position);
            assert length > 0 : "switch must have at least the default case";

            long total = 0;
            double[] result = new double[length];

            for (int i = 0; i < length; i++) {
                int offset = getCountOffset(i);
                long count = data.readUnsignedInt(position, offset);
                total += count;
                result[i] = count;
            }

            if (total < 10 * (length + 2)) {
                return null;
            } else {
                for (int i = 0; i < length; i++) {
                    result[i] = result[i] / total;
                }

                // default case is expected as last entry
                if (length >= 2) {
                    double defaultCase = result[0];
                    result[0] = result[length - 1];
                    result[length - 1] = defaultCase;
                }
                return result;
            }
        }

        @Override
        public int getExecutionCount(HotSpotMethodData data, int position) {
            int length = getLength(data, position);
            long total = 0;

            for (int i = 0; i < length; i++) {
                int offset = getCountOffset(i);
                total += data.readUnsignedInt(position, offset);
            }

            return truncateLongToInt(total);
        }

        private static int getCountOffset(int index) {
            return getElementOffset(index + MULTI_BRANCH_DATA_COUNT_OFFSET);
        }
    }

    private static class ArgInfoData extends ArrayData {
        private static final int ARG_INFO_DATA_TAG = 9;
        private static final int ARG_INFO_DATA_CELLS = 1;

        public ArgInfoData() {
            super(ARG_INFO_DATA_TAG, ARG_INFO_DATA_CELLS);
        }
    }
}
