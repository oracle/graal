/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.wasm.util;

import org.graalvm.wasm.WasmType;

/**
 * Helper class for generating extra data entries.
 */
public class ExtraDataUtil {
    private static final int EXTENDED_FORMAT_INDICATOR = 0x8000_0000;
    private static final int MIN_SIGNED_31BIT_VALUE = 0xc000_0000;
    private static final int MAX_SIGNED_31BIT_VALUE = 0x3fff_ffff;
    private static final int MAX_UNSIGNED_15BIT_VALUE = 0x0000_7fff;
    private static final int MIN_SIGNED_15BIT_VALUE = 0xffff_c000;
    private static final int MAX_SIGNED_15BIT_VALUE = 0x0000_3fff;
    private static final int MAX_UNSIGNED_7BIT_VALUE = 0x0000_007f;

    /**
     * Compact sizes.
     */
    public static final int COMPACT_JUMP_TARGET_SIZE = 1;
    public static final int COMPACT_STACK_CHANGE_SIZE = 1;
    public static final int COMPACT_CALL_TARGET_SIZE = 1;
    public static final int COMPACT_TABLE_HEADER_SIZE = 1;

    /**
     * Extended sizes.
     */
    public static final int EXTENDED_JUMP_TARGET_SIZE = 2;
    public static final int EXTENDED_STACK_CHANGE_SIZE = 3;
    public static final int EXTENDED_CALL_TARGET_SIZE = 1;
    public static final int EXTENDED_TABLE_HEADER_SIZE = 1;

    public static final int PROFILE_SIZE = 1;

    private static final int PRIMITIVE_UNWIND = 0x0080_0000;
    private static final int REFERENCE_UNWIND = 0x8000_0000;

    private static int createCompactShortValuesWithIndicator(int upperValue, int lowerValue) {
        return ((upperValue << 16) | lowerValue) & 0x7fff_ffff;
    }

    private static int createCompactUpperBytes(int leadValue, int upperValue, int lowerValue) {
        return leadValue | (upperValue << 24) | (lowerValue << 16);
    }

    public static boolean isValidUnwindType(int value) {
        return value == 0 || value == PRIMITIVE_UNWIND || value == REFERENCE_UNWIND || value == (PRIMITIVE_UNWIND | REFERENCE_UNWIND);
    }

    public static boolean exceedsUnsigned7BitValue(int value) {
        return Integer.compareUnsigned(value, MAX_UNSIGNED_7BIT_VALUE) > 0;
    }

    public static boolean exceedsUnsignedShortValueWithIndicator(int value) {
        return Integer.compareUnsigned(value, MAX_UNSIGNED_15BIT_VALUE) > 0;
    }

    public static boolean exceedsSignedShortValueWithIndicator(int value) {
        return value < MIN_SIGNED_15BIT_VALUE || MAX_SIGNED_15BIT_VALUE < value;
    }

    public static boolean exceedsSignedShortValue(int value) {
        return value < Short.MIN_VALUE || Short.MAX_VALUE < value;
    }

    public static boolean exceedsUnsignedIntValueWithIndicator(int value) {
        return value < 0;
    }

    public static boolean exceedsSignedIntValueWithIndicator(int value) {
        return value < MIN_SIGNED_31BIT_VALUE || MAX_SIGNED_31BIT_VALUE < value;
    }

    public static boolean exceedsPositiveIntValue(int value) {
        return value < 0;
    }

    /**
     * Adds a compact jump target entry, consisting of byte code displacement and extra data
     * displacement, at the given offset. The jump target entry has to be at the start of an extra
     * data entry.
     * <p>
     * The resulting entry looks as follows:
     * <p>
     * <code>
     *     | 0 (1-bit) | extraDataDisplacement (15-bits) | byteCodeDisplacement (16-bits) |
     * </code>
     *
     * @param extraData The extra data array
     * @param branchTargetOffset The offset in the array
     * @param byteCodeDisplacement The relative byte code offset
     * @param extraDataDisplacement The relative extra offset
     * @return The number of added array entries
     */
    public static int addCompactBranchTarget(int[] extraData, int branchTargetOffset, int byteCodeDisplacement, int extraDataDisplacement) {
        extraData[branchTargetOffset] = createCompactShortValuesWithIndicator(extraDataDisplacement, byteCodeDisplacement);
        return COMPACT_JUMP_TARGET_SIZE;
    }

    /**
     * Adds an extended jump target entry, consisting of byte code displacement and extra data
     * displacement, at the given offset. The jump target entry has to be at the start of an extra
     * data entry.
     * <p>
     * The resulting entry looks as follows:
     * <p>
     * <code>
     *     | 1 (1-bit) | extraDataDisplacement (31-bits) | byteCodeDisplacement (32-bit) |
     * </code>
     *
     * @param extraData The extra data array
     * @param branchTargetOffset The offset in the array
     * @param byteCodeDisplacement The relative byte code offset
     * @param extraDataDisplacement The relative extra offset
     * @return The number of added array entries
     */
    public static int addExtendedBranchTarget(int[] extraData, int branchTargetOffset, int byteCodeDisplacement, int extraDataDisplacement) {
        extraData[branchTargetOffset] = extraDataDisplacement | EXTENDED_FORMAT_INDICATOR;
        extraData[branchTargetOffset + 1] = byteCodeDisplacement;
        return EXTENDED_JUMP_TARGET_SIZE;
    }

