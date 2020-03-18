/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm.util;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.core.common.NumUtil;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.util.VMError;

public class LLVMStackMapInfo {
    public static final long DEFAULT_PATCHPOINT_ID = 0xABCDEF00L;

    static class StackMap {
        byte version;
        Function[] functions;
        long[] constants;
    }

    static class Function {
        long address;
        long stackSize;
        Record[] records;
    }

    static class Record {
        long patchpointID;
        int instructionOffset;
        short flags;
        Location[] locations;
        LiveOut[] liveOuts;
    }

    static class Location {
        Type type;
        short size;
        short regNum;
        int offset;

        enum Type {
            Register(1),
            Direct(2),
            Indirect(3),
            Constant(4),
            ConstantIndex(5);

            private final byte encoding;

            Type(int encoding) {
                this.encoding = (byte) encoding;
            }

            static Type decode(byte encoding) {
                for (Type type : values()) {
                    if (type.encoding == encoding) {
                        return type;
                    }
                }
                return null;
            }
        }
    }

    static class LiveOut {
        short regNum;
        byte size;
    }

    /*
     * Stack map format specification available at
     * https://llvm.org/docs/StackMaps.html#stack-map-format
     */
    LLVMStackMapInfo(ByteBuffer buffer) {
        StackMap stackMap = new StackMap();

        int offset = 0;

        stackMap.version = buffer.get(offset);
        offset += Byte.BYTES;

        // skip header
        offset += Byte.BYTES;
        offset += Short.BYTES;

        stackMap.functions = new Function[buffer.getInt(offset)];
        offset += Integer.BYTES;

        stackMap.constants = new long[buffer.getInt(offset)];
        offset += Integer.BYTES;

        int numRecords = buffer.getInt(offset);
        offset += Integer.BYTES;

        long totalNumRecords = 0L;
        for (int i = 0; i < stackMap.functions.length; i++) {
            Function function = new Function();

            function.address = buffer.getLong(offset); // always 0
            offset += Long.BYTES;

            function.stackSize = buffer.getLong(offset);
            offset += Long.BYTES;

            function.records = new Record[NumUtil.safeToInt(buffer.getLong(offset))];
            offset += Long.BYTES;

            stackMap.functions[i] = function;

            totalNumRecords += function.records.length;
        }

        for (int i = 0; i < stackMap.constants.length; ++i) {
            stackMap.constants[i] = buffer.getLong(offset);
            offset += Long.BYTES;
        }

        int fun = 0;
        int rec = 0;
        assert numRecords == totalNumRecords;
        for (int i = 0; i < numRecords; ++i, ++rec) {
            while (rec == stackMap.functions[fun].records.length) {
                fun++;
                rec = 0;
            }

            Function function = stackMap.functions[fun];
            Record record = new Record();

            record.patchpointID = buffer.getLong(offset);
            offset += Long.BYTES;

            record.instructionOffset = buffer.getInt(offset);
            offset += Integer.BYTES;

            record.flags = buffer.getShort(offset);
            offset += Short.BYTES;

            record.locations = new Location[buffer.getShort(offset)];
            offset += Short.BYTES;

            for (int j = 0; j < record.locations.length; j++) {
                Location location = new Location();

                location.type = Location.Type.decode(buffer.get(offset));
                offset += Byte.BYTES;
                offset += Byte.BYTES; // skip reserved bytes

                location.size = buffer.getShort(offset);
                offset += Short.BYTES;

                location.regNum = buffer.getShort(offset);
                offset += Short.BYTES;
                offset += Short.BYTES; // skip reserved bytes

                location.offset = buffer.getInt(offset);
                offset += Integer.BYTES;

                record.locations[j] = location;
            }
            if (offset % Long.BYTES != 0) {
                offset += Integer.BYTES; // skip alignment padding
            }
            offset += Short.BYTES; // skip padding

            record.liveOuts = new LiveOut[buffer.getShort(offset)];
            offset += Short.BYTES;

            for (int j = 0; j < record.liveOuts.length; j++) {
                LiveOut liveOut = new LiveOut();

                liveOut.regNum = buffer.getShort(offset);
                offset += Short.BYTES;
                offset += Byte.BYTES; // skip reserved bytes

                liveOut.size = buffer.get(offset);
                offset += Byte.BYTES;

                record.liveOuts[j] = liveOut;
            }
            if (offset % Long.BYTES != 0) {
                offset += Integer.BYTES; // skip alignment padding
            }

            function.records[rec] = record;

            if (patchpointToFunction.containsKey(record.patchpointID)) {
                assert record.patchpointID == DEFAULT_PATCHPOINT_ID || patchpointToFunction.get(record.patchpointID) == function;
            }
            patchpointToFunction.put(record.patchpointID, function);
            patchpointsByID.computeIfAbsent(record.patchpointID, v -> new HashSet<>()).add(record);
        }
    }

    private Map<Long, Function> patchpointToFunction = new HashMap<>();
    private Map<Long, Set<Record>> patchpointsByID = new HashMap<>();

    long getFunctionStackSize(long startPatchpointID) {
        assert patchpointToFunction.containsKey(startPatchpointID);
        return patchpointToFunction.get(startPatchpointID).stackSize;
    }

    private long getFunctionOffset(long startPatchpointID) {
        assert patchpointToFunction.containsKey(startPatchpointID);
        return patchpointToFunction.get(startPatchpointID).address;
    }

