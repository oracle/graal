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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ClassUtil;

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

    public static RuntimeException throwNoBytecodeClasses() {
        if (!supportsBytecodes()) {
            throw VMError.unsupportedFeature("Loading classes from bytecodes at runtime has been disabled. Enable with option: " + ENABLE_BYTECODES_OPTION);
        }
        assert !hasBytecodeClasses();
        throw VMError.unsupportedFeature("No classes have been predefined during the image build to load from bytecodes at runtime.");
    }

    @Fold
    static PredefinedClassesSupport singleton() {
        return ImageSingletons.lookup(PredefinedClassesSupport.class);
    }

    public static String hash(byte[] classData, int offset, int length) {
        try { // Only for lookups, cryptographic properties are irrelevant
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(classData, offset, length);
            return SubstrateUtil.toHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw VMError.shouldNotReachHere(e);
        }
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
            singleton().predefinedClasses.add(clazz);
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
            throw throwNoBytecodeClasses();
        }
        String hash = hash(data, offset, length);
        Class<?> clazz = singleton().predefinedClassesByHash.get(hash);
        if (clazz == null) {
            String name = (expectedName != null) ? expectedName : "(name not specified)";
            throw VMError.unsupportedFeature("Defining a class from new bytecodes at run time is not supported. Class " + name +
                            " with hash " + hash + " was not provided during the image build. Please see BuildConfiguration.md.");
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
                throw new LinkageError("loader " + classLoader + " attempted duplicate class definition for " + clazz.getName() + " defined by " + clazz.getClassLoader());
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
}
