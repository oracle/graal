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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@TargetClass(className = "jdk.internal.loader.Resource", onlyWith = JDK11OrLater.class)
@SuppressWarnings("unused")
final class Target_jdk_internal_loader_Resource_JDK11OrLater {

}

@TargetClass(className = "sun.misc.Resource", onlyWith = JDK8OrEarlier.class)
@SuppressWarnings("unused")
final class Target_sun_misc_Resource_JDK8OrEarlier {

}

@SuppressWarnings("unchecked")
class ResourcesHelper {

    private static <T> T urlToResource(String resourceName, URL url) {
        try {
            if (url == null) {
                return null;
            }
            URLConnection urlConnection = url.openConnection();
            Object resource = ImageSingletons.lookup(JDKVersionSpecificResourceBuilder.class).buildResource(resourceName, url, urlConnection);
            VMError.guarantee(resource != null);
            return (T) resource;
        } catch (IOException e) {
            return null;
        } catch (ClassCastException classCastException) {
            throw VMError.shouldNotReachHere(classCastException);
        }
    }

    static <T> T nameToResource(String resourceName) {
        return urlToResource(resourceName, nameToResourceURL(resourceName));
    }

    static <T> Enumeration<T> nameToResources(String resourceName) {
        Enumeration<URL> urls = Resources.createURLs(resourceName);
        List<T> resourceURLs = new ArrayList<>();
        while (urls.hasMoreElements()) {
            resourceURLs.add(urlToResource(resourceName, urls.nextElement()));
        }
        return Collections.enumeration(resourceURLs);
    }

    static URL nameToResourceURL(String resourceName) {
        return Resources.createURL(resourceName);
    }

    static InputStream nameToResourceInputStream(String resourceName) throws IOException {
        URL url = nameToResourceURL(resourceName);
        return url != null ? url.openStream() : null;
    }

    static List<URL> nameToResourceListURLs(String resourcesName) {
        Enumeration<URL> urls = Resources.createURLs(resourcesName);
        List<URL> resourceURLs = new ArrayList<>();
        while (urls.hasMoreElements()) {
            resourceURLs.add(urls.nextElement());
        }
        return resourceURLs;
    }