    int[] getPatchpointOffsets(long patchpointID) {
        if (patchpointsByID.containsKey(patchpointID)) {
            return patchpointsByID.get(patchpointID).stream().mapToInt(r -> r.instructionOffset).toArray();
        }
        return new int[0];
    }

    private static final int STATEPOINT_HEADER_LOCATION_COUNT = 3;
    private static final int STATEPOINT_DEOPT_COUNT_LOCATION_INDEX = 2;

    @FunctionalInterface
    public interface StatepointOffsetCallback {
        void accept(int derivedOffset, int baseOffset, boolean compressed);
    }

    void forEachStatepointOffset(long patchpointID, int instructionOffset, StatepointOffsetCallback callback) {
        Location[] locations = patchpointsByID.get(patchpointID).stream().filter(r -> r.instructionOffset == instructionOffset)
                        .findFirst().orElseThrow(VMError::shouldNotReachHere).locations;
        assert locations.length >= STATEPOINT_HEADER_LOCATION_COUNT;

        Location deoptCountLocation = locations[STATEPOINT_DEOPT_COUNT_LOCATION_INDEX];
        assert deoptCountLocation.type == Location.Type.Constant;
        int deoptCount = deoptCountLocation.offset;
        assert STATEPOINT_HEADER_LOCATION_COUNT + deoptCount <= locations.length;

        Set<Integer> compressedOffsets = new HashSet<>();
        for (int i = STATEPOINT_HEADER_LOCATION_COUNT; i < STATEPOINT_HEADER_LOCATION_COUNT + deoptCount; ++i) {
            Location loc = locations[i];
            assert loc.type == Location.Type.Indirect; // spilled values
            int[] offsets = getStackOffsets(patchpointID, loc);
            assert offsets.length == 1;
            compressedOffsets.add(offsets[0]);
        }

        Set<Integer> seenOffsets = new HashSet<>();
        Set<Integer> seenBases = new HashSet<>();
        for (int i = STATEPOINT_HEADER_LOCATION_COUNT + deoptCount; i < locations.length; i += 2) {
            assert i + 1 < locations.length;
            Location base = locations[i];
            Location ref = locations[i + 1];

            if (base.type == Location.Type.Constant || ref.type == Location.Type.Constant) {
                assert base.type == ref.type && base.offset == ref.offset;
                if (base.offset != 0) {
                    /*
                     * We are seeing a hard-coded pointer. This will most probably cause a segfault
                     * at runtime, but we have to emit it either way and not fail during
                     * compilation.
                     */
                    seenBases.add((int) (base.offset - getFunctionOffset(patchpointID)));
                    seenOffsets.add((int) (ref.offset - getFunctionOffset(patchpointID)));
                }
                continue;
            }

            assert base.type == Location.Type.Indirect; // spilled values
            int[] baseOffsets = getStackOffsets(patchpointID, base);

            assert ref.type == Location.Type.Indirect; // spilled values
            int[] derivedOffsets = getStackOffsets(patchpointID, ref);

            assert baseOffsets.length == derivedOffsets.length;

            for (int j = 0; j < baseOffsets.length; ++j) {
                int baseOffset = baseOffsets[j];
                int derivedOffset = derivedOffsets[j];

                seenBases.add(baseOffset);
                /* Derived pointers have their base already registered on the stackmap */
                if (!seenOffsets.contains(derivedOffset)) {
                    seenOffsets.add(derivedOffset);
                    assert compressedOffsets.contains(derivedOffset) == compressedOffsets.contains(baseOffset);
                    callback.accept(derivedOffset, baseOffset, compressedOffsets.contains(derivedOffset));
                }
            }
        }

        assert seenOffsets.containsAll(seenBases);
    }

    public int getAllocaOffset(long startPatchPointId) {
        Set<Record> startRecords = patchpointsByID.get(startPatchPointId);
        assert startRecords.size() == 1;
        Record startRecord = startRecords.stream().findAny().orElseThrow(VMError::shouldNotReachHere);

        assert startRecord.locations.length == 1;
        Location alloca = startRecord.locations[0];
        assert alloca.type == Location.Type.Direct;

        int[] offsets = getStackOffsets(startPatchPointId, alloca);
        assert offsets.length == 1;
        return offsets[0];
    }

    private int[] getStackOffsets(long patchpointID, Location location) {
        assert location.size % FrameAccess.wordSize() == 0;
        int numLocations = location.size / FrameAccess.wordSize();
        assert numLocations > 0;

        int baseOffset;
        if (location.regNum == LLVMTargetSpecific.get().getStackPointerDwarfRegNum()) {
            baseOffset = location.offset;
        } else if (location.regNum == LLVMTargetSpecific.get().getFramePointerDwarfRegNum()) {
            /*
             * Convert frame-relative offset (negative) to a stack-relative offset (positive).
             */
            baseOffset = location.offset + NumUtil.safeToInt(getFunctionStackSize(patchpointID)) + LLVMTargetSpecific.get().getFramePointerOffset();
        } else {
            throw shouldNotReachHere("found other register " + patchpointID + " " + location.regNum);
        }
        assert baseOffset >= 0 && baseOffset + location.size < getFunctionStackSize(patchpointID);

        int[] offsets = new int[numLocations];
        for (int i = 0; i < numLocations; ++i) {
            offsets[i] = baseOffset + i * FrameAccess.wordSize();
        }

        return offsets;
    }
}
