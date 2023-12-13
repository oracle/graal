/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.SubstrateUtil.toUnboxedClass;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.AtomicFieldUpdaterOffset;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.FieldOffset;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.StaticFieldBase;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.TranslateFieldOffset;
import static com.oracle.svm.core.util.VMError.guarantee;
import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.core.util.VMError.shouldNotReachHereUnexpectedInput;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability.ValueAvailability;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ameta.ReadableJavaField;
import com.oracle.svm.hosted.annotation.AnnotationWrapper;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Wraps a field whose value is recomputed when added to an image.
 *
 * @see RecomputeFieldValue
 * @see NativeImageReinitialize
 */
public class ComputedValueField implements ReadableJavaField, OriginalFieldProvider, AnnotationWrapper {

    private static final EnumSet<RecomputeFieldValue.Kind> offsetComputationKinds = EnumSet.of(FieldOffset, TranslateFieldOffset, AtomicFieldUpdaterOffset);
    private final ResolvedJavaField original;
    private final ResolvedJavaField annotated;

    private final RecomputeFieldValue.Kind kind;
    private final Class<?> targetClass;
    private final Field targetField;
    private final Class<?> transformedValueAllowedType;
    private final FieldValueTransformer fieldValueTransformer;
    private final boolean isFinal;
    private final boolean disableCaching;
    /** True if the value doesn't depend on any analysis results. */
    private final boolean isValueAvailableBeforeAnalysis;
    /** True if the value depends on analysis results. */
    private final boolean isValueAvailableOnlyAfterAnalysis;
    /** True if the value depends on compilation results. */
    private final boolean isValueAvailableOnlyAfterCompilation;

    private JavaConstant constantValue;

    private final EconomicMap<JavaConstant, JavaConstant> valueCache;
    /**
     * Economic map does not allow to store null keys. Therefore null key is stored in an extra
     * field.
     */
    private JavaConstant valueCacheNullKey;
    private final ReentrantReadWriteLock valueCacheLock = new ReentrantReadWriteLock();

    public ComputedValueField(ResolvedJavaField original, ResolvedJavaField annotated, RecomputeFieldValue.Kind kind, Class<?> targetClass, String targetName, boolean isFinal) {
        this(original, annotated, kind, null, null, targetClass, targetName, isFinal, false);
    }

    @SuppressWarnings("this-escape")
    public ComputedValueField(ResolvedJavaField original, ResolvedJavaField annotated, RecomputeFieldValue.Kind kind, Class<?> transformedValueAllowedType, FieldValueTransformer initialTransformer,
                    Class<?> targetClass, String targetName, boolean isFinal, boolean disableCaching) {
        assert original != null;
        assert initialTransformer != null || targetClass != null;

        this.original = original;
        this.annotated = annotated;
        this.kind = kind;
        this.transformedValueAllowedType = transformedValueAllowedType;
        this.targetClass = targetClass;
        this.isFinal = isFinal;
        this.disableCaching = disableCaching;

        boolean customValueAvailableBeforeAnalysis = true;
        boolean customValueAvailableOnlyAfterAnalysis = false;
        boolean customValueAvailableOnlyAfterCompilation = false;
        FieldValueTransformer transformer = null;
        Field f = null;
        switch (kind) {
            case Reset:
                constantValue = JavaConstant.defaultForKind(getJavaKind());
                break;
            case FieldOffset:
                f = getField(annotated, targetClass, targetName);
                break;
            case StaticFieldBase:
                f = getField(annotated, targetClass, targetName);
                UserError.guarantee(Modifier.isStatic(f.getModifiers()), "Target field must be static for %s computation of %s", StaticFieldBase, fieldFormat());
                break;
            case Custom:
                if (initialTransformer != null) {
                    transformer = initialTransformer;
                } else {
                    transformer = (FieldValueTransformer) ReflectionUtil.newInstance(targetClass);
                }

                if (transformer instanceof FieldValueTransformerWithAvailability) {
                    ValueAvailability valueAvailability = ((FieldValueTransformerWithAvailability) transformer).valueAvailability();
                    customValueAvailableBeforeAnalysis = valueAvailability == ValueAvailability.BeforeAnalysis;
                    customValueAvailableOnlyAfterAnalysis = valueAvailability == ValueAvailability.AfterAnalysis;
                    customValueAvailableOnlyAfterCompilation = valueAvailability == ValueAvailability.AfterCompilation;
                }
        }
        boolean isOffsetField = isOffsetRecomputation(kind);
        boolean isStaticFieldBase = kind == StaticFieldBase;
        guarantee(!isFinal || !isOffsetField);
        this.isValueAvailableBeforeAnalysis = customValueAvailableBeforeAnalysis && !isOffsetField && !isStaticFieldBase;
        this.isValueAvailableOnlyAfterAnalysis = customValueAvailableOnlyAfterAnalysis || isOffsetField || isStaticFieldBase;
        this.isValueAvailableOnlyAfterCompilation = customValueAvailableOnlyAfterCompilation;
        this.targetField = f;
        this.fieldValueTransformer = transformer;
        this.valueCache = EconomicMap.create();
    }

