/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect.target;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.configure.RuntimeConditionSet;
import com.oracle.svm.core.util.VMError;

public final class ReflectionObjectFactory {
    public static final int FIELD_OFFSET_NONE = 0;

    public static Field newField(RuntimeConditionSet conditions, Class<?> declaringClass, String name, Class<?> type, int modifiers,
                    boolean trustedFinal, String signature, byte[] annotations, int offset, String deletedReason, byte[] typeAnnotations) {
        Target_java_lang_reflect_Field field = new Target_java_lang_reflect_Field();
        field.constructor(declaringClass, name, type, modifiers, trustedFinal, -1, signature, annotations);
        field.offset = offset;
        field.deletedReason = deletedReason;
        Target_java_lang_reflect_AccessibleObject accessibleObject = SubstrateUtil.cast(field, Target_java_lang_reflect_AccessibleObject.class);
        accessibleObject.typeAnnotations = typeAnnotations;
        accessibleObject.conditions = conditions;
        return SubstrateUtil.cast(field, Field.class);
    }

    public static Method newMethod(RuntimeConditionSet conditions, Class<?> declaringClass, String name, Class<?>[] parameterTypes, Class<?> returnType, Class<?>[] exceptionTypes, int modifiers,
                    String signature, byte[] annotations, byte[] parameterAnnotations, byte[] annotationDefault, Object accessor, byte[] rawParameters,
                    byte[] typeAnnotations, int layerId) {
        Target_java_lang_reflect_Method method = new Target_java_lang_reflect_Method();
        method.constructor(declaringClass, name, parameterTypes, returnType, exceptionTypes, modifiers, -1, signature, annotations, parameterAnnotations, annotationDefault);
        method.methodAccessorFromMetadata = (Target_jdk_internal_reflect_MethodAccessor) accessor;
        SubstrateUtil.cast(method, Target_java_lang_reflect_Executable.class).rawParameters = rawParameters;
        Target_java_lang_reflect_AccessibleObject accessibleObject = SubstrateUtil.cast(method, Target_java_lang_reflect_AccessibleObject.class);
        accessibleObject.typeAnnotations = typeAnnotations;
        accessibleObject.conditions = conditions;
        method.layerId = layerId;
        return SubstrateUtil.cast(method, Method.class);
    }

    public static Constructor<?> newConstructor(RuntimeConditionSet conditions, Class<?> declaringClass, Class<?>[] parameterTypes, Class<?>[] exceptionTypes, int modifiers, String signature,
                    byte[] annotations, byte[] parameterAnnotations, Object accessor, byte[] rawParameters, byte[] typeAnnotations) {
        Target_java_lang_reflect_Constructor ctor = new Target_java_lang_reflect_Constructor();
        ctor.constructor(declaringClass, parameterTypes, exceptionTypes, modifiers, -1, signature, annotations, parameterAnnotations);
        ctor.constructorAccessorFromMetadata = (Target_jdk_internal_reflect_ConstructorAccessor) accessor;
        SubstrateUtil.cast(ctor, Target_java_lang_reflect_Executable.class).rawParameters = rawParameters;
        Target_java_lang_reflect_AccessibleObject accessibleObject = SubstrateUtil.cast(ctor, Target_java_lang_reflect_AccessibleObject.class);
        accessibleObject.typeAnnotations = typeAnnotations;
        accessibleObject.conditions = conditions;
        return SubstrateUtil.cast(ctor, Constructor.class);
    }

    public static RecordComponent newRecordComponent(Class<?> declaringClass, String name, Class<?> type, String signature, byte[] annotations, byte[] typeAnnotations) {
        Target_java_lang_reflect_RecordComponent rc = new Target_java_lang_reflect_RecordComponent();
        rc.clazz = declaringClass;
        rc.name = name;
        rc.type = type;
        rc.signature = signature;
        try {
            rc.accessor = declaringClass.getDeclaredMethod(name);
        } catch (NoSuchMethodException e) {
            throw VMError.shouldNotReachHere("Record component accessors should have been registered by the analysis.");
        }
        rc.annotations = annotations;
        rc.typeAnnotations = typeAnnotations;
        return SubstrateUtil.cast(rc, RecordComponent.class);
    }

    private ReflectionObjectFactory() {
    }

    public static Parameter newParameter(Executable executable, int i, String name, int modifiers) {
        Target_java_lang_reflect_Parameter parameter = new Target_java_lang_reflect_Parameter();
        parameter.constructor(name, modifiers, executable, i);
        return SubstrateUtil.cast(parameter, Parameter.class);
    }
}
