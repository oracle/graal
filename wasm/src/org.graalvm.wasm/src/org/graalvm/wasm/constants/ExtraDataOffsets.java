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

package org.graalvm.wasm.constants;

/**
 * Represents the offsets of entries in the extra data array.
 */
public final class ExtraDataOffsets {
    /**
     * If:
     * 
     * <code>
     *      | bytecodeIndex (4 byte) | extraIndex (4 byte) | conditionProfile (4 byte) |
     * </code>
     * 
     * bytecodeIndex (data array): The index of the first instruction in the else branch.
     * 
     * extraIndex (extra data array): The index of the first instruction, which needs extra data, in
     * the else branch.
     * 
     * conditionProfile: The condition profile probability for the false jump.
     */
    public static final int IF_BYTECODE_INDEX = 0;
    public static final int IF_EXTRA_INDEX = 1;
    public static final int IF_PROFILE = 2;
    public static final int IF_LENGTH = 3;

    /**
     * Else:
     * 
     * <code>
     *     | bytecodeIndex (4 byte) | extraIndex (4 byte) |
     * </code>
     * 
     * bytecodeIndex (data array): The index of the first instruction after the else branch.
     * 
     * extraIndex (extra data array): The index of the first instruction, which needs extra data,
     * after the else branch.
     */

    public static final int ELSE_BYTECODE_INDEX = 0;
    public static final int ELSE_EXTRA_INDEX = 1;
    public static final int ELSE_LENGTH = 2;

    /**
     * Constants to extract the returnLength and stackSize from a stackInfo entry.
     */
    public static final int STACK_INFO_RETURN_LENGTH_SHIFT = 24;
    public static final int STACK_INFO_STACK_SIZE_MASK = 0x00FF_FFFF;

    /**
     * Br_if:
     * 
     * <code>
     *     | bytecodeIndex (4 byte) | extraIndex (4 byte) | returnLength (1 byte) | stackSize (3 byte) | conditionProfile (4 byte) |
     * </code>
     * 
     * bytecodeIndex (data array): The index of the first instruction after the jump.
     * 
     * extraIndex (extra data array): The index of the first instruction, which needs extra data,
     * after the jump.
     * 
     * stackInfo: Combination of returnLength and stackSize.
     * 
     * returnLength: The number of return values of the jump target block.
     * 
     * stackSize: The stack pointer of the jump target block (number of stack values after the
     * jump).
     * 
     * conditionProfile: The condition profile probability for the jump.
     */
    public static final int BR_IF_BYTECODE_INDEX = 0;
    public static final int BR_IF_EXTRA_INDEX = 1;
    public static final int BR_IF_STACK_INFO = 2;
    public static final int BR_IF_PROFILE = 3;
    public static final int BR_IF_LENGTH = 4;

    /**
     * Br:
     * 
     * <code>
     *     | bytecodeIndex (4 byte) | extraIndex (4 byte) | returnLength (1 byte) | stackSize (3 byte) |
     * </code>
     * 
     * bytecodeIndex (data array): The index of the first instruction after the jump.
     * 
     * extraIndex (extra data array): The index of the first instruction, which needs extra data,
     * after the jump.
     * 
     * stackInfo: Combination of returnLength and stackSize.
     * 
     * returnLength: The number of return values of the jump target block.
     * 
     * stackSize: The stack pointer of the jump target block (number of stack values after the
     * jump).
     */
    public static final int BR_BYTECODE_INDEX = 0;
    public static final int BR_EXTRA_INDEX = 1;
    public static final int BR_STACK_INFO = 2;
    public static final int BR_LENGTH = 3;

    /**
     * Br_table:
     * 
     * <code>
     *     | size | count | entry | ... | entry | default entry |
     * </code>
     * 
     * size: The number of entries in the branch table.
     * 
     * count: The number of times the branch table instruction has been executed. Used for
     * calculating the branch probabilities.
     * 
     * entry: A branch table entry. (see below)
     */
    public static final int BR_TABLE_SIZE = 0;
    public static final int BR_TABLE_COUNT = 1;
    public static final int BR_TABLE_ENTRY_OFFSET = 2;

    /**
     * Br_table_entry:
     * 
     * <code>
     *     | bytecodeIndex (4 byte) | extraIndex (4 byte) | returnLength (1 byte) | stackSize (3 byte) | profileCount (4 byte) |
     * </code>
     * 
     * bytecodeIndex (data array): The index of the first instruction after the jump.
     * 
     * extraIndex (extra data array): The index of the first instruction, which needs extra data,
     * after the jump.
     * 
     * stackInfo: Combination of returnLength and stackSize.
     * 
     * returnLength: The number of values of the jump target block.
     * 
     * stackSize: The stack pointer of the jump target block (number of stack values after the
     * jump).
     * 
     * profileCount: The number of times this branch table entry has been chosen. Used for
     * calculating the branch probability.
     */
    public static final int BR_TABLE_ENTRY_BYTECODE_INDEX = 0;
    public static final int BR_TABLE_ENTRY_EXTRA_INDEX = 1;
    public static final int BR_TABLE_ENTRY_STACK_INFO = 2;
    public static final int BR_TABLE_ENTRY_PROFILE = 3;
    public static final int BR_TABLE_ENTRY_LENGTH = 4;

    /**
     * Call_indirect:
     * 
     * <code>
     *     | nodeIndex (4 byte) | conditionProfile (4 byte) |
     * </code>
     * 
     * nodeIndex: The index in the call node array.
     * 
     * conditionProfile: The condition profile probability for calling an external module.
     */
    public static final int CALL_INDIRECT_NODE_INDEX = 0;
    public static final int CALL_INDIRECT_PROFILE = 1;
    public static final int CALL_INDIRECT_LENGTH = 2;

    /**
     * Call:
     * 
     * <code>
     *     | nodeIndex (4 byte) |
     * </code>
     * 
     * nodeIndex: The index in the call node array.
     */
    public static final int CALL_NODE_INDEX = 0;
    public static final int CALL_LENGTH = 1;

    private ExtraDataOffsets() {
    }
}
