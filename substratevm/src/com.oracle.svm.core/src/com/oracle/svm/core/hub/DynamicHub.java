/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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

//Checkstyle: allow reflection

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.core.common.calc.UnsignedMath;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.util.DirectAnnotationAccess;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Hybrid;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.jdk.Package_jdk_internal_reflect;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.jdk.Target_java_lang_ClassLoader;
import com.oracle.svm.core.jdk.Target_java_lang_Module;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.LazyFinalReference;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;
import java.io.File;
import java.net.MalformedURLException;
import java.security.CodeSource;
import java.security.cert.Certificate;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.nativeimage.ProcessProperties;
import sun.security.util.SecurityConstants;

@Hybrid
@Substitute
@TargetClass(java.lang.Class.class)
@SuppressWarnings({"static-method", "serial"})
@SuppressFBWarnings(value = "Se", justification = "DynamicHub must implement Serializable for compatibility with java.lang.Class, not because of actual serialization")
public final class DynamicHub implements JavaKind.FormatWithToString, AnnotatedElement, java.lang.reflect.Type, GenericDeclaration, Serializable {

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];

    /* Value copied from java.lang.Class. */
    private static final int SYNTHETIC = 0x00001000;

    /**
     * The name of the class this hub is representing, as defined in {@link Class#getName()}.
     */
    private String name;

    /**
     * Encoding of the object or array size. Decode using {@link LayoutEncoding}.
     */
    private int layoutEncoding;

    /**
     * Unique id number for this type, used for fast type checks and type casts.
     */
    private int typeID;

    /**
     * The offset of the synthetic field which stores whatever is used for monitorEnter/monitorExit
     * by an instance of this class. If 0, then instances of this class can not be locked.
     * <p>
     * A class has a monitor field if an instance of this class may be an argument to a
     * "synchronized" statement. The current implementation stores a reference to a
     * {@link java.util.concurrent.locks.ReentrantLock}, which will be allocated the first time an
     * instance is locked.
     */
    private int monitorOffset;

    /**
     * The offset of the synthetic hash-code field which stores the identity hash-code for an
     * instance of the class.
     * <p>
     * If 0, the class has no hash-code field. A class has a hash-code field if an instance of this
     * class may be a parameter to {@link System#identityHashCode(Object)} or the this-parameter to
     * {@link Object#hashCode()}. It stores a random hash-code, which is generated at the first call
     * to one of those methods.
     */
    private int hashCodeOffset;

    /**
     * The result of {@link Class#isLocalClass()}.
     */
    private boolean isLocalClass;

    /**
     * Has the type been discovered as instantiated by the static analysis?
     */
    private boolean isInstantiated;

    /**
     * The {@link Modifier modifiers} of this class.
     */
    private final int modifiers;

    /**
     * The hub for the superclass, or null if an interface or primitive type.
     *
     * @see Class#getSuperclass()
     */
    private final DynamicHub superHub;

    /**
     * The hub for the component type of an array, or null if this hub is not an array hub.
     *
     * @see Class#getComponentType()
     */
    private final DynamicHub componentHub;

    /**
     * The hub for an array of this type, or null if the array type has been determined as
     * uninstantiated by the static analysis.
     */
    private DynamicHub arrayHub;

    /**
     * The hub for the enclosing class, or null if no enclosing class.
     * <p>
     * The value is lazily initialized to break cycles. But it is initialized during static
     * analysis, so we do not have to annotate is as an {@link UnknownObjectField}.
     *
     * @see Class#getEnclosingClass()
     */
    private DynamicHub enclosingClass;

    /**
     * The interfaces that this class implements. Either null (no interfaces), a {@link DynamicHub}
     * (one interface), or a {@link DynamicHub}[] array (more than one interface).
     */
    private Object interfacesEncoding;

    private int[] assignableFromMatches;

    /**
     * Reference to a list of enum values for subclasses of {@link Enum}; null otherwise.
     */
    private Object enumConstantsReference;

    /**
     * Reference map information for this hub. The byte[] array encoding data is available via
     * {@link DynamicHubSupport#getReferenceMapEncoding()}.
     */
    private int referenceMapIndex;

    /**
     * Back link to the SubstrateType used by the substrate meta access. Only used for the subset of
     * types for which a SubstrateType exists.
     */
    private SharedType metaType;

    /**
     * Source file name if known; null otherwise.
     */
    private String sourceFileName;

    /**
     * The annotations of this class. This field holds either null (no annotations), an Annotation
     * (one annotation), or an Annotation[] array (more than one annotation). This eliminates the
     * need for an array for the case that there are less than two annotations.
     */
    private Object annotationsEncoding;

    /**
     * Class/superclass/implemented interfaces has default methods. Necessary metadata for class
     * initialization, but even for classes/interfaces that are already initialized during image
     * generation, so it cannot be a field in {@link ClassInitializationInfo}.
     */
    private boolean hasDefaultMethods;

    /**
     * Directly declares default methods. Necessary metadata for class initialization, but even for
     * interfaces that are already initialized during image generation, so it cannot be a field in
     * {@link ClassInitializationInfo}.
     */
    private boolean declaresDefaultMethods;

    /**
     * Metadata for running class initializers at run time. Refers to a singleton marker object for
     * classes/interfaces already initialized during image generation, i.e., this field is never
     * null at run time.
     */
    private ClassInitializationInfo classInitializationInfo;

    /**
     * Classloader used for loading this class during image-build time.
     */
    private final Target_java_lang_ClassLoader classloader;

    /**
     * Bits used for instance-of checks. A bit is set for each type, which an object with this HUB
     * is an instance of.
     * <p>
     * This set only includes types for which no trivial type-ID range check can be done, i.e.
     * interface types, which are "distributed" over the type hierarchy. Therefore this bit-set is
     * relatively small (usually < 64 bits).
     * <p>
     * This bit-set is directly located in the layout of {@link DynamicHub} (see {@link Hybrid}). It
     * is accessed in the instance-of snippet with {@link ObjectAccess}.
     */
    @Hybrid.Bitset private BitSet instanceOfBits;

    @Hybrid.Array private CFunctionPointer[] vtable;

    private GenericInfo genericInfo;
    private AnnotatedSuperInfo annotatedSuperInfo;

    /**
     * Final fields in subsituted classes are treated as implicitly RecomputeFieldValue even when
     * not annotated with @RecomputeFieldValue. Their name must not match a field in the original
     * class, i.e., allPermDomain.
     */
    private static final LazyFinalReference<java.security.ProtectionDomain> allPermDomainReference = new LazyFinalReference<>(() -> {
        java.security.Permissions perms = new java.security.Permissions();
        perms.add(SecurityConstants.ALL_PERMISSION);
        CodeSource cs;
        try {
            // Try to use executable image's name as code source for the class.
            // The file location can be used by Java code to determine its location on disk, similar
            // to argv[0].
            cs = new CodeSource(new File(ProcessProperties.getExecutableName()).toURI().toURL(), (Certificate[]) null);
        } catch (MalformedURLException ex) {
            // This should not really happen; the file is cannonicalized, absolute, so it should
            // always have file:// URL.
            cs = null;
        }
        return new java.security.ProtectionDomain(cs, perms);
    });

    public static final LazyFinalReference<Target_java_lang_Module> singleModuleReference = new LazyFinalReference<>(Target_java_lang_Module::new);

    /**
     * Final fields in subsituted classes are treated as implicitly RecomputeFieldValue even when
     * not annotated with @RecomputeFieldValue. Their name must not match a field in the original
     * class, i.e., packageName.
     */
    private final LazyFinalReference<String> packageNameReference = new LazyFinalReference<>(this::computePackageName);

    @Platforms(Platform.HOSTED_ONLY.class)
    public DynamicHub(String name, boolean isLocalClass, DynamicHub superType, DynamicHub componentHub, String sourceFileName, int modifiers,
                    Target_java_lang_ClassLoader classLoader) {
        this.name = name;
        this.isLocalClass = isLocalClass;
        this.superHub = superType;
        this.componentHub = componentHub;
        this.sourceFileName = sourceFileName;
        this.modifiers = modifiers;
        this.classloader = classLoader;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setClassInitializationInfo(ClassInitializationInfo classInitializationInfo, boolean hasDefaultMethods, boolean declaresDefaultMethods) {
        this.classInitializationInfo = classInitializationInfo;
        this.hasDefaultMethods = hasDefaultMethods;
        this.declaresDefaultMethods = declaresDefaultMethods;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setData(int layoutEncoding, int typeID, int monitorOffset, int hashCodeOffset, int[] assignableFromMatches, BitSet instanceOfBits,
                    CFunctionPointer[] vtable, long referenceMapIndex, boolean isInstantiated) {
        this.layoutEncoding = layoutEncoding;
        this.typeID = typeID;
        this.monitorOffset = monitorOffset;
        this.hashCodeOffset = hashCodeOffset;
        this.assignableFromMatches = assignableFromMatches;
        this.instanceOfBits = instanceOfBits;
        this.vtable = vtable;

        if ((int) referenceMapIndex != referenceMapIndex) {
            throw VMError.shouldNotReachHere("Reference map index not within integer range, need to switch field from int to long");
        }
        this.referenceMapIndex = (int) referenceMapIndex;
        this.isInstantiated = isInstantiated;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setArrayHub(DynamicHub arrayHub) {
        assert (this.arrayHub == null || this.arrayHub == arrayHub) && arrayHub != null;
        assert arrayHub.getComponentHub() == this;
        this.arrayHub = arrayHub;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setEnclosingClass(DynamicHub enclosingClass) {
        assert (this.enclosingClass == null || this.enclosingClass == enclosingClass) && enclosingClass != null;
        this.enclosingClass = enclosingClass;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setGenericInfo(GenericInfo genericInfo) {
        this.genericInfo = genericInfo;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public GenericInfo getGenericInfo() {
        return genericInfo;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setAnnotatedSuperInfo(AnnotatedSuperInfo annotatedSuperInfo) {
        this.annotatedSuperInfo = annotatedSuperInfo;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public AnnotatedSuperInfo getAnnotatedSuperInfo() {
        return annotatedSuperInfo;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setInterfacesEncoding(Object interfacesEncoding) {
        this.interfacesEncoding = interfacesEncoding;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Object getInterfacesEncoding() {
        return interfacesEncoding;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setAnnotationsEncoding(Object annotationsEncoding) {
        this.annotationsEncoding = annotationsEncoding;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Object getAnnotationsEncoding() {
        return annotationsEncoding;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean shouldInitEnumConstants() {
        return enumConstantsReference == null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void initEnumConstants(Enum<?>[] enumConstants) {
        /* Enum is eagerly initialized, so no need for `LazyFinalReference`. */
        enumConstantsReference = enumConstants;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void initEnumConstantsAtRuntime(Class<?> enumClass) {
        /* Adapted from `Class.getEnumConstantsShared`. */
        try {
            Method values = ReflectionUtil.lookupMethod(enumClass, "values");
            enumConstantsReference = new LazyFinalReference<>(() -> initEnumConstantsAtRuntime(values));
        } catch (ReflectionUtilError e) {
            /*
             * This can happen when users concoct enum-like classes that don't comply with the enum
             * spec.
             */
            enumConstantsReference = null;
        } catch (NoClassDefFoundError e) {
            /*
             * This can happen when an enum references a missing class. So, in order to match the
             * JVM behaviour, we rethrow the error at runtime.
             */
            String message = e.getMessage();
            enumConstantsReference = new LazyFinalReference<>(() -> throwNoClassDefFoundErrorAtRuntime(message));
        }
    }

    /** Executed at runtime. */
    private static Object initEnumConstantsAtRuntime(Method values) {
        try {
            return values.invoke(null);
        } catch (InvocationTargetException | IllegalAccessException e) {
            /*
             * These can happen when users concoct enum-like classes that don't comply with the enum
             * spec.
             */
            return null;
        }
    }

    /** Executed at runtime. */
    private static Object throwNoClassDefFoundErrorAtRuntime(String message) {
        throw new NoClassDefFoundError(message);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setMetaType(SharedType metaType) {
        this.metaType = metaType;
    }

    public boolean hasDefaultMethods() {
        return hasDefaultMethods;
    }

    public boolean declaresDefaultMethods() {
        return declaresDefaultMethods;
    }

    public ClassInitializationInfo getClassInitializationInfo() {
        return classInitializationInfo;
    }

    public boolean isInitialized() {
        return classInitializationInfo.isInitialized();
    }

    public void ensureInitialized() {
        if (!classInitializationInfo.isInitialized()) {
            classInitializationInfo.initialize(this);
        }
    }

    public SharedType getMetaType() {
        return metaType;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getLayoutEncoding() {
        return layoutEncoding;
    }

    public int getTypeID() {
        return typeID;
    }

    public int getMonitorOffset() {
        return monitorOffset;
    }

    public int getHashCodeOffset() {
        return hashCodeOffset;
    }

    public DynamicHub getSuperHub() {
        return superHub;
    }

    public DynamicHub getComponentHub() {
        return componentHub;
    }

    public DynamicHub getArrayHub() {
        return arrayHub;
    }

    public int[] getAssignableFromMatches() {
        return assignableFromMatches;
    }

    public int getReferenceMapIndex() {
        return referenceMapIndex;
    }

    public boolean isInstantiated() {
        return isInstantiated;
    }

    public static DynamicHub fromClass(Class<?> clazz) {
        return SubstrateUtil.cast(clazz, DynamicHub.class);
    }

    /*
     * Note that this method must be a static method and not an instance method, otherwise null
     * values cannot be converted.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Class<?> toClass(DynamicHub hub) {
        return SubstrateUtil.cast(hub, Class.class);
    }

    @Substitute
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getName() {
        return name;
    }

    public boolean isInstanceClass() {
        // Special handling for hybrids, which are arrays from the point of view of LayoutEncoding.
        return LayoutEncoding.isInstance(getLayoutEncoding()) || (LayoutEncoding.isArray(getLayoutEncoding()) && name.charAt(0) != '[');
    }

    @Substitute
    public boolean isArray() {
        // Cannot use LayoutEncoding.isArray because it returns the wrong result for hybrids.
        return name.charAt(0) == '[';
    }

    @Substitute
    public boolean isInterface() {
        return LayoutEncoding.isInterface(getLayoutEncoding());
    }

    @Substitute
    public boolean isPrimitive() {
        return LayoutEncoding.isPrimitive(getLayoutEncoding());
    }

    @Substitute
    public int getModifiers() {
        return modifiers;
    }

    @Substitute
    private Object getComponentType() {
        return componentHub;
    }

    @Substitute
    private Object getSuperclass() {
        return superHub;
    }

    @Substitute
    private boolean isInstance(@SuppressWarnings("unused") Object obj) {
        throw VMError.shouldNotReachHere("Substituted in SubstrateGraphBuilderPlugins.");
    }

    @Substitute
    private Object cast(@SuppressWarnings("unused") Object obj) {
        throw VMError.shouldNotReachHere("Substituted in SubstrateGraphBuilderPlugins.");
    }

    @Substitute
    @TargetElement(name = "isAssignableFrom")
    private boolean isAssignableFromClass(Class<?> cls) {
        return isAssignableFromHub(fromClass(cls));
    }

    public boolean isAssignableFromHub(DynamicHub hub) {
        int checkTypeID = hub.getTypeID();
        for (int i = 0; i < assignableFromMatches.length; i += 2) {
            if (UnsignedMath.belowThan(checkTypeID - assignableFromMatches[i], assignableFromMatches[i + 1])) {
                return true;
            }
        }
        return false;
    }

    @Substitute
    private boolean isAnnotation() {
        /*
         * We do not do the check "this.getModifiers() & ANNOTATION) != 0" because we do not have
         * the full modifier bits.
         */
        return isInterface() && getInterfaces().length == 1 && DynamicHub.toClass(getInterfaces()[0]) == Annotation.class;
    }

    @Substitute
    private boolean isEnum() {
        /*
         * We do not do the check "this.getModifiers() & ENUM) != 0" because we do not have the full
         * modifier bits.
         */
        return this.getSuperclass() == java.lang.Enum.class;
    }

    @KeepOriginal
    private native Enum<?>[] getEnumConstants();

    @Substitute
    public Enum<?>[] getEnumConstantsShared() {
        if (enumConstantsReference instanceof LazyFinalReference) {
            return (Enum<?>[]) ((LazyFinalReference<?>) enumConstantsReference).get();
        }
        return (Enum<?>[]) enumConstantsReference;
    }

    @Substitute
    private InputStream getResourceAsStream(String resourceName) {
        final String path = resolveName(getName(), resourceName);
        List<byte[]> arr = Resources.get(path);
        return arr == null ? null : new ByteArrayInputStream(arr.get(0));
    }

    @Substitute
    private URL getResource(String resourceName) {
        final String path = resolveName(getName(), resourceName);
        List<byte[]> arr = Resources.get(path);
        return arr == null ? null : Resources.createURL(path, arr.get(0));
    }

    private String resolveName(String baseName, String resourceName) {
        if (resourceName == null) {
            return resourceName;
        }
        if (resourceName.startsWith("/")) {
            return resourceName.substring(1);
        }
        int index = baseName.lastIndexOf('.');
        if (index != -1) {
            return baseName.substring(0, index).replace('.', '/') + "/" + resourceName;
        } else {
            return resourceName;
        }
    }

    @KeepOriginal
    private native ClassLoader getClassLoader();

    @Substitute
    private ClassLoader getClassLoader0() {
        return SubstrateUtil.cast(classloader, ClassLoader.class);
    }

    @KeepOriginal
    @TargetElement(name = "getSimpleName", onlyWith = JDK8OrEarlier.class)
    private native String getSimpleNameJDK8OrEarlier();

    @Substitute
    @TargetElement(name = "getSimpleName", onlyWith = JDK11OrLater.class)
    private String getSimpleNameJDK11OrLater() {
        return getSimpleName0();
    }

    @KeepOriginal //
    @TargetElement(onlyWith = JDK11OrLater.class)
    private native String getSimpleName0();

    @KeepOriginal
    @TargetElement(name = "getCanonicalName", onlyWith = JDK8OrEarlier.class)
    private native String getCanonicalNameJDK8OrEarlier();

    @Substitute
    @TargetElement(name = "getCanonicalName", onlyWith = JDK11OrLater.class)
    private String getCanonicalNameJDK11OrLater() {
        return getCanonicalName0();
    }

    @KeepOriginal
    @TargetElement(onlyWith = JDK11OrLater.class)
    private native String getCanonicalName0();

    @KeepOriginal
    @Override
    public native String getTypeName();

    @KeepOriginal
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private static native boolean isAsciiDigit(char c);

    @KeepOriginal
    private native String getSimpleBinaryName();

    @KeepOriginal
    private native <U> Class<? extends U> asSubclass(Class<U> clazz);

    @KeepOriginal
    private native boolean isAnonymousClass();

    @Substitute
    private boolean isLocalClass() {
        return isLocalClass;
    }

    @KeepOriginal
    private native boolean isMemberClass();

    @Substitute
    public boolean isLocalOrAnonymousClass() {
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            return isLocalClass() || isAnonymousClass();
        } else {
            return rd.enclosingMethodOrConstructor != null;
        }
    }

    @Substitute
    private Object getEnclosingClass() {
        return enclosingClass;
    }

    @Substitute
    private Object getDeclaringClass() {
        if (isLocalOrAnonymousClass()) {
            return null;
        } else {
            return enclosingClass;
        }
    }

    @Substitute
    public DynamicHub[] getInterfaces() {
        return getInterfaces(this, true);
    }

    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    private DynamicHub[] getInterfaces(boolean cloneArray) {
        return getInterfaces(this, cloneArray);
    }

    private static DynamicHub[] getInterfaces(DynamicHub hub, boolean cloneArray) {
        if (hub.interfacesEncoding == null) {
            return new DynamicHub[0];
        } else if (hub.interfacesEncoding instanceof DynamicHub) {
            return new DynamicHub[]{(DynamicHub) hub.interfacesEncoding};
        } else {
            /* The caller is allowed to modify the array, so we have to make a copy. */
            return cloneArray ? ((DynamicHub[]) hub.interfacesEncoding).clone() : (DynamicHub[]) hub.interfacesEncoding;
        }
    }

    @Substitute
    public Object newInstance() throws Throwable {
        final Constructor<?> nullaryConstructor = rd.nullaryConstructor;
        if (nullaryConstructor == null) {
            if (JavaVersionUtil.JAVA_SPEC <= 8) {
                throw new InstantiationException("Type `" + this.getCanonicalNameJDK8OrEarlier() +
                                "` can not be instantiated reflectively as it does not have a no-parameter constructor or the no-parameter constructor has not been added explicitly to the native image.");
            } else {
                throw new InstantiationException("Type `" + this.getCanonicalNameJDK11OrLater() +
                                "` can not be instantiated reflectively as it does not have a no-parameter constructor or the no-parameter constructor has not been added explicitly to the native image.");
            }
        }
        try {
            return nullaryConstructor.newInstance((Object[]) null);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * Used for reporting errors related to reflective instantiation ({@code Class.newInstance}).
     * This method handles: i) abstract classes, ii) interfaces, and iii) primitive types.
     *
     * @param instance Allocated instance of a type. The instance is used to report errors with a
     *            proper type name.
     * @return Always nothing.
     * @throws InstantiationException always.
     */
    private static Object newInstanceInstantiationError(Object instance) throws InstantiationException {
        if (instance == null) {
            throw VMError.shouldNotReachHere("This case should be handled by the `DynamicNewInstance` lowering.");
        } else {
            throw new InstantiationException("Type `" + instance.getClass().getCanonicalName() + "` can not be instantiated reflectively as it does not have a no-parameter constructor.");
        }

    }

    /**
     * Used for reporting errors related to reflective instantiation ({@code Class.newInstance})
     * when a constructor is removed by reachability analysis.
     *
     * @param instance Allocated instance of a type. The instance is used to report errors with a
     *            proper type name.
     * @return Always nothing.
     */
    private static Object newInstanceReachableError(Object instance) {
        throw new RuntimeException("Constructor of `" + instance.getClass().getCanonicalName() +
                        "` was removed by reachability analysis. Use `Feature.BeforeAnalysisAccess.registerForReflectiveInstantiation` to register the type for reflective instantiation.");
    }

    @Substitute
    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return AnnotationsEncoding.decodeAnnotation(annotationsEncoding, annotationClass);
    }

    @Substitute
    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return getAnnotation(annotationClass) != null;
    }

    @Substitute
    @Override
    public Annotation[] getAnnotations() {
        return AnnotationsEncoding.decodeAnnotations(annotationsEncoding);
    }

    @Substitute
    @Override
    @SuppressWarnings("unchecked")
    public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {

        /*
         * Custom rewrite of GenericDeclaration.super.getAnnotationsByType(annotationClass) to avoid
         * the call to AnnotationType.getInstance(annotationClass).isInherited(). Calling
         * AnnotationType.getInstance(annotationClass) requires registration of the corresponding
         * AnnotationType objects in the image heap during image build time. This is currently done
         * only for repeatable annotations for space economy reasons.
         */
        T[] result = getDeclaredAnnotationsByType(annotationClass);

        if (result.length == 0 && DirectAnnotationAccess.isAnnotationPresent(annotationClass, Inherited.class)) {
            DynamicHub superClass = (DynamicHub) this.getSuperclass();
            if (superClass != null) {
                /* Determine if the annotation is associated with the superclass. */
                result = superClass.getAnnotationsByType(annotationClass);
            }
        }

        return result;
    }

    @Substitute
    @Override
    public Annotation[] getDeclaredAnnotations() {
        Map<Annotation, Void> superAnnotations = new IdentityHashMap<>();
        if (getSuperHub() != null) {
            for (Annotation annotation : getSuperHub().getAnnotations()) {
                superAnnotations.put(annotation, null);
            }
        }

        ArrayList<Annotation> annotations = new ArrayList<>();
        for (Annotation annotation : getAnnotations()) {
            if (!superAnnotations.containsKey(annotation)) {
                annotations.add(annotation);
            }
        }
        return annotations.toArray(new Annotation[annotations.size()]);
    }

    /**
     * In JDK this method uses a lazily computed map of annotations.
     *
     * In SVM we have a pre-initialized array so we use a less efficient implementation from
     * {@link AnnotatedElement} that does the same.
     */
    @Substitute
    @Override
    public <A extends Annotation> A[] getDeclaredAnnotationsByType(Class<A> annotationClass) {
        return GenericDeclaration.super.getDeclaredAnnotationsByType(annotationClass);
    }

    @Substitute
    @Override
    public <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass) {
        Objects.requireNonNull(annotationClass);

        T annotation = AnnotationsEncoding.decodeAnnotation(annotationsEncoding, annotationClass);
        /*
         * superclass has the same annotation instance as the base class => annotation comes from
         * the super class
         */
        if (annotation != null && getSuperHub() != null && getSuperHub().getAnnotation(annotationClass) == annotation) {
            return null;
        }

        return annotation;
    }

    /**
     * This class stores similar information as the non-public class java.lang.Class.ReflectionData.
     */
    public static final class ReflectionData {
        final Field[] declaredFields;
        final Field[] publicFields;
        final Field[] publicUnhiddenFields;
        final Method[] declaredMethods;
        final Method[] publicMethods;
        final Constructor<?>[] declaredConstructors;
        final Constructor<?>[] publicConstructors;
        final Constructor<?> nullaryConstructor;
        final Field[] declaredPublicFields;
        final Method[] declaredPublicMethods;
        final Class<?>[] declaredClasses;
        final Class<?>[] publicClasses;

        /**
         * The result of {@link Class#getEnclosingMethod()} or
         * {@link Class#getEnclosingConstructor()}.
         */
        final Executable enclosingMethodOrConstructor;

        public ReflectionData(Field[] declaredFields, Field[] publicFields, Field[] publicUnhiddenFields, Method[] declaredMethods, Method[] publicMethods, Constructor<?>[] declaredConstructors,
                        Constructor<?>[] publicConstructors, Constructor<?> nullaryConstructor, Field[] declaredPublicFields, Method[] declaredPublicMethods, Class<?>[] declaredClasses,
                        Class<?>[] publicClasses, Executable enclosingMethodOrConstructor) {
            this.declaredFields = declaredFields;
            this.publicFields = publicFields;
            this.publicUnhiddenFields = publicUnhiddenFields;
            this.declaredMethods = declaredMethods;
            this.publicMethods = publicMethods;
            this.declaredConstructors = declaredConstructors;
            this.publicConstructors = publicConstructors;
            this.nullaryConstructor = nullaryConstructor;
            this.declaredPublicFields = declaredPublicFields;
            this.declaredPublicMethods = declaredPublicMethods;
            this.declaredClasses = declaredClasses;
            this.publicClasses = publicClasses;
            this.enclosingMethodOrConstructor = enclosingMethodOrConstructor;
        }
    }

    @TargetClass(value = java.lang.Class.class, innerClass = "MethodArray", onlyWith = JDK8OrEarlier.class)
    static final class Target_java_lang_Class_MethodArray {
    }

    private static final ReflectionData NO_REFLECTION_DATA = new ReflectionData(new Field[0], new Field[0], new Field[0], new Method[0], new Method[0], new Constructor<?>[0], new Constructor<?>[0],
                    null, new Field[0], new Method[0], new Class<?>[0], new Class<?>[0], null);

    private ReflectionData rd = NO_REFLECTION_DATA;

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setReflectionData(ReflectionData rd) {
        this.rd = rd;
    }

    @KeepOriginal
    private native Field[] getFields();

    @KeepOriginal
    private native Method[] getMethods();

    @KeepOriginal
    private native Constructor<?>[] getConstructors();

    @Substitute
    private Field getField(@SuppressWarnings("hiding") String name) throws NoSuchFieldException {
        /*
         * The original code of getField() does a recursive search to avoid creating objects for all
         * public fields. We prepare them during the image build and can just iterate here.
         *
         * Note that we only search those fields which are not hidden by other fields which are
         * possibly not registered for reflective access. For example:
         *
         * class A { public int field; public static int staticField; } // both registered
         *
         * class B extends A { public int field; public static int staticField; } // not registered
         *
         * Here, we do not want B.class.getField("field") to return A.field; same applies to
         * staticField. Note that hidden fields of A are still returned by B.class.getFields().
         */
        for (Field field : rd.publicUnhiddenFields) {
            if (field.getName().equals(name)) {
                return field;
            }
        }
        throw new NoSuchFieldException(name);
    }

    @Substitute
    private Method getMethod(@SuppressWarnings("hiding") String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        /*
         * The original code of getMethods() does a recursive search to avoid creating objects for
         * all public methods. We prepare them during the image build and can just iterate here.
         */
        Method method = searchMethods(rd.publicMethods, name, parameterTypes);
        if (method == null) {
            throw new NoSuchMethodException(describeMethod(getName() + "." + name + "(", parameterTypes, ")"));
        }
        return method;
    }

    @KeepOriginal
    private native Constructor<?> getConstructor(Class<?>... parameterTypes);

    @Substitute
    private Class<?>[] getDeclaredClasses() {
        return rd.declaredClasses;
    }

    @Substitute
    private Class<?>[] getClasses() {
        return rd.publicClasses;
    }

    @KeepOriginal
    private native Field[] getDeclaredFields();

    @KeepOriginal
    private native Method[] getDeclaredMethods();

    @KeepOriginal
    private native Constructor<?>[] getDeclaredConstructors();

    @KeepOriginal
    private native Field getDeclaredField(@SuppressWarnings("hiding") String name);

    @KeepOriginal
    private native Method getDeclaredMethod(@SuppressWarnings("hiding") String name, Class<?>... parameterTypes);

    @KeepOriginal
    private native Constructor<?> getDeclaredConstructor(Class<?>... parameterTypes);

    @Substitute
    private Constructor<?>[] privateGetDeclaredConstructors(boolean publicOnly) {
        return publicOnly ? rd.publicConstructors : rd.declaredConstructors;
    }

    @Substitute
    private Field[] privateGetDeclaredFields(boolean publicOnly) {
        return publicOnly ? rd.declaredPublicFields : rd.declaredFields;
    }

    @Substitute
    private Method[] privateGetDeclaredMethods(boolean publicOnly) {
        return publicOnly ? rd.declaredPublicMethods : rd.declaredMethods;
    }

    @Substitute
    @TargetElement(name = "privateGetPublicFields", onlyWith = JDK8OrEarlier.class)
    private Field[] privateGetPublicFieldsJDK8OrEarlier(@SuppressWarnings("unused") Set<Class<?>> traversedInterfaces) {
        return rd.publicFields;
    }

    @Substitute
    @TargetElement(name = "privateGetPublicFields", onlyWith = JDK11OrLater.class)
    private Field[] privateGetPublicFieldsJDK11OrLater() {
        return rd.publicFields;
    }

    @Substitute
    private Method[] privateGetPublicMethods() {
        return rd.publicMethods;
    }

    @Substitute
    @TargetElement(name = "checkMemberAccess", onlyWith = JDK8OrEarlier.class)
    @SuppressWarnings("unused")
    private void checkMemberAccessJDK8OrEarlier(int which, Class<?> caller, boolean checkProxyInterfaces) {
        /* No runtime access checks. */
    }

    @Substitute
    @TargetElement(name = "checkMemberAccess", onlyWith = JDK11OrLater.class)
    @SuppressWarnings("unused")
    private void checkMemberAccessJDK11OrLater(SecurityManager sm, int which, Class<?> caller, boolean checkProxyInterfaces) {
        /* No runtime access checks. */
    }

    @Substitute
    private static Target_jdk_internal_reflect_ReflectionFactory getReflectionFactory() {
        return Target_jdk_internal_reflect_ReflectionFactory.getReflectionFactory();
    }

    @KeepOriginal
    private static native Field searchFields(Field[] fields, String name);

    @KeepOriginal
    private static native Method searchMethods(Method[] methods, String name, Class<?>[] parameterTypes);

    @KeepOriginal
    private native Constructor<?> getConstructor0(Class<?>[] parameterTypes, int which);

    @KeepOriginal
    private static native boolean arrayContentsEq(Object[] a1, Object[] a2);

    @KeepOriginal
    private static native Field[] copyFields(Field[] arg);

    @KeepOriginal
    private static native Method[] copyMethods(Method[] arg);

    @KeepOriginal
    private static native <U> Constructor<U>[] copyConstructors(Constructor<U>[] arg);

    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private static String argumentTypesToString(Class<?>[] argTypes) {
        return describeMethod("", argTypes, "");
    }

    @Substitute
    @Override
    public TypeVariable<?>[] getTypeParameters() {
        return genericInfo.getTypeParameters();
    }

    @Substitute
    public Type[] getGenericInterfaces() {
        return genericInfo.hasGenericInterfaces() ? genericInfo.getGenericInterfaces() : getInterfaces();
    }

    @Substitute
    public Type getGenericSuperclass() {
        return genericInfo.hasGenericSuperClass() ? genericInfo.getGenericSuperClass() : getSuperHub();
    }

    @Substitute
    public AnnotatedType getAnnotatedSuperclass() {
        return annotatedSuperInfo.getAnnotatedSuperclass();
    }

    @Substitute
    public AnnotatedType[] getAnnotatedInterfaces() {
        return annotatedSuperInfo.getAnnotatedInterfaces();
    }

    @Substitute
    private Method getEnclosingMethod() {
        if (rd.enclosingMethodOrConstructor instanceof Method) {
            return (Method) rd.enclosingMethodOrConstructor;
        }
        return null;
    }

    @Substitute
    private Constructor<?> getEnclosingConstructor() {
        if (rd.enclosingMethodOrConstructor instanceof Constructor) {
            return (Constructor<?>) rd.enclosingMethodOrConstructor;
        }
        return null;
    }

    @Substitute
    private static Class<?> forName(String className) throws ClassNotFoundException {
        return ClassForNameSupport.forName(className, true);
    }

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    @SuppressWarnings({"unused"})
    public static Class<?> forName(Target_java_lang_Module module, String className) {
        /* The module system is not supported for now, therefore the module parameter is ignored. */
        return ClassForNameSupport.forNameOrNull(className, false);
    }

    @Substitute
    private static Class<?> forName(String name, @SuppressWarnings("unused") boolean initialize, @SuppressWarnings("unused") ClassLoader loader) throws ClassNotFoundException {
        return ClassForNameSupport.forName(name, initialize);
    }

    @KeepOriginal
    private native Package getPackage();

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class)
    public String getPackageName() {
        return packageNameReference.get();
    }

    private String computePackageName() {
        String pn = null;
        DynamicHub me = this;
        while (me.isArray()) {
            me = (DynamicHub) me.getComponentType();
        }
        if (me.isPrimitive()) {
            pn = "java.lang";
        } else {
            String cn = me.getName();
            int dot = cn.lastIndexOf('.');
            pn = (dot != -1) ? cn.substring(0, dot).intern() : "";
        }
        return pn;
    }

    @Override
    @Substitute
    public String toString() {
        return (isInterface() ? "interface " : (isPrimitive() ? "" : "class ")) + getName();
    }

    @KeepOriginal
    public native String toGenericString();

    @KeepOriginal
    public native boolean isSynthetic();

    @Substitute
    public Object[] getSigners() {
        return null;
    }

    @Substitute
    public ProtectionDomain getProtectionDomain() {
        return allPermDomainReference.get();
    }

    @Substitute
    public boolean desiredAssertionStatus() {
        return SubstrateOptions.getRuntimeAssertionsForClass(getName());
    }

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class)
    public Target_java_lang_Module getModule() {
        return singleModuleReference.get();
    }

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class)
    private String methodToString(String nameArg, Class<?>[] argTypes) {
        return describeMethod(name + "." + nameArg + "(", argTypes, ")");
    }

    private static String describeMethod(String prefix, Class<?>[] argTypes, String suffix) {
        StringJoiner sj = new StringJoiner(", ", prefix, suffix);
        if (argTypes != null) {
            for (Class<?> c : argTypes) {
                sj.add((c == null) ? "null" : c.getName());
            }
        }
        return sj.toString();
    }

    @Substitute //
    private <T> Target_java_lang_Class_ReflectionData<T> reflectionData() {
        throw VMError.unsupportedFeature("JDK11OrLater: DynamicHub.reflectionData()");
    }

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class)
    private boolean isTopLevelClass() {
        return !isLocalOrAnonymousClass() && getDeclaringClass() == null;
    }

    @KeepOriginal
    @TargetElement(onlyWith = JDK11OrLater.class)
    private native Object[] getEnclosingMethod0();

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class)
    private String getSimpleBinaryName0() {
        if (enclosingClass == null) {
            return null;
        }
        try {
            return getName().substring(enclosingClass.getName().length() + 1);
        } catch (IndexOutOfBoundsException ex) {
            throw new InternalError("Malformed class name", ex);
        }
        /* See open/src/hotspot/share/prims/jvm.cpp#1522. */
    }

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    @SuppressWarnings({"unused"})
    List<Method> getDeclaredPublicMethods(String nameArg, Class<?>... parameterTypes) {
        throw VMError.unsupportedFeature("JDK11OrLater: DynamicHub.getDeclaredPublicMethods(String nameArg, Class<?>... parameterTypes)");
    }

    @Substitute //
    private /* native */ Class<?> getDeclaringClass0() {
        /* See open/src/hotspot/share/prims/jvm.cpp#1504. */
        throw VMError.unsupportedFeature("DynamicHub.getDeclaringClass0()");
    }
}

/** FIXME: How to handle java.lang.Class.ReflectionData? */
@TargetClass(className = "java.lang.Class", innerClass = "ReflectionData")
final class Target_java_lang_Class_ReflectionData<T> {
}

@TargetClass(classNameProvider = Package_jdk_internal_reflect.class, className = "ReflectionFactory")
final class Target_jdk_internal_reflect_ReflectionFactory {

    @Alias //
    private static Target_jdk_internal_reflect_ReflectionFactory soleInstance;

    @Substitute
    public static Target_jdk_internal_reflect_ReflectionFactory getReflectionFactory() {
        return soleInstance;
    }
}
