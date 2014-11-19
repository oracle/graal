/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.compiler.common.UnsafeAccess.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.options.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.ReplacementsImpl.FrameStateProcessing;
import com.oracle.graal.replacements.Snippet.SnippetInliningPolicy;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;

/**
 * HotSpot implementation of {@link ConstantReflectionProvider}.
 */
public class HotSpotConstantReflectionProvider implements ConstantReflectionProvider {
    private static final String SystemClassName = "Ljava/lang/System;";

    protected final HotSpotGraalRuntime runtime;
    protected final HotSpotMethodHandleAccessProvider methodHandleAccess;

    public HotSpotConstantReflectionProvider(HotSpotGraalRuntime runtime) {
        this.runtime = runtime;
        this.methodHandleAccess = new HotSpotMethodHandleAccessProvider(this);
    }

    public MethodHandleAccessProvider getMethodHandleAccess() {
        return methodHandleAccess;
    }

    @Override
    public Boolean constantEquals(Constant x, Constant y) {
        if (x == y) {
            return true;
        } else if (x instanceof HotSpotObjectConstantImpl) {
            return y instanceof HotSpotObjectConstantImpl && ((HotSpotObjectConstantImpl) x).object() == ((HotSpotObjectConstantImpl) y).object();
        } else {
            return x.equals(y);
        }
    }

    @Override
    public Integer readArrayLength(JavaConstant array) {
        if (array.getKind() != Kind.Object || array.isNull()) {
            return null;
        }

        Object arrayObject = ((HotSpotObjectConstantImpl) array).object();
        if (!arrayObject.getClass().isArray()) {
            return null;
        }
        return Array.getLength(arrayObject);
    }

