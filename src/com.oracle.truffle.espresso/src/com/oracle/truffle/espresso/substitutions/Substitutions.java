/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.substitutions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.espresso.descriptors.StaticSymbols;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.IntrinsicReflectionRootNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * Substitutions/intrinsics for Espresso.
 *
 * Some substitutions are statically defined, others runtime-dependent. The static-ones are
 * initialized in the static initializer; which allows using MethodHandles instead of reflection in
 * SVM.
 */
public final class Substitutions implements ContextAccess {

    public static void init() {
        /* nop */
    }

    private final EspressoContext context;

    @Override
    public EspressoContext getContext() {
        return context;
    }

    /**
     * We use a factory to create the substitution node once the target Method instance is known.
     */
    public interface EspressoRootNodeFactory {
        EspressoRootNode spawnNode(Method method);
    }

    private static final EconomicMap<MethodRef, EspressoRootNodeFactory> STATIC_SUBSTITUTIONS = EconomicMap.create();

    private final ConcurrentHashMap<MethodRef, EspressoRootNodeFactory> runtimeSubstitutions = new ConcurrentHashMap<>();

    private static final List<Class<?>> ESPRESSO_SUBSTITUTIONS = Collections.unmodifiableList(Arrays.asList(
                    Target_java_lang_Class.class,
                    Target_java_lang_ClassLoader.class,
                    Target_java_lang_Object.class,
                    Target_java_lang_System.class,
                    Target_java_lang_Thread.class,
                    Target_java_lang_ref_Finalizer$FinalizerThread.class,
                    Target_java_lang_ref_Reference$ReferenceHandler.class,
                    Target_java_lang_reflect_Array.class,
                    Target_java_security_AccessController.class,
                    Target_sun_awt_SunToolkit.class,
                    Target_sun_misc_Perf.class,
                    Target_sun_misc_Signal.class,
                    Target_sun_misc_Unsafe.class,
                    Target_sun_misc_URLClassPath.class,
                    Target_sun_misc_VM.class,
                    Target_sun_reflect_NativeConstructorAccessorImpl.class,
                    Target_sun_reflect_NativeMethodAccessorImpl.class,
                    Target_java_lang_invoke_MethodHandleNatives.class));

    static {
        for (Class<?> clazz : ESPRESSO_SUBSTITUTIONS) {
            registerStaticSubstitutions(clazz);
        }
    }

    public Substitutions(EspressoContext context) {
        this.context = context;
    }

    private static MethodRef getMethodKey(Method method) {
        return new MethodRef(
                        method.getDeclaringKlass().getType(),
                        method.getName(),
                        method.getRawSignature());
    }

    private static final class MethodRef {
        private final Symbol<Type> clazz;
        private final Symbol<Name> methodName;
        private final Symbol<Signature> signature;
        private final int hash;

