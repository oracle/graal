/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.target;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Supplier;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.CustomFieldValueComputer;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.reflect.hosted.ReflectionObjectReplacer;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import sun.reflect.annotation.TypeAnnotation;

@TargetClass(value = Executable.class)
public final class Target_java_lang_reflect_Executable {

    /**
     * The parameters field doesn't need a value recomputation. Its value is pre-loaded in the
     * {@link ReflectionObjectReplacer}.
     */
    @Alias //
    Target_java_lang_reflect_Parameter[] parameters;

    @Alias //
    boolean hasRealParameterData;

    /**
     * The declaredAnnotations field doesn't need a value recomputation. Its value is pre-loaded in
     * the {@link ReflectionObjectReplacer}.
     */
    @Alias //
    Map<Class<? extends Annotation>, Annotation> declaredAnnotations;

    @Inject @RecomputeFieldValue(kind = Kind.Reset) //
    byte[] typeAnnotations;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = ParameterAnnotationsComputer.class) //
    Annotation[][] parameterAnnotations;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = AnnotatedReceiverTypeComputer.class) //
    Object annotatedReceiverType;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = AnnotatedParameterTypesComputer.class) //
    Object[] annotatedParameterTypes;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = AnnotatedReturnTypeComputer.class) //
    Object annotatedReturnType;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = AnnotatedExceptionTypesComputer.class) //
    Object[] annotatedExceptionTypes;

    @Alias //
    public native int getModifiers();

    @Alias //
    native byte[] getAnnotationBytes();

    @Alias //
    public native Class<?> getDeclaringClass();

    @Alias //
    native Type[] getAllGenericParameterTypes();

    @Alias //
    public native Type[] getGenericExceptionTypes();

    @Alias //
    private native Target_java_lang_reflect_Parameter[] synthesizeAllParams();

    @Substitute
    private Target_java_lang_reflect_Parameter[] privateGetParameters() {
        Target_java_lang_reflect_Executable holder = ReflectionHelper.getHolder(this);
        if (holder.parameters != null) {
            return holder.parameters;
        } else if (MethodMetadataDecoderImpl.hasQueriedMethods()) {
            assert !hasRealParameterData;
            holder.parameters = synthesizeAllParams();
            return holder.parameters;
        }
        throw VMError.shouldNotReachHere();
    }

    @Substitute
    Map<Class<? extends Annotation>, Annotation> declaredAnnotations() {
        Map<Class<? extends Annotation>, Annotation> declAnnos;
        if ((declAnnos = declaredAnnotations) == null) {
            if (!MethodMetadataDecoderImpl.hasQueriedMethods()) {
                throw VMError.shouldNotReachHere();
            }
            synchronized (this) {
                if ((declAnnos = declaredAnnotations) == null) {
                    Target_java_lang_reflect_Executable holder = ReflectionHelper.getHolder(this);
                    declAnnos = Target_sun_reflect_annotation_AnnotationParser.parseAnnotations(
                                    holder.getAnnotationBytes(),
                                    new Target_jdk_internal_reflect_ConstantPool(),
                                    holder.getDeclaringClass());
                    declaredAnnotations = declAnnos;
                }
            }
        }
        return declAnnos;
    }

    @Alias
    native Annotation[][] sharedGetParameterAnnotations(Class<?>[] parameterTypes, byte[] annotations);

    @Substitute
    @SuppressWarnings({"unused", "hiding", "static-method"})
    Annotation[][] parseParameterAnnotations(byte[] parameterAnnotations) {
        if (!MethodMetadataDecoderImpl.hasQueriedMethods()) {
            throw VMError.shouldNotReachHere();
        }
        return Target_sun_reflect_annotation_AnnotationParser.parseParameterAnnotations(
                        parameterAnnotations,
                        new Target_jdk_internal_reflect_ConstantPool(),
                        getDeclaringClass());
    }

    @Substitute
    public AnnotatedType getAnnotatedReceiverType() {
        Target_java_lang_reflect_Executable holder = ReflectionHelper.getHolder(this);
        if (holder.annotatedReceiverType != null) {
            return (AnnotatedType) AnnotatedTypeEncoder.decodeAnnotationTypes(holder.annotatedReceiverType);
        }
        if (Modifier.isStatic(this.getModifiers())) {
            return null;
        }
        if (MethodMetadataDecoderImpl.hasQueriedMethods()) {
            AnnotatedType annotatedRecvType = Target_sun_reflect_annotation_TypeAnnotationParser.buildAnnotatedType(typeAnnotations,
                            new Target_jdk_internal_reflect_ConstantPool(),
                            SubstrateUtil.cast(this, AnnotatedElement.class),
                            getDeclaringClass(),
                            getDeclaringClass(),
                            TypeAnnotation.TypeAnnotationTarget.METHOD_RECEIVER);
            holder.annotatedReceiverType = annotatedRecvType;
            return annotatedRecvType;
        }
        throw VMError.shouldNotReachHere();
    }

    @Substitute
    public AnnotatedType[] getAnnotatedParameterTypes() {
        Target_java_lang_reflect_Executable holder = ReflectionHelper.getHolder(this);
        if (holder.annotatedParameterTypes != null) {
            return (AnnotatedType[]) AnnotatedTypeEncoder.decodeAnnotationTypes(holder.annotatedParameterTypes);
        } else if (MethodMetadataDecoderImpl.hasQueriedMethods()) {
            AnnotatedType[] annotatedParamTypes = Target_sun_reflect_annotation_TypeAnnotationParser.buildAnnotatedTypes(typeAnnotations,
                            new Target_jdk_internal_reflect_ConstantPool(),
                            SubstrateUtil.cast(this, AnnotatedElement.class),
                            getDeclaringClass(),
                            getAllGenericParameterTypes(),
                            TypeAnnotation.TypeAnnotationTarget.METHOD_FORMAL_PARAMETER);
            holder.annotatedParameterTypes = annotatedParamTypes;
            return annotatedParamTypes;
        }
        throw VMError.shouldNotReachHere();
    }

    @Substitute
    public AnnotatedType getAnnotatedReturnType0(@SuppressWarnings("unused") Type returnType) {
        Target_java_lang_reflect_Executable holder = ReflectionHelper.getHolder(this);
        if (holder.annotatedReturnType != null) {
            return (AnnotatedType) AnnotatedTypeEncoder.decodeAnnotationTypes(holder.annotatedReturnType);
        } else if (MethodMetadataDecoderImpl.hasQueriedMethods()) {
            AnnotatedType annotatedRetType = Target_sun_reflect_annotation_TypeAnnotationParser.buildAnnotatedType(typeAnnotations,
                            new Target_jdk_internal_reflect_ConstantPool(),
                            SubstrateUtil.cast(this, AnnotatedElement.class),
                            getDeclaringClass(),
                            returnType,
                            TypeAnnotation.TypeAnnotationTarget.METHOD_RETURN);
            holder.annotatedReturnType = annotatedRetType;
            return annotatedRetType;
        }
        throw VMError.shouldNotReachHere();
    }

    @Substitute
    public AnnotatedType[] getAnnotatedExceptionTypes() {
        Target_java_lang_reflect_Executable holder = ReflectionHelper.getHolder(this);
        if (holder.annotatedExceptionTypes != null) {
            return (AnnotatedType[]) AnnotatedTypeEncoder.decodeAnnotationTypes(holder.annotatedExceptionTypes);
        } else if (MethodMetadataDecoderImpl.hasQueriedMethods()) {
            AnnotatedType[] annotatedExcTypes = Target_sun_reflect_annotation_TypeAnnotationParser.buildAnnotatedTypes(typeAnnotations,
                            new Target_jdk_internal_reflect_ConstantPool(),
                            SubstrateUtil.cast(this, AnnotatedElement.class),
                            getDeclaringClass(),
                            getGenericExceptionTypes(),
                            TypeAnnotation.TypeAnnotationTarget.THROWS);
            holder.annotatedExceptionTypes = annotatedExcTypes;
            return annotatedExcTypes;
        }
        throw VMError.shouldNotReachHere();
    }

    public static final class ParameterAnnotationsComputer implements CustomFieldValueComputer {
        @Override
        public RecomputeFieldValue.ValueAvailability valueAvailability() {
            return RecomputeFieldValue.ValueAvailability.BeforeAnalysis;
        }

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            Executable executable = (Executable) receiver;
            return executable.getParameterAnnotations();
        }
    }

    public static final class AnnotatedReceiverTypeComputer implements CustomFieldValueComputer {
        @Override
        public RecomputeFieldValue.ValueAvailability valueAvailability() {
            return RecomputeFieldValue.ValueAvailability.BeforeAnalysis;
        }

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            Executable executable = (Executable) receiver;
            return AnnotatedTypeEncoder.encodeAnnotationTypes(executable::getAnnotatedReceiverType, receiver);
        }
    }

    public static final class AnnotatedParameterTypesComputer implements CustomFieldValueComputer {
        @Override
        public RecomputeFieldValue.ValueAvailability valueAvailability() {
            return RecomputeFieldValue.ValueAvailability.BeforeAnalysis;
        }

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            Executable executable = (Executable) receiver;
            return AnnotatedTypeEncoder.encodeAnnotationTypes(executable::getAnnotatedParameterTypes, receiver);
        }
    }

    public static final class AnnotatedReturnTypeComputer implements CustomFieldValueComputer {
        @Override
        public RecomputeFieldValue.ValueAvailability valueAvailability() {
            return RecomputeFieldValue.ValueAvailability.BeforeAnalysis;
        }

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            Executable executable = (Executable) receiver;
            return AnnotatedTypeEncoder.encodeAnnotationTypes(executable::getAnnotatedReturnType, receiver);
        }
    }

    public static final class AnnotatedExceptionTypesComputer implements CustomFieldValueComputer {
        @Override
        public RecomputeFieldValue.ValueAvailability valueAvailability() {
            return RecomputeFieldValue.ValueAvailability.BeforeAnalysis;
        }

        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            Executable executable = (Executable) receiver;
            return AnnotatedTypeEncoder.encodeAnnotationTypes(executable::getAnnotatedExceptionTypes, receiver);
        }
    }

    private static final class AnnotatedTypeEncoder {
        static Object encodeAnnotationTypes(Supplier<Object> supplier, Object receiver) {
            try {
                return supplier.get();
            } catch (InternalError e) {
                return e;
            } catch (LinkageError e) {
                if (NativeImageOptions.AllowIncompleteClasspath.getValue()) {
                    return e;
                }
                Executable culprit = (Executable) receiver;
                String message = "Encountered an error while processing annotated types for type: " + culprit.getDeclaringClass().getName() + ", executable: " + culprit.getName() + ". " +
                                "To avoid the issue at build time, use " + SubstrateOptionsParser.commandArgument(NativeImageOptions.AllowIncompleteClasspath, "+") + ". " +
                                "The error is then reported at runtime when these annotated types are first accessed.";
                throw new UnsupportedOperationException(message, e);
            }
        }

        static Object decodeAnnotationTypes(Object value) {
            if (value == null) {
                return null;
            } else if (value instanceof AnnotatedType || value instanceof AnnotatedType[]) {
                return value;
            } else if (value instanceof LinkageError) {
                throw (LinkageError) value;
            } else if (value instanceof InternalError) {
                throw (InternalError) value;
            } else {
                throw VMError.shouldNotReachHere("Unexpected value while decoding annotation types: " + value.toString());
            }
        }
    }
}