    private static long readRawValue(Constant baseConstant, long initialDisplacement, int bits) {
        Object base;
        long displacement;
        if (baseConstant instanceof JavaConstant) {
            JavaConstant javaConstant = (JavaConstant) baseConstant;
            if (javaConstant instanceof HotSpotObjectConstantImpl) {
                base = ((HotSpotObjectConstantImpl) javaConstant).object();
                displacement = initialDisplacement;
            } else if (javaConstant.getKind().isNumericInteger()) {
                long baseLong = javaConstant.asLong();
                assert baseLong != 0;
                displacement = initialDisplacement + baseLong;
                base = null;
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }

        long rawValue;
        switch (bits) {
            case 8:
                rawValue = base == null ? unsafe.getByte(displacement) : unsafe.getByte(base, displacement);
                break;
            case 16:
                rawValue = base == null ? unsafe.getShort(displacement) : unsafe.getShort(base, displacement);
                break;
            case 32:
                rawValue = base == null ? unsafe.getInt(displacement) : unsafe.getInt(base, displacement);
                break;
            case 64:
                rawValue = base == null ? unsafe.getLong(displacement) : unsafe.getLong(base, displacement);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return rawValue;
    }

    private Object readRawObject(Constant baseConstant, long displacement, boolean compressed) {
        if (baseConstant instanceof HotSpotObjectConstantImpl) {
            assert compressed == runtime.getConfig().useCompressedOops;
            return unsafe.getObject(((HotSpotObjectConstantImpl) baseConstant).object(), displacement);
        } else if (baseConstant instanceof HotSpotMetaspaceConstant) {
            Object metaspaceObject = HotSpotMetaspaceConstantImpl.getMetaspaceObject(baseConstant);
            if (metaspaceObject instanceof HotSpotResolvedObjectTypeImpl) {
                assert !compressed : "unexpected compressed read from Klass*";
                if (displacement == runtime.getConfig().classMirrorOffset) {
                    return ((HotSpotResolvedObjectTypeImpl) metaspaceObject).mirror();
                } else if (displacement == runtime.getConfig().arrayKlassComponentMirrorOffset) {
                    return ((HotSpotResolvedObjectTypeImpl) metaspaceObject).mirror().getComponentType();
                } else if (displacement == runtime.getConfig().instanceKlassNodeClassOffset) {
                    return NodeClass.get(((HotSpotResolvedObjectTypeImpl) metaspaceObject).mirror());
                }
            }
            throw GraalInternalError.shouldNotReachHere("read from unknown Klass* offset " + displacement);
        } else {
            throw GraalInternalError.shouldNotReachHere("unexpected base pointer: " + (baseConstant == null ? "null" : baseConstant.toString()));
        }
    }

    @Override
    public JavaConstant readUnsafeConstant(Kind kind, JavaConstant baseConstant, long displacement) {
        if (kind == Kind.Object) {
            Object o = readRawObject(baseConstant, displacement, runtime.getConfig().useCompressedOops);
            return HotSpotObjectConstantImpl.forObject(o);
        } else {
            return readRawConstant(kind, baseConstant, displacement, kind.getByteCount() * 8);
        }
    }

    @Override
    public JavaConstant readRawConstant(Kind kind, Constant baseConstant, long initialDisplacement, int bits) {
        try {
            long rawValue = readRawValue(baseConstant, initialDisplacement, bits);
            switch (kind) {
                case Boolean:
                    return JavaConstant.forBoolean(rawValue != 0);
                case Byte:
                    return JavaConstant.forByte((byte) rawValue);
                case Char:
                    return JavaConstant.forChar((char) rawValue);
                case Short:
                    return JavaConstant.forShort((short) rawValue);
                case Int:
                    return JavaConstant.forInt((int) rawValue);
                case Long:
                    return JavaConstant.forLong(rawValue);
                case Float:
                    return JavaConstant.forFloat(Float.intBitsToFloat((int) rawValue));
                case Double:
                    return JavaConstant.forDouble(Double.longBitsToDouble(rawValue));
                default:
                    throw GraalInternalError.shouldNotReachHere("unsupported kind: " + kind);
            }
        } catch (NullPointerException e) {
            return null;
        }
    }

    public Constant readPointerConstant(PointerType type, Constant base, long displacement) {
        switch (type) {
            case Object:
                return HotSpotObjectConstantImpl.forObject(readRawObject(base, displacement, false));
            case Type:
                long klass = readRawValue(base, displacement, runtime.getTarget().wordSize * 8);
                HotSpotResolvedObjectType metaKlass = HotSpotResolvedObjectTypeImpl.fromMetaspaceKlass(klass);
                return HotSpotMetaspaceConstantImpl.forMetaspaceObject(runtime.getTarget().wordKind, klass, metaKlass, false);
            case Method:
                long method = readRawValue(base, displacement, runtime.getTarget().wordSize * 8);
                HotSpotResolvedJavaMethod metaMethod = HotSpotResolvedJavaMethodImpl.fromMetaspace(method);
                return HotSpotMetaspaceConstantImpl.forMetaspaceObject(runtime.getTarget().wordKind, method, metaMethod, false);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    public Constant readNarrowPointerConstant(PointerType type, Constant base, long displacement) {
        switch (type) {
            case Object:
                return HotSpotObjectConstantImpl.forObject(readRawObject(base, displacement, true), true);
            case Type:
                int compressed = (int) readRawValue(base, displacement, 32);
                long klass = runtime.getConfig().getKlassEncoding().uncompress(compressed);
                HotSpotResolvedObjectType metaKlass = HotSpotResolvedObjectTypeImpl.fromMetaspaceKlass(klass);
                return HotSpotMetaspaceConstantImpl.forMetaspaceObject(Kind.Int, compressed, metaKlass, true);
            case Method:
                // there are no compressed method pointers
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        if (array.getKind() != Kind.Object || array.isNull()) {
            return null;
        }
        Object a = ((HotSpotObjectConstantImpl) array).object();

        if (index < 0 || index >= Array.getLength(a)) {
            return null;
        }

        if (a instanceof Object[]) {
            return HotSpotObjectConstantImpl.forObject(((Object[]) a)[index]);
        } else {
            return JavaConstant.forBoxedPrimitive(Array.get(a, index));
        }
    }

    @Override
    public JavaConstant boxPrimitive(JavaConstant source) {
        if (!source.getKind().isPrimitive()) {
            return null;
        }
        return HotSpotObjectConstantImpl.forObject(source.asBoxedPrimitive());
    }

    @Override
    public JavaConstant unboxPrimitive(JavaConstant source) {
        if (!source.getKind().isObject()) {
            return null;
        }
        if (source.isNull()) {
            return null;
        }
        return JavaConstant.forBoxedPrimitive(((HotSpotObjectConstantImpl) source).object());
    }

    public JavaConstant forString(String value) {
        return HotSpotObjectConstantImpl.forObject(value);
    }

    @Override
    public ResolvedJavaType asJavaType(JavaConstant constant) {
        if (constant instanceof HotSpotObjectConstant) {
            Object obj = ((HotSpotObjectConstantImpl) constant).object();
            if (obj instanceof Class) {
                return runtime.getHostProviders().getMetaAccess().lookupJavaType((Class<?>) obj);
            }
        }
        if (constant instanceof HotSpotMetaspaceConstant) {
            Object obj = HotSpotMetaspaceConstantImpl.getMetaspaceObject(constant);
            if (obj instanceof HotSpotResolvedObjectTypeImpl) {
                return (ResolvedJavaType) obj;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * The {@code value} field in {@link OptionValue} is considered constant if the type of
     * {@code receiver} is (assignable to) {@link StableOptionValue}.
     */
    public JavaConstant readConstantFieldValue(JavaField field, JavaConstant receiver) {
        assert !ImmutableCode.getValue() || isCalledForSnippets() : receiver;
        HotSpotResolvedJavaField hotspotField = (HotSpotResolvedJavaField) field;

        if (receiver == null) {
            assert hotspotField.isStatic();
            if (hotspotField.isFinal()) {
                ResolvedJavaType holder = hotspotField.getDeclaringClass();
                if (holder.isInitialized() && !holder.getName().equals(SystemClassName) && isEmbeddable(hotspotField)) {
                    return readFieldValue(field, receiver);
                }
            }
        } else {
            /*
             * for non-static final fields, we must assume that they are only initialized if they
             * have a non-default value.
             */
            assert !hotspotField.isStatic();
            Object object = receiver.isNull() ? null : ((HotSpotObjectConstantImpl) receiver).object();

            // Canonicalization may attempt to process an unsafe read before
            // processing a guard (e.g. a null check or a type check) for this read
            // so we need to check the object being read
            if (object != null) {
                if (hotspotField.isFinal()) {
                    if (hotspotField.isInObject(object)) {
                        JavaConstant value = readFieldValue(field, receiver);
                        if (!value.isDefaultForKind() || assumeNonStaticFinalDefaultFieldsAsFinal(object.getClass())) {
                            return value;
                        }
                    }
                } else if (hotspotField.isStable()) {
                    if (hotspotField.isInObject(object)) {
                        JavaConstant value = readFieldValue(field, receiver);
                        if (assumeDefaultStableFieldsAsFinal(object.getClass()) || !value.isDefaultForKind()) {
                            return value;
                        }
                    }
                } else {
                    Class<?> clazz = object.getClass();
                    if (StableOptionValue.class.isAssignableFrom(clazz)) {
                        if (hotspotField.isInObject(object)) {
                            assert hotspotField.getName().equals("value") : "Unexpected field in " + StableOptionValue.class.getName() + " hierarchy:" + this;
                            StableOptionValue<?> option = (StableOptionValue<?>) object;
                            return HotSpotObjectConstantImpl.forObject(option.getValue());
                        }
                    }
                }
            }
        }
        return null;
    }

    public JavaConstant readFieldValue(JavaField field, JavaConstant receiver) {
        HotSpotResolvedJavaField hotspotField = (HotSpotResolvedJavaField) field;

        if (receiver == null) {
            assert hotspotField.isStatic();
            HotSpotResolvedJavaType holder = (HotSpotResolvedJavaType) hotspotField.getDeclaringClass();
            if (holder.isInitialized()) {
                return readUnsafeConstant(hotspotField.getKind(), HotSpotObjectConstantImpl.forObject(holder.mirror()), hotspotField.offset());
            }
            return null;
        } else {
            assert !hotspotField.isStatic();
            assert receiver.isNonNull() && hotspotField.isInObject(((HotSpotObjectConstantImpl) receiver).object());
            return readUnsafeConstant(hotspotField.getKind(), receiver, hotspotField.offset());
        }
    }

    /**
     * Compares two {@link StackTraceElement}s for equality, ignoring differences in
     * {@linkplain StackTraceElement#getLineNumber() line number}.
     */
    private static boolean equalsIgnoringLine(StackTraceElement left, StackTraceElement right) {
        return left.getClassName().equals(right.getClassName()) && left.getMethodName().equals(right.getMethodName()) && left.getFileName().equals(right.getFileName());
    }

    /**
     * If the compiler is configured for AOT mode,
     * {@link #readConstantFieldValue(JavaField, JavaConstant)} should be only called for snippets
     * or replacements.
     */
    private static boolean isCalledForSnippets() {
        MetaAccessProvider metaAccess = runtime().getHostProviders().getMetaAccess();
        ResolvedJavaMethod makeGraphMethod = null;
        ResolvedJavaMethod initMethod = null;
        try {
            Class<?> rjm = ResolvedJavaMethod.class;
            makeGraphMethod = metaAccess.lookupJavaMethod(ReplacementsImpl.class.getDeclaredMethod("makeGraph", rjm, rjm, SnippetInliningPolicy.class, FrameStateProcessing.class));
            initMethod = metaAccess.lookupJavaMethod(SnippetTemplate.AbstractTemplates.class.getDeclaredMethod("template", Arguments.class));
        } catch (NoSuchMethodException | SecurityException e) {
            throw new GraalInternalError(e);
        }
        StackTraceElement makeGraphSTE = makeGraphMethod.asStackTraceElement(0);
        StackTraceElement initSTE = initMethod.asStackTraceElement(0);

        StackTraceElement[] stackTrace = new Exception().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            // Ignoring line numbers should not weaken this check too much while at
            // the same time making it more robust against source code changes
            if (equalsIgnoringLine(makeGraphSTE, element) || equalsIgnoringLine(initSTE, element)) {
                return true;
            }
        }
        return false;
    }

    private static boolean assumeNonStaticFinalDefaultFieldsAsFinal(Class<?> clazz) {
        if (TrustFinalDefaultFields.getValue()) {
            return true;
        }
        return clazz == SnippetCounter.class || clazz == NodeClass.class;
    }

    /**
     * Usually {@link Stable} fields are not considered constant if the value is the
     * {@link JavaConstant#isDefaultForKind default value}. For some special classes we want to
     * override this behavior.
     */
    private static boolean assumeDefaultStableFieldsAsFinal(Class<?> clazz) {
        // HotSpotVMConfig has a lot of zero-value fields which we know are stable and want to be
        // considered as constants.
        if (clazz == HotSpotVMConfig.class) {
            return true;
        }
        return false;
    }

    /**
     * in AOT mode, some fields should never be embedded even for snippets/replacements.
     */
    private static boolean isEmbeddable(HotSpotResolvedJavaField field) {
        return Embeddable.test(field);
    }

    /**
     * Separate out the static initialization to eliminate cycles between clinit and other locks
     * that could lead to deadlock. Static code that doesn't call back into type or field machinery
     * is probably ok but anything else should be made lazy.
     */
    static class Embeddable {

        /**
         * @return Return true if it's ok to embed the value of {@code field}.
         */
        public static boolean test(HotSpotResolvedJavaField field) {
            return !ImmutableCode.getValue() || !fields.contains(field);
        }

        private static final List<ResolvedJavaField> fields = new ArrayList<>();
        static {
            try {
                MetaAccessProvider metaAccess = runtime().getHostProviders().getMetaAccess();
                fields.add(metaAccess.lookupJavaField(Boolean.class.getDeclaredField("TRUE")));
                fields.add(metaAccess.lookupJavaField(Boolean.class.getDeclaredField("FALSE")));

                Class<?> characterCacheClass = Character.class.getDeclaredClasses()[0];
                assert "java.lang.Character$CharacterCache".equals(characterCacheClass.getName());
                fields.add(metaAccess.lookupJavaField(characterCacheClass.getDeclaredField("cache")));

                Class<?> byteCacheClass = Byte.class.getDeclaredClasses()[0];
                assert "java.lang.Byte$ByteCache".equals(byteCacheClass.getName());
                fields.add(metaAccess.lookupJavaField(byteCacheClass.getDeclaredField("cache")));

                Class<?> shortCacheClass = Short.class.getDeclaredClasses()[0];
                assert "java.lang.Short$ShortCache".equals(shortCacheClass.getName());
                fields.add(metaAccess.lookupJavaField(shortCacheClass.getDeclaredField("cache")));

                Class<?> integerCacheClass = Integer.class.getDeclaredClasses()[0];
                assert "java.lang.Integer$IntegerCache".equals(integerCacheClass.getName());
                fields.add(metaAccess.lookupJavaField(integerCacheClass.getDeclaredField("cache")));

                Class<?> longCacheClass = Long.class.getDeclaredClasses()[0];
                assert "java.lang.Long$LongCache".equals(longCacheClass.getName());
                fields.add(metaAccess.lookupJavaField(longCacheClass.getDeclaredField("cache")));

                fields.add(metaAccess.lookupJavaField(Throwable.class.getDeclaredField("UNASSIGNED_STACK")));
                fields.add(metaAccess.lookupJavaField(Throwable.class.getDeclaredField("SUPPRESSED_SENTINEL")));
            } catch (SecurityException | NoSuchFieldException e) {
                throw new GraalInternalError(e);
            }
        }
    }
}
