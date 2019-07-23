package com.oracle.truffle.llvm.runtime.debug.debugexpr.parser;

import java.util.EnumMap;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceArrayLikeType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceBasicType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceDecoratorType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourcePointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public class DebugExprType {

    private final Kind kind;
    private DebugExprType innerType; // used for arrays, pointers, ...
    private static EnumMap<Kind, DebugExprType> map = new EnumMap<>(Kind.class);

    private DebugExprType(Kind kind, DebugExprType innerType) {
        this.kind = kind;
        this.innerType = innerType;
    }

    public DebugExprType getInnerType() {
        return innerType;
    }

    public boolean isUnsigned() {
        switch (kind) {
            case UNSIGNED_CHAR:
            case UNSIGNED_INT:
            case UNSIGNED_LONG:
            case UNSIGNED_SHORT:
                return true;
            default:
                return false;
        }
    }

    public boolean isIntegerType() {
        switch (kind) {
            case UNSIGNED_CHAR:
            case UNSIGNED_INT:
            case UNSIGNED_LONG:
            case UNSIGNED_SHORT:
            case SIGNED_CHAR:
            case SIGNED_INT:
            case SIGNED_LONG:
            case SIGNED_SHORT:
            case BOOL:
                return true;
            default:
                return false;
        }
    }

    public boolean isFloatingType() {
        switch (kind) {
            case FLOAT:
            case DOUBLE:
            case LONG_DOUBLE:
                return true;
            default:
                return false;
        }
    }

    public Type getLLVMRuntimeType() {
        switch (kind) {
            case VOID:
                return VoidType.INSTANCE;
            case BOOL:
                return PrimitiveType.I1;
            case UNSIGNED_CHAR:
            case SIGNED_CHAR:
                return PrimitiveType.I8;
            case UNSIGNED_SHORT:
            case SIGNED_SHORT:
                return PrimitiveType.I16;
            case UNSIGNED_INT:
            case SIGNED_INT:
                return PrimitiveType.I32;
            case UNSIGNED_LONG:
            case SIGNED_LONG:
                return PrimitiveType.I64;
            case FLOAT:
                return PrimitiveType.FLOAT;
            case DOUBLE:
                return PrimitiveType.DOUBLE;
            case LONG_DOUBLE:
                return PrimitiveType.F128;
            default:
                return VoidType.INSTANCE;
        }
    }

    /**
     * returns the more general type in terms of implicit conversions (i.e. commonType(Int32,
     * Float32) returns Float32, commonType(Int64, UInt32) returns UInt64).
     */
    public static DebugExprType commonType(DebugExprType t1, DebugExprType t2) {
        if (t1 == t2)
            return t1;
        else if (t1 == getVoidType() || t2 == getVoidType())
            return getVoidType();
        else if (t1.isFloatingType() && t2.isFloatingType()) {
            return getFloatType(Math.max(t1.getBitSize(), t2.getBitSize()));
        } else if (t1.isIntegerType() && t2.isIntegerType()) {
            if (t1.isUnsigned() != t2.isUnsigned()) {
                // return unsigned type
                return getIntType(Math.max(t1.getBitSize(), t2.getBitSize()), false);
            } else {
                return getIntType(Math.max(t1.getBitSize(), t2.getBitSize()), !t1.isUnsigned());
            }
        } else if (t1.isIntegerType() && t2.isFloatingType()) {
            return t2;
        } else if (t1.isFloatingType() && t2.isIntegerType()) {
            return t2;
        }
        return getVoidType();
    }

    public int getBitSize() {
        switch (kind) {
            case VOID:
                return 0;
            case BOOL:
                return 1;
            case UNSIGNED_CHAR:
            case SIGNED_CHAR:
                return 8;
            case UNSIGNED_SHORT:
            case SIGNED_SHORT:
                return 16;
            case UNSIGNED_INT:
            case SIGNED_INT:
                return 32;
            case UNSIGNED_LONG:
            case SIGNED_LONG:
                return 64;
            case FLOAT:
                return 32;
            case DOUBLE:
                return 64;
            case LONG_DOUBLE:
                return 128;
            default:
                return 0;
        }
    }

    public static DebugExprType getIntType(long sizeInBits, boolean signed) {
        Kind kind = Kind.VOID;
        if (sizeInBits > 0) {
            if (sizeInBits == 1) {
                kind = Kind.BOOL;
            } else if (sizeInBits <= 8) {
                kind = signed ? Kind.SIGNED_CHAR : Kind.UNSIGNED_CHAR;
            } else if (sizeInBits <= 16) {
                kind = signed ? Kind.SIGNED_SHORT : Kind.UNSIGNED_SHORT;
            } else if (sizeInBits <= 32) {
                kind = signed ? Kind.SIGNED_INT : Kind.UNSIGNED_INT;
            } else if (sizeInBits <= 64) {
                kind = signed ? Kind.SIGNED_LONG : Kind.UNSIGNED_LONG;
            }
        }
        if (map.containsKey(kind))
            return map.get(kind);

        DebugExprType t = new DebugExprType(kind, null);
        map.put(kind, t);
        return t;
    }

    public static DebugExprType getVoidType() {
        if (map.containsKey(Kind.VOID))
            return map.get(Kind.VOID);
        DebugExprType t = new DebugExprType(Kind.VOID, null);
        map.put(Kind.VOID, t);
        return t;
    }

    public static DebugExprType getBoolType() {
        if (map.containsKey(Kind.BOOL))
            return map.get(Kind.BOOL);
        DebugExprType t = new DebugExprType(Kind.BOOL, null);
        map.put(Kind.BOOL, t);
        return t;
    }

    public static DebugExprType getFloatType(long sizeInBits) {
        Kind kind = Kind.VOID;
        if (sizeInBits == 32) {
            kind = Kind.FLOAT;
        } else if (sizeInBits == 64) {
            kind = Kind.DOUBLE;
        } else if (sizeInBits == 128) {
            kind = Kind.LONG_DOUBLE;
        }
        if (map.containsKey(kind))
            return map.get(kind);
        DebugExprType t = new DebugExprType(kind, null);
        map.put(kind, t);
        return t;
    }

    public static DebugExprType getTypeFromSymbolTableMetaObject(Object metaObj) {
        if (metaObj instanceof LLVMSourceBasicType) {
            LLVMSourceBasicType basicType = (LLVMSourceBasicType) metaObj;
            LLVMSourceBasicType.Kind typeKind = basicType.getKind();
            long typeSize = basicType.getSize();
            boolean signed = false;
            switch (typeKind) {
                case BOOLEAN:
                    return DebugExprType.getIntType(1, false);
                case SIGNED:
                    signed = true;
                case UNSIGNED:
                    return DebugExprType.getIntType(typeSize, signed);
                case SIGNED_CHAR:
                    signed = true;
                case UNSIGNED_CHAR:
                    return DebugExprType.getIntType(8, signed);
                case FLOATING:
                    return DebugExprType.getFloatType(typeSize);
                default:
                    System.out.println("metaObj.getClass() = " + metaObj.getClass().getName());
                    // TODO for pointers
                    return DebugExprType.getVoidType();
            }
        } else if (metaObj instanceof LLVMSourceArrayLikeType) {
            LLVMSourceArrayLikeType arrayType = (LLVMSourceArrayLikeType) metaObj;
            DebugExprType innerType = getTypeFromSymbolTableMetaObject(arrayType.getElementType(0));
            return new DebugExprType(Kind.ARRAY, innerType);
        } else if (metaObj instanceof LLVMSourceDecoratorType) {
            LLVMSourceDecoratorType arrayType = (LLVMSourceDecoratorType) metaObj;
            return new DebugExprType(Kind.STRUCT, null);
        } else if (metaObj instanceof LLVMSourcePointerType) {
            LLVMSourcePointerType pointerType = (LLVMSourcePointerType) metaObj;
            return new DebugExprType(Kind.POINTER, null);
        } else {
            System.out.println(metaObj + " is type of " + metaObj.getClass().getName());
            return DebugExprType.getVoidType();
        }
    }

    public Object parse(Object member) {
        switch (kind) {
            case BOOL:
                return Boolean.parseBoolean(member.toString());
            case UNSIGNED_CHAR:
            case SIGNED_CHAR:
                // TODO adjust to characters
                return Short.parseShort(member.toString());
            case UNSIGNED_SHORT:
            case SIGNED_SHORT:
                return Short.parseShort(member.toString());
            case UNSIGNED_INT:
            case SIGNED_INT:
                return Integer.parseInt(member.toString());
            case UNSIGNED_LONG:
            case SIGNED_LONG:
                return Long.parseLong(member.toString());
            case FLOAT:
                return Float.parseFloat(member.toString());
            case DOUBLE:
            case LONG_DOUBLE:
                return Double.parseDouble(member.toString());
            case STRUCT:
            case ARRAY:
            case POINTER:
                return member;
            default:
                return null;
        }
    }

    public enum Kind {
        VOID,
        BOOL,
        UNSIGNED_CHAR,
        SIGNED_CHAR,
        UNSIGNED_SHORT,
        SIGNED_SHORT,
        UNSIGNED_INT,
        SIGNED_INT,
        UNSIGNED_LONG,
        SIGNED_LONG,
        FLOAT,
        DOUBLE,
        LONG_DOUBLE,
        POINTER,
        REFERENCE,
        ARRAY,
        STRUCT,
        FUNCTION;

    }
}
