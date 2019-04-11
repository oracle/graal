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

import static com.oracle.svm.agent.Support.clearException;
import static com.oracle.svm.agent.Support.fromCString;
import static com.oracle.svm.agent.Support.fromJniString;
import static com.oracle.svm.agent.Support.getClassNameOr;
import static com.oracle.svm.agent.Support.getClassNameOrNull;
import static com.oracle.svm.agent.Support.handles;
import static com.oracle.svm.agent.Support.jniFunctions;
import static com.oracle.svm.agent.Support.jvmtiEnv;
import static com.oracle.svm.agent.Support.jvmtiFunctions;
import static com.oracle.svm.agent.Support.toCString;
import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;
import static org.graalvm.word.WordFactory.nullPointer;

import java.util.function.Supplier;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.svm.agent.Agent;
import com.oracle.svm.agent.Support.WordPredicate;
import com.oracle.svm.agent.Support.WordSupplier;
import com.oracle.svm.agent.jvmti.JvmtiError;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

import jdk.vm.ci.meta.MetaUtil;

public class ReflectAccessVerifier extends AbstractAccessVerifier {

    public ReflectAccessVerifier(Configuration configuration, AccessAdvisor advisor) {
        super(configuration, advisor);
    }

    public boolean verifyForName(JNIEnvironment env, JNIObjectHandle callerClass, JNIObjectHandle name) {
        if (shouldApproveWithoutChecks(env, callerClass)) {
            return true;
        }
        String className = fromJniString(env, name);
        if (className != null && configuration.get(MetaUtil.toInternalName(className)) != null) {
            return true;
        }
        try (CCharPointerHolder message = toCString(Agent.MESSAGE_PREFIX + "configuration does not permit access to class: " + className)) {
            beforeThrow(message);
            jniFunctions().getThrowNew().invoke(env, handles().javaLangClassNotFoundException, message.get());
        }
        return false;
    }

