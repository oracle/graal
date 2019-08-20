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
import static com.oracle.truffle.wasm.binary.constants.Instructions.BLOCK;
import static com.oracle.truffle.wasm.binary.constants.Instructions.BR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.BR_IF;
import static com.oracle.truffle.wasm.binary.constants.Instructions.CALL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.DROP;
import static com.oracle.truffle.wasm.binary.constants.Instructions.ELSE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.END;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_ABS;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_ADD;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_CEIL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_CONST;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_CONVERT_I32_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_CONVERT_I32_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_CONVERT_I64_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_CONVERT_I64_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_COPYSIGN;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_DEMOTE_F64;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_DIV;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_EQ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_FLOOR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_GE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_GT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_LE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_LOAD;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_LT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_MAX;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_MIN;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_MUL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_NE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_NEAREST;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_NEG;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_REINTERPRET_I32;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_SQRT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_STORE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_SUB;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F32_TRUNC;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_ABS;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_ADD;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_CEIL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_CONST;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_CONVERT_I32_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_CONVERT_I32_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_CONVERT_I64_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_CONVERT_I64_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_COPYSIGN;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_DIV;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_EQ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_FLOOR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_GE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_GT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_LE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_LOAD;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_LT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_MAX;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_MIN;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_MUL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_NE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_NEAREST;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_NEG;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_PROMOTE_F32;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_REINTERPRET_I64;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_SQRT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_STORE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_SUB;
import static com.oracle.truffle.wasm.binary.constants.Instructions.F64_TRUNC;
import static com.oracle.truffle.wasm.binary.constants.Instructions.GLOBAL_GET;
import static com.oracle.truffle.wasm.binary.constants.Instructions.GLOBAL_SET;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_ADD;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_AND;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_CLZ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_CONST;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_CTZ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_DIV_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_DIV_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_EQ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_EQZ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_GE_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_GE_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_GT_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_GT_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LE_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LE_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LOAD;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LOAD16_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LOAD16_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LOAD8_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LOAD8_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LT_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_LT_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_MUL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_NE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_OR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_POPCNT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_REINTERPRET_F32;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_REM_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_REM_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_ROTL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_ROTR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_SHL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_SHR_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_SHR_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_STORE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_STORE_16;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_STORE_8;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_SUB;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_TRUNC_F32_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_TRUNC_F32_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_TRUNC_F64_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_TRUNC_F64_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_WRAP_I64;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I32_XOR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_ADD;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_AND;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_CLZ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_CONST;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_CTZ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_DIV_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_DIV_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_EQ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_EQZ;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_EXTEND_I32_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_EXTEND_I32_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_GE_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_GE_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_GT_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_GT_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LE_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LE_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LOAD;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LOAD16_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LOAD16_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LOAD32_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LOAD32_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LOAD8_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LOAD8_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LT_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_LT_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_MUL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_NE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_OR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_POPCNT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_REINTERPRET_F64;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_REM_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_REM_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_ROTL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_ROTR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_SHL;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_SHR_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_SHR_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_STORE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_STORE_16;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_STORE_32;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_STORE_8;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_SUB;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_TRUNC_F32_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_TRUNC_F32_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_TRUNC_F64_S;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_TRUNC_F64_U;
import static com.oracle.truffle.wasm.binary.constants.Instructions.I64_XOR;
import static com.oracle.truffle.wasm.binary.constants.Instructions.IF;
import static com.oracle.truffle.wasm.binary.constants.Instructions.LOCAL_GET;
import static com.oracle.truffle.wasm.binary.constants.Instructions.LOCAL_SET;
import static com.oracle.truffle.wasm.binary.constants.Instructions.LOCAL_TEE;
import static com.oracle.truffle.wasm.binary.constants.Instructions.LOOP;
import static com.oracle.truffle.wasm.binary.constants.Instructions.NOP;
import static com.oracle.truffle.wasm.binary.constants.Instructions.SELECT;
import static com.oracle.truffle.wasm.binary.constants.Instructions.UNREACHABLE;

