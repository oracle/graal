/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.ameta;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.analysis.FieldValueComputer;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.hosted.substitute.AutomaticUnsafeTransformationSupport;
import com.oracle.svm.hosted.substitute.FieldValueTransformation;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * This class centralizes access to the several ways we have to transform and intercept field
 * values:
 *
 * {@link FieldValueTransformer} are part of the supported API, and the preferred way of
 * transformation. The non-API {@link FieldValueTransformerWithAvailability} allows to provide field
 * values only after static analysis. The API for registration transformers
 * {@link BeforeAnalysisAccess#registerFieldValueTransformer}. Transformers are also registered
 * automatically for {@link Alias} fields with a {@link RecomputeFieldValue} annotation, as well as
 * by the {@link AutomaticUnsafeTransformationSupport}.
 *
 * {@link UnknownObjectField} and {@link UnknownPrimitiveField} are internal annotations for fields
 * whose value is only available after static analysis. Once the value is available, it is read as
 * usual from the hosted HotSpot field without any further transformation.
 */
public final class FieldValueInterceptionSupport {
    private static final Object INTERCEPTOR_ACCESSED_MARKER = new Object();

    private final AnnotationSubstitutionProcessor annotationSubstitutions;
    private final Map<ResolvedJavaField, Object> fieldValueInterceptors = new ConcurrentHashMap<>();

    public static FieldValueInterceptionSupport singleton() {
        return ImageSingletons.lookup(FieldValueInterceptionSupport.class);
    }

    public FieldValueInterceptionSupport(AnnotationSubstitutionProcessor annotationSubstitutions) {
        this.annotationSubstitutions = annotationSubstitutions;
    }

    /**
     * Returns a {@link FieldValueTransformer} if one was already registered for the field. In
     * contrast to most other methods of this class, invoking this method does not prevent a future
     * registration of a field value transformer for that field.
     */
    public FieldValueTransformer lookupAlreadyRegisteredTransformer(ResolvedJavaField oField) {
        assert !(oField instanceof OriginalFieldProvider) : oField;

        var existingInterceptor = fieldValueInterceptors.get(oField);
        if (existingInterceptor instanceof FieldValueTransformation fieldValueTransformation) {
            return fieldValueTransformation.getFieldValueTransformer();
        }
        return null;
    }

    /**
     * Register a field value transformer for the provided field. There can only be one transformer
     * per field, if there is already a transformation in place, a {@link UserError} is reported.
     */
    public void registerFieldValueTransformer(Field reflectionField, FieldValueTransformer transformer) {
        registerFieldValueTransformer(GraalAccess.getOriginalProviders().getMetaAccess().lookupJavaField(reflectionField), transformer);
    }

    public void registerFieldValueTransformer(ResolvedJavaField oField, FieldValueTransformer transformer) {
        if (annotationSubstitutions.isDeleted(oField)) {
            throw UserError.abort("Cannot register a field value transformer for field %s: %s", oField.format("%H.%n"),
                            "The field is marked as deleted, i.e., the field is not available on this platform");
        }
        registerFieldValueTransformer(oField, OriginalClassProvider.getJavaClass(oField.getType()), transformer);
    }

    public void registerFieldValueTransformer(ResolvedJavaField oField, Class<?> transformedValueAllowedType, FieldValueTransformer transformer) {
        assert oField != null && !(oField instanceof OriginalFieldProvider) : oField;

        var transformation = new FieldValueTransformation(transformedValueAllowedType, Objects.requireNonNull(transformer));
        var existingInterceptor = fieldValueInterceptors.putIfAbsent(oField, transformation);

        if (existingInterceptor == INTERCEPTOR_ACCESSED_MARKER) {
            throw UserError.abort("Cannot register a field value transformer for field %s: %s", oField.format("%H.%n"),
                            "The field was already accessed by the static analysis. The transformer must be registered earlier, before the static analysis sees a reference to the field for the first time.");
        } else if (existingInterceptor != null) {
            throw UserError.abort("Cannot register a field value transformer for field %s: %s", oField.format("%H.%n"),
                            "A field value transformer is already registered for this field, or the field value is transformed via an @Alias annotation.");
        }
    }

    Object lookupFieldValueInterceptor(AnalysisField field) {
        var result = field.getFieldValueInterceptor();
        if (result == null) {
            result = computeAndCacheFieldValueInterceptor(field);
        }
        return result == INTERCEPTOR_ACCESSED_MARKER ? null : result;
    }

    private Object computeAndCacheFieldValueInterceptor(AnalysisField field) {
        /*
         * Trigger computation of automatic substitutions. There might be an automatic substitution
         * for the current field, and we must register it before checking if a field value
         * transformer exists.
         */
        field.beforeFieldValueAccess();

        ResolvedJavaField oField = OriginalFieldProvider.getOriginalField(field);
        FieldValueComputer computer = createFieldValueComputer(field);
        Object result;
        if (computer != null) {
            VMError.guarantee(oField != null, "Cannot have a @UnknownObjectField or @UnknownPrimitiveField annotation on synthetic field %s", field);

            var interceptor = fieldValueInterceptors.computeIfAbsent(oField, k -> computer);
            /*
             * There can be a race with another thread, so `interceptor` might not be the same
             * object as `computer`. But that is not a problem because they are equivalent
             * `FieldValueComputer`. We only need to check that there was no field value transformer
             * registered beforehand. Unfortunately, we do not have a good stack trace for the user
             * showing how the field value transformer was created. But we expect this to be a rare
             * error, since the `@Unknown*Field` annotations are not public API.
             */
            if (!(interceptor instanceof FieldValueComputer)) {
                throw UserError.abort("Cannot register a field value transformer for field %s: %s", field.format("%H.%n"),
                                "The field is annotated with @UnknownObjectField or @UnknownPrimitiveField.");
            }
            result = interceptor;

        } else if (oField != null) {
            /*
             * If no field value transformer was registered beforehand, install our marker value so
             * that later registration of a field value transformer is reported as an error.
             */
            result = fieldValueInterceptors.computeIfAbsent(oField, k -> INTERCEPTOR_ACCESSED_MARKER);
        } else {
            /*
             * This is a synthetic field, so it is not possible to install a field value transformer
             * for it.
             */
            result = INTERCEPTOR_ACCESSED_MARKER;
        }

        Objects.requireNonNull(result, "Must have a non-null value now to avoid repeated invocation of this method");
        /*
         * Cache the result for future fast lookups. No need to be atomic here again, that was
         * already done via the operations on `fieldValueInterceptors`. Multiple threads might write
         * the same value into the cache.
         */
        field.setFieldValueInterceptor(result);
        return result;
    }

    /**
     * Check if the value of the provided field is currently available. After this method has been
     * called, it is not possible to install a transformer anymore.
     */
    public boolean isValueAvailable(AnalysisField field) {
        var interceptor = lookupFieldValueInterceptor(field);
        if (interceptor instanceof FieldValueTransformation transformation) {
            if (!transformation.getFieldValueTransformer().isAvailable()) {
                return false;
            }
        } else if (interceptor instanceof FieldValueComputer computer) {
            if (!computer.isAvailable()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if a field value interceptor ({@link FieldValueTransformation} or
     * {@link FieldValueComputer}) has been registered for this field. Unlike
     * {@link #hasFieldValueTransformer(AnalysisField)}, calling this method is side effect free.
     */
    public static boolean hasFieldValueInterceptor(AnalysisField field) {
        var interceptor = field.getFieldValueInterceptor();
        if (interceptor != null && interceptor != INTERCEPTOR_ACCESSED_MARKER) {
            VMError.guarantee(interceptor instanceof FieldValueTransformation || interceptor instanceof FieldValueComputer);
            return true;
        }
        return false;
    }

    /**
     * Returns true if a field value transformer has been registered for this field. After this
     * method has been called, it is not possible to install a transformer anymore.
     */
    public boolean hasFieldValueTransformer(AnalysisField field) {
        return lookupFieldValueInterceptor(field) instanceof FieldValueTransformation;
    }

    /**
     * Returns the Graal IR node that intrinsifies the provided field load, or null if no
     * intrinsification is possible.
     */
    public ValueNode tryIntrinsifyFieldLoad(CoreProviders providers, LoadFieldNode node) {
        var field = (AnalysisField) node.field();

        var interceptor = lookupFieldValueInterceptor(field);
        if (!(interceptor instanceof FieldValueTransformation transformation)) {
            return null;
        }
        var transformer = transformation.getFieldValueTransformer();
        if (!(transformer instanceof FieldValueTransformerWithAvailability transformerWithAvailability)) {
            return null;
        }

        JavaConstant receiver;
        if (field.isStatic()) {
            receiver = null;
        } else {
            receiver = node.object().asJavaConstant();
            /*
             * The receiver constant might not be an instance of the field's declaring class,
             * because during optimizations the load can actually be dead code that will be removed
             * later. We do not want to burden the field value transformers with such details, so we
             * check here.
             */
            if (!(receiver instanceof ImageHeapConstant imageHeapConstant) || !field.getDeclaringClass().isAssignableFrom(imageHeapConstant.getType())) {
                return null;
            }
        }

        return transformerWithAvailability.intrinsify(providers, receiver);
    }

    JavaConstant readFieldValue(AnalysisField field, JavaConstant receiver) {
        assert isValueAvailable(field) : field;
        JavaConstant value;
        var interceptor = lookupFieldValueInterceptor(field);
        if (interceptor instanceof FieldValueTransformation transformation) {
            value = transformation.readValue(field, receiver);

        } else if (!field.getDeclaringClass().isInitialized()) {
            /*
             * The class is initialized at image run time. We must not use any field value from the
             * image builder VM, even if the class is already initialized there. We need to return
             * the value expected before running the class initializer.
             */
            if (field.isStatic()) {
                /*
                 * Use the value from the constant pool attribute for the static field. That is the
                 * value before the class initializer is executed.
                 */
                JavaConstant constantValue = field.getConstantValue();
                if (constantValue != null) {
                    value = constantValue;
                } else {
                    value = JavaConstant.defaultForKind(field.getJavaKind());
                }

            } else {
                /*
                 * Classes that are initialized at run time must not have instances in the image
                 * heap. Invoking instance methods would miss the class initialization checks. Image
                 * generation should have been aborted earlier with a user-friendly message, this is
                 * just a safeguard.
                 */
                throw VMError.shouldNotReachHere("Cannot read instance field of a class that is initialized at run time: " + field.format("%H.%n"));
            }

        } else {
            ResolvedJavaField oField = OriginalFieldProvider.getOriginalField(field);
            if (oField == null) {
                throw VMError.shouldNotReachHere("Cannot read value of field that has no host value: " + field.format("%H.%n"));
            }
            value = GraalAccess.getOriginalProviders().getConstantReflection().readFieldValue(oField, receiver);
        }

        return interceptValue(field, value);
    }

    private static JavaConstant interceptValue(AnalysisField field, JavaConstant value) {
        JavaConstant result = value;
        if (result != null) {
            result = filterInjectedAccessor(field, result);
            result = interceptAssertionStatus(field, result);
            result = interceptWordField(field, result);
        }
        return result;
    }

    /**
     * Fields whose accesses are intercepted by injected accessors are not actually present in the
     * image. Ideally they should never be read, but there are corner cases where this happens. We
     * intercept the value and return 0 / null.
     */
    private static JavaConstant filterInjectedAccessor(AnalysisField field, JavaConstant value) {
        if (field.getAnnotation(InjectAccessors.class) != null) {
            assert !field.isAccessed();
            return JavaConstant.defaultForKind(value.getJavaKind());
        }
        return value;
    }

    /**
     * Intercept assertion status: the value of the field during image generation does not matter at
     * all (because it is the hosted assertion status), we instead return the appropriate runtime
     * assertion status. Field loads are also intrinsified early in
     * {@link com.oracle.svm.hosted.phases.EarlyConstantFoldLoadFieldPlugin}, but we could still see
     * such a field here if user code, e.g., accesses it via reflection.
     */
    private static JavaConstant interceptAssertionStatus(AnalysisField field, JavaConstant value) {
        if (field.isStatic() && field.isSynthetic() && field.getName().startsWith("$assertionsDisabled")) {
            Class<?> clazz = field.getDeclaringClass().getJavaClass();
            boolean assertionsEnabled = RuntimeAssertionsSupport.singleton().desiredAssertionStatus(clazz);
            return JavaConstant.forBoolean(!assertionsEnabled);
        }
        return value;
    }

    /**
     * Intercept {@link Word} fields. {@link Word} values are boxed objects in the hosted world, but
     * primitive values in the runtime world, so the default value of {@link Word} fields is 0.
     */
    private static JavaConstant interceptWordField(AnalysisField field, JavaConstant value) {
        if (value.getJavaKind() == JavaKind.Object && value.isNull() && field.getType().isWordType()) {
            return JavaConstant.forIntegerKind(ConfigurationValues.getWordKind(), 0);
        }
        return value;
    }

    private static FieldValueComputer createFieldValueComputer(AnalysisField field) {
        UnknownObjectField unknownObjectField = field.getAnnotation(UnknownObjectField.class);
        if (unknownObjectField != null) {
            checkMisplacedAnnotation(field.getStorageKind().isObject(), field);
            return new FieldValueComputer(
                            ReflectionUtil.newInstance(unknownObjectField.availability()),
                            extractAnnotationTypes(field, unknownObjectField.types(), unknownObjectField.fullyQualifiedTypes()),
                            unknownObjectField.canBeNull());
        }
        UnknownPrimitiveField unknownPrimitiveField = field.getAnnotation(UnknownPrimitiveField.class);
        if (unknownPrimitiveField != null) {
            checkMisplacedAnnotation(field.getStorageKind().isPrimitive(), field);
            return new FieldValueComputer(
                            ReflectionUtil.newInstance(unknownPrimitiveField.availability()),
                            List.of(field.getType().getJavaClass()),
                            false);
        }
        return null;
    }

    /**
     * For compatibility reasons, we cannot unify {@link UnknownObjectField} and
     * {@link UnknownPrimitiveField} into a single annotation, but we can at least notify the
     * developers if the annotation is misplaced, e.g. {@link UnknownObjectField} is used on a
     * primitive field and vice versa.
     */
    private static void checkMisplacedAnnotation(boolean condition, AnalysisField field) {
        if (!condition) {
            String fieldType;
            Class<? extends Annotation> expectedAnnotationType;
            Class<? extends Annotation> usedAnnotationType;
            if (field.getStorageKind().isObject()) {
                fieldType = "object";
                expectedAnnotationType = UnknownObjectField.class;
                usedAnnotationType = UnknownPrimitiveField.class;
            } else {
                fieldType = "primitive";
                expectedAnnotationType = UnknownPrimitiveField.class;
                usedAnnotationType = UnknownObjectField.class;
            }
            throw UserError.abort("@%s should not be used on %s fields, use @%s on %s instead.", ClassUtil.getUnqualifiedName(usedAnnotationType),
                            fieldType, ClassUtil.getUnqualifiedName(expectedAnnotationType), field.format("%H.%n"));
        }
    }

    private static List<Class<?>> extractAnnotationTypes(AnalysisField field, Class<?>[] types, String[] fullyQualifiedTypes) {
        List<Class<?>> annotationTypes = new ArrayList<>(Arrays.asList(types));
        for (String annotationTypeName : fullyQualifiedTypes) {
            try {
                Class<?> annotationType = Class.forName(annotationTypeName);
                annotationTypes.add(annotationType);
            } catch (ClassNotFoundException e) {
                throw UserError.abort("Specified computed value type not found: " + annotationTypeName);
            }
        }

        if (annotationTypes.isEmpty()) {
            /* If no types are specified, fall back to the field declared type. */
            AnalysisType fieldType = field.getType();
            UserError.guarantee(CustomTypeFieldHandler.isConcreteType(fieldType), "Illegal use of @UnknownObjectField annotation on field %s. " +
                            "The field type must be concrete or the annotation must declare a concrete type.", field);
            annotationTypes.add(fieldType.getJavaClass());
        }
        return annotationTypes;
    }
}
