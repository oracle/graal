/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.graph.UnsafeAccess.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.lang.invoke.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;

/**
 * Implementation of {@link ConstantPool} for HotSpot.
 */
public class HotSpotConstantPool extends CompilerObject implements ConstantPool {

    private static final long serialVersionUID = -5443206401485234850L;

    /**
     * Reference to the C++ ConstantPool object.
     */
    private final long metaspaceConstantPool;

    public HotSpotConstantPool(long metaspaceConstantPool) {
        this.metaspaceConstantPool = metaspaceConstantPool;
    }

    /**
     * Gets the holder for this constant pool as {@link HotSpotResolvedObjectType}.
     * 
     * @return holder for this constant pool
     */
    private HotSpotResolvedObjectType getHolder() {
        final long metaspaceKlass = unsafe.getAddress(metaspaceConstantPool + runtime().getConfig().constantPoolHolderOffset);
        return (HotSpotResolvedObjectType) HotSpotResolvedObjectType.fromMetaspaceKlass(metaspaceKlass);
    }

    /**
     * Converts a raw index from the bytecodes to a constant pool index by adding a
     * {@link HotSpotVMConfig#constantPoolCpCacheIndexTag constant}.
     * 
     * @param rawIndex index from the bytecode
     * @param opcode bytecode to convert the index for
     * @return constant pool index
     */
    private static int toConstantPoolIndex(int rawIndex, int opcode) {
        int index;
        if (opcode == Bytecodes.INVOKEDYNAMIC) {
            index = rawIndex;
            // See: ConstantPool::is_invokedynamic_index
            assert index < 0 : "not an invokedynamic constant pool index " + index;
        } else {
            assert opcode == Bytecodes.GETFIELD || opcode == Bytecodes.PUTFIELD || opcode == Bytecodes.GETSTATIC || opcode == Bytecodes.PUTSTATIC || opcode == Bytecodes.INVOKEINTERFACE ||
                            opcode == Bytecodes.INVOKEVIRTUAL || opcode == Bytecodes.INVOKESPECIAL || opcode == Bytecodes.INVOKESTATIC : "unexpected invoke opcode " + Bytecodes.nameOf(opcode);
            index = rawIndex + runtime().getConfig().constantPoolCpCacheIndexTag;
        }
        return index;
    }

    /**
     * Gets the constant pool tag at index {@code index}.
     * 
     * @param index constant pool index
     * @return constant pool tag
     */
    private int getTagAt(int index) {
        assertBounds(index);
        HotSpotVMConfig config = runtime().getConfig();
        long tags = unsafe.getAddress(metaspaceConstantPool + config.constantPoolTagsOffset);
        return unsafe.getByteVolatile(null, tags + config.arrayU1DataOffset + index);
    }

    /**
     * Gets the constant pool entry at index {@code index}.
     * 
     * @param index constant pool index
     * @return constant pool entry
     */
    private long getEntryAt(int index) {
        assertBounds(index);
        HotSpotVMConfig config = runtime().getConfig();
        return unsafe.getAddress(metaspaceConstantPool + config.constantPoolSize + index * runtime().getTarget().wordSize);
    }

    /**
     * Gets the integer constant pool entry at index {@code index}.
     * 
     * @param index constant pool index
     * @return integer constant pool entry at index
     */
    private int getIntAt(int index) {
        HotSpotVMConfig config = runtime().getConfig();
        assertTag(index, config.jvmConstantInteger);
        return unsafe.getInt(metaspaceConstantPool + config.constantPoolSize + index * runtime().getTarget().wordSize);
    }

    /**
     * Gets the long constant pool entry at index {@code index}.
     * 
     * @param index constant pool index
     * @return long constant pool entry
     */
    private long getLongAt(int index) {
        HotSpotVMConfig config = runtime().getConfig();
        assertTag(index, config.jvmConstantLong);
        return unsafe.getLong(metaspaceConstantPool + config.constantPoolSize + index * runtime().getTarget().wordSize);
    }

    /**
     * Gets the float constant pool entry at index {@code index}.
     * 
     * @param index constant pool index
     * @return float constant pool entry
     */
    private float getFloatAt(int index) {
        HotSpotVMConfig config = runtime().getConfig();
        assertTag(index, config.jvmConstantFloat);
        return unsafe.getFloat(metaspaceConstantPool + config.constantPoolSize + index * runtime().getTarget().wordSize);
    }

