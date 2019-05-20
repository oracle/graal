/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm.binary;

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import static com.oracle.truffle.wasm.binary.Assert.format;

import static com.oracle.truffle.wasm.binary.Instructions.BLOCK;
import static com.oracle.truffle.wasm.binary.Instructions.DROP;
import static com.oracle.truffle.wasm.binary.Instructions.END;
import static com.oracle.truffle.wasm.binary.Instructions.F32_ADD;
import static com.oracle.truffle.wasm.binary.Instructions.F32_CONST;
import static com.oracle.truffle.wasm.binary.Instructions.F32_EQ;
import static com.oracle.truffle.wasm.binary.Instructions.F64_CONST;
import static com.oracle.truffle.wasm.binary.Instructions.I32_ADD;
import static com.oracle.truffle.wasm.binary.Instructions.I32_AND;
import static com.oracle.truffle.wasm.binary.Instructions.I32_CLZ;
import static com.oracle.truffle.wasm.binary.Instructions.I32_CONST;
import static com.oracle.truffle.wasm.binary.Instructions.I32_CTZ;
import static com.oracle.truffle.wasm.binary.Instructions.I32_DIV_S;
import static com.oracle.truffle.wasm.binary.Instructions.I32_DIV_U;
import static com.oracle.truffle.wasm.binary.Instructions.I32_EQ;
import static com.oracle.truffle.wasm.binary.Instructions.I32_EQZ;
import static com.oracle.truffle.wasm.binary.Instructions.I32_GE_S;
import static com.oracle.truffle.wasm.binary.Instructions.I32_GE_U;
import static com.oracle.truffle.wasm.binary.Instructions.I32_GT_S;
import static com.oracle.truffle.wasm.binary.Instructions.I32_GT_U;
import static com.oracle.truffle.wasm.binary.Instructions.I32_LE_S;
import static com.oracle.truffle.wasm.binary.Instructions.I32_LE_U;
import static com.oracle.truffle.wasm.binary.Instructions.I32_LT_S;
import static com.oracle.truffle.wasm.binary.Instructions.I32_LT_U;
import static com.oracle.truffle.wasm.binary.Instructions.I32_MUL;
import static com.oracle.truffle.wasm.binary.Instructions.I32_NE;
import static com.oracle.truffle.wasm.binary.Instructions.I32_OR;
import static com.oracle.truffle.wasm.binary.Instructions.I32_POPCNT;
import static com.oracle.truffle.wasm.binary.Instructions.I32_REM_S;
import static com.oracle.truffle.wasm.binary.Instructions.I32_REM_U;
import static com.oracle.truffle.wasm.binary.Instructions.I32_ROTL;
import static com.oracle.truffle.wasm.binary.Instructions.I32_ROTR;
import static com.oracle.truffle.wasm.binary.Instructions.I32_SHL;
import static com.oracle.truffle.wasm.binary.Instructions.I32_SHR_S;
import static com.oracle.truffle.wasm.binary.Instructions.I32_SHR_U;
import static com.oracle.truffle.wasm.binary.Instructions.I32_SUB;
import static com.oracle.truffle.wasm.binary.Instructions.I32_XOR;
import static com.oracle.truffle.wasm.binary.Instructions.I64_CONST;
import static com.oracle.truffle.wasm.binary.Instructions.LOCAL_GET;
import static com.oracle.truffle.wasm.binary.Instructions.LOCAL_SET;
import static com.oracle.truffle.wasm.binary.Instructions.I64_EQ;
import static com.oracle.truffle.wasm.binary.Instructions.I64_EQZ;
import static com.oracle.truffle.wasm.binary.Instructions.I64_GE_S;
import static com.oracle.truffle.wasm.binary.Instructions.I64_GE_U;
import static com.oracle.truffle.wasm.binary.Instructions.I64_GT_S;
import static com.oracle.truffle.wasm.binary.Instructions.I64_GT_U;
import static com.oracle.truffle.wasm.binary.Instructions.I64_LE_S;
import static com.oracle.truffle.wasm.binary.Instructions.I64_LE_U;
import static com.oracle.truffle.wasm.binary.Instructions.I64_LT_S;
import static com.oracle.truffle.wasm.binary.Instructions.I64_LT_U;
import static com.oracle.truffle.wasm.binary.Instructions.I64_NE;
import static com.oracle.truffle.wasm.binary.Instructions.NOP;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public class WasmBlockNode extends WasmNode {
    @CompilationFinal private final int startOffset;
    @CompilationFinal private final byte returnTypeId;
    @CompilationFinal private final int initialStackPointer;
    @CompilationFinal(dimensions = 1) byte[] constantLengthTable;
    @CompilationFinal(dimensions = 1) WasmNode[] nestedControlTable;

    public WasmBlockNode(WasmCodeEntry codeEntry, int startOffset, int size, byte returnTypeId, int initialStackPointer) {
        super(codeEntry, size);
        this.startOffset = startOffset;
        this.returnTypeId = returnTypeId;
        this.initialStackPointer = initialStackPointer;
        this.constantLengthTable = null;
        this.nestedControlTable = null;
    }

    @ExplodeLoop
    public void execute(VirtualFrame frame) {
        int constantOffset = 0;
        int nestedControlOffset = 0;
        int stackPointer = initialStackPointer;
        int offset = startOffset;
        while (offset < startOffset + size()) {
            byte byteOpcode = BinaryStreamReader.peek1(codeEntry().data(), offset);
            int opcode = byteOpcode & 0xFF;
            offset++;
            switch (opcode) {
                case NOP:
                    break;
                case BLOCK:
                    WasmNode block = nestedControlTable[nestedControlOffset];
                    block.execute(frame);
                    nestedControlOffset++;
                    offset += block.size();
                    stackPointer += block.returnTypeLength();
                    break;
                case END:
                    break;
                case DROP: {
                    stackPointer--;
                    pop(frame, stackPointer);
                    break;
                }
                case LOCAL_GET: {
                    int index = BinaryStreamReader.peekUnsignedInt32(codeEntry().data(), offset, null);
                    byte constantLength = constantLengthTable[constantOffset];
                    constantOffset++;
                    offset += constantLength;
                    byte type = codeEntry().localType(index);
                    switch (type) {
                        case ValueTypes.I32_TYPE: {
                            int value = getInt(frame, index);
                            pushInt(frame, stackPointer, value);
                            stackPointer++;
                            break;
                        }
                        case ValueTypes.I64_TYPE: {
                            long value = getLong(frame, index);
                            push(frame, stackPointer, value);
                            stackPointer++;
                            break;
                        }
                        case ValueTypes.F32_TYPE: {
                            float value = getFloat(frame, index);
                            pushFloat(frame, stackPointer, value);
                            stackPointer++;
                            break;
                        }
                        case ValueTypes.F64_TYPE: {
                            double value = getDouble(frame, index);
                            pushDouble(frame, stackPointer, value);
                            stackPointer++;
                            break;
                        }
                    }
                    break;
                }
                case LOCAL_SET: {
                    int index = BinaryStreamReader.peekUnsignedInt32(codeEntry().data(), offset, null);
                    byte constantLength = constantLengthTable[constantOffset];
                    constantOffset++;
                    offset += constantLength;
                    byte type = codeEntry().localType(index);
                    switch (type) {
                        case ValueTypes.I32_TYPE: {
                            stackPointer--;
                            int value = popInt(frame, stackPointer);
                            setInt(frame, index, value);
                            break;
                        }
                        case ValueTypes.I64_TYPE: {
                            stackPointer--;
                            long value = pop(frame, stackPointer);
                            setLong(frame, index, value);
                            break;
                        }
                        case ValueTypes.F32_TYPE: {
                            stackPointer--;
                            float value = popAsFloat(frame, stackPointer);
                            setFloat(frame, index, value);
                            break;
                        }
                        case ValueTypes.F64_TYPE: {
                            stackPointer--;
                            double value = popAsDouble(frame, stackPointer);
                            setDouble(frame, index, value);
                            break;
                        }
                    }
                    break;
                }
                case I32_CONST: {
                    int value = BinaryStreamReader.peekSignedInt32(codeEntry().data(), offset, null);
                    byte constantLength = constantLengthTable[constantOffset];
                    constantOffset++;
                    offset += constantLength;
                    pushInt(frame, stackPointer, value);
                    stackPointer++;
                    break;
                }
                case I64_CONST: {
                    long value = BinaryStreamReader.peekSignedInt64(codeEntry().data(), offset, null);
                    byte constantLength = constantLengthTable[constantOffset];
                    constantOffset++;
                    offset += constantLength;
                    push(frame, stackPointer, value);
                    stackPointer++;
                    break;
                }
                case I32_EQZ: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, x == 0 ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I32_EQ: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y == x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I32_NE: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y != x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I32_LT_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y < x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I32_LT_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, Integer.compareUnsigned(y, x) < 0 ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I32_GT_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y > x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I32_GT_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, Integer.compareUnsigned(y, x) > 0 ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I32_LE_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y <= x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I32_LE_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, Integer.compareUnsigned(y, x) <= 0 ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I32_GE_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y >= x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I32_GE_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, Integer.compareUnsigned(y, x) >= 0 ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I64_EQZ: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, x == 0 ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I64_EQ: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, y == x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I64_NE: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, y != x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I64_LT_S: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, y < x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I64_LT_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, Long.compareUnsigned(y, x) < 0 ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I64_GT_S: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, y > x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I64_GT_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, Long.compareUnsigned(y, x) > 0 ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I64_LE_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y <= x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I64_LE_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, Long.compareUnsigned(y, x) <= 0 ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I64_GE_S: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, y >= x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I64_GE_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, Long.compareUnsigned(y, x) >= 0 ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case F32_EQ: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushInt(frame, stackPointer, y == x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case I32_CLZ: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, Integer.numberOfLeadingZeros(x));
                    stackPointer++;
                    break;
                }
                case I32_CTZ: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, Integer.numberOfTrailingZeros(x));
                    stackPointer++;
                    break;
                }
                case I32_POPCNT: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, Integer.bitCount(x));
                    stackPointer++;
                    break;
                }
                case I32_ADD: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y + x);
                    stackPointer++;
                    break;
                }
                case I32_SUB: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y - x);
                    stackPointer++;
                    break;
                }
                case I32_MUL: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y * x);
                    stackPointer++;
                    break;
                }
                case I32_DIV_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y / x);
                    stackPointer++;
                    break;
                }
                case I32_DIV_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, Integer.divideUnsigned(y, x));
                    stackPointer++;
                    break;
                }
                case I32_REM_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y % x);
                    stackPointer++;
                    break;
                }
                case I32_REM_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, Integer.remainderUnsigned(y, x));
                    stackPointer++;
                    break;
                }
                case I32_AND: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y & x);
                    stackPointer++;
                    break;
                }
                case I32_OR: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y | x);
                    stackPointer++;
                    break;
                }
                case I32_XOR: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y ^ x);
                    stackPointer++;
                    break;
                }
                case I32_SHL: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y << x);
                    stackPointer++;
                    break;
                }
                case I32_SHR_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y >> x);
                    stackPointer++;
                    break;
                }
                case I32_SHR_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y >>> x);
                    stackPointer++;
                    break;
                }
                case I32_ROTL: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, Integer.rotateLeft(y, x));
                    stackPointer++;
                    break;
                }
                case I32_ROTR: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, Integer.rotateRight(y, x));
                    stackPointer++;
                    break;
                }
                case F32_CONST: {
                    int value = BinaryStreamReader.peekFloatAsInt32(codeEntry().data(), offset);
                    offset += 4;
                    pushInt(frame, stackPointer, value);
                    stackPointer++;
                    break;
                }
                case F32_ADD: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushFloat(frame, stackPointer, y + x);
                    stackPointer++;
                    break;
                }
                case F64_CONST: {
                    long value = BinaryStreamReader.peekFloatAsInt64(codeEntry().data(), offset);
                    offset += 8;
                    push(frame, stackPointer, value);
                    stackPointer++;
                    break;
                }
                default:
                    Assert.fail(format("Unknown opcode: 0x%02X", opcode));
            }
        }
    }

    public byte returnTypeId() {
        return returnTypeId;
    }

    @Override
    public int returnTypeLength() {
        switch (returnTypeId()) {
            case 0x00:
            case 0x40:
                return 0;
            default:
                return 1;
        }
    }
}
