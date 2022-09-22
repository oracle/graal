/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.memory.move;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMCopyTargetLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMNoOpNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

import java.util.ArrayList;
import java.util.List;

public abstract class LLVMPrimitiveMoveNode extends LLVMNode {

    private static final int LENGTH_LIMIT_FOR_REPLACEMENT = 64;
    private static final int LENGTH_ARG_INDEX = 3;
    private static final int DEST_ARG_INDEX = 1;
    private static final int SOURCE_ARG_INDEX = 2;

    @Child private LLVMLoadNode loadNode;
    @Child private LLVMStoreNode storeNode;
    @Child private LLVMPrimitiveMoveNode nextPrimitiveMoveNode;
    private final long step;

    public LLVMPrimitiveMoveNode(LLVMLoadNode loadNode, LLVMStoreNode storeNode, LLVMPrimitiveMoveNode nextPrimitiveMoveNode, long step) {
        this.loadNode = loadNode;
        this.storeNode = storeNode;
        this.nextPrimitiveMoveNode = nextPrimitiveMoveNode;
        this.step = step;
    }

    public abstract void executeWithTarget(LLVMPointer srcPtr, LLVMPointer destPtr, boolean forwardCopy);

    @Specialization(guards = "forwardCopy")
    void moveNormalDir(LLVMPointer srcPtr, LLVMPointer destPtr, @SuppressWarnings("unused") boolean forwardCopy) {
        Object val = loadNode.executeWithTargetGeneric(srcPtr);
        storeNode.executeWithTarget(destPtr, val);
        if (nextPrimitiveMoveNode != null) {
            nextPrimitiveMoveNode.executeWithTarget(srcPtr.increment(step), destPtr.increment(step), forwardCopy);
        }
    }

    @Specialization(guards = "!forwardCopy")
    void moveReverseDir(LLVMPointer src, LLVMPointer dest, @SuppressWarnings("unused") boolean forwardCopy) {
        LLVMPointer srcPtr = src.increment(-step);
        Object val = loadNode.executeWithTargetGeneric(srcPtr);
        LLVMPointer destPtr = dest.increment(-step);
        storeNode.executeWithTarget(destPtr, val);
        if (nextPrimitiveMoveNode != null) {
            nextPrimitiveMoveNode.executeWithTarget(srcPtr, destPtr, forwardCopy);
        }
    }

    public static LLVMExpressionNode createSerialMoves(LLVMExpressionNode[] args, NodeFactory nodeFactory, LLVMMemMoveNode memMoveNode) {
        LLVMExpressionNode sourceNode = args[SOURCE_ARG_INDEX];
        LLVMExpressionNode destNode = args[DEST_ARG_INDEX];

        LLVMExpressionNode lengthArgNode = args[LENGTH_ARG_INDEX];
        if (lengthArgNode instanceof LLVMSimpleLiteralNode) {
            long len = Long.MAX_VALUE;
            if (lengthArgNode instanceof LLVMSimpleLiteralNode.LLVMI64LiteralNode) {
                len = ((LLVMSimpleLiteralNode.LLVMI64LiteralNode) lengthArgNode).doI64();
            } else if (lengthArgNode instanceof LLVMSimpleLiteralNode.LLVMI32LiteralNode) {
                len = ((LLVMSimpleLiteralNode.LLVMI32LiteralNode) lengthArgNode).doI32();
            }

            if (len <= 0) {
                return LLVMNoOpNodeGen.create();
            }

            if (len < LENGTH_LIMIT_FOR_REPLACEMENT) {
                List<Type> moveTypes = new ArrayList<>();

                long m = len;
                long n = m / 8;
                if (n > 0) {
                    for (int i = 0; i < n; i++) {
                        moveTypes.add(PrimitiveType.I64);
                    }
                }
                m = m % 8;
                n = m / 4;
                if (n > 0) {
                    for (int i = 0; i < n; i++) {
                        moveTypes.add(PrimitiveType.I32);
                    }
                }
                m = m % 4;
                n = m / 2;
                if (n > 0) {
                    for (int i = 0; i < n; i++) {
                        moveTypes.add(PrimitiveType.I16);
                    }
                }
                n = m % 2;
                if (n > 0) {
                    moveTypes.add(PrimitiveType.I8);
                }

                return createScalarMemMoveSeries(moveTypes, destNode, sourceNode, nodeFactory, len, memMoveNode);
            }
        }
        return null;
    }

