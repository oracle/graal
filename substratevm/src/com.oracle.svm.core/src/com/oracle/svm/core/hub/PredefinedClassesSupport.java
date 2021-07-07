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
import org.graalvm.collections.UnmodifiableEconomicMap;
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

    public static RuntimeException throwBytecodeSupportDisabled() {
        assert !supportsBytecodes();
        throw VMError.unsupportedFeature("Loading classes from bytecodes at runtime is not supported. " +
                        "Consider predefining classes and enable class predefinition during the image build using option: " + ENABLE_BYTECODES_OPTION);
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

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean isPredefined(Class<?> clazz) {
        return singleton().predefinedClasses.contains(clazz);
    }

    public static Class<?> loadClass(ClassLoader classLoader, String expectedName, byte[] data, int offset, int length, ProtectionDomain protectionDomain) {
        if (!supportsBytecodes()) {
            throw throwBytecodeSupportDisabled();
        }
        String hash = hash(data, offset, length);
        Class<?> clazz = singleton().predefinedClassesByHash.get(hash);
        if (clazz == null) {
            String name = (expectedName != null) ? expectedName : "(name not specified)";
            throw VMError.unsupportedFeature("Defining a class from new bytecodes at run time is not supported. Class " + name +
                            " with hash " + hash + " was not provided during the image build. Please see BuildConfiguration.md.");
        }
        return singleton().load(classLoader, protectionDomain, clazz);
    }

    private Class<?> load(ClassLoader classLoader, ProtectionDomain protectionDomain, Class<?> clazz) {
        lock.lock();
        try {
            boolean alreadyLoaded = (loadedClassesByName.get(clazz.getName()) == clazz);
            if (alreadyLoaded) {
                if (classLoader == clazz.getClassLoader()) {
                    throw new LinkageError("loader " + classLoader + " attempted duplicate class definition for " + clazz.getName() + " defined by " + clazz.getClassLoader());
                } else {
                    throw VMError.unsupportedFeature("A predefined class can be loaded (defined) at runtime only once by a single class loader. " +
                                    "Hierarchies of class loaders and distinct sets of classes are not supported. Class " + clazz.getName() + " has already " +
                                    "been loaded by class loader: " + clazz.getClassLoader());
                }
            }

            throwIfUnresolvable(clazz.getSuperclass(), classLoader);
            for (Class<?> intf : clazz.getInterfaces()) {
                throwIfUnresolvable(intf, classLoader);
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
            return clazz;
        } finally {
            lock.unlock();
        }
    }

    public static void throwIfUnresolvable(Class<?> clazz, ClassLoader classLoader) {
        if (clazz == null) {
            return;
        }
        DynamicHub hub = DynamicHub.fromClass(clazz);
        if (!hub.isLoaded()) {
            throwResolutionError(clazz.getName());
        }
        if (!isSameOrParent(clazz.getClassLoader(), classLoader)) { // common case: same loader
            throwResolutionError(clazz.getName());
        }
    }

    private static void throwResolutionError(String name) {
        // NoClassDefFoundError with ClassNotFoundException required by Java VM specification, 5.3
        NoClassDefFoundError error = new NoClassDefFoundError(name.replace('.', '/'));
        error.initCause(new ClassNotFoundException(name));
        throw error;
    }

    static Class<?> getLoadedForNameOrNull(String name, ClassLoader classLoader) {
        Class<?> clazz = singleton().getLoaded(name);
        if (clazz == null || !isSameOrParent(clazz.getClassLoader(), classLoader)) {
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

    private static boolean isSameOrParent(ClassLoader parent, ClassLoader child) {
        if (parent == null) {
            return true; // boot loader: any loader's parent
        }
        ClassLoader c = child;
        do {
            if (c == parent) { // common case
                return true;
            }
            c = c.getParent();
        } while (c != null);
        return false;
    }

    public static class TestingBackdoor {
        public static UnmodifiableEconomicMap<String, Class<?>> getPredefinedClasses() {
            return singleton().predefinedClassesByHash;
        }
    }
}
