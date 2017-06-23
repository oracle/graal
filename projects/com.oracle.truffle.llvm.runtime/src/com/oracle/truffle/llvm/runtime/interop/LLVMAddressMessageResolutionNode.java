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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMSharedGlobalVariable;
import com.oracle.truffle.llvm.runtime.LLVMTruffleAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

@SuppressWarnings("unused")
abstract class LLVMAddressMessageResolutionNode extends Node {
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
            throw new UnsupportedOperationException(t.toString());
        }
    }

    public PrimitiveType getPointeeType(LLVMGlobalVariableAccess access, LLVMGlobalVariable receiver) {
        Type t = access.getType(receiver);
        if (t instanceof PrimitiveType) {
            return (PrimitiveType) t;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new UnsupportedOperationException(t.toString());
        }
    }

    public LLVMDataEscapeNode getPrepareValueForEscapeNode(Type t) {
        return LLVMDataEscapeNodeGen.create(t);
    }

    public boolean typeGuard(LLVMTruffleAddress receiver, Type type) {
        return receiver.getType() == (type);
    }

    public ToLLVMNode getToLLVMNode(PrimitiveType primitiveType) {
        return ToLLVMNode.createNode(ToLLVMNode.convert(primitiveType));
    }

    public ToLLVMNode getToTruffleObjectLLVMNode() {
        return ToLLVMNode.createNode((TruffleObject.class));
    }

    protected static final LLVMGlobalVariableAccess createGlobalAccess() {
        return new LLVMGlobalVariableAccess();
    }

    abstract static class LLVMAddressReadMessageResolutionNode extends LLVMAddressMessageResolutionNode {

        public abstract Object executeWithTarget(VirtualFrame frame, Object receiver, int index);

        @Specialization(guards = {"index == cachedIndex", "typeGuard(receiver, cachedType)"})
        public Object doCachedTypeCachedOffset(LLVMTruffleAddress receiver, int index,
                        @Cached("getType(receiver)") Type cachedType,
                        @Cached("index") int cachedIndex,
                        @Cached("getPointeeType(receiver)") PrimitiveType elementType,
                        @Cached("getPrepareValueForEscapeNode(elementType)") LLVMDataEscapeNode prepareValueForEscape) {
            return prepareValueForEscape.executeWithTarget(doRead(receiver, elementType, cachedIndex), receiver.getContext());
        }

        @Specialization(guards = {"typeGuard(receiver, cachedType)"}, replaces = "doCachedTypeCachedOffset")
        public Object doCachedType(LLVMTruffleAddress receiver, int index,
                        @Cached("getType(receiver)") Type cachedType,
                        @Cached("getPointeeType(receiver)") PrimitiveType elementType,
                        @Cached("getPrepareValueForEscapeNode(elementType)") LLVMDataEscapeNode prepareValueForEscape) {
            return prepareValueForEscape.executeWithTarget(doRead(receiver, elementType, index), receiver.getContext());
        }

        @Specialization(replaces = {"doCachedTypeCachedOffset", "doCachedType"})
        public Object doRegular(LLVMTruffleAddress receiver, int index) {
            if (receiver.getType() instanceof PointerType && ((PointerType) receiver.getType()).getPointeeType() instanceof PrimitiveType) {
                return LLVMDataEscapeNode.slowConvert(doRead(receiver, (PrimitiveType) ((PointerType) receiver.getType()).getPointeeType(), index), getPointeeType(receiver), receiver.getContext());
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new UnsupportedOperationException(receiver.getType().toString());
            }
        }

        private static Object doRead(LLVMTruffleAddress receiver, PrimitiveType elemntType, int cachedIndex) {
            LLVMAddress address = receiver.getAddress();
            return doPrimitiveRead(cachedIndex, address, elemntType);
        }

        private static Object doPrimitiveRead(int cachedIndex, LLVMAddress address, PrimitiveType primitiveType) {
            long ptr = address.getVal();
            switch (primitiveType.getPrimitiveKind()) {
                case I1:
                    return LLVMMemory.getI1(ptr + cachedIndex * I1_SIZE);
                case I8:
                    return LLVMMemory.getI8(ptr + cachedIndex * I8_SIZE);
                case I16:
                    return LLVMMemory.getI16(ptr + cachedIndex * I16_SIZE);
                case I32:
                    return LLVMMemory.getI32(ptr + cachedIndex * I32_SIZE);
                case I64:
                    return LLVMMemory.getI64(ptr + cachedIndex * I64_SIZE);
                case FLOAT:
                    return LLVMMemory.getFloat(ptr + cachedIndex * FLOAT_SIZE);
                case DOUBLE:
                    return LLVMMemory.getDouble(ptr + cachedIndex * DOUBLE_SIZE);
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException();
            }
        }

        protected Type getElementType(LLVMGlobalVariableAccess access, LLVMGlobalVariable variable) {
            return access.getType(variable);
        }

        @Specialization(guards = "receiver.getDescriptor() == cachedReceiver")
        public Object doGlobalCached(LLVMSharedGlobalVariable receiver, int index, @Cached("receiver.getDescriptor()") LLVMGlobalVariable cachedReceiver,
                        @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess,
                        @Cached("getElementType(globalAccess, cachedReceiver)") Type elementType,
                        @Cached("getPrepareValueForEscapeNode(elementType)") LLVMDataEscapeNode prepareValueForEscape) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Index must be 0 for globals!");
            }
            return prepareValueForEscape.executeWithTarget(globalAccess.get(cachedReceiver), receiver.getContext());
        }

        @Specialization(replaces = "doGlobalCached")
        public Object doGlobal(LLVMSharedGlobalVariable receiver, int index, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Index must be 0 for globals!");
            }
            return LLVMDataEscapeNode.slowConvert(globalAccess.get(receiver.getDescriptor()), globalAccess.getType(receiver.getDescriptor()), receiver.getContext());
        }

    }

    abstract static class LLVMAddressWriteMessageResolutionNode extends LLVMAddressMessageResolutionNode {

        public abstract Object executeWithTarget(VirtualFrame frame, Object receiver, int index, Object value);

        @Specialization(guards = {"index == cachedIndex", "typeGuard(receiver, cachedType)"})
        public Object doCachedTypeCachedOffset(LLVMTruffleAddress receiver, int index, Object value,
                        @Cached("getType(receiver)") Type cachedType,
                        @Cached("index") int cachedIndex,
                        @Cached("getPointeeType(receiver)") PrimitiveType elementType,
                        @Cached("getToLLVMNode(elementType)") ToLLVMNode toLLVM) {
            doFastWrite(receiver, elementType, cachedIndex, value, toLLVM);
            return value;
        }

        @Specialization(guards = {"typeGuard(receiver, cachedType)"}, replaces = "doCachedTypeCachedOffset")
        public Object doCachedType(LLVMTruffleAddress receiver, int index, Object value,
                        @Cached("getType(receiver)") Type cachedType,
                        @Cached("getPointeeType(receiver)") PrimitiveType elementType,
                        @Cached("getToLLVMNode(elementType)") ToLLVMNode toLLVM) {
            doFastWrite(receiver, elementType, index, value, toLLVM);
            return value;
        }

        @Child private ToLLVMNode slowConvert;

        @Specialization(replaces = {"doCachedTypeCachedOffset", "doCachedType"})
        public Object doRegular(LLVMTruffleAddress receiver, int index, Object value) {
            if (slowConvert == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.slowConvert = insert(ToLLVMNode.createNode(null));
            }
            if (receiver.getType() instanceof PointerType && ((PointerType) receiver.getType()).getPointeeType() instanceof PrimitiveType) {
                doSlowWrite(receiver, (PrimitiveType) ((PointerType) receiver.getType()).getPointeeType(), index, value, slowConvert);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new UnsupportedOperationException(receiver.getType().toString());
            }
            return value;
        }

        private static void doFastWrite(LLVMTruffleAddress receiver, PrimitiveType cachedType, int index, Object value, ToLLVMNode toLLVM) {
            Object v = toLLVM.executeWithTarget(value);
            doWrite(receiver, cachedType, index, v);
        }

        private static void doSlowWrite(LLVMTruffleAddress receiver, PrimitiveType cachedType, int index, Object value, ToLLVMNode toLLVM) {
            Object v = toLLVM.slowConvert(value, ToLLVMNode.convert(cachedType));
            doWrite(receiver, cachedType, index, v);
        }

        private static void doWrite(LLVMTruffleAddress receiver, PrimitiveType primitiveType, int index, Object v) {
            LLVMAddress address = receiver.getAddress();
            doPrimitiveWrite(index, v, address, primitiveType);
        }

        private static void doPrimitiveWrite(int index, Object v, LLVMAddress address, PrimitiveType primitiveType) {
            long ptr = address.getVal();
            switch (primitiveType.getPrimitiveKind()) {
                case I1:
                    LLVMMemory.putI1(ptr + index * I1_SIZE, (boolean) v);
                    break;
                case I8:
                    LLVMMemory.putI8(ptr + index * I8_SIZE, (byte) v);
                    break;
                case I16:
                    LLVMMemory.putI16(ptr + index * I16_SIZE, (short) v);
                    break;
                case I32:
                    LLVMMemory.putI32(ptr + index * I32_SIZE, (int) v);
                    break;
                case I64:
                    LLVMMemory.putI64(ptr + index * I64_SIZE, (long) v);
                    break;
                case FLOAT:
                    LLVMMemory.putFloat(ptr + index * FLOAT_SIZE, (float) v);
                    break;
                case DOUBLE:
                    LLVMMemory.putDouble(ptr + index * DOUBLE_SIZE, (double) v);
                    break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException();
            }
        }

        public boolean isPointerTypeGlobal(LLVMGlobalVariableAccess access, LLVMSharedGlobalVariable global) {
            return access.getType(global.getDescriptor()) instanceof PointerType;
        }

        public boolean isPrimitiveTypeGlobal(LLVMGlobalVariableAccess access, LLVMSharedGlobalVariable global) {
            return access.getType(global.getDescriptor()) instanceof PrimitiveType;
        }

        public boolean isPrimitiveTypeGlobal(LLVMGlobalVariableAccess access, LLVMGlobalVariable global) {
            return access.getType(global) instanceof PrimitiveType;
        }

        public boolean isPointerTypeGlobal(LLVMGlobalVariableAccess access, LLVMGlobalVariable global) {
            return access.getType(global) instanceof PointerType;
        }

        public boolean notLLVM(TruffleObject object) {
            return LLVMExpressionNode.notLLVM(object);
        }

        public boolean notTruffleObject(Object object) {
            return !(object instanceof TruffleObject);
        }

        @Specialization(guards = {"receiver.getDescriptor() == cachedReceiver", "isPointerTypeGlobal(globalAccess, cachedReceiver)", "notTruffleObject(value)"})
        public Object doPrimitiveToPointerCached(LLVMSharedGlobalVariable receiver, int index, Object value,
                        @Cached("receiver.getDescriptor()") LLVMGlobalVariable cachedReceiver, @Cached("getToTruffleObjectLLVMNode()") ToLLVMNode toLLVM,
                        @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Index must be 0 for globals!");
            }
            TruffleObject convertedValue = (TruffleObject) toLLVM.executeWithTarget(value);
            globalAccess.putTruffleObject(cachedReceiver, convertedValue);
            return value;
        }

        @Specialization(guards = {"isPointerTypeGlobal(globalAccess, receiver)", "notTruffleObject(value)"}, replaces = "doPrimitiveToPointerCached")
        public Object doPrimitiveToPointer(LLVMSharedGlobalVariable receiver, int index, Object value, @Cached("getToTruffleObjectLLVMNode()") ToLLVMNode toLLVM,
                        @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Index must be 0 for globals!");
            }
            TruffleObject convertedValue = (TruffleObject) toLLVM.executeWithTarget(value);
            globalAccess.putTruffleObject(receiver.getDescriptor(), convertedValue);
            return convertedValue;
        }

        @Specialization(guards = {"receiver.getDescriptor() == cachedReceiver", "isPointerTypeGlobal(globalAccess, cachedReceiver)", "notLLVM(value)"})
        public Object doGlobalTruffleObjectCached(LLVMSharedGlobalVariable receiver, int index, TruffleObject value,
                        @Cached("receiver.getDescriptor()") LLVMGlobalVariable cachedReceiver, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Index must be 0 for globals!");
            }
            globalAccess.putTruffleObject(cachedReceiver, value);
            return value;
        }

        @Specialization(guards = {"isPointerTypeGlobal(globalAccess, receiver)", "notLLVM(value)"}, replaces = "doGlobalTruffleObjectCached")
        public Object doGlobalTruffleObject(LLVMSharedGlobalVariable receiver, int index, TruffleObject value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Index must be 0 for globals!");
            }
            globalAccess.putTruffleObject(receiver.getDescriptor(), value);
            return value;
        }

        @Specialization(guards = {"receiver.getDescriptor() == cachedReceiver", "isPrimitiveTypeGlobal(globalAccess, cachedReceiver)"})
        public Object doGlobalCached(LLVMSharedGlobalVariable receiver, int index, Object value,
                        @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess,
                        @Cached("receiver.getDescriptor()") LLVMGlobalVariable cachedReceiver,
                        @Cached("getPointeeType(globalAccess, cachedReceiver)") PrimitiveType cachedType,
                        @Cached("getToLLVMNode(cachedType)") ToLLVMNode toLLVM) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Index must be 0 for globals!");
            }
            doFastWrite(globalAccess, cachedReceiver, cachedType, value, toLLVM);
            return value;
        }

        @Specialization(guards = "isPrimitiveTypeGlobal(globalAccess, receiver)", replaces = "doGlobalCached")
        public Object doGlobal(LLVMSharedGlobalVariable receiver, int index, Object value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            if (index != 0) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Index must be 0 for globals!");
            }
            if (slowConvert == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.slowConvert = insert(ToLLVMNode.createNode(null));
            }
            if (globalAccess.getType(receiver.getDescriptor()) instanceof PrimitiveType) {
                doSlowWrite(globalAccess, receiver.getDescriptor(), (PrimitiveType) globalAccess.getType(receiver.getDescriptor()), value, slowConvert);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new UnsupportedOperationException(globalAccess.getType(receiver.getDescriptor()).toString());
            }
            return value;
        }

        private static void doFastWrite(LLVMGlobalVariableAccess access, LLVMGlobalVariable receiver, PrimitiveType cachedType, Object value, ToLLVMNode toLLVM) {
            Object v = toLLVM.executeWithTarget(value);
            doWrite(access, receiver, cachedType, v);
        }

        private static void doSlowWrite(LLVMGlobalVariableAccess access, LLVMGlobalVariable receiver, PrimitiveType type, Object value, ToLLVMNode toLLVM) {
            Object v = toLLVM.slowConvert(value, ToLLVMNode.convert(type));
            doWrite(access, receiver, type, v);
        }

        private static void doWrite(LLVMGlobalVariableAccess access, LLVMGlobalVariable receiver, PrimitiveType cachedType, Object v) {
            doPrimitiveWrite(access, receiver, v, cachedType);
        }

        private static void doPrimitiveWrite(LLVMGlobalVariableAccess access, LLVMGlobalVariable address, Object v, PrimitiveType primitiveType) {
            switch (primitiveType.getPrimitiveKind()) {
                case I1:
                    access.putI1(address, (boolean) v);
                    break;
                case I8:
                    access.putI8(address, (byte) v);
                    break;
                case I16:
                    access.putI16(address, (short) v);
                    break;
                case I32:
                    access.putI32(address, (int) v);
                    break;
                case I64:
                    access.putI64(address, (long) v);
                    break;
                case FLOAT:
                    access.putFloat(address, (float) v);
                    break;
                case DOUBLE:
                    access.putDouble(address, (double) v);
                    break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException();
            }
        }

    }
}
