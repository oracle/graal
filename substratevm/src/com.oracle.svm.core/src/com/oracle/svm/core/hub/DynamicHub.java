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

import static com.oracle.svm.core.reflect.ReflectionMetadataDecoder.NO_DATA;

import java.io.InputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.jdk.JDK11OrEarlier;
import com.oracle.svm.core.jdk.JDK17OrLater;
import com.oracle.svm.core.jdk.JDK19OrLater;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.reflect.ReflectionMetadataDecoder;
import com.oracle.svm.core.reflect.Target_java_lang_reflect_RecordComponent;
import com.oracle.svm.core.reflect.Target_jdk_internal_reflect_ConstantPool;
import com.oracle.svm.core.util.LazyFinalReference;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;

import jdk.internal.misc.Unsafe;
import jdk.internal.reflect.Reflection;
import jdk.internal.reflect.ReflectionFactory;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.generics.factory.GenericsFactory;
import sun.reflect.generics.repository.ClassRepository;

@Hybrid
@Substitute
@TargetClass(java.lang.Class.class)
@SuppressWarnings({"static-method", "serial"})
@SuppressFBWarnings(value = "Se", justification = "DynamicHub must implement Serializable for compatibility with java.lang.Class, not because of actual serialization")
public final class DynamicHub implements AnnotatedElement, java.lang.reflect.Type, GenericDeclaration, Serializable,
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
     * The returned category does not necessarily match the {@link LayoutEncoding}, see
     * {@link Hybrid} objects for more details.
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
    private final short flags;

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
    /** Is this a VM-internal class that should be hidden from stack traces. */
    private static final int IS_VM_INTERNAL_FLAG_BIT = 8;
    /**
     * Is this a lambda form hidden class that should be hidden from stack traces in some
     * circumstances.
     */
    private static final int IS_LAMBDA_FORM_HIDDEN_BIT = 9;

    private byte instantiationFlags;

    /** Has the type been discovered as instantiated by the static analysis? */
    private static final int IS_INSTANTIATED_BIT = 0;
    /** Can this class be instantiated as an instance. */
    private static final int CAN_INSTANTIATE_AS_INSTANCE_BIT = 1;

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
     * The class that declares this class, as returned by {@code Class.getDeclaringClass0} or an
     * exception that happened at image-build time.
     */
    private final Object declaringClass;

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

    /**
     * Field used for module information access at run-time.
     */
    private Module module;

    /**
     * JDK 11 and later: the class that serves as the host for the nest. All nestmates have the same
     * host.
     */
    private final Class<?> nestHost;

    /**
     * The simple binary name of this class, as returned by {@code Class.getSimpleBinaryName0}.
     */
    private final String simpleBinaryName;

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setModule(Module module) {
        assert module != null;
        this.module = module;
    }

    private final DynamicHubCompanion companion;

    private String signature;

    @Substitute @InjectAccessors(ClassLoaderAccessors.class) //
    private ClassLoader classLoader;

    @Substitute @InjectAccessors(ReflectionDataAccessors.class) //
    private SoftReference<Target_java_lang_Class_ReflectionData<?>> reflectionData;

    @Substitute @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private int classRedefinedCount;

    @Substitute @InjectAccessors(AnnotationDataAccessors.class) //
    private Target_java_lang_Class_AnnotationData annotationData;

    @Substitute @InjectAccessors(AnnotationTypeAccessors.class) //
    private AnnotationType annotationType;

    // This field has a fixed value 3206093459760846163L in java.lang.Class
    @Substitute private static final long serialVersionUID = 3206093459760846163L;

    @Substitute @InjectAccessors(CachedConstructorAccessors.class) //
    private Constructor<?> cachedConstructor;

    @Substitute @InjectAccessors(NewInstanceCallerCacheAccessors.class) //
    @TargetElement(onlyWith = JDK11OrEarlier.class) //
    private Class<?> newInstanceCallerCache;

    @UnknownObjectField(types = DynamicHubMetadata.class, canBeNull = true) private DynamicHubMetadata hubMetadata;

    @UnknownObjectField(types = ReflectionMetadata.class, canBeNull = true) private ReflectionMetadata reflectionMetadata;

    @Platforms(Platform.HOSTED_ONLY.class)
    public DynamicHub(Class<?> hostedJavaClass, String name, int hubType, ReferenceType referenceType, DynamicHub superType, DynamicHub componentHub, String sourceFileName, int modifiers,
                    ClassLoader classLoader, boolean isHidden, boolean isRecord, Class<?> nestHost, boolean assertionStatus, boolean hasDefaultMethods, boolean declaresDefaultMethods,
                    boolean isSealed, boolean isVMInternal, boolean isLambdaFormHidden, String simpleBinaryName, Object declaringClass) {
        this.hostedJavaClass = hostedJavaClass;
        this.name = name;
        this.hubType = hubType;
        this.referenceType = referenceType.getValue();
        this.superHub = superType;
        this.componentType = componentHub;
        this.sourceFileName = sourceFileName;
        this.modifiers = modifiers;
        this.nestHost = nestHost;
        this.simpleBinaryName = simpleBinaryName;
        this.declaringClass = declaringClass;

        this.flags = NumUtil.safeToUShort(makeFlag(IS_PRIMITIVE_FLAG_BIT, hostedJavaClass.isPrimitive()) |
                        makeFlag(IS_INTERFACE_FLAG_BIT, hostedJavaClass.isInterface()) |
                        makeFlag(IS_HIDDEN_FLAG_BIT, isHidden) |
                        makeFlag(IS_RECORD_FLAG_BIT, isRecord) |
                        makeFlag(ASSERTION_STATUS_FLAG_BIT, assertionStatus) |
                        makeFlag(HAS_DEFAULT_METHODS_FLAG_BIT, hasDefaultMethods) |
                        makeFlag(DECLARES_DEFAULT_METHODS_FLAG_BIT, declaresDefaultMethods) |
                        makeFlag(IS_SEALED_FLAG_BIT, isSealed) |
                        makeFlag(IS_VM_INTERNAL_FLAG_BIT, isVMInternal) |
                        makeFlag(IS_LAMBDA_FORM_HIDDEN_BIT, isLambdaFormHidden));

        this.companion = new DynamicHubCompanion(hostedJavaClass, classLoader);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static int makeFlag(int flagBit, boolean value) {
        int flagMask = 1 << flagBit;
        return value ? flagMask : 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isFlagSet(byte flags, int flagBit) {
        int flagMask = 1 << flagBit;
        return (flags & flagMask) != 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isFlagSet(short flags, int flagBit) {
        int flagMask = 1 << flagBit;
        return (flags & flagMask) != 0;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setClassInitializationInfo(ClassInitializationInfo classInitializationInfo) {
        this.classInitializationInfo = classInitializationInfo;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setData(int layoutEncoding, int typeID, int monitorOffset, short typeCheckStart, short typeCheckRange, short typeCheckSlot, short[] typeCheckSlots,
                    CFunctionPointer[] vtable, long referenceMapIndex, boolean isInstantiated, boolean canInstantiateAsInstance) {
        assert this.vtable == null : "Initialization must be called only once";
        assert !(!isInstantiated && canInstantiateAsInstance);

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
        this.instantiationFlags = NumUtil.safeToUByte(makeFlag(IS_INSTANTIATED_BIT, isInstantiated) | makeFlag(CAN_INSTANTIATE_AS_INSTANCE_BIT, canInstantiateAsInstance));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setArrayHub(DynamicHub arrayHub) {
        assert (this.arrayHub == null || this.arrayHub == arrayHub) && arrayHub != null;
        assert arrayHub.getComponentHub() == this;
        this.arrayHub = arrayHub;
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

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setSignature(String signature) {
        this.signature = signature;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public String getSignature() {
        return signature;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setHubMetadata(int enclosingMethodInfoIndex, int annotationsIndex, int typeAnnotationsIndex, int classesEncodingIndex, int permittedSubclassesEncodingIndex) {
        this.hubMetadata = new DynamicHubMetadata(enclosingMethodInfoIndex, annotationsIndex, typeAnnotationsIndex, classesEncodingIndex, permittedSubclassesEncodingIndex);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setReflectionMetadata(int fieldsEncodingIndex, int methodsEncodingIndex, int constructorsEncodingIndex, int recordComponentsEncodingIndex, int classAccessFlags) {
        this.reflectionMetadata = new ReflectionMetadata(fieldsEncodingIndex, methodsEncodingIndex, constructorsEncodingIndex, recordComponentsEncodingIndex, classAccessFlags);
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
        return isFlagSet(flags, HAS_DEFAULT_METHODS_FLAG_BIT);
    }

    public boolean declaresDefaultMethods() {
        return isFlagSet(flags, DECLARES_DEFAULT_METHODS_FLAG_BIT);
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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
        return isFlagSet(instantiationFlags, IS_INSTANTIATED_BIT);
    }

    public boolean canInstantiateAsInstance() {
        return isFlagSet(instantiationFlags, CAN_INSTANTIATE_AS_INSTANCE_BIT);
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

    public int getHubType() {
        return hubType;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInstanceClass() {
        return HubType.isInstance(hubType);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isPodInstanceClass() {
        return HubType.isPodInstance(hubType);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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
        return isFlagSet(flags, IS_INTERFACE_FLAG_BIT);
    }

    @Substitute
    @Override
    public boolean isPrimitive() {
        return isFlagSet(flags, IS_PRIMITIVE_FLAG_BIT);
    }

    @Substitute
    public int getModifiers() {
        return modifiers;
    }

    public int getClassAccessFlags() {
        return reflectionMetadata != null ? reflectionMetadata.classAccessFlags : modifiers;
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

    @KeepOriginal
    private native ClassLoader getClassLoader0();

    public boolean isLoaded() {
        return companion.hasClassLoader();
    }

    void setClassLoaderAtRuntime(ClassLoader loader) {
        companion.setClassLoader(loader);
    }

    @KeepOriginal
    private native String getSimpleName();

    @KeepOriginal //
    private native String getSimpleName0();

    @KeepOriginal
    private native String getCanonicalName();

    @KeepOriginal
    private native String getCanonicalName0();

    @KeepOriginal
    @Override
    public native String getTypeName();

    @KeepOriginal
    private native String getSimpleBinaryName();

    @KeepOriginal
    private native <U> Class<? extends U> asSubclass(Class<U> clazz);

    @KeepOriginal
    private native boolean isAnonymousClass();

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isHidden() {
        return isFlagSet(flags, IS_HIDDEN_FLAG_BIT);
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    public boolean isRecord() {
        return isFlagSet(flags, IS_RECORD_FLAG_BIT);
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    public boolean isSealed() {
        return isFlagSet(flags, IS_SEALED_FLAG_BIT);
    }

    public boolean isVMInternal() {
        return isFlagSet(flags, IS_VM_INTERNAL_FLAG_BIT);
    }

    public boolean isLambdaFormHidden() {
        return isFlagSet(flags, IS_LAMBDA_FORM_HIDDEN_BIT);
    }

    @KeepOriginal
    private native boolean isLocalClass();

    @KeepOriginal
    private native boolean isMemberClass();

    @KeepOriginal
    private native boolean isLocalOrAnonymousClass();

    @KeepOriginal
    private native Class<?> getEnclosingClass();

    @KeepOriginal
    private native Class<?> getDeclaringClass();

    @Substitute
    private Class<?> getDeclaringClass0() {
        if (declaringClass == null) {
            return null;
        } else if (declaringClass instanceof Class) {
            PredefinedClassesSupport.throwIfUnresolvable((Class<?>) declaringClass, getClassLoader0());
            return (Class<?>) declaringClass;
        } else if (declaringClass instanceof LinkageError) {
            throw (LinkageError) declaringClass;
        } else {
            throw VMError.shouldNotReachHere();
        }
    }

    @KeepOriginal
    public native DynamicHub[] getInterfaces();

    @KeepOriginal
    private native DynamicHub[] getInterfaces(boolean cloneArray);

    @KeepOriginal
    public native Object newInstance() throws Throwable;

// Checkstyle: allow direct annotation access (false positives)

    @KeepOriginal
    @Override
    public native <T extends Annotation> T getAnnotation(Class<T> annotationClass);

    @Substitute
    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return getAnnotation(annotationClass) != null;
    }

    @KeepOriginal
    @Override
    public native Annotation[] getAnnotations();

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

        if (result.length == 0 && AnnotationAccess.isAnnotationPresent(annotationClass, Inherited.class)) {
            DynamicHub superClass = (DynamicHub) this.getSuperclass();
            if (superClass != null) {
                /* Determine if the annotation is associated with the superclass. */
                result = superClass.getAnnotationsByType(annotationClass);
            }
        }

        return result;
    }

    @KeepOriginal
    @Override
    public native Annotation[] getDeclaredAnnotations();

    /**
     * In JDK this method uses a lazily computed map of annotations.
     * <p>
     * In SVM we have a pre-initialized array so we use a less efficient implementation from
     * {@link AnnotatedElement} that does the same.
     */
    @KeepOriginal
    @Override
    public native <A extends Annotation> A[] getDeclaredAnnotationsByType(Class<A> annotationClass);

    @KeepOriginal
    @Override
    public native <T extends Annotation> T getDeclaredAnnotation(Class<T> annotationClass);

// Checkstyle: disallow direct annotation access

    @KeepOriginal
    private native Field[] getFields();

    @KeepOriginal
    private native Method[] getMethods();

    @KeepOriginal
    private native Constructor<?>[] getConstructors();

    @Substitute
    public Field getField(String fieldName) throws NoSuchFieldException, SecurityException {
        Objects.requireNonNull(fieldName);
        @SuppressWarnings("removal")
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            checkMemberAccess(sm, Member.PUBLIC, Reflection.getCallerClass(), true);
        }
        Field field = getField0(fieldName);
        if (field == null || ImageSingletons.lookup(ReflectionMetadataDecoder.class).isHiding(field.getModifiers())) {
            throw new NoSuchFieldException(fieldName);
        }
        return getReflectionFactory().copyField(field);
    }

    @KeepOriginal
    private native Method getMethod(@SuppressWarnings("hiding") String name, Class<?>... parameterTypes) throws NoSuchMethodException;

    @KeepOriginal
    private native Constructor<?> getConstructor(Class<?>... parameterTypes);

    @KeepOriginal
    private native Class<?>[] getDeclaredClasses();

    @KeepOriginal
    private native Class<?>[] getClasses();

    @KeepOriginal
    private native Field[] getDeclaredFields();

    @KeepOriginal
    private native Method[] getDeclaredMethods();

    @KeepOriginal
    private native Constructor<?>[] getDeclaredConstructors();

    /**
     * @see #filterHidingFields(Field...)
     */
    @Substitute
    public Field getDeclaredField(String fieldName) throws NoSuchFieldException, SecurityException {
        Objects.requireNonNull(fieldName);
        @SuppressWarnings("removal")
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            checkMemberAccess(sm, Member.DECLARED, Reflection.getCallerClass(), true);
        }
        Field field = searchFields(privateGetDeclaredFields(false), fieldName);
        if (field == null || ImageSingletons.lookup(ReflectionMetadataDecoder.class).isHiding(field.getModifiers())) {
            throw new NoSuchFieldException(fieldName);
        }
        return getReflectionFactory().copyField(field);
    }

    @KeepOriginal
    private native Method getDeclaredMethod(@SuppressWarnings("hiding") String name, Class<?>... parameterTypes);

    @KeepOriginal
    private native Constructor<?> getDeclaredConstructor(Class<?>... parameterTypes);

    @KeepOriginal
    private native Constructor<?>[] privateGetDeclaredConstructors(boolean publicOnly);

    @KeepOriginal
    private native Field[] privateGetDeclaredFields(boolean publicOnly);

    @KeepOriginal
    private native Method[] privateGetDeclaredMethods(boolean publicOnly);

    @KeepOriginal
    private native Field[] privateGetPublicFields();

    @KeepOriginal
    native Method[] privateGetPublicMethods();

    @KeepOriginal
    @TargetElement(onlyWith = JDK17OrLater.class)
    private native Target_java_lang_reflect_RecordComponent[] getRecordComponents();

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    private Target_java_lang_reflect_RecordComponent[] getRecordComponents0() {
        if (reflectionMetadata == null || reflectionMetadata.recordComponentsEncodingIndex == NO_DATA) {
            /* See ReflectionDataBuilder.buildRecordComponents() for details. */
            throw VMError.unsupportedFeature("Record components not available for record class " + getTypeName() + ". " +
                            "All record component accessor methods of this record class must be included in the reflection configuration at image build time, then this method can be called.");
        }
        return ImageSingletons.lookup(ReflectionMetadataDecoder.class).parseRecordComponents(this, reflectionMetadata.recordComponentsEncodingIndex);
    }

    @KeepOriginal
    @TargetElement(onlyWith = JDK17OrLater.class)
    private native Class<?>[] getPermittedSubclasses();

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
    private static ReflectionFactory getReflectionFactory() {
        return Target_jdk_internal_reflect_ReflectionFactory.getReflectionFactory();
    }

    @KeepOriginal
    private static native Field searchFields(Field[] fields, String name);

    /**
     * @see #filterHidingMethods(Method...)
     */
    @Substitute
    private static Method searchMethods(Method[] allMethods, String name, Class<?>[] parameterTypes) {
        Method[] methods = filterHidingMethods(allMethods);
        ReflectionFactory fact = getReflectionFactory();
        Method res = null;
        for (Method m : methods) {
            if (m.getName().equals(name) && arrayContentsEq(parameterTypes, fact.getExecutableSharedParameterTypes(m)) &&
                            (res == null || (res.getReturnType() != m.getReturnType() && res.getReturnType().isAssignableFrom(m.getReturnType())))) {
                res = m;
            }
        }
        return res;
    }

    @KeepOriginal
    private native Constructor<?> getConstructor0(Class<?>[] parameterTypes, int which);

    @KeepOriginal
    private static native boolean arrayContentsEq(Object[] a1, Object[] a2);

    /**
     * @see #filterHidingFields(Field...)
     */
    @Substitute
    private static Field[] copyFields(Field[] original) {
        Field[] arg = filterHidingFields(original);
        Field[] out = new Field[arg.length];
        ReflectionFactory fact = getReflectionFactory();
        for (int i = 0; i < arg.length; i++) {
            out[i] = fact.copyField(arg[i]);
        }
        return out;
    }

    /**
     * @see #filterHidingMethods(Method...)
     */
    @Substitute
    private static Method[] copyMethods(Method[] original) {
        Method[] arg = filterHidingMethods(original);
        Method[] out = new Method[arg.length];
        ReflectionFactory fact = getReflectionFactory();
        for (int i = 0; i < arg.length; i++) {
            out[i] = fact.copyMethod(arg[i]);
        }
        return out;
    }

    @KeepOriginal
    private static native <U> Constructor<U>[] copyConstructors(Constructor<U>[] arg);

    @KeepOriginal
    @Override
    public native TypeVariable<?>[] getTypeParameters();

    @KeepOriginal
    public native Type[] getGenericInterfaces();

    @KeepOriginal
    public native Type getGenericSuperclass();

    @KeepOriginal
    public native AnnotatedType getAnnotatedSuperclass();

    @KeepOriginal
    public native AnnotatedType[] getAnnotatedInterfaces();

    @KeepOriginal
    private native Method getEnclosingMethod();

    @KeepOriginal
    private native Constructor<?> getEnclosingConstructor();

    @Substitute
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    private static Class<?> forName(String className) throws Throwable {
        return forName(className, Reflection.getCallerClass());
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    private static Class<?> forName(String className, Class<?> caller) throws Throwable {
        return forName(className, true, caller.getClassLoader(), caller);
    }

    @Substitute
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    private static Class<?> forName(Module module, String className) throws Throwable {
        return forName(module, className, Reflection.getCallerClass());
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    private static Class<?> forName(@SuppressWarnings("unused") Module module, String className, Class<?> caller) throws Throwable {
        /*
         * The module system is not supported for now, therefore the module parameter is ignored and
         * we use the class loader of the caller class instead of the module's loader.
         */
        try {
            return forName(className, false, caller.getClassLoader(), caller);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Substitute
    private static Class<?> forName(String name, boolean initialize, ClassLoader loader) throws Throwable {
        return forName(name, initialize, loader, Reflection.getCallerClass());
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    private static Class<?> forName(String name, boolean initialize, ClassLoader loader, @SuppressWarnings("unused") Class<?> caller) throws Throwable {
        if (name == null) {
            throw new NullPointerException();
        }
        Class<?> result = ClassForNameSupport.forNameOrNull(name, loader);
        if (result == null && loader != null && PredefinedClassesSupport.hasBytecodeClasses()) {
            result = loader.loadClass(name); // may throw
        }
        if (result == null) {
            throw ClassLoadingExceptionSupport.getExceptionForClass(name, new ClassNotFoundException(name));
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

    /**
     * This method is a copy of {@link Class#toString()}. We cannot use {@link KeepOriginal} because
     * then it would be a native method that cannot be invoked at image build time, which is bad for
     * debug printing.
     */
    @Substitute
    @Override
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
        return isFlagSet(flags, ASSERTION_STATUS_FLAG_BIT);
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

    @KeepOriginal //
    private native <T> Target_java_lang_Class_ReflectionData<T> reflectionData();

    @KeepOriginal
    private native boolean isTopLevelClass();

    @Substitute //
    private String getSimpleBinaryName0() {
        return simpleBinaryName;
    }

    /**
     * @see #filterHidingMethods(Method...)
     */
    @Substitute //
    @SuppressWarnings({"unused"})
    List<Method> getDeclaredPublicMethods(String methodName, Class<?>... parameterTypes) {
        Method[] methods = filterHidingMethods(privateGetDeclaredMethods(/* publicOnly */ true));
        ReflectionFactory factory = getReflectionFactory();
        List<Method> result = new ArrayList<>();
        for (Method method : methods) {
            if (method.getName().equals(methodName) && Arrays.equals(factory.getExecutableSharedParameterTypes(method), parameterTypes)) {
                result.add(factory.copyMethod(method));
            }
        }
        return result;
    }

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

    @Substitute
    private Object[] getEnclosingMethod0() {
        if (hubMetadata == null || hubMetadata.enclosingMethodInfoIndex == NO_DATA) {
            return null;
        }
        Object[] enclosingMethod = ImageSingletons.lookup(ReflectionMetadataDecoder.class).parseEnclosingMethod(hubMetadata.enclosingMethodInfoIndex);
        if (enclosingMethod != null) {
            PredefinedClassesSupport.throwIfUnresolvable((Class<?>) enclosingMethod[0], getClassLoader0());
        }
        return enclosingMethod;
    }

    @Substitute
    private DynamicHub[] getInterfaces0() {
        if (interfacesEncoding == null) {
            return new DynamicHub[0];
        } else if (interfacesEncoding instanceof DynamicHub) {
            return new DynamicHub[]{(DynamicHub) interfacesEncoding};
        } else {
            return (DynamicHub[]) interfacesEncoding;
        }
    }

    @Substitute
    private void setSigners(@SuppressWarnings("unused") Object[] signers) {
        throw VMError.unsupportedFeature("Class metadata cannot be changed at run time");
    }

    @Delete
    private native java.security.ProtectionDomain getProtectionDomain0();

    @Substitute
    private String getGenericSignature0() {
        return signature;
    }

    @Substitute
    byte[] getRawAnnotations() {
        if (hubMetadata == null || hubMetadata.annotationsIndex == NO_DATA) {
            return null;
        }
        return ImageSingletons.lookup(ReflectionMetadataDecoder.class).parseByteArray(hubMetadata.annotationsIndex);
    }

    @Substitute
    byte[] getRawTypeAnnotations() {
        if (hubMetadata == null || hubMetadata.typeAnnotationsIndex == NO_DATA) {
            return null;
        }
        return ImageSingletons.lookup(ReflectionMetadataDecoder.class).parseByteArray(hubMetadata.typeAnnotationsIndex);
    }

    @Substitute
    Target_jdk_internal_reflect_ConstantPool getConstantPool() {
        return new Target_jdk_internal_reflect_ConstantPool();
    }

    @Substitute
    private Field[] getDeclaredFields0(boolean publicOnly) {
        if (reflectionMetadata == null || reflectionMetadata.fieldsEncodingIndex == NO_DATA) {
            return new Field[0];
        }
        return ImageSingletons.lookup(ReflectionMetadataDecoder.class).parseFields(this, reflectionMetadata.fieldsEncodingIndex, publicOnly, true);
    }

    @Substitute
    private Method[] getDeclaredMethods0(boolean publicOnly) {
        if (reflectionMetadata == null || reflectionMetadata.methodsEncodingIndex == NO_DATA) {
            return new Method[0];
        }
        return ImageSingletons.lookup(ReflectionMetadataDecoder.class).parseMethods(this, reflectionMetadata.methodsEncodingIndex, publicOnly, true);
    }

    @Substitute
    private Constructor<?>[] getDeclaredConstructors0(boolean publicOnly) {
        if (reflectionMetadata == null || reflectionMetadata.constructorsEncodingIndex == NO_DATA) {
            return new Constructor<?>[0];
        }
        return ImageSingletons.lookup(ReflectionMetadataDecoder.class).parseConstructors(this, reflectionMetadata.constructorsEncodingIndex, publicOnly, true);
    }

    @Substitute
    private Class<?>[] getDeclaredClasses0() {
        if (hubMetadata == null || hubMetadata.classesEncodingIndex == NO_DATA) {
            return new Class<?>[0];
        }
        Class<?>[] declaredClasses = ImageSingletons.lookup(ReflectionMetadataDecoder.class).parseClasses(hubMetadata.classesEncodingIndex);
        for (Class<?> clazz : declaredClasses) {
            PredefinedClassesSupport.throwIfUnresolvable(clazz, getClassLoader0());
        }
        return declaredClasses;
    }

    @Delete
    private static native boolean desiredAssertionStatus0(Class<?> clazz);

    @Delete
    private native Class<?> getNestHost0();

    @Delete
    private native Class<?>[] getNestMembers0();

    @Delete
    private native String initClassName();

    @KeepOriginal
    private static native Class<?> toClass(Type o);

    @Substitute
    private ClassRepository getGenericInfo() {
        return companion.getGenericInfo(this);
    }

    ClassRepository computeGenericInfo() {
        String genericSignature = getGenericSignature0();
        if (genericSignature == null) {
            return ClassRepository.NONE;
        } else {
            return ClassRepository.make(genericSignature, getFactory());
        }
    }

    @KeepOriginal
    private native Target_java_lang_Class_EnclosingMethodInfo getEnclosingMethodInfo();

    @KeepOriginal
    private native boolean hasEnclosingMethodInfo();

    @KeepOriginal
    private native <T> Target_java_lang_Class_ReflectionData<T> newReflectionData(SoftReference<Target_java_lang_Class_ReflectionData<T>> oldReflectionData,
                    int redefinitionCount);

    @KeepOriginal
    private native Target_java_lang_Class_AnnotationData annotationData();

    @KeepOriginal
    private native Target_java_lang_Class_AnnotationData createAnnotationData(int redefinitionCount);

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    private Class<?>[] getPermittedSubclasses0() {
        if (hubMetadata == null || hubMetadata.permittedSubclassesEncodingIndex == NO_DATA) {
            return null;
        }
        Class<?>[] permittedSubclasses = ImageSingletons.lookup(ReflectionMetadataDecoder.class).parseClasses(hubMetadata.permittedSubclassesEncodingIndex);
        for (Class<?> clazz : permittedSubclasses) {
            PredefinedClassesSupport.throwIfUnresolvable(clazz, getClassLoader0());
        }
        return permittedSubclasses;
    }

    @KeepOriginal
    private native GenericsFactory getFactory();

    @KeepOriginal
    private native Method getMethod0(String methodName, Class<?>[] parameterTypes);

    @KeepOriginal
    private static native void addAll(Collection<Field> c, Field[] o);

    @KeepOriginal
    private native Target_java_lang_PublicMethods_MethodList getMethodsRecursive(String methodName, Class<?>[] parameterTypes, boolean includeStatic);

    @KeepOriginal
    private native Field getField0(String fieldName);

    @KeepOriginal
    native AnnotationType getAnnotationType();

    @KeepOriginal
    static native byte[] getExecutableTypeAnnotationBytes(Executable ex);

    @KeepOriginal
    @TargetElement(onlyWith = JDK17OrLater.class)
    private native boolean isDirectSubType(Class<?> c);

    @KeepOriginal
    native boolean casAnnotationType(AnnotationType oldType, AnnotationType newType);

    /*
     * We need to filter out hiding elements at the last moment. This ensures that the JDK internals
     * see them as regular methods and fields and ensure their visibility is correct, but they
     * should not be returned to application code.
     */
    private static Field[] filterHidingFields(Field... fields) {
        List<Field> filtered = new ArrayList<>();
        for (Field field : fields) {
            if (!ImageSingletons.lookup(ReflectionMetadataDecoder.class).isHiding(field.getModifiers())) {
                filtered.add(field);
            }
        }
        return filtered.toArray(new Field[0]);
    }

    private static Method[] filterHidingMethods(Method... methods) {
        List<Method> filtered = new ArrayList<>();
        for (Method method : methods) {
            if (!ImageSingletons.lookup(ReflectionMetadataDecoder.class).isHiding(method.getModifiers())) {
                filtered.add(method);
            }
        }
        return filtered.toArray(new Method[0]);
    }

    public void setJrfEventConfiguration(Object configuration) {
        companion.setJfrEventConfiguration(configuration);
    }

    public Object getJfrEventConfiguration() {
        return companion.getJfrEventConfiguration();
    }

    private static class ReflectionDataAccessors {
        @SuppressWarnings("unused")
        private static SoftReference<Target_java_lang_Class_ReflectionData<?>> getReflectionData(DynamicHub that) {
            return that.companion.getReflectionData();
        }
    }

    private static class ClassLoaderAccessors {
        @SuppressWarnings("unused")
        private static ClassLoader getClassLoader(DynamicHub that) {
            return that.companion.getClassLoader();
        }
    }

    private static class AnnotationDataAccessors {
        @SuppressWarnings("unused")
        private static Target_java_lang_Class_AnnotationData getAnnotationData(DynamicHub that) {
            return that.companion.getAnnotationData();
        }
    }

    private static class AnnotationTypeAccessors {
        @SuppressWarnings("unused")
        private static AnnotationType getAnnotationType(DynamicHub that) {
            return that.companion.getAnnotationType();
        }
    }

    private static class CachedConstructorAccessors {
        @SuppressWarnings("unused")
        private static Constructor<?> getCachedConstructor(DynamicHub that) {
            /*
             * The JavaDoc for the Class.newInstance method states that "The class is initialized if
             * it has not already been initialized". However, it doesn't specify if the absence of a
             * nullary constructor will result in an InstantiationException before the class is
             * initialized. We eagerly initialize the class to conform with JCK tests.
             */
            that.ensureInitialized();
            return that.companion.getCachedConstructor();
        }

        @SuppressWarnings("unused")
        private static void setCachedConstructor(DynamicHub that, Constructor<?> value) {
            that.companion.setCachedConstructor(value);
        }
    }

    private static class NewInstanceCallerCacheAccessors {
        @SuppressWarnings("unused")
        private static Class<?> getNewInstanceCallerCache(DynamicHub that) {
            return that.companion.getNewInstanceCallerCache();
        }

        @SuppressWarnings("unused")
        private static void setNewInstanceCallerCache(DynamicHub that, Class<?> value) {
            that.companion.setNewInstanceCallerCache(value);
        }
    }

    private static final class DynamicHubMetadata {
        final int enclosingMethodInfoIndex;

        final int annotationsIndex;

        final int typeAnnotationsIndex;

        final int classesEncodingIndex;

        @TargetElement(onlyWith = JDK17OrLater.class)//
        final int permittedSubclassesEncodingIndex;

        private DynamicHubMetadata(int enclosingMethodInfoIndex, int annotationsIndex, int typeAnnotationsIndex, int classesEncodingIndex, int permittedSubclassesEncodingIndex) {
            this.enclosingMethodInfoIndex = enclosingMethodInfoIndex;
            this.annotationsIndex = annotationsIndex;
            this.typeAnnotationsIndex = typeAnnotationsIndex;
            this.classesEncodingIndex = classesEncodingIndex;
            this.permittedSubclassesEncodingIndex = permittedSubclassesEncodingIndex;
        }
    }

    private static final class ReflectionMetadata {
        final int fieldsEncodingIndex;

        final int methodsEncodingIndex;

        final int constructorsEncodingIndex;

        @TargetElement(onlyWith = JDK17OrLater.class)//
        final int recordComponentsEncodingIndex;

        final int classAccessFlags;

        private ReflectionMetadata(int fieldsEncodingIndex, int methodsEncodingIndex, int constructorsEncodingIndex, int recordComponentsEncodingIndex, int classAccessFlags) {
            this.fieldsEncodingIndex = fieldsEncodingIndex;
            this.methodsEncodingIndex = methodsEncodingIndex;
            this.constructorsEncodingIndex = constructorsEncodingIndex;
            this.recordComponentsEncodingIndex = recordComponentsEncodingIndex;
            this.classAccessFlags = classAccessFlags;
        }
    }

    public Field[] getReachableFields() {
        if (reflectionMetadata == null || reflectionMetadata.fieldsEncodingIndex == NO_DATA) {
            return new Field[0];
        }
        return ImageSingletons.lookup(ReflectionMetadataDecoder.class).parseFields(this, reflectionMetadata.fieldsEncodingIndex, false, false);
    }

    public Method[] getReachableMethods() {
        if (reflectionMetadata == null || reflectionMetadata.methodsEncodingIndex == NO_DATA) {
            return new Method[0];
        }
        return ImageSingletons.lookup(ReflectionMetadataDecoder.class).parseMethods(this, reflectionMetadata.methodsEncodingIndex, false, false);
    }

    public Constructor<?>[] getReachableConstructors() {
        if (reflectionMetadata == null || reflectionMetadata.constructorsEncodingIndex == NO_DATA) {
            return new Constructor<?>[0];
        }
        return ImageSingletons.lookup(ReflectionMetadataDecoder.class).parseConstructors(this, reflectionMetadata.constructorsEncodingIndex, false, false);
    }
}

/**
 * In JDK versions after 11, {@link java.lang.Class} implements more interfaces: Constable and
 * TypeDescriptor.OfField. Since these interfaces do not exist in older JDK versions, we cannot just
 * have DynamicHub implement them, the code would not compile. But the substitution mechanism also
 * requires the class {@link DynamicHub} to be final, so we cannot use inheritance to have a
 * subclass that implements the additional interfaces.
 * <p>
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
    private static ReflectionFactory soleInstance;

    /**
     * This substitution eliminates the SecurityManager check in the original method, which would
     * make some build-time verifications fail.
     */
    @Substitute
    public static ReflectionFactory getReflectionFactory() {
        return soleInstance;
    }

    /**
     * Do not use the field handle based field accessor but the one based on unsafe. It takes effect
     * when {@code Target_java_lang_reflect_Field#fieldAccessorField#fieldAccessor} is recomputed at
     * runtime. See also GR-39586.
     */
    @TargetElement(onlyWith = JDK19OrLater.class)
    @Substitute
    static boolean useFieldHandleAccessor() {
        return false;
    }
}

@TargetClass(className = "java.lang.Class", innerClass = "EnclosingMethodInfo")
final class Target_java_lang_Class_EnclosingMethodInfo {
}

@TargetClass(className = "java.lang.Class", innerClass = "AnnotationData")
final class Target_java_lang_Class_AnnotationData {
}

@TargetClass(className = "java.lang.PublicMethods", innerClass = "MethodList")
final class Target_java_lang_PublicMethods_MethodList {
    @Alias //
    Method method;

    @Alias //
    Target_java_lang_PublicMethods_MethodList next;

    @Substitute
    Method getMostSpecific() {
        Method m = method;
        Class<?> rt = m.getReturnType();
        for (Target_java_lang_PublicMethods_MethodList ml = next; ml != null; ml = ml.next) {
            Method m2 = ml.method;
            Class<?> rt2 = m2.getReturnType();
            if (rt2 != rt && rt.isAssignableFrom(rt2)) {
                // found more specific return type
                m = m2;
                rt = rt2;
            }
        }
        /* Filter out hiding methods after the retursive lookup is done */
        return ImageSingletons.lookup(ReflectionMetadataDecoder.class).isHiding(m.getModifiers()) ? null : m;
    }
}

@TargetClass(className = "java.lang.Class", innerClass = "Atomic")
final class Target_java_lang_Class_Atomic {
    @Delete static Unsafe unsafe;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, declClass = DynamicHubCompanion.class, name = "reflectionData") //
    private static long reflectionDataOffset;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, declClass = DynamicHubCompanion.class, name = "annotationType") //
    private static long annotationTypeOffset;

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, declClass = DynamicHubCompanion.class, name = "annotationData") //
    private static long annotationDataOffset;

    @Substitute
    static <T> boolean casReflectionData(DynamicHub clazz,
                    SoftReference<Target_java_lang_Class_ReflectionData<T>> oldData,
                    SoftReference<Target_java_lang_Class_ReflectionData<T>> newData) {
        return GraalUnsafeAccess.getUnsafe().compareAndSwapObject(clazz.getCompanion(), reflectionDataOffset, oldData, newData);
    }

    @Substitute
    static boolean casAnnotationType(DynamicHub clazz,
                    AnnotationType oldType,
                    AnnotationType newType) {
        return GraalUnsafeAccess.getUnsafe().compareAndSwapObject(clazz.getCompanion(), annotationTypeOffset, oldType, newType);
    }

    @Substitute
    static boolean casAnnotationData(DynamicHub clazz,
                    Target_java_lang_Class_AnnotationData oldData,
                    Target_java_lang_Class_AnnotationData newData) {
        return GraalUnsafeAccess.getUnsafe().compareAndSwapObject(clazz.getCompanion(), annotationDataOffset, oldData, newData);
    }
}