        public MethodRef(Symbol<Type> clazz, Symbol<Name> methodName, Symbol<Signature> signature) {
            this.clazz = clazz;
            this.methodName = methodName;
            this.signature = signature;
            this.hash = Objects.hash(clazz, methodName, signature);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            MethodRef other = (MethodRef) obj;
            return Objects.equals(clazz, other.clazz) &&
                            Objects.equals(methodName, other.methodName) &&
                            Objects.equals(signature, other.signature);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return "MethodKey<" + clazz + "." + methodName + " -> " + signature + ">";
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerStaticSubstitutions(Class<?> clazz) {
        int registered = 0;

        Symbol<Type> classType;
        Class<?> annotatedClass = clazz.getAnnotation(EspressoSubstitutions.class).value();
        if (annotatedClass == EspressoSubstitutions.class) {
            // Target class is derived from class name by simple substitution
            // e.g. Target_java_lang_System becomes java.lang.System
            assert clazz.getSimpleName().startsWith("Target_");
            classType = StaticSymbols.putType("L" + clazz.getSimpleName().substring("Target_".length()).replace('_', '/') + ";");
        } else {
            throw EspressoError.shouldNotReachHere("Substitutions class must be decorated with @" + EspressoSubstitutions.class.getName());
        }

        for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
            Substitution substitution = method.getAnnotation(Substitution.class);
            if (substitution == null) {
                continue;
            }

            final EspressoRootNodeFactory factory;
            factory = new EspressoRootNodeFactory() {
                @Override
                public EspressoRootNode spawnNode(Method espressoMethod) {
                    return new EspressoRootNode(espressoMethod, new IntrinsicReflectionRootNode(method, espressoMethod));
                }
            };

            java.lang.reflect.Parameter[] parameters = method.getParameters();

            List<Symbol<Type>> parameterTypes = new ArrayList<>();

            for (int i = substitution.hasReceiver() ? 1 : 0; i < parameters.length; i++) {
                java.lang.reflect.Parameter parameter = parameters[i];
                Symbol<Type> parameterType;
                Host annotatedType = parameter.getAnnotatedType().getAnnotation(Host.class);
                if (annotatedType != null) {
                    Class<?> guestType = annotatedType.value();
                    if (guestType == Host.class) {
                        parameterType = StaticSymbols.putType(annotatedType.typeName());
                    } else {
                        parameterType = StaticSymbols.putType(guestType);
                    }
                } else {
                    parameterType = StaticSymbols.putType(parameter.getType());
                }
                parameterTypes.add(parameterType);
            }

            Host annotatedReturnType = method.getAnnotatedReturnType().getAnnotation(Host.class);
            Symbol<Type> returnType;
            if (annotatedReturnType != null) {
                Class<?> guestType = annotatedReturnType.value();
                if (guestType == Host.class) {
                    returnType = StaticSymbols.putType(annotatedReturnType.typeName());
                } else {
                    returnType = StaticSymbols.putType(guestType);
                }
            } else {
                returnType = StaticSymbols.putType(method.getReturnType());
            }

            String methodName = substitution.methodName();
            if (methodName.length() == 0) {
                methodName = method.getName();
            }

            ++registered;
            registerStaticSubstitution(classType,
                            StaticSymbols.putName(methodName),
                            StaticSymbols.putSignature(returnType, parameterTypes.toArray(new Symbol[parameterTypes.size()])),
                            factory,
                            true);
        }
        assert registered > 0 : "No substitutions found in " + clazz;
    }

    private static void registerStaticSubstitution(Symbol<Type> type, Symbol<Name> methodName, Symbol<Signature> signature, EspressoRootNodeFactory factory, boolean throwIfPresent) {
        MethodRef key = new MethodRef(type, methodName, signature);
        if (throwIfPresent && STATIC_SUBSTITUTIONS.containsKey(key)) {
            throw EspressoError.shouldNotReachHere("substitution already registered" + key);
        }
        STATIC_SUBSTITUTIONS.put(key, factory);
    }

    public void registerRuntimeSubstitution(Symbol<Type> type, Symbol<Name> methodName, Symbol<Signature> signature, EspressoRootNodeFactory factory, boolean throwIfPresent) {
        MethodRef key = new MethodRef(type, methodName, signature);

        EspressoError.warnIf(STATIC_SUBSTITUTIONS.containsKey(key), "Runtime substitution shadowed by static one " + key);

        if (throwIfPresent && runtimeSubstitutions.containsKey(key)) {
            throw EspressoError.shouldNotReachHere("substitution already registered" + key);
        }
        runtimeSubstitutions.put(key, factory);
    }

    public EspressoRootNode get(Method method) {
        MethodRef key = getMethodKey(method);
        EspressoRootNodeFactory factory = STATIC_SUBSTITUTIONS.get(key);
        if (factory == null) {
            factory = runtimeSubstitutions.get(key);
        }
        if (factory == null) {
            return null;
        }
        return factory.spawnNode(method);
    }
}
