/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.agent.restrict;

import static com.oracle.svm.agent.Support.fromCString;
import static com.oracle.svm.agent.Support.getClassNameOr;
import static com.oracle.svm.agent.Support.getClassNameOrNull;
import static com.oracle.svm.agent.Support.handles;
import static com.oracle.svm.agent.Support.jniFunctions;
import static com.oracle.svm.agent.Support.jvmtiEnv;
import static com.oracle.svm.agent.Support.jvmtiFunctions;
import static com.oracle.svm.agent.Support.toCString;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.svm.agent.Agent;
import com.oracle.svm.agent.jvmti.JvmtiError;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

public class JniAccessVerifier extends AbstractAccessVerifier {

    public JniAccessVerifier(Configuration configuration, AccessAdvisor advisor) {
        super(configuration, advisor);
    }

    @SuppressWarnings("unused")
    public boolean verifyDefineClass(JNIEnvironment env, CCharPointer name, JNIObjectHandle loader, CCharPointer buf, int bufLen, JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(env, callerClass)) {
            return true;
        }
        try (CCharPointerHolder message = toCString(Agent.MESSAGE_PREFIX + "defining classes is not permitted.")) {
            // SecurityException seems most fitting from the exceptions allowed by the JNI spec
            beforeThrow(message);
            jniFunctions().getThrowNew().invoke(env, handles().javaLangSecurityException, message.get());
        }
        return false;
    }

    public boolean verifyFindClass(JNIEnvironment env, CCharPointer cname, JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(env, callerClass)) {
            return true;
        }
        String name = fromCString(cname);
        if (name != null) {
            if (!name.startsWith("[") && name.length() > 1) {
                name = "L" + name + ";"; // FindClass doesn't require those
            }
            if (configuration.get(name) != null) {
                return true;
            }
        }
        try (CCharPointerHolder message = toCString(Agent.MESSAGE_PREFIX + "configuration does not permit access to class: " + name)) {
            beforeThrow(message);
            jniFunctions().getThrowNew().invoke(env, handles().javaLangNoClassDefFoundError, message.get());
        }
        return false;
    }

    public boolean verifyGetMethodID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer cname, CCharPointer csignature, JNIMethodId result, JNIObjectHandle callerClass) {
        assert result.isNonNull();
        String name = fromCString(cname);
        if (accessAdvisor.shouldIgnoreJniMethodLookup(() -> (String) getClassNameOrNull(env, clazz), () -> name, () -> fromCString(csignature), () -> (String) getClassNameOrNull(env, callerClass))) {
            return true;
        }
        WordPointer declaringPtr = StackValue.get(WordPointer.class);
        if (jvmtiFunctions().GetMethodDeclaringClass().invoke(jvmtiEnv(), result, declaringPtr) == JvmtiError.JVMTI_ERROR_NONE) {
            JNIObjectHandle declaring = declaringPtr.read();
            if (isMethodAccessible(env, clazz, name, () -> fromCString(csignature), result, declaring)) {
                return true;
            }
        }
        try (CCharPointerHolder message = toCString(Agent.MESSAGE_PREFIX + "configuration does not permit access to method: " +
                        getClassNameOr(env, clazz, "(null)", "(?)") + "." + name + fromCString(csignature))) {

            beforeThrow(message);
            jniFunctions().getThrowNew().invoke(env, handles().javaLangNoSuchMethodError, message.get());
        }
        return false;
    }

    public boolean verifyGetFieldID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer cname,
                    @SuppressWarnings("unused") CCharPointer csignature, JNIFieldId result, JNIObjectHandle callerClass) {

        assert result.isNonNull();
        if (shouldApproveWithoutChecks(env, callerClass)) {
            return true;
        }
        // Check if the member in the declaring method is registered.
        WordPointer declaringPtr = StackValue.get(WordPointer.class);
        if (jvmtiFunctions().GetFieldDeclaringClass().invoke(jvmtiEnv(), clazz, result, declaringPtr) == JvmtiError.JVMTI_ERROR_NONE) {
            JNIObjectHandle declaring = declaringPtr.read();
            if (isFieldAccessible(env, clazz, () -> fromCString(cname), result, declaring)) {
                return true;
            }
        }
        try (CCharPointerHolder message = toCString(Agent.MESSAGE_PREFIX + "configuration does not permit access to field: " +
                        getClassNameOr(env, clazz, "(null)", "(?)") + "." + fromCString(cname))) {
            beforeThrow(message);
            jniFunctions().getThrowNew().invoke(env, handles().javaLangNoSuchFieldError, message.get());
        }
        return false;
    }

    private static void beforeThrow(@SuppressWarnings("unused") CCharPointerHolder message) {
        // System.err.println(fromCString(message.get()));
    }
}
