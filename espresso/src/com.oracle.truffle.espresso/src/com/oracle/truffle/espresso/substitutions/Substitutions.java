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
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.StaticSymbols;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.IntrinsicSubstitutorNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.dispatch.EspressoInterop;

/**
 * Substitutions/intrinsics for Espresso.
 * <p>
 * Some substitutions are statically defined, others runtime-dependent. The static ones are loaded
 * via {@link ServiceLoader}. Iterating over the collection in this class allows to register them
 * directly, and assign to each of them a node, which will dispatch them directly, without the need
 * for reflection. In practice, this allows inlining.
 * <p>
 * To register a substitution in Espresso:
 * <li>Create a class annotated with {@link EspressoSubstitutions}. Its name must be the fully
 * qualified name of the substituted class, to which is prepended "Target_" and each "." is replaced
 * by a "_". For example, java.lang.Class becomes Target_java_lang_Class. Keep the "$" in case you
 * want to substitute an inner class.
 * <li>For each substituted method of the class, create a method in the "Target_" class. This method
 * should be annotated with {@link Substitution}. If the method is an instance method, it must be
 * annotated with {@link Substitution#hasReceiver()} = true
 * <li>If the method has a primitive signature, the signature of the substitution should be the
 * same, save for a potential receiver. If there are reference types in the signature, Simply put a
 * StaticObject type instead, but annotate the argument with {@link JavaType}. This must be done for
 * EVERY reference argument, even the receiver.
 * <li>If the class of the reference argument is public, (/ex {@link Class}), you can simply put @
 * {@link JavaType}({@link Class}.class) in the annotation. If the class is private, you have to put
 * {@link JavaType}(typeName() = ...), where "..." is the internal name of the class (ie: the
 * qualified name, where all "." are replaced with "/", an "L" is prepended, and a ";" is appended.
 * /ex: java.lang.Class becomes Ljava/lang/Class;.)
 * <li>The name of the method in the substitution can be the same as the substitution target, and it
 * will work out. Note that it might happen that a class overloads a method, and since types gets
 * "erased" in the substitution, it is not possible to give the same name to both. If that happens,
 * you can use the {@link Substitution#methodName()} value. For example, in {@link java.util.Arrays}
 * , the toString(... array) method is overloaded with every primitive array type. In that case you
 * can write in the substitution
 *
 * <pre>
 * {@literal @}Substitution(methodName = "toString")
 * public static @JavaType(String.class) StaticObject toString_byte(@JavaType(byte[].class) StaticObject array) {
 *     ...
 * }
 *
 * {@literal @}Substitution(methodName = "toString")
 * public static @JavaType(String.class) StaticObject toString_int(@JavaType(int[].class) StaticObject array) {
 *     ...
 * }
 * </pre>
 *
 * and so on so forth.
 * <li>Additionally, some substitutions may not be given a meta accessor as parameter, but may need
 * to get the meta from somewhere. Regular meta obtention can be done through
 * {@link EspressoContext#get(Node)}, but this is quite a slow access. As such, it is possible to
 * append the meta as an argument to the substitution, annotated with {@link Inject} . Once again,
 * the processor will generate all that is needed to give the meta.
 * <p>
 * <p>
 * The order of arguments matter: First, the actual guest arguments, next the list of guest method
 * nodes, and finally the meta to be injected.
 */
public final class Substitutions implements ContextAccess {

