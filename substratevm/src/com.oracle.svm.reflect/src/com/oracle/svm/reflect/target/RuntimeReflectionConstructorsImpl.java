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
package com.oracle.svm.reflect.target;

// Checkstyle: stop
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
// Checkstyle: resume

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.reflect.RuntimeReflectionConstructors;

public class RuntimeReflectionConstructorsImpl implements RuntimeReflectionConstructors {
    @Override
    public Method newMethod(Class<?> declaringClass, String name, Class<?>[] parameterTypes, Class<?> returnType, Class<?>[] checkedExceptions, int modifiers, String signature,
                    byte[] annotations, byte[] parameterAnnotations, byte[] annotationDefault, byte[] typeAnnotations, String[] parameterNames, int[] parameterModifiers) {
        Target_java_lang_reflect_Method method = new Target_java_lang_reflect_Method();
        method.constructor(declaringClass, name, parameterTypes, returnType, checkedExceptions, modifiers, -1, signature, annotations, parameterAnnotations, annotationDefault);
        fillParameters(SubstrateUtil.cast(method, Target_java_lang_reflect_Executable.class), parameterNames, parameterModifiers);
        SubstrateUtil.cast(method, Target_java_lang_reflect_Executable.class).typeAnnotations = typeAnnotations;
        return SubstrateUtil.cast(method, Method.class);
    }

    @Override
    public Constructor<?> newConstructor(Class<?> declaringClass, Class<?>[] parameterTypes, Class<?>[] checkedExceptions, int modifiers, String signature,
                    byte[] annotations, byte[] parameterAnnotations, byte[] typeAnnotations, String[] parameterNames, int[] parameterModifiers) {
        Target_java_lang_reflect_Constructor cons = new Target_java_lang_reflect_Constructor();
        cons.constructor(declaringClass, parameterTypes, checkedExceptions, modifiers, -1, signature, annotations, parameterAnnotations);
        fillParameters(SubstrateUtil.cast(cons, Target_java_lang_reflect_Executable.class), parameterNames, parameterModifiers);
        SubstrateUtil.cast(cons, Target_java_lang_reflect_Executable.class).typeAnnotations = typeAnnotations;
        return SubstrateUtil.cast(cons, Constructor.class);
    }

    private static void fillParameters(Target_java_lang_reflect_Executable executable, String[] parameterNames, int[] parameterModifiers) {
        if (parameterNames != null && parameterModifiers != null) {
            executable.hasRealParameterData = true;
            assert parameterNames.length == parameterModifiers.length;
            executable.parameters = new Target_java_lang_reflect_Parameter[parameterNames.length];
            for (int i = 0; i < parameterNames.length; ++i) {
                executable.parameters[i] = new Target_java_lang_reflect_Parameter();
                executable.parameters[i].constructor(parameterNames[i], parameterModifiers[i], executable, i);
            }
        } else {
            executable.hasRealParameterData = false;
        }
    }
}
