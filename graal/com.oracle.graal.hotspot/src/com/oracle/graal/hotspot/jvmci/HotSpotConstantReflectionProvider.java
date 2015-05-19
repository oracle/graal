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
package com.oracle.graal.hotspot.jvmci;

import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.stubs.SnippetStub.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.options.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;

/**
 * HotSpot implementation of {@link ConstantReflectionProvider}.
 */
public class HotSpotConstantReflectionProvider implements ConstantReflectionProvider, HotSpotProxified {
    private static final String SystemClassName = "Ljava/lang/System;";

    protected final HotSpotGraalRuntimeProvider runtime;
    protected final HotSpotMethodHandleAccessProvider methodHandleAccess;
    protected final HotSpotMemoryAccessProviderImpl memoryAccess;

    public HotSpotConstantReflectionProvider(HotSpotGraalRuntimeProvider runtime) {
        this.runtime = runtime;
        this.methodHandleAccess = new HotSpotMethodHandleAccessProvider(this);
        this.memoryAccess = new HotSpotMemoryAccessProviderImpl(runtime);
    }

    public MethodHandleAccessProvider getMethodHandleAccess() {
        return methodHandleAccess;
    }

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        return memoryAccess;
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

    public JavaConstant readConstantArrayElement(JavaConstant array, int index) {
        if (array instanceof HotSpotObjectConstantImpl && ((HotSpotObjectConstantImpl) array).getStableDimension() > 0) {
            JavaConstant element = readArrayElement(array, index);
            if (element != null && (((HotSpotObjectConstantImpl) array).isDefaultStable() || !element.isDefaultForKind())) {
                return element;
            }
        }
        return null;
    }

    /**
     * Try to convert {@code offset} into an an index into {@code array}.
     *
     * @return -1 if the offset isn't within the array or the computed index
     */
    private int indexForOffset(JavaConstant array, long offset) {
        if (array.getKind() != Kind.Object || array.isNull()) {
            return -1;
        }
        Class<?> componentType = ((HotSpotObjectConstantImpl) array).object().getClass().getComponentType();
        Kind kind = runtime.getHostProviders().getMetaAccess().lookupJavaType(componentType).getKind();
        int arraybase = runtime.getArrayBaseOffset(kind);
        int scale = runtime.getArrayIndexScale(kind);
        if (offset < arraybase) {
            return -1;
        }
        long index = offset - arraybase;
        if (index % scale != 0) {
            return -1;
        }
        long result = index / scale;
        if (result >= Integer.MAX_VALUE) {
            return -1;
        }
        return (int) result;
    }

    public JavaConstant readConstantArrayElementForOffset(JavaConstant array, long offset) {
        if (array instanceof HotSpotObjectConstantImpl && ((HotSpotObjectConstantImpl) array).getStableDimension() > 0) {
            return readConstantArrayElement(array, indexForOffset(array, offset));
        }
        return null;
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
            Object element = ((Object[]) a)[index];
            if (((HotSpotObjectConstantImpl) array).getStableDimension() > 1) {
                return HotSpotObjectConstantImpl.forStableArray(element, ((HotSpotObjectConstantImpl) array).getStableDimension() - 1, ((HotSpotObjectConstantImpl) array).isDefaultStable());
            } else {
                return HotSpotObjectConstantImpl.forObject(element);
            }
        } else {
            return JavaConstant.forBoxedPrimitive(Array.get(a, index));
        }
    }

    /**
     * Check if the constant is a boxed value that is guaranteed to be cached by the platform.
     * Otherwise the generated code might be the only reference to the boxed value and since object
     * references from nmethods are weak this can cause GC problems.
     *
     * @param source
     * @return true if the box is cached
     */
    private static boolean isBoxCached(JavaConstant source) {
        switch (source.getKind()) {
            case Boolean:
                return true;
            case Char:
                return source.asInt() <= 127;
            case Byte:
            case Short:
            case Int:
                return source.asInt() >= -128 && source.asInt() <= 127;
            case Long:
                return source.asLong() >= -128 && source.asLong() <= 127;
            case Float:
            case Double:
                return false;
            default:
                throw new IllegalArgumentException("unexpected kind " + source.getKind());
        }
    }

    @Override
    public JavaConstant boxPrimitive(JavaConstant source) {
        if (!source.getKind().isPrimitive() || !isBoxCached(source)) {
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
    public ResolvedJavaType asJavaType(Constant constant) {
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
        assert !ImmutableCode.getValue() || isCalledForSnippets() || SnippetGraphUnderConstruction.get() != null || HotSpotLoadFieldPlugin.FieldReadEnabledInImmutableCode.get() == Boolean.TRUE : receiver;
        HotSpotResolvedJavaField hotspotField = (HotSpotResolvedJavaField) field;

        if (hotspotField.isStatic()) {
            if (hotspotField.isFinal() || hotspotField.isStable()) {
                ResolvedJavaType holder = hotspotField.getDeclaringClass();
                if (holder.isInitialized() && !holder.getName().equals(SystemClassName) && isEmbeddable(hotspotField)) {
                    JavaConstant value = readFieldValue(field, receiver);
                    if (hotspotField.isFinal() || !value.isDefaultForKind()) {
                        return value;
                    }
                }
            }
        } else {
            /*
             * for non-static final fields, we must assume that they are only initialized if they
             * have a non-default value.
             */
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
                        if (hotspotField.isInObject(object) && hotspotField.getName().equals("value")) {
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
        if (!hotspotField.isStable()) {
            return readNonStableFieldValue(field, receiver);
        } else {
            return readStableFieldValue(field, receiver, false);
        }
    }

    private JavaConstant readNonStableFieldValue(JavaField field, JavaConstant receiver) {
        HotSpotResolvedJavaField hotspotField = (HotSpotResolvedJavaField) field;
        if (hotspotField.isStatic()) {
            HotSpotResolvedJavaType holder = (HotSpotResolvedJavaType) hotspotField.getDeclaringClass();
            if (holder.isInitialized()) {
                return memoryAccess.readUnsafeConstant(hotspotField.getKind(), HotSpotObjectConstantImpl.forObject(holder.mirror()), hotspotField.offset());
            }
        } else {
            if (receiver.isNonNull() && hotspotField.isInObject(((HotSpotObjectConstantImpl) receiver).object())) {
                return memoryAccess.readUnsafeConstant(hotspotField.getKind(), receiver, hotspotField.offset());
            }
        }
        return null;
    }

    public JavaConstant readStableFieldValue(JavaField field, JavaConstant receiver, boolean isDefaultStable) {
        JavaConstant fieldValue = readNonStableFieldValue(field, receiver);
        if (fieldValue.isNonNull()) {
            JavaType declaredType = field.getType();
            if (declaredType.getComponentType() != null) {
                int stableDimension = getArrayDimension(declaredType);
                return HotSpotObjectConstantImpl.forStableArray(((HotSpotObjectConstantImpl) fieldValue).object(), stableDimension, isDefaultStable);
            }
        }
        return fieldValue;
    }

    private static int getArrayDimension(JavaType type) {
        int dimensions = 0;
        JavaType componentType = type;
        while ((componentType = componentType.getComponentType()) != null) {
            dimensions++;
        }
        return dimensions;
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
            makeGraphMethod = metaAccess.lookupJavaMethod(ReplacementsImpl.class.getDeclaredMethod("makeGraph", rjm, Object[].class, rjm));
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
