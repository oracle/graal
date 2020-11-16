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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import org.graalvm.wasm.Assert;
import org.graalvm.wasm.BinaryStreamParser;
import org.graalvm.wasm.SymbolTable;
import org.graalvm.wasm.WasmCodeEntry;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmFunction;
import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmTable;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.memory.WasmMemoryException;

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
    @CompilationFinal private final int initialStackPointer;
    @CompilationFinal private final int initialByteConstantOffset;
    @CompilationFinal private final int initialIntConstantOffset;
    @CompilationFinal private final int initialLongConstantOffset;
    @CompilationFinal private final int initialBranchTableOffset;
    @CompilationFinal private final int initialProfileOffset;
    @CompilationFinal private int profileCount;
    @CompilationFinal private ContextReference<WasmContext> rawContextReference;
    @Children private Node[] children;

    public WasmBlockNode(WasmInstance wasmInstance, WasmCodeEntry codeEntry, int startOffset, byte returnTypeId, int initialStackPointer, int initialByteConstantOffset, int initialIntConstantOffset,
                    int initialLongConstantOffset, int initialBranchTableOffset, int initialProfileOffset) {
        super(wasmInstance, codeEntry, -1);
        this.startOffset = startOffset;
        this.returnTypeId = returnTypeId;
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
    public Object initialLoopStatus() {
        return 0;
    }

    @Override
    public boolean executeRepeating(VirtualFrame frame) {
        throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, this, "This method should never have been called.");
    }

    @Override
    public Integer executeRepeatingWithValue(VirtualFrame frame) {
        final long[] stack;
        try {
            stack = (long[]) frame.getObject(codeEntry().stackSlot());
        } catch (FrameSlotTypeException e) {
            throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, this, "Invalid object type in the stack slot.");
        }
        return execute(contextReference().get(), frame, stack);
    }

    @Override
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    public int execute(WasmContext context, VirtualFrame frame, long[] stack) {
        int childrenOffset = 0;
        int byteConstantOffset = initialByteConstantOffset;
        int intConstantOffset = initialIntConstantOffset;
        int longConstantOffset = initialLongConstantOffset;
        int branchTableOffset = initialBranchTableOffset;
        int stackPointer = initialStackPointer;
        int profileOffset = initialProfileOffset;
        int offset = startOffset;
        try {
            while (offset < startOffset + byteLength()) {
                byte byteOpcode = BinaryStreamParser.peek1(codeEntry().data(), offset);
                int opcode = byteOpcode & 0xFF;
                offset++;
                CompilerAsserts.partialEvaluationConstant(offset);
                switch (opcode) {
                    case UNREACHABLE:
                        throw WasmException.create(Failure.UNSPECIFIED_TRAP, this, "unreachable");
                    case NOP:
                        break;
                    case BLOCK: {
                        WasmBlockNode block = (WasmBlockNode) children[childrenOffset];

                        // The unwind counter indicates how many levels up we need to branch from
                        // within the block.
                        int unwindCounter = block.execute(context, frame, stack);
                        if (unwindCounter > 0) {
                            return unwindCounter - 1;
                        }

                        childrenOffset++;
                        offset += block.byteLength();
                        stackPointer += block.returnLength();
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

                        // The unwind counter indicates how many levels up we need to branch from
                        // within the loop block.
                        // There are three possibilities for the value of unwind counter:
                        // - A value equal to -1 indicates normal loop completion and that the flow
                        // should continue after the loop end (break out of the loop).
                        // - A value equal to 0 indicates that we need to branch to the beginning of
                        // the loop (repeat loop).
                        // This is handled internally by Truffle and the executing loop should never
                        // return 0 here.
                        // - A value larger than 0 indicates that we need to branch to a level
                        // "shallower" than the current loop block
                        // (break out of the loop and even further).
                        int unwindCounter = (Integer) loopNode.execute(frame);
                        if (unwindCounter > 0) {
                            return unwindCounter - 1;
                        }
                        // The unwind counter cannot be 0 at this point.
                        assert unwindCounter == -1 : "Unwind counter after loop exit: " + unwindCounter;

                        childrenOffset++;
                        offset += loopBody.byteLength();
                        stackPointer += loopBody.returnLength();
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
                        int unwindCounter = ifNode.execute(context, frame, stack);
                        if (unwindCounter > 0) {
                            return unwindCounter - 1;
                        }
                        childrenOffset++;
                        offset += ifNode.byteLength();
                        stackPointer += ifNode.returnLength();
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

                        // Populate the stack with the return values of the current block (the one
                        // we are escaping from).
                        unwindStack(stack, stackPointer, continuationStackPointer, targetBlockReturnLength);

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

                        boolean condition = codeEntry().profileCondition(profileOffset, popBoolean(stack, stackPointer));
                        ++profileOffset;

                        if (condition) {
                            // Populate the stack with the return values of the current block (the
                            // one we are escaping from).
                            unwindStack(stack, stackPointer, continuationStackPointer, targetBlockReturnLength);

                            return unwindCounter;
                        }
                        break;
                    }
                    case BR_TABLE: {
                        stackPointer--;
                        int index = popInt(stack, stackPointer);
                        int[] table = codeEntry().branchTable(branchTableOffset);
                        index = index < 0 || index >= (table.length - 1) / 2 ? (table.length - 1) / 2 - 1 : index;
                        // Technically, we should increment the branchTableOffset at this point,
                        // but since we are returning, it does not really matter.

                        int returnTypeLength = table[0];

                        for (int i = 0; i < (table.length - 1) / 2; ++i) {
                            if (i == index) {
                                int unwindCounter = table[1 + 2 * i];
                                int continuationStackPointer = table[1 + 2 * i + 1];

                                // Populate the stack with the return values of the current block
                                // (the one we are escaping from).
                                unwindStack(stack, stackPointer, continuationStackPointer, returnTypeLength);

                                return unwindCounter;
                            }
                        }
                        throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, this, "Should not reach here");
                    }
                    case RETURN: {
                        // A return statement causes the termination of the current function, i.e.
                        // causes the execution to resume after the instruction that invoked
                        // the current frame.
                        // region Load int unwindCounterValue
                        int unwindCounter = codeEntry().intConstant(intConstantOffset);
                        intConstantOffset++;
                        // endregion
                        // region Load int rootBlockReturnLength
                        int rootBlockReturnLength = codeEntry().intConstant(intConstantOffset);
                        intConstantOffset++;
                        // endregion
                        unwindStack(stack, stackPointer, 0, rootBlockReturnLength);
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

                        WasmFunction function = instance().symbolTable().function(functionIndex);
                        byte returnType = function.returnType();
                        int numArgs = function.numArguments();

                        DirectCallNode callNode = (DirectCallNode) children[childrenOffset];
                        childrenOffset++;

                        Object[] args = createArgumentsForCall(stack, function.typeIndex(), numArgs, stackPointer);
                        stackPointer -= args.length;

                        Object result = callNode.call(args);
                        // At the moment, WebAssembly functions may return up to one value.
                        // As per the WebAssembly specification,
                        // this restriction may be lifted in the future.
                        switch (returnType) {
                            case WasmType.I32_TYPE: {
                                pushInt(stack, stackPointer, (int) result);
                                stackPointer++;
                                break;
                            }
                            case WasmType.I64_TYPE: {
                                push(stack, stackPointer, (long) result);
                                stackPointer++;
                                break;
                            }
                            case WasmType.F32_TYPE: {
                                pushFloat(stack, stackPointer, (float) result);
                                stackPointer++;
                                break;
                            }
                            case WasmType.F64_TYPE: {
                                pushDouble(stack, stackPointer, (double) result);
                                stackPointer++;
                                break;
                            }
                            case WasmType.VOID_TYPE: {
                                // Void return type - do nothing.
                                break;
                            }
                            default: {
                                throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Unknown return type: %d", returnType);
                            }
                        }

                        break;
                    }
                    case CALL_INDIRECT: {
                        // Extract the function object.
                        stackPointer--;
                        final SymbolTable symtab = instance().symbolTable();
                        final WasmTable table = instance().table();
                        final Object[] elements = table.elements();
                        final int elementIndex = popInt(stack, stackPointer);
                        if (elementIndex < 0 || elementIndex >= elements.length) {
                            throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Element index '%d' out of table bounds.", elementIndex);
                        }
                        // Currently, table elements may only be functions.
                        // We can add a check here when this changes in the future.
                        final Object element = elements[elementIndex];
                        if (element == null) {
                            throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Table element at index %d is uninitialized.", elementIndex);
                        }
                        final WasmFunction function;
                        final CallTarget target;
                        if (element instanceof WasmFunctionInstance) {
                            final WasmFunctionInstance functionInstance = (WasmFunctionInstance) element;
                            function = functionInstance.function();
                            target = functionInstance.target();
                        } else {
                            throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Unknown table element type: %s", element);
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
                        // Consume the ZERO_TABLE constant at the end of the CALL_INDIRECT
                        // instruction.
                        // TODO: Add validation that this is really zero.
                        offset += 1;

                        // Validate that the function type matches the expected type.
                        if (function != null && expectedTypeEquivalenceClass != function.typeEquivalenceClass()) {
                            // TODO: This check may be too rigorous, as the WebAssembly
                            // specification seems to allow multiple definitions
                            // of the same type.
                            // We should refine the check.
                            throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Actual (type %d of function %s) and expected (type %d in module %s) types differ in the indirect call.",
                                            function.typeIndex(), function.name(), expectedFunctionTypeIndex, instance().name());
                        }

                        // Invoke the resolved function.
                        WasmIndirectCallNode callNode = (WasmIndirectCallNode) children[childrenOffset];
                        childrenOffset++;

                        int numArgs = instance().symbolTable().functionTypeArgumentCount(expectedFunctionTypeIndex);
                        Object[] args = createArgumentsForCall(stack, expectedFunctionTypeIndex, numArgs, stackPointer);
                        stackPointer -= args.length;

                        final Object result = callNode.execute(target, args);
                        // At the moment, WebAssembly functions may return up to one value.
                        // As per the WebAssembly specification, this restriction may be lifted in
                        // the future.
                        int returnType = instance().symbolTable().functionTypeReturnType(expectedFunctionTypeIndex);
                        switch (returnType) {
                            case WasmType.I32_TYPE: {
                                pushInt(stack, stackPointer, (int) result);
                                stackPointer++;
                                break;
                            }
                            case WasmType.I64_TYPE: {
                                push(stack, stackPointer, (long) result);
                                stackPointer++;
                                break;
                            }
                            case WasmType.F32_TYPE: {
                                pushFloat(stack, stackPointer, (float) result);
                                stackPointer++;
                                break;
                            }
                            case WasmType.F64_TYPE: {
                                pushDouble(stack, stackPointer, (double) result);
                                stackPointer++;
                                break;
                            }
                            case WasmType.VOID_TYPE: {
                                // Void return type - do nothing.
                                break;
                            }
                            default: {
                                throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Unknown return type: %d", returnType);
                            }
                        }

                        break;
                    }
                    case DROP: {
                        stackPointer--;
                        pop(stack, stackPointer);
                        break;
                    }
                    case SELECT: {
                        stackPointer--;
                        int cond = popInt(stack, stackPointer);
                        stackPointer--;
                        long val2 = pop(stack, stackPointer);
                        stackPointer--;
                        long val1 = pop(stack, stackPointer);
                        push(stack, stackPointer, cond != 0 ? val1 : val2);
                        stackPointer++;
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
                        local_get(frame, stack, stackPointer, index);
                        stackPointer++;
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
                        stackPointer--;
                        local_set(frame, stack, stackPointer, index);
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
                        local_tee(frame, stack, stackPointer - 1, index);
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
                        global_get(context, stack, stackPointer, index);
                        stackPointer++;
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
                        stackPointer--;
                        global_set(context, stack, stackPointer, index);
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

                        load(stack, stackPointer - 1, opcode, memOffset);
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

                        store(stack, stackPointer, opcode, memOffset);
                        stackPointer -= 2;

                        break;
                    }
                    case MEMORY_SIZE: {
                        // Skip the 0x00 constant.
                        offset++;
                        int pageSize = instance().memory().pageSize();
                        pushInt(stack, stackPointer, pageSize);
                        stackPointer++;
                        break;
                    }
                    case MEMORY_GROW: {
                        // Skip the 0x00 constant.
                        offset++;
                        stackPointer--;
                        int extraSize = popInt(stack, stackPointer);
                        final WasmMemory memory = instance().memory();
                        int pageSize = memory.pageSize();
                        if (memory.grow(extraSize)) {
                            pushInt(stack, stackPointer, pageSize);
                            stackPointer++;
                        } else {
                            pushInt(stack, stackPointer, -1);
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
                        pushInt(stack, stackPointer, value);
                        stackPointer++;
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
                        push(stack, stackPointer, value);
                        stackPointer++;
                        break;
                    }
                    case I32_EQZ:
                        i32_eqz(stack, stackPointer);
                        break;
                    case I32_EQ:
                        i32_eq(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_NE:
                        i32_ne(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_LT_S:
                        i32_lt_s(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_LT_U:
                        i32_lt_u(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_GT_S:
                        i32_gt_s(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_GT_U:
                        i32_gt_u(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_LE_S:
                        i32_le_s(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_LE_U:
                        i32_le_u(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_GE_S:
                        i32_ge_s(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_GE_U:
                        i32_ge_u(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_EQZ:
                        i64_eqz(stack, stackPointer);
                        break;
                    case I64_EQ:
                        i64_eq(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_NE:
                        i64_ne(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_LT_S:
                        i64_lt_s(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_LT_U:
                        i64_lt_u(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_GT_S:
                        i64_gt_s(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_GT_U:
                        i64_gt_u(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_LE_S:
                        i64_le_s(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_LE_U:
                        i64_le_u(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_GE_S:
                        i64_ge_s(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_GE_U:
                        i64_ge_u(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F32_EQ:
                        f32_eq(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F32_NE:
                        f32_ne(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F32_LT:
                        f32_lt(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F32_GT:
                        f32_gt(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F32_LE:
                        f32_le(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F32_GE:
                        f32_ge(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F64_EQ:
                        f64_eq(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F64_NE:
                        f64_ne(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F64_LT:
                        f64_lt(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F64_GT:
                        f64_gt(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F64_LE:
                        f64_le(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F64_GE:
                        f64_ge(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_CLZ:
                        i32_clz(stack, stackPointer);
                        break;
                    case I32_CTZ:
                        i32_ctz(stack, stackPointer);
                        break;
                    case I32_POPCNT:
                        i32_popcnt(stack, stackPointer);
                        break;
                    case I32_ADD:
                        i32_add(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_SUB:
                        i32_sub(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_MUL:
                        i32_mul(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_DIV_S:
                        i32_div_s(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_DIV_U:
                        i32_div_u(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_REM_S:
                        i32_rem_s(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_REM_U:
                        i32_rem_u(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_AND:
                        i32_and(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_OR:
                        i32_or(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_XOR:
                        i32_xor(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_SHL:
                        i32_shl(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_SHR_S:
                        i32_shr_s(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_SHR_U:
                        i32_shr_u(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_ROTL:
                        i32_rotl(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_ROTR:
                        i32_rotr(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_CLZ:
                        i64_clz(stack, stackPointer);
                        break;
                    case I64_CTZ:
                        i64_ctz(stack, stackPointer);
                        break;
                    case I64_POPCNT:
                        i64_popcnt(stack, stackPointer);
                        break;
                    case I64_ADD:
                        i64_add(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_SUB:
                        i64_sub(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_MUL:
                        i64_mul(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_DIV_S:
                        i64_div_s(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_DIV_U:
                        i64_div_u(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_REM_S:
                        i64_rem_s(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_REM_U:
                        i64_rem_u(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_AND:
                        i64_and(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_OR:
                        i64_or(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_XOR:
                        i64_xor(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_SHL:
                        i64_shl(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_SHR_S:
                        i64_shr_s(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_SHR_U:
                        i64_shr_u(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_ROTL:
                        i64_rotl(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I64_ROTR:
                        i64_rotr(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F32_CONST: {
                        // region Load int value
                        int value = BinaryStreamParser.peek4(codeEntry().data(), offset);
                        // endregion
                        offset += 4;
                        pushInt(stack, stackPointer, value);
                        stackPointer++;
                        break;
                    }
                    case F32_ABS:
                        f32_abs(stack, stackPointer);
                        break;
                    case F32_NEG:
                        f32_neg(stack, stackPointer);
                        break;
                    case F32_CEIL:
                        f32_ceil(stack, stackPointer);
                        break;
                    case F32_FLOOR:
                        f32_floor(stack, stackPointer);
                        break;
                    case F32_TRUNC:
                        f32_trunc(stack, stackPointer);
                        break;
                    case F32_NEAREST:
                        f32_nearest(stack, stackPointer);
                        break;
                    case F32_SQRT:
                        f32_sqrt(stack, stackPointer);
                        break;
                    case F32_ADD:
                        f32_add(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F32_SUB:
                        f32_sub(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F32_MUL:
                        f32_mul(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F32_DIV:
                        f32_div(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F32_MIN:
                        f32_min(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F32_MAX:
                        f32_max(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F32_COPYSIGN:
                        f32_copysign(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F64_CONST: {
                        // region Load long value
                        long value = BinaryStreamParser.peek8(codeEntry().data(), offset);
                        // endregion
                        offset += 8;
                        push(stack, stackPointer, value);
                        stackPointer++;
                        break;
                    }
                    case F64_ABS:
                        f64_abs(stack, stackPointer);
                        break;
                    case F64_NEG:
                        f64_neg(stack, stackPointer);
                        break;
                    case F64_CEIL:
                        f64_ceil(stack, stackPointer);
                        break;
                    case F64_FLOOR:
                        f64_floor(stack, stackPointer);
                        break;
                    case F64_TRUNC:
                        f64_trunc(stack, stackPointer);
                        break;
                    case F64_NEAREST:
                        f64_nearest(stack, stackPointer);
                        break;
                    case F64_SQRT:
                        f64_sqrt(stack, stackPointer);
                        break;
                    case F64_ADD:
                        f64_add(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F64_SUB:
                        f64_sub(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F64_MUL:
                        f64_mul(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F64_DIV:
                        f64_div(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F64_MIN:
                        f64_min(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F64_MAX:
                        f64_max(stack, stackPointer);
                        stackPointer--;
                        break;
                    case F64_COPYSIGN:
                        f64_copysign(stack, stackPointer);
                        stackPointer--;
                        break;
                    case I32_WRAP_I64:
                        i32_wrap_i64(stack, stackPointer);
                        break;
                    case I32_TRUNC_F32_S:
                        i32_trunc_f32_s(stack, stackPointer);
                        break;
                    case I32_TRUNC_F32_U:
                        i32_trunc_f32_u(stack, stackPointer);
                        break;
                    case I32_TRUNC_F64_S:
                        i32_trunc_f64_s(stack, stackPointer);
                        break;
                    case I32_TRUNC_F64_U:
                        i32_trunc_f64_u(stack, stackPointer);
                        break;
                    case I64_EXTEND_I32_S:
                        i64_extend_i32_s(stack, stackPointer);
                        break;
                    case I64_EXTEND_I32_U:
                        i64_extend_i32_u(stack, stackPointer);
                        break;
                    case I64_TRUNC_F32_S:
                        i64_trunc_f32_s(stack, stackPointer);
                        break;
                    case I64_TRUNC_F32_U:
                        i64_trunc_f32_u(stack, stackPointer);
                        break;
                    case I64_TRUNC_F64_S:
                        i64_trunc_f64_s(stack, stackPointer);
                        break;
                    case I64_TRUNC_F64_U:
                        i64_trunc_f64_u(stack, stackPointer);
                        break;
                    case F32_CONVERT_I32_S:
                        f32_convert_i32_s(stack, stackPointer);
                        break;
                    case F32_CONVERT_I32_U:
                        f32_convert_i32_u(stack, stackPointer);
                        break;
                    case F32_CONVERT_I64_S:
                        f32_convert_i64_s(stack, stackPointer);
                        break;
                    case F32_CONVERT_I64_U:
                        f32_convert_i64_u(stack, stackPointer);
                        break;
                    case F32_DEMOTE_F64:
                        f32_demote_f64(stack, stackPointer);
                        break;
                    case F64_CONVERT_I32_S:
                        f64_convert_i32_s(stack, stackPointer);
                        break;
                    case F64_CONVERT_I32_U:
                        f64_convert_i32_u(stack, stackPointer);
                        break;
                    case F64_CONVERT_I64_S:
                        f64_convert_i64_s(stack, stackPointer);
                        break;
                    case F64_CONVERT_I64_U:
                        f64_convert_i64_u(stack, stackPointer);
                        break;
                    case F64_PROMOTE_F32:
                        f64_promote_f32(stack, stackPointer);
                        break;
                    case I32_REINTERPRET_F32:
                        // As we don't store type information for the frame slots (everything is
                        // stored as raw bits in a long,
                        // and interpreted appropriately upon access), we don't need to do anything
                        // for these instructions.
                        break;
                    case I64_REINTERPRET_F64:
                        // As we don't store type information for the frame slots (everything is
                        // stored as raw bits in a long,
                        // and interpreted appropriately upon access), we don't need to do anything
                        // for these instructions.
                        break;
                    case F32_REINTERPRET_I32:
                        // As we don't store type information for the frame slots (everything is
                        // stored as raw bits in a long,
                        // and interpreted appropriately upon access), we don't need to do anything
                        // for these instructions.
                        break;
                    case F64_REINTERPRET_I64:
                        // As we don't store type information for the frame slots (everything is
                        // stored as raw bits in a long,
                        // and interpreted appropriately upon access), we don't need to do anything
                        // for these instructions.
                        break;
                    default:
                        Assert.fail(Assert.format("Unknown opcode: 0x%02X", opcode), Failure.UNSPECIFIED_MALFORMED);
                }
            }
        } catch (ArithmeticException e) {
            throw WasmException.fromArithmeticException(this, e);
        }
        return -1;
    }

    private void load(long[] stack, int stackPointer, int opcode, int memOffset) {
        int baseAddress = popInt(stack, stackPointer);
        int address = baseAddress + memOffset;
        WasmMemory memory = instance().memory();

        try {
            switch (opcode) {
                case I32_LOAD: {
                    int value = memory.load_i32(this, address);
                    pushInt(stack, stackPointer, value);
                    break;
                }
                case I64_LOAD: {
                    long value = memory.load_i64(this, address);
                    push(stack, stackPointer, value);
                    break;
                }
                case F32_LOAD: {
                    float value = memory.load_f32(this, address);
                    pushFloat(stack, stackPointer, value);
                    break;
                }
                case F64_LOAD: {
                    double value = memory.load_f64(this, address);
                    pushDouble(stack, stackPointer, value);
                    break;
                }
                case I32_LOAD8_S: {
                    int value = memory.load_i32_8s(this, address);
                    pushInt(stack, stackPointer, value);
                    break;
                }
                case I32_LOAD8_U: {
                    int value = memory.load_i32_8u(this, address);
                    pushInt(stack, stackPointer, value);
                    break;
                }
                case I32_LOAD16_S: {
                    int value = memory.load_i32_16s(this, address);
                    pushInt(stack, stackPointer, value);
                    break;
                }
                case I32_LOAD16_U: {
                    int value = memory.load_i32_16u(this, address);
                    pushInt(stack, stackPointer, value);
                    break;
                }
                case I64_LOAD8_S: {
                    long value = memory.load_i64_8s(this, address);
                    push(stack, stackPointer, value);
                    break;
                }
                case I64_LOAD8_U: {
                    long value = memory.load_i64_8u(this, address);
                    push(stack, stackPointer, value);
                    break;
                }
                case I64_LOAD16_S: {
                    long value = memory.load_i64_16s(this, address);
                    push(stack, stackPointer, value);
                    break;
                }
                case I64_LOAD16_U: {
                    long value = memory.load_i64_16u(this, address);
                    push(stack, stackPointer, value);
                    break;
                }
                case I64_LOAD32_S: {
                    long value = memory.load_i64_32s(this, address);
                    push(stack, stackPointer, value);
                    break;
                }
                case I64_LOAD32_U: {
                    long value = memory.load_i64_32u(this, address);
                    push(stack, stackPointer, value);
                    break;
                }
                default: {
                    throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Unknown load opcode: %d", opcode);
                }
            }
        } catch (WasmMemoryException e) {
            throw WasmException.create(Failure.UNSPECIFIED_TRAP, this, "memory address out-of-bounds");
        }
    }

    private void store(long[] stack, int stackPointer, int opcode, int memOffset) {
        WasmMemory memory = instance().memory();

        try {
            switch (opcode) {
                case I32_STORE: {
                    int value = popInt(stack, stackPointer - 1);
                    int baseAddress = popInt(stack, stackPointer - 2);
                    int address = baseAddress + memOffset;
                    memory.store_i32(this, address, value);
                    break;
                }
                case I64_STORE: {
                    long value = pop(stack, stackPointer - 1);
                    int baseAddress = popInt(stack, stackPointer - 2);
                    int address = baseAddress + memOffset;
                    memory.store_i64(this, address, value);
                    break;
                }
                case F32_STORE: {
                    float value = popAsFloat(stack, stackPointer - 1);
                    int baseAddress = popInt(stack, stackPointer - 2);
                    int address = baseAddress + memOffset;
                    memory.store_f32(this, address, value);
                    break;
                }
                case F64_STORE: {
                    double value = popAsDouble(stack, stackPointer - 1);
                    int baseAddress = popInt(stack, stackPointer - 2);
                    int address = baseAddress + memOffset;
                    memory.store_f64(this, address, value);
                    break;
                }
                case I32_STORE_8: {
                    int value = popInt(stack, stackPointer - 1);
                    int baseAddress = popInt(stack, stackPointer - 2);
                    int address = baseAddress + memOffset;
                    memory.store_i32_8(this, address, (byte) value);
                    break;
                }
                case I32_STORE_16: {
                    int value = popInt(stack, stackPointer - 1);
                    int baseAddress = popInt(stack, stackPointer - 2);
                    int address = baseAddress + memOffset;
                    memory.store_i32_16(this, address, (short) value);
                    break;
                }
                case I64_STORE_8: {
                    long value = pop(stack, stackPointer - 1);
                    int baseAddress = popInt(stack, stackPointer - 2);
                    int address = baseAddress + memOffset;
                    memory.store_i64_8(this, address, (byte) value);
                    break;
                }
                case I64_STORE_16: {
                    long value = pop(stack, stackPointer - 1);
                    int baseAddress = popInt(stack, stackPointer - 2);
                    int address = baseAddress + memOffset;
                    memory.store_i64_16(this, address, (short) value);
                    break;
                }
                case I64_STORE_32: {
                    long value = pop(stack, stackPointer - 1);
                    int baseAddress = popInt(stack, stackPointer - 2);
                    int address = baseAddress + memOffset;
                    memory.store_i64_32(this, address, (int) value);
                    break;
                }
                default: {
                    throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Unknown store opcode: %d", opcode);
                }
            }
        } catch (WasmMemoryException e) {
            throw WasmException.create(Failure.UNSPECIFIED_TRAP, this, "memory address out-of-bounds");
        }
    }

    // Checkstyle: stop method name check

    private void global_set(WasmContext context, long[] stack, int stackPointer, int index) {
        byte type = instance().symbolTable().globalValueType(index);
        // For global.set, we don't need to make sure that the referenced global is
        // mutable.
        // This is taken care of by validation during wat to wasm compilation.
        switch (type) {
            case WasmType.I32_TYPE: {
                int value = popInt(stack, stackPointer);
                int address = instance().globalAddress(index);
                context.globals().storeInt(address, value);
                break;
            }
            case WasmType.I64_TYPE: {
                long value = pop(stack, stackPointer);
                int address = instance().globalAddress(index);
                context.globals().storeLong(address, value);
                break;
            }
            case WasmType.F32_TYPE: {
                int value = popInt(stack, stackPointer);
                int address = instance().globalAddress(index);
                context.globals().storeFloatWithInt(address, value);
                break;
            }
            case WasmType.F64_TYPE: {
                long value = pop(stack, stackPointer);
                int address = instance().globalAddress(index);
                context.globals().storeDoubleWithLong(address, value);
                break;
            }
            default: {
                throw WasmException.create(Failure.UNSPECIFIED_TRAP, this, "Local variable cannot have the void type.");
            }
        }
    }

    private void global_get(WasmContext context, long[] stack, int stackPointer, int index) {
        byte type = instance().symbolTable().globalValueType(index);
        switch (type) {
            case WasmType.I32_TYPE: {
                int address = instance().globalAddress(index);
                int value = context.globals().loadAsInt(address);
                pushInt(stack, stackPointer, value);
                break;
            }
            case WasmType.I64_TYPE: {
                int address = instance().globalAddress(index);
                long value = context.globals().loadAsLong(address);
                push(stack, stackPointer, value);
                break;
            }
            case WasmType.F32_TYPE: {
                int address = instance().globalAddress(index);
                int value = context.globals().loadAsInt(address);
                pushInt(stack, stackPointer, value);
                break;
            }
            case WasmType.F64_TYPE: {
                int address = instance().globalAddress(index);
                long value = context.globals().loadAsLong(address);
                push(stack, stackPointer, value);
                break;
            }
            default: {
                throw WasmException.create(Failure.UNSPECIFIED_TRAP, this, "Local variable cannot have the void type.");
            }
        }
    }

    private void local_tee(VirtualFrame frame, long[] stack, int stackPointer, int index) {
        byte type = codeEntry().localType(index);
        switch (type) {
            case WasmType.I32_TYPE: {
                int value = popInt(stack, stackPointer);
                pushInt(stack, stackPointer, value);
                setInt(frame, index, value);
                break;
            }
            case WasmType.I64_TYPE: {
                long value = pop(stack, stackPointer);
                push(stack, stackPointer, value);
                setLong(frame, index, value);
                break;
            }
            case WasmType.F32_TYPE: {
                float value = popAsFloat(stack, stackPointer);
                pushFloat(stack, stackPointer, value);
                setFloat(frame, index, value);
                break;
            }
            case WasmType.F64_TYPE: {
                double value = popAsDouble(stack, stackPointer);
                pushDouble(stack, stackPointer, value);
                setDouble(frame, index, value);
                break;
            }
            default: {
                throw WasmException.create(Failure.UNSPECIFIED_TRAP, this, "Local variable cannot have the void type.");
            }
        }
    }

    private void local_set(VirtualFrame frame, long[] stack, int stackPointer, int index) {
        byte type = codeEntry().localType(index);
        switch (type) {
            case WasmType.I32_TYPE: {
                int value = popInt(stack, stackPointer);
                setInt(frame, index, value);
                break;
            }
            case WasmType.I64_TYPE: {
                long value = pop(stack, stackPointer);
                setLong(frame, index, value);
                break;
            }
            case WasmType.F32_TYPE: {
                float value = popAsFloat(stack, stackPointer);
                setFloat(frame, index, value);
                break;
            }
            case WasmType.F64_TYPE: {
                double value = popAsDouble(stack, stackPointer);
                setDouble(frame, index, value);
                break;
            }
            default: {
                throw WasmException.create(Failure.UNSPECIFIED_TRAP, this, "Local variable cannot have the void type.");
            }
        }
    }

    private void local_get(VirtualFrame frame, long[] stack, int stackPointer, int index) {
        byte type = codeEntry().localType(index);
        switch (type) {
            case WasmType.I32_TYPE: {
                int value = getInt(frame, index);
                pushInt(stack, stackPointer, value);
                break;
            }
            case WasmType.I64_TYPE: {
                long value = getLong(frame, index);
                push(stack, stackPointer, value);
                break;
            }
            case WasmType.F32_TYPE: {
                float value = getFloat(frame, index);
                pushFloat(stack, stackPointer, value);
                break;
            }
            case WasmType.F64_TYPE: {
                double value = getDouble(frame, index);
                pushDouble(stack, stackPointer, value);
                break;
            }
            default: {
                throw WasmException.create(Failure.UNSPECIFIED_TRAP, this, "Local variable cannot have the void type.");
            }
        }
    }

    @SuppressWarnings("unused")
    private void unary_op(int opcode, long[] stack, int stackPointer) {
        switch (opcode) {
            case I32_EQZ:
                i32_eqz(stack, stackPointer);
                break;
            case I64_EQZ:
                i64_eqz(stack, stackPointer);
                break;
            case I32_CLZ:
                i32_clz(stack, stackPointer);
                break;
            case I32_CTZ:
                i32_ctz(stack, stackPointer);
                break;
            case I32_POPCNT:
                i32_popcnt(stack, stackPointer);
                break;
            case I64_CLZ:
                i64_clz(stack, stackPointer);
                break;
            case I64_CTZ:
                i64_ctz(stack, stackPointer);
                break;
            case I64_POPCNT:
                i64_popcnt(stack, stackPointer);
                break;
            case F32_ABS:
                f32_abs(stack, stackPointer);
                break;
            case F32_NEG:
                f32_neg(stack, stackPointer);
                break;
            case F32_CEIL:
                f32_ceil(stack, stackPointer);
                break;
            case F32_FLOOR:
                f32_floor(stack, stackPointer);
                break;
            case F32_TRUNC:
                f32_trunc(stack, stackPointer);
                break;
            case F32_NEAREST:
                f32_nearest(stack, stackPointer);
                break;
            case F32_SQRT:
                f32_sqrt(stack, stackPointer);
                break;
            case F64_ABS:
                f64_abs(stack, stackPointer);
                break;
            case F64_NEG:
                f64_neg(stack, stackPointer);
                break;
            case F64_CEIL:
                f64_ceil(stack, stackPointer);
                break;
            case F64_FLOOR:
                f64_floor(stack, stackPointer);
                break;
            case F64_TRUNC:
                f64_trunc(stack, stackPointer);
                break;
            case F64_NEAREST:
                f64_nearest(stack, stackPointer);
                break;
            case F64_SQRT:
                f64_sqrt(stack, stackPointer);
                break;
            case I32_WRAP_I64:
                i32_wrap_i64(stack, stackPointer);
                break;
            case I32_TRUNC_F32_S:
                i32_trunc_f32_s(stack, stackPointer);
                break;
            case I32_TRUNC_F32_U:
                i32_trunc_f32_u(stack, stackPointer);
                break;
            case I32_TRUNC_F64_S:
                i32_trunc_f64_s(stack, stackPointer);
                break;
            case I32_TRUNC_F64_U:
                i32_trunc_f64_u(stack, stackPointer);
                break;
            case I64_EXTEND_I32_S:
                i64_extend_i32_s(stack, stackPointer);
                break;
            case I64_EXTEND_I32_U:
                i64_extend_i32_u(stack, stackPointer);
                break;
            case I64_TRUNC_F32_S:
                i64_trunc_f32_s(stack, stackPointer);
                break;
            case I64_TRUNC_F32_U:
                i64_trunc_f32_u(stack, stackPointer);
                break;
            case I64_TRUNC_F64_S:
                i64_trunc_f64_s(stack, stackPointer);
                break;
            case I64_TRUNC_F64_U:
                i64_trunc_f64_u(stack, stackPointer);
                break;
            case F32_CONVERT_I32_S:
                f32_convert_i32_s(stack, stackPointer);
                break;
            case F32_CONVERT_I32_U:
                f32_convert_i32_u(stack, stackPointer);
                break;
            case F32_CONVERT_I64_S:
                f32_convert_i64_s(stack, stackPointer);
                break;
            case F32_CONVERT_I64_U:
                f32_convert_i64_u(stack, stackPointer);
                break;
            case F32_DEMOTE_F64:
                f32_demote_f64(stack, stackPointer);
                break;
            case F64_CONVERT_I32_S:
                f64_convert_i32_s(stack, stackPointer);
                break;
            case F64_CONVERT_I32_U:
                f64_convert_i32_u(stack, stackPointer);
                break;
            case F64_CONVERT_I64_S:
                f64_convert_i64_s(stack, stackPointer);
                break;
            case F64_CONVERT_I64_U:
                f64_convert_i64_u(stack, stackPointer);
                break;
            case F64_PROMOTE_F32:
                f64_promote_f32(stack, stackPointer);
                break;
            default:
                Assert.fail(Assert.format("Unexpected opcode: 0x%02X", opcode), Failure.UNSPECIFIED_INTERNAL);
        }
    }

    private void i32_eqz(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        pushInt(stack, stackPointer - 1, x == 0 ? 1 : 0);
    }

    private void i64_eqz(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        pushInt(stack, stackPointer - 1, x == 0 ? 1 : 0);
    }

    @SuppressWarnings("unused")
    private void binary_op(int opcode, long[] stack, int stackPointer) {
        switch (opcode) {
            case I32_EQ:
                i32_eq(stack, stackPointer);
                break;
            case I32_NE:
                i32_ne(stack, stackPointer);
                break;
            case I32_LT_S:
                i32_lt_s(stack, stackPointer);
                break;
            case I32_LT_U:
                i32_lt_u(stack, stackPointer);
                break;
            case I32_GT_S:
                i32_gt_s(stack, stackPointer);
                break;
            case I32_GT_U:
                i32_gt_u(stack, stackPointer);
                break;
            case I32_LE_S:
                i32_le_s(stack, stackPointer);
                break;
            case I32_LE_U:
                i32_le_u(stack, stackPointer);
                break;
            case I32_GE_S:
                i32_ge_s(stack, stackPointer);
                break;
            case I32_GE_U:
                i32_ge_u(stack, stackPointer);
                break;
            case I64_EQ:
                i64_eq(stack, stackPointer);
                break;
            case I64_NE:
                i64_ne(stack, stackPointer);
                break;
            case I64_LT_S:
                i64_lt_s(stack, stackPointer);
                break;
            case I64_LT_U:
                i64_lt_u(stack, stackPointer);
                break;
            case I64_GT_S:
                i64_gt_s(stack, stackPointer);
                break;
            case I64_GT_U:
                i64_gt_u(stack, stackPointer);
                break;
            case I64_LE_S:
                i64_le_s(stack, stackPointer);
                break;
            case I64_LE_U:
                i64_le_u(stack, stackPointer);
                break;
            case I64_GE_S:
                i64_ge_s(stack, stackPointer);
                break;
            case I64_GE_U:
                i64_ge_u(stack, stackPointer);
                break;
            case F32_EQ:
                f32_eq(stack, stackPointer);
                break;
            case F32_NE:
                f32_ne(stack, stackPointer);
                break;
            case F32_LT:
                f32_lt(stack, stackPointer);
                break;
            case F32_GT:
                f32_gt(stack, stackPointer);
                break;
            case F32_LE:
                f32_le(stack, stackPointer);
                break;
            case F32_GE:
                f32_ge(stack, stackPointer);
                break;
            case F64_EQ:
                f64_eq(stack, stackPointer);
                break;
            case F64_NE:
                f64_ne(stack, stackPointer);
                break;
            case F64_LT:
                f64_lt(stack, stackPointer);
                break;
            case F64_GT:
                f64_gt(stack, stackPointer);
                break;
            case F64_LE:
                f64_le(stack, stackPointer);
                break;
            case F64_GE:
                f64_ge(stack, stackPointer);
                break;
            case I32_ADD:
                i32_add(stack, stackPointer);
                break;
            case I32_SUB:
                i32_sub(stack, stackPointer);
                break;
            case I32_MUL:
                i32_mul(stack, stackPointer);
                break;
            case I32_DIV_S:
                i32_div_s(stack, stackPointer);
                break;
            case I32_DIV_U:
                i32_div_u(stack, stackPointer);
                break;
            case I32_REM_S:
                i32_rem_s(stack, stackPointer);
                break;
            case I32_REM_U:
                i32_rem_u(stack, stackPointer);
                break;
            case I32_AND:
                i32_and(stack, stackPointer);
                break;
            case I32_OR:
                i32_or(stack, stackPointer);
                break;
            case I32_XOR:
                i32_xor(stack, stackPointer);
                break;
            case I32_SHL:
                i32_shl(stack, stackPointer);
                break;
            case I32_SHR_S:
                i32_shr_s(stack, stackPointer);
                break;
            case I32_SHR_U:
                i32_shr_u(stack, stackPointer);
                break;
            case I32_ROTL:
                i32_rotl(stack, stackPointer);
                break;
            case I32_ROTR:
                i32_rotr(stack, stackPointer);
                break;
            case I64_ADD:
                i64_add(stack, stackPointer);
                break;
            case I64_SUB:
                i64_sub(stack, stackPointer);
                break;
            case I64_MUL:
                i64_mul(stack, stackPointer);
                break;
            case I64_DIV_S:
                i64_div_s(stack, stackPointer);
                break;
            case I64_DIV_U:
                i64_div_u(stack, stackPointer);
                break;
            case I64_REM_S:
                i64_rem_s(stack, stackPointer);
                break;
            case I64_REM_U:
                i64_rem_u(stack, stackPointer);
                break;
            case I64_AND:
                i64_and(stack, stackPointer);
                break;
            case I64_OR:
                i64_or(stack, stackPointer);
                break;
            case I64_XOR:
                i64_xor(stack, stackPointer);
                break;
            case I64_SHL:
                i64_shl(stack, stackPointer);
                break;
            case I64_SHR_S:
                i64_shr_s(stack, stackPointer);
                break;
            case I64_SHR_U:
                i64_shr_u(stack, stackPointer);
                break;
            case I64_ROTL:
                i64_rotl(stack, stackPointer);
                break;
            case I64_ROTR:
                i64_rotr(stack, stackPointer);
                break;
            case F32_ADD:
                f32_add(stack, stackPointer);
                break;
            case F32_SUB:
                f32_sub(stack, stackPointer);
                break;
            case F32_MUL:
                f32_mul(stack, stackPointer);
                break;
            case F32_DIV:
                f32_div(stack, stackPointer);
                break;
            case F32_MIN:
                f32_min(stack, stackPointer);
                break;
            case F32_MAX:
                f32_max(stack, stackPointer);
                break;
            case F32_COPYSIGN:
                f32_copysign(stack, stackPointer);
                break;
            case F64_ADD:
                f64_add(stack, stackPointer);
                break;
            case F64_SUB:
                f64_sub(stack, stackPointer);
                break;
            case F64_MUL:
                f64_mul(stack, stackPointer);
                break;
            case F64_DIV:
                f64_div(stack, stackPointer);
                break;
            case F64_MIN:
                f64_min(stack, stackPointer);
                break;
            case F64_MAX:
                f64_max(stack, stackPointer);
                break;
            case F64_COPYSIGN:
                f64_copysign(stack, stackPointer);
                break;
            default:
                Assert.fail(Assert.format("Unexpected opcode: 0x%02X", opcode), Failure.UNSPECIFIED_INTERNAL);
        }
    }

    private void i32_eq(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y == x ? 1 : 0);
    }

    private void i32_ne(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y != x ? 1 : 0);
    }

    private void i32_lt_s(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y < x ? 1 : 0);
    }

    private void i32_lt_u(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, Integer.compareUnsigned(y, x) < 0 ? 1 : 0);
    }

    private void i32_gt_s(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y > x ? 1 : 0);
    }

    private void i32_gt_u(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, Integer.compareUnsigned(y, x) > 0 ? 1 : 0);
    }

    private void i32_le_s(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y <= x ? 1 : 0);
    }

    private void i32_le_u(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, Integer.compareUnsigned(y, x) <= 0 ? 1 : 0);
    }

    private void i32_ge_s(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y >= x ? 1 : 0);
    }

    private void i32_ge_u(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, Integer.compareUnsigned(y, x) >= 0 ? 1 : 0);
    }

    private void i64_eq(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y == x ? 1 : 0);
    }

    private void i64_ne(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y != x ? 1 : 0);
    }

    private void i64_lt_s(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y < x ? 1 : 0);
    }

    private void i64_lt_u(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, Long.compareUnsigned(y, x) < 0 ? 1 : 0);
    }

    private void i64_gt_s(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y > x ? 1 : 0);
    }

    private void i64_gt_u(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, Long.compareUnsigned(y, x) > 0 ? 1 : 0);
    }

    private void i64_le_s(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y <= x ? 1 : 0);
    }

    private void i64_le_u(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, Long.compareUnsigned(y, x) <= 0 ? 1 : 0);
    }

    private void i64_ge_s(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y >= x ? 1 : 0);
    }

    private void i64_ge_u(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, Long.compareUnsigned(y, x) >= 0 ? 1 : 0);
    }

    private void f32_eq(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float y = popAsFloat(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y == x ? 1 : 0);
    }

    private void f32_ne(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float y = popAsFloat(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y != x ? 1 : 0);
    }

    private void f32_lt(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float y = popAsFloat(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y < x ? 1 : 0);
    }

    private void f32_gt(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float y = popAsFloat(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y > x ? 1 : 0);
    }

    private void f32_le(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float y = popAsFloat(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y <= x ? 1 : 0);
    }

    private void f32_ge(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float y = popAsFloat(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y >= x ? 1 : 0);
    }

    private void f64_eq(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double y = popAsDouble(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y == x ? 1 : 0);
    }

    private void f64_ne(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double y = popAsDouble(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y != x ? 1 : 0);
    }

    private void f64_lt(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double y = popAsDouble(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y < x ? 1 : 0);
    }

    private void f64_gt(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double y = popAsDouble(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y > x ? 1 : 0);
    }

    private void f64_le(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double y = popAsDouble(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y <= x ? 1 : 0);
    }

    private void f64_ge(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double y = popAsDouble(stack, stackPointer - 2);
        pushInt(stack, stackPointer - 2, y >= x ? 1 : 0);
    }

    private void i32_clz(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int result = Integer.numberOfLeadingZeros(x);
        pushInt(stack, stackPointer - 1, result);
    }

    private void i32_ctz(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int result = Integer.numberOfTrailingZeros(x);
        pushInt(stack, stackPointer - 1, result);
    }

    private void i32_popcnt(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int result = Integer.bitCount(x);
        pushInt(stack, stackPointer - 1, result);
    }

    private void i32_add(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        int result = y + x;
        pushInt(stack, stackPointer - 2, result);
    }

    private void i32_sub(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        int result = y - x;
        pushInt(stack, stackPointer - 2, result);
    }

    private void i32_mul(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        int result = y * x;
        pushInt(stack, stackPointer - 2, result);
    }

    private void i32_div_s(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        if (x == -1 && y == Integer.MIN_VALUE) {
            throw WasmException.create(Failure.INT_OVERFLOW, this);
        }
        int result = y / x;
        pushInt(stack, stackPointer - 2, result);
    }

    private void i32_div_u(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        int result = Integer.divideUnsigned(y, x);
        pushInt(stack, stackPointer - 2, result);
    }

    private void i32_rem_s(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        int result = y % x;
        pushInt(stack, stackPointer - 2, result);
    }

    private void i32_rem_u(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        int result = Integer.remainderUnsigned(y, x);
        pushInt(stack, stackPointer - 2, result);
    }

    private void i32_and(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        int result = y & x;
        pushInt(stack, stackPointer - 2, result);
    }

    private void i32_or(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        int result = y | x;
        pushInt(stack, stackPointer - 2, result);
    }

    private void i32_xor(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        int result = y ^ x;
        pushInt(stack, stackPointer - 2, result);
    }

    private void i32_shl(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        int result = y << x;
        pushInt(stack, stackPointer - 2, result);
    }

    private void i32_shr_s(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        int result = y >> x;
        pushInt(stack, stackPointer - 2, result);
    }

    private void i32_shr_u(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        int result = y >>> x;
        pushInt(stack, stackPointer - 2, result);
    }

    private void i32_rotl(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        int result = Integer.rotateLeft(y, x);
        pushInt(stack, stackPointer - 2, result);
    }

    private void i32_rotr(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        int y = popInt(stack, stackPointer - 2);
        int result = Integer.rotateRight(y, x);
        pushInt(stack, stackPointer - 2, result);
    }

    private void i64_clz(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long result = Long.numberOfLeadingZeros(x);
        push(stack, stackPointer - 1, result);
    }

    private void i64_ctz(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long result = Long.numberOfTrailingZeros(x);
        push(stack, stackPointer - 1, result);
    }

    private void i64_popcnt(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long result = Long.bitCount(x);
        push(stack, stackPointer - 1, result);
    }

    private void i64_add(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        long result = y + x;
        push(stack, stackPointer - 2, result);
    }

    private void i64_sub(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        long result = y - x;
        push(stack, stackPointer - 2, result);
    }

    private void i64_mul(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        long result = y * x;
        push(stack, stackPointer - 2, result);
    }

    private void i64_div_s(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        if (x == -1 && y == Long.MIN_VALUE) {
            throw WasmException.create(Failure.INT_OVERFLOW, this);
        }
        final long result = y / x;
        push(stack, stackPointer - 2, result);
    }

    private void i64_div_u(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        long result = Long.divideUnsigned(y, x);
        push(stack, stackPointer - 2, result);
    }

    private void i64_rem_s(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        long result = y % x;
        push(stack, stackPointer - 2, result);
    }

    private void i64_rem_u(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        long result = Long.remainderUnsigned(y, x);
        push(stack, stackPointer - 2, result);
    }

    private void i64_and(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        long result = y & x;
        push(stack, stackPointer - 2, result);
    }

    private void i64_or(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        long result = y | x;
        push(stack, stackPointer - 2, result);
    }

    private void i64_xor(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        long result = y ^ x;
        push(stack, stackPointer - 2, result);
    }

    private void i64_shl(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        long result = y << x;
        push(stack, stackPointer - 2, result);
    }

    private void i64_shr_s(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        long result = y >> x;
        push(stack, stackPointer - 2, result);
    }

    private void i64_shr_u(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        long result = y >>> x;
        push(stack, stackPointer - 2, result);
    }

    private void i64_rotl(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        long result = Long.rotateLeft(y, (int) x);
        push(stack, stackPointer - 2, result);
    }

    private void i64_rotr(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        long y = pop(stack, stackPointer - 2);
        long result = Long.rotateRight(y, (int) x);
        push(stack, stackPointer - 2, result);
    }

    private void f32_abs(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float result = Math.abs(x);
        pushFloat(stack, stackPointer - 1, result);
    }

    private void f32_neg(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float result = -x;
        pushFloat(stack, stackPointer - 1, result);
    }

    private void f32_ceil(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float result = (float) Math.ceil(x);
        pushFloat(stack, stackPointer - 1, result);
    }

    private void f32_floor(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float result = (float) Math.floor(x);
        pushFloat(stack, stackPointer - 1, result);
    }

    private void f32_trunc(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float result = (float) (x < 0.0 ? Math.ceil(x) : Math.floor(x));
        pushFloat(stack, stackPointer - 1, result);
    }

    private void f32_nearest(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float result = (float) Math.rint(x);
        pushFloat(stack, stackPointer - 1, result);
    }

    private void f32_sqrt(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float result = (float) Math.sqrt(x);
        pushFloat(stack, stackPointer - 1, result);
    }

    private void f32_add(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float y = popAsFloat(stack, stackPointer - 2);
        float result = y + x;
        pushFloat(stack, stackPointer - 2, result);
    }

    private void f32_sub(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float y = popAsFloat(stack, stackPointer - 2);
        float result = y - x;
        pushFloat(stack, stackPointer - 2, result);
    }

    private void f32_mul(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float y = popAsFloat(stack, stackPointer - 2);
        float result = y * x;
        pushFloat(stack, stackPointer - 2, result);
    }

    private void f32_div(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float y = popAsFloat(stack, stackPointer - 2);
        float result = y / x;
        pushFloat(stack, stackPointer - 2, result);
    }

    private void f32_min(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float y = popAsFloat(stack, stackPointer - 2);
        float result = Math.min(y, x);
        pushFloat(stack, stackPointer - 2, result);
    }

    private void f32_max(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float y = popAsFloat(stack, stackPointer - 2);
        float result = Math.max(y, x);
        pushFloat(stack, stackPointer - 2, result);
    }

    private void f32_copysign(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        float y = popAsFloat(stack, stackPointer - 2);
        float result = Math.copySign(y, x);
        pushFloat(stack, stackPointer - 2, result);
    }

    private void f64_abs(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double result = Math.abs(x);
        pushDouble(stack, stackPointer - 1, result);
    }

    private void f64_neg(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double result = -x;
        pushDouble(stack, stackPointer - 1, result);
    }

    private void f64_ceil(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double result = Math.ceil(x);
        pushDouble(stack, stackPointer - 1, result);
    }

    private void f64_floor(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double result = Math.floor(x);
        pushDouble(stack, stackPointer - 1, result);
    }

    private void f64_trunc(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double result = x < 0.0 ? Math.ceil(x) : Math.floor(x);
        pushDouble(stack, stackPointer - 1, result);
    }

    private void f64_nearest(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double result = Math.rint(x);
        pushDouble(stack, stackPointer - 1, result);
    }

    private void f64_sqrt(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double result = Math.sqrt(x);
        pushDouble(stack, stackPointer - 1, result);
    }

    private void f64_add(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double y = popAsDouble(stack, stackPointer - 2);
        double result = y + x;
        pushDouble(stack, stackPointer - 2, result);
    }

    private void f64_sub(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double y = popAsDouble(stack, stackPointer - 2);
        double result = y - x;
        pushDouble(stack, stackPointer - 2, result);
    }

    private void f64_mul(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double y = popAsDouble(stack, stackPointer - 2);
        double result = y * x;
        pushDouble(stack, stackPointer - 2, result);
    }

    private void f64_div(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double y = popAsDouble(stack, stackPointer - 2);
        double result = y / x;
        pushDouble(stack, stackPointer - 2, result);
    }

    private void f64_min(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double y = popAsDouble(stack, stackPointer - 2);
        double result = Math.min(y, x);
        pushDouble(stack, stackPointer - 2, result);
    }

    private void f64_max(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double y = popAsDouble(stack, stackPointer - 2);
        double result = Math.max(y, x);
        pushDouble(stack, stackPointer - 2, result);
    }

    private void f64_copysign(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        double y = popAsDouble(stack, stackPointer - 2);
        double result = Math.copySign(y, x);
        pushDouble(stack, stackPointer - 2, result);
    }

    private void i32_wrap_i64(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        int result = (int) (x & 0xFFFF_FFFFL);
        pushInt(stack, stackPointer - 1, result);
    }

    private void i32_trunc_f32_u(long[] stack, int stackPointer) {
        // TODO(mbovel): fix the i32_trunc_f32_u case.
        i32_trunc_f32_s(stack, stackPointer);
    }

    private void i32_trunc_f32_s(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        int result = (int) x;
        pushInt(stack, stackPointer - 1, result);
    }

    private void i32_trunc_f64_s(long[] stack, int stackPointer) {
        i32_trunc_f64_u(stack, stackPointer);
    }

    private void i32_trunc_f64_u(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        int result = (int) x;
        pushInt(stack, stackPointer - 1, result);
    }

    private void i64_extend_i32_s(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        long result = x;
        push(stack, stackPointer - 1, result);
    }

    private void i64_extend_i32_u(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        long result = x & 0xFFFF_FFFFL;
        push(stack, stackPointer - 1, result);
    }

    private void i64_trunc_f32_s(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        long result = (long) x;
        push(stack, stackPointer - 1, result);
    }

    private void i64_trunc_f32_u(long[] stack, int stackPointer) {
        // TODO(mbovel): fix this case.
        i64_trunc_f32_s(stack, stackPointer);
    }

    private void i64_trunc_f64_s(long[] stack, int stackPointer) {
        i64_trunc_f64_u(stack, stackPointer);
    }

    private void i64_trunc_f64_u(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        long result = (long) x;
        push(stack, stackPointer - 1, result);
    }

    private void f32_convert_i32_s(long[] stack, int stackPointer) {
        f32_convert_i32_u(stack, stackPointer);
    }

    private void f32_convert_i32_u(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        float result = x;
        pushFloat(stack, stackPointer - 1, result);
    }

    private void f32_convert_i64_s(long[] stack, int stackPointer) {
        f32_convert_i64_u(stack, stackPointer);
    }

    private void f32_convert_i64_u(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        float result = x;
        pushFloat(stack, stackPointer - 1, result);
    }

    private void f32_demote_f64(long[] stack, int stackPointer) {
        double x = popAsDouble(stack, stackPointer - 1);
        float result = (float) x;
        pushFloat(stack, stackPointer - 1, result);
    }

    private void f64_convert_i32_s(long[] stack, int stackPointer) {
        f64_convert_i32_u(stack, stackPointer);
    }

    private void f64_convert_i32_u(long[] stack, int stackPointer) {
        int x = popInt(stack, stackPointer - 1);
        double result = x;
        pushDouble(stack, stackPointer - 1, result);
    }

    private void f64_convert_i64_s(long[] stack, int stackPointer) {
        f64_convert_i64_u(stack, stackPointer);
    }

    private void f64_convert_i64_u(long[] stack, int stackPointer) {
        long x = pop(stack, stackPointer - 1);
        double result = x;
        pushDouble(stack, stackPointer - 1, result);
    }

    private void f64_promote_f32(long[] stack, int stackPointer) {
        float x = popAsFloat(stack, stackPointer - 1);
        double result = x;
        pushDouble(stack, stackPointer - 1, result);
    }

    // Checkstyle: resume method name check

    private boolean popBoolean(long[] stack, int stackPointer) {
        int condition = popInt(stack, stackPointer);
        return condition != 0;
    }

    @TruffleBoundary
    public void resolveCallNode(int childOffset) {
        final WasmFunction function = ((WasmCallStubNode) children[childOffset]).function();
        final CallTarget target = instance().target(function.index());
        children[childOffset] = Truffle.getRuntime().createDirectCallNode(target);
    }

    @ExplodeLoop
    private Object[] createArgumentsForCall(long[] stack, int functionTypeIndex, int numArgs, int stackPointerOffset) {
        CompilerAsserts.partialEvaluationConstant(numArgs);
        Object[] args = new Object[numArgs];
        int stackPointer = stackPointerOffset;
        for (int i = numArgs - 1; i >= 0; --i) {
            stackPointer--;
            byte type = instance().symbolTable().functionTypeArgumentTypeAt(functionTypeIndex, i);
            switch (type) {
                case WasmType.I32_TYPE:
                    args[i] = popInt(stack, stackPointer);
                    break;
                case WasmType.I64_TYPE:
                    args[i] = pop(stack, stackPointer);
                    break;
                case WasmType.F32_TYPE:
                    args[i] = popAsFloat(stack, stackPointer);
                    break;
                case WasmType.F64_TYPE:
                    args[i] = popAsDouble(stack, stackPointer);
                    break;
                default: {
                    throw WasmException.format(Failure.UNSPECIFIED_TRAP, this, "Unknown type: %d", type);
                }
            }
        }
        return args;
    }

    @ExplodeLoop
    private void unwindStack(long[] stack, int stackPointer, int continuationStackPointer, int returnLength) {
        CompilerAsserts.partialEvaluationConstant(stackPointer);
        CompilerAsserts.partialEvaluationConstant(returnLength);
        for (int i = 0; i < returnLength; ++i) {
            long value = pop(stack, stackPointer + i - 1);
            push(stack, continuationStackPointer + i, value);
        }
        for (int i = continuationStackPointer + returnLength; i < stackPointer; ++i) {
            pop(stack, i);
        }
    }

    @Override
    public byte returnTypeId() {
        return returnTypeId;
    }

    private int unsignedIntConstant(int offset, int intConstantOffset) {
        switch (instance().storeConstantsPolicy()) {
            case ALL:
                return codeEntry().intConstant(intConstantOffset);
            case LARGE_ONLY:
                return isLargeConstant(offset) ? codeEntry().intConstant(intConstantOffset) : codeEntry().data()[offset];
            case NONE:
                return BinaryStreamParser.peekUnsignedInt32(codeEntry().data(), offset);
            default:
                throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, this, "Invalid StoreConstantsInPoolChoice");
        }
    }

    private int signedIntConstant(int offset, int intConstantOffset) {
        switch (instance().storeConstantsPolicy()) {
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
                throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, this, "Invalid StoreConstantsInPoolChoice");
        }
    }

    public long signedLongConstant(int offset, int longConstantOffset) {
        switch (instance().storeConstantsPolicy()) {
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
                throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, this, "Invalid StoreConstantsInPoolChoice");
        }
    }

    private int offsetDelta(int offset, int byteConstantOffset) {
        switch (instance().storeConstantsPolicy()) {
            case ALL:
                return codeEntry().byteConstant(byteConstantOffset);
            case LARGE_ONLY:
                return isLargeConstant(offset) ? codeEntry().byteConstant(byteConstantOffset) : 1;
            case NONE:
                return peekLeb128Length(offset);
            default:
                throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, this, "Invalid StoreConstantsInPoolChoice");
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
        switch (instance().storeConstantsPolicy()) {
            case ALL:
                return 1;
            case LARGE_ONLY:
                return isLargeConstant(offset) ? 1 : 0;
            case NONE:
                return 0;
            default:
                throw WasmException.create(Failure.UNSPECIFIED_INTERNAL, this, "Invalid StoreConstantsInPoolChoice");
        }
    }

    public int peekLeb128Length(int offset) {
        return BinaryStreamParser.peekLeb128Length(codeEntry().data(), offset);
    }

    private boolean isLargeConstant(int offset) {
        return (codeEntry().data()[offset] & 0x80) != 0;
    }
}
