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
import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;
import static org.graalvm.word.WordFactory.nullPointer;

import java.lang.reflect.Modifier;
import java.util.function.Predicate;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.svm.agent.Agent;
import com.oracle.svm.agent.jvmti.JvmtiError;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

public class JniAccessVerifier {

    private final Configuration configuration;
    private final AccessAdvisor advisor = new AccessAdvisor();

    public JniAccessVerifier(Configuration configuration) {
        this.configuration = configuration;
    }

    public void setInLivePhase(boolean live) {
        advisor.setInLivePhase(live);
    }

    private boolean shouldApproveWithoutChecks(JNIEnvironment env, JNIObjectHandle callerClass) {
        return advisor.shouldIgnore(() -> (String) getClassNameOrNull(env, callerClass));
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
        if (advisor.shouldIgnoreJniMethodLookup(() -> (String) getClassNameOrNull(env, clazz), () -> name, () -> fromCString(csignature), () -> (String) getClassNameOrNull(env, callerClass))) {
            return true;
        }
        WordPointer declaringPtr = StackValue.get(WordPointer.class);
        if (jvmtiFunctions().GetMethodDeclaringClass().invoke(jvmtiEnv(), result, declaringPtr) == JvmtiError.JVMTI_ERROR_NONE) {
            boolean isConstructor = ConfigurationMethod.CONSTRUCTOR_NAME.equals(name);
            JNIObjectHandle declaring = declaringPtr.read();
            ConfigurationType declaringType = getType(declaring);
            if (declaringType != null) {
                if (isConstructor) {
                    if (declaringType.haveAllDeclaredConstructors()) {
                        return true;
                    }
                } else if (declaringType.haveAllDeclaredMethods()) {
                    return true;
                }
                if (declaringType.hasIndividualMethod(name, fromCString(csignature))) {
                    return true;
                }
            }
            /*
             * Check if the field is public and not static, and if so, whether `clazz` or any of its
             * superclasses or superinterfaces which are subtypes of `declaring` have all public
             * methods registered.
             */
            CIntPointer modifiers = StackValue.get(CIntPointer.class);
            if (jvmtiFunctions().GetMethodModifiers().invoke(jvmtiEnv(), result, modifiers) == JvmtiError.JVMTI_ERROR_NONE) {
                // Checkstyle: allow reflection
                boolean isPublicAndNotStatic = (modifiers.read() & (Modifier.PUBLIC | Modifier.STATIC)) == Modifier.PUBLIC;
                if (isPublicAndNotStatic) {
                    if (isConstructor) {
                        if (declaringType != null && declaringType.haveAllPublicConstructors()) {
                            return true;
                        }
                        // Finished: constructors of superclasses are irrelevant.
                    } else {
                        JNIObjectHandle current = clazz;
                        do {
                            ConfigurationType currentType = getType(current);
                            if (currentType != null && currentType.haveAllPublicMethods()) {
                                return true;
                            }
                            current = jniFunctions().getGetSuperclass().invoke(env, current);
                        } while (current.notEqual(nullHandle()) && jniFunctions().getIsAssignableFrom().invoke(env, current, declaring));

                        if (testAnyInterfaceBetween(env, declaring, clazz, ConfigurationType::haveAllPublicMethods)) {
                            return true;
                        }
                    }
                }
            }
            // NOTE: this method might override a virtual method in a superclass which is
            // registered, but we still deny access because it really is a different method.
        }
        try (CCharPointerHolder message = toCString(Agent.MESSAGE_PREFIX + "configuration does not permit access to method: " +
                        getClassNameOr(env, clazz, "(null)", "(?)") + "." + fromCString(cname) + fromCString(csignature))) {

            beforeThrow(message);
            jniFunctions().getThrowNew().invoke(env, handles().javaLangNoSuchMethodError, message.get());
        }
        return false;
    }

