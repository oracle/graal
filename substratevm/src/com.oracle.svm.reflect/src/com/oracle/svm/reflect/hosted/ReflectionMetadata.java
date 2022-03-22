/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.hosted;

import static com.oracle.svm.core.meta.SharedField.LOC_UNINITIALIZED;

import java.lang.annotation.Annotation;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.reflect.ReflectionMetadataDecoder;
import com.oracle.svm.hosted.image.NativeImageCodeCache.ReflectionMetadataEncoderFactory;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.reflect.target.ReflectionMetadataDecoderImpl;
import com.oracle.svm.reflect.target.ReflectionMetadataEncoding;

import jdk.vm.ci.meta.JavaConstant;
import sun.reflect.annotation.TypeAnnotation;

@AutomaticFeature
class ReflectionMetadataFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(ReflectionMetadataEncoderFactory.class, new ReflectionMetadataEncoderImpl.Factory());
        ImageSingletons.add(ReflectionMetadataDecoder.class, new ReflectionMetadataDecoderImpl());
        ImageSingletons.add(ReflectionMetadataEncoding.class, new ReflectionMetadataEncoding());
    }
}

public class ReflectionMetadata {

    static class AnnotatedElementMetadata {
        final Annotation[] annotations;
        final TypeAnnotation[] typeAnnotations;

        AnnotatedElementMetadata(Annotation[] annotations, TypeAnnotation[] typeAnnotations) {
            this.annotations = annotations;
            this.typeAnnotations = typeAnnotations;
        }
    }

    static class ClassMetadata extends AnnotatedElementMetadata {
        final HostedType[] classes;
        final Object[] enclosingMethodInfo;
        final RecordComponentMetadata[] recordComponents;
        final HostedType[] permittedSubclasses;

        ClassMetadata(HostedType[] classes, Object[] enclosingMethodInfo, RecordComponentMetadata[] recordComponents, HostedType[] permittedSubclasses, Annotation[] annotations,
                        TypeAnnotation[] typeAnnotations) {
            super(annotations, typeAnnotations);
            this.classes = classes;
            this.enclosingMethodInfo = enclosingMethodInfo;
            this.recordComponents = recordComponents;
            this.permittedSubclasses = permittedSubclasses;
        }
    }

    static class AccessibleObjectMetadata extends AnnotatedElementMetadata {
        final boolean complete;
        final JavaConstant heapObject;
        final HostedType declaringType;
        final int modifiers;
        final String signature;

        AccessibleObjectMetadata(boolean complete, JavaConstant heapObject, HostedType declaringType, int modifiers, String signature, Annotation[] annotations, TypeAnnotation[] typeAnnotations) {
            super(annotations, typeAnnotations);
            this.complete = complete;
            this.heapObject = heapObject;
            this.declaringType = declaringType;
            this.modifiers = modifiers;
            this.signature = signature;
        }
    }

    static class FieldMetadata extends AccessibleObjectMetadata {
        final String name;
        final HostedType type;
        final boolean trustedFinal;
        final int offset;
        final String deletedReason;

        FieldMetadata(boolean complete, JavaConstant heapObject, HostedType declaringType, String name, HostedType type, int modifiers, boolean trustedFinal, String signature,
                        Annotation[] annotations, TypeAnnotation[] typeAnnotations, int offset, String deletedReason) {
            super(complete, heapObject, declaringType, modifiers, signature, annotations, typeAnnotations);
            this.name = name;
            this.type = type;
            this.trustedFinal = trustedFinal;
            this.offset = offset;
            this.deletedReason = deletedReason;
        }

        FieldMetadata(HostedType declaringType, String name, HostedType type, int modifiers, boolean trustedFinal, String signature, Annotation[] annotations, TypeAnnotation[] typeAnnotations,
                        int offset, String deletedReason) {
            this(true, null, declaringType, name, type, modifiers, trustedFinal, signature, annotations, typeAnnotations, offset, deletedReason);
        }

        FieldMetadata(JavaConstant heapObject, Annotation[] annotations, TypeAnnotation[] typeAnnotations) {
            this(true, heapObject, null, null, null, 0, false, null, annotations, typeAnnotations, LOC_UNINITIALIZED, null);
        }

        FieldMetadata(HostedType declaringType, String name) {
            this(false, null, declaringType, name, null, 0, false, null, null, null, LOC_UNINITIALIZED, null);
        }
    }

    static class ExecutableMetadata extends AccessibleObjectMetadata {
        final HostedType[] parameterTypes;
        final HostedType[] exceptionTypes;
        final Annotation[][] parameterAnnotations;
        final ReflectParameterMetadata[] reflectParameters;
        final JavaConstant accessor;

        ExecutableMetadata(boolean complete, JavaConstant heapObject, HostedType declaringType, HostedType[] parameterTypes, int modifiers, HostedType[] exceptionTypes, String signature,
                        Annotation[] annotations, Annotation[][] parameterAnnotations, TypeAnnotation[] typeAnnotations, ReflectParameterMetadata[] reflectParameters, JavaConstant accessor) {
            super(complete, heapObject, declaringType, modifiers, signature, annotations, typeAnnotations);
            this.parameterTypes = parameterTypes;
            this.exceptionTypes = exceptionTypes;
            this.parameterAnnotations = parameterAnnotations;
            this.reflectParameters = reflectParameters;
            this.accessor = accessor;
        }
    }