    public boolean verifyGetField(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle name, JNIObjectHandle result, JNIObjectHandle declaring, JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(env, callerClass)) {
            return true;
        }
        JNIFieldId field = jniFunctions().getFromReflectedField().invoke(env, result);
        if (field.isNonNull() && isFieldAccessible(env, clazz, () -> fromJniString(env, name), field, declaring)) {
            return true;
        }
        try (CCharPointerHolder message = toCString(Agent.MESSAGE_PREFIX + "configuration does not permit access to field: " +
                        getClassNameOr(env, clazz, "(null)", "(?)") + "." + fromJniString(env, name))) {
            beforeThrow(message);
            jniFunctions().getThrowNew().invoke(env, handles().javaLangNoSuchFieldException, message.get());
        }
        return false;
    }

    public boolean verifyGetMethod(JNIEnvironment env, JNIObjectHandle clazz, String name, Object paramTypesArray, JNIObjectHandle result, JNIObjectHandle declaring, JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(env, callerClass)) {
            return true;
        }
        JNIMethodId method = jniFunctions().getFromReflectedMethod().invoke(env, result);
        return verifyGetMethod0(env, clazz, name, () -> asInternalSignature(paramTypesArray), method, declaring);
    }

    public boolean verifyGetConstructor(JNIEnvironment env, JNIObjectHandle clazz, Object paramTypesArray, JNIObjectHandle result, JNIObjectHandle callerClass) {
        return verifyGetMethod(env, clazz, ConfigurationMethod.CONSTRUCTOR_NAME, paramTypesArray, result, clazz, callerClass);
    }

    public boolean verifyNewInstance(JNIEnvironment env, JNIObjectHandle clazz, String name, String signature, JNIMethodId result, JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(env, callerClass)) {
            return true;
        }
        return verifyGetMethod0(env, clazz, name, () -> signature, result, clazz);
    }

    public boolean verifyGetEnclosingMethod(JNIEnvironment env, JNIObjectHandle clazz, String name, String signature, JNIObjectHandle result, JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(env, callerClass)) {
            return true;
        }
        JNIMethodId method = jniFunctions().getFromReflectedMethod().invoke(env, result);
        return verifyGetMethod0(env, clazz, name, () -> signature, method, clazz);
    }

    private boolean verifyGetMethod0(JNIEnvironment env, JNIObjectHandle clazz, String name, Supplier<String> signature, JNIMethodId method, JNIObjectHandle declaring) {
        if (method.isNonNull() && isMethodAccessible(env, clazz, name, signature, method, declaring)) {
            return true;
        }
        try (CCharPointerHolder message = toCString(Agent.MESSAGE_PREFIX + "configuration does not permit access to method: " +
                        getClassNameOr(env, clazz, "(null)", "(?)") + "." + name + signature.get())) {

            beforeThrow(message);
            jniFunctions().getThrowNew().invoke(env, handles().javaLangNoSuchMethodException, message.get());
        }
        return false;
    }

    private static String asInternalSignature(Object paramTypesArray) {
        if (paramTypesArray instanceof Object[]) {
            StringBuilder sb = new StringBuilder("(");
            for (Object paramType : (Object[]) paramTypesArray) {
                sb.append(MetaUtil.toInternalName(paramType.toString()));
            }
            return sb.append(')').toString();
        }
        return null;
    }

    private static void beforeThrow(@SuppressWarnings("unused") CCharPointerHolder message) {
        // System.err.println(fromCString(message.get()));
    }

    private static void beforeFilter(@SuppressWarnings("unused") Supplier<String> message) {
        // System.err.println("Filtering: " + message.get());
    }

    public JNIObjectHandle filterGetFields(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle array, boolean declaredOnly, JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(env, callerClass)) {
            return array;
        }
        WordPredicate<JNIObjectHandle> predicate = f -> shouldFilterField(env, clazz, f, declaredOnly);
        return filterArray(env, array, () -> handles().getJavaLangReflectField(env), predicate);
    }

    public JNIObjectHandle filterGetMethods(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle array,
                    WordSupplier<JNIObjectHandle> elementClass, boolean declaredOnly, JNIObjectHandle callerClass) {

        if (shouldApproveWithoutChecks(env, callerClass)) {
            return array;
        }
        WordPredicate<JNIObjectHandle> predicate = m -> shouldFilterMethod(env, clazz, m, declaredOnly);
        return filterArray(env, array, elementClass, predicate);
    }

    private boolean shouldFilterMethod(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle methodObj, boolean declaredOnly) {
        JNIMethodId method = jniFunctions().getFromReflectedMethod().invoke(env, methodObj);
        if (method.isNonNull() && !clearException(env)) {
            JNIObjectHandle declaring = nullHandle();
            if (declaredOnly) {
                declaring = clazz;
            } else {
                WordPointer declaringPtr = StackValue.get(WordPointer.class);
                if (jvmtiFunctions().GetMethodDeclaringClass().invoke(jvmtiEnv(), method, declaringPtr) == JvmtiError.JVMTI_ERROR_NONE) {
                    declaring = declaringPtr.read();
                }
            }
            if (declaring.notEqual(nullHandle())) {
                CCharPointerPointer namePtr = StackValue.get(CCharPointerPointer.class);
                CCharPointerPointer signaturePtr = StackValue.get(CCharPointerPointer.class);
                if (jvmtiFunctions().GetMethodName().invoke(jvmtiEnv(), method, namePtr, signaturePtr, nullPointer()) == JvmtiError.JVMTI_ERROR_NONE) {
                    boolean accessible = isMethodAccessible(env, clazz, fromCString(namePtr.read()), () -> fromCString(signaturePtr.read()), method, declaring);
                    if (!accessible) {
                        beforeFilter(() -> "Method " + getClassNameOrNull(env, clazz) + "." + fromCString(namePtr.read()) + fromCString(signaturePtr.read()));
                    }
                    jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), namePtr.read());
                    jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), signaturePtr.read());
                    if (accessible) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static JNIObjectHandle filterArray(JNIEnvironment env, JNIObjectHandle array, WordSupplier<JNIObjectHandle> elementClass, WordPredicate<JNIObjectHandle> shouldRetain) {
        JNIObjectHandle result = nullHandle();
        int length = jniFunctions().getGetArrayLength().invoke(env, array);
        if (length > 0 && !clearException(env)) {
            JNIObjectHandle[] newArrayContents = new JNIObjectHandle[length];
            int newLength = 0;
            for (int i = 0; i < length; i++) {
                JNIObjectHandle element = jniFunctions().getGetObjectArrayElement().invoke(env, array, i);
                if (!clearException(env) && shouldRetain.test(element)) {
                    newArrayContents[newLength] = element;
                    newLength++;
                }
            }
            if (newLength == length) {
                result = array;
            } else {
                result = jniFunctions().getNewObjectArray().invoke(env, newLength, elementClass.get(), nullHandle());
                if (result.notEqual(nullHandle()) && !clearException(env)) {
                    for (int i = 0; i < newLength; i++) {
                        jniFunctions().getSetObjectArrayElement().invoke(env, result, i, newArrayContents[i]);
                        if (clearException(env)) {
                            result = nullHandle();
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    private boolean shouldFilterField(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle fieldObj, boolean declaredOnly) {
        JNIFieldId field = jniFunctions().getFromReflectedField().invoke(env, fieldObj);
        if (field.isNonNull() && !clearException(env)) {
            JNIObjectHandle declaring = nullHandle();
            if (declaredOnly) {
                declaring = clazz;
            } else {
                WordPointer declaringPtr = StackValue.get(WordPointer.class);
                if (jvmtiFunctions().GetFieldDeclaringClass().invoke(jvmtiEnv(), clazz, field, declaringPtr) == JvmtiError.JVMTI_ERROR_NONE) {
                    declaring = declaringPtr.read();
                }
            }
            if (declaring.notEqual(nullHandle())) {
                Supplier<String> nameSupplier = () -> {
                    String result = null;
                    CCharPointerPointer namePtr = StackValue.get(CCharPointerPointer.class);
                    if (jvmtiFunctions().GetFieldName().invoke(jvmtiEnv(), clazz, field, namePtr, nullPointer(), nullPointer()) == JvmtiError.JVMTI_ERROR_NONE) {
                        result = fromCString(namePtr.read());
                        jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), namePtr.read());
                    }
                    return result;
                };
                if (isFieldAccessible(env, clazz, nameSupplier, field, declaring)) {
                    return true;
                }
                beforeFilter(() -> "Method " + getClassNameOrNull(env, clazz) + "." + nameSupplier.get());
            }
        }
        return false;
    }
}
