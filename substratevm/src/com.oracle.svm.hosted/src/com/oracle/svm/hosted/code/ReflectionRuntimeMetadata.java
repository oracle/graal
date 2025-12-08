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
package com.oracle.svm.hosted.code;

import static com.oracle.svm.core.meta.SharedField.LOC_UNINITIALIZED;

import com.oracle.svm.core.configure.RuntimeDynamicAccessMetadata;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.TypeAnnotationValue;
import jdk.vm.ci.meta.JavaConstant;

final class ReflectionRuntimeMetadata {

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
        final HostedType[] nestMembers;
        final JavaConstant[] signers;
        final int flags;

        ClassMetadata(HostedType[] classes, Object enclosingMethodInfo, RecordComponentMetadata[] recordComponents, HostedType[] permittedSubclasses, HostedType[] nestMembers, JavaConstant[] signers,
                        int flags, AnnotationValue[] annotations, TypeAnnotationValue[] typeAnnotations) {
            super(annotations, typeAnnotations);
            this.classes = classes;
            this.enclosingMethodInfo = enclosingMethodInfo;
            this.recordComponents = recordComponents;
            this.permittedSubclasses = permittedSubclasses;
            this.nestMembers = nestMembers;
            this.signers = signers;
            this.flags = flags;
        }
    }

    static class AccessibleObjectMetadata extends AnnotatedElementMetadata {
        final RuntimeDynamicAccessMetadata dynamicAccessMetadata;
        final boolean complete;
        final boolean negative;
        final JavaConstant heapObject;
        final HostedType declaringType;
        final int modifiers;
        final String signature;

        AccessibleObjectMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, boolean complete, boolean negative, JavaConstant heapObject, HostedType declaringType, int modifiers,
                        String signature,
                        AnnotationValue[] annotations,
                        TypeAnnotationValue[] typeAnnotations) {
            super(annotations, typeAnnotations);
            this.dynamicAccessMetadata = dynamicAccessMetadata;
            this.complete = complete;
            this.negative = negative;
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

        private FieldMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, boolean complete, boolean negative, boolean hiding, JavaConstant heapObject, HostedType declaringType, String name,
                        HostedType type,
                        int modifiers, boolean trustedFinal,
                        String signature, AnnotationValue[] annotations, TypeAnnotationValue[] typeAnnotations, int offset, String deletedReason) {
            super(dynamicAccessMetadata, complete, negative, heapObject, declaringType, modifiers, signature, annotations, typeAnnotations);
            this.hiding = hiding;
            this.name = name;
            this.type = type;
            this.trustedFinal = trustedFinal;
            this.offset = offset;
            this.deletedReason = deletedReason;
        }

        /* Field registered for reflection */
        FieldMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, HostedType declaringType, String name, HostedType type, int modifiers, boolean trustedFinal, String signature,
                        AnnotationValue[] annotations,
                        TypeAnnotationValue[] typeAnnotations, int offset, String deletedReason) {
            this(dynamicAccessMetadata, true, false, false, null, declaringType, name, type, modifiers, trustedFinal, signature, annotations, typeAnnotations, offset, deletedReason);
        }

        /* Field in heap */
        FieldMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, boolean registered, JavaConstant heapObject, AnnotationValue[] annotations, TypeAnnotationValue[] typeAnnotations) {
            this(dynamicAccessMetadata, registered, false, false, heapObject, null, null, null, 0, false, null, annotations, typeAnnotations, LOC_UNINITIALIZED, null);
        }

        /* Hiding field */
        FieldMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, HostedType declaringType, String name, HostedType type, int modifiers) {
            this(dynamicAccessMetadata, false, false, true, null, declaringType, name, type, modifiers, false, null, null, null, LOC_UNINITIALIZED, null);
        }

        /* Reachable or negative query field */
        FieldMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, HostedType declaringType, String name, boolean negative) {
            this(dynamicAccessMetadata, false, negative, false, null, declaringType, name, null, 0, false, null, null, null, LOC_UNINITIALIZED, null);
        }
    }

    static class ExecutableMetadata extends AccessibleObjectMetadata {
        final Object[] parameterTypes;
        final HostedType[] exceptionTypes;
        final AnnotationValue[][] parameterAnnotations;
        final Object reflectParameters;
        final JavaConstant accessor;

        ExecutableMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, boolean complete, boolean negative, JavaConstant heapObject, HostedType declaringType, Object[] parameterTypes,
                        int modifiers,
                        HostedType[] exceptionTypes, String signature,
                        AnnotationValue[] annotations, AnnotationValue[][] parameterAnnotations, TypeAnnotationValue[] typeAnnotations, Object reflectParameters,
                        JavaConstant accessor) {
            super(dynamicAccessMetadata, complete, negative, heapObject, declaringType, modifiers, signature, annotations, typeAnnotations);

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

        private MethodMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, boolean complete, boolean negative, boolean hiding, JavaConstant heapObject, HostedType declaringClass, String name,
                        Object[] parameterTypes, int modifiers,
                        HostedType returnType, HostedType[] exceptionTypes, String signature, AnnotationValue[] annotations, AnnotationValue[][] parameterAnnotations,
                        Object annotationDefault, TypeAnnotationValue[] typeAnnotations, Object reflectParameters, JavaConstant accessor) {
            super(dynamicAccessMetadata, complete, negative, heapObject, declaringClass, parameterTypes, modifiers, exceptionTypes, signature, annotations, parameterAnnotations, typeAnnotations,
                            reflectParameters,
                            accessor);
            this.hiding = hiding;
            this.name = name;
            this.returnType = returnType;
            this.annotationDefault = annotationDefault;
        }

        /* Method registered for reflection */
        MethodMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, HostedType declaringClass, String name, HostedType[] parameterTypes, int modifiers, HostedType returnType,
                        HostedType[] exceptionTypes,
                        String signature,
                        AnnotationValue[] annotations, AnnotationValue[][] parameterAnnotations, Object annotationDefault, TypeAnnotationValue[] typeAnnotations,
                        Object reflectParameters, JavaConstant accessor) {
            this(dynamicAccessMetadata, true, false, false, null, declaringClass, name, parameterTypes, modifiers, returnType, exceptionTypes, signature, annotations, parameterAnnotations,
                            annotationDefault,
                            typeAnnotations, reflectParameters, accessor);
        }

        /* Method in heap */
        MethodMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, boolean registered, JavaConstant heapObject, AnnotationValue[] annotations, AnnotationValue[][] parameterAnnotations,
                        Object annotationDefault,
                        TypeAnnotationValue[] typeAnnotations, Object reflectParameters) {
            this(dynamicAccessMetadata, registered, false, false, heapObject, null, null, null, 0, null, null, null, annotations, parameterAnnotations, annotationDefault,
                            typeAnnotations,
                            reflectParameters, null);
        }

        /* Hiding method */
        MethodMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, HostedType declaringClass, String name, HostedType[] parameterTypes, int modifiers, HostedType returnType) {
            this(dynamicAccessMetadata, false, false, true, null, declaringClass, name, parameterTypes, modifiers, returnType, null, null, null, null, null, null, null, null);
        }

        /* Reachable method */
        MethodMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, HostedType declaringClass, String name, String[] parameterTypeNames) {
            this(dynamicAccessMetadata, false, false, false, null, declaringClass, name, parameterTypeNames, 0, null, null, null, null, null, null, null, null, null);
        }

        /* Negative query method */
        MethodMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, HostedType declaringClass, String name, HostedType[] parameterTypes) {
            this(dynamicAccessMetadata, false, true, false, null, declaringClass, name, parameterTypes, 0, null, null, null, null, null, null, null, null, null);
        }
    }

    static class ConstructorMetadata extends ExecutableMetadata {

        private ConstructorMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, boolean complete, boolean negative, JavaConstant heapObject, HostedType declaringClass, Object[] parameterTypes,
                        int modifiers,
                        HostedType[] exceptionTypes,
                        String signature, AnnotationValue[] annotations, AnnotationValue[][] parameterAnnotations, TypeAnnotationValue[] typeAnnotations, Object reflectParameters,
                        JavaConstant accessor) {
            super(dynamicAccessMetadata, complete, negative, heapObject, declaringClass, parameterTypes, modifiers, exceptionTypes, signature, annotations, parameterAnnotations, typeAnnotations,
                            reflectParameters,
                            accessor);
        }

        /* Constructor registered for reflection */
        ConstructorMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, HostedType declaringClass, HostedType[] parameterTypes, int modifiers, HostedType[] exceptionTypes, String signature,
                        AnnotationValue[] annotations,
                        AnnotationValue[][] parameterAnnotations, TypeAnnotationValue[] typeAnnotations, Object reflectParameters, JavaConstant accessor) {
            this(dynamicAccessMetadata, true, false, null, declaringClass, parameterTypes, modifiers, exceptionTypes, signature, annotations, parameterAnnotations, typeAnnotations, reflectParameters,
                            accessor);
        }

        /* Constructor in heap */
        ConstructorMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, boolean registered, JavaConstant heapObject, AnnotationValue[] annotations, AnnotationValue[][] parameterAnnotations,
                        TypeAnnotationValue[] typeAnnotations,
                        Object reflectParameters) {
            this(dynamicAccessMetadata, registered, false, heapObject, null, null, 0, null, null, annotations, parameterAnnotations, typeAnnotations, reflectParameters, null);
        }

        /* Reachable constructor */
        ConstructorMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, HostedType declaringClass, String[] parameterTypeNames) {
            this(dynamicAccessMetadata, false, false, null, declaringClass, parameterTypeNames, 0, null, null, null, null, null, null, null);
        }

        /* Negative query constructor */
        ConstructorMetadata(RuntimeDynamicAccessMetadata dynamicAccessMetadata, HostedType declaringClass, HostedType[] parameterTypes) {
            this(dynamicAccessMetadata, false, true, null, declaringClass, parameterTypes, 0, null, null, null, null, null, null, null);
        }
    }

    static class RecordComponentMetadata extends AnnotatedElementMetadata {
        final HostedType declaringType;
        final String name;
        final HostedType type;
        final String signature;

        RecordComponentMetadata(HostedType declaringType, String name, HostedType type, String signature, AnnotationValue[] annotations, TypeAnnotationValue[] typeAnnotations) {
            super(annotations, typeAnnotations);
            this.declaringType = declaringType;
            this.name = name;
            this.type = type;
            this.signature = signature;
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

    private ReflectionRuntimeMetadata() {
    }
}