    private static LLVMExpressionNode createScalarMemMoveSeries(List<Type> moveTypes, LLVMExpressionNode dest, LLVMExpressionNode source, NodeFactory nodeFactory, long length,
                    LLVMMemMoveNode memMoveNode) {
        assert !moveTypes.isEmpty();

        LLVMPrimitiveMoveNode primitiveMoveNode = null;
        for (Type memberType : moveTypes) {
            LLVMExpressionNode loadNode = nodeFactory.createExtractValue(memberType, null);
            assert loadNode instanceof LLVMLoadNode;
            LLVMStatementNode storeNode = nodeFactory.createStore(null, null, memberType);
            assert storeNode instanceof LLVMStoreNode;

            try {
                long step = nodeFactory.getDataLayout().getByteSize(memberType);
                primitiveMoveNode = LLVMPrimitiveMoveNodeGen.create((LLVMLoadNode) loadNode, (LLVMStoreNode) storeNode, primitiveMoveNode, step);
            } catch (Type.TypeOverflowException e) {
                throw Type.throwOverflowExceptionAsLLVMException(primitiveMoveNode, e);
            }
        }

        return LLVMPrimitiveMoveNodeGen.HeadNodeGen.create(length, primitiveMoveNode, memMoveNode, source, dest);
    }

    /**
     * The head of the {@link LLVMPrimitiveMoveNode load/store nodes chain}. It intercepts
     * situations that cannot be handled by the chain and must be handled specifically. In
     * particular, there are two cases:
     * <ol>
     * <li>the targets exporting {@link LLVMCopyTargetLibrary}: it concerns va_list objects that do
     * not support sequential reads/writes, but can copy from one to another using the
     * {@link LLVMCopyTargetLibrary}.</li>
     * <li>foreign managed objects: the copying mechanism uses the fallback implementation of
     * {@link com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary} and
     * {@link com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary} that is
     * built on top of the interop. However, the fallback implementation is not compatible with the
     * sequential reads/writes either, and therefore the original memmove intrinsic must be used
     * instead.</li>
     * </ol>
     */
    @NodeChild(value = "source", type = LLVMExpressionNode.class)
    @NodeChild(value = "destination", type = LLVMExpressionNode.class)
    public abstract static class HeadNode extends LLVMExpressionNode {

        private final long length;
        @Child private LLVMPrimitiveMoveNode primitiveMoveNode;
        @Child private LLVMMemMoveNode memMoveNode;

        protected HeadNode(long length, LLVMPrimitiveMoveNode primitiveMoveNode, LLVMMemMoveNode memMoveNode) {
            this.length = length;
            this.primitiveMoveNode = primitiveMoveNode;
            this.memMoveNode = memMoveNode;
        }

        public abstract Object executeWithTarget(LLVMPointer sourcePtr, LLVMPointer destPtr);

        boolean doCustomCopy(LLVMPointer sourcePtr, LLVMPointer destPtr, LLVMCopyTargetLibrary copyTargetLib) {
            Object src;
            Object dest;
            src = getReceiver(sourcePtr);
            if (src == null) {
                return false;
            }
            dest = getReceiver(destPtr);
            if (dest == null) {
                return false;
            }
            return copyTargetLib.canCopyFrom(dest, src, length);
        }

        /**
         * The receiver of {@link LLVMCopyTargetLibrary#canCopyFrom(Object, Object, long)}} invoked
         * in the guard expressions needs to be an object exporting {@link LLVMCopyTargetLibrary},
         * not {@link LLVMManagedPointer}, such as
         * {@link com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64.linux.LLVMLinuxAarch64VaListStorage}.
         * <p>
         * N.B. The {@code LLVMLinuxAarch64VaListStorage.canCopyFrom} method returns {@code true} if
         * the source is another {@code LLVMLinuxAarch64VaListStorage} or a native pointer, as both
         * are valid sources to copy from. The {@code copyFrom} implementation then uses
         * {@link com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListLibrary#copy(Object, Object, Frame)},
         * where the source argument becomes the receiver of
         * {@link com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVaListLibrary#copy}:
         * one for handling "object->object" ({@code LLVMLinuxAarch64VaListStorage.copy} message)
         * and "native->object" ({@code LLVMLinuxAarch64VaListStorage.NativeVAListWrapper.copy}
         * message).
         */
        private static Object getReceiver(LLVMPointer receiverPtr) {
            Object recv;
            if (LLVMManagedPointer.isInstance(receiverPtr)) {
                if (LLVMManagedPointer.cast(receiverPtr).getOffset() != 0) {
                    return null;
                }
                recv = LLVMManagedPointer.cast(receiverPtr).getObject();
            } else {
                recv = receiverPtr;
            }
            return recv;
        }