    private static Field getField(ResolvedJavaField annotated, Class<?> targetClass, String targetName) {
        try {
            return ReflectionUtil.lookupField(targetClass, targetName);
        } catch (ReflectionUtilError e) {
            throw UserError.abort("Could not find target field %s.%s for alias %s.", targetClass.getName(), targetName, annotated == null ? null : annotated.format("%H.%n"));
        }
    }

    public static boolean isOffsetRecomputation(RecomputeFieldValue.Kind kind) {
        return offsetComputationKinds.contains(kind);
    }

    @Override
    public boolean isValueAvailableBeforeAnalysis() {
        return isValueAvailableBeforeAnalysis;
    }

    @Override
    public boolean isValueAvailable() {
        /*
         * Note that we use isHostedUniverseBuild on purpose to define "available after analysis":
         * many field value transformers require field offsets to be available, i.e., the hosted
         * universe to be built. This ensures that such field value transformers do not have their
         * value available when strengthening graphs after analysis, i.e., when applying analysis
         * results back into the IR.
         */
        return constantValue != null || isValueAvailableBeforeAnalysis() ||
                        (isValueAvailableOnlyAfterAnalysis && BuildPhaseProvider.isHostedUniverseBuilt()) ||
                        (isValueAvailableOnlyAfterCompilation && BuildPhaseProvider.isCompilationFinished());
    }

    public ResolvedJavaField getAnnotated() {
        return annotated;
    }

    public Field getTargetField() {
        return targetField;
    }

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
                target.registerAsAccessed(this);
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
                throw shouldNotReachHereUnexpectedInput(getJavaKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    public void processSubstrate(HostedMetaAccess metaAccess) {
        switch (kind) {
            case FieldOffset:
                constantValue = asConstant(metaAccess.lookupJavaField(targetField).getLocation());
                break;
        }
    }

    @Override
    public JavaConstant readValue(MetaAccessProvider metaAccess, ClassInitializationSupport classInitializationSupport, JavaConstant receiver) {
        if (constantValue != null) {
            return constantValue;
        }
        switch (kind) {
            case None:
            case Manual:
                return ReadableJavaField.readFieldValue(metaAccess, classInitializationSupport, original, receiver);

            case FromAlias:
                assert Modifier.isStatic(annotated.getModifiers()) : "Cannot use " + kind + " on non-static alias " + annotated.format("%H.%n");
                annotated.getDeclaringClass().initialize();
                constantValue = ReadableJavaField.readFieldValue(metaAccess, classInitializationSupport, annotated, null);
                return constantValue;

            case ArrayBaseOffset:
                constantValue = asConstant(ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.fromJavaClass(targetClass.getComponentType())));
                return constantValue;
            case ArrayIndexScale:
                constantValue = asConstant(ConfigurationValues.getObjectLayout().getArrayIndexScale(JavaKind.fromJavaClass(targetClass.getComponentType())));
                return constantValue;
            case ArrayIndexShift:
                constantValue = asConstant(ConfigurationValues.getObjectLayout().getArrayIndexShift(JavaKind.fromJavaClass(targetClass.getComponentType())));
                return constantValue;
            case StaticFieldBase:
                Object staticFieldsArray = targetField.getType().isPrimitive() ? StaticFieldsSupport.getStaticPrimitiveFields() : StaticFieldsSupport.getStaticObjectFields();
                constantValue = GraalAccess.getOriginalSnippetReflection().forObject(staticFieldsArray);
                return constantValue;
        }

        ReadLock readLock = valueCacheLock.readLock();
        try {
            readLock.lock();
            JavaConstant result = getCached(receiver);
            if (result != null) {
                return result;
            }
        } finally {
            readLock.unlock();
        }

        WriteLock writeLock = valueCacheLock.writeLock();
        try {
            writeLock.lock();
            /*
             * Check the cache again, now that we are holding the write-lock, i.e., we know that no
             * other thread is computing a value right now.
             */
            JavaConstant result = getCached(receiver);
            if (result != null) {
                return result;
            }
            /*
             * Note that the value computation must be inside the lock, because we want to guarantee
             * that field-value computers are only executed once per unique receiver.
             */
            result = computeValue(metaAccess, classInitializationSupport, receiver);
            putCached(receiver, result);
            return result;
        } finally {
            writeLock.unlock();
        }
    }