    /**
     * Gets the double constant pool entry at index {@code index}.
     * 
     * @param index constant pool index
     * @return float constant pool entry
     */
    private double getDoubleAt(int index) {
        HotSpotVMConfig config = runtime().getConfig();
        assertTag(index, config.jvmConstantDouble);
        return unsafe.getDouble(metaspaceConstantPool + config.constantPoolSize + index * runtime().getTarget().wordSize);
    }

    /**
     * Gets the {@code JVM_CONSTANT_NameAndType} constant pool entry at index {@code index}.
     * 
     * @param index constant pool index
     * @return {@code JVM_CONSTANT_NameAndType} constant pool entry
     */
    private int getNameAndTypeAt(int index) {
        HotSpotVMConfig config = runtime().getConfig();
        assertTag(index, config.jvmConstantNameAndType);
        return unsafe.getInt(metaspaceConstantPool + config.constantPoolSize + index * runtime().getTarget().wordSize);
    }

    /**
     * Gets the {@code JVM_CONSTANT_NameAndType} reference index constant pool entry at index
     * {@code index}.
     * 
     * @param index constant pool index
     * @return {@code JVM_CONSTANT_NameAndType} reference constant pool entry
     */
    private int getNameAndTypeRefIndexAt(int index) {
        return runtime().getCompilerToVM().lookupNameAndTypeRefIndexInPool(metaspaceConstantPool, index);
    }

    /**
     * Gets the name of a {@code JVM_CONSTANT_NameAndType} constant pool entry at index
     * {@code index}.
     * 
     * @param index constant pool index
     * @return name as {@link String}
     */
    private String getNameRefAt(int index) {
        final long name = runtime().getCompilerToVM().lookupNameRefInPool(metaspaceConstantPool, index);
        HotSpotSymbol symbol = new HotSpotSymbol(name);
        return symbol.asString();
    }

    /**
     * Gets the name reference index of a {@code JVM_CONSTANT_NameAndType} constant pool entry at
     * index {@code index}.
     * 
     * @param index constant pool index
     * @return name reference index
     */
    private int getNameRefIndexAt(int index) {
        final int refIndex = getNameAndTypeAt(index);
        // name ref index is in the low 16-bits.
        return refIndex & 0xFFFF;
    }

    /**
     * Gets the signature of a {@code JVM_CONSTANT_NameAndType} constant pool entry at index
     * {@code index}.
     * 
     * @param index constant pool index
     * @return signature as {@link String}
     */
    private String getSignatureRefAt(int index) {
        final long name = runtime().getCompilerToVM().lookupSignatureRefInPool(metaspaceConstantPool, index);
        HotSpotSymbol symbol = new HotSpotSymbol(name);
        return symbol.asString();
    }

    /**
     * Gets the signature reference index of a {@code JVM_CONSTANT_NameAndType} constant pool entry
     * at index {@code index}.
     * 
     * @param index constant pool index
     * @return signature reference index
     */
    private int getSignatureRefIndexAt(int index) {
        final int refIndex = getNameAndTypeAt(index);
        // signature ref index is in the high 16-bits.
        return refIndex >>> 16;
    }

    /**
     * Gets the klass reference index constant pool entry at index {@code index}.
     * 
     * @param index constant pool index
     * @return klass reference index
     */
    private int getKlassRefIndexAt(int index) {
        return runtime().getCompilerToVM().lookupKlassRefIndexInPool(metaspaceConstantPool, index);
    }

    /**
     * Asserts that the constant pool index {@code index} is in the bounds of the constant pool.
     * 
     * @param index constant pool index
     */
    private void assertBounds(int index) {
        assert 0 <= index && index < length() : "index " + index + " not between 0 and " + length();
    }

    /**
     * Asserts that the constant pool tag at index {@code index} is equal to {@code tag}.
     * 
     * @param index constant pool index
     * @param tag expected tag
     */
    private void assertTag(int index, int tag) {
        assert getTagAt(index) == tag : "constant pool tag at index " + index + " is " + getNameForTag(getTagAt(index)) + " but expected " + getNameForTag(tag);
    }

