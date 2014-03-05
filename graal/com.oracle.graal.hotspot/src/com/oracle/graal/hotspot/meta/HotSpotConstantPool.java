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
     * Returns the constant pool tag at index {@code index}.
     * 
     * @param index constant pool index
     * @return constant pool tag at index
     */
    private int getTagAt(int index) {
        assertBounds(index);
        HotSpotVMConfig config = runtime().getConfig();
        long tags = unsafe.getAddress(metaspaceConstantPool + config.constantPoolTagsOffset);
        return unsafe.getByteVolatile(null, tags + config.arrayU1DataOffset + index);
    }

    /**
     * Returns the constant pool entry at index {@code index}.
     * 
     * @param index constant pool index
     * @return constant pool entry at index
     */
    private long getEntryAt(int index) {
        assertBounds(index);
        HotSpotVMConfig config = runtime().getConfig();
        return unsafe.getAddress(metaspaceConstantPool + config.constantPoolSize + index * runtime().getTarget().wordSize);
    }

    /**
     * Returns the integer constant pool entry at index {@code index}.
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
     * Returns the long constant pool entry at index {@code index}.
     * 
     * @param index constant pool index
     * @return long constant pool entry at index
     */
    private long getLongAt(int index) {
        HotSpotVMConfig config = runtime().getConfig();
        assertTag(index, config.jvmConstantLong);
        return unsafe.getLong(metaspaceConstantPool + config.constantPoolSize + index * runtime().getTarget().wordSize);
    }

    /**
     * Returns the float constant pool entry at index {@code index}.
     * 
     * @param index constant pool index
     * @return float constant pool entry at index
     */
    private float getFloatAt(int index) {
        HotSpotVMConfig config = runtime().getConfig();
        assertTag(index, config.jvmConstantFloat);
        return unsafe.getFloat(metaspaceConstantPool + config.constantPoolSize + index * runtime().getTarget().wordSize);
    }

    /**
     * Returns the double constant pool entry at index {@code index}.
     * 
     * @param index constant pool index
     * @return float constant pool entry at index
     */
    private double getDoubleAt(int index) {
        HotSpotVMConfig config = runtime().getConfig();
        assertTag(index, config.jvmConstantDouble);
        return unsafe.getDouble(metaspaceConstantPool + config.constantPoolSize + index * runtime().getTarget().wordSize);
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
     * Gets a {@link JavaType} corresponding a given metaspace Klass or to a given name if the
     * former is null.
     * 
     * @param metaspaceSymbol a type name
     * @param metaspaceKlass a resolved type (if non-zero)
     * @param mayBePrimitive specifies if the requested type may be primitive
     */
    private static JavaType getType(long metaspaceSymbol, long metaspaceKlass, boolean mayBePrimitive) {
        if (metaspaceKlass == 0L) {
            String name = new HotSpotSymbol(metaspaceSymbol).asString();
            if (mayBePrimitive && name.length() == 1) {
                Kind kind = Kind.fromPrimitiveOrVoidTypeChar(name.charAt(0));
                return HotSpotResolvedPrimitiveType.fromClass(kind.toJavaClass());
            }
            return HotSpotUnresolvedJavaType.create(name);
        } else {
            return HotSpotResolvedObjectType.fromMetaspaceKlass(metaspaceKlass);
        }
    }

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode) {
        final int index = toConstantPoolIndex(cpi, opcode);
        // {name, signature, unresolved_holder_name, resolved_holder}
        long[] unresolvedInfo = new long[4];
        long metaspaceMethod = runtime().getCompilerToVM().lookupMethodInPool(metaspaceConstantPool, index, (byte) opcode, unresolvedInfo);
        if (metaspaceMethod != 0L) {
            return HotSpotResolvedJavaMethod.fromMetaspace(metaspaceMethod);
        } else {
            String name = new HotSpotSymbol(unresolvedInfo[0]).asString();
            String signature = new HotSpotSymbol(unresolvedInfo[1]).asString();
            JavaType holder = getType(unresolvedInfo[2], unresolvedInfo[3], false);
            return new HotSpotMethodUnresolved(name, signature, holder);
        }
    }

    @Override
    public JavaType lookupType(int cpi, int opcode) {
        long[] unresolvedTypeName = {0};
        long metaspaceKlass = runtime().getCompilerToVM().lookupTypeInPool(metaspaceConstantPool, cpi, unresolvedTypeName);
        return getType(unresolvedTypeName[0], metaspaceKlass, false);
    }

    @Override
    public JavaField lookupField(int cpi, int opcode) {
        final int index = toConstantPoolIndex(cpi, opcode);
        long[] info = new long[7];
        boolean resolved = runtime().getCompilerToVM().lookupFieldInPool(metaspaceConstantPool, index, (byte) opcode, info);
        String name = new HotSpotSymbol(info[0]).asString();
        JavaType type = getType(info[1], info[2], true);
        JavaType holder = getType(info[3], info[4], false);
        int flags = (int) info[5];
        int offset = (int) info[6];
        if (resolved) {
            HotSpotResolvedObjectType resolvedHolder = (HotSpotResolvedObjectType) holder;
            HotSpotResolvedJavaField f = resolvedHolder.createField(name, type, offset, flags);
            return f;
        } else {
            return new HotSpotUnresolvedField(holder, name, type);
        }
    }

    @Override
    public void loadReferencedType(int cpi, int opcode) {
        int index;
        if (opcode != Bytecodes.CHECKCAST && opcode != Bytecodes.INSTANCEOF && opcode != Bytecodes.NEW && opcode != Bytecodes.ANEWARRAY && opcode != Bytecodes.MULTIANEWARRAY &&
                        opcode != Bytecodes.LDC && opcode != Bytecodes.LDC_W && opcode != Bytecodes.LDC2_W) {
            index = toConstantPoolIndex(cpi, opcode);
        } else {
            index = cpi;
        }
        runtime().getCompilerToVM().loadReferencedTypeInPool(metaspaceConstantPool, index, (byte) opcode);
    }
}
