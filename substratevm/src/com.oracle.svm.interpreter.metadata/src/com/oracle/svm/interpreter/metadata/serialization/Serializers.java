/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.interpreter.metadata.serialization;

import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ToLongFunction;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.FunctionPointerHolder;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.ParserConstantPool;
import com.oracle.svm.espresso.classfile.descriptors.ModifiedUTF8;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.interpreter.metadata.InterpreterConstantPool;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedPrimitiveType;
import com.oracle.svm.interpreter.metadata.InterpreterUnresolvedSignature;
import com.oracle.svm.interpreter.metadata.ReferenceConstant;

import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Serializers for types included in the interpreter metadata.
 */
public final class Serializers {

    @VisibleForSerialization
    public static <T> ValueSerializer<T> createSerializer(ValueReader<T> reader, ValueWriter<T> writer) {
        return new ValueSerializer<>(reader, writer);
    }

    public static final ValueSerializer<byte[]> BYTE_ARRAY = createSerializer(
                    (context, in) -> {
                        int length = LEB128.readUnsignedInt(in);
                        byte[] bytes = new byte[length];
                        in.readFully(bytes);
                        return bytes;
                    },
                    (context, out, value) -> {
                        LEB128.writeUnsignedInt(out, value.length);
                        out.write(value);
                    });

    public static final ValueSerializer<String> STRING = createSerializer(
                    (context, in) -> {
                        byte[] bytes = BYTE_ARRAY.getReader().read(context, in);
                        return new String(bytes, StandardCharsets.UTF_8);
                    },
                    (context, out, value) -> {
                        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                        BYTE_ARRAY.getWriter().write(context, out, bytes);
                    });