    private JavaConstant computeValue(MetaAccessProvider metaAccess, ClassInitializationSupport classInitializationSupport, JavaConstant receiver) {
        assert isValueAvailable() : "Field " + format("%H.%n") + " value not available for reading.";
        SnippetReflectionProvider originalSnippetReflection = GraalAccess.getOriginalSnippetReflection();
        JavaConstant result;
        Object originalValue;
        switch (kind) {
            case NewInstanceWhenNotNull:
                originalValue = fetchOriginalValue(metaAccess, classInitializationSupport, receiver, originalSnippetReflection);
                result = originalValue == null ? originalSnippetReflection.forObject(null) : createNewInstance(originalSnippetReflection);
                break;
            case NewInstance:
                result = createNewInstance(originalSnippetReflection);
                break;
            case AtomicFieldUpdaterOffset:
                result = computeAtomicFieldUpdaterOffset(metaAccess, classInitializationSupport, receiver);
                break;
            case TranslateFieldOffset:
                result = translateFieldOffset(metaAccess, classInitializationSupport, receiver, targetClass);
                break;
            case Custom:
                Object receiverValue = receiver == null ? null : originalSnippetReflection.asObject(Object.class, receiver);
                originalValue = fetchOriginalValue(metaAccess, classInitializationSupport, receiver, originalSnippetReflection);
                Object newValue = fieldValueTransformer.transform(receiverValue, originalValue);
                checkValue(newValue);
                result = originalSnippetReflection.forBoxed(original.getJavaKind(), newValue);

                assert result.getJavaKind() == original.getJavaKind();
                break;
            default:
                throw shouldNotReachHere("Field recomputation of kind " + kind + " for " + fieldFormat() + " not yet supported");
        }
        return result;
    }

    private void checkValue(Object newValue) {
        boolean primitive = transformedValueAllowedType.isPrimitive();
        if (newValue == null) {
            if (primitive) {
                throw UserError.abort("Field value transformer returned null for primitive %s", fieldFormat());
            } else {
                /* Null is always allowed for reference fields. */
                return;
            }
        }
        /*
         * The compute/transform methods autobox primitive values. We unbox them here, but only if
         * the original field is primitive.
         */
        Class<?> actualType = primitive ? toUnboxedClass(newValue.getClass()) : newValue.getClass();
        if (!transformedValueAllowedType.isAssignableFrom(actualType)) {
            throw UserError.abort("Field value transformer returned value of type `%s` that is not assignable to declared type `%s` of %s",
                            actualType.getTypeName(), transformedValueAllowedType.getTypeName(), fieldFormat());
        }
    }