    static class MethodMetadata extends ExecutableMetadata {
        final boolean hiding;
        final String name;
        final HostedType returnType;
        final Object annotationDefault;

        MethodMetadata(boolean complete, boolean hiding, JavaConstant heapObject, HostedType declaringClass, String name, HostedType[] parameterTypes, int modifiers, HostedType returnType,
                        HostedType[] exceptionTypes, String signature, Annotation[] annotations, Annotation[][] parameterAnnotations, Object annotationDefault, TypeAnnotation[] typeAnnotations,
                        ReflectParameterMetadata[] reflectParameters, JavaConstant accessor) {
            super(complete, heapObject, declaringClass, parameterTypes, modifiers, exceptionTypes, signature, annotations, parameterAnnotations, typeAnnotations, reflectParameters, accessor);
            this.hiding = hiding;
            this.name = name;
            this.returnType = returnType;
            this.annotationDefault = annotationDefault;
        }

        MethodMetadata(HostedType declaringClass, String name, HostedType[] parameterTypes, int modifiers, HostedType returnType, HostedType[] exceptionTypes, String signature,
                        Annotation[] annotations, Annotation[][] parameterAnnotations, Object annotationDefault, TypeAnnotation[] typeAnnotations, ReflectParameterMetadata[] reflectParameters,
                        JavaConstant accessor) {
            this(true, false, null, declaringClass, name, parameterTypes, modifiers, returnType, exceptionTypes, signature, annotations, parameterAnnotations, annotationDefault, typeAnnotations,
                            reflectParameters,
                            accessor);
        }

        MethodMetadata(JavaConstant heapObject, Annotation[] annotations, Annotation[][] parameterAnnotations, Object annotationDefault, TypeAnnotation[] typeAnnotations,
                        ReflectParameterMetadata[] reflectParameters) {
            this(true, false, heapObject, null, null, null, 0, null, null, null, annotations, parameterAnnotations, annotationDefault, typeAnnotations, reflectParameters, null);
        }

        MethodMetadata(boolean hiding, HostedType declaringClass, String name, HostedType[] parameterTypes, int modifiers, HostedType returnType) {
            this(false, hiding, null, declaringClass, name, parameterTypes, modifiers, returnType, null, null, null, null, null, null, null, null);
        }
    }

    static class ConstructorMetadata extends ExecutableMetadata {

        ConstructorMetadata(boolean complete, JavaConstant heapObject, HostedType declaringClass, HostedType[] parameterTypes, int modifiers, HostedType[] exceptionTypes, String signature,
                        Annotation[] annotations, Annotation[][] parameterAnnotations, TypeAnnotation[] typeAnnotations, ReflectParameterMetadata[] reflectParameters, JavaConstant accessor) {
            super(complete, heapObject, declaringClass, parameterTypes, modifiers, exceptionTypes, signature, annotations, parameterAnnotations, typeAnnotations, reflectParameters, accessor);
        }

        ConstructorMetadata(HostedType declaringClass, HostedType[] parameterTypes, int modifiers, HostedType[] exceptionTypes, String signature, Annotation[] annotations,
                        Annotation[][] parameterAnnotations, TypeAnnotation[] typeAnnotations, ReflectParameterMetadata[] reflectParameters, JavaConstant accessor) {
            this(true, null, declaringClass, parameterTypes, modifiers, exceptionTypes, signature, annotations, parameterAnnotations, typeAnnotations, reflectParameters, accessor);
        }

        ConstructorMetadata(JavaConstant heapObject, Annotation[] annotations, Annotation[][] parameterAnnotations, TypeAnnotation[] typeAnnotations, ReflectParameterMetadata[] reflectParameters) {
            this(true, heapObject, null, null, 0, null, null, annotations, parameterAnnotations, typeAnnotations, reflectParameters, null);
        }

        ConstructorMetadata(HostedType declaringClass, HostedType[] parameterTypes, int modifiers) {
            this(false, null, declaringClass, parameterTypes, modifiers, null, null, null, null, null, null, null);
        }
    }

    static class RecordComponentMetadata extends AnnotatedElementMetadata {
        final HostedType declaringType;
        final String name;
        final HostedType type;
        final String signature;
        final JavaConstant accessor;

        RecordComponentMetadata(HostedType declaringType, String name, HostedType type, String signature, JavaConstant accessor, Annotation[] annotations, TypeAnnotation[] typeAnnotations) {
            super(annotations, typeAnnotations);
            this.declaringType = declaringType;
            this.name = name;
            this.type = type;
            this.signature = signature;
            this.accessor = accessor;
        }
    }

    static class ReflectParameterMetadata {
        final String name;
        final int modifiers;

        ReflectParameterMetadata(String name, int modifiers) {
            this.name = name;
            this.modifiers = modifiers;
        }
    }
}