    public static final ValueSerializer<Class<?>> CLASS_BY_NAME = createSerializer(
                    (context, in) -> {
                        boolean isPrimitive = in.readBoolean();
                        if (isPrimitive) {
                            return JavaKind.fromPrimitiveOrVoidTypeChar(in.readChar()).toJavaClass();
                        } else {
                            String name = STRING.getReader().read(context, in);
                            try {
                                return Class.forName(name);
                            } catch (ClassNotFoundException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    },
                    (context, out, value) -> {
                        boolean isPrimitive = value.isPrimitive();
                        out.writeBoolean(isPrimitive);
                        if (isPrimitive) {
                            out.writeChar(JavaKind.fromJavaClass(value).getTypeChar());
                        } else {
                            STRING.getWriter().write(context, out, value.getName());
                        }
                    });
    static final ValueSerializer<boolean[]> BOOLEAN_ARRAY = createSerializer(
                    (context, in) -> {
                        int length = LEB128.readUnsignedInt(in);
                        boolean[] array = new boolean[length];
                        for (int i = 0; i < length; ++i) {
                            array[i] = in.readBoolean();
                        }
                        return array;
                    },
                    (context, out, value) -> {
                        LEB128.writeUnsignedInt(out, value.length);
                        for (boolean e : value) {
                            out.writeBoolean(e);
                        }
                    });

    static final ValueSerializer<int[]> INT_ARRAY = createSerializer(
                    (context, in) -> {
                        int length = LEB128.readUnsignedInt(in);
                        int[] array = new int[length];
                        for (int i = 0; i < length; ++i) {
                            array[i] = in.readInt();
                        }
                        return array;
                    },
                    (context, out, value) -> {
                        LEB128.writeUnsignedInt(out, value.length);
                        for (int e : value) {
                            out.writeInt(e);
                        }
                    });

    static final ValueSerializer<short[]> SHORT_ARRAY = createSerializer(
                    (context, in) -> {
                        int length = LEB128.readUnsignedInt(in);
                        short[] array = new short[length];
                        for (int i = 0; i < length; ++i) {
                            array[i] = in.readShort();
                        }
                        return array;
                    },
                    (context, out, value) -> {
                        LEB128.writeUnsignedInt(out, value.length);
                        for (short e : value) {
                            out.writeShort(e);
                        }
                    });

    static final ValueSerializer<char[]> CHAR_ARRAY = createSerializer(
                    (context, in) -> {
                        int length = LEB128.readUnsignedInt(in);
                        char[] array = new char[length];
                        for (int i = 0; i < length; ++i) {
                            array[i] = in.readChar();
                        }
                        return array;
                    },
                    (context, out, value) -> {
                        LEB128.writeUnsignedInt(out, value.length);
                        for (char e : value) {
                            out.writeChar(e);
                        }
                    });

    static final ValueSerializer<float[]> FLOAT_ARRAY = createSerializer(
                    (context, in) -> {
                        int length = LEB128.readUnsignedInt(in);
                        float[] array = new float[length];
                        for (int i = 0; i < length; ++i) {
                            array[i] = in.readFloat();
                        }
                        return array;
                    },
                    (context, out, value) -> {
                        LEB128.writeUnsignedInt(out, value.length);
                        for (float e : value) {
                            out.writeFloat(e);
                        }
                    });

    static final ValueSerializer<double[]> DOUBLE_ARRAY = createSerializer(
                    (context, in) -> {
                        int length = LEB128.readUnsignedInt(in);
                        double[] array = new double[length];
                        for (int i = 0; i < length; ++i) {
                            array[i] = in.readDouble();
                        }
                        return array;
                    },
                    (context, out, value) -> {
                        LEB128.writeUnsignedInt(out, value.length);
                        for (double e : value) {
                            out.writeDouble(e);
                        }
                    });

    static final ValueSerializer<long[]> LONG_ARRAY = createSerializer(
                    (context, in) -> {
                        int length = LEB128.readUnsignedInt(in);
                        long[] array = new long[length];
                        for (int i = 0; i < length; ++i) {
                            array[i] = in.readLong();
                        }
                        return array;
                    },
                    (context, out, value) -> {
                        LEB128.writeUnsignedInt(out, value.length);
                        for (long e : value) {
                            out.writeLong(e);
                        }
                    });

    public static <E extends Enum<E>> ValueSerializer<E> ofEnum(Class<E> enumClass) {
        return createSerializer(
                        (context, in) -> {
                            String name = context.readReference(in);
                            return Enum.valueOf(enumClass, name);
                        },
                        (context, out, value) -> {
                            context.writeReference(out, value.name());
                        });
    }

    public static <T> ValueSerializer<T[]> ofReferenceArray(IntFunction<T[]> allocateArray) {
        return createSerializer(
                        (context, in) -> {
                            int length = LEB128.readUnsignedInt(in);
                            T[] array = allocateArray.apply(length);
                            for (int i = 0; i < length; i++) {
                                array[i] = context.readReference(in);
                            }
                            return array;
                        },
                        (context, out, value) -> {
                            int length = value.length;
                            LEB128.writeUnsignedInt(out, length);
                            for (T e : value) {
                                context.writeReference(out, e);
                            }
                        });
    }

    private static final ValueSerializer<?> AS_REFERENCE_CONSTANT = createSerializer(
                    (context, in) -> {
                        ReferenceConstant<?> ref = context.readerFor(ReferenceConstant.class).read(context, in);
                        return ref.getReferent();
                    },
                    (context, out, value) -> {
                        context.writerFor(ReferenceConstant.class).write(context, out, ReferenceConstant.createFromNonNullReference(value));
                    });

    /**
     * Force serialization of reference using the ReferenceConstant serializer within the context.
     */
    @SuppressWarnings("unchecked")
    static <T> ValueSerializer<T> asReferenceConstant() {
        return (ValueSerializer<T>) AS_REFERENCE_CONSTANT;
    }

    public static final ValueReader<ReferenceConstant<?>> REFERENCE_CONSTANT_READER = (context, in) -> {
        boolean inNativeHeap = in.readBoolean();
        if (inNativeHeap) {
            long nativeHeapAddress = in.readLong();
            if (nativeHeapAddress == 0L) {
                return ReferenceConstant.nullReference();
            }
            Pointer heapBase = KnownIntrinsics.heapBase();
            Object ref = heapBase.add(Word.unsigned(nativeHeapAddress)).toObject();
            return ReferenceConstant.createFromNonNullReference(ref);
        } else {
            // The reference could have been serialized despite not being on the native image heap.
            // This may happen for some known types e.g. String.
            // Or can be serialized as null.
            Object ref = context.readReference(in);
            return ReferenceConstant.createFromReference(ref);
        }
    };

    @Platforms(Platform.HOSTED_ONLY.class)
    public static ValueWriter<ReferenceConstant<?>> newReferenceConstantWriter(ToLongFunction<Object> getImageHeapOffset) {
        return (context, out, value) -> {
            Object ref = value.getReferent();
            long imageHeapOffset = ref == null
                            ? 0L
                            : getImageHeapOffset.applyAsLong(ref);

            // The reference was not included in the image heap.
            boolean inImageHeap = (ref == null || imageHeapOffset != 0L);
            assert !(ref == null) || inImageHeap; // ref == null => inImageHeap
            out.writeBoolean(inImageHeap);
            if (inImageHeap) {
                out.writeLong(imageHeapOffset);
            } else if (ref instanceof String) {
                // Allow Strings to be serialized despite not being on the native heap.
                context.writeReference(out, ref);
            } else {
                // Serialize as null.
                context.writeReference(out, null);
            }
        };
    }

    public static ValueSerializer<ReferenceConstant<?>> newReferenceConstantSerializer(ToLongFunction<Object> getNativeHeapAddress) {
        return createSerializer(REFERENCE_CONSTANT_READER, newReferenceConstantWriter(getNativeHeapAddress));
    }

    static final ValueSerializer<JavaKind> JAVA_KIND = Serializers.ofEnum(JavaKind.class);

    static final ValueSerializer<UnresolvedJavaType> UNRESOLVED_TYPE = createSerializer(
                    (context, in) -> {
                        String name = context.readReference(in);
                        return UnresolvedJavaType.create(name);
                    },
                    (context, out, value) -> {
                        context.writeReference(out, value.getName());
                    });

    static final ValueSerializer<UnresolvedJavaMethod> UNRESOLVED_METHOD = createSerializer(
                    (context, in) -> {
                        String name = context.readReference(in);
                        InterpreterUnresolvedSignature signature = context.readReference(in);
                        JavaType holder = context.readReference(in);
                        assert holder instanceof InterpreterResolvedPrimitiveType || holder instanceof UnresolvedJavaType;
                        return new UnresolvedJavaMethod(name, signature, holder);
                    },
                    (context, out, value) -> {
                        context.writeReference(out, value.getName());
                        assert value.getSignature() instanceof InterpreterUnresolvedSignature;
                        context.writeReference(out, value.getSignature());
                        context.writeReference(out, value.getDeclaringClass());
                    });

    static final ValueSerializer<UnresolvedJavaField> UNRESOLVED_FIELD = createSerializer(
                    (context, in) -> {
                        JavaType holder = context.readReference(in);
                        assert holder instanceof InterpreterResolvedPrimitiveType || holder instanceof UnresolvedJavaType;
                        String name = context.readReference(in);
                        JavaType type = context.readReference(in);
                        assert type instanceof InterpreterResolvedPrimitiveType || type instanceof UnresolvedJavaType;
                        return new UnresolvedJavaField(holder, name, type);
                    },
                    (context, out, value) -> {
                        context.writeReference(out, value.getDeclaringClass());
                        context.writeReference(out, value.getName());
                        context.writeReference(out, value.getType());
                    });

    static final ValueSerializer<InterpreterResolvedPrimitiveType> PRIMITIVE_TYPE = createSerializer(
                    (context, in) -> {
                        JavaKind kind = context.readReference(in);
                        return InterpreterResolvedPrimitiveType.fromKind(kind);
                    },
                    (context, out, value) -> {
                        context.writeReference(out, value.getJavaKind());
                    });

    static final ValueSerializer<InterpreterUnresolvedSignature> UNRESOLVED_SIGNATURE = createSerializer(
                    (context, in) -> {
                        JavaType returnType = context.readReference(in);
                        int length = LEB128.readUnsignedInt(in);
                        JavaType[] parameterTypes = new JavaType[length];
                        for (int i = 0; i < length; ++i) {
                            parameterTypes[i] = context.readReference(in);
                        }
                        return InterpreterUnresolvedSignature.create(returnType, parameterTypes);
                    },
                    (context, out, value) -> {
                        JavaType returnType = value.getReturnType(null);
                        context.writeReference(out, returnType);
                        int length = value.getParameterCount(false);
                        LEB128.writeUnsignedInt(out, length);
                        for (int i = 0; i < length; ++i) {
                            JavaType parameterType = value.getParameterType(i, null);
                            context.writeReference(out, parameterType);
                        }
                    });

    static final ValueSerializer<MethodType> METHOD_TYPE = createSerializer(
                    (context, in) -> {
                        Class<?> returnType = context.readReference(in);
                        int length = LEB128.readUnsignedInt(in);
                        Class<?>[] parameterTypes = new Class<?>[length];
                        for (int i = 0; i < length; ++i) {
                            parameterTypes[i] = context.readReference(in);
                        }
                        return MethodType.methodType(returnType, parameterTypes);
                    },
                    (context, out, value) -> {
                        Class<?> returnType = value.returnType();
                        context.writeReference(out, returnType);
                        int length = value.parameterCount();
                        LEB128.writeUnsignedInt(out, length);
                        for (int i = 0; i < length; ++i) {
                            Class<?> parameterType = value.parameterType(i);
                            context.writeReference(out, parameterType);
                        }
                    });

    static final ValueSerializer<Local> LOCAL = createSerializer(
                    (context, in) -> {
                        String name = context.readReference(in);
                        JavaType type = context.readReference(in);
                        int startBci = LEB128.readUnsignedInt(in);
                        int endBci = LEB128.readUnsignedInt(in);
                        int slot = LEB128.readUnsignedInt(in);
                        return new Local(name, type, startBci, endBci, slot);
                    },
                    (context, out, value) -> {
                        context.writeReference(out, value.getName());
                        context.writeReference(out, value.getType());
                        LEB128.writeUnsignedInt(out, value.getStartBCI());
                        LEB128.writeUnsignedInt(out, value.getEndBCI());
                        LEB128.writeUnsignedInt(out, value.getSlot());
                    });

    static final ValueSerializer<ExceptionHandler> EXCEPTION_HANDLER = createSerializer(
                    (context, in) -> {
                        int startBCI = LEB128.readUnsignedInt(in);
                        int endBCI = LEB128.readUnsignedInt(in);
                        int catchBCI = LEB128.readUnsignedInt(in);
                        int catchTypeCPI = LEB128.readUnsignedInt(in);
                        JavaType catchType = context.readReference(in);
                        return new ExceptionHandler(startBCI, endBCI, catchBCI, catchTypeCPI, catchType);
                    },
                    (context, out, value) -> {
                        LEB128.writeUnsignedInt(out, value.getStartBCI());
                        LEB128.writeUnsignedInt(out, value.getEndBCI());
                        LEB128.writeUnsignedInt(out, value.getHandlerBCI());
                        LEB128.writeUnsignedInt(out, value.catchTypeCPI());
                        context.writeReference(out, value.getCatchType());
                    });

    static final ValueSerializer<LocalVariableTable> LOCAL_VARIABLE_TABLE = createSerializer(
                    (context, in) -> {
                        Local[] locals = context.readerFor(Local[].class).read(context, in);
                        return new LocalVariableTable(locals);
                    },
                    (context, out, value) -> {
                        context.writerFor(Local[].class).write(context, out, value.getLocals());
                    });

    static final int[] EMPTY_INT_ARRAY = new int[0];

    static final ValueSerializer<LineNumberTable> LINE_NUMBER_TABLE = createSerializer(
                    (context, in) -> {
                        int length = LEB128.readUnsignedInt(in);
                        if (length == 0) {
                            return new LineNumberTable(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
                        }
                        int[] lineNumbers = new int[length];
                        int[] bcis = new int[length];

                        int lastLineNumber = 0;
                        int lastBci = 0;
                        for (int i = 0; i < length; i++) {
                            int curLineNumber = lastLineNumber + LEB128.readSignedInt(in);
                            int curBci = lastBci + LEB128.readSignedInt(in);
                            lineNumbers[i] = curLineNumber;
                            bcis[i] = curBci;

                            lastLineNumber = curLineNumber;
                            lastBci = curBci;
                        }

                        return new LineNumberTable(lineNumbers, bcis);
                    },
                    (context, out, value) -> {
                        int[] lines = value.getLineNumbers();
                        int[] bcis = value.getBcis();
                        VMError.guarantee(lines.length == bcis.length);
                        LEB128.writeUnsignedInt(out, lines.length);
                        int lastLineNumber = 0;
                        int lastBci = 0;
                        for (int i = 0; i < lines.length; i++) {
                            LEB128.writeSignedInt(out, lines[i] - lastLineNumber);
                            LEB128.writeSignedInt(out, bcis[i] - lastBci);
                            lastLineNumber = lines[i];
                            lastBci = bcis[i];
                        }
                    });

    static final ValueSerializer<PrimitiveConstant> PRIMITIVE_CONSTANT = createSerializer(
                    (context, in) -> {
                        JavaKind kind = context.readReference(in);
                        long rawValue = in.readLong();
                        if (kind == JavaKind.Illegal) {
                            // JavaConstant.forPrimitive below throws for JavaKind.Illegal.
                            return JavaConstant.forIllegal();
                        }
                        return JavaConstant.forPrimitive(kind, rawValue);
                    },
                    (context, out, value) -> {
                        context.writeReference(out, value.getJavaKind());
                        out.writeLong(value.getRawValue());
                    });

    // Register this serializer for JavaConstant.NULL_POINTER.getClass().
    static final ValueSerializer<? extends JavaConstant> NULL_CONSTANT = createSerializer(
                    (context, in) -> {
                        return JavaConstant.NULL_POINTER;
                    },
                    (context, out, value) -> {
                        // nop
                    });

    static final ValueSerializer<InterpreterConstantPool> CONSTANT_POOL = createSerializer(
                    (context, in) -> {
                        InterpreterResolvedObjectType holder = context.readReference(in);
                        byte[] parserConstantPoolBytes = context.readerFor(byte[].class).read(context, in);

                        @SuppressWarnings("unchecked")
                        ParserConstantPool parserConstantPool = ParserConstantPool.fromBytesForSerialization(parserConstantPoolBytes,
                                        byteSequence -> {
                                            return (Symbol<ModifiedUTF8>) SymbolsSupport.getUtf8().getOrCreateValidUtf8(byteSequence);
                                        });
                        Object[] cachedEntries = context.readerFor(Object[].class).read(context, in);
                        return InterpreterConstantPool.create(holder, parserConstantPool, cachedEntries);
                    },
                    (context, out, value) -> {
                        context.writeReference(out, value.getHolder());
                        context.writerFor(byte[].class).write(context, out, value.getParserConstantPool().toBytesForSerialization());
                        context.writerFor(Object[].class).write(context, out, value.getCachedEntries());
                    });

    static final ValueSerializer<InterpreterResolvedJavaField> RESOLVED_FIELD = createSerializer(
                    (context, in) -> {
                        String name = context.readReference(in);
                        InterpreterResolvedJavaType type = context.readReference(in);
                        InterpreterResolvedObjectType declaringClass = context.readReference(in);
                        int modifiers = LEB128.readUnsignedInt(in);
                        int offset = LEB128.readUnsignedInt(in);
                        JavaConstant constant = context.readReference(in);
                        return InterpreterResolvedJavaField.create(name, modifiers, type, declaringClass, offset, constant);
                    },
                    (context, out, value) -> {
                        context.writeReference(out, value.getName());
                        context.writeReference(out, value.getType());
                        context.writeReference(out, value.getDeclaringClass());
                        LEB128.writeUnsignedInt(out, value.getModifiers());
                        LEB128.writeUnsignedInt(out, value.getOffset());
                        if (value.isUnmaterializedConstant()) {
                            JavaConstant constant = value.getUnmaterializedConstant();
                            context.writeReference(out, constant);
                        } else {
                            context.writeReference(out, null);
                        }
                    });

    static final ValueSerializer<InterpreterResolvedObjectType> OBJECT_TYPE = createSerializer(
                    (context, in) -> {
                        String name = context.readReference(in);
                        int modifiers = LEB128.readUnsignedInt(in);
                        InterpreterResolvedJavaType componentType = context.readReference(in);

                        InterpreterResolvedObjectType superclass = context.readReference(in);
                        InterpreterResolvedObjectType[] interfaces = context.readReference(in);
                        /* vtable is serialized later to avoid cycle dependencies via methods */

                        InterpreterConstantPool constantPool = context.readReference(in);
                        ReferenceConstant<Class<?>> clazzConstant = context.readReference(in);
                        boolean isWordType = in.readBoolean();
                        String sourceFileName = context.readReference(in);
                        if (clazzConstant.isOpaque()) {
                            return InterpreterResolvedObjectType.createWithOpaqueClass(name, modifiers, componentType, superclass, interfaces, constantPool, clazzConstant, isWordType, sourceFileName);
                        } else {
                            return InterpreterResolvedObjectType.createForInterpreter(name, modifiers, componentType, superclass, interfaces, constantPool, clazzConstant.getReferent(), isWordType);
                        }
                    },
                    (context, out, value) -> {
                        String name = value.getName();
                        int modifiers = value.getModifiers();
                        InterpreterResolvedJavaType componentType = value.getComponentType();

                        InterpreterResolvedObjectType superclass = value.getSuperclass();
                        InterpreterResolvedObjectType[] interfaces = value.getInterfaces();

                        // Constant pools are serialized separately, to break reference cycles, and
                        // patched after deserialization.
                        InterpreterConstantPool constantPool = null;

                        Class<?> javaClass = value.getJavaClass();
                        ReferenceConstant<Class<?>> clazzConstant = ReferenceConstant.createFromNonNullReference(javaClass);

                        context.writeReference(out, name);
                        LEB128.writeUnsignedInt(out, modifiers);
                        context.writeReference(out, componentType);

                        context.writeReference(out, superclass);
                        context.writeReference(out, interfaces);

                        context.writeReference(out, constantPool);
                        context.writeReference(out, clazzConstant);
                        out.writeBoolean(value.isWordType());
                        context.writeReference(out, value.getSourceFileName());
                    });

    static final ValueSerializer<InterpreterResolvedObjectType.VTableHolder> VTABLE_HOLDER = createSerializer(
                    (context, in) -> {
                        InterpreterResolvedObjectType holder = context.readReference(in);
                        InterpreterResolvedJavaMethod[] vtable = context.readerFor(InterpreterResolvedJavaMethod[].class).read(context, in);
                        return new InterpreterResolvedObjectType.VTableHolder(holder, vtable);
                    },
                    (context, out, value) -> {
                        context.writeReference(out, value.holder);
                        context.writerFor(InterpreterResolvedJavaMethod[].class).write(context, out, value.vtable);
                    });

    static final ValueSerializer<InterpreterResolvedJavaMethod> RESOLVED_METHOD = createSerializer(
                    (context, in) -> {
                        String name = context.readReference(in);
                        int maxLocals = LEB128.readUnsignedInt(in);
                        int maxStackSize = LEB128.readUnsignedInt(in);
                        int modifiers = LEB128.readUnsignedInt(in);
                        InterpreterResolvedObjectType declaringClass = context.readReference(in);
                        InterpreterUnresolvedSignature signature = context.readReference(in);
                        byte[] code = context.readReference(in);
                        ExceptionHandler[] exceptionHandlers = context.readReference(in);
                        LineNumberTable lineNumberTable = context.readReference(in);
                        LocalVariableTable localVariableTable = context.readReference(in);

                        ReferenceConstant<FunctionPointerHolder> nativeEntryPoint = context.readReference(in);
                        int vtableIndex = LEB128.readUnsignedInt(in);
                        int gotOffset = LEB128.readUnsignedInt(in);
                        int enterStubOffset = LEB128.readUnsignedInt(in);
                        int methodId = LEB128.readUnsignedInt(in);

                        return InterpreterResolvedJavaMethod.create(name, maxLocals, maxStackSize, modifiers, declaringClass, signature, code, exceptionHandlers, lineNumberTable, localVariableTable,
                                        nativeEntryPoint, vtableIndex, gotOffset, enterStubOffset, methodId);
                    },
                    (context, out, value) -> {
                        String name = value.getName();
                        int maxLocals = value.getMaxLocals();
                        int maxStackSize = value.getMaxStackSize();
                        int modifiers = value.getModifiers();
                        InterpreterResolvedObjectType declaringClass = value.getDeclaringClass();
                        InterpreterUnresolvedSignature signature = value.getSignature();
                        byte[] code = value.getInterpretedCode();
                        ExceptionHandler[] exceptionHandlers = value.getExceptionHandlers();
                        LineNumberTable lineNumberTable = value.getLineNumberTable();
                        LocalVariableTable localVariableTable = value.getLocalVariableTable();
                        /*
                         * `inlinedBy` and `oneImplementation` serialized separately to break
                         * reference cycle
                         */

                        ReferenceConstant<FunctionPointerHolder> nativeEntryPointHolder = value.getNativeEntryPointHolderConstant();
                        int vtableIndex = value.getVTableIndex();
                        int gotOffset = value.getGotOffset();
                        int enterStubOffset = value.getEnterStubOffset();
                        int methodId = value.getMethodId();

                        context.writeReference(out, name);
                        LEB128.writeUnsignedInt(out, maxLocals);
                        LEB128.writeUnsignedInt(out, maxStackSize);
                        LEB128.writeUnsignedInt(out, modifiers);
                        context.writeReference(out, declaringClass);
                        context.writeReference(out, signature);
                        context.writeReference(out, code);
                        context.writeReference(out, exceptionHandlers);
                        context.writeReference(out, lineNumberTable);
                        context.writeReference(out, localVariableTable);

                        context.writeReference(out, nativeEntryPointHolder);
                        LEB128.writeUnsignedInt(out, vtableIndex);
                        LEB128.writeUnsignedInt(out, gotOffset);
                        LEB128.writeUnsignedInt(out, enterStubOffset);
                        LEB128.writeUnsignedInt(out, methodId);
                    });
    static final ValueSerializer<InterpreterResolvedJavaMethod.InlinedBy> INLINED_BY = createSerializer(
                    (context, in) -> {
                        InterpreterResolvedJavaMethod holder = context.readReference(in);
                        int size = LEB128.readUnsignedInt(in);

                        InterpreterResolvedJavaMethod.InlinedBy ret = new InterpreterResolvedJavaMethod.InlinedBy(holder, new HashSet<>());
                        while (size > 0) {
                            ret.inliners.add(context.readReference(in));
                            size--;
                        }
                        return ret;
                    },
                    (context, out, value) -> {
                        InterpreterResolvedJavaMethod holder = value.holder;
                        int size = value.inliners.size();

                        context.writeReference(out, holder);
                        LEB128.writeUnsignedInt(out, size);
                        for (InterpreterResolvedJavaMethod m : value.inliners) {
                            context.writeReference(out, m);
                        }
                    });

    public static final List<Class<?>> UNIVERSE_KNOWN_CLASSES = List.of(
                    byte[].class,
                    String.class,
                    JavaKind.class,
                    UnresolvedJavaType.class,
                    UnresolvedJavaField.class,
                    UnresolvedJavaMethod.class,
                    InterpreterUnresolvedSignature.class,
                    Local.class,
                    Local[].class,
                    LocalVariableTable.class,
                    LineNumberTable.class,
                    ExceptionHandler.class,
                    ExceptionHandler[].class,
                    PrimitiveConstant.class,
                    JavaConstant.NULL_POINTER.getClass(),
                    MethodType.class,
                    InterpreterConstantPool.class,
                    Object[].class,
                    InterpreterResolvedObjectType[].class,
                    InterpreterResolvedJavaMethod[].class,
                    ReferenceConstant.class,
                    InterpreterResolvedPrimitiveType.class,
                    InterpreterResolvedObjectType.class,
                    InterpreterResolvedObjectType.VTableHolder.class,
                    InterpreterResolvedJavaField.class,
                    FunctionPointerHolder.class,
                    InterpreterResolvedJavaMethod.class,
                    InterpreterResolvedJavaMethod.InlinedBy.class);

    @Platforms(Platform.HOSTED_ONLY.class)
    public static SerializationContext.Builder newBuilderForInterpreterMetadata() {
        @SuppressWarnings("unchecked")
        Class<JavaConstant> nullConstantClass = (Class<JavaConstant>) JavaConstant.NULL_POINTER.getClass();
        return SerializationContext.newBuilder()
                        .setKnownClasses(UNIVERSE_KNOWN_CLASSES)
                        // Only UNIVERSE_KNOWN_CLASSES can be (de-)serialized.
                        .registerSerializer(byte[].class, BYTE_ARRAY)
                        .registerSerializer(String.class, STRING)
                        .registerSerializer(JavaKind.class, JAVA_KIND)
                        .registerSerializer(UnresolvedJavaType.class, UNRESOLVED_TYPE)
                        .registerSerializer(UnresolvedJavaField.class, UNRESOLVED_FIELD)
                        .registerSerializer(UnresolvedJavaMethod.class, UNRESOLVED_METHOD)
                        .registerSerializer(InterpreterUnresolvedSignature.class, UNRESOLVED_SIGNATURE)
                        .registerSerializer(Local.class, LOCAL)
                        .registerSerializer(Local[].class, ofReferenceArray(Local[]::new))
                        .registerSerializer(LocalVariableTable.class, LOCAL_VARIABLE_TABLE)
                        .registerSerializer(LineNumberTable.class, LINE_NUMBER_TABLE)
                        .registerSerializer(ExceptionHandler.class, EXCEPTION_HANDLER)
                        .registerSerializer(ExceptionHandler[].class, ofReferenceArray(ExceptionHandler[]::new))
                        .registerSerializer(PrimitiveConstant.class, PRIMITIVE_CONSTANT)
                        .registerSerializer(nullConstantClass, NULL_CONSTANT)
                        .registerSerializer(MethodType.class, METHOD_TYPE)
                        .registerSerializer(InterpreterConstantPool.class, CONSTANT_POOL)
                        .registerSerializer(Object[].class, ofReferenceArray(Object[]::new)) // CP-entries
                        .registerSerializer(InterpreterResolvedObjectType[].class, ofReferenceArray(InterpreterResolvedObjectType[]::new)) // interfaces
                        .registerSerializer(InterpreterResolvedJavaMethod[].class, ofReferenceArray(InterpreterResolvedJavaMethod[]::new)) // vtable
                        .registerSerializer(InterpreterResolvedPrimitiveType.class, PRIMITIVE_TYPE)
                        .registerSerializer(InterpreterResolvedObjectType.class, OBJECT_TYPE)
                        .registerSerializer(InterpreterResolvedObjectType.VTableHolder.class, VTABLE_HOLDER)
                        .registerSerializer(InterpreterResolvedJavaField.class, RESOLVED_FIELD)
                        .registerSerializer(FunctionPointerHolder.class, asReferenceConstant())
                        .registerSerializer(InterpreterResolvedJavaMethod.class, RESOLVED_METHOD)
                        .registerSerializer(InterpreterResolvedJavaMethod.InlinedBy.class, INLINED_BY)
                        .registerReader(ReferenceConstant.class, REFERENCE_CONSTANT_READER)
                        // The ReferenceConstant writer must be patched at build time.
                        .registerWriter(ReferenceConstant.class, (context, out, value) -> {
                            throw VMError.shouldNotReachHereAtRuntime();
                        });
    }
}
