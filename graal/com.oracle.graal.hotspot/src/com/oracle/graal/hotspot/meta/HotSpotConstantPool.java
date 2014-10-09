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

import static com.oracle.graal.compiler.common.UnsafeAccess.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.lang.invoke.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.*;

/**
 * Implementation of {@link ConstantPool} for HotSpot.
 */
public class HotSpotConstantPool extends CompilerObject implements ConstantPool {

    private static final long serialVersionUID = -5443206401485234850L;

    /**
     * Enum of all {@code JVM_CONSTANT} constants used in the VM. This includes the public and
     * internal ones.
     */
    private enum JVM_CONSTANT {
        // @formatter:off
        Utf8(config().jvmConstantUtf8),
        Integer(config().jvmConstantInteger),
        Long(config().jvmConstantLong),
        Float(config().jvmConstantFloat),
        Double(config().jvmConstantDouble),
        Class(config().jvmConstantClass),
        UnresolvedClass(config().jvmConstantUnresolvedClass),
        UnresolvedClassInError(config().jvmConstantUnresolvedClassInError),
        String(config().jvmConstantString),
        Fieldref(config().jvmConstantFieldref),
        MethodRef(config().jvmConstantMethodref),
        InterfaceMethodref(config().jvmConstantInterfaceMethodref),
        NameAndType(config().jvmConstantNameAndType),
        MethodHandle(config().jvmConstantMethodHandle),
        MethodHandleInError(config().jvmConstantMethodHandleInError),
        MethodType(config().jvmConstantMethodType),
        MethodTypeInError(config().jvmConstantMethodTypeInError),
        InvokeDynamic(config().jvmConstantInvokeDynamic);
        // @formatter:on

        private final int tag;

        private static final int ExternalMax = config().jvmConstantExternalMax;
        private static final int InternalMin = config().jvmConstantInternalMin;
        private static final int InternalMax = config().jvmConstantInternalMax;

        private JVM_CONSTANT(int tag) {
            this.tag = tag;
        }

        private static HotSpotVMConfig config() {
            return runtime().getConfig();
        }

        /**
         * Maps JVM_CONSTANT tags to {@link JVM_CONSTANT} values. Using a separate class for lazy
         * initialization.
         */
        static class TagValueMap {
            private static final JVM_CONSTANT[] table = new JVM_CONSTANT[ExternalMax + 1 + (InternalMax - InternalMin) + 1];
            static {
                assert InternalMin > ExternalMax;
                for (JVM_CONSTANT e : values()) {
                    table[indexOf(e.tag)] = e;
                }
            }

            private static int indexOf(int tag) {
                if (tag >= InternalMin) {
                    return tag - InternalMin + ExternalMax + 1;
                } else {
                    assert tag <= ExternalMax;
                }
                return tag;
            }

            static JVM_CONSTANT get(int tag) {
                JVM_CONSTANT res = table[indexOf(tag)];
                if (res != null) {
                    return res;
                }
                throw GraalInternalError.shouldNotReachHere("unknown JVM_CONSTANT tag " + tag);
            }
        }

        public static JVM_CONSTANT getEnum(int tag) {
            return TagValueMap.get(tag);
        }
    }

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
     * Decode a constant pool cache index to a constant pool index.
     *
     * See {@code ConstantPool::decode_cpcache_index}.
     *
     * @param index constant pool cache index
     * @return decoded index
     */
    private static int decodeConstantPoolCacheIndex(int index) {
        if (isInvokedynamicIndex(index)) {
            return decodeInvokedynamicIndex(index);
        } else {
            return index - runtime().getConfig().constantPoolCpCacheIndexTag;
        }
    }

    /**
     * See {@code ConstantPool::is_invokedynamic_index}.
     */
    private static boolean isInvokedynamicIndex(int index) {
        return index < 0;
    }

    /**
     * See {@code ConstantPool::decode_invokedynamic_index}.
     */
    private static int decodeInvokedynamicIndex(int i) {
        assert isInvokedynamicIndex(i) : i;
        return ~i;
    }

