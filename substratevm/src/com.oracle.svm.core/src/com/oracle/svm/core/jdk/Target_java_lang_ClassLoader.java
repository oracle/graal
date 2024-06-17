/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.io.File;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.util.LazyFinalReference;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.util.Digest;
import jdk.internal.loader.ClassLoaderValue;
import jdk.internal.loader.NativeLibrary;

@TargetClass(ClassLoader.class)
@SuppressWarnings("static-method")
public final class Target_java_lang_ClassLoader {

    @Alias private Target_java_lang_ClassLoader parent;

    /**
     * This field can be safely deleted, but that would require substituting the entire constructor
     * of ClassLoader, so we just reset it. The original javadoc mentions: "The classes loaded by
     * this class loader. The only purpose of this table is to keep the classes from being GC'ed
     * until the loader is GC'ed". This field is only accessed by ClassLoader.addClass() which is "
     * invoked by the VM to record every loaded class with this loader".
     */
    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    private Vector<Class<?>> classes;

    @Alias @RecomputeFieldValue(kind = Kind.NewInstanceWhenNotNull, declClass = ConcurrentHashMap.class)//
    private ConcurrentHashMap<String, Object> parallelLockMap;

    /**
     * Recompute ClassLoader.packages; See {@link ClassLoaderSupport} for explanation on why this
     * information must be reset.
     */
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = PackageFieldTransformer.class)//
    private ConcurrentHashMap<String, Package> packages;

    @Alias //
    private static ClassLoader scl;

    @Substitute
    public static ClassLoader getSystemClassLoader() {
        VMError.guarantee(scl != null);
        return scl;
    }

    @Delete
    private static native ClassLoader initSystemClassLoader();

    @Alias
    public native Enumeration<URL> findResources(String name);

    @Substitute
    private Enumeration<URL> getResources(String name) {
        /* Every class loader sees every resource, so we still need this substitution (GR-19998). */
        Enumeration<URL> urls = ResourcesHelper.nameToResourceEnumerationURLs(name);
        return urls.hasMoreElements() ? urls : findResources(name);
    }

    @Substitute
    @SuppressWarnings("unused")
    static NativeLibrary loadLibrary(Class<?> fromClass, String name) {
        NativeLibrarySupport.singleton().loadLibraryRelative(name);
        // We don't use the JDK's NativeLibraries or NativeLibrary implementations
        return null;
    }

    @Substitute
    @SuppressWarnings("unused")
    static NativeLibrary loadLibrary(Class<?> fromClass, File file) {
        NativeLibrarySupport.singleton().loadLibraryAbsolute(file);
        // We don't use the JDK's NativeLibraries or NativeLibrary implementations
        return null;
    }

    @Substitute
    private Class<?> loadClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }

    @Alias
    protected native Class<?> findLoadedClass(String name);

    @Alias
    protected native Class<?> findClass(String name);

    @Substitute
    @SuppressWarnings("unused")
    Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> clazz = findLoadedClass(name);
        if (clazz != null) {
            return clazz;
        }
        if (!PredefinedClassesSupport.hasBytecodeClasses()) {
            throw new ClassNotFoundException(name);
        }
        if (parent != null) {
            try {
                clazz = parent.loadClass(name);
                if (clazz != null) {
                    return clazz;
                }
            } catch (ClassNotFoundException ignored) {
                // not found in parent loader
            }
        }
        return findClass(name);
    }

    // JDK-8265605
    @Delete
    static native Class<?> findBootstrapClassOrNull(String name);

    @Substitute
    @SuppressWarnings("unused")
    static void checkClassLoaderPermission(ClassLoader cl, Class<?> caller) {
    }

    @Substitute //
    @SuppressWarnings("unused")
    Class<?> loadClass(Module module, String name) {
        /* The module system is not supported for now, therefore the module parameter is ignored. */
        try {
            return loadClass(name, false);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Substitute //
    @SuppressWarnings({"unused"}) //
    private Class<?> findLoadedClass0(String name) {
        return ClassForNameSupport.singleton().forNameOrNull(name, SubstrateUtil.cast(this, ClassLoader.class));
    }

    /**
     * Most {@link ClassLoaderValue}s are reset. For the list of preserved transformers see
     * {@link ClassLoaderValueMapFieldValueTransformer}.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = ClassLoaderValueMapFieldValueTransformer.class)//
    volatile ConcurrentHashMap<?, ?> classLoaderValueMap;

    /**
     * This substitution is a temporary workaround for GR-33896 until GR-36494 is merged.
     */
    @Substitute //
    @SuppressWarnings({"unused"}) //
    ConcurrentHashMap<?, ?> createOrGetClassLoaderValueMap() {
        ConcurrentHashMap<?, ?> result = classLoaderValueMap;
        if (result == null) {
            synchronized (this) {
                result = classLoaderValueMap;
                if (result == null) {
                    classLoaderValueMap = result = new ConcurrentHashMap<>();
                }
            }
        }
        return result;
    }

    @Alias
    native Stream<Package> packages();

    @SuppressWarnings("static-method")
    @Substitute
    public Target_java_lang_Module getUnnamedModule() {
        return ClassLoaderUtil.unnamedModuleReference.get();
    }
    /*
     * The assertion status of classes is fixed at image build time because it is baked into the AOT
     * compiled code. All methods that modify the assertion status are substituted to throw an
     * error.
     *
     * Note that the assertion status can be queried at run time, see the relevant method in
     * DynamicHub.
     */

    @Substitute
    @SuppressWarnings({"unused"})
    private void setDefaultAssertionStatus(boolean enabled) {
        throw VMError.unsupportedFeature("The assertion status of classes is fixed at image build time.");
    }

    @Substitute
    @SuppressWarnings({"unused"})
    private void setPackageAssertionStatus(String packageName, boolean enabled) {
        throw VMError.unsupportedFeature("The assertion status of classes is fixed at image build time.");
    }

    @Substitute
    @SuppressWarnings({"unused"})
    private void setClassAssertionStatus(String className, boolean enabled) {
        throw VMError.unsupportedFeature("The assertion status of classes is fixed at image build time.");
    }

    @Substitute
    @SuppressWarnings({"unused"})
    private void clearAssertionStatus() {
        throw VMError.unsupportedFeature("The assertion status of classes is fixed at image build time.");
    }

    @Delete
    private native void initializeJavaAssertionMaps();

    /*
     * We are defensive and also handle private native methods by marking them as deleted. If they
     * are reachable, the user is certainly doing something wrong. But we do not want to fail with a
     * linking error.
     */

    @Delete
    private static native void registerNatives();

    /**
     * Ignores {@code loader}, as {@link Target_java_lang_ClassLoader#loadLibrary}.
     */
    @Substitute
    private static long findNative(@SuppressWarnings("unused") ClassLoader loader, String entryName) {
        return NativeLibrarySupport.singleton().findSymbol(entryName).rawValue();
    }

    @Substitute
    @SuppressWarnings({"unused", "static-method"})
    Class<?> defineClass(byte[] b, int off, int len) throws ClassFormatError {
        return PredefinedClassesSupport.loadClass(SubstrateUtil.cast(this, ClassLoader.class), null, b, off, len, null);
    }

    @Substitute
    @SuppressWarnings({"unused", "static-method"})
    Class<?> defineClass(String name, byte[] b, int off, int len) throws ClassFormatError {
        return PredefinedClassesSupport.loadClass(SubstrateUtil.cast(this, ClassLoader.class), name, b, off, len, null);
    }

    @Substitute
    @SuppressWarnings({"unused", "static-method"})
    private Class<?> defineClass(String name, byte[] b, int off, int len, ProtectionDomain protectionDomain) {
        return PredefinedClassesSupport.loadClass(SubstrateUtil.cast(this, ClassLoader.class), name, b, off, len, protectionDomain);
    }

    @Substitute
    @SuppressWarnings({"unused", "static-method"})
    private Class<?> defineClass(String name, java.nio.ByteBuffer b, ProtectionDomain protectionDomain) {
        if (!PredefinedClassesSupport.hasBytecodeClasses()) {
            throw PredefinedClassesSupport.throwNoBytecodeClasses(name);
        }
        byte[] array;
        int off;
        int len = b.remaining();
        if (b.hasArray()) {
            array = b.array();
            off = b.position() + b.arrayOffset();
        } else {
            array = new byte[len];
            b.get(array);
            off = 0;
        }
        return PredefinedClassesSupport.loadClass(SubstrateUtil.cast(this, ClassLoader.class), name, array, off, len, null);
    }

    @Substitute
    protected void resolveClass(@SuppressWarnings("unused") Class<?> c) {
        // All classes are already linked at runtime.
    }

    @Delete
    @SuppressWarnings("unused")
    private static native Class<?> defineClass1(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd, String source);

    @Delete
    private static native Class<?> defineClass2(ClassLoader loader, String name, java.nio.ByteBuffer b, int off, int len, ProtectionDomain pd, String source);

    @Substitute
    @SuppressWarnings("unused")
    private static Class<?> defineClass0(ClassLoader loader, Class<?> lookup, String name, byte[] b, int off, int len, ProtectionDomain pd, boolean initialize, int flags, Object classData) {
        String actualName = name;
        if (LambdaUtils.isLambdaClassName(name)) {
            actualName += Digest.digest(b);
        }
        return PredefinedClassesSupport.loadClass(loader, actualName.replace('/', '.'), b, off, b.length, null);
    }

    // JDK-8265605
    @Delete
    private static native Class<?> findBootstrapClass(String name);

    @Delete
    private static native Target_java_lang_AssertionStatusDirectives retrieveDirectives();
}

@TargetClass(className = "java.lang.AssertionStatusDirectives") //
final class Target_java_lang_AssertionStatusDirectives {
}

class PackageFieldTransformer implements FieldValueTransformerWithAvailability {

    @Override
    public ValueAvailability valueAvailability() {
        return ValueAvailability.AfterAnalysis;
    }

    @Override
    public Object transform(Object receiver, Object originalValue) {
        assert receiver instanceof ClassLoader;

        /* JDK9+ stores packages in a ConcurrentHashMap, while 8 and before use a HashMap. */
        boolean useConcurrentHashMap = originalValue instanceof ConcurrentHashMap;

        /* Retrieving initial package state for this class loader. */
        ConcurrentHashMap<String, Package> packages = ClassLoaderSupport.getRegisteredPackages((ClassLoader) receiver);
        if (packages == null) {
            /* No package state available - have to create clean state. */
            return useConcurrentHashMap ? new ConcurrentHashMap<String, Package>() : new HashMap<String, Package>();
        } else {
            return useConcurrentHashMap ? packages : new HashMap<>(packages);
        }
    }
}

final class ClassLoaderUtil {

    public static final LazyFinalReference<Target_java_lang_Module> unnamedModuleReference = new LazyFinalReference<>(Target_java_lang_Module::new);
}

@TargetClass(className = "java.lang.ClassLoader", innerClass = "ParallelLoaders")
final class Target_java_lang_ClassLoader_ParallelLoaders {

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    private static Set<Class<? extends ClassLoader>> loaderTypes = Collections.newSetFromMap(new WeakHashMap<>());
}
