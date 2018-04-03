/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMSharedGlobalVariable;
import com.oracle.truffle.llvm.runtime.LLVMTruffleAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalReadNode.ReadObjectNode;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNode;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNode.WriteDoubleNode;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNode.WriteFloatNode;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNode.WriteI16Node;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNode.WriteI1Node;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNode.WriteI32Node;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNode.WriteI64Node;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNode.WriteI8Node;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalWriteNode.WriteObjectNode;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.SlowPathForeignToLLVM;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

abstract class LLVMAddressMessageResolutionNode extends LLVMNode {
    private static final int I1_SIZE = 1;
    private static final int I8_SIZE = 1;
    private static final int I16_SIZE = 2;
    private static final int I32_SIZE = 4;
    private static final int I64_SIZE = 8;
    private static final int FLOAT_SIZE = 4;
    private static final int DOUBLE_SIZE = 8;

    public Type getType(LLVMTruffleAddress receiver) {
        return receiver.getType();
    }

    public PrimitiveType getPointeeType(LLVMTruffleAddress receiver) {
        Type t = receiver.getType();
        if (t instanceof PointerType && ((PointerType) t).getPointeeType() instanceof PrimitiveType) {
            return (PrimitiveType) ((PointerType) t).getPointeeType();
        } else {
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.raise(String.format(
                            "Pointer with (currently) unsupported type dereferenced (unsupported: %s) - please only dereference pointers to primitive types from foreign languages (e.g. int*).",
                            String.valueOf(t)));
        }
    }

    public PrimitiveType getPointeeType(LLVMGlobal receiver) {
        Type t = receiver.getType();
        if (t instanceof PrimitiveType) {
            return (PrimitiveType) t;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.raise(
                            String.format("Pointer with (currently) unsupported type dereferenced (unsupported: %s) - please only dereference pointers to primitive types from foreign languages (e.g. int*).",
                                            String.valueOf(t)));
        }
    }

    public LLVMDataEscapeNode getPrepareValueForEscapeNode(Type t) {
        return LLVMDataEscapeNodeGen.create(t);
    }

    public boolean typeGuard(LLVMTruffleAddress receiver, Type type) {
        return receiver.getType() == (type);
    }

    public ForeignToLLVM getToLLVMNode(PrimitiveType primitiveType) {
        return ForeignToLLVM.create(primitiveType);
    }

    public ForeignToLLVM getToTruffleObjectLLVMNode() {
        return ForeignToLLVM.create(ForeignToLLVMType.POINTER);
    }

    abstract static class LLVMAddressReadMessageResolutionNode extends LLVMAddressMessageResolutionNode {

        public abstract Object executeWithTarget(Object receiver, int index);

        @Specialization(guards = {"index == cachedIndex", "typeGuard(receiver, cachedType)"})
        protected Object doCachedTypeCachedOffset(LLVMTruffleAddress receiver, @SuppressWarnings("unused") int index,
                        @Cached("getType(receiver)") @SuppressWarnings("unused") Type cachedType,
                        @Cached("index") int cachedIndex,
                        @Cached("getPointeeType(receiver)") PrimitiveType elementType,
                        @Cached("getPrepareValueForEscapeNode(elementType)") LLVMDataEscapeNode prepareValueForEscape,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return prepareValueForEscape.executeWithTarget(doRead(memory, receiver, elementType, cachedIndex));
        }

        @Specialization(guards = {"typeGuard(receiver, cachedType)"}, replaces = "doCachedTypeCachedOffset")
        protected Object doCachedType(LLVMTruffleAddress receiver, int index,
                        @Cached("getType(receiver)") @SuppressWarnings("unused") Type cachedType,
                        @Cached("getPointeeType(receiver)") PrimitiveType elementType,
                        @Cached("getPrepareValueForEscapeNode(elementType)") LLVMDataEscapeNode prepareValueForEscape,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return prepareValueForEscape.executeWithTarget(doRead(memory, receiver, elementType, index));
        }

