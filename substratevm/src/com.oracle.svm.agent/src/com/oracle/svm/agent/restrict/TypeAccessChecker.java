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
import static com.oracle.svm.jvmtiagentbase.Support.jniFunctions;
import static com.oracle.svm.jvmtiagentbase.Support.jvmtiEnv;
import static com.oracle.svm.jvmtiagentbase.Support.jvmtiFunctions;
import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;
import static org.graalvm.word.WordFactory.nullPointer;

import java.lang.reflect.Modifier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.WordPointer;

import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiError;
import com.oracle.svm.configure.config.ConfigurationMethod;
import com.oracle.svm.configure.config.ConfigurationType;
import com.oracle.svm.configure.config.TypeConfiguration;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

public class TypeAccessChecker {
    private final TypeConfiguration configuration;

    public TypeAccessChecker(TypeConfiguration configuration) {
        this.configuration = configuration;
    }

    public TypeConfiguration getConfiguration() {
        return configuration;
    }

    public boolean isFieldAccessible(JNIEnvironment env, JNIObjectHandle clazz, Supplier<String> name, JNIFieldId field, JNIObjectHandle declaring) {
        ConfigurationType declaringType = getType(declaring);
        if (declaringType != null) {
            if (declaringType.haveAllDeclaredFields() || declaringType.hasIndividualField(name.get())) {
                return true;
            }
        }
        /*
         * Check if the field is public and if so, whether `clazz` or any of its superclasses or
         * superinterfaces which are subtypes of `declaring` have "allPublicFields" set on them.
         */
        CIntPointer modifiers = StackValue.get(CIntPointer.class);
        if (jvmtiFunctions().GetFieldModifiers().invoke(jvmtiEnv(), clazz, field, modifiers) == JvmtiError.JVMTI_ERROR_NONE) {
            // Checkstyle: allow reflection
            boolean isPublic = (modifiers.read() & Modifier.PUBLIC) != 0;
            if (isPublic) {
                if (declaringType != null && declaringType.haveAllPublicFields()) {
                    return true;
                }
                if (declaring.notEqual(clazz)) { // shortcut for declaring-only lookups
                    JNIObjectHandle current = clazz;
                    do {
                        ConfigurationType currentType = getType(current);
                        if (currentType != null && currentType.haveAllPublicFields()) {
                            return true;
                        }
                        if (testInterfacesBetween(env, declaring, current, ConfigurationType::haveAllPublicFields)) {
                            return true;
                        }
                        current = jniFunctions().getGetSuperclass().invoke(env, current);
                    } while (current.notEqual(nullHandle()) && jniFunctions().getIsAssignableFrom().invoke(env, current, declaring));
                }
            }
        }
        return false;

    }

    public boolean isFieldUnsafeAccessible(Supplier<String> name, JNIObjectHandle declaring) {
        ConfigurationType declaringType = getType(declaring);
        return declaringType != null && declaringType.hasIndividualUnsafeAccessField(name.get());
    }

    public boolean isMethodAccessible(JNIEnvironment env, JNIObjectHandle clazz, String name, Supplier<String> signature, JNIMethodId method, JNIObjectHandle declaring) {
        boolean isConstructor = ConfigurationMethod.CONSTRUCTOR_NAME.equals(name);
        ConfigurationType declaringType = getType(declaring);
        if (declaringType != null) {
            boolean haveAllDeclared = isConstructor ? declaringType.haveAllDeclaredConstructors() : declaringType.haveAllDeclaredMethods();
            if (haveAllDeclared || declaringType.hasIndividualMethod(name, signature.get())) {
                return true;
            }
        }
        /*
         * Check if the method is public and not static, and if so, whether `clazz` or any of its
         * superclasses or superinterfaces which are subtypes of `declaring` have "allPublicMethods"
         * set on them.
         */
        CIntPointer modifiers = StackValue.get(CIntPointer.class);
        if (jvmtiFunctions().GetMethodModifiers().invoke(jvmtiEnv(), method, modifiers) == JvmtiError.JVMTI_ERROR_NONE) {
            // Checkstyle: allow reflection
            boolean isPublic = (modifiers.read() & Modifier.PUBLIC) != 0;
            if (isPublic && declaringType != null) {
                if (isConstructor ? declaringType.haveAllPublicConstructors() : declaringType.haveAllPublicMethods()) {
                    return true;
                }
            }
            boolean isStatic = (modifiers.read() & Modifier.STATIC) != 0;
            if (isPublic && !isStatic && !isConstructor && declaring.notEqual(clazz)) {
                JNIObjectHandle current = clazz;
                do {
                    ConfigurationType currentType = getType(current);
                    if (currentType != null && currentType.haveAllPublicMethods()) {
                        return true;
                    }
                    if (testInterfacesBetween(env, declaring, current, ConfigurationType::haveAllPublicMethods)) {
                        return true;
                    }
                    current = jniFunctions().getGetSuperclass().invoke(env, current);
                } while (current.notEqual(nullHandle()) && jniFunctions().getIsAssignableFrom().invoke(env, current, declaring));
            }
        }
        // NOTE: this method might override a virtual method in a superclass which is
        // registered, but we still deny access because it really is a different method.
        return false;
    }

    public ConfigurationType getType(JNIObjectHandle clazz) {
        WordPointer signaturePtr = StackValue.get(WordPointer.class);
        if (jvmtiFunctions().GetClassSignature().invoke(jvmtiEnv(), clazz, signaturePtr, nullPointer()) == JvmtiError.JVMTI_ERROR_NONE) {
            String className = fromCString(signaturePtr.read());
            jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), signaturePtr.read());
            return configuration.getByInternalName(className);
        }
        return null;
    }

    /**
     * Determines, for interfaces implemented by {@code clazz} which are assignable to
     * {@code declaring}, if {@code predicate} returns {@code true}, returning on the first match.
     */
    public boolean testInterfacesBetween(JNIEnvironment env, JNIObjectHandle declaring, JNIObjectHandle clazz, Predicate<ConfigurationType> predicate) {
        CIntPointer ifaceCountPtr = StackValue.get(CIntPointer.class);
        WordPointer ifaceArrayPtr = StackValue.get(WordPointer.class);
        // NOTE: GetImplementedInterfaces provides only direct superinterfaces, so we recurse
        if (jvmtiFunctions().GetImplementedInterfaces().invoke(jvmtiEnv(), clazz, ifaceCountPtr, ifaceArrayPtr) == JvmtiError.JVMTI_ERROR_NONE) {
            WordPointer ifaceArray = ifaceArrayPtr.read();
            try {
                int ifaceCount = ifaceCountPtr.read();
                for (int i = 0; i < ifaceCount; i++) {
                    JNIObjectHandle iface = ifaceArray.read(i);
                    if (jniFunctions().getIsAssignableFrom().invoke(env, iface, declaring)) {
                        ConfigurationType ifaceType = getType(iface);
                        if (ifaceType != null && predicate.test(ifaceType)) {
                            return true;
                        }
                        if (testInterfacesBetween(env, declaring, iface, predicate)) {
                            return true;
                        }
                    }
                }
            } finally {
                jvmtiFunctions().Deallocate().invoke(jvmtiEnv(), ifaceArray);
            }
        }
        return false;
    }
}