    private static final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, Substitutions.class);

    public static TruffleLogger getLogger() {
        return logger;
    }

    public static void ensureInitialized() {
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
        /**
         * Creates a node with a substitution for the given method, or returns <code>null</code> if
         * the substitution does not apply e.g. static substitutions are only valid classes/methods
         * on the boot and platform class loaders.
         *
         * @param forceValid if true, skips all checks to validate the substitution for the given
         *            method.
         */
        EspressoRootNode createNodeIfValid(Method method, boolean forceValid);

        default EspressoRootNode createNodeIfValid(Method method) {
            return createNodeIfValid(method, false);
        }
    }

    private static final EconomicMap<MethodRef, EspressoRootNodeFactory> STATIC_SUBSTITUTIONS = EconomicMap.create();

    private final ConcurrentHashMap<MethodRef, EspressoRootNodeFactory> runtimeSubstitutions = new ConcurrentHashMap<>();

    static {
        for (JavaSubstitution.Factory factory : SubstitutionCollector.getInstances(JavaSubstitution.Factory.class)) {
            registerStaticSubstitution(factory);
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

        MethodRef(Symbol<Type> clazz, Symbol<Name> methodName, Symbol<Signature> signature) {
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
            return Types.binaryName(clazz) + "#" + methodName + signature;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerStaticSubstitution(JavaSubstitution.Factory substitutorFactory) {
        List<Symbol<Type>> parameterTypes = new ArrayList<>();
        for (int i = substitutorFactory.hasReceiver() ? 1 : 0; i < substitutorFactory.parameterTypes().length; i++) {
            String type = substitutorFactory.parameterTypes()[i];
            parameterTypes.add(StaticSymbols.putType(type));
        }
        Symbol<Type> returnType = StaticSymbols.putType(substitutorFactory.returnType());
        Symbol<Signature> signature = StaticSymbols.putSignature(returnType, parameterTypes.toArray(Symbol.EMPTY_ARRAY));

        EspressoRootNodeFactory factory = new EspressoRootNodeFactory() {
            @Override
            public EspressoRootNode createNodeIfValid(Method methodToSubstitute, boolean forceValid) {
                if (!substitutorFactory.isValidFor(methodToSubstitute.getJavaVersion())) {
                    return null;
                }
                StaticObject classLoader = methodToSubstitute.getDeclaringKlass().getDefiningClassLoader();
                if (forceValid || ClassRegistry.loaderIsBootOrPlatform(classLoader, methodToSubstitute.getMeta())) {
                    return EspressoRootNode.create(null, new IntrinsicSubstitutorNode(substitutorFactory, methodToSubstitute));
                }

                getLogger().warning(new Supplier<String>() {
                    @Override
                    public String get() {
                        StaticObject givenLoader = methodToSubstitute.getDeclaringKlass().getDefiningClassLoader();
                        return "Static substitution for " + methodToSubstitute + " does not apply.\n" +
                                        "\tExpected class loader: Boot (null) or platform class loader\n" +
                                        "\tGiven class loader: " + EspressoInterop.toDisplayString(givenLoader, false) + "\n";
                    }
                });
                return null;
            }
        };
        String[] classNames = substitutorFactory.substitutionClassNames();
        String[] methodNames = substitutorFactory.getMethodNames();
        for (int i = 0; i < classNames.length; i++) {
            assert classNames[i].startsWith("Target_");
            Symbol<Type> classType = StaticSymbols.putType("L" + classNames[i].substring("Target_".length()).replace('_', '/') + ";");
            Symbol<Name> methodName = StaticSymbols.putName(methodNames[i]);
            registerStaticSubstitution(classType, methodName, signature, factory, true);
        }
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

        if (STATIC_SUBSTITUTIONS.containsKey(key)) {
            getLogger().log(Level.FINE, "Runtime substitution shadowed by static one: " + key);
        }

        if (throwIfPresent && runtimeSubstitutions.containsKey(key)) {
            throw EspressoError.shouldNotReachHere("substitution already registered " + key);
        }
        runtimeSubstitutions.put(key, factory);
    }

    public void removeRuntimeSubstitution(Method method) {
        MethodRef key = getMethodKey(method);
        runtimeSubstitutions.remove(key);
    }

    /**
     * Returns a node with a substitution for the given method, or <code>null</code> if the
     * substitution does not exist or does not apply.
     */
    public EspressoRootNode get(Method method) {
        MethodRef key = getMethodKey(method);
        EspressoRootNodeFactory factory = STATIC_SUBSTITUTIONS.get(key);
        if (factory == null) {
            factory = runtimeSubstitutions.get(key);
        }
        if (factory == null) {
            return null;
        }
        return factory.createNodeIfValid(method);
    }

    public boolean hasSubstitutionFor(Method method) {
        MethodRef key = getMethodKey(method);
        return STATIC_SUBSTITUTIONS.containsKey(key) || runtimeSubstitutions.containsKey(key);
    }
}