import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.wasm.binary.exception.WasmException;
import com.oracle.truffle.wasm.binary.exception.WasmTrap;
import com.oracle.truffle.wasm.binary.memory.WasmMemoryException;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class WasmBlockNode extends WasmNode implements RepeatingNode {
    @CompilationFinal private final int startOffset;
    @CompilationFinal private final byte returnTypeId;
    @CompilationFinal private final int initialStackPointer;
    @CompilationFinal private final int initialByteConstantOffset;
    @CompilationFinal private final int initialIntConstantOffset;
    @CompilationFinal private final int initialNumericLiteralOffset;
    @CompilationFinal private ContextReference<WasmContext> rawContextReference;
    @Children WasmNode[] nestedControlTable;
    @Children DirectCallNode[] callNodeTable;

    public WasmBlockNode(WasmModule wasmModule, WasmCodeEntry codeEntry, int startOffset, byte returnTypeId, int initialStackPointer, int initialByteConstantOffset, int initialIntConstantOffset, int initialNumericLiteralOffset) {
        super(wasmModule, codeEntry, -1, -1, -1);
        this.startOffset = startOffset;
        this.returnTypeId = returnTypeId;
        this.initialStackPointer = initialStackPointer;
        this.initialByteConstantOffset = initialByteConstantOffset;
        this.initialIntConstantOffset = initialIntConstantOffset;
        this.initialNumericLiteralOffset = initialNumericLiteralOffset;
        this.nestedControlTable = null;
        this.callNodeTable = null;
    }

    private ContextReference<WasmContext> contextReference() {
        if (rawContextReference == null) {
            rawContextReference = lookupContextReference(WasmLanguage.class);
        }
        return rawContextReference;
    }

    @ExplodeLoop
    public int execute(WasmContext context, VirtualFrame frame) {
        int nestedControlOffset = 0;
        int callNodeOffset = 0;
        int byteConstantOffset = initialByteConstantOffset;
        int intConstantOffset = initialIntConstantOffset;
        int numericLiteralOffset = initialNumericLiteralOffset;
        int stackPointer = initialStackPointer;
        int offset = startOffset;
        while (offset < startOffset + byteLength()) {
            byte byteOpcode = BinaryStreamReader.peek1(codeEntry().data(), offset);
            int opcode = byteOpcode & 0xFF;
            offset++;
            switch (opcode) {
                case UNREACHABLE:
                    throw new WasmTrap("unreachable", this);
                case NOP:
                    break;
                case BLOCK: {
                    WasmNode block = nestedControlTable[nestedControlOffset];

                    // The unwind counter indicates how many levels up we need to branch from within the block.
                    int unwindCounter = block.execute(context, frame);
                    if (unwindCounter > 0) {
                        return unwindCounter - 1;
                    }

                    nestedControlOffset++;
                    offset += block.byteLength();
                    stackPointer += block.returnTypeLength();
                    byteConstantOffset += block.byteConstantLength();
                    intConstantOffset += block.intConstantLength();
                    numericLiteralOffset += block.numericLiteralLength();
                    break;
                }
                case LOOP: {
                    WasmNode loopNode = nestedControlTable[nestedControlOffset];

                    // The unwind counter indicates how many levels up we need to branch from within the loop block.
                    // There are three possibilities for the value of unwind counter:
                    // - A value equal to -1 indicates normal loop completion and that the flow should continue
                    //   after the loop end (break out of the loop).
                    // - A value equal to 0 indicates that we need to branch to the beginning of the loop (repeat loop).
                    //   This is handled internally by Truffle and the executing loop should never return 0 here.
                    // - A value larger than 0 indicates that we need to branch to a level "shallower" than the current
                    //   loop block (break out of the loop and even further).
                    int unwindCounter = loopNode.execute(context, frame);
                    assert unwindCounter != 0;
                    if (unwindCounter > 0) {
                        return unwindCounter - 1;
                    }

                    nestedControlOffset++;
                    offset += loopNode.byteLength();
                    stackPointer += loopNode.returnTypeLength();
                    byteConstantOffset += loopNode.byteConstantLength();
                    intConstantOffset += loopNode.intConstantLength();
                    numericLiteralOffset += loopNode.numericLiteralLength();
                    break;
                }
                case IF: {
                    WasmNode ifNode = nestedControlTable[nestedControlOffset];
                    stackPointer--;
                    int unwindCounter = ifNode.execute(context, frame);
                    if (unwindCounter > 0) {
                        return unwindCounter - 1;
                    }
                    nestedControlOffset++;
                    offset += ifNode.byteLength();
                    stackPointer += ifNode.returnTypeLength();
                    byteConstantOffset += ifNode.byteConstantLength();
                    intConstantOffset += ifNode.intConstantLength();
                    numericLiteralOffset += ifNode.numericLiteralLength();
                    break;
                }
                case ELSE:
                    break;
                case END:
                    break;
                case BR: {
                    int unwindCounter = codeEntry().numericLiteralAsInt(numericLiteralOffset);

                    // Reset the stack pointer to the target block stack pointer.
                    int continuationStackPointer = codeEntry().intConstant(intConstantOffset);
                    // Technically, we should increment the intConstantOffset and numericLiteralOffset at this point,
                    // but since we are returning, it does not really matter.

                    // Populate the stack with the return values of the current block (the one we are escaping from).
                    unwindStack(frame, stackPointer, continuationStackPointer);

                    return unwindCounter;
                }
                case BR_IF: {
                    stackPointer--;
                    int cond = popInt(frame, stackPointer);
                    if (cond != 0) {
                        int unwindCounter = codeEntry().numericLiteralAsInt(numericLiteralOffset);

                        // Reset the stack pointer to the target block stack pointer.
                        int continuationStackPointer = codeEntry().intConstant(intConstantOffset);
                        // Technically, we should increment the intConstantOffset and numericLiteralOffset at this point,
                        // but since we are returning, it does not really matter.

                        // Populate the stack with the return values of the current block (the one we are escaping from).
                        unwindStack(frame, stackPointer, continuationStackPointer);

                        return unwindCounter;
                    }
                    numericLiteralOffset++;
                    byte constantLength = codeEntry().byteConstant(byteConstantOffset);
                    byteConstantOffset++;
                    offset += constantLength;
                    break;
                }
                case CALL: {
                    int functionIndex = codeEntry().numericLiteralAsInt(numericLiteralOffset);
                    numericLiteralOffset++;
                    byte constantLength = codeEntry().byteConstant(byteConstantOffset);
                    byteConstantOffset++;
                    offset += constantLength;

                    WasmFunction function = wasmModule().symbolTable().function(functionIndex);
                    byte returnType = function.returnType();
                    int numArgs = function.numArguments();

                    DirectCallNode callNode = callNodeTable[callNodeOffset];
                    callNodeOffset++;

                    Object[] args = createArgumentsForCall(frame, function, numArgs, stackPointer);
                    stackPointer -= args.length;

                    Object result = callNode.call(args);
                    // At the moment, WebAssembly functions may return up to one value.
                    // As per the WebAssembly specification, this restriction may be lifted in the future.
                    switch (returnType) {
                        case ValueTypes.I32_TYPE: {
                            pushInt(frame, stackPointer, (int) result);
                            stackPointer++;
                            break;
                        }
                        case ValueTypes.I64_TYPE: {
                            push(frame, stackPointer, (long) result);
                            stackPointer++;
                            break;
                        }
                        case ValueTypes.F32_TYPE: {
                            pushFloat(frame, stackPointer, (float) result);
                            stackPointer++;
                            break;
                        }
                        case ValueTypes.F64_TYPE: {
                            pushDouble(frame, stackPointer, (double) result);
                            stackPointer++;
                            break;
                        }
                        default: {
                            // Void return type - do nothing.
                            break;
                        }
                    }

                    break;
                }
                case DROP: {
                    stackPointer--;
                    pop(frame, stackPointer);
                    break;
                }
                case SELECT: {
                    stackPointer--;
                    int cond = popInt(frame, stackPointer);
                    stackPointer--;
                    long val2 = pop(frame, stackPointer);
                    stackPointer--;
                    long val1 = pop(frame, stackPointer);
                    push(frame, stackPointer, cond != 0 ? val1 : val2);
                    stackPointer++;
                    break;
                }
                case LOCAL_GET: {
                    int index = codeEntry().numericLiteralAsInt(numericLiteralOffset);
                    numericLiteralOffset++;
                    byte constantLength = codeEntry().byteConstant(byteConstantOffset);
                    byteConstantOffset++;
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
                    int index = codeEntry().numericLiteralAsInt(numericLiteralOffset);
                    numericLiteralOffset++;
                    byte constantLength = codeEntry().byteConstant(byteConstantOffset);
                    byteConstantOffset++;
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
                case LOCAL_TEE: {
                    int index = codeEntry().numericLiteralAsInt(numericLiteralOffset);
                    numericLiteralOffset++;
                    byte constantLength = codeEntry().byteConstant(byteConstantOffset);
                    byteConstantOffset++;
                    offset += constantLength;
                    byte type = codeEntry().localType(index);
                    switch (type) {
                        case ValueTypes.I32_TYPE: {
                            stackPointer--;
                            int value = popInt(frame, stackPointer);
                            pushInt(frame, stackPointer, value);
                            stackPointer++;
                            setInt(frame, index, value);
                            break;
                        }
                        case ValueTypes.I64_TYPE: {
                            stackPointer--;
                            long value = pop(frame, stackPointer);
                            push(frame, stackPointer, value);
                            stackPointer++;
                            setLong(frame, index, value);
                            break;
                        }
                        case ValueTypes.F32_TYPE: {
                            stackPointer--;
                            float value = popAsFloat(frame, stackPointer);
                            pushFloat(frame, stackPointer, value);
                            stackPointer++;
                            setFloat(frame, index, value);
                            break;
                        }
                        case ValueTypes.F64_TYPE: {
                            stackPointer--;
                            double value = popAsDouble(frame, stackPointer);
                            pushDouble(frame, stackPointer, value);
                            stackPointer++;
                            setDouble(frame, index, value);
                            break;
                        }
                    }
                    break;
                }
                case GLOBAL_GET: {
                    int index = codeEntry().numericLiteralAsInt(numericLiteralOffset);
                    numericLiteralOffset++;
                    byte constantLength = codeEntry().byteConstant(byteConstantOffset);
                    byteConstantOffset++;
                    offset += constantLength;
                    byte type = wasmModule().globals().type(index);
                    switch (type) {
                        case ValueTypes.I32_TYPE: {
                            int value = wasmModule().globals().getAsInt(index);
                            pushInt(frame, stackPointer, value);
                            stackPointer++;
                            break;
                        }
                        case ValueTypes.I64_TYPE: {
                            long value = wasmModule().globals().getAsLong(index);
                            push(frame, stackPointer, value);
                            stackPointer++;
                            break;
                        }
                        case ValueTypes.F32_TYPE: {
                            float value = wasmModule().globals().getAsFloat(index);
                            pushFloat(frame, stackPointer, value);
                            stackPointer++;
                            break;
                        }
                        case ValueTypes.F64_TYPE: {
                            double value = wasmModule().globals().getAsDouble(index);
                            pushDouble(frame, stackPointer, value);
                            stackPointer++;
                            break;
                        }
                    }
                    break;
                }
                case GLOBAL_SET: {
                    int index = codeEntry().numericLiteralAsInt(numericLiteralOffset);
                    numericLiteralOffset++;
                    byte constantLength = codeEntry().byteConstant(byteConstantOffset);
                    byteConstantOffset++;
                    offset += constantLength;
                    byte type = wasmModule().globals().type(index);
                    // For global.set, we don't need to make sure that the referenced global is mutable.
                    // This is taken care of by validation during wat to wasm compilation.
                    switch (type) {
                        case ValueTypes.I32_TYPE: {
                            stackPointer--;
                            int value = popInt(frame, stackPointer);
                            wasmModule().globals().setInt(index, value);
                            break;
                        }
                        case ValueTypes.I64_TYPE: {
                            stackPointer--;
                            long value = pop(frame, stackPointer);
                            wasmModule().globals().setLong(index, value);
                            break;
                        }
                        case ValueTypes.F32_TYPE: {
                            stackPointer--;
                            float value = popAsFloat(frame, stackPointer);
                            wasmModule().globals().setFloat(index, value);
                            break;
                        }
                        case ValueTypes.F64_TYPE: {
                            stackPointer--;
                            double value = popAsDouble(frame, stackPointer);
                            wasmModule().globals().setDouble(index, value);
                            break;
                        }
                    }
                    break;
                }
                case I32_LOAD:
                case I64_LOAD:
                case F32_LOAD:
                case F64_LOAD:
                case I32_LOAD8_S:
                case I32_LOAD8_U:
                case I32_LOAD16_S:
                case I32_LOAD16_U:
                case I64_LOAD8_S:
                case I64_LOAD8_U:
                case I64_LOAD16_S:
                case I64_LOAD16_U:
                case I64_LOAD32_S:
                case I64_LOAD32_U: {
                    /* The memAlign hint is not currently used or taken into account. */
                    byte memAlignConstantLength = codeEntry().byteConstant(byteConstantOffset);
                    byteConstantOffset++;
                    offset += memAlignConstantLength;

                    int memOffset = codeEntry().numericLiteralAsInt(numericLiteralOffset);
                    numericLiteralOffset++;
                    byte memOffsetConstantLength = codeEntry().byteConstant(byteConstantOffset);
                    byteConstantOffset++;
                    offset += memOffsetConstantLength;

                    stackPointer--;
                    int baseAddress = popInt(frame, stackPointer);
                    int address = baseAddress + memOffset;

                    try {
                        switch (opcode) {
                            case I32_LOAD: {
                                context.memory().validateAddress(address, 32);
                                int value = context.memory().load_i32(address);
                                pushInt(frame, stackPointer, value);
                                break;
                            }
                            case I64_LOAD: {
                                context.memory().validateAddress(address, 64);
                                long value = context.memory().load_i64(address);
                                push(frame, stackPointer, value);
                                break;
                            }
                            case F32_LOAD: {
                                context.memory().validateAddress(address, 32);
                                float value = context.memory().load_f32(address);
                                pushFloat(frame, stackPointer, value);
                                break;
                            }
                            case F64_LOAD: {
                                context.memory().validateAddress(address, 64);
                                double value = context.memory().load_f64(address);
                                pushDouble(frame, stackPointer, value);
                                break;
                            }
                            case I32_LOAD8_S: {
                                context.memory().validateAddress(address, 8);
                                int value = context.memory().load_i32_8s(address);
                                pushInt(frame, stackPointer, value);
                                break;
                            }
                            case I32_LOAD8_U: {
                                context.memory().validateAddress(address, 8);
                                int value = context.memory().load_i32_8u(address);
                                pushInt(frame, stackPointer, value);
                                break;
                            }
                            case I32_LOAD16_S: {
                                context.memory().validateAddress(address, 16);
                                int value = context.memory().load_i32_16s(address);
                                pushInt(frame, stackPointer, value);
                                break;
                            }
                            case I32_LOAD16_U: {
                                context.memory().validateAddress(address, 16);
                                int value = context.memory().load_i32_16u(address);
                                pushInt(frame, stackPointer, value);
                                break;
                            }
                            case I64_LOAD8_S: {
                                context.memory().validateAddress(address, 8);
                                long value = context.memory().load_i64_8s(address);
                                push(frame, stackPointer, value);
                                break;
                            }
                            case I64_LOAD8_U: {
                                context.memory().validateAddress(address, 8);
                                long value = context.memory().load_i64_8u(address);
                                push(frame, stackPointer, value);
                                break;
                            }
                            case I64_LOAD16_S: {
                                context.memory().validateAddress(address, 16);
                                long value = context.memory().load_i64_16s(address);
                                push(frame, stackPointer, value);
                                break;
                            }
                            case I64_LOAD16_U: {
                                context.memory().validateAddress(address, 16);
                                long value = context.memory().load_i64_16u(address);
                                push(frame, stackPointer, value);
                                break;
                            }
                            case I64_LOAD32_S: {
                                context.memory().validateAddress(address, 32);
                                long value = context.memory().load_i64_32s(address);
                                push(frame, stackPointer, value);
                                break;
                            }
                            case I64_LOAD32_U: {
                                context.memory().validateAddress(address, 32);
                                long value = context.memory().load_i64_32u(address);
                                push(frame, stackPointer, value);
                                break;
                            }
                        }
                    } catch (WasmMemoryException e) {
                        throw new WasmTrap("memory address out-of-bounds", this);
                    }
                    stackPointer++;
                    break;
                }
                case I32_STORE:
                case I64_STORE:
                case F32_STORE:
                case F64_STORE:
                case I32_STORE_8:
                case I32_STORE_16:
                case I64_STORE_8:
                case I64_STORE_16:
                case I64_STORE_32: {
                    /* The memAlign hint is not currently used or taken into account. */
                    byte memAlignConstantLength = codeEntry().byteConstant(byteConstantOffset);
                    byteConstantOffset++;
                    offset += memAlignConstantLength;

                    int memOffset = codeEntry().numericLiteralAsInt(numericLiteralOffset);
                    numericLiteralOffset++;
                    byte memOffsetConstantLength = codeEntry().byteConstant(byteConstantOffset);
                    byteConstantOffset++;
                    offset += memOffsetConstantLength;

                    try {
                        switch (opcode) {
                            case I32_STORE: {
                                stackPointer--;
                                int value = popInt(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                context.memory().validateAddress(address, 32);
                                context.memory().store_i32(address, value);
                                break;
                            }
                            case I64_STORE: {
                                stackPointer--;
                                long value = pop(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                context.memory().validateAddress(address, 64);
                                context.memory().store_i64(address, value);
                                break;
                            }
                            case F32_STORE: {
                                stackPointer--;
                                float value = popAsFloat(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                context.memory().validateAddress(address, 32);
                                context.memory().store_f32(address, value);
                                break;
                            }
                            case F64_STORE: {
                                stackPointer--;
                                double value = popAsDouble(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                context.memory().validateAddress(address, 64);
                                context.memory().store_f64(address, value);
                                break;
                            }
                            case I32_STORE_8: {
                                stackPointer--;
                                int value = popInt(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                context.memory().validateAddress(address, 8);
                                context.memory().store_i32_8(address, (byte) value);
                                break;
                            }
                            case I32_STORE_16: {
                                stackPointer--;
                                int value = popInt(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                context.memory().validateAddress(address, 16);
                                context.memory().store_i32_16(address, (short) value);
                                break;
                            }
                            case I64_STORE_8: {
                                stackPointer--;
                                long value = pop(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                context.memory().validateAddress(address, 8);
                                context.memory().store_i64_8(address, (byte) value);
                                break;
                            }
                            case I64_STORE_16: {
                                stackPointer--;
                                long value = pop(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                context.memory().validateAddress(address, 16);
                                context.memory().store_i64_16(address, (short) value);
                                break;
                            }
                            case I64_STORE_32: {
                                stackPointer--;
                                long value = pop(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                context.memory().validateAddress(address, 32);
                                context.memory().store_i64_32(address, (int) value);
                                break;
                            }
                        }
                    } catch (WasmMemoryException e) {
                        throw new WasmTrap("memory address out-of-bounds", this);
                    }

                    break;
                }
                case I32_CONST: {
                    int value = codeEntry().numericLiteralAsInt(numericLiteralOffset);
                    numericLiteralOffset++;
                    byte constantLength = codeEntry().byteConstant(byteConstantOffset);
                    byteConstantOffset++;
                    offset += constantLength;
                    pushInt(frame, stackPointer, value);
                    stackPointer++;
                    break;
                }
                case I64_CONST: {
                    long value = codeEntry().numericLiteral(numericLiteralOffset);
                    numericLiteralOffset++;
                    byte constantLength = codeEntry().byteConstant(byteConstantOffset);
                    byteConstantOffset++;
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
                case F32_NE: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushInt(frame, stackPointer, y != x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case F32_LT: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushInt(frame, stackPointer, y < x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case F32_GT: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushInt(frame, stackPointer, y > x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case F32_LE: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushInt(frame, stackPointer, y <= x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case F32_GE: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushInt(frame, stackPointer, y >= x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case F64_EQ: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushInt(frame, stackPointer, y == x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case F64_NE: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushInt(frame, stackPointer, y != x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case F64_LT: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushInt(frame, stackPointer, y < x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case F64_GT: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushInt(frame, stackPointer, y > x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case F64_LE: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushInt(frame, stackPointer, y <= x ? 1 : 0);
                    stackPointer++;
                    break;
                }
                case F64_GE: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushInt(frame, stackPointer, y >= x ? 1 : 0);
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
                case I64_CLZ: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    push(frame, stackPointer, Long.numberOfLeadingZeros(x));
                    stackPointer++;
                    break;
                }
                case I64_CTZ: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    push(frame, stackPointer, Long.numberOfTrailingZeros(x));
                    stackPointer++;
                    break;
                }
                case I64_POPCNT: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    push(frame, stackPointer, Long.bitCount(x));
                    stackPointer++;
                    break;
                }
                case I64_ADD: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    push(frame, stackPointer, y + x);
                    stackPointer++;
                    break;
                }
                case I64_SUB: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    push(frame, stackPointer, y - x);
                    stackPointer++;
                    break;
                }
                case I64_MUL: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    push(frame, stackPointer, y * x);
                    stackPointer++;
                    break;
                }
                case I64_DIV_S: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    push(frame, stackPointer, y / x);
                    stackPointer++;
                    break;
                }
                case I64_DIV_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    push(frame, stackPointer, Long.divideUnsigned(y, x));
                    stackPointer++;
                    break;
                }
                case I64_REM_S: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    push(frame, stackPointer, y % x);
                    stackPointer++;
                    break;
                }
                case I64_REM_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    push(frame, stackPointer, Long.remainderUnsigned(y, x));
                    stackPointer++;
                    break;
                }
                case I64_AND: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    push(frame, stackPointer, y & x);
                    stackPointer++;
                    break;
                }
                case I64_OR: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    push(frame, stackPointer, y | x);
                    stackPointer++;
                    break;
                }
                case I64_XOR: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    push(frame, stackPointer, y ^ x);
                    stackPointer++;
                    break;
                }
                case I64_SHL: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    push(frame, stackPointer, y << x);
                    stackPointer++;
                    break;
                }
                case I64_SHR_S: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    push(frame, stackPointer, y >> x);
                    stackPointer++;
                    break;
                }
                case I64_SHR_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    push(frame, stackPointer, y >>> x);
                    stackPointer++;
                    break;
                }
                case I64_ROTL: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    push(frame, stackPointer, Long.rotateLeft(y, (int) x));
                    stackPointer++;
                    break;
                }
                case I64_ROTR: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    push(frame, stackPointer, Long.rotateRight(y, (int) x));
                    stackPointer++;
                    break;
                }
                case F32_CONST: {
                    int value = codeEntry().numericLiteralAsInt(numericLiteralOffset);
                    numericLiteralOffset++;
                    offset += 4;
                    pushInt(frame, stackPointer, value);
                    stackPointer++;
                    break;
                }
                case F32_ABS: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    pushFloat(frame, stackPointer, Math.abs(x));
                    stackPointer++;
                    break;
                }
                case F32_NEG: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    pushFloat(frame, stackPointer, -x);
                    stackPointer++;
                    break;
                }
                case F32_CEIL: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    pushFloat(frame, stackPointer, (float) Math.ceil(x));
                    stackPointer++;
                    break;
                }
                case F32_FLOOR: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    pushFloat(frame, stackPointer, (float) Math.floor(x));
                    stackPointer++;
                    break;
                }
                case F32_TRUNC: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    pushFloat(frame, stackPointer, (int) x);
                    stackPointer++;
                    break;
                }
                case F32_NEAREST: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    pushFloat(frame, stackPointer, Math.round(x));
                    stackPointer++;
                    break;
                }
                case F32_SQRT: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    pushFloat(frame, stackPointer, (float) Math.sqrt(x));
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
                case F32_SUB: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushFloat(frame, stackPointer, y - x);
                    stackPointer++;
                    break;
                }
                case F32_MUL: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushFloat(frame, stackPointer, y * x);
                    stackPointer++;
                    break;
                }
                case F32_DIV: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushFloat(frame, stackPointer, y / x);
                    stackPointer++;
                    break;
                }
                case F32_MIN: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushFloat(frame, stackPointer, Math.min(y, x));
                    stackPointer++;
                    break;
                }
                case F32_MAX: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushFloat(frame, stackPointer, Math.max(y, x));
                    stackPointer++;
                    break;
                }
                case F32_COPYSIGN: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushFloat(frame, stackPointer, Math.copySign(y, x));
                    stackPointer++;
                    break;
                }
                case F64_CONST: {
                    long value = codeEntry().numericLiteral(numericLiteralOffset);
                    numericLiteralOffset++;
                    offset += 8;
                    push(frame, stackPointer, value);
                    stackPointer++;
                    break;
                }
                case F64_ABS: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    pushDouble(frame, stackPointer, Math.abs(x));
                    stackPointer++;
                    break;
                }
                case F64_NEG: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    pushDouble(frame, stackPointer, -x);
                    stackPointer++;
                    break;
                }
                case F64_CEIL: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    pushDouble(frame, stackPointer, Math.ceil(x));
                    stackPointer++;
                    break;
                }
                case F64_FLOOR: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    pushDouble(frame, stackPointer, Math.floor(x));
                    stackPointer++;
                    break;
                }
                case F64_TRUNC: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    pushDouble(frame, stackPointer, (long) x);
                    stackPointer++;
                    break;
                }
                case F64_NEAREST: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    pushDouble(frame, stackPointer, Math.round(x));
                    stackPointer++;
                    break;
                }
                case F64_SQRT: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    pushDouble(frame, stackPointer, Math.sqrt(x));
                    stackPointer++;
                    break;
                }
                case F64_ADD: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushDouble(frame, stackPointer, y + x);
                    stackPointer++;
                    break;
                }
                case F64_SUB: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushDouble(frame, stackPointer, y - x);
                    stackPointer++;
                    break;
                }
                case F64_MUL: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushDouble(frame, stackPointer, y * x);
                    stackPointer++;
                    break;
                }
                case F64_DIV: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushDouble(frame, stackPointer, y / x);
                    stackPointer++;
                    break;
                }
                case F64_MIN: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushDouble(frame, stackPointer, Math.min(y, x));
                    stackPointer++;
                    break;
                }
                case F64_MAX: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushDouble(frame, stackPointer, Math.max(y, x));
                    stackPointer++;
                    break;
                }
                case F64_COPYSIGN: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushDouble(frame, stackPointer, Math.copySign(y, x));
                    stackPointer++;
                    break;
                }
                case I32_WRAP_I64: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, (int) (x % 0x8000_0000));
                    stackPointer++;
                    break;
                }
                case I32_TRUNC_F32_S:
                case I32_TRUNC_F32_U: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    pushInt(frame, stackPointer, (int) x);
                    stackPointer++;
                    break;
                }
                case I32_TRUNC_F64_S:
                case I32_TRUNC_F64_U: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    pushInt(frame, stackPointer, (int) x);
                    stackPointer++;
                    break;
                }
                case I64_EXTEND_I32_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    push(frame, stackPointer, x);
                    stackPointer++;
                    break;
                }
                case I64_EXTEND_I32_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    push(frame, stackPointer, x & 0xFFFF_FFFFL);
                    stackPointer++;
                    break;
                }
                case I64_TRUNC_F32_S:
                case I64_TRUNC_F32_U: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    push(frame, stackPointer, (long) x);
                    stackPointer++;
                    break;
                }
                case I64_TRUNC_F64_S:
                case I64_TRUNC_F64_U: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    push(frame, stackPointer, (long) x);
                    stackPointer++;
                    break;
                }
                case F32_CONVERT_I32_S:
                case F32_CONVERT_I32_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    pushFloat(frame, stackPointer, (float) x);
                    stackPointer++;
                    break;
                }
                case F32_CONVERT_I64_S:
                case F32_CONVERT_I64_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    pushFloat(frame, stackPointer, (float) x);
                    stackPointer++;
                    break;
                }
                case F32_DEMOTE_F64: {
                    throw new NotImplementedException();
                }
                case F64_CONVERT_I32_S:
                case F64_CONVERT_I32_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    pushDouble(frame, stackPointer, (double) x);
                    stackPointer++;
                    break;
                }
                case F64_CONVERT_I64_S:
                case F64_CONVERT_I64_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    pushDouble(frame, stackPointer, (double) x);
                    stackPointer++;
                    break;
                }
                case F64_PROMOTE_F32: {
                    throw new NotImplementedException();
                }
                case I32_REINTERPRET_F32:
                case I64_REINTERPRET_F64:
                case F32_REINTERPRET_I32:
                case F64_REINTERPRET_I64: {
                    // As we don't store type information for the frame slots (everything is stored as raw bits in a long,
                    // and interpreted appropriately upon access), we don't need to do anything for these instructions.
                    break;
                }
                default:
                    Assert.fail(format("Unknown opcode: 0x%02X", opcode));
            }
        }
        return -1;
    }

    @ExplodeLoop
    private Object[] createArgumentsForCall(VirtualFrame frame, WasmFunction function, int numArgs, int stackPointer) {
        Object[] args = new Object[numArgs];
        for (int i = 0; i != numArgs; ++i) {
            stackPointer--;
            byte type = wasmModule().symbolTable().getFunctionTypeArgumentTypeAt(function.typeIndex(), i);
            switch (type) {
                case ValueTypes.I32_TYPE:
                    args[i] = popInt(frame, stackPointer);
                    break;
                case ValueTypes.I64_TYPE:
                    args[i] = pop(frame, stackPointer);
                    break;
                case ValueTypes.F32_TYPE:
                    args[i] = popAsFloat(frame, stackPointer);
                    break;
                case ValueTypes.F64_TYPE:
                    args[i] = popAsDouble(frame, stackPointer);
                    break;
            }
        }
        return args;
    }

    private void unwindStack(VirtualFrame frame, int stackPointer, int continuationStackPointer) {
        for (int i = 0; i != returnTypeLength(); ++i) {
            stackPointer--;
            long value = pop(frame, stackPointer);
            push(frame, continuationStackPointer, value);
            continuationStackPointer++;
        }
    }

    @Override
    public boolean executeRepeating(VirtualFrame frame) {
        throw new WasmException(this, "This method should never have been called.");
    }

    @Override
    public int executeRepeatingWithStatus(VirtualFrame frame) {
        return execute(contextReference().get(), frame);
    }

    @Override
    public byte returnTypeId() {
        return returnTypeId;
    }

}
