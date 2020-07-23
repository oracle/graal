/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.nodes;

import static org.graalvm.wasm.WasmTracing.trace;
import static org.graalvm.wasm.constants.Instructions.BLOCK;
import static org.graalvm.wasm.constants.Instructions.BR;
import static org.graalvm.wasm.constants.Instructions.BR_IF;
import static org.graalvm.wasm.constants.Instructions.BR_TABLE;
import static org.graalvm.wasm.constants.Instructions.CALL;
import static org.graalvm.wasm.constants.Instructions.CALL_INDIRECT;
import static org.graalvm.wasm.constants.Instructions.DROP;
import static org.graalvm.wasm.constants.Instructions.ELSE;
import static org.graalvm.wasm.constants.Instructions.END;
import static org.graalvm.wasm.constants.Instructions.F32_ABS;
import static org.graalvm.wasm.constants.Instructions.F32_ADD;
import static org.graalvm.wasm.constants.Instructions.F32_CEIL;
import static org.graalvm.wasm.constants.Instructions.F32_CONST;
import static org.graalvm.wasm.constants.Instructions.F32_CONVERT_I32_S;
import static org.graalvm.wasm.constants.Instructions.F32_CONVERT_I32_U;
import static org.graalvm.wasm.constants.Instructions.F32_CONVERT_I64_S;
import static org.graalvm.wasm.constants.Instructions.F32_CONVERT_I64_U;
import static org.graalvm.wasm.constants.Instructions.F32_COPYSIGN;
import static org.graalvm.wasm.constants.Instructions.F32_DEMOTE_F64;
import static org.graalvm.wasm.constants.Instructions.F32_DIV;
import static org.graalvm.wasm.constants.Instructions.F32_EQ;
import static org.graalvm.wasm.constants.Instructions.F32_FLOOR;
import static org.graalvm.wasm.constants.Instructions.F32_GE;
import static org.graalvm.wasm.constants.Instructions.F32_GT;
import static org.graalvm.wasm.constants.Instructions.F32_LE;
import static org.graalvm.wasm.constants.Instructions.F32_LOAD;
import static org.graalvm.wasm.constants.Instructions.F32_LT;
import static org.graalvm.wasm.constants.Instructions.F32_MAX;
import static org.graalvm.wasm.constants.Instructions.F32_MIN;
import static org.graalvm.wasm.constants.Instructions.F32_MUL;
import static org.graalvm.wasm.constants.Instructions.F32_NE;
import static org.graalvm.wasm.constants.Instructions.F32_NEAREST;
import static org.graalvm.wasm.constants.Instructions.F32_NEG;
import static org.graalvm.wasm.constants.Instructions.F32_REINTERPRET_I32;
import static org.graalvm.wasm.constants.Instructions.F32_SQRT;
import static org.graalvm.wasm.constants.Instructions.F32_STORE;
import static org.graalvm.wasm.constants.Instructions.F32_SUB;
import static org.graalvm.wasm.constants.Instructions.F32_TRUNC;
import static org.graalvm.wasm.constants.Instructions.F64_ABS;
import static org.graalvm.wasm.constants.Instructions.F64_ADD;
import static org.graalvm.wasm.constants.Instructions.F64_CEIL;
import static org.graalvm.wasm.constants.Instructions.F64_CONST;
import static org.graalvm.wasm.constants.Instructions.F64_CONVERT_I32_S;
import static org.graalvm.wasm.constants.Instructions.F64_CONVERT_I32_U;
import static org.graalvm.wasm.constants.Instructions.F64_CONVERT_I64_S;
import static org.graalvm.wasm.constants.Instructions.F64_CONVERT_I64_U;
import static org.graalvm.wasm.constants.Instructions.F64_COPYSIGN;
import static org.graalvm.wasm.constants.Instructions.F64_DIV;
import static org.graalvm.wasm.constants.Instructions.F64_EQ;
import static org.graalvm.wasm.constants.Instructions.F64_FLOOR;
import static org.graalvm.wasm.constants.Instructions.F64_GE;
import static org.graalvm.wasm.constants.Instructions.F64_GT;
import static org.graalvm.wasm.constants.Instructions.F64_LE;
import static org.graalvm.wasm.constants.Instructions.F64_LOAD;
import static org.graalvm.wasm.constants.Instructions.F64_LT;
import static org.graalvm.wasm.constants.Instructions.F64_MAX;
import static org.graalvm.wasm.constants.Instructions.F64_MIN;
import static org.graalvm.wasm.constants.Instructions.F64_MUL;
import static org.graalvm.wasm.constants.Instructions.F64_NE;
import static org.graalvm.wasm.constants.Instructions.F64_NEAREST;
import static org.graalvm.wasm.constants.Instructions.F64_NEG;
import static org.graalvm.wasm.constants.Instructions.F64_PROMOTE_F32;
import static org.graalvm.wasm.constants.Instructions.F64_REINTERPRET_I64;
import static org.graalvm.wasm.constants.Instructions.F64_SQRT;
import static org.graalvm.wasm.constants.Instructions.F64_STORE;
import static org.graalvm.wasm.constants.Instructions.F64_SUB;
import static org.graalvm.wasm.constants.Instructions.F64_TRUNC;
import static org.graalvm.wasm.constants.Instructions.GLOBAL_GET;
import static org.graalvm.wasm.constants.Instructions.GLOBAL_SET;
import static org.graalvm.wasm.constants.Instructions.I32_ADD;
import static org.graalvm.wasm.constants.Instructions.I32_AND;
import static org.graalvm.wasm.constants.Instructions.I32_CLZ;
import static org.graalvm.wasm.constants.Instructions.I32_CONST;
import static org.graalvm.wasm.constants.Instructions.I32_CTZ;
import static org.graalvm.wasm.constants.Instructions.I32_DIV_S;
import static org.graalvm.wasm.constants.Instructions.I32_DIV_U;
import static org.graalvm.wasm.constants.Instructions.I32_EQ;
import static org.graalvm.wasm.constants.Instructions.I32_EQZ;
import static org.graalvm.wasm.constants.Instructions.I32_GE_S;
import static org.graalvm.wasm.constants.Instructions.I32_GE_U;
import static org.graalvm.wasm.constants.Instructions.I32_GT_S;
import static org.graalvm.wasm.constants.Instructions.I32_GT_U;
import static org.graalvm.wasm.constants.Instructions.I32_LE_S;
import static org.graalvm.wasm.constants.Instructions.I32_LE_U;
import static org.graalvm.wasm.constants.Instructions.I32_LOAD;
import static org.graalvm.wasm.constants.Instructions.I32_LOAD16_S;
import static org.graalvm.wasm.constants.Instructions.I32_LOAD16_U;
import static org.graalvm.wasm.constants.Instructions.I32_LOAD8_S;
import static org.graalvm.wasm.constants.Instructions.I32_LOAD8_U;
import static org.graalvm.wasm.constants.Instructions.I32_LT_S;
import static org.graalvm.wasm.constants.Instructions.I32_LT_U;
import static org.graalvm.wasm.constants.Instructions.I32_MUL;
import static org.graalvm.wasm.constants.Instructions.I32_NE;
import static org.graalvm.wasm.constants.Instructions.I32_OR;
import static org.graalvm.wasm.constants.Instructions.I32_POPCNT;
import static org.graalvm.wasm.constants.Instructions.I32_REINTERPRET_F32;
import static org.graalvm.wasm.constants.Instructions.I32_REM_S;
import static org.graalvm.wasm.constants.Instructions.I32_REM_U;
import static org.graalvm.wasm.constants.Instructions.I32_ROTL;
import static org.graalvm.wasm.constants.Instructions.I32_ROTR;
import static org.graalvm.wasm.constants.Instructions.I32_SHL;
import static org.graalvm.wasm.constants.Instructions.I32_SHR_S;
import static org.graalvm.wasm.constants.Instructions.I32_SHR_U;
import static org.graalvm.wasm.constants.Instructions.I32_STORE;
import static org.graalvm.wasm.constants.Instructions.I32_STORE_16;
import static org.graalvm.wasm.constants.Instructions.I32_STORE_8;
import static org.graalvm.wasm.constants.Instructions.I32_SUB;
import static org.graalvm.wasm.constants.Instructions.I32_TRUNC_F32_S;
import static org.graalvm.wasm.constants.Instructions.I32_TRUNC_F32_U;
import static org.graalvm.wasm.constants.Instructions.I32_TRUNC_F64_S;
import static org.graalvm.wasm.constants.Instructions.I32_TRUNC_F64_U;
import static org.graalvm.wasm.constants.Instructions.I32_WRAP_I64;
import static org.graalvm.wasm.constants.Instructions.I32_XOR;
import static org.graalvm.wasm.constants.Instructions.I64_ADD;
import static org.graalvm.wasm.constants.Instructions.I64_AND;
import static org.graalvm.wasm.constants.Instructions.I64_CLZ;
import static org.graalvm.wasm.constants.Instructions.I64_CONST;
import static org.graalvm.wasm.constants.Instructions.I64_CTZ;
import static org.graalvm.wasm.constants.Instructions.I64_DIV_S;
import static org.graalvm.wasm.constants.Instructions.I64_DIV_U;
import static org.graalvm.wasm.constants.Instructions.I64_EQ;
import static org.graalvm.wasm.constants.Instructions.I64_EQZ;
import static org.graalvm.wasm.constants.Instructions.I64_EXTEND_I32_S;
import static org.graalvm.wasm.constants.Instructions.I64_EXTEND_I32_U;
import static org.graalvm.wasm.constants.Instructions.I64_GE_S;
import static org.graalvm.wasm.constants.Instructions.I64_GE_U;
import static org.graalvm.wasm.constants.Instructions.I64_GT_S;
import static org.graalvm.wasm.constants.Instructions.I64_GT_U;
import static org.graalvm.wasm.constants.Instructions.I64_LE_S;
import static org.graalvm.wasm.constants.Instructions.I64_LE_U;
import static org.graalvm.wasm.constants.Instructions.I64_LOAD;
import static org.graalvm.wasm.constants.Instructions.I64_LOAD16_S;
import static org.graalvm.wasm.constants.Instructions.I64_LOAD16_U;
import static org.graalvm.wasm.constants.Instructions.I64_LOAD32_S;
import static org.graalvm.wasm.constants.Instructions.I64_LOAD32_U;
import static org.graalvm.wasm.constants.Instructions.I64_LOAD8_S;
import static org.graalvm.wasm.constants.Instructions.I64_LOAD8_U;
import static org.graalvm.wasm.constants.Instructions.I64_LT_S;
import static org.graalvm.wasm.constants.Instructions.I64_LT_U;
import static org.graalvm.wasm.constants.Instructions.I64_MUL;
import static org.graalvm.wasm.constants.Instructions.I64_NE;
import static org.graalvm.wasm.constants.Instructions.I64_OR;
import static org.graalvm.wasm.constants.Instructions.I64_POPCNT;
import static org.graalvm.wasm.constants.Instructions.I64_REINTERPRET_F64;
import static org.graalvm.wasm.constants.Instructions.I64_REM_S;
import static org.graalvm.wasm.constants.Instructions.I64_REM_U;
import static org.graalvm.wasm.constants.Instructions.I64_ROTL;
import static org.graalvm.wasm.constants.Instructions.I64_ROTR;
import static org.graalvm.wasm.constants.Instructions.I64_SHL;
import static org.graalvm.wasm.constants.Instructions.I64_SHR_S;
import static org.graalvm.wasm.constants.Instructions.I64_SHR_U;
import static org.graalvm.wasm.constants.Instructions.I64_STORE;
import static org.graalvm.wasm.constants.Instructions.I64_STORE_16;
import static org.graalvm.wasm.constants.Instructions.I64_STORE_32;
import static org.graalvm.wasm.constants.Instructions.I64_STORE_8;
import static org.graalvm.wasm.constants.Instructions.I64_SUB;
import static org.graalvm.wasm.constants.Instructions.I64_TRUNC_F32_S;
import static org.graalvm.wasm.constants.Instructions.I64_TRUNC_F32_U;
import static org.graalvm.wasm.constants.Instructions.I64_TRUNC_F64_S;
import static org.graalvm.wasm.constants.Instructions.I64_TRUNC_F64_U;
import static org.graalvm.wasm.constants.Instructions.I64_XOR;
import static org.graalvm.wasm.constants.Instructions.IF;
import static org.graalvm.wasm.constants.Instructions.LOCAL_GET;
import static org.graalvm.wasm.constants.Instructions.LOCAL_SET;
import static org.graalvm.wasm.constants.Instructions.LOCAL_TEE;
import static org.graalvm.wasm.constants.Instructions.LOOP;
import static org.graalvm.wasm.constants.Instructions.MEMORY_GROW;
import static org.graalvm.wasm.constants.Instructions.MEMORY_SIZE;
import static org.graalvm.wasm.constants.Instructions.NOP;
import static org.graalvm.wasm.constants.Instructions.RETURN;
import static org.graalvm.wasm.constants.Instructions.SELECT;
import static org.graalvm.wasm.constants.Instructions.UNREACHABLE;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import org.graalvm.wasm.Assert;
import org.graalvm.wasm.BinaryStreamParser;
import org.graalvm.wasm.SymbolTable;
import org.graalvm.wasm.ValueTypes;
import org.graalvm.wasm.WasmCodeEntry;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmFunction;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.exception.WasmExecutionException;
import org.graalvm.wasm.exception.WasmTrap;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryException;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public final class WasmBlockNode extends WasmNode implements RepeatingNode {

    /**
     * The number of bytes in the byte constant table used by this node.
     */
    @CompilationFinal private int byteConstantLength;

    /**
     * The number of integers in the int constant table used by this node.
     */
    @CompilationFinal private int intConstantLength;

    /**
     * The number of literals in the numeric literals table used by this node.
     */
    @CompilationFinal private int longConstantLength;

    /**
     * The number of branch tables used by this node.
     */
    @CompilationFinal private int branchTableLength;

    @CompilationFinal private final int startOffset;
    @CompilationFinal private final byte returnTypeId;
    @CompilationFinal private final byte continuationTypeId;
    @CompilationFinal private final int initialStackPointer;
    @CompilationFinal private final int initialByteConstantOffset;
    @CompilationFinal private final int initialIntConstantOffset;
    @CompilationFinal private final int initialLongConstantOffset;
    @CompilationFinal private final int initialBranchTableOffset;
    @CompilationFinal private final int initialProfileOffset;
    @CompilationFinal private int profileCount;
    @CompilationFinal private ContextReference<WasmContext> rawContextReference;
    @Children private Node[] children;

    public WasmBlockNode(WasmModule wasmModule, WasmCodeEntry codeEntry, int startOffset, byte returnTypeId, byte continuationTypeId, int initialStackPointer,
                    int initialByteConstantOffset, int initialIntConstantOffset, int initialLongConstantOffset, int initialBranchTableOffset,
                    int initialProfileOffset) {
        super(wasmModule, codeEntry, -1);
        this.startOffset = startOffset;
        this.returnTypeId = returnTypeId;
        this.continuationTypeId = continuationTypeId;
        this.initialStackPointer = initialStackPointer;
        this.initialByteConstantOffset = initialByteConstantOffset;
        this.initialIntConstantOffset = initialIntConstantOffset;
        this.initialLongConstantOffset = initialLongConstantOffset;
        this.initialBranchTableOffset = initialBranchTableOffset;
        this.initialProfileOffset = initialProfileOffset;
    }

    private ContextReference<WasmContext> contextReference() {
        if (rawContextReference == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            rawContextReference = lookupContextReference(WasmLanguage.class);
        }
        return rawContextReference;
    }

    @SuppressWarnings("hiding")
    public void initialize(Node[] children, int byteLength, int byteConstantLength,
                    int intConstantLength, int longConstantLength, int branchTableLength, int profileCount) {
        initialize(byteLength);
        this.byteConstantLength = byteConstantLength;
        this.intConstantLength = intConstantLength;
        this.longConstantLength = longConstantLength;
        this.branchTableLength = branchTableLength;
        this.profileCount = profileCount;
        this.children = children;
    }

    @Override
    int byteConstantLength() {
        return byteConstantLength;
    }

    @Override
    int intConstantLength() {
        return intConstantLength;
    }

    @Override
    int longConstantLength() {
        return longConstantLength;
    }

    @Override
    int branchTableLength() {
        return branchTableLength;
    }

    @Override
    int profileCount() {
        return profileCount;
    }

    public int startOfset() {
        return startOffset;
    }

    @Override
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    public int execute(WasmContext context, VirtualFrame frame) {
        int childrenOffset = 0;
        int byteConstantOffset = initialByteConstantOffset;
        int intConstantOffset = initialIntConstantOffset;
        int longConstantOffset = initialLongConstantOffset;
        int branchTableOffset = initialBranchTableOffset;
        int stackPointer = initialStackPointer;
        int profileOffset = initialProfileOffset;
        int offset = startOffset;
        trace("block/if/loop EXECUTE");
        while (offset < startOffset + byteLength()) {
            byte byteOpcode = BinaryStreamParser.peek1(codeEntry().data(), offset);
            int opcode = byteOpcode & 0xFF;
            offset++;
            CompilerAsserts.partialEvaluationConstant(offset);
            switch (opcode) {
                case UNREACHABLE:
                    trace("unreachable");
                    throw new WasmTrap(this, "unreachable");
                case NOP:
                    trace("noop");
                    break;
                case BLOCK: {
                    WasmBlockNode block = (WasmBlockNode) children[childrenOffset];

                    // The unwind counter indicates how many levels up we need to branch from within
                    // the block.
                    trace("block ENTER");
                    int unwindCounter = block.execute(context, frame);
                    trace("block EXIT, target = %d", unwindCounter);
                    if (unwindCounter > 0) {
                        return unwindCounter - 1;
                    }

                    childrenOffset++;
                    offset += block.byteLength();
                    stackPointer += block.returnTypeLength();
                    byteConstantOffset += block.byteConstantLength();
                    intConstantOffset += block.intConstantLength();
                    longConstantOffset += block.longConstantLength();
                    branchTableOffset += block.branchTableLength();
                    profileOffset += block.profileCount();
                    break;
                }
                case LOOP: {
                    LoopNode loopNode = (LoopNode) children[childrenOffset];
                    final WasmBlockNode loopBody = (WasmBlockNode) loopNode.getRepeatingNode();

                    // The unwind counter indicates how many levels up we need to branch from within
                    // the loop block.
                    // There are three possibilities for the value of unwind counter:
                    // - A value equal to -1 indicates normal loop completion and that the flow
                    // should continue after the loop end (break out of the loop).
                    // - A value equal to 0 indicates that we need to branch to the beginning of the
                    // loop (repeat loop).
                    // This is handled internally by Truffle and the executing loop should never
                    // return 0 here.
                    // - A value larger than 0 indicates that we need to branch to a level
                    // "shallower" than the current loop block
                    // (break out of the loop and even further).
                    trace("loop ENTER");
                    int unwindCounter = ((Integer) loopNode.execute(frame));
                    trace("loop EXIT, target = %d", unwindCounter);
                    if (unwindCounter > 0) {
                        return unwindCounter - 1;
                    }
                    // The unwind counter cannot be 0 at this point.
                    assert unwindCounter == -1 : "Unwind counter after loop exit: " + unwindCounter;

                    childrenOffset++;
                    offset += loopBody.byteLength();
                    stackPointer += loopBody.returnTypeLength();
                    byteConstantOffset += loopBody.byteConstantLength();
                    intConstantOffset += loopBody.intConstantLength();
                    longConstantOffset += loopBody.longConstantLength();
                    branchTableOffset += loopBody.branchTableLength();
                    profileOffset += loopBody.profileCount();
                    break;
                }
                case IF: {
                    WasmIfNode ifNode = (WasmIfNode) children[childrenOffset];
                    stackPointer--;
                    trace("if ENTER");
                    int unwindCounter = ifNode.execute(context, frame);
                    trace("if EXIT, target = %d", unwindCounter);
                    if (unwindCounter > 0) {
                        return unwindCounter - 1;
                    }
                    childrenOffset++;
                    offset += ifNode.byteLength();
                    stackPointer += ifNode.returnTypeLength();
                    byteConstantOffset += ifNode.byteConstantLength();
                    intConstantOffset += ifNode.intConstantLength();
                    longConstantOffset += ifNode.longConstantLength();
                    branchTableOffset += ifNode.branchTableLength();
                    profileOffset += ifNode.profileCount();
                    break;
                }
                case ELSE:
                    break;
                case END:
                    break;
                case BR: {
                    // region Load LEB128 Unsigned32 -> unwindCounter
                    int unwindCounter = unsignedIntConstant(offset, intConstantOffset);
                    int offsetDelta = offsetDelta(offset, byteConstantOffset);
                    intConstantOffset += intConstantDelta(offset);
                    byteConstantOffset += byteConstantDelta(offset);
                    offset += offsetDelta;
                    // endregion

                    // Reset the stack pointer to the target block stack pointer.
                    // region Load int continuationStackPointer
                    int continuationStackPointer = codeEntry().intConstant(intConstantOffset);
                    intConstantOffset++;
                    // endregion
                    // region Load int targetBlockReturnLength
                    int targetBlockReturnLength = codeEntry().intConstant(intConstantOffset);
                    intConstantOffset++;
                    // endregion

                    trace("br, target = %d", unwindCounter);

                    // Populate the stack with the return values of the current block (the one we
                    // are escaping from).
                    unwindStack(frame, stackPointer, continuationStackPointer, targetBlockReturnLength);

                    return unwindCounter;
                }
                case BR_IF: {
                    stackPointer--;
                    // region Load LEB128 Unsigned32 -> unwindCounter
                    int unwindCounter = unsignedIntConstant(offset, intConstantOffset);
                    int offsetDelta = offsetDelta(offset, byteConstantOffset);
                    intConstantOffset += intConstantDelta(offset);
                    byteConstantOffset += byteConstantDelta(offset);
                    offset += offsetDelta;
                    // endregion
                    // region Load int continuationStackPointer
                    int continuationStackPointer = codeEntry().intConstant(intConstantOffset);
                    intConstantOffset++;
                    // endregion
                    // region Load int targetBlockReturnLength
                    int targetBlockReturnLength = codeEntry().intConstant(intConstantOffset);
                    intConstantOffset++;
                    // endregion

                    boolean condition = codeEntry().profileCondition(profileOffset, popCondition(frame, stackPointer));
                    ++profileOffset;

                    if (condition) {
                        trace("br_if, target = %d", unwindCounter);

                        // Populate the stack with the return values of the current block (the one
                        // we are escaping from).
                        unwindStack(frame, stackPointer, continuationStackPointer, targetBlockReturnLength);

                        return unwindCounter;
                    }
                    break;
                }
                case BR_TABLE: {
                    stackPointer--;
                    int index = popInt(frame, stackPointer);
                    int[] table = codeEntry().branchTable(branchTableOffset);
                    index = index < 0 || index >= (table.length - 1) / 2 ? (table.length - 1) / 2 - 1 : index;
                    // Technically, we should increment the branchTableOffset at this point,
                    // but since we are returning, it does not really matter.

                    int returnTypeLength = table[0];

                    for (int i = 0; i < (table.length - 1) / 2; ++i) {
                        if (i == index) {
                            int unwindCounter = table[1 + 2 * i];
                            int continuationStackPointer = table[1 + 2 * i + 1];
                            trace("br_table, target = %d", unwindCounter);

                            // Populate the stack with the return values of the current block (the
                            // one we are escaping from).
                            unwindStack(frame, stackPointer, continuationStackPointer, returnTypeLength);

                            return unwindCounter;
                        }
                    }
                    throw new WasmExecutionException(this, "Should not reach here");
                }
                case RETURN: {
                    // A return statement causes the termination of the current function, i.e.
                    // causes the execution
                    // to resume after the instruction that invoked the current frame.
                    // region Load int unwindCounterValue
                    int unwindCounter = codeEntry().intConstant(intConstantOffset);
                    intConstantOffset++;
                    // endregion
                    // region Load int rootBlockReturnLength
                    int rootBlockReturnLength = codeEntry().intConstant(intConstantOffset);
                    intConstantOffset++;
                    // endregion
                    unwindStack(frame, stackPointer, 0, rootBlockReturnLength);
                    trace("return");
                    return unwindCounter;
                }
                case CALL: {
                    // region Load LEB128 Unsigned32 -> functionIndex
                    int functionIndex = unsignedIntConstant(offset, intConstantOffset);
                    int offsetDelta = offsetDelta(offset, byteConstantOffset);
                    intConstantOffset += intConstantDelta(offset);
                    byteConstantOffset += byteConstantDelta(offset);
                    offset += offsetDelta;
                    // endregion

                    WasmFunction function = module().symbolTable().function(functionIndex);
                    byte returnType = function.returnType();
                    int numArgs = function.numArguments();

                    DirectCallNode callNode = (DirectCallNode) children[childrenOffset];
                    childrenOffset++;

                    Object[] args = createArgumentsForCall(frame, function, numArgs, stackPointer);
                    stackPointer -= args.length;

                    trace("direct call to function %s (%d args)", function, args.length);
                    Object result = callNode.call(args);
                    trace("return from direct call to function %s : %s", function, result);
                    // At the moment, WebAssembly functions may return up to one value.
                    // As per the WebAssembly specification,
                    // this restriction may be lifted in the future.
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
                        case ValueTypes.VOID_TYPE: {
                            // Void return type - do nothing.
                            break;
                        }
                        default: {
                            throw new WasmTrap(this, "Unknown return type: " + returnType);
                        }
                    }

                    break;
                }
                case CALL_INDIRECT: {
                    // Extract the function object.
                    stackPointer--;
                    final SymbolTable symtab = module().symbolTable();
                    final Object[] elements = symtab.table().elements();
                    final int elementIndex = popInt(frame, stackPointer);
                    if (elementIndex < 0 || elementIndex >= elements.length) {
                        throw new WasmTrap(this, "Element index '" + elementIndex + "' out of table bounds.");
                    }
                    // Currently, table elements may only be functions.
                    // We can add a check here when this changes in the future.
                    final WasmFunction function = (WasmFunction) elements[elementIndex];
                    if (function == null) {
                        throw new WasmTrap(this, "Table element at index " + elementIndex + " is uninitialized.");
                    }

                    // Extract the function type index.
                    // region Load LEB128 Unsigned32 -> expectedFunctionTypeIndex
                    int expectedFunctionTypeIndex = unsignedIntConstant(offset, intConstantOffset);
                    int offsetDelta = offsetDelta(offset, byteConstantOffset);
                    intConstantOffset += intConstantDelta(offset);
                    byteConstantOffset += byteConstantDelta(offset);
                    offset += offsetDelta;
                    // endregion
                    int expectedTypeEquivalenceClass = symtab.equivalenceClass(expectedFunctionTypeIndex);
                    // Consume the ZERO_TABLE constant at the end of the CALL_INDIRECT instruction.
                    // TODO: Add validation that this is really zero.
                    offset += 1;

                    // Validate that the function type matches the expected type.
                    if (expectedTypeEquivalenceClass != function.typeEquivalenceClass()) {
                        // TODO: This check may be too rigorous, as the WebAssembly specification
                        // seems to allow multiple definitions of the same type.
                        // We should refine the check.
                        throw new WasmTrap(this, Assert.format("Actual (type %d of function %s) and expected (type %d in module %s) types differ in the indirect call.",
                                        function.typeIndex(), function.name(), expectedFunctionTypeIndex, module().name()));
                    }

                    // Invoke the resolved function.
                    WasmIndirectCallNode callNode = (WasmIndirectCallNode) children[childrenOffset];
                    childrenOffset++;

                    int numArgs = module().symbolTable().functionTypeArgumentCount(expectedFunctionTypeIndex);
                    Object[] args = createArgumentsForCall(frame, function, numArgs, stackPointer);
                    stackPointer -= args.length;

                    trace("indirect call to function %s (%d args)", function, args.length);
                    Object result = callNode.execute(function, args);
                    trace("return from indirect_call to function %s : %s", function, result);
                    // At the moment, WebAssembly functions may return up to one value.
                    // As per the WebAssembly specification, this restriction may be lifted in the
                    // future.
                    int returnType = module().symbolTable().functionTypeReturnType(expectedFunctionTypeIndex);
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
                        case ValueTypes.VOID_TYPE: {
                            // Void return type - do nothing.
                            break;
                        }
                        default: {
                            throw new WasmTrap(this, "Unknown return type: " + returnType);
                        }
                    }

                    break;
                }
                case DROP: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    trace("drop (raw long value = 0x%016X)", x);
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
                    trace("select 0x%08X ? 0x%08X : 0x%08X = 0x%08X", cond, val1, val2, cond != 0 ? val1 : val2);
                    break;
                }
                case LOCAL_GET: {
                    // region Load LEB128 Unsigned32 -> index
                    int index = unsignedIntConstant(offset, intConstantOffset);
                    int offsetDelta = offsetDelta(offset, byteConstantOffset);
                    intConstantOffset += intConstantDelta(offset);
                    byteConstantOffset += byteConstantDelta(offset);
                    offset += offsetDelta;
                    // endregion
                    byte type = codeEntry().localType(index);
                    switch (type) {
                        case ValueTypes.I32_TYPE: {
                            int value = getInt(frame, index);
                            pushInt(frame, stackPointer, value);
                            stackPointer++;
                            trace("local.get %d, value = 0x%08X (%d) [i32]", index, value, value);
                            break;
                        }
                        case ValueTypes.I64_TYPE: {
                            long value = getLong(frame, index);
                            push(frame, stackPointer, value);
                            stackPointer++;
                            trace("local.get %d, value = 0x%016X (%d) [i64]", index, value, value);
                            break;
                        }
                        case ValueTypes.F32_TYPE: {
                            float value = getFloat(frame, index);
                            pushFloat(frame, stackPointer, value);
                            stackPointer++;
                            trace("local.get %d, value = %f [f32]", index, value);
                            break;
                        }
                        case ValueTypes.F64_TYPE: {
                            double value = getDouble(frame, index);
                            pushDouble(frame, stackPointer, value);
                            stackPointer++;
                            trace("local.get %d, value = %f [f64]", index, value);
                            break;
                        }
                        default: {
                            throw new WasmTrap(this, "Local variable cannot have the void type.");
                        }
                    }
                    break;
                }
                case LOCAL_SET: {
                    // region Load LEB128 Unsigned32 -> index
                    int index = unsignedIntConstant(offset, intConstantOffset);
                    int offsetDelta = offsetDelta(offset, byteConstantOffset);
                    intConstantOffset += intConstantDelta(offset);
                    byteConstantOffset += byteConstantDelta(offset);
                    offset += offsetDelta;
                    // endregion
                    byte type = codeEntry().localType(index);
                    switch (type) {
                        case ValueTypes.I32_TYPE: {
                            stackPointer--;
                            int value = popInt(frame, stackPointer);
                            setInt(frame, index, value);
                            trace("local.set %d, value = 0x%08X (%d) [i32]", index, value, value);
                            break;
                        }
                        case ValueTypes.I64_TYPE: {
                            stackPointer--;
                            long value = pop(frame, stackPointer);
                            setLong(frame, index, value);
                            trace("local.set %d, value = 0x%016X (%d) [i64]", index, value, value);
                            break;
                        }
                        case ValueTypes.F32_TYPE: {
                            stackPointer--;
                            float value = popAsFloat(frame, stackPointer);
                            setFloat(frame, index, value);
                            trace("local.set %d, value = %f [f32]", index, value);
                            break;
                        }
                        case ValueTypes.F64_TYPE: {
                            stackPointer--;
                            double value = popAsDouble(frame, stackPointer);
                            setDouble(frame, index, value);
                            trace("local.set %d, value = %f [f64]", index, value);
                            break;
                        }
                        default: {
                            throw new WasmTrap(this, "Local variable cannot have the void type.");
                        }
                    }
                    break;
                }
                case LOCAL_TEE: {
                    // region Load LEB128 Unsigned32 -> index
                    int index = unsignedIntConstant(offset, intConstantOffset);
                    int offsetDelta = offsetDelta(offset, byteConstantOffset);
                    intConstantOffset += intConstantDelta(offset);
                    byteConstantOffset += byteConstantDelta(offset);
                    offset += offsetDelta;
                    // endregion
                    byte type = codeEntry().localType(index);
                    switch (type) {
                        case ValueTypes.I32_TYPE: {
                            stackPointer--;
                            int value = popInt(frame, stackPointer);
                            pushInt(frame, stackPointer, value);
                            stackPointer++;
                            setInt(frame, index, value);
                            trace("local.tee %d, value = 0x%08X (%d) [i32]", index, value, value);
                            break;
                        }
                        case ValueTypes.I64_TYPE: {
                            stackPointer--;
                            long value = pop(frame, stackPointer);
                            push(frame, stackPointer, value);
                            stackPointer++;
                            setLong(frame, index, value);
                            trace("local.tee %d, value = 0x%016X (%d) [i64]", index, value, value);
                            break;
                        }
                        case ValueTypes.F32_TYPE: {
                            stackPointer--;
                            float value = popAsFloat(frame, stackPointer);
                            pushFloat(frame, stackPointer, value);
                            stackPointer++;
                            setFloat(frame, index, value);
                            trace("local.tee %d, value = %f [f32]", index, value);
                            break;
                        }
                        case ValueTypes.F64_TYPE: {
                            stackPointer--;
                            double value = popAsDouble(frame, stackPointer);
                            pushDouble(frame, stackPointer, value);
                            stackPointer++;
                            setDouble(frame, index, value);
                            trace("local.tee %d, value = %f [f64]", index, value);
                            break;
                        }
                        default: {
                            throw new WasmTrap(this, "Local variable cannot have the void type.");
                        }
                    }
                    break;
                }
                case GLOBAL_GET: {
                    // region Load LEB128 Unsigned32 -> index
                    int index = unsignedIntConstant(offset, intConstantOffset);
                    int offsetDelta = offsetDelta(offset, byteConstantOffset);
                    intConstantOffset += intConstantDelta(offset);
                    byteConstantOffset += byteConstantDelta(offset);
                    offset += offsetDelta;
                    // endregion

                    byte type = module().symbolTable().globalValueType(index);
                    switch (type) {
                        case ValueTypes.I32_TYPE: {
                            int address = module().symbolTable().globalAddress(index);
                            int value = context.globals().loadAsInt(address);
                            pushInt(frame, stackPointer, value);
                            stackPointer++;
                            trace("global.get %d, value = 0x%08X (%d) [i32]", index, value, value);
                            break;
                        }
                        case ValueTypes.I64_TYPE: {
                            int address = module().symbolTable().globalAddress(index);
                            long value = context.globals().loadAsLong(address);
                            push(frame, stackPointer, value);
                            stackPointer++;
                            trace("global.get %d, value = 0x%016X (%d) [i64]", index, value, value);
                            break;
                        }
                        case ValueTypes.F32_TYPE: {
                            int address = module().symbolTable().globalAddress(index);
                            int value = context.globals().loadAsInt(address);
                            pushInt(frame, stackPointer, value);
                            stackPointer++;
                            trace("global.get %d, value = %f [f32]", index, Float.intBitsToFloat(value));
                            break;
                        }
                        case ValueTypes.F64_TYPE: {
                            int address = module().symbolTable().globalAddress(index);
                            long value = context.globals().loadAsLong(address);
                            push(frame, stackPointer, value);
                            stackPointer++;
                            trace("global.get %d, value = %f [f64]", index, Double.longBitsToDouble(value));
                            break;
                        }
                        default: {
                            throw new WasmTrap(this, "Local variable cannot have the void type.");
                        }
                    }
                    break;
                }
                case GLOBAL_SET: {
                    // region Load LEB128 Unsigned32 -> index
                    int index = unsignedIntConstant(offset, intConstantOffset);
                    int offsetDelta = offsetDelta(offset, byteConstantOffset);
                    intConstantOffset += intConstantDelta(offset);
                    byteConstantOffset += byteConstantDelta(offset);
                    offset += offsetDelta;
                    // endregion

                    byte type = module().symbolTable().globalValueType(index);
                    // For global.set, we don't need to make sure that the referenced global is
                    // mutable.
                    // This is taken care of by validation during wat to wasm compilation.
                    switch (type) {
                        case ValueTypes.I32_TYPE: {
                            stackPointer--;
                            int value = popInt(frame, stackPointer);
                            int address = module().symbolTable().globalAddress(index);
                            context.globals().storeInt(address, value);
                            trace("global.set %d, value = 0x%08X (%d) [i32]", index, value, value);
                            break;
                        }
                        case ValueTypes.I64_TYPE: {
                            stackPointer--;
                            long value = pop(frame, stackPointer);
                            int address = module().symbolTable().globalAddress(index);
                            context.globals().storeLong(address, value);
                            trace("global.set %d, value = 0x%016X (%d) [i64]", index, value, value);
                            break;
                        }
                        case ValueTypes.F32_TYPE: {
                            stackPointer--;
                            int value = popInt(frame, stackPointer);
                            int address = module().symbolTable().globalAddress(index);
                            context.globals().storeFloatWithInt(address, value);
                            trace("global.set %d, value = %f [f32]", index, Float.intBitsToFloat(value));
                            break;
                        }
                        case ValueTypes.F64_TYPE: {
                            stackPointer--;
                            long value = pop(frame, stackPointer);
                            int address = module().symbolTable().globalAddress(index);
                            context.globals().storeDoubleWithLong(address, value);
                            trace("global.set %d, value = %f [f64]", index, Double.longBitsToDouble(value));
                            break;
                        }
                        default: {
                            throw new WasmTrap(this, "Local variable cannot have the void type.");
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
                    int memAlignOffsetDelta = offsetDelta(offset, byteConstantOffset);
                    byteConstantOffset += byteConstantDelta(offset);
                    offset += memAlignOffsetDelta;

                    // region Load LEB128 Unsigned32 -> memOffset
                    int memOffset = unsignedIntConstant(offset, intConstantOffset);
                    int offsetDelta = offsetDelta(offset, byteConstantOffset);
                    intConstantOffset += intConstantDelta(offset);
                    byteConstantOffset += byteConstantDelta(offset);
                    offset += offsetDelta;
                    // endregion

                    stackPointer--;
                    int baseAddress = popInt(frame, stackPointer);
                    int address = baseAddress + memOffset;
                    WasmMemory memory = module().symbolTable().memory();

                    try {
                        switch (opcode) {
                            case I32_LOAD: {
                                int value = memory.load_i32(this, address);
                                pushInt(frame, stackPointer, value);
                                break;
                            }
                            case I64_LOAD: {
                                long value = memory.load_i64(this, address);
                                push(frame, stackPointer, value);
                                break;
                            }
                            case F32_LOAD: {
                                float value = memory.load_f32(this, address);
                                pushFloat(frame, stackPointer, value);
                                break;
                            }
                            case F64_LOAD: {
                                double value = memory.load_f64(this, address);
                                pushDouble(frame, stackPointer, value);
                                break;
                            }
                            case I32_LOAD8_S: {
                                int value = memory.load_i32_8s(this, address);
                                pushInt(frame, stackPointer, value);
                                break;
                            }
                            case I32_LOAD8_U: {
                                int value = memory.load_i32_8u(this, address);
                                pushInt(frame, stackPointer, value);
                                break;
                            }
                            case I32_LOAD16_S: {
                                int value = memory.load_i32_16s(this, address);
                                pushInt(frame, stackPointer, value);
                                break;
                            }
                            case I32_LOAD16_U: {
                                int value = memory.load_i32_16u(this, address);
                                pushInt(frame, stackPointer, value);
                                break;
                            }
                            case I64_LOAD8_S: {
                                long value = memory.load_i64_8s(this, address);
                                push(frame, stackPointer, value);
                                break;
                            }
                            case I64_LOAD8_U: {
                                long value = memory.load_i64_8u(this, address);
                                push(frame, stackPointer, value);
                                break;
                            }
                            case I64_LOAD16_S: {
                                long value = memory.load_i64_16s(this, address);
                                push(frame, stackPointer, value);
                                break;
                            }
                            case I64_LOAD16_U: {
                                long value = memory.load_i64_16u(this, address);
                                push(frame, stackPointer, value);
                                break;
                            }
                            case I64_LOAD32_S: {
                                long value = memory.load_i64_32s(this, address);
                                push(frame, stackPointer, value);
                                break;
                            }
                            case I64_LOAD32_U: {
                                long value = memory.load_i64_32u(this, address);
                                push(frame, stackPointer, value);
                                break;
                            }
                            default: {
                                throw new WasmTrap(this, "Unknown load opcode: " + opcode);
                            }
                        }
                    } catch (WasmMemoryException e) {
                        throw new WasmTrap(this, "memory address out-of-bounds");
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
                    int memAlignOffsetDelta = offsetDelta(offset, byteConstantOffset);
                    byteConstantOffset += byteConstantDelta(offset);
                    offset += memAlignOffsetDelta;

                    // region Load LEB128 Unsigned32 -> memOffset
                    int memOffset = unsignedIntConstant(offset, intConstantOffset);
                    int offsetDelta = offsetDelta(offset, byteConstantOffset);
                    intConstantOffset += intConstantDelta(offset);
                    byteConstantOffset += byteConstantDelta(offset);
                    offset += offsetDelta;
                    // endregion

                    WasmMemory memory = module().symbolTable().memory();

                    try {
                        switch (opcode) {
                            case I32_STORE: {
                                stackPointer--;
                                int value = popInt(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                memory.store_i32(this, address, value);
                                break;
                            }
                            case I64_STORE: {
                                stackPointer--;
                                long value = pop(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                memory.store_i64(this, address, value);
                                break;
                            }
                            case F32_STORE: {
                                stackPointer--;
                                float value = popAsFloat(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                memory.store_f32(this, address, value);
                                break;
                            }
                            case F64_STORE: {
                                stackPointer--;
                                double value = popAsDouble(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                memory.store_f64(this, address, value);
                                break;
                            }
                            case I32_STORE_8: {
                                stackPointer--;
                                int value = popInt(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                memory.store_i32_8(this, address, (byte) value);
                                break;
                            }
                            case I32_STORE_16: {
                                stackPointer--;
                                int value = popInt(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                memory.store_i32_16(this, address, (short) value);
                                break;
                            }
                            case I64_STORE_8: {
                                stackPointer--;
                                long value = pop(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                memory.store_i64_8(this, address, (byte) value);
                                break;
                            }
                            case I64_STORE_16: {
                                stackPointer--;
                                long value = pop(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                memory.store_i64_16(this, address, (short) value);
                                break;
                            }
                            case I64_STORE_32: {
                                stackPointer--;
                                long value = pop(frame, stackPointer);
                                stackPointer--;
                                int baseAddress = popInt(frame, stackPointer);
                                int address = baseAddress + memOffset;
                                memory.store_i64_32(this, address, (int) value);
                                break;
                            }
                            default: {
                                throw new WasmTrap(this, "Unknown store opcode: " + opcode);
                            }
                        }
                    } catch (WasmMemoryException e) {
                        throw new WasmTrap(this, "memory address out-of-bounds");
                    }

                    break;
                }
                case MEMORY_SIZE: {
                    // Skip the 0x00 constant.
                    offset++;
                    trace("memory_size");
                    int pageSize = (int) (module().symbolTable().memory().pageSize());
                    pushInt(frame, stackPointer, pageSize);
                    stackPointer++;
                    break;
                }
                case MEMORY_GROW: {
                    // Skip the 0x00 constant.
                    offset++;
                    trace("memory_grow");
                    stackPointer--;
                    int extraSize = popInt(frame, stackPointer);
                    final WasmMemory memory = module().symbolTable().memory();
                    int pageSize = (int) memory.pageSize();
                    if (memory.grow(extraSize)) {
                        pushInt(frame, stackPointer, pageSize);
                        stackPointer++;
                    } else {
                        pushInt(frame, stackPointer, -1);
                        stackPointer++;
                    }
                    break;
                }
                case I32_CONST: {
                    // region Load LEB128 Signed32 -> value
                    int value = signedIntConstant(offset, intConstantOffset);
                    int offsetDelta = offsetDelta(offset, byteConstantOffset);
                    intConstantOffset += intConstantDelta(offset);
                    byteConstantOffset += byteConstantDelta(offset);
                    offset += offsetDelta;
                    // endregion
                    pushInt(frame, stackPointer, value);
                    stackPointer++;
                    trace("i32.const 0x%08X (%d)", value, value);
                    break;
                }
                case I64_CONST: {
                    // region Load LEB128 Signed64 -> value
                    long value = signedLongConstant(offset, longConstantOffset);
                    int offsetDelta = offsetDelta(offset, byteConstantOffset);
                    longConstantOffset += longConstantDelta(offset);
                    byteConstantOffset += byteConstantDelta(offset);
                    offset += offsetDelta;
                    // endregion
                    push(frame, stackPointer, value);
                    stackPointer++;
                    trace("i64.const 0x%016X (%d)", value, value);
                    break;
                }
                case I32_EQZ: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, x == 0 ? 1 : 0);
                    stackPointer++;
                    trace("0x%08X == 0x%08X ? [i32]", x, 0);
                    break;
                }
                case I32_EQ: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y == x ? 1 : 0);
                    stackPointer++;
                    trace("0x%08X == 0x%08X ? [i32]", y, x);
                    break;
                }
                case I32_NE: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y != x ? 1 : 0);
                    stackPointer++;
                    trace("0x%08X != 0x%08X ? [i32]", y, x);
                    break;
                }
                case I32_LT_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y < x ? 1 : 0);
                    stackPointer++;
                    trace("0x%08X < 0x%08X ? [i32]", y, x);
                    break;
                }
                case I32_LT_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, Integer.compareUnsigned(y, x) < 0 ? 1 : 0);
                    stackPointer++;
                    trace("0x%08X <u 0x%08X ? [i32]", y, x);
                    break;
                }
                case I32_GT_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y > x ? 1 : 0);
                    stackPointer++;
                    trace("0x%08X > 0x%08X ? [i32]", y, x);
                    break;
                }
                case I32_GT_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, Integer.compareUnsigned(y, x) > 0 ? 1 : 0);
                    stackPointer++;
                    trace("0x%08X >u 0x%08X ? [i32]", y, x);
                    break;
                }
                case I32_LE_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y <= x ? 1 : 0);
                    stackPointer++;
                    trace("0x%08X <= 0x%08X ? [i32]", y, x);
                    break;
                }
                case I32_LE_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, Integer.compareUnsigned(y, x) <= 0 ? 1 : 0);
                    stackPointer++;
                    trace("0x%08X <=u 0x%08X ? [i32]", y, x);
                    break;
                }
                case I32_GE_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, y >= x ? 1 : 0);
                    stackPointer++;
                    trace("0x%08X >= 0x%08X ? [i32]", y, x);
                    break;
                }
                case I32_GE_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    pushInt(frame, stackPointer, Integer.compareUnsigned(y, x) >= 0 ? 1 : 0);
                    stackPointer++;
                    trace("0x%08X >=u 0x%08X ? [i32]", y, x);
                    break;
                }
                case I64_EQZ: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, x == 0 ? 1 : 0);
                    stackPointer++;
                    trace("0x%016X == 0x%016X ? [i64]", x, 0);
                    break;
                }
                case I64_EQ: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, y == x ? 1 : 0);
                    stackPointer++;
                    trace("0x%016X == 0x%016X ? [i64]", y, x);
                    break;
                }
                case I64_NE: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, y != x ? 1 : 0);
                    stackPointer++;
                    trace("0x%016X != 0x%016X ? [i64]", y, x);
                    break;
                }
                case I64_LT_S: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, y < x ? 1 : 0);
                    stackPointer++;
                    trace("0x%016X < 0x%016X ? [i64]", y, x);
                    break;
                }
                case I64_LT_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, Long.compareUnsigned(y, x) < 0 ? 1 : 0);
                    stackPointer++;
                    trace("0x%016X <u 0x%016X ? [i64]", y, x);
                    break;
                }
                case I64_GT_S: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, y > x ? 1 : 0);
                    stackPointer++;
                    trace("0x%016X > 0x%016X ? [i64]", y, x);
                    break;
                }
                case I64_GT_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, Long.compareUnsigned(y, x) > 0 ? 1 : 0);
                    stackPointer++;
                    trace("0x%016X >u 0x%016X ? [i64]", y, x);
                    break;
                }
                case I64_LE_S: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, y <= x ? 1 : 0);
                    stackPointer++;
                    trace("0x%016X <= 0x%016X ? [i64]", y, x);
                    break;
                }
                case I64_LE_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, Long.compareUnsigned(y, x) <= 0 ? 1 : 0);
                    stackPointer++;
                    trace("0x%016X <=u 0x%016X ? [i64]", y, x);
                    break;
                }
                case I64_GE_S: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, y >= x ? 1 : 0);
                    stackPointer++;
                    trace("0x%016X >= 0x%016X ? [i64]", y, x);
                    break;
                }
                case I64_GE_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    pushInt(frame, stackPointer, Long.compareUnsigned(y, x) >= 0 ? 1 : 0);
                    stackPointer++;
                    trace("0x%016X >=u 0x%016X ? [i64]", y, x);
                    break;
                }
                case F32_EQ: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushInt(frame, stackPointer, y == x ? 1 : 0);
                    stackPointer++;
                    trace("%f == %f ? [f32]", y, x);
                    break;
                }
                case F32_NE: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushInt(frame, stackPointer, y != x ? 1 : 0);
                    stackPointer++;
                    trace("%f != %f ? [f32]", y, x);
                    break;
                }
                case F32_LT: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushInt(frame, stackPointer, y < x ? 1 : 0);
                    stackPointer++;
                    trace("%f < %f ? [f32]", y, x);
                    break;
                }
                case F32_GT: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushInt(frame, stackPointer, y > x ? 1 : 0);
                    stackPointer++;
                    trace("%f > %f ? [f32]", y, x);
                    break;
                }
                case F32_LE: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushInt(frame, stackPointer, y <= x ? 1 : 0);
                    stackPointer++;
                    trace("%f <= %f ? [f32]", y, x);
                    break;
                }
                case F32_GE: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    pushInt(frame, stackPointer, y >= x ? 1 : 0);
                    stackPointer++;
                    trace("%f >= %f ? [f32]", y, x);
                    break;
                }
                case F64_EQ: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushInt(frame, stackPointer, y == x ? 1 : 0);
                    stackPointer++;
                    trace("%f == %f ? [f64]", y, x);
                    break;
                }
                case F64_NE: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushInt(frame, stackPointer, y != x ? 1 : 0);
                    stackPointer++;
                    trace("%f != %f ? [f64]", y, x);
                    break;
                }
                case F64_LT: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushInt(frame, stackPointer, y < x ? 1 : 0);
                    stackPointer++;
                    trace("%f < %f ? [f64]", y, x);
                    break;
                }
                case F64_GT: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushInt(frame, stackPointer, y > x ? 1 : 0);
                    stackPointer++;
                    trace("%f > %f ? [f64]", y, x);
                    break;
                }
                case F64_LE: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushInt(frame, stackPointer, y <= x ? 1 : 0);
                    stackPointer++;
                    trace("%f <= %f ? [f64]", y, x);
                    break;
                }
                case F64_GE: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    pushInt(frame, stackPointer, y >= x ? 1 : 0);
                    stackPointer++;
                    trace("%f >= %f ? [f64]", y, x);
                    break;
                }
                case I32_CLZ: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    int result = Integer.numberOfLeadingZeros(x);
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("clz(0x%08X) = %d [i32]", x, result);
                    break;
                }
                case I32_CTZ: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    int result = Integer.numberOfTrailingZeros(x);
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("ctz(0x%08X) = %d [i32]", x, result);
                    break;
                }
                case I32_POPCNT: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    int result = Integer.bitCount(x);
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("popcnt(0x%08X) = %d [i32]", x, result);
                    break;
                }
                case I32_ADD: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    int result = y + x;
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%08X + 0x%08X = 0x%08X (%d) [i32]", y, x, result, result);
                    break;
                }
                case I32_SUB: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    int result = y - x;
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%08X - 0x%08X = 0x%08X (%d) [i32]", y, x, result, result);
                    break;
                }
                case I32_MUL: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    int result = y * x;
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%08X * 0x%08X = 0x%08X (%d) [i32]", y, x, result, result);
                    break;
                }
                case I32_DIV_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    int result = y / x;
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%08X / 0x%08X = 0x%08X (%d) [i32]", y, x, result, result);
                    break;
                }
                case I32_DIV_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    int result = Integer.divideUnsigned(y, x);
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%08X /u 0x%08X = 0x%08X (%d) [i32]", y, x, result, result);
                    break;
                }
                case I32_REM_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    int result = y % x;
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%08X %% 0x%08X = 0x%08X (%d) [i32]", y, x, result, result);
                    break;
                }
                case I32_REM_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    int result = Integer.remainderUnsigned(y, x);
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%08X %%u 0x%08X = 0x%08X (%d) [i32]", y, x, result, result);
                    break;
                }
                case I32_AND: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    int result = y & x;
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%08X & 0x%08X = 0x%08X (%d) [i32]", y, x, result, result);
                    break;
                }
                case I32_OR: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    int result = y | x;
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%08X | 0x%08X = 0x%08X (%d) [i32]", y, x, result, result);
                    break;
                }
                case I32_XOR: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    int result = y ^ x;
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%08X ^ 0x%08X = 0x%08X (%d) [i32]", y, x, result, result);
                    break;
                }
                case I32_SHL: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    int result = y << x;
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%08X << 0x%08X = 0x%08X (%d) [i32]", y, x, result, result);
                    break;
                }
                case I32_SHR_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    int result = y >> x;
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%08X >> 0x%08X = 0x%08X (%d) [i32]", y, x, result, result);
                    break;
                }
                case I32_SHR_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    int result = y >>> x;
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%08X >>> 0x%08X = 0x%08X (%d) [i32]", y, x, result, result);
                    break;
                }
                case I32_ROTL: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    int result = Integer.rotateLeft(y, x);
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%08X rotl 0x%08X = 0x%08X (%d) [i32]", y, x, result, result);
                    break;
                }
                case I32_ROTR: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    stackPointer--;
                    int y = popInt(frame, stackPointer);
                    int result = Integer.rotateRight(y, x);
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%08X rotr 0x%08X = 0x%08X (%d) [i32]", y, x, result, result);
                    break;
                }
                case I64_CLZ: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    long result = Long.numberOfLeadingZeros(x);
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("clz(0x%016X) = 0x%08X (%d) [i32]", x, result, result);
                    break;
                }
                case I64_CTZ: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    long result = Long.numberOfTrailingZeros(x);
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("ctz(0x%016X) = 0x%08X (%d) [i32]", x, result, result);
                    break;
                }
                case I64_POPCNT: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    long result = Long.bitCount(x);
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("popcnt(0x%016X) = 0x%08X (%d) [i32]", x, result, result);
                    break;
                }
                case I64_ADD: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    long result = y + x;
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%016X + 0x%016X = 0x%016X (%d) [i64]", y, x, result, result);
                    break;
                }
                case I64_SUB: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    long result = y - x;
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%016X - 0x%016X = 0x%016X (%d) [i64]", y, x, result, result);
                    break;
                }
                case I64_MUL: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    long result = y * x;
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%016X * 0x%016X = 0x%016X (%d) [i64]", y, x, result, result);
                    break;
                }
                case I64_DIV_S: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    long result = y / x;
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%016X / 0x%016X = 0x%016X (%d) [i64]", y, x, result, result);
                    break;
                }
                case I64_DIV_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    long result = Long.divideUnsigned(y, x);
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%016X /u 0x%016X = 0x%016X (%d) [i64]", y, x, result, result);
                    break;
                }
                case I64_REM_S: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    long result = y % x;
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%016X %% 0x%016X = 0x%016X (%d) [i64]", y, x, result, result);
                    break;
                }
                case I64_REM_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    long result = Long.remainderUnsigned(y, x);
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%016X %%u 0x%016X = 0x%016X (%d) [i64]", y, x, result, result);
                    break;
                }
                case I64_AND: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    long result = y & x;
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%016X & 0x%016X = 0x%016X (%d) [i64]", y, x, result, result);
                    break;
                }
                case I64_OR: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    long result = y | x;
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%016X | 0x%016X = 0x%016X (%d) [i64]", y, x, result, result);
                    break;
                }
                case I64_XOR: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    long result = y ^ x;
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%016X ^ 0x%016X = 0x%016X (%d) [i64]", y, x, result, result);
                    break;
                }
                case I64_SHL: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    long result = y << x;
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%016X << 0x%016X = 0x%016X (%d) [i64]", y, x, result, result);
                    break;
                }
                case I64_SHR_S: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    long result = y >> x;
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%016X >> 0x%016X = 0x%016X (%d) [i64]", y, x, result, result);
                    break;
                }
                case I64_SHR_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    long result = y >>> x;
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%016X >>> 0x%016X = 0x%016X (%d) [i64]", y, x, result, result);
                    break;
                }
                case I64_ROTL: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    long result = Long.rotateLeft(y, (int) x);
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%016X rotl 0x%016X = 0x%016X (%d) [i64]", y, x, result, result);
                    break;
                }
                case I64_ROTR: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    stackPointer--;
                    long y = pop(frame, stackPointer);
                    long result = Long.rotateRight(y, (int) x);
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push 0x%016X rotr 0x%016X = 0x%016X (%d) [i64]", y, x, result, result);
                    break;
                }
                case F32_CONST: {
                    // region Load int value
                    int value = BinaryStreamParser.peek4(codeEntry().data(), offset);
                    // endregion
                    offset += 4;
                    pushInt(frame, stackPointer, value);
                    stackPointer++;
                    trace("f32.const %f", Float.intBitsToFloat(value));
                    break;
                }
                case F32_ABS: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    float result = Math.abs(x);
                    pushFloat(frame, stackPointer, result);
                    stackPointer++;
                    trace("f32.abs(%f) = %f", x, result);
                    break;
                }
                case F32_NEG: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    float result = -x;
                    pushFloat(frame, stackPointer, result);
                    stackPointer++;
                    trace("f32.neg(%f) = %f", x, result);
                    break;
                }
                case F32_CEIL: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    float result = (float) Math.ceil(x);
                    pushFloat(frame, stackPointer, result);
                    stackPointer++;
                    trace("f32.ceil(%f) = %f", x, result);
                    break;
                }
                case F32_FLOOR: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    float result = (float) Math.floor(x);
                    pushFloat(frame, stackPointer, result);
                    stackPointer++;
                    trace("f32.floor(%f) = %f", x, result);
                    break;
                }
                case F32_TRUNC: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    float result = (int) x;
                    pushFloat(frame, stackPointer, result);
                    stackPointer++;
                    trace("f32.trunc(%f) = %f", x, result);
                    break;
                }
                case F32_NEAREST: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    float result = Math.round(x);
                    pushFloat(frame, stackPointer, result);
                    stackPointer++;
                    trace("f32.nearest(%f) = %f", x, result);
                    break;
                }
                case F32_SQRT: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    float result = (float) Math.sqrt(x);
                    pushFloat(frame, stackPointer, result);
                    stackPointer++;
                    trace("f32.sqrt(%f) = %f", x, result);
                    break;
                }
                case F32_ADD: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    float result = y + x;
                    pushFloat(frame, stackPointer, result);
                    trace("push %f + %f = %f [f32]", y, x, result);
                    stackPointer++;
                    break;
                }
                case F32_SUB: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    float result = y - x;
                    pushFloat(frame, stackPointer, result);
                    stackPointer++;
                    trace("push %f - %f = %f [f32]", y, x, result);
                    break;
                }
                case F32_MUL: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    float result = y * x;
                    pushFloat(frame, stackPointer, result);
                    stackPointer++;
                    trace("push %f * %f = %f [f32]", y, x, result);
                    break;
                }
                case F32_DIV: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    float result = y / x;
                    pushFloat(frame, stackPointer, result);
                    stackPointer++;
                    trace("push %f / %f = %f [f32]", y, x, result);
                    break;
                }
                case F32_MIN: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    float result = Math.min(y, x);
                    pushFloat(frame, stackPointer, result);
                    stackPointer++;
                    trace("push min(%f, %f) = %f [f32]", y, x, result);
                    break;
                }
                case F32_MAX: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    float result = Math.max(y, x);
                    pushFloat(frame, stackPointer, result);
                    stackPointer++;
                    trace("push max(%f, %f) = %f [f32]", y, x, result);
                    break;
                }
                case F32_COPYSIGN: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    stackPointer--;
                    float y = popAsFloat(frame, stackPointer);
                    float result = Math.copySign(y, x);
                    pushFloat(frame, stackPointer, result);
                    stackPointer++;
                    trace("push copysign(%f, %f) = %f [f32]", y, x, result);
                    break;
                }
                case F64_CONST: {
                    // region Load long value
                    long value = BinaryStreamParser.peek8(codeEntry().data(), offset);
                    // endregion
                    offset += 8;
                    push(frame, stackPointer, value);
                    stackPointer++;
                    trace("f64.const %f", Double.longBitsToDouble(value));
                    break;
                }
                case F64_ABS: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    double result = Math.abs(x);
                    pushDouble(frame, stackPointer, result);
                    stackPointer++;
                    trace("f64.abs(%f) = %f", x, result);
                    break;
                }
                case F64_NEG: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    double result = -x;
                    pushDouble(frame, stackPointer, -x);
                    stackPointer++;
                    trace("f64.neg(%f) = %f", x, result);
                    break;
                }
                case F64_CEIL: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    double result = Math.ceil(x);
                    pushDouble(frame, stackPointer, result);
                    stackPointer++;
                    trace("f64.ceil(%f) = %f", x, result);
                    break;
                }
                case F64_FLOOR: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    double result = Math.floor(x);
                    pushDouble(frame, stackPointer, result);
                    stackPointer++;
                    trace("f64.floor(%f) = %f", x, result);
                    break;
                }
                case F64_TRUNC: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    double result = (long) x;
                    pushDouble(frame, stackPointer, result);
                    stackPointer++;
                    trace("f64.trunc(%f) = %f", x, result);
                    break;
                }
                case F64_NEAREST: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    double result = Math.round(x);
                    pushDouble(frame, stackPointer, result);
                    stackPointer++;
                    trace("f64.nearest(%f) = %f", x, result);
                    break;
                }
                case F64_SQRT: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    double result = Math.sqrt(x);
                    pushDouble(frame, stackPointer, result);
                    stackPointer++;
                    trace("f64.sqrt(%f) = %f", x, result);
                    break;
                }
                case F64_ADD: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    double result = y + x;
                    pushDouble(frame, stackPointer, result);
                    stackPointer++;
                    trace("push %f + %f = %f [f64]", y, x, result);
                    break;
                }
                case F64_SUB: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    double result = y - x;
                    pushDouble(frame, stackPointer, result);
                    stackPointer++;
                    trace("push %f - %f = %f [f64]", y, x, result);
                    break;
                }
                case F64_MUL: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    double result = y * x;
                    pushDouble(frame, stackPointer, result);
                    stackPointer++;
                    trace("push %f * %f = %f [f64]", y, x, result);
                    break;
                }
                case F64_DIV: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    double result = y / x;
                    pushDouble(frame, stackPointer, result);
                    stackPointer++;
                    trace("push %f / %f = %f [f64]", y, x, result);
                    break;
                }
                case F64_MIN: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    double result = Math.min(y, x);
                    pushDouble(frame, stackPointer, result);
                    stackPointer++;
                    trace("push min(%f, %f) = %f [f64]", y, x, result);
                    break;
                }
                case F64_MAX: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    double result = Math.max(y, x);
                    pushDouble(frame, stackPointer, result);
                    stackPointer++;
                    trace("push max(%f, %f) = %f [f64]", y, x, result);
                    break;
                }
                case F64_COPYSIGN: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    stackPointer--;
                    double y = popAsDouble(frame, stackPointer);
                    double result = Math.copySign(y, x);
                    pushDouble(frame, stackPointer, result);
                    stackPointer++;
                    trace("push copysign(%f, %f) = %f [f64]", y, x, result);
                    break;
                }
                case I32_WRAP_I64: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    int result = (int) (x & 0xFFFF_FFFFL);
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push wrap_i64(0x%016X) = 0x%08X (%d) [i32]", x, result, result);
                    break;
                }
                case I32_TRUNC_F32_S:
                case I32_TRUNC_F32_U: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    int result = (int) x;
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push trunc_f32(%f) = 0x%08X (%d) [i32]", x, result, result);
                    break;
                }
                case I32_TRUNC_F64_S:
                case I32_TRUNC_F64_U: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    int result = (int) x;
                    pushInt(frame, stackPointer, result);
                    stackPointer++;
                    trace("push trunc_f64(%f) = 0x%08X (%d) [i32]", x, result, result);
                    break;
                }
                case I64_EXTEND_I32_S: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    long result = x;
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push extend_i32_s(0x%08X) = 0x%016X (%d) [i64]", x, result, result);
                    break;
                }
                case I64_EXTEND_I32_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    long result = x & 0xFFFF_FFFFL;
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push extend_i32_u(0x%08X) = 0x%016X (%d) [i64]", x, result, result);
                    break;
                }
                case I64_TRUNC_F32_S:
                case I64_TRUNC_F32_U: {
                    stackPointer--;
                    float x = popAsFloat(frame, stackPointer);
                    long result = (long) x;
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push trunc_f32(%f) = 0x%016X (%d) [i64]", x, result, result);
                    break;
                }
                case I64_TRUNC_F64_S:
                case I64_TRUNC_F64_U: {
                    stackPointer--;
                    double x = popAsDouble(frame, stackPointer);
                    long result = (long) x;
                    push(frame, stackPointer, result);
                    stackPointer++;
                    trace("push trunc_f64(%f) = 0x%016X (%d) [i64]", x, result, result);
                    break;
                }
                case F32_CONVERT_I32_S:
                case F32_CONVERT_I32_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    float result = x;
                    pushFloat(frame, stackPointer, result);
                    stackPointer++;
                    trace("push convert_i32(0x%08X) = %f [f32]", x, result);
                    break;
                }
                case F32_CONVERT_I64_S:
                case F32_CONVERT_I64_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    float result = x;
                    pushFloat(frame, stackPointer, result);
                    stackPointer++;
                    trace("push convert_i64(0x%016X) = %f [f32]", x, result);
                    break;
                }
                case F32_DEMOTE_F64: {
                    throw new NotImplementedException();
                }
                case F64_CONVERT_I32_S:
                case F64_CONVERT_I32_U: {
                    stackPointer--;
                    int x = popInt(frame, stackPointer);
                    double result = x;
                    pushDouble(frame, stackPointer, result);
                    stackPointer++;
                    trace("push convert_i32(0x%08X) = %f [f64]", x, result);
                    break;
                }
                case F64_CONVERT_I64_S:
                case F64_CONVERT_I64_U: {
                    stackPointer--;
                    long x = pop(frame, stackPointer);
                    double result = x;
                    pushDouble(frame, stackPointer, result);
                    stackPointer++;
                    trace("push convert_i64(0x%016X) = %f [f64]", x, result);
                    break;
                }
                case F64_PROMOTE_F32: {
                    throw new NotImplementedException();
                }
                case I32_REINTERPRET_F32: {
                    // As we don't store type information for the frame slots (everything is stored
                    // as raw bits in a long,
                    // and interpreted appropriately upon access), we don't need to do anything for
                    // these instructions.
                    trace("push reinterpret_f32 [i32]");
                    break;
                }
                case I64_REINTERPRET_F64: {
                    // As we don't store type information for the frame slots (everything is stored
                    // as raw bits in a long,
                    // and interpreted appropriately upon access), we don't need to do anything for
                    // these instructions.
                    trace("push reinterpret_f64 [i64]");
                    break;
                }
                case F32_REINTERPRET_I32: {
                    // As we don't store type information for the frame slots (everything is stored
                    // as raw bits in a long,
                    // and interpreted appropriately upon access), we don't need to do anything for
                    // these instructions.
                    trace("push reinterpret_i32 [f32]");
                    break;
                }
                case F64_REINTERPRET_I64: {
                    // As we don't store type information for the frame slots (everything is stored
                    // as raw bits in a long,
                    // and interpreted appropriately upon access), we don't need to do anything for
                    // these instructions.
                    trace("push reinterpret_i64 [f64]");
                    break;
                }
                default:
                    Assert.fail(Assert.format("Unknown opcode: 0x%02X", opcode));
            }
        }
        return -1;
    }

    private boolean popCondition(VirtualFrame frame, int stackPointer) {
        int condition = popInt(frame, stackPointer);
        return condition != 0;
    }

    @TruffleBoundary
    public void resolveCallNode(int childOffset) {
        final CallTarget target = ((WasmCallStubNode) children[childOffset]).function().resolveCallTarget();
        children[childOffset] = Truffle.getRuntime().createDirectCallNode(target);
    }

    @ExplodeLoop
    private Object[] createArgumentsForCall(VirtualFrame frame, WasmFunction function, int numArgs, int stackPointerOffset) {
        CompilerAsserts.partialEvaluationConstant(numArgs);
        Object[] args = new Object[numArgs];
        int stackPointer = stackPointerOffset;
        for (int i = numArgs - 1; i >= 0; --i) {
            stackPointer--;
            byte type = module().symbolTable().functionTypeArgumentTypeAt(function.typeIndex(), i);
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
                default: {
                    throw new WasmTrap(this, "Unknown type: " + type);
                }
            }
        }
        return args;
    }

    @ExplodeLoop
    private void unwindStack(VirtualFrame frame, int stackPointer, int continuationStackPointer, int returnLength) {
        CompilerAsserts.partialEvaluationConstant(stackPointer);
        CompilerAsserts.partialEvaluationConstant(returnLength);
        for (int i = 0; i < returnLength; ++i) {
            long value = pop(frame, stackPointer + i - 1);
            push(frame, continuationStackPointer + i, value);
        }
        for (int i = continuationStackPointer + returnLength; i < stackPointer; ++i) {
            pop(frame, i);
        }
    }

    @Override
    public Object initialLoopStatus() {
        return 0;
    }

    @Override
    public boolean executeRepeating(VirtualFrame frame) {
        throw new WasmExecutionException(this, "This method should never have been called.");
    }

    @Override
    public Integer executeRepeatingWithValue(VirtualFrame frame) {
        return execute(contextReference().get(), frame);
    }

    @Override
    public byte returnTypeId() {
        return returnTypeId;
    }

    public int continuationTypeLength() {
        return typeLength(continuationTypeId);
    }

    private int unsignedIntConstant(int offset, int intConstantOffset) {
        switch (module().storeConstantsPolicy) {
            case ALL:
                return codeEntry().intConstant(intConstantOffset);
            case LARGE_ONLY:
                return isLargeConstant(offset) ? codeEntry().intConstant(intConstantOffset) : codeEntry().data()[offset];
            case NONE:
                return BinaryStreamParser.peekUnsignedInt32(codeEntry().data(), offset);
            default:
                throw new WasmExecutionException(this, "Invalid StoreConstantsInPoolChoice");
        }
    }

    private int signedIntConstant(int offset, int intConstantOffset) {
        switch (module().storeConstantsPolicy) {
            case ALL:
                return codeEntry().intConstant(intConstantOffset);
            case LARGE_ONLY:
                if (isLargeConstant(offset)) {
                    return codeEntry().intConstant(intConstantOffset);
                } else {
                    int result = codeEntry().data()[offset];
                    return (result & 0x40) == 0 ? result : result | 0xffff_ff80;
                }
            case NONE:
                return BinaryStreamParser.peekSignedInt32(codeEntry().data(), offset);
            default:
                throw new WasmExecutionException(this, "Invalid StoreConstantsInPoolChoice");
        }
    }

    public long signedLongConstant(int offset, int longConstantOffset) {
        switch (module().storeConstantsPolicy) {
            case ALL:
                return codeEntry().longConstant(longConstantOffset);
            case LARGE_ONLY:
                if (isLargeConstant(offset)) {
                    return codeEntry().longConstant(longConstantOffset);
                } else {
                    long result = codeEntry().data()[offset];
                    return (result & 0x40) == 0 ? result : result | 0xffff_ffff_ffff_ff80L;
                }
            case NONE:
                return BinaryStreamParser.peekSignedInt64(codeEntry().data(), offset);
            default:
                throw new WasmExecutionException(this, "Invalid StoreConstantsInPoolChoice");
        }
    }

    private int offsetDelta(int offset, int byteConstantOffset) {
        switch (module().storeConstantsPolicy) {
            case ALL:
                return codeEntry().byteConstant(byteConstantOffset);
            case LARGE_ONLY:
                return isLargeConstant(offset) ? codeEntry().byteConstant(byteConstantOffset) : 1;
            case NONE:
                return peekLeb128Length(offset);
            default:
                throw new WasmExecutionException(this, "Invalid StoreConstantsInPoolChoice");
        }
    }

    private int longConstantDelta(int offset) {
        return constantDelta(offset);
    }

    private int intConstantDelta(int offset) {
        return constantDelta(offset);
    }

    private int byteConstantDelta(int offset) {
        return constantDelta(offset);
    }

    private int constantDelta(int offset) {
        switch (module().storeConstantsPolicy) {
            case ALL:
                return 1;
            case LARGE_ONLY:
                return isLargeConstant(offset) ? 1 : 0;
            case NONE:
                return 0;
            default:
                throw new WasmExecutionException(this, "Invalid StoreConstantsInPoolChoice");
        }
    }

    public int peekLeb128Length(int offset) {
        return BinaryStreamParser.peekLeb128Length(codeEntry().data(), offset);
    }

    private boolean isLargeConstant(int offset) {
        return (codeEntry().data()[offset] & 0x80) != 0;
    }
}
