/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.ref.Reference;
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
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.util.DirectAnnotationAccess;

import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Hybrid;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.jdk.JDK17OrLater;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.LazyFinalReference;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;

import jdk.internal.reflect.ConstantPool;
import jdk.internal.reflect.Reflection;
import jdk.vm.ci.meta.JavaKind;

@Hybrid(canHybridFieldsBeDuplicated = false)
@Substitute
@TargetClass(java.lang.Class.class)
@SuppressWarnings({"static-method", "serial"})
@SuppressFBWarnings(value = "Se", justification = "DynamicHub must implement Serializable for compatibility with java.lang.Class, not because of actual serialization")
public final class DynamicHub implements JavaKind.FormatWithToString, AnnotatedElement, java.lang.reflect.Type, GenericDeclaration, Serializable,
                Target_java_lang_invoke_TypeDescriptor_OfField, Target_java_lang_constant_Constable {

    @Substitute //
    private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Class<?> hostedJavaClass;

    /**
     * The name of the class this hub is representing, as defined in {@link Class#getName()}.
     */
    private String name;

    /**
     * Used to quickly determine in which category a certain hub falls (e.g., instance or array).
     */
    private final int hubType;

    /**
     * Used to quickly determine if this class is a subclass of {@link Reference}.
     */
    private final byte referenceType;

    /**
     * Encoding of the object or array size. Decode using {@link LayoutEncoding}.
     */
    private int layoutEncoding;

    /**
     * Unique id number for this type, used for fast type checks and type casts.
     */
    private int typeID;

    /**
     * In our current version, type checks are accomplished by performing a range check on a value
     * from an array. The slot to read from the checked type is determined by
     * {@link #getTypeCheckSlot()} and the check passes if {@link #getTypeCheckStart()} <= value <
     * ({@link #getTypeCheckStart()} + {@link #getTypeCheckRange()}).
     */
    private short typeCheckStart;

    /**
     * The number of ids which are in valid range for a type check.
     */
    private short typeCheckRange;

    /**
     *
     * The value slot within the type id slot array to read when comparing against this type.
     */
    private short typeCheckSlot;

    /**
     * The offset of the synthetic field which stores whatever is used for monitorEnter/monitorExit
     * by an instance of this class. If 0, then instances of this class are locked using a side
     * table.
     */
    private short monitorOffset;

    /**
     * Bit-set for various boolean flags, to reduce size of instances. It is important that this
     * field is final, so that various methods are constant folded for constant classes already
     * before the static analysis.
     */
    private final byte flags;

    /** Is this a primitive type. */
    private static final int IS_PRIMITIVE_FLAG_BIT = 0;
    /** Is this an interface. */
    private static final int IS_INTERFACE_FLAG_BIT = 1;
    /** Is this a Hidden Class. */
    private static final int IS_HIDDEN_FLAG_BIT = 2;
    /** Is this a Record Class. */
    private static final int IS_RECORD_FLAG_BIT = 3;
    /** Holds assertionStatus determined by {@link RuntimeAssertionsSupport}. */
    private static final int ASSERTION_STATUS_FLAG_BIT = 4;
    /**
     * Class/superclass/implemented interfaces has default methods. Necessary metadata for class
     * initialization, but even for classes/interfaces that are already initialized during image
     * generation, so it cannot be a field in {@link ClassInitializationInfo}.
     */
    private static final int HAS_DEFAULT_METHODS_FLAG_BIT = 5;
    /**
     * Directly declares default methods. Necessary metadata for class initialization, but even for
     * interfaces that are already initialized during image generation, so it cannot be a field in
     * {@link ClassInitializationInfo}.
     */
    private static final int DECLARES_DEFAULT_METHODS_FLAG_BIT = 6;
    /** Is this a Sealed Class. */
    private static final int IS_SEALED_FLAG_BIT = 7;
    /**
     * Has the type been discovered as instantiated by the static analysis?
     */
    private boolean isInstantiated;

    /**
     * Boolean value or exception that happened at image-build time.
     */
    private final Object isLocalClass;

    /**
     * Boolean value or exception that happened at image-build time.
     */
    private final Object isAnonymousClass;

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
    @Substitute //
    private final DynamicHub componentType;

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
    private final String sourceFileName;

    /**
     * The annotations of this class. This field holds either null (no annotations), an Annotation
     * (one annotation), or an Annotation[] array (more than one annotation). This eliminates the
     * need for an array for the case that there are less than two annotations.
     */
    private Object annotationsEncoding;

    /**
     * Metadata for running class initializers at run time. Refers to a singleton marker object for
     * classes/interfaces already initialized during image generation, i.e., this field is never
     * null at run time.
     */
    private ClassInitializationInfo classInitializationInfo;

    /**
     * Array containing this type's type check id information. During a type check, a requested
     * column of this array is read to determine if this value fits within the range of ids which
     * match the assignee's type.
     */
    @Hybrid.TypeIDSlots private short[] typeCheckSlots;

    @Hybrid.Array private CFunctionPointer[] vtable;

    private GenericInfo genericInfo;
    private AnnotatedSuperInfo annotatedSuperInfo;

    /**
     * Field used for module information access at run-time.
     */
    private Module module;

    /**
     * JDK 11 and later: the class that serves as the host for the nest. All nestmates have the same
     * host.
     */
    private final Class<?> nestHost;

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setModule(Module module) {
        this.module = module;
    }

    private final DynamicHubCompanion companion;

    @Platforms(Platform.HOSTED_ONLY.class)
    public DynamicHub(Class<?> hostedJavaClass, String name, HubType hubType, ReferenceType referenceType, Object isLocalClass, Object isAnonymousClass, DynamicHub superType, DynamicHub componentHub,
                    String sourceFileName, int modifiers, ClassLoader classLoader, boolean isHidden, boolean isRecord, Class<?> nestHost, boolean assertionStatus,
                    boolean hasDefaultMethods, boolean declaresDefaultMethods, boolean isSealed) {
        this.hostedJavaClass = hostedJavaClass;
        this.name = name;
        this.hubType = hubType.getValue();
        this.referenceType = referenceType.getValue();
        this.isLocalClass = isLocalClass;
        this.isAnonymousClass = isAnonymousClass;
        this.superHub = superType;
        this.componentType = componentHub;
        this.sourceFileName = sourceFileName;
        this.modifiers = modifiers;
        this.nestHost = nestHost;

        this.flags = NumUtil.safeToUByte(makeFlag(IS_PRIMITIVE_FLAG_BIT, hostedJavaClass.isPrimitive()) |
                        makeFlag(IS_INTERFACE_FLAG_BIT, hostedJavaClass.isInterface()) |
                        makeFlag(IS_HIDDEN_FLAG_BIT, isHidden) |
                        makeFlag(IS_RECORD_FLAG_BIT, isRecord) |
                        makeFlag(ASSERTION_STATUS_FLAG_BIT, assertionStatus) |
                        makeFlag(HAS_DEFAULT_METHODS_FLAG_BIT, hasDefaultMethods) |
                        makeFlag(DECLARES_DEFAULT_METHODS_FLAG_BIT, declaresDefaultMethods) |
                        makeFlag(IS_SEALED_FLAG_BIT, isSealed));

        this.companion = new DynamicHubCompanion(hostedJavaClass, classLoader);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private int makeFlag(int flagBit, boolean value) {
        int flagMask = 1 << flagBit;
        return value ? flagMask : 0;
    }

    private boolean isFlagSet(int flagBit) {
        int flagMask = 1 << flagBit;
        return (flags & flagMask) != 0;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setClassInitializationInfo(ClassInitializationInfo classInitializationInfo) {
        this.classInitializationInfo = classInitializationInfo;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setData(int layoutEncoding, int typeID, int monitorOffset,
                    short typeCheckStart, short typeCheckRange, short typeCheckSlot, short[] typeCheckSlots,
                    CFunctionPointer[] vtable, long referenceMapIndex, boolean isInstantiated) {
        assert this.vtable == null : "Initialization must be called only once";

        this.layoutEncoding = layoutEncoding;
        this.typeID = typeID;
        this.monitorOffset = NumUtil.safeToShort(monitorOffset);
        this.typeCheckStart = typeCheckStart;
        this.typeCheckRange = typeCheckRange;
        this.typeCheckSlot = typeCheckSlot;
        this.typeCheckSlots = typeCheckSlots;
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
        return isFlagSet(HAS_DEFAULT_METHODS_FLAG_BIT);
    }

    public boolean declaresDefaultMethods() {
        return isFlagSet(DECLARES_DEFAULT_METHODS_FLAG_BIT);
    }

    public ClassInitializationInfo getClassInitializationInfo() {
        return classInitializationInfo;
    }

    public boolean isInitialized() {
        return classInitializationInfo.isInitialized();
    }

    public void ensureInitialized() {
        EnsureClassInitializedNode.ensureClassInitialized(toClass(this));
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getTypeID() {
        return typeID;
    }

    public short getTypeCheckSlot() {
        return typeCheckSlot;
    }

    public short getTypeCheckStart() {
        return typeCheckStart;
    }

    public short getTypeCheckRange() {
        return typeCheckRange;
    }

    public int getMonitorOffset() {
        return monitorOffset;
    }

    public DynamicHub getSuperHub() {
        return superHub;
    }

    public DynamicHub getComponentHub() {
        return componentType;
    }

    public DynamicHub getArrayHub() {
        return arrayHub;
    }

    public int getReferenceMapIndex() {
        return referenceMapIndex;
    }

    public boolean isInstantiated() {
        return isInstantiated;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static DynamicHub fromClass(Class<?> clazz) {
        return SubstrateUtil.cast(clazz, DynamicHub.class);
    }

    public DynamicHubCompanion getCompanion() {
        return companion;
    }

    /*
     * Note that this method must be a static method and not an instance method, otherwise null
     * values cannot be converted.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Class<?> toClass(DynamicHub hub) {
        return SubstrateUtil.cast(hub, Class.class);
    }

    /**
     * Returns the {@link Class} object that represents the type during image generation.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public Class<?> getHostedJavaClass() {
        return hostedJavaClass;
    }

    @Substitute
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getName() {
        return name;
    }

    public boolean isInstanceClass() {
        return HubType.isInstance(hubType);
    }

    public boolean isStoredContinuationClass() {
        return HubType.isStoredContinuation(hubType);
    }

    public boolean isReferenceInstanceClass() {
        return HubType.isReferenceInstance(hubType);
    }

    @Substitute
    @Override
    public boolean isArray() {
        throw VMError.shouldNotReachHere("Intrinsified in StandardGraphBuilderPlugins.");
    }

    public boolean hubIsArray() {
        return HubType.isArray(hubType);
    }

    @Substitute
    public boolean isInterface() {
        return isFlagSet(IS_INTERFACE_FLAG_BIT);
    }

    @Substitute
    @Override
    public boolean isPrimitive() {
        return isFlagSet(IS_PRIMITIVE_FLAG_BIT);
    }

    @Substitute
    public int getModifiers() {
        return modifiers;
    }

    @Substitute
    private Object getComponentType() {
        return componentType;
    }

    @Substitute
    private Object getSuperclass() {
        return superHub;
    }

    @Substitute
    private boolean isInstance(@SuppressWarnings("unused") Object obj) {
        throw VMError.shouldNotReachHere("Intrinsified in StandardGraphBuilderPlugins.");
    }

    @Substitute
    private Object cast(@SuppressWarnings("unused") Object obj) {
        throw VMError.shouldNotReachHere("Intrinsified in StandardGraphBuilderPlugins.");
    }

    @Substitute
    private boolean isAssignableFrom(@SuppressWarnings("unused") Class<?> cls) {
        throw VMError.shouldNotReachHere("Intrinsified in StandardGraphBuilderPlugins.");
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

    @KeepOriginal
    public native URL getResource(String resourceName);

    @Substitute
    public InputStream getResourceAsStream(String resourceName) {
        String moduleName = module == null ? null : module.getName();
        String resolvedName = resolveName(resourceName);
        return Resources.createInputStream(moduleName, resolvedName);
    }

    @KeepOriginal
    private native String resolveName(String resourceName);

    @KeepOriginal
    private native boolean isOpenToCaller(String resourceName, Class<?> caller);

    @KeepOriginal
    private native ClassLoader getClassLoader();

    @Substitute
    private ClassLoader getClassLoader0() {
        return companion.getClassLoader();
    }

    public boolean isLoaded() {
        return companion.hasClassLoader();
    }

    void setClassLoaderAtRuntime(ClassLoader loader) {
        companion.setClassLoader(loader);
    }

    @Substitute
    private String getSimpleName() {
        return getSimpleName0();
    }

    @KeepOriginal //
    private native String getSimpleName0();

    @SuppressFBWarnings(value = "ES_COMPARING_STRINGS_WITH_EQ", justification = "sentinel string comparison")
    @Substitute
    private String getCanonicalName() {
        String canonicalName = getCanonicalName0();
        return canonicalName == Target_java_lang_Class_ReflectionData.NULL_SENTINEL ? null : canonicalName;
    }

    @KeepOriginal
    private native String getCanonicalName0();

    @KeepOriginal
    @Override
    public native String getTypeName();

    @KeepOriginal
    private native String getSimpleBinaryName();

    @KeepOriginal
    private native <U> Class<? extends U> asSubclass(Class<U> clazz);

    @Substitute
    private boolean isAnonymousClass() {
        return booleanOrError(isAnonymousClass);
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    public boolean isHidden() {
        return isFlagSet(IS_HIDDEN_FLAG_BIT);
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    public boolean isRecord() {
        return isFlagSet(IS_RECORD_FLAG_BIT);
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    public boolean isSealed() {
        return isFlagSet(IS_SEALED_FLAG_BIT);
    }

    @Substitute
    private boolean isLocalClass() {
        return booleanOrError(isLocalClass);
    }

    private static boolean booleanOrError(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof LinkageError) {
            throw (LinkageError) value;
        } else if (value instanceof InternalError) {
            throw (InternalError) value;
        } else {
            throw VMError.shouldNotReachHere();
        }
    }

    @KeepOriginal
    private native boolean isMemberClass();

    @Substitute
    public boolean isLocalOrAnonymousClass() {
        return isLocalClass() || isAnonymousClass();
    }

    @Substitute
    private Object getEnclosingClass() {
        PredefinedClassesSupport.throwIfUnresolvable(toClass(enclosingClass), getClassLoader0());
        return enclosingClass;
    }

    @KeepOriginal
    private native Object getDeclaringClass();

    @Substitute
    private Object getDeclaringClass0() {
        return getDeclaringClassInternal();
    }

    private Object getDeclaringClassInternal() {
        if (isLocalOrAnonymousClass()) {
            return null;
        } else {
            return getEnclosingClass();
        }
    }

    @Substitute
    public DynamicHub[] getInterfaces() {
        return getInterfaces(this, true);
    }

    @Substitute
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
        /*
         * The JavaDoc for the original method states that "The class is initialized if it has not
         * already been initialized". However, it doesn't specify if the absence of a nullary
         * constructor will result in an InstantiationException before the class is initialized. We
         * eagerly initialize it to conform with JCK tests.
         */
        ensureInitialized();
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

// Checkstyle: allow direct annotation access (false positives)

    @Substitute
    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return AnnotationsEncoding.decodeAnnotations(annotationsEncoding).getAnnotation(annotationClass);
    }

    @Substitute
    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return getAnnotation(annotationClass) != null;
    }

    @Substitute
    @Override
    public Annotation[] getAnnotations() {
        return AnnotationsEncoding.decodeAnnotations(annotationsEncoding).getAnnotations();
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
        return AnnotationsEncoding.decodeAnnotations(annotationsEncoding).getDeclaredAnnotations();
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
        return AnnotationsEncoding.decodeAnnotations(annotationsEncoding).getDeclaredAnnotation(annotationClass);
    }

// Checkstyle: disallow direct annotation access

    /**
     * This class stores similar information as the non-public class java.lang.Class.ReflectionData.
     */
    public static final class ReflectionData {
        static final ReflectionData EMPTY = new ReflectionData(new Field[0], new Field[0], new Field[0], new Method[0], new Method[0], new Constructor<?>[0], new Constructor<?>[0], null, new Field[0],
                        new Method[0], new Class<?>[0], null, new Class<?>[0], null, null);

        public static ReflectionData get(Field[] declaredFields, Field[] publicFields, Field[] publicUnhiddenFields, Method[] declaredMethods, Method[] publicMethods,
                        Constructor<?>[] declaredConstructors, Constructor<?>[] publicConstructors, Constructor<?> nullaryConstructor, Field[] declaredPublicFields,
                        Method[] declaredPublicMethods, Class<?>[] declaredClasses, Class<?>[] permittedSubclasses, Class<?>[] publicClasses, Executable enclosingMethodOrConstructor,
                        Object[] recordComponents) {

            if (z(declaredFields) && z(publicFields) && z(publicUnhiddenFields) && z(declaredMethods) && z(publicMethods) && z(declaredConstructors) &&
                            z(publicConstructors) && nullaryConstructor == null && z(declaredPublicFields) && z(declaredPublicMethods) && z(declaredClasses) &&
                            permittedSubclasses == null && z(publicClasses) && enclosingMethodOrConstructor == null && (recordComponents == null || z(recordComponents))) {
                return EMPTY; // avoid redundant objects in image heap
            }
            return new ReflectionData(declaredFields, publicFields, publicUnhiddenFields, declaredMethods, publicMethods, declaredConstructors, publicConstructors, nullaryConstructor,
                            declaredPublicFields, declaredPublicMethods, declaredClasses, permittedSubclasses, publicClasses, enclosingMethodOrConstructor, recordComponents);
        }

        private static boolean z(Object[] array) { // for better readability above
            return array.length == 0;
        }

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
        final Class<?>[] permittedSubclasses;
        final Class<?>[] publicClasses;
        final Object[] recordComponents;

        /**
         * The result of {@link Class#getEnclosingMethod()} or
         * {@link Class#getEnclosingConstructor()}.
         */
        final Executable enclosingMethodOrConstructor;

        ReflectionData(Field[] declaredFields, Field[] publicFields, Field[] publicUnhiddenFields, Method[] declaredMethods, Method[] publicMethods, Constructor<?>[] declaredConstructors,
                        Constructor<?>[] publicConstructors, Constructor<?> nullaryConstructor, Field[] declaredPublicFields, Method[] declaredPublicMethods, Class<?>[] declaredClasses,
                        Class<?>[] permittedSubclasses, Class<?>[] publicClasses, Executable enclosingMethodOrConstructor,
                        Object[] recordComponents) {
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
            this.permittedSubclasses = permittedSubclasses;
            this.publicClasses = publicClasses;
            this.enclosingMethodOrConstructor = enclosingMethodOrConstructor;
            this.recordComponents = recordComponents;
        }
    }

    ReflectionData rd = ReflectionData.EMPTY;

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
        Method method = searchMethods(companion.getCompleteReflectionData(this).publicMethods, name, parameterTypes);
        if (method == null) {
            throw new NoSuchMethodException(describeMethod(getName() + "." + name + "(", parameterTypes, ")"));
        }
        return method;
    }

    @KeepOriginal
    private native Constructor<?> getConstructor(Class<?>... parameterTypes);

    @Substitute
    private Class<?>[] getDeclaredClasses() {
        for (Class<?> clazz : rd.declaredClasses) {
            PredefinedClassesSupport.throwIfUnresolvable(clazz, getClassLoader0());
        }
        return rd.declaredClasses;
    }

    @Substitute
    private Class<?>[] getClasses() {
        for (Class<?> clazz : rd.publicClasses) {
            PredefinedClassesSupport.throwIfUnresolvable(clazz, getClassLoader0());
        }
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
        ReflectionData reflectionData = companion.getCompleteReflectionData(this);
        return publicOnly ? reflectionData.publicConstructors : reflectionData.declaredConstructors;
    }

    @Substitute
    private Field[] privateGetDeclaredFields(boolean publicOnly) {
        return publicOnly ? rd.declaredPublicFields : rd.declaredFields;
    }

    @Substitute
    private Method[] privateGetDeclaredMethods(boolean publicOnly) {
        ReflectionData reflectionData = companion.getCompleteReflectionData(this);
        return publicOnly ? reflectionData.declaredPublicMethods : reflectionData.declaredMethods;
    }

    @Substitute
    private Field[] privateGetPublicFields() {
        return rd.publicFields;
    }

    @Substitute
    Method[] privateGetPublicMethods() {
        return companion.getCompleteReflectionData(this).publicMethods;
    }

    @KeepOriginal
    @TargetElement(onlyWith = JDK17OrLater.class)
    private native Target_java_lang_reflect_RecordComponent[] getRecordComponents();

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    private Target_java_lang_reflect_RecordComponent[] getRecordComponents0() {
        Object[] result = rd.recordComponents;
        if (result == null) {
            /* See ReflectionDataBuilder.buildRecordComponents() for details. */
            throw VMError.unsupportedFeature("Record components not available for record class " + getTypeName() + ". " +
                            "All record component accessor methods of this record class must be included in the reflection configuration at image build time, then this method can be called.");
        }
        return (Target_java_lang_reflect_RecordComponent[]) result;
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    private Class<?>[] getPermittedSubclasses() {
        /*
         * We make several assumptions here: we precompute this value by using the cached value from
         * image build time, agent run / custom reflection configuration is required, we ignore all
         * classloader checks, and assume that cached result would be valid.
         */
        if (rd.permittedSubclasses != null) {
            for (Class<?> clazz : rd.permittedSubclasses) {
                PredefinedClassesSupport.throwIfUnresolvable(clazz, getClassLoader0());
            }
        }
        return rd.permittedSubclasses;
    }

    @Substitute
    @SuppressWarnings("unused")
    private void checkMemberAccess(SecurityManager sm, int which, Class<?> caller, boolean checkProxyInterfaces) {
        /* No runtime access checks. */
    }

    @Substitute
    @SuppressWarnings({"deprecation", "unused"})
    private void checkPackageAccess(SecurityManager sm, ClassLoader ccl, boolean checkProxyInterfaces) {
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
            PredefinedClassesSupport.throwIfUnresolvable(rd.enclosingMethodOrConstructor.getDeclaringClass(), getClassLoader0());
            return (Method) rd.enclosingMethodOrConstructor;
        }
        return null;
    }

    @Substitute
    private Constructor<?> getEnclosingConstructor() {
        if (rd.enclosingMethodOrConstructor instanceof Constructor) {
            PredefinedClassesSupport.throwIfUnresolvable(rd.enclosingMethodOrConstructor.getDeclaringClass(), getClassLoader0());
            return (Constructor<?>) rd.enclosingMethodOrConstructor;
        }
        return null;
    }

    @Substitute
    public static Class<?> forName(String className) throws ClassNotFoundException {
        Class<?> caller = Reflection.getCallerClass();
        return forName(className, true, caller.getClassLoader());
    }

    @Substitute //
    public static Class<?> forName(@SuppressWarnings("unused") Module module, String className) {
        /*
         * The module system is not supported for now, therefore the module parameter is ignored and
         * we use the class loader of the caller class instead of the module's loader.
         */
        Class<?> caller = Reflection.getCallerClass();
        try {
            return forName(className, false, caller.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Substitute
    public static Class<?> forName(String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        Class<?> result = ClassForNameSupport.forNameOrNull(name, loader);
        if (result == null && loader != null && PredefinedClassesSupport.hasBytecodeClasses()) {
            result = loader.loadClass(name); // may throw
        }
        if (result == null) {
            throw new ClassNotFoundException(name);
        }
        if (initialize) {
            DynamicHub.fromClass(result).ensureInitialized();
        }
        return result;
    }

    @KeepOriginal
    private native Package getPackage();

    @Substitute //
    public String getPackageName() {
        if (SubstrateUtil.HOSTED) { // avoid eager initialization in image heap
            return computePackageName();
        }
        return companion.getPackageName(this);
    }

    String computePackageName() {
        String pn = null;
        DynamicHub me = this;
        while (me.hubIsArray()) {
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
        return companion.getProtectionDomain();
    }

    @TargetElement(onlyWith = JDK17OrLater.class)
    @Substitute
    private ProtectionDomain protectionDomain() {
        return getProtectionDomain();
    }

    void setProtectionDomainAtRuntime(ProtectionDomain protectionDomain) {
        companion.setProtectionDomain(protectionDomain);
    }

    @Substitute
    public boolean desiredAssertionStatus() {
        return isFlagSet(ASSERTION_STATUS_FLAG_BIT);
    }

    @Substitute //
    public Module getModule() {
        return module;
    }

    @Substitute //
    public String methodToString(String nameArg, Class<?>[] argTypes) {
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
        throw VMError.unsupportedFeature("DynamicHub.reflectionData()");
    }

    @KeepOriginal
    private native boolean isTopLevelClass();

    @Substitute //
    private String getSimpleBinaryName0() {
        if (isAnonymousClass() || enclosingClass == null) {
            return null;
        }
        try {
            int prefix = enclosingClass.getName().length();
            char firstLetter;
            do {
                prefix += 1;
                firstLetter = name.charAt(prefix);
            } while (!Character.isLetter(firstLetter));
            return name.substring(prefix);
        } catch (IndexOutOfBoundsException ex) {
            throw new InternalError("Malformed class name", ex);
        }
        /* See open/src/hotspot/share/prims/jvm.cpp#1522. */
    }

    @KeepOriginal //
    @SuppressWarnings({"unused"})
    private native List<Method> getDeclaredPublicMethods(String nameArg, Class<?>... parameterTypes);

    @Substitute
    public Class<?> getNestHost() {
        return nestHost;
    }

    @Substitute
    public boolean isNestmateOf(Class<?> c) {
        return nestHost == DynamicHub.fromClass(c).nestHost;
    }

    @Substitute
    private Class<?>[] getNestMembers() {
        /*
         * Supporting all nest members is not as easy as supporting only the nest host. It is not
         * enough to just preserve the result of Class.getNestMembers() returned during image
         * generation. This would significantly worsen the static analysis quality, because it would
         * make all nest members reachable if only a single class of the nest is reachable. A full
         * solution would need to filter the nest members based on reachability, i.e., only add nest
         * members when they are reachable by the static analysis. If necessary, this can be
         * implemented though.
         */
        throw VMError.unsupportedFeature("Class.getNestMembers is not supported yet");
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    @Override
    public DynamicHub componentType() {
        return componentType;
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    @Override
    public DynamicHub arrayType() {
        return arrayHub;
    }

    /*
     * The following methods were introduced after JDK 11, so they should be unreachable in JDK
     * versions beforehand. But we still do not want to declare them as native to avoid strange
     * linkage errors, but throw an error instead.
     */

    @KeepOriginal
    @TargetElement(onlyWith = JDK17OrLater.class)
    private Class<?> elementType() {
        throw VMError.unsupportedFeature("Method is not available in JDK 8 or JDK 11");
    }

    @KeepOriginal
    @TargetElement(onlyWith = JDK17OrLater.class)
    @Override
    public String descriptorString() {
        throw VMError.unsupportedFeature("Method is not available in JDK 8 or JDK 11");
    }

    @KeepOriginal
    @TargetElement(onlyWith = JDK17OrLater.class)
    @Override
    public Optional<?> describeConstable() {
        throw VMError.unsupportedFeature("Method is not available in JDK 8 or JDK 11");
    }

    @KeepOriginal
    @TargetElement(onlyWith = JDK17OrLater.class)
    private static native String typeVarBounds(TypeVariable<?> typeVar);

    /*
     * We are defensive and also handle private native methods by marking them as deleted. If they
     * are reachable, the user is certainly doing something wrong. But we do not want to fail with a
     * linking error.
     */
    @Delete
    private static native void registerNatives();

    @Delete
    static native Class<?> getPrimitiveClass(String name);

    @Delete
    private native Object[] getEnclosingMethod0();

    @Delete
    private native Class<?>[] getInterfaces0();

    @Substitute
    private void setSigners(@SuppressWarnings("unused") Object[] signers) {
        throw VMError.unsupportedFeature("Class metadata cannot be changed at run time");
    }

    @Delete
    private native java.security.ProtectionDomain getProtectionDomain0();

    @Delete
    private native String getGenericSignature0();

    @Delete
    native byte[] getRawAnnotations();

    @Delete
    native byte[] getRawTypeAnnotations();

    @Delete
    native ConstantPool getConstantPool();

    @Delete
    private native Field[] getDeclaredFields0(boolean publicOnly);

    @Delete
    private native Method[] getDeclaredMethods0(boolean publicOnly);

    @Delete
    private native <T> Constructor<T>[] getDeclaredConstructors0(boolean publicOnly);

    @Delete
    private native Class<?>[] getDeclaredClasses0();

    @Delete
    private static native boolean desiredAssertionStatus0(Class<?> clazz);

    @Delete
    private native Class<?> getNestHost0();

    @Delete
    private native Class<?>[] getNestMembers0();

    @Delete
    private native String initClassName();
}

/**
 * In JDK versions after 11, {@link java.lang.Class} implements more interfaces: Constable and
 * TypeDescriptor.OfField. Since these interfaces do not exist in older JDK versions, we cannot just
 * have DynamicHub implement them, the code would not compile. But the substitution mechanism also
 * requires the class {@link DynamicHub} to be final, so we cannot use inheritance to have a
 * subclass that implements the additional interfaces.
 *
 * So we use JDK-specific substitution interfaces. When the target interfaces exist, they are like
 * an alias of the original interface. For older JDK versions, they are just normal interfaces
 * without any substitution target. This means they really show up as implemented interfaces of
 * java.lang.Class at run time. This is a benign side effect.
 */

@Substitute
@TargetClass(className = "java.lang.constant.Constable", onlyWith = JDK17OrLater.class)
interface Target_java_lang_constant_Constable {
    @KeepOriginal
    Optional<?> describeConstable();
}

@Substitute
@TargetClass(className = "java.lang.invoke.TypeDescriptor", onlyWith = JDK17OrLater.class)
interface Target_java_lang_invoke_TypeDescriptor {
    @KeepOriginal
    String descriptorString();
}

@Substitute
@TargetClass(className = "java.lang.invoke.TypeDescriptor", innerClass = "OfField", onlyWith = JDK17OrLater.class)
interface Target_java_lang_invoke_TypeDescriptor_OfField extends Target_java_lang_invoke_TypeDescriptor {
    @KeepOriginal
    boolean isArray();

    @KeepOriginal
    boolean isPrimitive();

    @KeepOriginal
    DynamicHub componentType();

    @KeepOriginal
    DynamicHub arrayType();
}

@TargetClass(className = "java.lang.Class", innerClass = "ReflectionData")
final class Target_java_lang_Class_ReflectionData<T> {
    // Checkstyle: stop
    @Alias //
    static String NULL_SENTINEL;
    // Checkstyle: resume
}

@TargetClass(value = jdk.internal.reflect.ReflectionFactory.class)
final class Target_jdk_internal_reflect_ReflectionFactory {

    @Alias //
    private static Target_jdk_internal_reflect_ReflectionFactory soleInstance;

    /**
     * This substitution eliminates the SecurityManager check in the original method, which would
     * make some build-time verifications fail.
     */
    @Substitute
    public static Target_jdk_internal_reflect_ReflectionFactory getReflectionFactory() {
        return soleInstance;
    }
}

@TargetClass(className = "java.lang.reflect.RecordComponent", onlyWith = JDK17OrLater.class)
final class Target_java_lang_reflect_RecordComponent {
}