        @Specialization(replaces = {"doCachedTypeCachedOffset", "doCachedType"})
        protected Object doRegular(LLVMTruffleAddress receiver, int index,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            if (receiver.getType() instanceof PointerType && ((PointerType) receiver.getType()).getPointeeType() instanceof PrimitiveType) {
                return LLVMDataEscapeNode.slowConvert(doRead(memory, receiver, (PrimitiveType) ((PointerType) receiver.getType()).getPointeeType(), index), getPointeeType(receiver));
            } else {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(
                                String.format("Pointer with (currently) unsupported type dereferenced (unsupported: %s) - please only dereference pointers to primitive types from foreign languages (e.g. int*).",
                                                String.valueOf(receiver.getType())));
            }
        }

        private static Object doRead(LLVMMemory memory, LLVMTruffleAddress receiver, PrimitiveType elemntType, int cachedIndex) {
            LLVMAddress address = receiver.getAddress();
            return doPrimitiveRead(memory, cachedIndex, address, elemntType);
        }

        private static Object doPrimitiveRead(LLVMMemory memory, int cachedIndex, LLVMAddress address, PrimitiveType primitiveType) {
            long ptr = address.getVal();
            switch (primitiveType.getPrimitiveKind()) {
                case I1:
                    return memory.getI1(ptr + cachedIndex * I1_SIZE);
                case I8:
                    return memory.getI8(ptr + cachedIndex * I8_SIZE);
                case I16:
                    return memory.getI16(ptr + cachedIndex * I16_SIZE);
                case I32:
                    return memory.getI32(ptr + cachedIndex * I32_SIZE);
                case I64:
                    return memory.getI64(ptr + cachedIndex * I64_SIZE);
                case FLOAT:
                    return memory.getFloat(ptr + cachedIndex * FLOAT_SIZE);
                case DOUBLE:
                    return memory.getDouble(ptr + cachedIndex * DOUBLE_SIZE);
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.raise(
                                    String.format("Pointer with (currently) unsupported type dereferenced (unsupported: %s) - please only dereference pointers to primitive types from foreign languages (e.g. int*).",
                                                    String.valueOf(primitiveType.getPrimitiveKind())));
            }
        }

        protected Type getElementType(LLVMGlobal variable) {
            return variable.getType();
        }