    /**
     * Adds a compact stack change entry, consisting of return length and stack size, at the given
     * offset. The stack change entry cannot be at the start of an extra data entry.
     * <p>
     * The resulting entry looks as follows:
     * <p>
     * <code>
     *     | resultCount (8-bit) | stackSize (8-bit) | 0 (16-bit) |
     * </code>
     * 
     * @param extraData The extra data array
     * @param stackChangeOffset The offset in the array
     * @param unwindType The most common type of the result values and stack value that are affected
     *            by the stack change
     * @param resultCount The result count
     * @param stackSize The stack size
     * @return The number of added array entries
     */
    public static int addCompactStackChange(int[] extraData, int stackChangeOffset, int unwindType, int resultCount, int stackSize) {
        extraData[stackChangeOffset] = createCompactUpperBytes(unwindType, resultCount, stackSize);
        return COMPACT_STACK_CHANGE_SIZE;
    }

    /**
     * Adds an extended stack change entry, consisting of return length and stack size, at the given
     * offset. The stack change entry cannot be at the start of an extra data entry.
     * <p>
     * The resulting entry looks as follows:
     * <p>
     * <code>
     *     | resultCount (32-bit) | stackSize (32-bit) |
     * </code>
     * 
     * @param extraData The extra data array
     * @param stackChangeOffset The offset in the array
     * @param unwindType The most common type of the result values and stack value that are affected
     *            by the stack change
     * @param resultCount The result count
     * @param stackSize The stack size
     * @return The number of added array entries
     */
    public static int addExtendedStackChange(int[] extraData, int stackChangeOffset, int unwindType, int resultCount, int stackSize) {
        extraData[stackChangeOffset] = unwindType;
        extraData[stackChangeOffset + 1] = resultCount;
        extraData[stackChangeOffset + 2] = stackSize;
        return EXTENDED_STACK_CHANGE_SIZE;
    }

    /**
     * Adds a compact call target entry, consisting of a node index, at the given offset. The call
     * target entry has to be at the start of an extra data entry.
     * <p>
     * The resulting entry looks as follows:
     * <p>
     * <code>
     *     | 0 (1-bit) | nodeIndex (15-bit) | 0 (16-bit) |
     * </code>
     * 
     * @param extraData The extra data array
     * @param callTargetOffset The offset in the array
     * @param nodeIndex The node index
     * @return The number of added array entries
     */
    public static int addCompactCallTarget(int[] extraData, int callTargetOffset, int nodeIndex) {
        extraData[callTargetOffset] = createCompactShortValuesWithIndicator(nodeIndex, 0);
        return COMPACT_CALL_TARGET_SIZE;
    }

    /**
     * Adds an extended call target entry, consisting of a node index, at the given offset. The call
     * target entry has to be at the start of an extra data entry.
     * <p>
     * The resulting entry looks as follows:
     * <p>
     * <code>
     *     | 1 (1-bit) | nodeIndex (31-bit) |
     * </code>
     *
     * @param extraData The extra data array
     * @param callTargetOffset The offset in the array
     * @param nodeIndex The node index
     * @return The number of added array entries
     */
    public static int addExtendedCallTarget(int[] extraData, int callTargetOffset, int nodeIndex) {
        extraData[callTargetOffset] = (nodeIndex | EXTENDED_FORMAT_INDICATOR);
        return EXTENDED_CALL_TARGET_SIZE;
    }

    /**
     * Adds a compact table header entry, consisting of the table size, at the given offset. The
     * table header entry has to be at the start of an extra data entry.
     * <p>
     * The resulting entry looks a follows:
     * <p>
     * <code>
     *     | 0 (1-bit) | size (15-bit) | 0 (16-bit) |
     * </code>
     *
     * @param extraData The extra data array
     * @param tableOffset The offset in the array
     * @param size The size of the table
     * @return The number of added array entries
     */
    public static int addCompactTableHeader(int[] extraData, int tableOffset, int size) {
        extraData[tableOffset] = createCompactShortValuesWithIndicator(size, 0);
        return COMPACT_TABLE_HEADER_SIZE;
    }

    /**
     * Adds an extended table header entry, consisting of the table size, at the given offset. The
     * table header entry has to be at the start of an extra data entry.
     * <p>
     * The resulting entry looks as follows:
     * <p>
     * <code>
     *     | 1 (1-bit) | size (31-bit) |
     * </code>
     *
     * @param extraData The extra data array
     * @param tableOffset The offset in the array
     * @param size The size of the table
     * @return The number of added array entries
     */
    public static int addExtendedTableHeader(int[] extraData, int tableOffset, int size) {
        extraData[tableOffset] = (size | EXTENDED_FORMAT_INDICATOR);
        return EXTENDED_TABLE_HEADER_SIZE;
    }

    /**
     * Adds a profile counter at the given offset. The profile counter cannot be at the start of an
     * extra data entry.
     * <p>
     * The resulting entry looks as follows:
     * <p>
     * <code>
     *     | profileCounter (32-bit) |
     * </code>
     * 
     * @param extraData The extra data array
     * @param profileOffset The offset in the array
     * @return The number of added array entries
     */
    @SuppressWarnings("unused")
    public static int addProfileCounter(int[] extraData, int profileOffset) {
        return PROFILE_SIZE;
    }

    /**
     * Extracts the most common unwind type from the given value types.
     * 
     * @param types An array of value types
     * @return The extracted unwind type
     */
    public static int extractUnwindType(byte[] types) {
        int unwindType = 0;
        for (byte type : types) {
            unwindType |= WasmType.isNumberType(type) ? PRIMITIVE_UNWIND : 0;
            unwindType |= WasmType.isReferenceType(type) ? REFERENCE_UNWIND : 0;
        }
        return unwindType;
    }
}
