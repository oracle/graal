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
import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.core.common.calc.UnsignedMath;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.core.HostedIdentityHashCodeProvider;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Hybrid;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.jdk.JDK9OrLater;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.jdk.Target_java_lang_ClassLoader;
import com.oracle.svm.core.jdk.Target_java_lang_Module;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaKind;
import sun.reflect.ReflectionFactory;
import sun.security.util.SecurityConstants;

@Hybrid
@Substitute
@TargetClass(java.lang.Class.class)
@SuppressWarnings({"static-method", "serial"})
@SuppressFBWarnings(value = "Se", justification = "DynamicHub must implement Serializable for compatibility with java.lang.Class, not because of actual serialization")
public final class DynamicHub implements JavaKind.FormatWithToString, AnnotatedElement, HostedIdentityHashCodeProvider, java.lang.reflect.Type, GenericDeclaration, Serializable {

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
     * Does this represent a static class?
     */
    private final boolean isStatic;

    /**
     * Does this represent a synthetic class?
     */
    private final boolean isSynthetic;

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
     * List of enum values for subclasses of {@link Enum}; null otherwise.
     */
    private Enum<?>[] enumConstants;

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

    @Platforms(Platform.HOSTED_ONLY.class) private int hostedIdentityHashCode;

    private GenericInfo genericInfo;
    private AnnotatedSuperInfo annotatedSuperInfo;

    @Alias private static java.security.ProtectionDomain allPermDomain;

