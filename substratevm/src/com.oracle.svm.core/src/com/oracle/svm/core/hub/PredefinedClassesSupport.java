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

import java.io.Serializable;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

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
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

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

    @Fold
    public static boolean supportsBytecodes() {
        return Options.SupportPredefinedClasses.getValue();
    }

    @Fold
    public static boolean hasBytecodeClasses() {
        return supportsBytecodes() && !singleton().predefinedClassesByHash.isEmpty();
    }

    private static final String DEFINITION_NOT_SUPPORTED_MESSAGE = """
                    To make this work, you have the following options:
                      1) Modify or reconfigure your application (or a third-party library) so that it does not generate classes at runtime or load them via non-built-in class loaders.
                      2) If the classes must be generated, try to generate them at build time in a static initializer of a dedicated class.\
                     The generated java.lang.Class objects should be stored in static fields and the dedicated class initialized by passing '--initialize-at-build-time=<class_name>' as the build argument.
                      3) If none of the above is applicable, use the tracing agent to run this application and collect predefined classes with\
                     'java -agentlib:native-image-agent=config-output-dir=<config-dir>,experimental-class-define-support <application-arguments>'.\
                     Note that this is an experimental feature and that it does not guarantee success. Furthermore, the resulting classes can contain entries\
                     from the classpath that should be manually filtered out to reduce image size. The agent should be used only in cases where modifying the source of the project is not possible.
                    """
                    .replace("\n", System.lineSeparator());

    public static RuntimeException throwNoBytecodeClasses(String className) {
        assert !hasBytecodeClasses();
        throw VMError.unsupportedFeature("Classes cannot be defined at runtime when using ahead-of-time Native Image compilation. Tried to define class '" + className + "'" + System.lineSeparator() +
                        DEFINITION_NOT_SUPPORTED_MESSAGE);
    }

    @Fold
    static PredefinedClassesSupport singleton() {
        return ImageSingletons.lookup(PredefinedClassesSupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Set<Class<?>> predefinedClasses = new HashSet<>();

    private final ReentrantLock lock = new ReentrantLock();

    /** Predefined classes by hash. */
    private final EconomicMap<String, Class<?>> predefinedClassesByHash = ImageHeapMap.create();

    /** Predefined classes which have already been loaded, by name. */
    private final EconomicMap<String, Class<?>> loadedClassesByName = EconomicMap.create();

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerClass(String hash, Class<?> clazz) {
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
                        SerializationSupport.isLambdaCapturingClassRegistered(LambdaUtils.capturingClass(lambdaClass.getName()))) {
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
        singleton().predefinedClasses.add(clazz);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean isPredefined(Class<?> clazz) {
        return singleton().predefinedClasses.contains(clazz);
    }

    public static Class<?> loadClass(ClassLoader classLoader, String expectedName, byte[] data, int offset, int length, ProtectionDomain protectionDomain) {
        if (!hasBytecodeClasses()) {
            throw throwNoBytecodeClasses(expectedName);
        }
        String hash = Digest.digest(data, offset, length);
        Class<?> clazz = singleton().predefinedClassesByHash.get(hash);
        if (clazz == null) {
            String name = (expectedName != null) ? expectedName : "(name not specified)";
            throw VMError.unsupportedFeature(
                            "Class " + name + " with hash " + hash + " was not provided during the image build via the 'predefined-classes-config.json' file. Please see 'BuildConfiguration.md'.");
        }
        if (expectedName != null && !expectedName.equals(clazz.getName())) {
            throw new NoClassDefFoundError(clazz.getName() + " (wrong name: " + expectedName + ')');
        }
        loadClass(classLoader, protectionDomain, clazz);
        return clazz;
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
        ClassReader cr = new ClassReader(data);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        cr.accept(new ClassVisitor(Opcodes.ASM5, cw) {
            // Change lambda class name in the bytecode
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, access, newName, signature, superName, interfaces);
            }

            // Change all class references in the lambda class bytecode
            @Override
            public MethodVisitor visitMethod(int access, String originalName, String desc, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5, super.visitMethod(access, originalName, desc, signature, exceptions)) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        String name = type.equals(oldName) ? newName : type;
                        super.visitTypeInsn(opcode, name);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName, String descriptor, boolean isInterface) {
                        String name = owner.equals(oldName) ? newName : owner;
                        super.visitMethodInsn(opcode, name, methodName, descriptor, isInterface);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName, String descriptor) {
                        String name = owner.equals(oldName) ? newName : owner;
                        super.visitFieldInsn(opcode, name, fieldName, descriptor);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);

        return cw.toByteArray();
    }
}
