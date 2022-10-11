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
package com.oracle.svm.hosted.reflect;

import static com.oracle.svm.core.meta.SharedField.LOC_UNINITIALIZED;

import com.oracle.svm.hosted.annotation.AnnotationMemberValue;
import com.oracle.svm.hosted.annotation.AnnotationValue;
import com.oracle.svm.hosted.annotation.TypeAnnotationValue;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.vm.ci.meta.JavaConstant;

public class ReflectionMetadata {

    static class AnnotatedElementMetadata {
        final AnnotationValue[] annotations;
        final TypeAnnotationValue[] typeAnnotations;

        AnnotatedElementMetadata(AnnotationValue[] annotations, TypeAnnotationValue[] typeAnnotations) {
            this.annotations = annotations;
            this.typeAnnotations = typeAnnotations;
        }
    }

    static class ClassMetadata extends AnnotatedElementMetadata {
        final HostedType[] classes;
        final Object enclosingMethodInfo;
        final RecordComponentMetadata[] recordComponents;
        final HostedType[] permittedSubclasses;
        final int classAccessFlags;

        ClassMetadata(HostedType[] classes, Object enclosingMethodInfo, RecordComponentMetadata[] recordComponents, HostedType[] permittedSubclasses, int classAccessFlags,
                        AnnotationValue[] annotations, TypeAnnotationValue[] typeAnnotations) {
            super(annotations, typeAnnotations);
            this.classes = classes;
            this.enclosingMethodInfo = enclosingMethodInfo;
            this.recordComponents = recordComponents;
            this.permittedSubclasses = permittedSubclasses;
            this.classAccessFlags = classAccessFlags;
        }
    }

    static class AccessibleObjectMetadata extends AnnotatedElementMetadata {
        final boolean complete;
        final JavaConstant heapObject;
        final HostedType declaringType;
        final int modifiers;
        final String signature;

        AccessibleObjectMetadata(boolean complete, JavaConstant heapObject, HostedType declaringType, int modifiers, String signature, AnnotationValue[] annotations,
                        TypeAnnotationValue[] typeAnnotations) {
            super(annotations, typeAnnotations);
            this.complete = complete;
            this.heapObject = heapObject;
            this.declaringType = declaringType;
            this.modifiers = modifiers;
            this.signature = signature;
        }
    }

    static class FieldMetadata extends AccessibleObjectMetadata {
        final boolean hiding;
        final String name;
        final HostedType type;
        final boolean trustedFinal;
        final int offset;
        final String deletedReason;

        private FieldMetadata(boolean complete, boolean hiding, JavaConstant heapObject, HostedType declaringType, String name, HostedType type, int modifiers, boolean trustedFinal, String signature,
                        AnnotationValue[] annotations, TypeAnnotationValue[] typeAnnotations, int offset, String deletedReason) {
            super(complete, heapObject, declaringType, modifiers, signature, annotations, typeAnnotations);
            this.hiding = hiding;
            this.name = name;
            this.type = type;
            this.trustedFinal = trustedFinal;
            this.offset = offset;
            this.deletedReason = deletedReason;
        }

        /* Field registered for reflection */
        FieldMetadata(HostedType declaringType, String name, HostedType type, int modifiers, boolean trustedFinal, String signature, AnnotationValue[] annotations,
                        TypeAnnotationValue[] typeAnnotations, int offset, String deletedReason) {
            this(true, false, null, declaringType, name, type, modifiers, trustedFinal, signature, annotations, typeAnnotations, offset, deletedReason);
        }

        /* Field in heap */
        FieldMetadata(boolean registered, JavaConstant heapObject, AnnotationValue[] annotations, TypeAnnotationValue[] typeAnnotations) {
            this(registered, false, heapObject, null, null, null, 0, false, null, annotations, typeAnnotations, LOC_UNINITIALIZED, null);
        }

        /* Hiding field */
        FieldMetadata(HostedType declaringType, String name, HostedType type, int modifiers) {
            this(false, true, null, declaringType, name, type, modifiers, false, null, null, null, LOC_UNINITIALIZED, null);
        }

        /* Reachable field */
        FieldMetadata(HostedType declaringType, String name) {
            this(false, false, null, declaringType, name, null, 0, false, null, null, null, LOC_UNINITIALIZED, null);
        }
    }

    static class ExecutableMetadata extends AccessibleObjectMetadata {
        final HostedType[] parameterTypes;
        final HostedType[] exceptionTypes;
        final AnnotationValue[][] parameterAnnotations;
        final ReflectParameterMetadata[] reflectParameters;
        final JavaConstant accessor;

