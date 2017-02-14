/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.interop;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNodeFactory.ToBooleanNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNodeFactory.ToByteNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNodeFactory.ToCharNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNodeFactory.ToDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNodeFactory.ToFloatNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNodeFactory.ToIntNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNodeFactory.ToLongNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNodeFactory.ToShortNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.ToLLVMNodeFactory.ToTruffleObjectNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LLVMRuntimeType;

public abstract class ToLLVMNode extends Node {

    @Child protected Node unbox = Message.UNBOX.createNode();
    @Child protected Node isBoxed = Message.IS_BOXED.createNode();

    public static ToLLVMNode createNode(Class<?> expectedType) {
        if (expectedType == TruffleObject.class) {
            return ToTruffleObjectNodeGen.create();
        } else if (expectedType == int.class) {
            return ToIntNodeGen.create();
        } else if (expectedType == long.class) {
            return ToLongNodeGen.create();
        } else if (expectedType == byte.class) {
            return ToByteNodeGen.create();
        } else if (expectedType == short.class) {
            return ToShortNodeGen.create();
        } else if (expectedType == char.class) {
            return ToCharNodeGen.create();
        } else if (expectedType == float.class) {
            return ToFloatNodeGen.create();
        } else if (expectedType == double.class) {
            return ToDoubleNodeGen.create();
        } else if (expectedType == boolean.class) {
            return ToBooleanNodeGen.create();
        } else if (expectedType == null || expectedType == void.class) {
            return new SlowConvertNodeObject();
        } else {
            throw new IllegalStateException("Unsupported Type");
        }
    }

    public abstract Object executeWithTarget(Object value);

    abstract static class ToIntNode extends ToLLVMNode {
        @Specialization
        public int fromInt(int value) {
            return value;
        }

        @Specialization
        public int fromChar(char value) {
            return value;
        }

        @Specialization
        public int fromShort(short value) {
            return value;
        }

        @Specialization
        public int fromLong(long value) {
            return (int) value;
        }

        @Specialization
        public int fromByte(byte value) {
            return value;
        }

        @Specialization
        public int fromFloat(float value) {
            return (int) value;
        }

        @Specialization
        public int fromDouble(double value) {
            return (int) value;
        }

        @Specialization
        public int fromBoolean(boolean value) {
            return value ? 1 : 0;
        }

