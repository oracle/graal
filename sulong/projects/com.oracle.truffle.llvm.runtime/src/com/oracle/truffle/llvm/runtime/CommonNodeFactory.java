package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Value;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.interop.convert.ToAnyLLVMNodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToDoubleNodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToFloatNodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToI16NodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToI1NodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToI32NodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToI64NodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToI8NodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToPointer;
import com.oracle.truffle.llvm.runtime.interop.convert.ToVoidLLVMNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVMPointerDirectLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDoubleLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMFloatLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI1LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMDoubleStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMFloatStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI1StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNodeGen;

public class CommonNodeFactory {

    public CommonNodeFactory(){

    }

    public static LLVMLoadNode createLoadNode(LLVMInteropType.ValueKind kind) {
        switch (kind) {
            case I1:
                return LLVMI1LoadNodeGen.create(null);
            case I8:
                return LLVMI8LoadNodeGen.create(null);
            case I16:
                return LLVMI16LoadNodeGen.create(null);
            case I32:
                return LLVMI32LoadNodeGen.create(null);
            case I64:
                return LLVMI64LoadNodeGen.create(null);
            case FLOAT:
                return LLVMFloatLoadNodeGen.create(null);
            case DOUBLE:
                return LLVMDoubleLoadNodeGen.create(null);
            case POINTER:
                return LLVMPointerDirectLoadNodeGen.create(null);
            default:
                throw new IllegalStateException("unexpected interop kind " + kind);
        }
    }

    public static LLVMStoreNode createStoreNode(LLVMInteropType.ValueKind kind) {
        switch (kind) {
            case I1:
                return LLVMI1StoreNodeGen.create(null, null);
            case I8:
                return LLVMI8StoreNodeGen.create(null, null);
            case I16:
                return LLVMI16StoreNodeGen.create(null, null);
            case I32:
                return LLVMI32StoreNodeGen.create(null, null);
            case I64:
                return LLVMI64StoreNodeGen.create(null, null);
            case FLOAT:
                return LLVMFloatStoreNodeGen.create(null, null);
            case DOUBLE:
                return LLVMDoubleStoreNodeGen.create(null, null);
            case POINTER:
                return LLVMPointerStoreNodeGen.create(null, null);
            default:
                throw new IllegalStateException("unexpected interop kind " + kind);
        }
    }

    public static ForeignToLLVM createForeignToLLVM(Value type) {
        switch (type.getKind()) {
            case I1:
                return ToI1NodeGen.create();
            case I8:
                return ToI8NodeGen.create();
            case I16:
                return ToI16NodeGen.create();
            case I32:
                return ToI32NodeGen.create();
            case I64:
                return ToI64NodeGen.create();
            case FLOAT:
                return ToFloatNodeGen.create();
            case DOUBLE:
                return ToDoubleNodeGen.create();
            case POINTER:
                return ToPointer.create(type.getBaseType());
            default:
                throw new IllegalStateException("unexpected interop kind " + type.getKind());
        }
    }

    public static ForeignToLLVM createForeignToLLVM(ForeignToLLVMType type) {
        switch (type) {
            case VOID:
                return ToVoidLLVMNodeGen.create();
            case ANY:
                return ToAnyLLVMNodeGen.create();
            case I1:
                return ToI1NodeGen.create();
            case I8:
                return ToI8NodeGen.create();
            case I16:
                return ToI16NodeGen.create();
            case I32:
                return ToI32NodeGen.create();
            case I64:
                return ToI64NodeGen.create();
            case FLOAT:
                return ToFloatNodeGen.create();
            case DOUBLE:
                return ToDoubleNodeGen.create();
            case POINTER:
                return ToPointer.create();
            default:
                throw new IllegalStateException(type.toString());
        }
    }
}