    static Enumeration<URL> nameToResourceEnumerationURLs(String resourcesName) {
        return Collections.enumeration(nameToResourceListURLs(resourcesName));
    }
}

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

    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    private ConcurrentHashMap<String, Object> parallelLockMap;

    /**
     * Recompute ClassLoader.packages; See {@link ClassLoaderSupport} for explanation on why this
     * information must be reset.
     */
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = PackageFieldTransformer.class)//
    @TargetElement(name = "packages", onlyWith = JDK8OrEarlier.class)//
    private HashMap<String, Package> packagesJDK8;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = PackageFieldTransformer.class)//
    @TargetElement(name = "packages", onlyWith = JDK11OrLater.class)//
    private ConcurrentHashMap<String, Package> packagesJDK11;

    @Alias //
    private static ClassLoader scl;

    @Substitute
    public static ClassLoader getSystemClassLoader() {
        VMError.guarantee(scl != null);
        return scl;
    }

    @Alias
    @TargetElement(onlyWith = JDK11OrLater.class)
    native Stream<Package> packages();

    @Delete
    private static native void initSystemClassLoader();

    @Substitute
    private URL getResource(String name) {
        return ResourcesHelper.nameToResourceURL(name);
    }

    @Substitute
    private Enumeration<URL> getResources(String name) {
        return ResourcesHelper.nameToResourceEnumerationURLs(name);
    }

    @Substitute
    public InputStream getResourceAsStream(String name) {
        return Resources.createInputStream(name);
    }

    @Substitute
    public static InputStream getSystemResourceAsStream(String name) {
        return Resources.createInputStream(name);
    }

    @Substitute
    @SuppressWarnings("unused")
    @TargetElement(onlyWith = JDK14OrEarlier.class) //
    /* Substitution for JDK 15 and later is in Target_java_lang_ClassLoader_JDK15OrLater. */
    static void loadLibrary(Class<?> fromClass, String name, boolean isAbsolute) {
        if (isAbsolute) {
            NativeLibrarySupport.singleton().loadLibraryAbsolute(new File(name));
        } else {
            NativeLibrarySupport.singleton().loadLibraryRelative(name);
        }
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

    @Delete
    @TargetElement(onlyWith = JDK16OrEarlier.class)
    native Class<?> findBootstrapClassOrNull(String name);

    // JDK-8265605
    @Delete
    @TargetElement(onlyWith = JDK17OrLater.class, name = "findBootstrapClassOrNull")
    static native Class<?> findBootstrapClassOrNullJDK17OrLater(String name);

    @Substitute
    @SuppressWarnings("unused")
    static void checkClassLoaderPermission(ClassLoader cl, Class<?> caller) {
    }

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    @SuppressWarnings({"unused"})
    Class<?> loadClass(Target_java_lang_Module module, String name) {
        /* The module system is not supported for now, therefore the module parameter is ignored. */
        try {
            return loadClass(name, false);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * All ClassLoaderValue are reset at run time for now. See also
     * {@link Target_jdk_internal_loader_BootLoader#CLASS_LOADER_VALUE_MAP} for resetting of the
     * boot class loader.
     */
    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    @TargetElement(onlyWith = JDK11OrLater.class)//
    ConcurrentHashMap<?, ?> classLoaderValueMap;

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    @SuppressWarnings({"unused"})
    private boolean trySetObjectField(String name, Object obj) {
        throw VMError.unsupportedFeature("JDK11OrLater: Target_java_lang_ClassLoader.trySetObjectField(String name, Object obj)");
    }

    @Substitute //
    @SuppressWarnings({"unused"})
    Object getClassLoadingLock(String className) {
        throw VMError.unsupportedFeature("Target_java_lang_ClassLoader.getClassLoadingLock(String)");
    }

    @Substitute //
    @SuppressWarnings({"unused"}) //
    private Class<?> findLoadedClass0(String name) {
        if (name == null) {
            return null;
        }
        return ClassForNameSupport.forNameOrNull(name, SubstrateUtil.cast(this, ClassLoader.class));
    }

    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    @SuppressWarnings({"unused"})
    public Target_java_lang_Module getUnnamedModule() {
        return DynamicHub.singleModuleReference.get();
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

    /*
     * We are defensive and also handle private native methods by marking them as deleted. If they
     * are reachable, the user is certainly doing something wrong. But we do not want to fail with a
     * linking error.
     */

    @Delete
    private static native void registerNatives();

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
            throw PredefinedClassesSupport.throwNoBytecodeClasses();
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
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private native Class<?> defineClass0(String name, byte[] b, int off, int len, ProtectionDomain pd);

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private native Class<?> defineClass1(String name, byte[] b, int off, int len, ProtectionDomain pd, String source);

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private native Class<?> defineClass2(String name, java.nio.ByteBuffer b, int off, int len, ProtectionDomain pd, String source);

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private native void resolveClass0(Class<?> c);

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static native Class<?> defineClass1(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd, String source);

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static native Class<?> defineClass2(ClassLoader loader, String name, java.nio.ByteBuffer b, int off, int len, ProtectionDomain pd, String source);

    @Delete
    @TargetElement(onlyWith = JDK16OrEarlier.class)
    private native Class<?> findBootstrapClass(String name);

    // JDK-8265605
    @Delete
    @TargetElement(onlyWith = JDK17OrLater.class, name = "findBootstrapClass")
    private static native Class<?> findBootstrapClassJDK17OrLater(String name);

    @Delete
    @TargetElement(onlyWith = JDK14OrEarlier.class)
    private static native String findBuiltinLib(String name);

    @Delete
    private static native Target_java_lang_AssertionStatusDirectives retrieveDirectives();
}

@TargetClass(value = ClassLoader.class, innerClass = "NativeLibrary", onlyWith = JDK14OrEarlier.class)
final class Target_java_lang_ClassLoader_NativeLibrary {

    /*
     * We are defensive and also handle private native methods by marking them as deleted. If they
     * are reachable, the user is certainly doing something wrong. But we do not want to fail with a
     * linking error.
     */

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private native void load(String name, boolean isBuiltin);

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private native long find(String name);

    @Delete
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private native void unload(String name, boolean isBuiltin);

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private native boolean load0(String name, boolean isBuiltin);

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private native long findEntry(String name);

    @Delete
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static native void unload(String name, boolean isBuiltin, long handle);
}

@TargetClass(className = "java.lang.AssertionStatusDirectives") //
final class Target_java_lang_AssertionStatusDirectives {
}

@TargetClass(className = "java.lang.NamedPackage", onlyWith = JDK11OrLater.class) //
final class Target_java_lang_NamedPackage {
}

class PackageFieldTransformer implements RecomputeFieldValue.CustomFieldValueTransformer {
    @Override
    public Object transform(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver, Object originalValue) {
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
