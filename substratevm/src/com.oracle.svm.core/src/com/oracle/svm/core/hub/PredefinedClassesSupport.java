/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.core.hub;

import static java.lang.classfile.ClassFile.ConstantPoolSharingOption.NEW_POOL;

import java.io.Serializable;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ClassUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.util.Digest;

public final class PredefinedClassesSupport {
    public static final class Options {
        /**
         * This option controls only support for the user-facing mechanism working with bytecodes
         * (class data). Internal support (needed for proxy classes) remains active either way.
         */
        @Option(help = "Enable support for predefining additional classes.") //
        static final HostedOptionKey<Boolean> SupportPredefinedClasses = new HostedOptionKey<>(true);
    }

    public static final String ENABLE_BYTECODES_OPTION = SubstrateOptionsParser.commandArgument(Options.SupportPredefinedClasses, "+");

    @Platforms(Platform.HOSTED_ONLY.class) private Consumer<Class<?>> validator = null;

    @Fold
    public static boolean supportsBytecodes() {
        return Options.SupportPredefinedClasses.getValue();
    }

    @Fold
    public static boolean hasBytecodeClasses() {
        return supportsBytecodes() && !singleton().predefinedClassesByHash.isEmpty();
    }

    @Fold
    static PredefinedClassesSupport singleton() {
        return ImageSingletons.lookup(PredefinedClassesSupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Set<Class<?>> predefinedClasses = new HashSet<>();

    private final ReentrantLock lock = new ReentrantLock();

    /** Predefined classes by hash. */
    private final EconomicMap<String, Class<?>> predefinedClassesByHash = ImageHeapMap.create("predefinedClassesByHash");

    /** Predefined classes which have already been loaded, by name. */
    private final EconomicMap<String, Class<?>> loadedClassesByName = EconomicMap.create();

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setRegistrationValidator(Consumer<Class<?>> consumer) {
        validator = consumer;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerClass(String hash, Class<?> clazz) {
        if (singleton().validator != null) {
            singleton().validator.accept(clazz);
        }
        Class<?> existing = singleton().predefinedClassesByHash.putIfAbsent(hash, clazz);
        if (existing != clazz) {
            VMError.guarantee(existing == null, "Can define only one class per hash");
            /*
             * Predefined lambda classes do not have an addressHash at the end. They are in the form
             * capturingClass$$Lambda$stableHash instead of
             * capturingClass$$Lambda$stableHash/addressHash. The only way to register a predefined
             * lambdas for serialization or reflection is here, where we have an actual predefined
             * class as an instance of {@code java.lang.Class} at build time.
             */
            if (LambdaUtils.isLambdaClass(clazz)) {
                registerLambdaForReflection(clazz);
            }
            singleton().predefinedClasses.add(clazz);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static void registerLambdaForReflection(Class<?> lambdaClass) {
        /*
         * When {@code java.lang.invoke.InnerClassLambdaMetafactory} builds a call site for a lambda
         * class, it uses either static field LAMBDA_INSTANCE$ or lambda constructor. Since we are
         * generating lambda classes at runtime, we need to register that field or a lambda
         * constructor for reflection.
         */
        try {
            RuntimeReflection.register(lambdaClass.getDeclaredField("LAMBDA_INSTANCE$"));
        } catch (NoSuchFieldException ignored) {
            RuntimeReflection.register(lambdaClass.getDeclaredConstructors());
        }

        /*
         * In some cases, predefined lambda should be serialized. We have to register proper method
         * for this. We cannot do this in {@code
         * com.oracle.svm.hosted.reflect.serialize.SerializationFeature}, since we cannot extract
         * lambda-class information from the capturing class.
         */
        if (Serializable.class.isAssignableFrom(lambdaClass) &&
                        SerializationSupport.currentLayer().isLambdaCapturingClassRegistered(LambdaUtils.capturingClass(lambdaClass.getName()))) {
            try {
                Method serializeLambdaMethod = lambdaClass.getDeclaredMethod("writeReplace");
                RuntimeReflection.register(serializeLambdaMethod);
            } catch (NoSuchMethodException e) {
                throw VMError.shouldNotReachHere("Serializable lambda class must contain the writeReplace method.");
            }
        }
    }

    /**
     * Register a class that cannot be loaded via a byte array of its class data, only with
     * {@link #loadClass(ClassLoader, ProtectionDomain, Class)} or {@link #loadClassIfNotLoaded}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerClass(Class<?> clazz) {
        if (singleton().validator != null) {
            singleton().validator.accept(clazz);
        }
        singleton().predefinedClasses.add(clazz);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean isPredefined(Class<?> clazz) {
        return singleton().predefinedClasses.contains(clazz);
    }

    public static Class<?> knownClass(byte[] data, int offset, int length) {
        String hash = getHash(data, offset, length);
        Class<?> clazz = singleton().predefinedClassesByHash.get(hash);
        return clazz;
    }

    public static String getHash(byte[] data, int offset, int length) {
        return Digest.digest(data, offset, length);
    }

    public static void loadClass(ClassLoader classLoader, ProtectionDomain protectionDomain, Class<?> clazz) {
        boolean loaded = loadClassIfNotLoaded(classLoader, protectionDomain, clazz);
        if (!loaded) {
            if (classLoader == clazz.getClassLoader()) {
                throw new LinkageError("Loader " + classLoader + " attempted duplicate class definition for " + clazz.getName() + " defined by " + clazz.getClassLoader());
            } else {
                throw VMError.unsupportedFeature("A predefined class can be loaded (defined) at runtime only once by a single class loader. " +
                                "Hierarchies of class loaders and distinct sets of classes are not supported. Class " + clazz.getName() + " has already " +
                                "been loaded by class loader: " + clazz.getClassLoader());
            }
        }
    }

    /**
     * Load the class if it has not already been loaded. Returns {@code true} if the class has been
     * loaded as a result of this call, {@code false} otherwise. Throws if an error occurred.
     */
    public static boolean loadClassIfNotLoaded(ClassLoader classLoader, ProtectionDomain protectionDomain, Class<?> clazz) {
        return singleton().loadClass0(classLoader, protectionDomain, clazz);
    }

    private boolean loadClass0(ClassLoader classLoader, ProtectionDomain protectionDomain, Class<?> clazz) {
        if (DynamicHub.fromClass(clazz).isLoaded()) {
            return false;
        }

        loadSuperType(clazz, clazz.getSuperclass(), classLoader);
        for (Class<?> intf : clazz.getInterfaces()) {
            loadSuperType(clazz, intf, classLoader);
        }

        lock.lock();
        try {
            if (DynamicHub.fromClass(clazz).isLoaded()) {
                return false;
            }

            /*
             * The following is part of the locked block so that other threads can observe only the
             * initialized values once the class can be found.
             */
            DynamicHub hub = DynamicHub.fromClass(clazz);
            hub.setClassLoaderAtRuntime(classLoader);
            if (protectionDomain != null) {
                hub.setProtectionDomainAtRuntime(protectionDomain);
            }
            loadedClassesByName.put(clazz.getName(), clazz);
            return true;
        } finally {
            lock.unlock();
        }
    }

    private static void loadSuperType(Class<?> clazz, Class<?> supertype, ClassLoader classLoader) {
        if (supertype == null) {
            return;
        }
        if (classLoader != null && !DynamicHub.fromClass(supertype).isLoaded()) {
            Class<?> loaded;
            try {
                loaded = classLoader.loadClass(supertype.getName());
            } catch (ClassNotFoundException e) {
                throw throwUnresolvable(supertype, e);
            }
            if (loaded != supertype) {
                throw new LinkageError("Loader " + classLoader + " supplied unexpected class " + loaded.getName() + " for supertype of " + clazz.getName() + " when expecting " + supertype.getName());
            }
        } else {
            throwIfUnresolvable(supertype, classLoader);
        }
    }

    public static void throwIfUnresolvable(Class<?> clazz, ClassLoader classLoader) {
        if (clazz == null) {
            return;
        }
        DynamicHub hub = DynamicHub.fromClass(clazz);
        if (!hub.isLoaded() || !ClassUtil.isSameOrParentLoader(clazz.getClassLoader(), classLoader)) {
            throw throwUnresolvable(clazz, null);
        }
    }

    /** Throw a NoClassDefFoundError with ClassNotFoundException as required by Java VM spec 5.3. */
    private static RuntimeException throwUnresolvable(Class<?> clazz, ClassNotFoundException cause) {
        String name = clazz.getName();
        NoClassDefFoundError error = new NoClassDefFoundError(name.replace('.', '/'));
        error.initCause((cause != null) ? cause : new ClassNotFoundException(name));
        throw error;
    }

    static Class<?> getLoadedForNameOrNull(String name, ClassLoader classLoader) {
        Class<?> clazz = singleton().getLoaded(name);
        if (clazz == null || !ClassUtil.isSameOrParentLoader(clazz.getClassLoader(), classLoader)) {
            return null;
        }
        return clazz;
    }

    private Class<?> getLoaded(String name) {
        lock.lock();
        try {
            return loadedClassesByName.get(name);
        } finally {
            lock.unlock();
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static class TestingBackdoor {
        public static Set<Class<?>> getConfigurationPredefinedClasses() {
            Set<Class<?>> set = new HashSet<>();
            for (Class<?> clazz : singleton().predefinedClassesByHash.getValues()) {
                set.add(clazz);
            }
            return set; // excludes internal classes such as proxy classes
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static Class<?> maybeAdjustLambdaNestHost(String className, Class<?> javaClass, ClassLoader classLoader, Class<?> originalNestHost) {
        Class<?> lambdaNestHost = originalNestHost;
        /*
         * Predefined lambda classes do not contain the address hash of the name at this point.
         * Their names look like capturingClass$$Lambda$stableUniqueHash. Because we cut off the
         * address hash at the end of the lambda name, the nest host for predefined lambda would be
         * lambda itself which is incorrect. The nest host for lambda class should be the nest host
         * of it's capturing class.
         *
         * When {@code java.lang.invoke.InnerClassLambdaMetafactory} tries to define a hidden class,
         * it ends up in {@code java.lang.invoke.MethodHandles#defineClass}. This method requires
         * that the nest host of the class we define is the same as the nest host of the lookup
         * class. For predefined classes, that won't be the case, so we need to re-calculate the
         * nest host for them.
         */
        if (LambdaUtils.isLambdaClassName(className) && PredefinedClassesSupport.isPredefined(javaClass)) {
            Class<?> capturingClass;

            try {
                capturingClass = Class.forName(LambdaUtils.capturingClass(className), false, classLoader);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }

            lambdaNestHost = capturingClass.getNestHost();
        }

        return lambdaNestHost;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static byte[] changeLambdaClassName(byte[] data, String oldName, String newName) {
        ClassDesc oldDesc = ClassDesc.ofInternalName(oldName);
        ClassDesc newDesc = ClassDesc.ofInternalName(newName);

        ClassFile classFile = ClassFile.of(NEW_POOL);
        ClassModel original = classFile.parse(data);

        return classFile.transformClass(original, newDesc,
                        ClassTransform.transformingMethods(
                                        MethodTransform.transformingCode((builder, element) -> {
                                            ClassEntry newClassEntry = builder.constantPool().classEntry(newDesc);
                                            // Pass through any unhandled elements unchanged
                                            if (element instanceof TypeCheckInstruction ti && ti.type().asSymbol().equals(oldDesc)) {
                                                builder.with(TypeCheckInstruction.of(ti.opcode(), newClassEntry));
                                            } else if (element instanceof NewObjectInstruction ti && ti.className().asSymbol().equals(oldDesc)) {
                                                builder.with(NewObjectInstruction.of(newClassEntry));
                                            } else if (element instanceof NewReferenceArrayInstruction ti && ti.componentType().asSymbol().equals(oldDesc)) {
                                                builder.with(NewReferenceArrayInstruction.of(newClassEntry));
                                            } else if (element instanceof NewMultiArrayInstruction ti && ti.arrayType().asSymbol().equals(oldDesc)) {
                                                builder.with(NewMultiArrayInstruction.of(newClassEntry, ti.dimensions()));
                                            } else if (element instanceof InvokeInstruction mi && mi.owner().asSymbol().equals(oldDesc)) {
                                                builder.with(InvokeInstruction.of(mi.opcode(), newClassEntry, mi.name(), mi.type(), mi.isInterface()));
                                            } else if (element instanceof FieldInstruction fi && fi.owner().asSymbol().equals(oldDesc)) {
                                                builder.with(FieldInstruction.of(fi.opcode(), newClassEntry, fi.name(), fi.type()));
                                            } else {
                                                builder.with(element);
                                            }
                                        })));
    }
}
