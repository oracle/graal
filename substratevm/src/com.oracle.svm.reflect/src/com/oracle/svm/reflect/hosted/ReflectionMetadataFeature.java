/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

//Checkstyle: allow reflection

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import org.graalvm.nativeimage.hosted.Feature;

import org.graalvm.util.GuardedAnnotationAccess;
import com.oracle.svm.core.annotate.AutomaticFeature;

/** Pre-load reflection metadata. */
@AutomaticFeature
public class ReflectionMetadataFeature implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(ReflectionMetadataFeature::replacer);
    }

    private static Object replacer(Object original) {

        /*
         * Ensure that the generic info and annotations data structures are initialized. By calling
         * the methods that access the metadata we ensure that the corresponding data structures are
         * pre-loaded and cached in the native image heap, therefore no field value recomputation is
         * required.
         */

        if (original instanceof Method) {
            Method method = (Method) original;
            method.getGenericReturnType();
        }

        if (original instanceof Executable) {
            Executable executable = (Executable) original;
            executable.getGenericParameterTypes();
            executable.getGenericExceptionTypes();
            executable.getParameters();
        }

        if (original instanceof Field) {
            Field field = (Field) original;
            field.getGenericType();
        }

        if (original instanceof AccessibleObject) {
            AccessibleObject accessibleObject = (AccessibleObject) original;
            GuardedAnnotationAccess.getDeclaredAnnotations(accessibleObject);
        }

        if (original instanceof Parameter) {
            Parameter parameter = (Parameter) original;
            parameter.getType();
        }

        return original;
    }
}