    private static String getNameForTag(int tag) {
        HotSpotVMConfig config = runtime().getConfig();
        if (tag == config.jvmConstantUtf8) {
            return "JVM_CONSTANT_Utf8";
        }
        if (tag == config.jvmConstantInteger) {
            return "JVM_CONSTANT_Integer";
        }
        if (tag == config.jvmConstantLong) {
            return "JVM_CONSTANT_Long";
        }
        if (tag == config.jvmConstantFloat) {
            return "JVM_CONSTANT_Float";
        }
        if (tag == config.jvmConstantDouble) {
            return "JVM_CONSTANT_Double";
        }
        if (tag == config.jvmConstantClass) {
            return "JVM_CONSTANT_Class";
        }
        if (tag == config.jvmConstantUnresolvedClass) {
            return "JVM_CONSTANT_UnresolvedClass";
        }
        if (tag == config.jvmConstantUnresolvedClassInError) {
            return "JVM_CONSTANT_UnresolvedClassInError";
        }
        if (tag == config.jvmConstantString) {
            return "JVM_CONSTANT_String";
        }
        if (tag == config.jvmConstantFieldref) {
            return "JVM_CONSTANT_Fieldref";
        }
        if (tag == config.jvmConstantMethodref) {
            return "JVM_CONSTANT_Methodref";
        }
        if (tag == config.jvmConstantInterfaceMethodref) {
            return "JVM_CONSTANT_InterfaceMethodref";
        }
        if (tag == config.jvmConstantNameAndType) {
            return "JVM_CONSTANT_NameAndType";
        }
        if (tag == config.jvmConstantMethodHandle) {
            return "JVM_CONSTANT_MethodHandle";
        }
        if (tag == config.jvmConstantMethodHandleInError) {
            return "JVM_CONSTANT_MethodHandleInError";
        }
        if (tag == config.jvmConstantMethodType) {
            return "JVM_CONSTANT_MethodType";
        }
        if (tag == config.jvmConstantMethodTypeInError) {
            return "JVM_CONSTANT_MethodTypeInError";
        }
        return "unknown constant tag " + tag;
    }

    @Override
    public int length() {
        HotSpotVMConfig config = runtime().getConfig();
        return unsafe.getInt(metaspaceConstantPool + config.constantPoolLengthOffset);
    }

    @Override
    public Object lookupConstant(int cpi) {
        assert cpi != 0;

        HotSpotVMConfig config = runtime().getConfig();
        final int tag = getTagAt(cpi);

        // Handle primitive constant pool entries directly.
        if (tag == config.jvmConstantInteger) {
            return Constant.forInt(getIntAt(cpi));
        }
        if (tag == config.jvmConstantLong) {
            return Constant.forLong(getLongAt(cpi));
        }
        if (tag == config.jvmConstantFloat) {
            return Constant.forFloat(getFloatAt(cpi));
        }
        if (tag == config.jvmConstantDouble) {
            return Constant.forDouble(getDoubleAt(cpi));
        }

        // All the other constant pool entries need special attention so we call down into the VM.
        if (tag == config.jvmConstantClass || tag == config.jvmConstantUnresolvedClass || tag == config.jvmConstantUnresolvedClassInError) {
            final int opcode = -1;  // opcode is not used
            return lookupType(cpi, opcode);
        }
        if (tag == config.jvmConstantString) {
            Object string = runtime().getCompilerToVM().lookupConstantInPool(metaspaceConstantPool, cpi);
            return Constant.forObject(string);
        }
        if (tag == config.jvmConstantMethodHandle || tag == config.jvmConstantMethodHandleInError || tag == config.jvmConstantMethodType || tag == config.jvmConstantMethodTypeInError) {
            Object obj = runtime().getCompilerToVM().lookupConstantInPool(metaspaceConstantPool, cpi);
            return Constant.forObject(obj);
        }

        throw GraalInternalError.shouldNotReachHere("unknown constant pool tag " + tag);
    }

    @Override
    public String lookupUtf8(int cpi) {
        assertTag(cpi, runtime().getConfig().jvmConstantUtf8);
        long signature = getEntryAt(cpi);
        HotSpotSymbol symbol = new HotSpotSymbol(signature);
        return symbol.asString();
    }

    @Override
    public Signature lookupSignature(int cpi) {
        return new HotSpotSignature(lookupUtf8(cpi));
    }

    @Override
    public Object lookupAppendix(int cpi, int opcode) {
        assert Bytecodes.isInvoke(opcode);
        final int index = toConstantPoolIndex(cpi, opcode);
        return runtime().getCompilerToVM().lookupAppendixInPool(metaspaceConstantPool, index);
    }

