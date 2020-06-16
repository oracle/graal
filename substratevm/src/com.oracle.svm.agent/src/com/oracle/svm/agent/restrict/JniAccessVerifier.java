/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.jvmtiagentbase.Support.fromCString;
import static com.oracle.svm.jvmtiagentbase.Support.getClassNameOr;
import static com.oracle.svm.jvmtiagentbase.Support.jniFunctions;
import static com.oracle.svm.jvmtiagentbase.Support.jvmtiEnv;
import static com.oracle.svm.jvmtiagentbase.Support.jvmtiFunctions;
import static com.oracle.svm.jvmtiagentbase.Support.toCString;
import static com.oracle.svm.configure.trace.LazyValueUtils.lazyGet;
import static com.oracle.svm.configure.trace.LazyValueUtils.lazyValue;

import org.graalvm.compiler.phases.common.LazyValue;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.svm.agent.NativeImageAgent;
import com.oracle.svm.configure.config.ConfigurationMethod;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiError;

import jdk.vm.ci.meta.MetaUtil;

/**
 * In restriction mode, decides whether to permit or deny individual accesses via JNI, using
 * {@link AccessAdvisor} for additional exemptions from its own rules, such as system classes.
 */
public class JniAccessVerifier extends AbstractAccessVerifier {
    private final TypeAccessChecker typeAccessChecker;
    private final TypeAccessChecker reflectTypeAccessChecker;
    private final NativeImageAgent agent;

    public JniAccessVerifier(TypeAccessChecker typeAccessChecker, TypeAccessChecker reflectTypeAccessChecker, AccessAdvisor advisor, NativeImageAgent agent) {
        super(advisor);
        this.typeAccessChecker = typeAccessChecker;
        this.reflectTypeAccessChecker = reflectTypeAccessChecker;
        this.agent = agent;
    }

    @SuppressWarnings("unused")
    public boolean verifyDefineClass(JNIEnvironment env, CCharPointer name, JNIObjectHandle loader, CCharPointer buf, int bufLen, JNIObjectHandle callerClass) {
        LazyValue<String> javaName = lazyConvertFindClassName(name);
        if (shouldApproveWithoutChecks(javaName, lazyClassNameOrNull(env, callerClass))) {
            return true;
        }
        try (CCharPointerHolder message = toCString(NativeImageAgent.MESSAGE_PREFIX + "defining classes is not permitted: " + javaName.get())) {
            // SecurityException seems most fitting from the exceptions allowed by the JNI spec
            jniFunctions().getThrowNew().invoke(env, agent.handles().javaLangSecurityException, message.get());
        }
        return false;
    }

    public boolean verifyFindClass(JNIEnvironment env, CCharPointer name, JNIObjectHandle callerClass) {
        LazyValue<String> javaName = lazyConvertFindClassName(name);
        if (shouldApproveWithoutChecks(javaName, lazyClassNameOrNull(env, callerClass))) {
            return true;
        }
        if (javaName.get() != null && typeAccessChecker.getConfiguration().get(javaName.get()) != null) {
            return true;
        }
        try (CCharPointerHolder message = toCString(NativeImageAgent.MESSAGE_PREFIX + "configuration does not permit access to class: " + javaName.get())) {
            jniFunctions().getThrowNew().invoke(env, agent.handles().javaLangNoClassDefFoundError, message.get());
        }
        return false;
    }

    private static LazyValue<String> lazyConvertFindClassName(CCharPointer name) {
        return lazyGet(() -> {
            String s = fromCString(name);
            if (s != null) {
                if (!s.startsWith("[") && s.length() > 1) {
                    s = "L" + s + ";";
                }
                try {
                    return MetaUtil.internalNameToJava(s, true, true);
                } catch (Exception ignored) {
                    // likely malformed input from the observed application
                }
            }
            return null;
        });
    }