        @Specialization(guards = "receiver.getDescriptor() == cachedReceiver")
        protected Object doGlobalCached(@SuppressWarnings("unused") LLVMSharedGlobalVariable receiver, int index,
                        @Cached("receiver.getDescriptor()") LLVMGlobal cachedReceiver,
                        @Cached("create()") ReadObjectNode globalAccess,
                        @Cached("getElementType(cachedReceiver)") @SuppressWarnings("unused") Type elementType,
                        @Cached("getPrepareValueForEscapeNode(elementType)") LLVMDataEscapeNode prepareValueForEscape) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise("Index must be 0 for globals - but was " + index);
            }
            return prepareValueForEscape.executeWithTarget(globalAccess.execute(cachedReceiver));
        }

        @Specialization(replaces = "doGlobalCached")
        protected Object doGlobal(LLVMSharedGlobalVariable receiver, int index,
                        @Cached("create()") ReadObjectNode globalAccess) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise("Index must be 0 for globals - but was " + index);
            }
            return LLVMDataEscapeNode.slowConvert(globalAccess.execute(receiver.getDescriptor()), receiver.getDescriptor().getType());
        }
    }

    abstract static class LLVMAddressWriteMessageResolutionNode extends LLVMAddressMessageResolutionNode {

        public abstract Object executeWithTarget(Object receiver, int index, Object value);

        @Specialization(guards = {"index == cachedIndex", "typeGuard(receiver, cachedType)"})
        protected Object doCachedTypeCachedOffset(LLVMTruffleAddress receiver, @SuppressWarnings("unused") int index, Object value,
                        @Cached("getType(receiver)") @SuppressWarnings("unused") Type cachedType,
                        @Cached("index") int cachedIndex,
                        @Cached("getPointeeType(receiver)") PrimitiveType elementType,
                        @Cached("getToLLVMNode(elementType)") ForeignToLLVM toLLVM,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            doFastWrite(memory, receiver, elementType, cachedIndex, value, toLLVM);
            return value;
        }

        @Specialization(guards = {"typeGuard(receiver, cachedType)"}, replaces = "doCachedTypeCachedOffset")
        protected Object doCachedType(LLVMTruffleAddress receiver, int index, Object value,
                        @Cached("getType(receiver)") @SuppressWarnings("unused") Type cachedType,
                        @Cached("getPointeeType(receiver)") PrimitiveType elementType,
                        @Cached("getToLLVMNode(elementType)") ForeignToLLVM toLLVM,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            doFastWrite(memory, receiver, elementType, index, value, toLLVM);
            return value;
        }

        @Child private SlowPathForeignToLLVM slowConvert;

        @Specialization(replaces = {"doCachedTypeCachedOffset", "doCachedType"})
        protected Object doRegular(LLVMTruffleAddress receiver, int index, Object value,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            if (slowConvert == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.slowConvert = insert(SlowPathForeignToLLVM.createSlowPathNode());
            }
            if (receiver.getType() instanceof PointerType && ((PointerType) receiver.getType()).getPointeeType() instanceof PrimitiveType) {
                doSlowWrite(memory, receiver, (PrimitiveType) ((PointerType) receiver.getType()).getPointeeType(), index, value, slowConvert);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(
                                String.format("Pointer with (currently) unsupported type dereferenced (unsupported: %s) - please only dereference pointers to primitive types from foreign languages (e.g. int*).",
                                                String.valueOf(receiver.getType())));
            }
            return value;
        }

        private static void doFastWrite(LLVMMemory memory, LLVMTruffleAddress receiver, PrimitiveType cachedType, int index, Object value, ForeignToLLVM toLLVM) {
            Object v = toLLVM.executeWithTarget(value);
            doWrite(memory, receiver, cachedType, index, v);
        }

        private static void doSlowWrite(LLVMMemory memory, LLVMTruffleAddress receiver, PrimitiveType cachedType, int index, Object value, SlowPathForeignToLLVM toLLVM) {
            Object v = toLLVM.convert(cachedType, memory, value);
            doWrite(memory, receiver, cachedType, index, v);
        }

        private static void doWrite(LLVMMemory memory, LLVMTruffleAddress receiver, PrimitiveType primitiveType, int index, Object v) {
            LLVMAddress address = receiver.getAddress();
            doPrimitiveWrite(memory, index, v, address, primitiveType);
        }

        private static void doPrimitiveWrite(LLVMMemory memory, int index, Object v, LLVMAddress address, PrimitiveType primitiveType) {
            long ptr = address.getVal();
            switch (primitiveType.getPrimitiveKind()) {
                case I1:
                    memory.putI1(ptr + index * I1_SIZE, (boolean) v);
                    break;
                case I8:
                    memory.putI8(ptr + index * I8_SIZE, (byte) v);
                    break;
                case I16:
                    memory.putI16(ptr + index * I16_SIZE, (short) v);
                    break;
                case I32:
                    memory.putI32(ptr + index * I32_SIZE, (int) v);
                    break;
                case I64:
                    memory.putI64(ptr + index * I64_SIZE, (long) v);
                    break;
                case FLOAT:
                    memory.putFloat(ptr + index * FLOAT_SIZE, (float) v);
                    break;
                case DOUBLE:
                    memory.putDouble(ptr + index * DOUBLE_SIZE, (double) v);
                    break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.raise(
                                    String.format("Pointer with (currently) unsupported type dereferenced (unsupported: %s) - please only dereference pointers to primitive types from foreign languages (I1, I8, I16, I32, I64, float, double).",
                                                    String.valueOf(primitiveType.getPrimitiveKind())));
            }
        }

        public boolean isPointerTypeGlobal(LLVMSharedGlobalVariable global) {
            return global.getDescriptor().getType() instanceof PointerType;
        }

        public boolean isPrimitiveTypeGlobal(LLVMSharedGlobalVariable global) {
            return global.getDescriptor().getType() instanceof PrimitiveType;
        }

        public boolean isPrimitiveTypeGlobal(LLVMGlobal global) {
            return global.getType() instanceof PrimitiveType;
        }

        public boolean isPointerTypeGlobal(LLVMGlobal global) {
            return global.getType() instanceof PointerType;
        }

        public boolean notLLVM(TruffleObject object) {
            return LLVMExpressionNode.notLLVM(object);
        }

        public boolean notTruffleObject(Object object) {
            return !(object instanceof TruffleObject);
        }

        @Specialization(guards = {"receiver.getDescriptor() == cachedReceiver", "isPointerTypeGlobal(cachedReceiver)", "notTruffleObject(value)"})
        protected Object doPrimitiveToPointerCached(@SuppressWarnings("unused") LLVMSharedGlobalVariable receiver, int index, Object value,
                        @Cached("receiver.getDescriptor()") LLVMGlobal cachedReceiver,
                        @Cached("getToTruffleObjectLLVMNode()") ForeignToLLVM toLLVM,
                        @Cached("create()") WriteObjectNode globalAccess) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise("Index must be 0 for globals - but was " + index);
            }
            LLVMBoxedPrimitive convertedValue = (LLVMBoxedPrimitive) toLLVM.executeWithTarget(value);
            globalAccess.execute(cachedReceiver, convertedValue);
            return value;
        }

        @Specialization(guards = {"isPointerTypeGlobal(receiver)", "notTruffleObject(value)"}, replaces = "doPrimitiveToPointerCached")
        protected Object doPrimitiveToPointer(LLVMSharedGlobalVariable receiver, int index, Object value,
                        @Cached("getToTruffleObjectLLVMNode()") ForeignToLLVM toLLVM,
                        @Cached("create()") WriteObjectNode globalAccess) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise("Index must be 0 for globals - but was " + index);
            }
            LLVMBoxedPrimitive convertedValue = (LLVMBoxedPrimitive) toLLVM.executeWithTarget(value);
            globalAccess.execute(receiver.getDescriptor(), convertedValue);
            return convertedValue;
        }

        @Specialization(guards = {"receiver.getDescriptor() == cachedReceiver", "isPointerTypeGlobal(cachedReceiver)", "notTruffleObject(value)"})
        protected Object doGlobalTruffleObjectCached(@SuppressWarnings("unused") LLVMSharedGlobalVariable receiver, int index, TruffleObject value,
                        @Cached("getToTruffleObjectLLVMNode()") ForeignToLLVM toLLVM,
                        @Cached("receiver.getDescriptor()") LLVMGlobal cachedReceiver,
                        @Cached("create()") WriteObjectNode globalAccess) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise("Index must be 0 for globals - but was " + index);
            }
            LLVMTruffleObject convertedValue = (LLVMTruffleObject) toLLVM.executeWithTarget(value);
            globalAccess.execute(cachedReceiver, convertedValue);
            return value;
        }

        @Specialization(guards = {"isPointerTypeGlobal(receiver)", "notLLVM(value)"}, replaces = "doGlobalTruffleObjectCached")
        protected Object doGlobalTruffleObject(LLVMSharedGlobalVariable receiver, int index, TruffleObject value,
                        @Cached("getToTruffleObjectLLVMNode()") ForeignToLLVM toLLVM,
                        @Cached("create()") WriteObjectNode globalAccess) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise("Index must be 0 for globals - but was " + index);
            }
            LLVMTruffleObject convertedValue = (LLVMTruffleObject) toLLVM.executeWithTarget(value);
            globalAccess.execute(receiver.getDescriptor(), convertedValue);
            return value;
        }

        @Specialization(guards = {"receiver.getDescriptor() == cachedReceiver", "isPrimitiveTypeGlobal(cachedReceiver)"})
        protected Object doGlobalCached(@SuppressWarnings("unused") LLVMSharedGlobalVariable receiver, int index, Object value,
                        @Cached("receiver.getDescriptor()") LLVMGlobal cachedReceiver,
                        @Cached("getPointeeType(cachedReceiver)") PrimitiveType cachedType,
                        @Cached("createPrimitiveGlobalWrite(cachedType)") LLVMGlobalWriteNode globalAccess,
                        @Cached("getToLLVMNode(cachedType)") ForeignToLLVM toLLVM) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise("Index must be 0 for globals - but was " + index);
            }
            doFastWrite(globalAccess, cachedReceiver, cachedType, value, toLLVM);
            return value;
        }

        @Specialization(guards = "isPrimitiveTypeGlobal(receiver)", replaces = "doGlobalCached")
        protected Object doGlobal(LLVMSharedGlobalVariable receiver, int index, Object value,
                        @Cached("getContextReference()") ContextReference<LLVMContext> context,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise("Index must be 0 for globals - but was " + index);
            }
            if (slowConvert == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.slowConvert = insert(SlowPathForeignToLLVM.createSlowPathNode());
            }
            if (receiver.getDescriptor().getType() instanceof PrimitiveType) {
                doSlowWrite(memory, context.get(), receiver.getDescriptor(), (PrimitiveType) receiver.getDescriptor().getType(), value, slowConvert);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(
                                String.format("Pointer with (currently) unsupported type dereferenced (unsupported: %s) - please only dereference pointers to primitive types from foreign languages (e.g. int*).",
                                                String.valueOf(receiver.getDescriptor().getType())));
            }
            return value;
        }

        private static void doFastWrite(LLVMGlobalWriteNode access, LLVMGlobal receiver, PrimitiveType cachedType, Object value, ForeignToLLVM toLLVM) {
            Object v = toLLVM.executeWithTarget(value);
            doWrite(access, receiver, cachedType, v);
        }

        private static void doSlowWrite(LLVMMemory memory, LLVMContext context, LLVMGlobal receiver, PrimitiveType type, Object value, SlowPathForeignToLLVM toLLVM) {
            Object v = toLLVM.convert(type, memory, value);
            LLVMGlobalWriteNode.slowPrimitiveWrite(context, memory, type, receiver, v);
        }

        private static void doWrite(LLVMGlobalWriteNode access, LLVMGlobal receiver, PrimitiveType cachedType, Object v) {
            doPrimitiveWrite(access, receiver, v, cachedType);
        }

        private static void doPrimitiveWrite(LLVMGlobalWriteNode access, LLVMGlobal address, Object v, PrimitiveType primitiveType) {
            switch (primitiveType.getPrimitiveKind()) {
                case I1:
                    ((WriteI1Node) access).execute(address, (boolean) v);
                    break;
                case I8:
                    ((WriteI8Node) access).execute(address, (byte) v);
                    break;
                case I16:
                    ((WriteI16Node) access).execute(address, (short) v);
                    break;
                case I32:
                    ((WriteI32Node) access).execute(address, (int) v);
                    break;
                case I64:
                    ((WriteI64Node) access).execute(address, (long) v);
                    break;
                case FLOAT:
                    ((WriteFloatNode) access).execute(address, (float) v);
                    break;
                case DOUBLE:
                    ((WriteDoubleNode) access).execute(address, (double) v);
                    break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.raise(
                                    String.format("Pointer with (currently) unsupported type dereferenced (unsupported: %s) - please only dereference pointers to primitive types from foreign languages (I1, I8, I16, I32, I64, float, double).",
                                                    String.valueOf(primitiveType.getPrimitiveKind())));
            }
        }
    }

    protected static LLVMGlobalWriteNode createPrimitiveGlobalWrite(PrimitiveType primitiveType) {
        switch (primitiveType.getPrimitiveKind()) {
            case I1:
                return WriteI1Node.create();
            case I8:
                return WriteI8Node.create();
            case I16:
                return WriteI16Node.create();
            case I32:
                return WriteI32Node.create();
            case I64:
                return WriteI64Node.create();
            case FLOAT:
                return WriteFloatNode.create();
            case DOUBLE:
                return WriteDoubleNode.create();
            default:
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(
                                String.format("Pointer with (currently) unsupported type dereferenced (unsupported: %s) - please only dereference pointers to primitive types from foreign languages (I1, I8, I16, I32, I64, float, double).",
                                                String.valueOf(primitiveType.getPrimitiveKind())));
        }
    }
}