    private String fieldFormat() {
        return "field " + original.format("%H.%n") + (annotated != null ? " specified by alias " + annotated.format("%H.%n") : "");
    }

    private JavaConstant createNewInstance(SnippetReflectionProvider originalSnippetReflection) {
        JavaConstant result;
        try {
            result = originalSnippetReflection.forObject(ReflectionUtil.newInstance(targetClass));
        } catch (ReflectionUtilError ex) {
            throw VMError.shouldNotReachHere("Error performing field recomputation for alias " + annotated.format("%H.%n"), ex.getCause());
        }
        return result;
    }

    private Object fetchOriginalValue(MetaAccessProvider metaAccess, ClassInitializationSupport classInitializationSupport, JavaConstant receiver,
                    SnippetReflectionProvider originalSnippetReflection) {
        JavaConstant originalValueConstant = ReadableJavaField.readFieldValue(metaAccess, classInitializationSupport, original, receiver);
        if (originalValueConstant == null) {
            /*
             * The class is still uninitialized, so static fields cannot be read. Or it is an
             * instance field in a substitution class, i.e., a field that does not exist in the
             * hosted object.
             */
            return null;
        } else if (originalValueConstant.getJavaKind().isPrimitive()) {
            return originalValueConstant.asBoxedPrimitive();
        } else {
            return originalSnippetReflection.asObject(Object.class, originalValueConstant);
        }
    }

    private void putCached(JavaConstant receiver, JavaConstant result) {
        if (disableCaching) {
            return;
        }
        if (receiver == null) {
            valueCacheNullKey = result;
        } else {
            valueCache.put(receiver, result);
        }
    }

    private JavaConstant getCached(JavaConstant receiver) {
        if (receiver == null) {
            return valueCacheNullKey;
        } else {
            return valueCache.get(receiver);
        }
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

    private JavaConstant translateFieldOffset(MetaAccessProvider metaAccess, ClassInitializationSupport classInitializationSupport, JavaConstant receiver, Class<?> tclass) {
        long searchOffset = ReadableJavaField.readFieldValue(metaAccess, classInitializationSupport, original, receiver).asLong();
        // search the declared fields for a field with a matching offset
        for (Field f : tclass.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                long fieldOffset = Unsafe.getUnsafe().objectFieldOffset(f);
                if (fieldOffset == searchOffset) {
                    HostedField sf = (HostedField) metaAccess.lookupJavaField(f);
                    guarantee(sf.isAccessed() && sf.getLocation() > 0, "Field not marked as accessed: %s", sf);
                    return JavaConstant.forLong(sf.getLocation());
                }
            }
        }
        throw shouldNotReachHere("unknown field offset class: " + tclass + ", offset = " + searchOffset);
    }

    private JavaConstant computeAtomicFieldUpdaterOffset(MetaAccessProvider metaAccess, ClassInitializationSupport classInitializationSupport, JavaConstant receiver) {
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
        Class<?> tclass = originalSnippetReflection.asObject(Class.class, ReadableJavaField.readFieldValue(metaAccess, classInitializationSupport, tclassField, receiver));
        return translateFieldOffset(metaAccess, classInitializationSupport, receiver, tclass);
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
    public AnnotatedElement getAnnotationRoot() {
        return original;
    }

    public boolean isCompatible(ResolvedJavaField o) {
        if (this.equals(o)) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ComputedValueField that = (ComputedValueField) o;
        return isFinal == that.isFinal && disableCaching == that.disableCaching && original.equals(that.original) && kind == that.kind &&
                        Objects.equals(targetClass, that.targetClass) && Objects.equals(targetField, that.targetField) && Objects.equals(constantValue, that.constantValue);
    }

    @Override
    public String toString() {
        return "RecomputeValueField<original " + original.toString() + ", kind " + kind + ">";
    }

    @Override
    public Field getJavaField() {
        return OriginalFieldProvider.getJavaField(original);
    }

    @Override
    public JavaConstant getConstantValue() {
        return original.getConstantValue();
    }
}