        @Specialization
        public int fromTruffleObject(TruffleObject obj) {
            try {
                Object unboxed = ForeignAccess.sendUnbox(unbox, obj);
                return (int) convertPrimitive(int.class, unboxed);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    abstract static class ToLongNode extends ToLLVMNode {
        @Specialization
        public long fromInt(int value) {
            return value;
        }

        @Specialization
        public long fromChar(char value) {
            return value;
        }

        @Specialization
        public long fromShort(short value) {
            return value;
        }

        @Specialization
        public long fromLong(long value) {
            return value;
        }

        @Specialization
        public long fromByte(byte value) {
            return value;
        }

        @Specialization
        public long fromFloat(float value) {
            return (long) value;
        }

        @Specialization
        public long fromDouble(double value) {
            return (long) value;
        }

        @Specialization
        public long fromBoolean(boolean value) {
            return value ? 1 : 0;
        }

        @Specialization
        public long fromTruffleObject(TruffleObject obj) {
            try {
                Object unboxed = ForeignAccess.sendUnbox(unbox, obj);
                return (long) convertPrimitive(long.class, unboxed);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    abstract static class ToShortNode extends ToLLVMNode {
        @Specialization
        public short fromInt(int value) {
            return (short) value;
        }

        @Specialization
        public short fromChar(char value) {
            return (short) value;
        }

        @Specialization
        public short fromShort(short value) {
            return value;
        }

        @Specialization
        public short fromLong(long value) {
            return (short) value;
        }

        @Specialization
        public short fromByte(byte value) {
            return value;
        }

        @Specialization
        public short fromFloat(float value) {
            return (short) value;
        }

        @Specialization
        public short fromDouble(double value) {
            return (short) value;
        }

        @Specialization
        public short fromBoolean(boolean value) {
            return (short) (value ? 1 : 0);
        }

        @Specialization
        public long fromTruffleObject(TruffleObject obj) {
            try {
                Object unboxed = ForeignAccess.sendUnbox(unbox, obj);
                return (short) convertPrimitive(short.class, unboxed);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    abstract static class ToByteNode extends ToLLVMNode {
        @Specialization
        public byte fromInt(int value) {
            return (byte) value;
        }

        @Specialization
        public byte fromChar(char value) {
            return (byte) value;
        }

        @Specialization
        public byte fromLong(long value) {
            return (byte) value;
        }

        @Specialization
        public byte fromShort(short value) {
            return (byte) value;
        }

        @Specialization
        public byte fromByte(byte value) {
            return value;
        }

        @Specialization
        public byte fromFloat(float value) {
            return (byte) value;
        }

        @Specialization
        public byte fromDouble(double value) {
            return (byte) value;
        }

        @Specialization
        public byte fromBoolean(boolean value) {
            return (byte) (value ? 1 : 0);
        }

        @Specialization
        public byte fromTruffleObject(TruffleObject obj) {
            try {
                Object unboxed = ForeignAccess.sendUnbox(unbox, obj);
                return (byte) convertPrimitive(byte.class, unboxed);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    abstract static class ToCharNode extends ToLLVMNode {
        @Specialization
        public char fromInt(int value) {
            return (char) value;
        }

        @Specialization
        public char fromLong(long value) {
            return (char) value;
        }

        @Specialization
        public char fromChar(char value) {
            return value;
        }

        @Specialization
        public char fromShort(short value) {
            return (char) value;
        }

        @Specialization
        public char fromByte(byte value) {
            return (char) value;
        }

        @Specialization
        public char fromFloat(float value) {
            return (char) value;
        }

        @Specialization
        public char fromDouble(double value) {
            return (char) value;
        }

        @Specialization
        public char fromBoolean(boolean value) {
            return (char) (value ? 1 : 0);
        }

        @Specialization
        public char fromTruffleObject(TruffleObject obj) {
            try {
                Object unboxed = ForeignAccess.sendUnbox(unbox, obj);
                return (char) convertPrimitive(char.class, unboxed);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    abstract static class ToFloatNode extends ToLLVMNode {
        @Specialization
        public float fromInt(int value) {
            return value;
        }

        @Specialization
        public float fromLong(long value) {
            return value;
        }

        @Specialization
        public float fromChar(char value) {
            return value;
        }

        @Specialization
        public float fromShort(short value) {
            return value;
        }

        @Specialization
        public float fromByte(byte value) {
            return value;
        }

        @Specialization
        public float fromFloat(float value) {
            return value;
        }

        @Specialization
        public float fromDouble(double value) {
            return (float) value;
        }

        @Specialization
        public float fromBoolean(boolean value) {
            return (value ? 1 : 0);
        }

        @Specialization
        public float fromTruffleObject(TruffleObject obj) {
            try {
                Object unboxed = ForeignAccess.sendUnbox(unbox, obj);
                return (float) convertPrimitive(float.class, unboxed);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    abstract static class ToDoubleNode extends ToLLVMNode {
        @Specialization
        public double fromInt(int value) {
            return value;
        }

        @Specialization
        public double fromChar(char value) {
            return value;
        }

        @Specialization
        public double fromLong(long value) {
            return value;
        }

        @Specialization
        public double fromByte(byte value) {
            return value;
        }

        @Specialization
        public double fromShort(short value) {
            return value;
        }

        @Specialization
        public double fromFloat(float value) {
            return value;
        }

        @Specialization
        public double fromDouble(double value) {
            return value;
        }

        @Specialization
        public double fromBoolean(boolean value) {
            return (value ? 1 : 0);
        }

        @Specialization
        public double fromTruffleObject(TruffleObject obj) {
            try {
                Object unboxed = ForeignAccess.sendUnbox(unbox, obj);
                return (double) convertPrimitive(double.class, unboxed);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    abstract static class ToBooleanNode extends ToLLVMNode {
        @Specialization
        public boolean fromInt(int value) {
            return value != 0;
        }

        @Specialization
        public boolean fromChar(char value) {
            return value != 0;
        }

        @Specialization
        public boolean fromShort(short value) {
            return value != 0;
        }

        @Specialization
        public boolean fromLong(long value) {
            return value != 0;
        }

        @Specialization
        public boolean fromByte(byte value) {
            return value != 0;
        }

        @Specialization
        public boolean fromFloat(float value) {
            return value != 0;
        }

        @Specialization
        public boolean fromDouble(double value) {
            return value != 0;
        }

        @Specialization
        public boolean fromBoolean(boolean value) {
            return value;
        }

        @Specialization
        public boolean fromTruffleObject(TruffleObject obj) {
            try {
                Object unboxed = ForeignAccess.sendUnbox(unbox, obj);
                return (boolean) convertPrimitive(boolean.class, unboxed);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    abstract static class ToTruffleObject extends ToLLVMNode {
        @Specialization
        public int fromInt(int value) {
            return value;
        }

        @Specialization
        public char fromChar(char value) {
            return value;
        }

        @Specialization
        public long fromLong(long value) {
            return value;
        }

        @Specialization
        public byte fromByte(byte value) {
            return value;
        }

        @Specialization
        public short fromShort(short value) {
            return value;
        }

        @Specialization
        public float fromFloat(float value) {
            return value;
        }

        @Specialization
        public double fromDouble(double value) {
            return value;
        }

        @Specialization
        public boolean fromBoolean(boolean value) {
            return value;
        }

        @Specialization
        public String fromString(String obj) {
            return obj;
        }

        @Specialization
        public TruffleObject fromTruffleObject(TruffleObject obj) {
            return obj;
        }
    }

    public static Class<?> convert(LLVMRuntimeType type) {
        Class<?> t;
        switch (type) {
            case I1:
                t = boolean.class;
                break;
            case I8:
                t = byte.class;
                break;
            case I16:
                t = short.class;
                break;
            case I32:
                t = int.class;
                break;
            case I64:
                t = long.class;
                break;
            case FLOAT:
                t = float.class;
                break;
            case DOUBLE:
                t = double.class;
                break;
            case I1_POINTER:
            case I8_POINTER:
            case I16_POINTER:
            case I32_POINTER:
            case I64_POINTER:
            case HALF_POINTER:
            case FLOAT_POINTER:
            case DOUBLE_POINTER:
            case ADDRESS:
            case FUNCTION_ADDRESS:
                t = TruffleObject.class;
                break;
            case VOID:
                t = void.class;
                break;
            default:
                throw UnsupportedTypeException.raise(new Object[]{type});
        }
        return t;
    }

    static final class SlowConvertNodeObject extends ToLLVMNode {

        @Override
        public Object executeWithTarget(Object value) {
            return value;
        }
    }

    public Object slowConvert(Object value, Class<?> requestedType) {
        Object attr;
        if (value instanceof TruffleObject) {
            if (!Boolean.TRUE.equals(ForeignAccess.sendIsBoxed(isBoxed, (TruffleObject) value))) {
                return null;
            }
            try {
                attr = ForeignAccess.sendUnbox(unbox, (TruffleObject) value);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedTypeException.raise(new Object[]{value});
            }
        } else {
            attr = value;
        }
        return convertPrimitive(requestedType, attr);
    }

    @TruffleBoundary
    private static boolean isPrimitiveType(Class<?> clazz) {
        CompilerAsserts.compilationConstant(clazz);
        return clazz == int.class || clazz == Integer.class ||
                        clazz == boolean.class || clazz == Boolean.class ||
                        clazz == byte.class || clazz == Byte.class ||
                        clazz == short.class || clazz == Short.class ||
                        clazz == long.class || clazz == Long.class ||
                        clazz == float.class || clazz == Float.class ||
                        clazz == double.class || clazz == Double.class ||
                        clazz == char.class || clazz == Character.class ||
                        CharSequence.class.isAssignableFrom(clazz);
    }

    @TruffleBoundary
    private static Object convertPrimitive(Class<?> requestedType, Object attr) {
        if (attr instanceof Number) {
            if (requestedType == null) {
                return attr;
            }
            Number n = (Number) attr;
            if (requestedType == byte.class || requestedType == Byte.class) {
                return n.byteValue();
            }
            if (requestedType == short.class || requestedType == Short.class) {
                return n.shortValue();
            }
            if (requestedType == int.class || requestedType == Integer.class) {
                return n.intValue();
            }
            if (requestedType == long.class || requestedType == Long.class) {
                return n.longValue();
            }
            if (requestedType == float.class || requestedType == Float.class) {
                return n.floatValue();
            }
            if (requestedType == double.class || requestedType == Double.class) {
                return n.doubleValue();
            }
            if (requestedType == char.class || requestedType == Character.class) {
                return (char) n.intValue();
            }
            return n;
        }
        if (attr instanceof CharSequence) {
            if (requestedType == char.class || requestedType == Character.class) {
                if (((String) attr).length() == 1) {
                    return ((String) attr).charAt(0);
                }
            }
            return String.valueOf(attr);
        }
        if (attr instanceof Character) {
            return attr;
        }
        if (attr instanceof Boolean) {
            return attr;
        }
        return null;
    }
}
