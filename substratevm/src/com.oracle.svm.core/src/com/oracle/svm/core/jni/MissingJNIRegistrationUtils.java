/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jni;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.graalvm.nativeimage.MissingJNIRegistrationError;

import com.oracle.svm.configure.config.ConfigurationMemberInfo;
import com.oracle.svm.configure.config.ConfigurationType;
import com.oracle.svm.core.MissingRegistrationUtils;

public final class MissingJNIRegistrationUtils extends MissingRegistrationUtils {

    public static void reportClassAccess(String className) {
        var type = namedConfigurationType(className);
        type.setJniAccessible();
        MissingJNIRegistrationError exception = new MissingJNIRegistrationError(
                        jniMessage("access class", className, elementToJSON(type)),
                        Class.class, null, className, null);
        report(exception);
    }

    public static void reportFieldAccess(Class<?> declaringClass, String fieldName) {
        var type = getConfigurationType(declaringClass);
        type.setJniAccessible();
        addField(type, fieldName);
        MissingJNIRegistrationError exception = new MissingJNIRegistrationError(
                        jniMessage("access field", declaringClass.getTypeName() + "." + fieldName, elementToJSON(type)),
                        Field.class, declaringClass, fieldName, null);
        report(exception);
    }

    public static void reportMethodAccess(Class<?> declaringClass, String methodName, String signature) {
        ConfigurationType type = getConfigurationType(declaringClass);
        type.setJniAccessible();
        type.addMethod(methodName, signature, ConfigurationMemberInfo.ConfigurationMemberDeclaration.PRESENT);
        String json = elementToJSON(type);
        MissingJNIRegistrationError exception = new MissingJNIRegistrationError(
                        jniMessage("access method", declaringClass.getTypeName() + "." + methodName + signature, json),
                        Method.class, declaringClass, methodName, signature);
        report(exception);
    }

    private static String jniMessage(String failedAction, String elementDescriptor, String json) {
        return registrationMessage(failedAction, elementDescriptor, json, "reflectively", "reflection", "reflection");
    }

    private static void report(MissingJNIRegistrationError exception) {
        // GR-54504: get responsible class from anchor
        MissingRegistrationUtils.report(exception, null);
    }
}