    /**
     * Gets the constant pool tag at index {@code index}.
     *
     * @param index constant pool index
     * @return constant pool tag
     */
    private JVM_CONSTANT getTagAt(int index) {
        assertBounds(index);
        HotSpotVMConfig config = runtime().getConfig();
        final long metaspaceConstantPoolTags = unsafe.getAddress(metaspaceConstantPool + config.constantPoolTagsOffset);
        final int tag = unsafe.getByteVolatile(null, metaspaceConstantPoolTags + config.arrayU1DataOffset + index);
        return JVM_CONSTANT.getEnum(tag);
    }

    /**
     * Gets the constant pool entry at index {@code index}.
     *
     * @param index constant pool index
     * @return constant pool entry
     */
    private long getEntryAt(int index) {
        assertBounds(index);
        return unsafe.getAddress(metaspaceConstantPool + runtime().getConfig().constantPoolSize + index * runtime().getTarget().wordSize);
    }

    /**
     * Gets the integer constant pool entry at index {@code index}.
     *
     * @param index constant pool index
     * @return integer constant pool entry at index
     */
    private int getIntAt(int index) {
        assertTag(index, JVM_CONSTANT.Integer);
        return unsafe.getInt(metaspaceConstantPool + runtime().getConfig().constantPoolSize + index * runtime().getTarget().wordSize);
    }

    /**
     * Gets the long constant pool entry at index {@code index}.
     *
     * @param index constant pool index
     * @return long constant pool entry
     */
    private long getLongAt(int index) {
        assertTag(index, JVM_CONSTANT.Long);
        return unsafe.getLong(metaspaceConstantPool + runtime().getConfig().constantPoolSize + index * runtime().getTarget().wordSize);
    }

    /**
     * Gets the float constant pool entry at index {@code index}.
     *
     * @param index constant pool index
     * @return float constant pool entry
     */
    private float getFloatAt(int index) {
        assertTag(index, JVM_CONSTANT.Float);
        return unsafe.getFloat(metaspaceConstantPool + runtime().getConfig().constantPoolSize + index * runtime().getTarget().wordSize);
    }

    /**
     * Gets the double constant pool entry at index {@code index}.
     *
     * @param index constant pool index
     * @return float constant pool entry
     */
    private double getDoubleAt(int index) {
        assertTag(index, JVM_CONSTANT.Double);
        return unsafe.getDouble(metaspaceConstantPool + runtime().getConfig().constantPoolSize + index * runtime().getTarget().wordSize);
    }