        ExecutableMetadata(boolean complete, JavaConstant heapObject, HostedType declaringType, HostedType[] parameterTypes, int modifiers, HostedType[] exceptionTypes, String signature,
                        AnnotationValue[] annotations, AnnotationValue[][] parameterAnnotations, TypeAnnotationValue[] typeAnnotations, ReflectParameterMetadata[] reflectParameters,
                        JavaConstant accessor) {
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
        final AnnotationMemberValue annotationDefault;

        private MethodMetadata(boolean complete, boolean hiding, JavaConstant heapObject, HostedType declaringClass, String name, HostedType[] parameterTypes, int modifiers, HostedType returnType,
                        HostedType[] exceptionTypes, String signature, AnnotationValue[] annotations, AnnotationValue[][] parameterAnnotations, AnnotationMemberValue annotationDefault,
                        TypeAnnotationValue[] typeAnnotations, ReflectParameterMetadata[] reflectParameters, JavaConstant accessor) {
            super(complete, heapObject, declaringClass, parameterTypes, modifiers, exceptionTypes, signature, annotations, parameterAnnotations, typeAnnotations, reflectParameters, accessor);
            this.hiding = hiding;
            this.name = name;
            this.returnType = returnType;
            this.annotationDefault = annotationDefault;
        }

        /* Method registered for reflection */
        MethodMetadata(HostedType declaringClass, String name, HostedType[] parameterTypes, int modifiers, HostedType returnType, HostedType[] exceptionTypes, String signature,
                        AnnotationValue[] annotations, AnnotationValue[][] parameterAnnotations, AnnotationMemberValue annotationDefault, TypeAnnotationValue[] typeAnnotations,
                        ReflectParameterMetadata[] reflectParameters, JavaConstant accessor) {
            this(true, false, null, declaringClass, name, parameterTypes, modifiers, returnType, exceptionTypes, signature, annotations, parameterAnnotations, annotationDefault, typeAnnotations,
                            reflectParameters, accessor);
        }

        /* Method in heap */
        MethodMetadata(boolean registered, JavaConstant heapObject, AnnotationValue[] annotations, AnnotationValue[][] parameterAnnotations, AnnotationMemberValue annotationDefault,
                        TypeAnnotationValue[] typeAnnotations, ReflectParameterMetadata[] reflectParameters) {
            this(registered, false, heapObject, null, null, null, 0, null, null, null, annotations, parameterAnnotations, annotationDefault, typeAnnotations, reflectParameters, null);
        }

        /* Hiding method */
        MethodMetadata(HostedType declaringClass, String name, HostedType[] parameterTypes, int modifiers, HostedType returnType) {
            this(false, true, null, declaringClass, name, parameterTypes, modifiers, returnType, null, null, null, null, null, null, null, null);
        }

        /* Reachable method */
        MethodMetadata(HostedType declaringClass, String name, HostedType[] parameterTypes) {
            this(false, false, null, declaringClass, name, parameterTypes, 0, null, null, null, null, null, null, null, null, null);
        }
    }

    static class ConstructorMetadata extends ExecutableMetadata {

        private ConstructorMetadata(boolean complete, JavaConstant heapObject, HostedType declaringClass, HostedType[] parameterTypes, int modifiers, HostedType[] exceptionTypes, String signature,
                        AnnotationValue[] annotations, AnnotationValue[][] parameterAnnotations, TypeAnnotationValue[] typeAnnotations, ReflectParameterMetadata[] reflectParameters,
                        JavaConstant accessor) {
            super(complete, heapObject, declaringClass, parameterTypes, modifiers, exceptionTypes, signature, annotations, parameterAnnotations, typeAnnotations, reflectParameters, accessor);
        }

        /* Constructor registered for reflection */
        ConstructorMetadata(HostedType declaringClass, HostedType[] parameterTypes, int modifiers, HostedType[] exceptionTypes, String signature, AnnotationValue[] annotations,
                        AnnotationValue[][] parameterAnnotations, TypeAnnotationValue[] typeAnnotations, ReflectParameterMetadata[] reflectParameters, JavaConstant accessor) {
            this(true, null, declaringClass, parameterTypes, modifiers, exceptionTypes, signature, annotations, parameterAnnotations, typeAnnotations, reflectParameters, accessor);
        }

        /* Constructor in heap */
        ConstructorMetadata(boolean registered, JavaConstant heapObject, AnnotationValue[] annotations, AnnotationValue[][] parameterAnnotations, TypeAnnotationValue[] typeAnnotations,
                        ReflectParameterMetadata[] reflectParameters) {
            this(registered, heapObject, null, null, 0, null, null, annotations, parameterAnnotations, typeAnnotations, reflectParameters, null);
        }

        /* Reachable constructor */
        ConstructorMetadata(HostedType declaringClass, HostedType[] parameterTypes) {
            this(false, null, declaringClass, parameterTypes, 0, null, null, null, null, null, null, null);
        }
    }

    static class RecordComponentMetadata extends AnnotatedElementMetadata {
        final HostedType declaringType;
        final String name;
        final HostedType type;
        final String signature;
        final JavaConstant accessor;

        RecordComponentMetadata(HostedType declaringType, String name, HostedType type, String signature, JavaConstant accessor, AnnotationValue[] annotations, TypeAnnotationValue[] typeAnnotations) {
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