    public boolean verifyGetFieldID(JNIEnvironment env, JNIObjectHandle clazz, CCharPointer cname, @SuppressWarnings("unused") CCharPointer csignature, JNIFieldId result,
                    JNIObjectHandle callerClass) {
        assert result.isNonNull();
        if (shouldApproveWithoutChecks(env, callerClass)) {
            return true;
        }
        // Check if the member in the declaring method is registered.
        WordPointer declaringPtr = StackValue.get(WordPointer.class);
        if (jvmtiFunctions().GetFieldDeclaringClass().invoke(jvmtiEnv(), clazz, result, declaringPtr) == JvmtiError.JVMTI_ERROR_NONE) {
            JNIObjectHandle declaring = declaringPtr.read();
            ConfigurationType declaringType = getType(declaring);
            if (declaringType != null) {
                if (declaringType.haveAllDeclaredFields()) {
                    return true;
                } else if (declaringType.hasIndividualField(fromCString(cname))) {
                    return true;
                }
            }
            /*
             * Check if the field is public and if so, whether `clazz` or any of its superclasses or
             * superinterfaces which are subtypes of `declaring` have all public fields registered.
             */
            CIntPointer modifiers = StackValue.get(CIntPointer.class);
            if (jvmtiFunctions().GetFieldModifiers().invoke(jvmtiEnv(), clazz, result, modifiers) == JvmtiError.JVMTI_ERROR_NONE) {
                // Checkstyle: allow reflection
                boolean isPublic = (modifiers.read() & Modifier.PUBLIC) != 0;
                if (isPublic) {
                    JNIObjectHandle current = clazz;
                    do {
                        ConfigurationType currentType = getType(current);
                        if (currentType != null && currentType.haveAllPublicFields()) {
                            return true;
                        }
                        current = jniFunctions().getGetSuperclass().invoke(env, current);
                    } while (current.notEqual(nullHandle()) && jniFunctions().getIsAssignableFrom().invoke(env, current, declaring));

                    if (testAnyInterfaceBetween(env, declaring, clazz, ConfigurationType::haveAllPublicFields)) {
                        return true;
                    }
                }
            }
        }
        try (CCharPointerHolder message = toCString(Agent.MESSAGE_PREFIX + "configuration does not permit access to field: " +
                        getClassNameOr(env, clazz, "(null)", "(?)") + "." + fromCString(cname))) {

            beforeThrow(message);
            jniFunctions().getThrowNew().invoke(env, handles().javaLangNoSuchFieldError, message.get());
        }
        return false;
    }

    private ConfigurationType getType(JNIObjectHandle clazz) {
        WordPointer signaturePtr = StackValue.get(WordPointer.class);
        if (jvmtiFunctions().GetClassSignature().invoke(jvmtiEnv(), clazz, signaturePtr, nullPointer()) == JvmtiError.JVMTI_ERROR_NONE) {
            String className = fromCString(signaturePtr.read());
            jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), signaturePtr.read());
            return configuration.get(className);
        }
        return null;
    }

    /**
     * If {@code declaring} is an interface, determines, for any interfaces implemented by
     * {@code clazz} which are assignable to {@code declaring}, whether {@code predicate} holds
     * {@code true}, returning early on the first match.
     */
    private boolean testAnyInterfaceBetween(JNIEnvironment env, JNIObjectHandle declaring, JNIObjectHandle clazz, Predicate<ConfigurationType> predicate) {
        CIntPointer isInterfacePtr = StackValue.get(CIntPointer.class);
        if (jvmtiFunctions().IsInterface().invoke(jvmtiEnv(), declaring, isInterfacePtr) == JvmtiError.JVMTI_ERROR_NONE && isInterfacePtr.read() != 0) {
            CIntPointer ifaceCountPtr = StackValue.get(CIntPointer.class);
            WordPointer ifaceArrayPtr = StackValue.get(WordPointer.class);
            if (jvmtiFunctions().GetImplementedInterfaces().invoke(jvmtiEnv(), clazz, ifaceCountPtr, ifaceArrayPtr) == JvmtiError.JVMTI_ERROR_NONE) {
                WordPointer ifaceArray = ifaceArrayPtr.read();
                int ifaceCount = ifaceCountPtr.read();
                for (int i = 0; i < ifaceCount; i++) {
                    JNIObjectHandle iface = ifaceArray.read(i);
                    if (jniFunctions().getIsAssignableFrom().invoke(env, iface, declaring)) {
                        ConfigurationType ifaceType = getType(iface);
                        if (ifaceType != null && predicate.test(ifaceType)) {
                            return true;
                        }
                    }
                }
                jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), ifaceArray);
            }
        }
        return false;
    }

    private static void beforeThrow(@SuppressWarnings("unused") CCharPointerHolder message) {
        // System.err.println(fromCString(message.get()));
    }
}