    /**
     * Gets the {@code JVM_CONSTANT_NameAndType} constant pool entry at index {@code index}.
     *
     * @param index constant pool index
     * @return {@code JVM_CONSTANT_NameAndType} constant pool entry
     */
    private int getNameAndTypeAt(int index) {
        assertTag(index, JVM_CONSTANT.NameAndType);
        return unsafe.getInt(metaspaceConstantPool + runtime().getConfig().constantPoolSize + index * runtime().getTarget().wordSize);
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
        return runtime().getCompilerToVM().lookupNameRefInPool(metaspaceConstantPool, index);
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
        return runtime().getCompilerToVM().lookupSignatureRefInPool(metaspaceConstantPool, index);
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
     * Gets the uncached klass reference index constant pool entry at index {@code index}. See:
     * {@code ConstantPool::uncached_klass_ref_index_at}.
     *
     * @param index constant pool index
     * @return klass reference index
     */
    private int getUncachedKlassRefIndexAt(int index) {
        assert getTagAt(index) == JVM_CONSTANT.Fieldref || getTagAt(index) == JVM_CONSTANT.MethodRef || getTagAt(index) == JVM_CONSTANT.InterfaceMethodref;
        final int refIndex = unsafe.getInt(metaspaceConstantPool + runtime().getConfig().constantPoolSize + index * runtime().getTarget().wordSize);
        // klass ref index is in the low 16-bits.
        return refIndex & 0xFFFF;
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
    private void assertTag(int index, JVM_CONSTANT tag) {
        assert getTagAt(index) == tag : "constant pool tag at index " + index + " is " + getTagAt(index) + " but expected " + tag;
    }

    @Override
    public int length() {
        return unsafe.getInt(metaspaceConstantPool + runtime().getConfig().constantPoolLengthOffset);
    }

    @Override
    public Object lookupConstant(int cpi) {
        assert cpi != 0;
        final JVM_CONSTANT tag = getTagAt(cpi);
        switch (tag) {
            case Integer:
                return Constant.forInt(getIntAt(cpi));
            case Long:
                return Constant.forLong(getLongAt(cpi));
            case Float:
                return Constant.forFloat(getFloatAt(cpi));
            case Double:
                return Constant.forDouble(getDoubleAt(cpi));
            case Class:
            case UnresolvedClass:
            case UnresolvedClassInError:
                final int opcode = -1;  // opcode is not used
                return lookupType(cpi, opcode);
            case String:
                Object string = runtime().getCompilerToVM().resolvePossiblyCachedConstantInPool(metaspaceConstantPool, cpi);
                return HotSpotObjectConstant.forObject(string);
            case MethodHandle:
            case MethodHandleInError:
            case MethodType:
            case MethodTypeInError:
                Object obj = runtime().getCompilerToVM().resolveConstantInPool(metaspaceConstantPool, cpi);
                return HotSpotObjectConstant.forObject(obj);
            default:
                throw GraalInternalError.shouldNotReachHere("unknown constant pool tag " + tag);
        }
    }

    @Override
    public String lookupUtf8(int cpi) {
        assertTag(cpi, JVM_CONSTANT.Utf8);
        return runtime().getCompilerToVM().getSymbol(getEntryAt(cpi));
    }

    @Override
    public Signature lookupSignature(int cpi) {
        return new HotSpotSignature(lookupUtf8(cpi));
    }

    @Override
    public Constant lookupAppendix(int cpi, int opcode) {
        assert Bytecodes.isInvoke(opcode);
        final int index = toConstantPoolIndex(cpi, opcode);
        Object result = runtime().getCompilerToVM().lookupAppendixInPool(metaspaceConstantPool, index);
        if (result == null) {
            return null;
        } else {
            return HotSpotObjectConstant.forObject(result);
        }
    }

    /**
     * Gets a {@link JavaType} corresponding a given metaspace Klass or a metaspace Symbol depending
     * on the {@link HotSpotVMConfig#compilerToVMKlassTag tag}.
     *
     * @param metaspacePointer either a metaspace Klass or a metaspace Symbol
     */
    private static JavaType getJavaType(final long metaspacePointer) {
        HotSpotGraalRuntime runtime = runtime();
        HotSpotVMConfig config = runtime.getConfig();
        if ((metaspacePointer & config.compilerToVMSymbolTag) != 0) {
            final long metaspaceSymbol = metaspacePointer & ~config.compilerToVMSymbolTag;
            String name = runtime.getCompilerToVM().getSymbol(metaspaceSymbol);
            return HotSpotUnresolvedJavaType.create("L" + name + ";");
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
            case Bytecodes.INVOKEDYNAMIC:
                // invokedynamic instructions point to a constant pool cache entry.
                index = decodeConstantPoolCacheIndex(cpi) + runtime().getConfig().constantPoolCpCacheIndexTag;
                index = runtime().getCompilerToVM().constantPoolRemapInstructionOperandFromCache(metaspaceConstantPool, index);
                break;
            default:
                index = toConstantPoolIndex(cpi, opcode);
                index = runtime().getCompilerToVM().constantPoolRemapInstructionOperandFromCache(metaspaceConstantPool, index);
        }

        JVM_CONSTANT tag = getTagAt(index);
        switch (tag) {
            case Fieldref:
            case MethodRef:
            case InterfaceMethodref:
                index = getUncachedKlassRefIndexAt(index);
                tag = getTagAt(index);
                assert tag == JVM_CONSTANT.Class || tag == JVM_CONSTANT.UnresolvedClass || tag == JVM_CONSTANT.UnresolvedClassInError : tag;
                // fall-through
            case Class:
            case UnresolvedClass:
            case UnresolvedClassInError:
                final long metaspaceKlass = runtime().getCompilerToVM().constantPoolKlassAt(metaspaceConstantPool, index);
                HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) HotSpotResolvedObjectType.fromMetaspaceKlass(metaspaceKlass);
                Class<?> klass = type.mirror();
                if (!klass.isPrimitive() && !klass.isArray()) {
                    unsafe.ensureClassInitialized(klass);
                }
                break;
            case InvokeDynamic:
                if (!isInvokedynamicIndex(cpi)) {
                    throw new IllegalArgumentException("InvokeDynamic entries must be accessed");
                }
                runtime().getCompilerToVM().resolveInvokeDynamic(metaspaceConstantPool, cpi);
                break;
            default:
                // nothing
                break;
        }
    }
}
