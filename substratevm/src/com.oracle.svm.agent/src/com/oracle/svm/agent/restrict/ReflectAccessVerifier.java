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

import static com.oracle.svm.configure.trace.LazyValueUtils.lazyValue;
import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;
import static com.oracle.svm.jvmtiagentbase.Support.clearException;
import static com.oracle.svm.jvmtiagentbase.Support.fromCString;
import static com.oracle.svm.jvmtiagentbase.Support.fromJniString;
import static com.oracle.svm.jvmtiagentbase.Support.jniFunctions;
import static com.oracle.svm.jvmtiagentbase.Support.jvmtiEnv;
import static com.oracle.svm.jvmtiagentbase.Support.jvmtiFunctions;
import static org.graalvm.word.WordFactory.nullPointer;

import java.util.function.Supplier;

import org.graalvm.compiler.phases.common.LazyValue;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.svm.agent.NativeImageAgent;
import com.oracle.svm.jvmtiagentbase.Support;
import com.oracle.svm.jvmtiagentbase.Support.WordSupplier;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiError;
import com.oracle.svm.configure.config.ConfigurationMethod;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.core.util.WordPredicate;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

/**
 * In restriction mode, decides whether to permit or deny individual reflective accesses, using
 * {@link AccessAdvisor} to decide additional exemptions from its own rules, such as system classes.
 */
public class ReflectAccessVerifier extends AbstractAccessVerifier {
    private final TypeAccessChecker typeAccessChecker;
    private final NativeImageAgent agent;

    public ReflectAccessVerifier(TypeAccessChecker typeAccessChecker, AccessAdvisor advisor, NativeImageAgent agent) {
        super(advisor);
        this.typeAccessChecker = typeAccessChecker;
        this.agent = agent;
    }

    public boolean verifyForName(JNIEnvironment env, JNIObjectHandle callerClass, String className) {
        if (shouldApproveWithoutChecks(lazyValue(className), lazyClassNameOrNull(env, callerClass))) {
            return true;
        }
        return className == null || typeAccessChecker.getConfiguration().get(className) != null;
    }

    public boolean verifyLoadClass(JNIEnvironment env, JNIObjectHandle callerClass, String className) {
        LazyValue<String> lazyName = lazyValue(className);
        LazyValue<String> callerClassName = lazyClassNameOrNull(env, callerClass);
        if (shouldApproveWithoutChecks(lazyName, callerClassName)) {
            return true;
        }
        if (accessAdvisor.shouldIgnoreLoadClass(lazyName, callerClassName)) {
            return true;
        }
        return className == null || typeAccessChecker.getConfiguration().get(className) != null;
    }

    public boolean verifyGetField(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle name, JNIObjectHandle result, JNIObjectHandle declaring, JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(env, clazz, callerClass)) {
            return true;
        }
        JNIFieldId field = jniFunctions().getFromReflectedField().invoke(env, result);
        return field.isNull() || typeAccessChecker.isFieldAccessible(env, clazz, () -> fromJniString(env, name), field, declaring);
    }

    public boolean verifyObjectFieldOffset(JNIEnvironment env, JNIObjectHandle name, JNIObjectHandle declaring, JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(env, declaring, callerClass)) {
            return true;
        }
        return typeAccessChecker.isFieldUnsafeAccessible(() -> fromJniString(env, name), declaring);
    }

    public boolean verifyGetMethod(JNIEnvironment env, JNIObjectHandle clazz, String name, Supplier<String> signature, JNIObjectHandle result, JNIObjectHandle declaring, JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(env, clazz, callerClass)) {
            return true;
        }
        JNIMethodId method = jniFunctions().getFromReflectedMethod().invoke(env, result);
        return verifyGetMethod0(env, clazz, name, signature, method, declaring);
    }

    public boolean verifyGetConstructor(JNIEnvironment env, JNIObjectHandle clazz, Supplier<String> signature, JNIObjectHandle result, JNIObjectHandle callerClass) {
        return verifyGetMethod(env, clazz, ConfigurationMethod.CONSTRUCTOR_NAME, signature, result, clazz, callerClass);
    }

    public boolean verifyNewInstance(JNIEnvironment env, JNIObjectHandle clazz, String name, String signature, JNIMethodId result, JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(env, clazz, callerClass)) {
            return true;
        }
        return verifyGetMethod0(env, clazz, name, () -> signature, result, clazz);
    }

    public boolean verifyNewArray(JNIEnvironment env, JNIObjectHandle arrayClass, JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(env, arrayClass, callerClass)) {
            return true;
        }
        return typeAccessChecker.getType(arrayClass) != null;
    }

    public boolean verifyGetEnclosingMethod(JNIEnvironment env, JNIObjectHandle clazz, String name, String signature, JNIObjectHandle result, JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(env, clazz, callerClass)) {
            return true;
        }
        JNIMethodId method = jniFunctions().getFromReflectedMethod().invoke(env, result);
        return method.isNull() || typeAccessChecker.isMethodAccessible(env, clazz, name, () -> signature, method, clazz);
    }

    private boolean verifyGetMethod0(JNIEnvironment env, JNIObjectHandle clazz, String name, Supplier<String> signature, JNIMethodId method, JNIObjectHandle declaring) {
        return method.isNull() || typeAccessChecker.isMethodAccessible(env, clazz, name, signature, method, declaring);
    }

    public JNIObjectHandle filterGetFields(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle array, boolean declaredOnly, JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(env, clazz, callerClass)) {
            return array;
        }
        WordPredicate<JNIObjectHandle> predicate = f -> shouldRetainField(env, clazz, f, declaredOnly);
        return filterArray(env, array, () -> agent.handles().getJavaLangReflectField(env), predicate);
    }

    public JNIObjectHandle filterGetMethods(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle array,
                    WordSupplier<JNIObjectHandle> elementClass, boolean declaredOnly, JNIObjectHandle callerClass) {

        if (shouldApproveWithoutChecks(env, clazz, callerClass)) {
            return array;
        }
        WordPredicate<JNIObjectHandle> predicate = m -> shouldRetainMethod(env, clazz, m, declaredOnly);
        return filterArray(env, array, elementClass, predicate);
    }

    private boolean shouldRetainMethod(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle methodObj, boolean declaredOnly) {
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
                    boolean accessible = typeAccessChecker.isMethodAccessible(env, clazz, fromCString(namePtr.read()), () -> fromCString(signaturePtr.read()), method, declaring);
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

    private boolean shouldRetainField(JNIEnvironment env, JNIObjectHandle clazz, JNIObjectHandle fieldObj, boolean declaredOnly) {
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
                Supplier<String> nameSupplier = () -> Support.getFieldName(clazz, field);
                if (typeAccessChecker.isFieldAccessible(env, clazz, nameSupplier, field, declaring)) {
                    return true;
                }
            }
        }
        return false;
    }
}