    @Platforms(Platform.HOSTED_ONLY.class)
    public DynamicHub(String name, boolean isLocalClass, DynamicHub superType, DynamicHub componentHub, String sourceFileName, boolean isStatic, boolean isSynthetic,
                    Target_java_lang_ClassLoader classLoader) {
        /* Class names must be interned strings according to the Java spec. */
        this.name = name.intern();
        this.isLocalClass = isLocalClass;
        this.superHub = superType;
        this.componentHub = componentHub;
        this.sourceFileName = sourceFileName;
        this.genericInfo = GenericInfo.forEmpty();
        this.annotatedSuperInfo = AnnotatedSuperInfo.forEmpty();
        this.isStatic = isStatic;
        this.isSynthetic = isSynthetic;
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
    public void setAnnotatedSuperInfo(AnnotatedSuperInfo annotatedSuperInfo) {
        this.annotatedSuperInfo = annotatedSuperInfo;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setInterfacesEncoding(Object interfacesEncoding) {
        this.interfacesEncoding = interfacesEncoding;
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
    public void setEnumConstants(Enum<?>[] enumConstants) {
        this.enumConstants = enumConstants;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setMetaType(SharedType metaType) {
        this.metaType = metaType;
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public int hostedIdentityHashCode() {
        return hostedIdentityHashCode;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setHostedIdentityHashCode(int newHostedIdentityHashCode) {
        assert this.hostedIdentityHashCode == 0 || this.hostedIdentityHashCode == newHostedIdentityHashCode;
        this.hostedIdentityHashCode = newHostedIdentityHashCode;
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
        return KnownIntrinsics.unsafeCast(clazz, DynamicHub.class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Class<?> asClass() {
        return KnownIntrinsics.unsafeCast(this, Class.class);
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
        /* We do not have detailed access level information, so we make every class public. */
        return Modifier.PUBLIC |
                        (LayoutEncoding.isAbstract(getLayoutEncoding()) ? Modifier.ABSTRACT : 0) |
                        (isStatic ? Modifier.STATIC : 0) |
                        (isSynthetic ? SYNTHETIC : 0) |
                        (isInterface() ? Modifier.INTERFACE : 0);
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
        return isAssignableFromHub(KnownIntrinsics.unsafeCast(cls, DynamicHub.class));
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
        return isInterface() && getInterfaces().length == 1 && getInterfaces()[0].asClass() == Annotation.class;
    }

    @Substitute
    private boolean isEnum() {
        /*
         * We do not do the check "this.getModifiers() & ENUM) != 0" because we do not have the full
         * modifier bits.
         */
        return this.getSuperclass() == java.lang.Enum.class;
    }

    @Substitute
    private Enum<?>[] getEnumConstants() {
        return enumConstants != null ? enumConstants.clone() : null;
    }

    @Substitute
    public Enum<?>[] getEnumConstantsShared() {
        return enumConstants;
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
        return KnownIntrinsics.unsafeCast(classloader, ClassLoader.class);
    }

    @KeepOriginal
    private native String getSimpleName();

    @Substitute //
    @TargetElement(onlyWith = JDK9OrLater.class)
    private String getSimpleName0() {
        throw VMError.unsupportedFeature("JDK9OrLater: DynamicHub.getSimpleName0()");
    }

    @KeepOriginal
    private native String getCanonicalName();

    @Substitute
    @TargetElement(onlyWith = JDK9OrLater.class)
    private String getCanonicalName0() {
        throw VMError.unsupportedFeature("JDK9OrLater: DynamicHub.getCanonicalName0()");
    }

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
    private boolean isLocalOrAnonymousClass() {
        return isLocalClass() || isAnonymousClass();
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
        if (interfacesEncoding == null) {
            return new DynamicHub[0];
        } else if (interfacesEncoding instanceof DynamicHub) {
            return new DynamicHub[]{(DynamicHub) interfacesEncoding};
        } else {
            /* The caller is allowed to modify the array, so we have to make a copy. */
            return ((DynamicHub[]) interfacesEncoding).clone();
        }
    }

    @Substitute
    public Object newInstance() throws Throwable {
        final Constructor<?> nullaryConstructor = rd.nullaryConstructor;
        if (nullaryConstructor == null) {
            throw new InstantiationException("Type `" + this.getCanonicalName() +
                            "` can not be instantiated reflectively as it does not have a no-parameter constructor or the no-parameter constructor has not been added explicitly to the native image.");
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
        return AnnotationsEncoding.getAnnotation(annotationsEncoding, annotationClass);
    }

    @Substitute
    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return getAnnotation(annotationClass) != null;
    }

    @Substitute
    @Override
    public Annotation[] getAnnotations() {
        return AnnotationsEncoding.getAnnotations(annotationsEncoding);
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

        T annotation = AnnotationsEncoding.getAnnotation(annotationsEncoding, annotationClass);
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
        final Method[] declaredMethods;
        final Method[] publicMethods;
        final Constructor<?>[] declaredConstructors;
        final Constructor<?>[] publicConstructors;
        final Constructor<?> nullaryConstructor;
        final Field[] declaredPublicFields;
        final Method[] declaredPublicMethods;

        /**
         * The result of {@link Class#getEnclosingMethod()} or
         * {@link Class#getEnclosingConstructor()}.
         */
        final Executable enclosingMethodOrConstructor;

        public ReflectionData(Field[] declaredFields, Field[] publicFields, Method[] declaredMethods, Method[] publicMethods, Constructor<?>[] declaredConstructors,
                        Constructor<?>[] publicConstructors, Constructor<?> nullaryConstructor, Field[] declaredPublicFields, Method[] declaredPublicMethods, Executable enclosingMethodOrConstructor) {
            this.declaredFields = declaredFields;
            this.publicFields = publicFields;
            this.declaredMethods = declaredMethods;
            this.publicMethods = publicMethods;
            this.declaredConstructors = declaredConstructors;
            this.publicConstructors = publicConstructors;
            this.nullaryConstructor = nullaryConstructor;
            this.declaredPublicFields = declaredPublicFields;
            this.declaredPublicMethods = declaredPublicMethods;
            this.enclosingMethodOrConstructor = enclosingMethodOrConstructor;
        }
    }

    @TargetClass(value = java.lang.Class.class, innerClass = "MethodArray", onlyWith = JDK8OrEarlier.class)
    static final class Target_java_lang_Class_MethodArray {
    }

    private static final ReflectionData NO_REFLECTION_DATA = new ReflectionData(new Field[0], new Field[0], new Method[0], new Method[0], new Constructor<?>[0], new Constructor<?>[0], null,
                    new Field[0], new Method[0], null);

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

    @KeepOriginal
    private native Field getField(@SuppressWarnings("hiding") String name);

    @KeepOriginal
    private native Method getMethod(@SuppressWarnings("hiding") String name, Class<?>... parameterTypes);

    @KeepOriginal
    private native Constructor<?> getConstructor(Class<?>... parameterTypes);

    @KeepOriginal
    private native Class<?>[] getDeclaredClasses();

    @KeepOriginal
    public native Class<?>[] getClasses();

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
    @TargetElement(name = "privateGetPublicFields", onlyWith = JDK9OrLater.class)
    private Field[] privateGetPublicFieldsJDK9Orlater() {
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
    @TargetElement(name = "checkMemberAccess", onlyWith = JDK9OrLater.class)
    @SuppressWarnings("unused")
    private void checkMemberAccessJDK9OrLater(SecurityManager sm, int which, Class<?> caller, boolean checkProxyInterfaces) {
        /* No runtime access checks. */
    }

    @Substitute
    private static ReflectionFactory getReflectionFactory() {
        return reflectionFactory;
    }

    private static final ReflectionFactory reflectionFactory = ReflectionFactory.getReflectionFactory();

    @KeepOriginal
    private static native Field searchFields(Field[] fields, String name);

    @KeepOriginal
    private native Field getField0(@SuppressWarnings("hiding") String name);

    @KeepOriginal
    private static native Method searchMethods(Method[] methods, String name, Class<?>[] parameterTypes);

    @KeepOriginal
    @TargetElement(name = "getMethod0", onlyWith = JDK8OrEarlier.class)
    private native Method getMethod0JDK8OrEarlier(@SuppressWarnings("hiding") String name, Class<?>[] parameterTypes, boolean includeStaticMethods);

    @KeepOriginal
    @TargetElement(name = "getMethod0", onlyWith = JDK9OrLater.class)
    private native Method getMethod0JDK9OrLater(@SuppressWarnings("hiding") String name, Class<?>[] parameterTypes);

    @KeepOriginal
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private native Method privateGetMethodRecursive(@SuppressWarnings("hiding") String name, Class<?>[] parameterTypes, boolean includeStaticMethods,
                    Target_java_lang_Class_MethodArray allInterfaceCandidates);

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

    @KeepOriginal
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private static native String argumentTypesToString(Class<?>[] argTypes);

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
        return ClassForNameSupport.forName(className);
    }

    @Substitute //
    @TargetElement(onlyWith = JDK9OrLater.class) //
    @SuppressWarnings({"unused"})
    public static Class<?> forName(Target_java_lang_Module module, String name) {
        throw VMError.unsupportedFeature("JDK9OrLater: DynamicHub.forName(Target_java_lang_Module module, String name)");
    }

    @Substitute
    private static Class<?> forName(String name, @SuppressWarnings("unused") boolean initialize, @SuppressWarnings("unused") ClassLoader loader) throws ClassNotFoundException {
        return ClassForNameSupport.forName(name);
    }

    @KeepOriginal
    @TargetElement(name = "getPackage", onlyWith = JDK8OrEarlier.class)
    public native Package getPackageJDK8OrEarlier();

    @Substitute
    @TargetElement(name = "getPackage", onlyWith = JDK9OrLater.class)
    public Package getPackageJDK9OrLater() {
        throw VMError.unsupportedFeature("JDK9OrLater: DynamicHub.getPackage()");
    }

    @Substitute //
    @TargetElement(onlyWith = JDK9OrLater.class)
    public String getPackageName() {
        throw VMError.unsupportedFeature("JDK9OrLater: DynamicHub.getPackageName()");
    }

    @Override
    @Substitute
    public String toString() {
        return (isInterface() ? "interface " : (isPrimitive() ? "" : "class ")) + getName();
    }

    @KeepOriginal
    public native String toGenericString();

    @Substitute
    public boolean isSynthetic() {
        return isSynthetic;
    }

    @Substitute
    public Object[] getSigners() {
        return null;
    }

    @Substitute
    public Object getProtectionDomain() {
        if (allPermDomain == null) {
            java.security.Permissions perms = new java.security.Permissions();
            perms.add(SecurityConstants.ALL_PERMISSION);
            allPermDomain = new java.security.ProtectionDomain(null, perms);
        }
        return allPermDomain;
    }

    @Substitute
    public boolean desiredAssertionStatus() {
        return SubstrateOptions.RuntimeAssertions.getValue() && SubstrateOptions.getRuntimeAssertionsFilter().test(getName());
    }

    @Substitute //
    @TargetElement(onlyWith = JDK9OrLater.class)
    public Target_java_lang_Module getModule() {
        throw VMError.unsupportedFeature("JDK9OrLater: DynamicHub.getModule()");
    }

    @Substitute //
    @TargetElement(onlyWith = JDK9OrLater.class)
    @SuppressWarnings({"unused"})
    private String methodToString(String nameArg, Class<?>[] argTypes) {
        throw VMError.unsupportedFeature("JDK9OrLater: DynamicHub.methodToString(String nameArg, Class<?>[] argTypes)");
    }

    @Substitute //
    private <T> Target_java_lang_Class_ReflectionData<T> reflectionData() {
        throw VMError.unsupportedFeature("JDK9OrLater: DynamicHub.reflectionData()");
    }

    @Substitute //
    @TargetElement(onlyWith = JDK9OrLater.class)
    private boolean isTopLevelClass() {
        throw VMError.unsupportedFeature("JDK9OrLater: DynamicHub.isTopLevelClass()");
    }

    @Substitute //
    @TargetElement(onlyWith = JDK9OrLater.class)
    private /* native */ String getSimpleBinaryName0() {
        /* See open/src/hotspot/share/prims/jvm.cpp#1522. */
        throw VMError.unsupportedFeature("JDK9OrLater: DynamicHub.getSimpleBinaryName0()");
    }

    @Substitute //
    @TargetElement(onlyWith = JDK9OrLater.class) //
    @SuppressWarnings({"unused"})
    List<Method> getDeclaredPublicMethods(String nameArg, Class<?>... parameterTypes) {
        throw VMError.unsupportedFeature("JDK9OrLater: DynamicHub.getDeclaredPublicMethods(String nameArg, Class<?>... parameterTypes)");
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
