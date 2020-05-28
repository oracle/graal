/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.substitute;

import static com.oracle.svm.core.util.VMError.guarantee;
import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;

import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.CustomFieldValueComputer;
import com.oracle.svm.core.annotate.RecomputeFieldValue.CustomFieldValueTransformer;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.meta.ReadableJavaField;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;

import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.misc.Unsafe;

/**
 * Wraps a field whose value is recomputed when added to an image.
 *
 * @see RecomputeFieldValue
 * @see NativeImageReinitialize
 */
public class ComputedValueField implements ReadableJavaField, OriginalFieldProvider, ComputedValue {

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();
    private final ResolvedJavaField original;
    private final ResolvedJavaField annotated;

    private final RecomputeFieldValue.Kind kind;
    private final Class<?> targetClass;
    private final Field targetField;
    private final boolean isFinal;

    private JavaConstant constantValue;

    private final EconomicMap<JavaConstant, JavaConstant> valueCache;
    /**
     * Economic map does not allow to store null keys. Therefore null key is stored in an extra
     * field.
     */
    private JavaConstant valueCacheNullKey;
    private final ReentrantReadWriteLock valueCacheLock = new ReentrantReadWriteLock();

    private HostedMetaAccess hMetaAccess;

    public ComputedValueField(ResolvedJavaField original, ResolvedJavaField annotated, RecomputeFieldValue.Kind kind, Class<?> targetClass, String targetName, boolean isFinal) {
        this.original = original;
        this.annotated = annotated;
        this.kind = kind;
        this.targetClass = targetClass;
        this.isFinal = isFinal;

        Field f = null;
        switch (kind) {
            case Reset:
                constantValue = JavaConstant.defaultForKind(getJavaKind());
                break;
            case FromAlias:
                assert Modifier.isStatic(annotated.getModifiers()) : "Cannot use " + kind + " on non-static alias " + annotated.format("%H.%n");
                annotated.getDeclaringClass().initialize();
                constantValue = ReadableJavaField.readFieldValue(GraalAccess.getOriginalProviders().getConstantReflection(), annotated, null);
                break;
            case FieldOffset:
                try {
                    f = targetClass.getDeclaredField(targetName);
                } catch (NoSuchFieldException e) {
                    throw shouldNotReachHere("could not find target field " + targetClass.getName() + "." + targetName + " for alias " + annotated.format("%H.%n"));
                }
                break;
        }
        guarantee(!isFinal || isFinalValid(kind));
        this.targetField = f;
        this.valueCache = EconomicMap.create();
    }

    public static boolean isFinalValid(RecomputeFieldValue.Kind kind) {
        EnumSet<RecomputeFieldValue.Kind> finalIllegal = EnumSet.of(RecomputeFieldValue.Kind.FieldOffset,
                        RecomputeFieldValue.Kind.TranslateFieldOffset, RecomputeFieldValue.Kind.AtomicFieldUpdaterOffset);
        return !finalIllegal.contains(kind);
    }

    public ResolvedJavaField getAnnotated() {
        return annotated;
    }

    @Override
    public Field getTargetField() {
        return targetField;
    }

    @Override
    public RecomputeFieldValue.Kind getRecomputeValueKind() {
        return kind;
    }

    @Override
    public String getName() {
        return original.getName();
    }

    @Override
    public JavaType getType() {
        return original.getType();
    }

    @Override
    public int getModifiers() {
        int result = original.getModifiers();
        if (isFinal) {
            result = result | Modifier.FINAL;
        } else {
            result = result & ~Modifier.FINAL;
        }
        return result;
    }

    @Override
    public int getOffset() {
        return original.getOffset();
    }

    @Override
    public boolean isInternal() {
        return original.isInternal();
    }

    @Override
    public boolean isSynthetic() {
        return original.isSynthetic();
    }

    public void processAnalysis(AnalysisMetaAccess aMetaAccess) {
        switch (kind) {
            case FieldOffset:
                AnalysisField target = aMetaAccess.lookupJavaField(targetField);
                target.registerAsAccessed();
                break;
        }
    }

    private JavaConstant asConstant(int value) {
        switch (getJavaKind()) {
            case Int:
                return JavaConstant.forInt(value);
            case Long:
                return JavaConstant.forLong(value);
            default:
                throw shouldNotReachHere();
        }
    }

