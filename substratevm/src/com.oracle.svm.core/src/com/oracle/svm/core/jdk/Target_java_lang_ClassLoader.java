/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.ref.WeakReference;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.RuntimeClassLoading.ClassDefinitionInfo;
import com.oracle.svm.core.hub.registry.AbstractClassRegistry;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.util.Digest;
import jdk.internal.loader.ClassLoaderValue;
import jdk.internal.loader.NativeLibrary;

/**
 * Note that we currently disable parallel class loading at image run time because the
 * {@linkplain RuntimeClassLoading runtime class loading} implementation doesn't support parallel
 * class loading (GR-62338). In particular this means:
 * <ul>
 * <li>{@code ClassLoader.parallelLockMap} is reset to null for class loaders that are part of the
 * image heap.</li>
 * <li>{@code ClassLoader.assertionLock} is reset to "this" for class loaders that are part of the
 * image heap.</li>
 * <li>{@code ClassLoader.ParallelLoaders.loaderTypes} is reset to an empty set.</li>
 * <li>Class loaders created at runtime will also have parallel class loading disabled since a class
 * loader can only be parallel if their superclass is parallel.</li>
 * </ul>
 * <p>
 * Depending on the value of the {@code ClassForNameRespectsClassLoader} flag, methods that lookup
 * classes by name (such as {@code ClassLoader#findLoadedClass},
 * {@code ClassLoader#findBootstrapClass}, or {@code Class#forName}) will either ignore the class
 * loader argument and find classes in a global namespace or respect it and look names up in
 * per-class loader {@linkplain ClassRegistries registries}.
 */
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
    ArrayList<Class<?>> classes;

    @Alias @RecomputeFieldValue(kind = Kind.Reset, isFinal = true)// GR-62338
    public ConcurrentHashMap<String, Object> parallelLockMap;

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = AssertionLockComputer.class, isFinal = true) // GR-62338
    private Object assertionLock;

    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    private boolean defaultAssertionStatus;

    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    private Map<String, Boolean> packageAssertionStatus;

    @Alias @RecomputeFieldValue(kind = Kind.Reset)//
    Map<String, Boolean> classAssertionStatus;

    @Delete private static ClassLoader scl;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = ClassRegistries.ClassRegistryComputer.class)//
    public volatile AbstractClassRegistry classRegistry;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = ResourceLoaderIdComputer.class)//
    public int resourceLoaderId;

    /**
     * Used to implement
     * {@linkplain com.oracle.svm.espresso.shared.constraints.LoadingConstraintsShared loading
     * constraints} in Crema, in such a way that the constraints storage does not prevent collection
     * of loader or their classes.
     * <p>
     * These weak references are registered to the {@link java.lang.ref.ReferenceQueue queue} in
     * {@link ClassRegistries}{@code .collectedLoaders}, in order to get notified when such a loader
     * is collected.
     * <p>
     * They are attached to the corresponding loader so that there is no need to create multiple
     * references to a single loader.
     */
    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = ClassRegistries.WeakSelfComputer.class) //
    @TargetElement(onlyWith = RuntimeClassLoading.WithRuntimeClassLoading.class) //
    public volatile WeakReference<Object> weakSelf;

    @Alias
    static native ClassLoader getBuiltinAppClassLoader();

    @Substitute
    public static ClassLoader getSystemClassLoader() {
        /*
         * Setting custom SystemClassLoader via java.system.class.loader system property currently
         * not supported for native-images.
         */
        return getBuiltinAppClassLoader();
    }

    @Delete
    private static native ClassLoader initSystemClassLoader();

    @Alias
    public native Enumeration<URL> findResources(String name);

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    private Enumeration<URL> getResources(String name) {
        /* Every class loader sees every resource, so we still need this substitution (GR-19998). */
        Enumeration<URL> urls = ResourcesHelper.nameToResourceEnumerationURLs(name);
        return urls.hasMoreElements() ? urls : findResources(name);
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    @SuppressWarnings("unused")
    static NativeLibrary loadLibrary(Class<?> fromClass, String name) {
        NativeLibrarySupport.singleton().loadLibraryRelative(name);
        // We don't use the JDK's NativeLibraries or NativeLibrary implementations
        return null;
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    @SuppressWarnings("unused")
    static NativeLibrary loadLibrary(Class<?> fromClass, File file) {
        NativeLibrarySupport.singleton().loadLibraryAbsolute(file);
        // We don't use the JDK's NativeLibraries or NativeLibrary implementations
        return null;
    }

    @Alias
    public static native long findNative(ClassLoader loader, Class<?> clazz, String entryName, String javaName);

    @Alias
    public native String nameAndId();

    @Alias
    protected native Class<?> findLoadedClass(String name);

    @Alias
    protected native Class<?> findClass(String name);

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
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
                clazz = parent.loadClass(name, resolve);
                if (clazz != null) {
                    return clazz;
                }
            } catch (ClassNotFoundException ignored) {
                // not found in parent loader
            }
        }
        return findClass(name);
    }

    @Substitute
    @TargetElement(onlyWith = RuntimeClassLoading.NoRuntimeClassLoading.class)
    @SuppressWarnings("unused")
    Class<?> loadClass(Module module, String name) {
        /*
         * When runtime class loading is disabled, named-module lookups still need to resolve
         * classes already linked into the image.
         */
        try {
            return loadClass(name, false);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Substitute //
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/java.base/share/native/libjava/ClassLoader.c#L320-L329")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/hotspot/share/prims/jvm.cpp#L1056-L1096")
    @SuppressWarnings({"unused"}) //
    private Class<?> findLoadedClass0(String name) {
        if (name == null) {
            return null;
        }

        /*
         * HotSpot supports both dot- and slash-names here as well as array types The only caller
         * (findLoadedClass) errors out on slash-names and array types so we assume dot-names
         */
        assert !name.contains("/") && !name.startsWith("[");
        return ClassRegistries.findLoadedClass(name, SubstrateUtil.cast(this, ClassLoader.class));
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

    @Alias
    native Package definePackage(String packageName, Module module);

    @Substitute
    private static Target_java_lang_AssertionStatusDirectives retrieveDirectives() {
        RuntimeAssertionsSupport.ClassLoaderAssertionStatusDirectives assertionSupport = RuntimeAssertionsSupport.singleton().createClassLoaderAssertionStatusDirectives();
        Target_java_lang_AssertionStatusDirectives directives = new Target_java_lang_AssertionStatusDirectives();
        directives.classes = assertionSupport.classes();
        directives.classEnabled = assertionSupport.classEnabled();
        directives.packages = assertionSupport.packages();
        directives.packageEnabled = assertionSupport.packageEnabled();
        directives.deflt = assertionSupport.deflt();
        return directives;
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
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    private Class<?> defineClass(String name, byte[] b, int off, int len, ProtectionDomain protectionDomain) {
        return RuntimeClassLoading.defineClass(SubstrateUtil.cast(this, ClassLoader.class), name, b, off, len, new ClassDefinitionInfo(protectionDomain));
    }

    @Substitute
    @SuppressWarnings({"unused", "static-method"})
    @TargetElement(onlyWith = ClassRegistries.IgnoresClassLoader.class)
    private Class<?> defineClass(String name, java.nio.ByteBuffer b, ProtectionDomain protectionDomain) {
        return defineClass2(SubstrateUtil.cast(this, ClassLoader.class), name, b, b.position(), b.remaining(), protectionDomain, null);
    }

    @Delete
    @TargetElement(name = "defineClass1", onlyWith = ClassRegistries.IgnoresClassLoader.class)
    @SuppressWarnings("unused")
    private static native Class<?> defineClass1Deleted(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd, String source);

    @Delete
    @TargetElement(name = "defineClass2", onlyWith = ClassRegistries.IgnoresClassLoader.class)
    private static native Class<?> defineClass2Deleted(ClassLoader loader, String name, java.nio.ByteBuffer b, int off, int len, ProtectionDomain pd, String source);

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.RespectsClassLoader.class)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/java.base/share/native/libjava/ClassLoader.c#L71-L151")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/hotspot/share/prims/jvm.cpp#L1051-L1054")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/hotspot/share/prims/jvm.cpp#L857-L896")
    private static Class<?> defineClass1(ClassLoader loader, String name, byte[] b, int off, int len, ProtectionDomain pd, @SuppressWarnings("unused") String source) {
        // Note that if name is not null, it is a binary name in either / or .-form
        return RuntimeClassLoading.defineClass(loader, name, b, off, len, new ClassDefinitionInfo(pd));
    }

    @Substitute
    @TargetElement(onlyWith = ClassRegistries.RespectsClassLoader.class)
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/java.base/share/native/libjava/ClassLoader.c#L153-L213")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/hotspot/share/prims/jvm.cpp#L1051-L1054")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/hotspot/share/prims/jvm.cpp#L857-L896")
    private static Class<?> defineClass2(ClassLoader loader, String name, java.nio.ByteBuffer b, int off, int len, ProtectionDomain pd, @SuppressWarnings("unused") String source) {
        // Note that if name is not null, it is a binary name in either / or .-form
        // only bother extracting the bytes if it has a chance to work
        if (PredefinedClassesSupport.hasBytecodeClasses() || RuntimeClassLoading.isSupported()) {
            byte[] array;
            int offset;
            if (b.hasArray()) {
                array = b.array();
                offset = off + b.arrayOffset();
            } else {
                array = new byte[len];
                b.get(off, array);
                offset = 0;
            }
            return RuntimeClassLoading.defineClass(loader, name, array, offset, len, new ClassDefinitionInfo(pd));
        }
        throw RuntimeClassLoading.throwNoBytecodeClasses(name);
    }

    @Substitute
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/java.base/share/native/libjava/ClassLoader.c#L215-L283")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/hotspot/share/prims/jvm.cpp#L1039-L1049")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/hotspot/share/prims/jvm.cpp#L909-L1022")
    private static Class<?> defineClass0(ClassLoader loader, Class<?> lookup, String name, byte[] b, int off, int len, ProtectionDomain pd,
                    @SuppressWarnings("unused") boolean initialize, int flags, Object classData) {
        // Note that if name is not null, it is a binary name in either / or .-form
        String actualName = name;
        assert !(PredefinedClassesSupport.hasBytecodeClasses() && RuntimeClassLoading.isSupported());
        if (!RuntimeClassLoading.isSupported() && LambdaUtils.isLambdaClassName(name)) {
            actualName += Digest.digest(b);
        }
        boolean isNestMate = (flags & ClassLoaderHelper.NESTMATE_CLASS) != 0;
        boolean isHidden = (flags & ClassLoaderHelper.HIDDEN_CLASS) != 0;
        boolean isStrong = (flags & ClassLoaderHelper.STRONG_LOADER_LINK) != 0;
        boolean vmAnnotations = (flags & ClassLoaderHelper.ACCESS_VM_ANNOTATIONS) != 0;
        Class<?> nest = null;
        if (isNestMate) {
            nest = lookup.getNestHost();
        }
        ClassDefinitionInfo info;
        if (isHidden) {
            info = new ClassDefinitionInfo(pd, nest, classData, isStrong, vmAnnotations);
        } else {
            if (classData != null) {
                throw new IllegalArgumentException("Class data is only applicable for hidden classes");
            }
            if (isNestMate) {
                throw new IllegalArgumentException("Dynamic nestmate is only applicable for hidden classes");
            }
            if (!isStrong) {
                throw new IllegalArgumentException("An ordinary class must be strongly referenced by its defining loader");
            }
            if (vmAnnotations) {
                throw new IllegalArgumentException("VM annotations only allowed for hidden classes");
            }
            if (flags != ClassLoaderHelper.STRONG_LOADER_LINK) {
                throw new IllegalArgumentException(String.format("invalid flags 0x%x", flags));
            }
            info = new ClassDefinitionInfo(pd);
        }
        Class<?> cls = RuntimeClassLoading.defineClass(loader, actualName, b, off, len, info);
        DynamicHub hub = DynamicHub.fromClass(cls);
        if (initialize) {
            hub.ensureInitialized();
        } else {
            hub.getClassInitializationInfo().ensureLinked(hub);
        }
        return cls;
    }

    @Substitute
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/java.base/share/native/libjava/ClassLoader.c#L288-L328")
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+16/src/hotspot/share/prims/jvm.cpp#L780-L800")
    static Class<?> findBootstrapClass(String name) {
        /*
         * HotSpot supports both dot- and slash-names here as well as array types The only caller
         * (findBootstrapClassOrNull) errors out on slash-names and array types.
         */
        assert !name.contains("/") && !name.startsWith("[");
        return ClassRegistries.findBootstrapClass(name);
    }

}

final class ClassLoaderHelper {
    static final int NESTMATE_CLASS;
    static final int HIDDEN_CLASS;
    static final int STRONG_LOADER_LINK;
    static final int ACCESS_VM_ANNOTATIONS;
    static {
        Class<?> constantsClass = ReflectionUtil.lookupClass("java.lang.invoke.MethodHandleNatives$Constants");
        NESTMATE_CLASS = ReflectionUtil.readStaticField(constantsClass, "NESTMATE_CLASS");
        HIDDEN_CLASS = ReflectionUtil.readStaticField(constantsClass, "HIDDEN_CLASS");
        STRONG_LOADER_LINK = ReflectionUtil.readStaticField(constantsClass, "STRONG_LOADER_LINK");
        ACCESS_VM_ANNOTATIONS = ReflectionUtil.readStaticField(constantsClass, "ACCESS_VM_ANNOTATIONS");
    }
}

@TargetClass(className = "java.lang.AssertionStatusDirectives") //
final class Target_java_lang_AssertionStatusDirectives {
    @Alias String[] classes;
    @Alias boolean[] classEnabled;
    @Alias String[] packages;
    @Alias boolean[] packageEnabled;
    @Alias boolean deflt;
}

@TargetClass(className = "java.lang.NamedPackage") //
final class Target_java_lang_NamedPackage {
}

@TargetClass(className = "java.lang.ClassLoader", innerClass = "ParallelLoaders")
final class Target_java_lang_ClassLoader_ParallelLoaders {

    @Alias //
    @RecomputeFieldValue(kind = Kind.FromAlias) // GR-62338
    private static Set<Class<? extends ClassLoader>> loaderTypes = Collections.newSetFromMap(new WeakHashMap<>());
}

final class AssertionLockComputer implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        assert receiver != null;
        return receiver;
    }
}

@Platforms(Platform.HOSTED_ONLY.class)
final class ResourceLoaderIdComputer implements FieldValueTransformer {
    @Override
    public Object transform(Object receiver, Object originalValue) {
        return ResourceLoaderKeys.hosted().getResourceLoaderId((ClassLoader) receiver);
    }
}