    public boolean verifyGetMethodID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer cname, CCharPointer csignature, JNIMethodId result, JNIObjectHandle callerClass) {
        LazyValue<String> clazzName = lazyClassNameOrNull(env, clazz);
        LazyValue<String> callerClassName = lazyClassNameOrNull(env, callerClass);
        if (shouldApproveWithoutChecks(clazzName, callerClassName)) {
            return true;
        }
        assert result.isNonNull();
        String name = fromCString(cname);
        if (accessAdvisor.shouldIgnoreJniMethodLookup(clazzName, lazyValue(name), lazyGet(() -> fromCString(csignature)), callerClassName)) {
            return true;
        }
        WordPointer declaringPtr = StackValue.get(WordPointer.class);
        if (jvmtiFunctions().GetMethodDeclaringClass().invoke(jvmtiEnv(), result, declaringPtr) == JvmtiError.JVMTI_ERROR_NONE) {
            JNIObjectHandle declaring = declaringPtr.read();
            if (typeAccessChecker.isMethodAccessible(env, clazz, name, () -> fromCString(csignature), result, declaring)) {
                return true;
            }
        }
        try (CCharPointerHolder message = toCString(NativeImageAgent.MESSAGE_PREFIX + "configuration does not permit access to method: " +
                        getClassNameOr(env, clazz, "(null)", "(?)") + "." + name + fromCString(csignature))) {

            jniFunctions().getThrowNew().invoke(env, agent.handles().javaLangNoSuchMethodError, message.get());
        }
        return false;
    }

    public boolean verifyGetFieldID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer cname,
                    @SuppressWarnings("unused") CCharPointer csignature, JNIFieldId result, JNIObjectHandle callerClass) {

        assert result.isNonNull();
        if (shouldApproveWithoutChecks(env, clazz, callerClass)) {
            return true;
        }
        // Check if the member in the declaring method is registered.
        WordPointer declaringPtr = StackValue.get(WordPointer.class);
        if (jvmtiFunctions().GetFieldDeclaringClass().invoke(jvmtiEnv(), clazz, result, declaringPtr) == JvmtiError.JVMTI_ERROR_NONE) {
            JNIObjectHandle declaring = declaringPtr.read();
            if (typeAccessChecker.isFieldAccessible(env, clazz, () -> fromCString(cname), result, declaring)) {
                return true;
            }
        }
        try (CCharPointerHolder message = toCString(NativeImageAgent.MESSAGE_PREFIX + "configuration does not permit access to field: " +
                        getClassNameOr(env, clazz, "(null)", "(?)") + "." + fromCString(cname))) {
            jniFunctions().getThrowNew().invoke(env, agent.handles().javaLangNoSuchFieldError, message.get());
        }
        return false;
    }

    public boolean verifyThrowNew(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle callerClass) {
        LazyValue<String> callerClassName = lazyClassNameOrNull(env, callerClass);
        if (shouldApproveWithoutChecks(lazyClassNameOrNull(env, clazz), callerClassName)) {
            return true;
        }
        String name = ConfigurationMethod.CONSTRUCTOR_NAME;
        String signature = "(Ljava/lang/String;)V";
        if (accessAdvisor.shouldIgnoreJniMethodLookup(lazyClassNameOrNull(env, clazz), lazyValue(name), lazyValue(signature), callerClassName)) {
            return true;
        }
        JNIMethodId result;
        try (CCharPointerHolder cname = toCString(name); CCharPointerHolder csignature = toCString(signature)) {
            result = jniFunctions().getGetMethodID().invoke(env, clazz, cname.get(), csignature.get());
            // NOTE: GetMethodID() can have initialized `clazz` as a side effect
        }
        return result.isNull() || typeAccessChecker.isMethodAccessible(env, clazz, name, () -> signature, result, clazz);
    }

    public boolean verifyFromReflectedMethod(JNIEnvironment env, JNIObjectHandle declaring, String name, String signature, JNIMethodId result, JNIObjectHandle callerClass) {
        assert result.isNonNull();
        if (shouldApproveWithoutChecks(env, declaring, callerClass)) {
            return true;
        }
        return typeAccessChecker.isMethodAccessible(env, declaring, name, () -> signature, result, declaring);
    }

    public boolean verifyFromReflectedField(JNIEnvironment env, JNIObjectHandle declaring, String name, JNIFieldId result, JNIObjectHandle callerClass) {
        assert result.isNonNull();
        if (shouldApproveWithoutChecks(env, declaring, callerClass)) {
            return true;
        }
        return typeAccessChecker.isFieldAccessible(env, declaring, () -> name, result, declaring);
    }

    public boolean verifyToReflectedMethod(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle declaring, JNIMethodId methodId, String name, String signature, JNIObjectHandle callerClass) {
        assert methodId.isNonNull();
        if (reflectTypeAccessChecker == null) {
            return true;
        }
        if (shouldApproveWithoutChecks(env, clazz, callerClass)) {
            return true;
        }
        return reflectTypeAccessChecker.isMethodAccessible(env, clazz, name, () -> signature, methodId, declaring);
    }

    public boolean verifyToReflectedField(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle declaring, String name, JNIFieldId fieldId, JNIObjectHandle callerClass) {
        assert fieldId.isNonNull();
        if (reflectTypeAccessChecker == null) {
            return true;
        }
        if (shouldApproveWithoutChecks(env, clazz, callerClass)) {
            return true;
        }
        return reflectTypeAccessChecker.isFieldAccessible(env, clazz, () -> name, fieldId, declaring);
    }

    public boolean verifyNewObjectArray(JNIEnvironment env, JNIObjectHandle arrayClass, JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(env, arrayClass, callerClass)) {
            return true;
        }
        return typeAccessChecker.getType(arrayClass) != null;
    }
}