        boolean useMemMoveIntrinsic(LLVMPointer sourcePtr, LLVMPointer destPtr, LLVMAsForeignLibrary asForeignLib) {
            if (LLVMManagedPointer.isInstance(sourcePtr)) {
                if (LLVMManagedPointer.cast(sourcePtr).getOffset() != 0) {
                    return true;
                }
                Object recv = LLVMManagedPointer.cast(sourcePtr).getObject();
                if (asForeignLib.isForeign(recv)) {
                    return true;
                }
            }
            if (LLVMManagedPointer.isInstance(destPtr)) {
                if (LLVMManagedPointer.cast(destPtr).getOffset() != 0) {
                    return true;
                }
                Object recv = LLVMManagedPointer.cast(destPtr).getObject();
                if (asForeignLib.isForeign(recv)) {
                    return true;
                }
            }
            return false;
        }

        short copyDirection(LLVMPointer sourcePtr, LLVMPointer destPtr) {
            if (LLVMManagedPointer.isInstance(sourcePtr) && LLVMManagedPointer.isInstance(destPtr)) {
                LLVMManagedPointer managedSourcePtr = LLVMManagedPointer.cast(sourcePtr);
                LLVMManagedPointer managedDestPtr = LLVMManagedPointer.cast(destPtr);
                if (managedSourcePtr.getObject() == managedDestPtr.getObject()) {
                    if (managedSourcePtr.getOffset() == managedDestPtr.getOffset()) {
                        return 0;
                    } else if (managedSourcePtr.getOffset() < managedDestPtr.getOffset() &&
                                    managedSourcePtr.getOffset() + length > managedDestPtr.getOffset()) {
                        return -1;
                    } else {
                        return 1;
                    }
                }
            } else if (LLVMNativePointer.isInstance(sourcePtr) && LLVMNativePointer.isInstance(destPtr)) {
                LLVMNativePointer nativeSourcePtr = LLVMNativePointer.cast(sourcePtr);
                LLVMNativePointer nativeDestPtr = LLVMNativePointer.cast(destPtr);
                if (nativeSourcePtr.asNative() == nativeDestPtr.asNative()) {
                    return 0;
                } else if (nativeSourcePtr.asNative() < nativeDestPtr.asNative() &&
                                nativeSourcePtr.asNative() + length > nativeDestPtr.asNative()) {
                    return -1;
                } else {
                    return 1;
                }
            }
            return 1;
        }

        @Specialization(guards = "doCustomCopy(sourcePtr, destPtr, copyTargetLib)")
        Object customCopy(LLVMPointer sourcePtr, LLVMPointer destPtr,
                        @CachedLibrary(limit = "3") LLVMCopyTargetLibrary copyTargetLib) {
            copyTargetLib.copyFrom(getReceiver(destPtr), getReceiver(sourcePtr), length);
            return null;
        }

        @Specialization(guards = {"!doCustomCopy(sourcePtr, destPtr, copyTargetLib)", "useMemMoveIntrinsic(sourcePtr, destPtr, asForeignLib)"})
        Object delegateToMemMove(LLVMPointer sourcePtr, LLVMPointer destPtr,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMCopyTargetLibrary copyTargetLib,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMAsForeignLibrary asForeignLib) {
            memMoveNode.executeWithTarget(destPtr, sourcePtr, length);
            return null;
        }

        @Specialization(guards = {"!doCustomCopy(sourcePtr, destPtr, copyTargetLib)", "!useMemMoveIntrinsic(sourcePtr, destPtr, asForeignLib)", "copyDirection(sourcePtr, destPtr) > 0"})
        Object primitiveMoveInForwardDir(LLVMPointer sourcePtr, LLVMPointer destPtr,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMCopyTargetLibrary copyTargetLib,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMAsForeignLibrary asForeignLib) {
            primitiveMoveNode.executeWithTarget(sourcePtr, destPtr, true);
            return null;
        }

        @Specialization(guards = {"!doCustomCopy(sourcePtr, destPtr, copyTargetLib)", "!useMemMoveIntrinsic(sourcePtr, destPtr, asForeignLib)", "copyDirection(sourcePtr, destPtr) < 0"})
        Object primitiveMoveInBackwardDir(LLVMPointer sourcePtr, LLVMPointer destPtr,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMCopyTargetLibrary copyTargetLib,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMAsForeignLibrary asForeignLib) {
            primitiveMoveNode.executeWithTarget(sourcePtr.increment(length), destPtr.increment(length), false);
            return null;
        }

        @Specialization(guards = {"!doCustomCopy(sourcePtr, destPtr, copyTargetLib)", "!useMemMoveIntrinsic(sourcePtr, destPtr, asForeignLib)", "copyDirection(sourcePtr, destPtr) == 0"})
        Object noOp(@SuppressWarnings("unused") LLVMPointer sourcePtr, @SuppressWarnings("unused") LLVMPointer destPtr,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMCopyTargetLibrary copyTargetLib,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMAsForeignLibrary asForeignLib) {
            return null;
        }
    }
}