    /**
     * Gets a {@link JavaType} corresponding a given metaspace Klass or a metaspace Symbol depending
     * on the {@link HotSpotVMConfig#compilerToVMKlassTag tag}.
     * 
     * @param metaspacePointer either a metaspace Klass or a metaspace Symbol
     */
    private static JavaType getJavaType(final long metaspacePointer) {
        HotSpotVMConfig config = runtime().getConfig();
        if ((metaspacePointer & config.compilerToVMSymbolTag) != 0) {
            final long metaspaceSymbol = metaspacePointer & ~config.compilerToVMSymbolTag;
            String name = new HotSpotSymbol(metaspaceSymbol).asString();
            return HotSpotUnresolvedJavaType.create(name);
        } else {
            assert (metaspacePointer & config.compilerToVMKlassTag) == 0;
            return HotSpotResolvedObjectType.fromMetaspaceKlass(metaspacePointer);
        }
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode) {
        final int index = toConstantPoolIndex(cpi, opcode);
        final long metaspaceMethod = runtime().getCompilerToVM().lookupMethodInPool(metaspaceConstantPool, index, (byte) opcode);
        if (metaspaceMethod != 0L) {
            return HotSpotResolvedJavaMethod.fromMetaspace(metaspaceMethod);
        } else {
            // Get the method's name and signature.
            String name = getNameRefAt(index);
            String signature = getSignatureRefAt(index);
            if (opcode == Bytecodes.INVOKEDYNAMIC) {
                JavaType holder = HotSpotResolvedJavaType.fromClass(MethodHandle.class);
                return new HotSpotMethodUnresolved(name, signature, holder);
            } else {
                final int klassIndex = getKlassRefIndexAt(index);
                final long metaspacePointer = runtime().getCompilerToVM().lookupKlassInPool(metaspaceConstantPool, klassIndex);
                JavaType holder = getJavaType(metaspacePointer);
                return new HotSpotMethodUnresolved(name, signature, holder);
            }
        }
    }

    @Override
    public JavaType lookupType(int cpi, int opcode) {
        final long metaspacePointer = runtime().getCompilerToVM().lookupKlassInPool(metaspaceConstantPool, cpi);
        return getJavaType(metaspacePointer);
    }

    @Override
    public JavaField lookupField(int cpi, int opcode) {
        final int index = toConstantPoolIndex(cpi, opcode);
        final int nameAndTypeIndex = getNameAndTypeRefIndexAt(index);
        final int nameIndex = getNameRefIndexAt(nameAndTypeIndex);
        String name = lookupUtf8(nameIndex);
        final int typeIndex = getSignatureRefIndexAt(nameAndTypeIndex);
        String typeName = lookupUtf8(typeIndex);
        JavaType type = runtime().lookupType(typeName, getHolder(), false);

        final int holderIndex = getKlassRefIndexAt(index);
        JavaType holder = lookupType(holderIndex, opcode);

        if (holder instanceof HotSpotResolvedObjectType) {
            long[] info = new long[2];
            long metaspaceKlass;
            try {
                metaspaceKlass = runtime().getCompilerToVM().resolveField(metaspaceConstantPool, index, (byte) opcode, info);
            } catch (Throwable t) {
                /*
                 * If there was an exception resolving the field we give up and return an unresolved
                 * field.
                 */
                return new HotSpotUnresolvedField(holder, name, type);
            }
            HotSpotResolvedObjectType resolvedHolder = (HotSpotResolvedObjectType) HotSpotResolvedObjectType.fromMetaspaceKlass(metaspaceKlass);
            final int flags = (int) info[0];
            final long offset = info[1];
            return resolvedHolder.createField(name, type, offset, flags);
        } else {
            return new HotSpotUnresolvedField(holder, name, type);
        }
    }

    @Override
    public void loadReferencedType(int cpi, int opcode) {
        int index;
        switch (opcode) {
            case Bytecodes.CHECKCAST:
            case Bytecodes.INSTANCEOF:
            case Bytecodes.NEW:
            case Bytecodes.ANEWARRAY:
            case Bytecodes.MULTIANEWARRAY:
            case Bytecodes.LDC:
            case Bytecodes.LDC_W:
            case Bytecodes.LDC2_W:
                index = cpi;
                break;
            default:
                index = toConstantPoolIndex(cpi, opcode);
        }
        runtime().getCompilerToVM().loadReferencedTypeInPool(metaspaceConstantPool, index, (byte) opcode);
    }
}
