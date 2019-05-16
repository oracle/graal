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
import static com.oracle.truffle.wasm.binary.Instructions.*;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public class WasmBlockNode extends WasmNode {
    @CompilationFinal private final int startOffset;
    @CompilationFinal private int size;
    @CompilationFinal private final byte typeId;
    @CompilationFinal private final int initialStackPointer;
    @CompilationFinal byte[] constantLengthTable;

    public WasmBlockNode(WasmCodeEntry codeEntry, int startOffset, int size, byte typeId, int initialStackPointer) {
        super(codeEntry);
        this.startOffset = startOffset;
        this.size = size;
        this.typeId = typeId;
        this.initialStackPointer = initialStackPointer;
        this.constantLengthTable = null;
    }

    @ExplodeLoop
    public void execute(VirtualFrame frame) {
        int constantOffset = 0;
        int stackPointer = initialStackPointer;
        int offset = startOffset;
        while (offset < startOffset + size) {
            byte byteOpcode = BinaryStreamReader.peek1(codeEntry().data(), offset);
            int opcode = byteOpcode & 0xFF;
            offset++;
            switch (opcode) {
                case END:
                    break;
                case DROP: {
                    stackPointer--;
                    pop(frame, stackPointer);
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
                    Assert.fail("Not implemented");
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

    public byte typeId() {
        return typeId;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