    public void processSubstrate(HostedMetaAccess metaAccess) {
        hMetaAccess = metaAccess;
        switch (kind) {
            case FieldOffset:
                constantValue = asConstant(hMetaAccess.lookupJavaField(targetField).getLocation());
                break;
        }
    }

    @Override
    public JavaConstant readValue(JavaConstant receiver) {
        if (constantValue != null) {
            return constantValue;
        }
        if (kind == RecomputeFieldValue.Kind.None || kind == RecomputeFieldValue.Kind.Manual) {
            return ReadableJavaField.readFieldValue(GraalAccess.getOriginalProviders().getConstantReflection(), original, receiver);
        }

        JavaConstant result = getCached(receiver);
        if (result != null) {
            return result;
        }

        SnippetReflectionProvider originalSnippetReflection = GraalAccess.getOriginalSnippetReflection();
        switch (kind) {
            case ArrayBaseOffset:
                constantValue = asConstant(ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.fromJavaClass(targetClass.getComponentType())));
                return constantValue;
            case ArrayIndexScale:
                constantValue = asConstant(ConfigurationValues.getObjectLayout().getArrayIndexScale(JavaKind.fromJavaClass(targetClass.getComponentType())));
                return constantValue;
            case ArrayIndexShift:
                constantValue = asConstant(ConfigurationValues.getObjectLayout().getArrayIndexShift(JavaKind.fromJavaClass(targetClass.getComponentType())));
                return constantValue;

            case NewInstance:
                try {
                    result = originalSnippetReflection.forObject(ReflectionUtil.newInstance(targetClass));
                } catch (ReflectionUtilError ex) {
                    throw VMError.shouldNotReachHere("Error performing field recomputation for alias " + annotated.format("%H.%n"), ex.getCause());
                }
                break;
            case AtomicFieldUpdaterOffset:
                result = computeAtomicFieldUpdaterOffset(receiver);
                break;
            case TranslateFieldOffset:
                result = translateFieldOffset(receiver, targetClass);
                break;
            case Custom:
                try {
                    Constructor<?>[] constructors = targetClass.getDeclaredConstructors();
                    if (constructors.length != 1) {
                        throw UserError.abort("The custom field value computer class " + targetClass.getName() + " has more than one constructor");
                    }
                    Constructor<?> constructor = constructors[0];

                    Object[] constructorArgs = new Object[constructor.getParameterCount()];
                    for (int i = 0; i < constructorArgs.length; i++) {
                        constructorArgs[i] = configurationValue(constructor.getParameterTypes()[i]);
                    }
                    constructor.setAccessible(true);
                    Object instance = constructor.newInstance(constructorArgs);

                    Object receiverValue = receiver == null ? null : originalSnippetReflection.asObject(Object.class, receiver);
                    Object newValue;
                    if (instance instanceof CustomFieldValueComputer) {
                        newValue = ((CustomFieldValueComputer) instance).compute(hMetaAccess, original, annotated, receiverValue);
                    } else if (instance instanceof CustomFieldValueTransformer) {
                        JavaConstant originalValueConstant = ReadableJavaField.readFieldValue(GraalAccess.getOriginalProviders().getConstantReflection(), original, receiver);
                        Object originalValue;
                        if (originalValueConstant.getJavaKind().isPrimitive()) {
                            originalValue = originalValueConstant.asBoxedPrimitive();
                        } else {
                            originalValue = originalSnippetReflection.asObject(Object.class, originalValueConstant);
                        }
                        newValue = ((CustomFieldValueTransformer) instance).transform(hMetaAccess, original, annotated, receiverValue, originalValue);
                    } else {
                        throw UserError.abort("The custom field value computer class " + targetClass.getName() + " does not implement " + CustomFieldValueComputer.class.getSimpleName() + " or " +
                                        CustomFieldValueTransformer.class.getSimpleName());
                    }

                    result = originalSnippetReflection.forBoxed(annotated.getJavaKind(), newValue);
                    assert result.getJavaKind() == annotated.getJavaKind();
                } catch (InvocationTargetException | InstantiationException | IllegalAccessException ex) {
                    throw shouldNotReachHere("Error performing field recomputation for alias " + annotated.format("%H.%n"), ex);
                }
                break;
            default:
                throw shouldNotReachHere("Field recomputation of kind " + kind + " specified by alias " + annotated.format("%H.%n") + " not yet supported");
        }
        putCached(receiver, result);
        return result;
    }

    private void putCached(JavaConstant receiver, JavaConstant result) {
        WriteLock writeLock = valueCacheLock.writeLock();
        try {
            writeLock.lock();
            if (receiver == null) {
                valueCacheNullKey = result;
            } else {
                valueCache.put(receiver, result);
            }
        } finally {
            writeLock.unlock();
        }
    }

    private JavaConstant getCached(JavaConstant receiver) {
        JavaConstant result;
        ReadLock readLock = valueCacheLock.readLock();
        try {
            readLock.lock();
            if (receiver == null) {
                result = valueCacheNullKey;
            } else {
                result = valueCache.get(receiver);
            }
        } finally {
            readLock.unlock();
        }
        return result;
    }

    @Override
    public boolean allowConstantFolding() {
        return getDeclaringClass().isInitialized() && isFinal;
    }

    @Override
    public boolean injectFinalForRuntimeCompilation() {
        if (original.isFinal()) {
            /*
             * We remove the "final" modifier for AOT compilation because the recomputed value is
             * not guaranteed to be known yet. But for runtime compilation, we know that we can
             * treat the field as "final".
             */
            return true;
        }
        return ReadableJavaField.injectFinalForRuntimeCompilation(original);
    }

    private static Object configurationValue(Class<?> clazz) {
        throw shouldNotReachHere("Parameter type not supported yet: " + clazz.getName());
    }

    private JavaConstant translateFieldOffset(JavaConstant receiver, Class<?> tclass) {
        long searchOffset = ReadableJavaField.readFieldValue(GraalAccess.getOriginalProviders().getConstantReflection(), original, receiver).asLong();
        // search the declared fields for a field with a matching offset
        for (Field f : tclass.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                long fieldOffset = UNSAFE.objectFieldOffset(f);
                if (fieldOffset == searchOffset) {
                    HostedField sf = hMetaAccess.lookupJavaField(f);
                    guarantee(sf.isAccessed() && sf.getLocation() > 0, "Field not marked as accessed: " + sf.format("%H.%n"));
                    return JavaConstant.forLong(sf.getLocation());
                }
            }
        }
        throw shouldNotReachHere("unknown field offset class: " + tclass + ", offset = " + searchOffset);
    }

    private JavaConstant computeAtomicFieldUpdaterOffset(JavaConstant receiver) {
        assert !Modifier.isStatic(original.getModifiers());
        assert receiver.isNonNull();

        /*
         * Explanation: java.util.concurrent is full of objects and classes that cache the offset of
         * particular fields in the JDK. Here, Atomic<X>FieldUpdater implementation objects cache
         * the offset of some specified field. Which field? Oh...only Doug Lea knows that. We have
         * to search the declaring class for a field that has the same "unsafe" offset as the cached
         * offset in this atomic updater object.
         */
        ResolvedJavaField tclassField = findField(original.getDeclaringClass(), "tclass");
        SnippetReflectionProvider originalSnippetReflection = GraalAccess.getOriginalSnippetReflection();
        Class<?> tclass = originalSnippetReflection.asObject(Class.class, ReadableJavaField.readFieldValue(GraalAccess.getOriginalProviders().getConstantReflection(), tclassField, receiver));
        return translateFieldOffset(receiver, tclass);
    }

    private static ResolvedJavaField findField(ResolvedJavaType declaringClass, String name) {
        for (ResolvedJavaField field : declaringClass.getInstanceFields(false)) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        throw shouldNotReachHere("Field not found: " + declaringClass.toJavaName(true) + "." + name);
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return original.getDeclaringClass();
    }

    @Override
    public Annotation[] getAnnotations() {
        return original.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return original.getDeclaredAnnotations();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return original.getAnnotation(annotationClass);
    }

    @Override
    public String toString() {
        return "RecomputeValueField<original " + original.toString() + ", kind " + kind + ">";
    }

    @Override
    public Field getJavaField() {
        return OriginalFieldProvider.getJavaField(GraalAccess.getOriginalSnippetReflection(), original);
    }
}
