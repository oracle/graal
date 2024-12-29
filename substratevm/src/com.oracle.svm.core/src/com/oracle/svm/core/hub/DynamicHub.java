/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.MissingRegistrationUtils.throwMissingRegistrationErrors;
import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.annotate.TargetElement.CONSTRUCTOR_NAME;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_CLASSES_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_CONSTRUCTORS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_DECLARED_CLASSES_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_DECLARED_CONSTRUCTORS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_DECLARED_FIELDS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_DECLARED_METHODS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_FIELDS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_METHODS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_NEST_MEMBERS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_PERMITTED_SUBCLASSES_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_RECORD_COMPONENTS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_SIGNERS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.CLASS_ACCESS_FLAGS_MASK;
import static com.oracle.svm.core.graal.meta.DynamicHubOffsets.writeObject;
import static com.oracle.svm.core.graal.meta.DynamicHubOffsets.writeInt;
import static com.oracle.svm.core.graal.meta.DynamicHubOffsets.writeChar;
import static com.oracle.svm.core.graal.meta.DynamicHubOffsets.writeShort;
import static com.oracle.svm.core.graal.meta.DynamicHubOffsets.writeByte;
import static com.oracle.svm.core.reflect.RuntimeMetadataDecoder.NO_DATA;

import java.io.InputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.invoke.TypeDescriptor;
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
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URL;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import com.oracle.svm.core.NeverInlineTrivial;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.BuildPhaseProvider.AfterCompilation;
import com.oracle.svm.core.BuildPhaseProvider.AfterHostedUniverse;
import com.oracle.svm.core.BuildPhaseProvider.CompileQueueFinished;
import com.oracle.svm.core.NeverInline;
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
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.configure.RuntimeConditionSet;
import com.oracle.svm.core.graal.meta.DynamicHubOffsets;
import com.oracle.svm.core.graal.nodes.SubstrateNewDynamicHubNode;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.imagelayer.DynamicImageLayerInfo;
import com.oracle.svm.core.jdk.JDK21OrEarlier;
import com.oracle.svm.core.jdk.JDKLatest;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils;
import com.oracle.svm.core.reflect.RuntimeMetadataDecoder;
import com.oracle.svm.core.reflect.RuntimeMetadataDecoder.ConstructorDescriptor;
import com.oracle.svm.core.reflect.RuntimeMetadataDecoder.FieldDescriptor;
import com.oracle.svm.core.reflect.RuntimeMetadataDecoder.MethodDescriptor;
import com.oracle.svm.core.reflect.fieldaccessor.UnsafeFieldAccessorFactory;
import com.oracle.svm.core.reflect.serialize.SerializationRegistry;
import com.oracle.svm.core.reflect.target.Target_jdk_internal_reflect_ConstantPool;
import com.oracle.svm.core.util.LazyFinalReference;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.nodes.java.FinalFieldBarrierNode;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import jdk.internal.access.JavaLangReflectAccess;
import jdk.internal.misc.Unsafe;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.CallerSensitiveAdapter;
import jdk.internal.reflect.ConstructorAccessor;
import jdk.internal.reflect.FieldAccessor;
import jdk.internal.reflect.Reflection;
import jdk.internal.reflect.ReflectionFactory;
import jdk.internal.vm.annotation.Stable;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.generics.factory.GenericsFactory;
import sun.reflect.generics.repository.ClassRepository;

/**
 * Instantiations of this class have a special layout. See {@code DynamicHubLayout} for a
 * description of how the object is arranged.
 * <p />
 * A {@code DynamicHub} ends up initialized in the read-only part of the image heap, and therefore
 * fields are considered immutable. In scenarios where a {@code DynamicHub} can be allocated at
 * run-time it is important to keep this property.
 */
@Substitute
@TargetClass(java.lang.Class.class)
@SuppressWarnings({"static-method", "serial"})
@SuppressFBWarnings(value = "Se", justification = "DynamicHub must implement Serializable for compatibility with java.lang.Class, not because of actual serialization")
public final class DynamicHub implements AnnotatedElement, java.lang.reflect.Type, GenericDeclaration, Serializable, TypeDescriptor.OfField<DynamicHub>, Constable {

    @Substitute //
    private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Class<?> hostedJavaClass;

    /**
     * The name of the class this hub is representing, as defined in {@link Class#getName()}.
     *
     * Even though the field is only assigned in the constructor, it cannot be final: The
     * substitution system does not allow a final field when the target class has a field with the
     * same name. And using a different field name fails for various other reasons that are too
     * complicated to fix. Therefore, we ensure early constant folding using an invocation plugin
     * for the getName() method.
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
    @UnknownPrimitiveField(availability = AfterHostedUniverse.class)//
    private int layoutEncoding;

    /**
     * Unique id number for this type, used for fast type checks and type casts.
     */
    @UnknownPrimitiveField(availability = AfterHostedUniverse.class)//
    private int typeID;

    // region closed-world only fields

    /**
     * In our current version, type checks are accomplished by performing a range check on a value
     * from an array. The slot to read from the checked type is determined by
     * {@link #getTypeCheckSlot()} and the check passes if {@link #getTypeCheckStart()} <= value <
     * ({@link #getTypeCheckStart()} + {@link #getTypeCheckRange()}).
     */
    @UnknownPrimitiveField(availability = AfterHostedUniverse.class)//
    private short typeCheckStart;

    /**
     * The number of ids which are in valid range for a type check.
     */
    @UnknownPrimitiveField(availability = AfterHostedUniverse.class)//
    private short typeCheckRange;

    /**
     * The value slot within the type id slot array to read when comparing against this type.
     */
    @UnknownPrimitiveField(availability = AfterHostedUniverse.class)//
    private short typeCheckSlot;

    /**
     * Array containing this type's type check id information. During a type check, a requested
     * column of this array is read to determine if this value fits within the range of ids which
     * match the assignee's type.
     */
    @UnknownObjectField(availability = AfterHostedUniverse.class)//
    private short[] closedTypeWorldTypeCheckSlots;

    // endregion closed-world only fields

    // region open-world only fields

    /**
     * This stores the depth of the type in the inheritance hierarchy. If the type is an interface
     * then the typeIDDepth is -1.
     */
    @UnknownPrimitiveField(availability = AfterHostedUniverse.class)//
    private int typeIDDepth;

    @UnknownPrimitiveField(availability = AfterHostedUniverse.class)//
    private int numClassTypes;

    @UnknownPrimitiveField(availability = AfterHostedUniverse.class)//
    private int numInterfaceTypes;

    /**
     * Array containing this type's type check id information. During a type check, these slots are
     * searched for a matching typeID.
     */
    @UnknownObjectField(availability = AfterHostedUniverse.class)//
    private int[] openTypeWorldTypeCheckSlots;

    // endregion open-world only fields

    /**
     * The offset of the synthetic field which stores whatever is used for monitorEnter/monitorExit
     * by an instance of this class. If 0, then instances of this class are locked using a side
     * table.
     */
    @UnknownPrimitiveField(availability = AfterHostedUniverse.class)//
    private char monitorOffset;

    @UnknownPrimitiveField(availability = AfterHostedUniverse.class)//
    private char identityHashOffset;

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
    /**
     * Indicates this Class was linked during build time. Accessing an unlinked class during run
     * time will throw an error.
     */
    private static final int IS_LINKED_BIT = 10;
    /**
     * Indicates whether the class is a proxy class according to
     * {@link java.lang.reflect.Proxy#isProxyClass}.
     */
    private static final int IS_PROXY_CLASS_BIT = 11;

    /**
     * Similar to {@link #flags}, but non-final because {@link #setSharedData} sets the value.
     */
    @UnknownPrimitiveField(availability = AfterHostedUniverse.class)//
    private byte additionalFlags;

    /** Indicates whether the type has been discovered as instantiated by the static analysis. */
    private static final int IS_INSTANTIATED_BIT = 0;

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
    @UnknownPrimitiveField(availability = AfterHostedUniverse.class)//
    private int referenceMapIndex;

    private final byte layerId;

    /**
     * Back link to the SubstrateType used by the substrate meta access. Only used for the subset of
     * types for which a SubstrateType exists.
     */
    @UnknownObjectField(fullyQualifiedTypes = "com.oracle.svm.graal.meta.SubstrateType", canBeNull = true)//
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
    @Stable //
    private ClassInitializationInfo classInitializationInfo;

    @UnknownObjectField(availability = AfterHostedUniverse.class)//
    private CFunctionPointer[] vtable;

    /** Field used for module information access at run-time. */
    @Stable //
    private Module module;

    /** The class that serves as the host for the nest. All nestmates have the same host. */
    private final Class<?> nestHost;

    /** The simple binary name of this class, as returned by {@code Class.getSimpleBinaryName0}. */
    private final String simpleBinaryName;

    private final DynamicHubCompanion companion;

    private final String signature;

    @Substitute //
    @InjectAccessors(ClassLoaderAccessors.class) //
    private ClassLoader classLoader;

    @Substitute //
    @InjectAccessors(ReflectionDataAccessors.class) //
    private SoftReference<Target_java_lang_Class_ReflectionData<?>> reflectionData;

    @Substitute //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private int classRedefinedCount;

    @Substitute //
    @InjectAccessors(AnnotationDataAccessors.class) //
    private Target_java_lang_Class_AnnotationData annotationData;

    @Substitute //
    @InjectAccessors(AnnotationTypeAccessors.class) //
    private AnnotationType annotationType;

    // This field has a fixed value 3206093459760846163L in java.lang.Class
    @Substitute private static final long serialVersionUID = 3206093459760846163L;

    @Substitute //
    @InjectAccessors(CachedConstructorAccessors.class) //
    private Constructor<?> cachedConstructor;

    @UnknownObjectField(canBeNull = true, availability = AfterCompilation.class) //
    private DynamicHubMetadata hubMetadata;

    @UnknownObjectField(canBeNull = true, availability = AfterCompilation.class) //
    private ReflectionMetadata reflectionMetadata;

    @Platforms(Platform.HOSTED_ONLY.class)
    public DynamicHub(Class<?> hostedJavaClass, String name, int hubType, ReferenceType referenceType, DynamicHub superType,
                    DynamicHub componentHub, String sourceFileName, int modifiers, short flags, ClassLoader classLoader,
                    Class<?> nestHost, String simpleBinaryName, Object declaringClass, String signature, int layerId) {
        this.hostedJavaClass = hostedJavaClass;
        this.module = hostedJavaClass.getModule();
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
        this.signature = signature;

        assert layerId < DynamicImageLayerInfo.CREMA_LAYER_ID;
        this.layerId = NumUtil.safeToByte(layerId);

        this.flags = flags;

        this.companion = new DynamicHubCompanion(hostedJavaClass, classLoader);
    }

    /**
     * This helper is used for allocating and initializing a new {@code DynamicHub} at runtime.
     * <p>
     * Fields in {@code DynamicHub} are immutable, and writes in this method have location identity
     * {@code ANY_LOCATION}: With this setup the compiler is allowed to float reads above such
     * writes, therefore there must not be any reads in this helper. Also, further code must not be
     * reachable that reads from the just created {@code DynamicHub} in this method.
     * <p>
     * Regular stores should also not be used for non-final fields of {@code DynamicHub}, otherwise
     * the analysis won't conclude immutability for such fields.
     * <p>
     * Note that the GC can handle partially initialized {@code DynamicHub}s therefore this helper
     * does not need to be {@link Uninterruptible}. However, other components might not, e.g. a
     * {@code DynamicHub} must be fully initialized when it is used in an object header.
     */
    @NeverInline("Fields of DynamicHub are immutable. Immutable reads could float above ANY_LOCATION writes.")
    public static DynamicHub allocate(String name, DynamicHub superHub, DynamicHub componentHub, String sourceFileName,
                    int modifiers, short flags, ClassLoader classLoader, Class<?> nestHost, String simpleBinaryName,
                    Object declaringClass, String signature) {
        VMError.guarantee(RuntimeClassLoading.isSupported());

        ReferenceType referenceType = ReferenceType.computeReferenceType(DynamicHub.toClass(superHub));
        // GR-59683: HubType.OBJECT_ARRAY?
        int hubTybe = referenceType == ReferenceType.None ? HubType.INSTANCE : HubType.REFERENCE_INSTANCE;

        DynamicHubCompanion companion = new DynamicHubCompanion(classLoader);
        /* Always allow unsafe allocation for classes that were loaded at run-time. */
        companion.setUnsafeAllocate();

        // GR-59687: Correct size and content for vtable
        int vTableEntries = 0x100;
        ClassInitializationInfo classInitializationInfo = new ClassInitializationInfo(false);

        // GR-60069: Determine size for instance and offsets for monitor and identityHashCode
        int layoutEncoding = 0x40;
        char monitorOffset = 0;
        char identityHashOffset = 0;

        // GR-59687: Determine typecheck related infos
        int typeID = 0;
        int typeIDDepth = 0;
        int numClassTypes = 2;
        int numInterfacesTypes = 0;
        int[] openTypeWorldTypeCheckSlots = new int[numClassTypes + (numInterfacesTypes * 2)];

        byte additionalFlags = NumUtil.safeToUByte(makeFlag(IS_INSTANTIATED_BIT, true));

        // GR-59683: Proper values needed.
        DynamicHub arrayHub = null;
        Object interfacesEncoding = null;
        Object enumConstantsReference = null;

        // GR-60080: Proper referenceMap needed.
        int referenceMapIndex = DynamicHub.fromClass(Object.class).referenceMapIndex;

        // GR-59683: Maybe can be used to inject a backreference to InterpreterResolvedObjectType
        SharedType metaType = null;

        // GR-59683
        Module module = null;

        // GR-57813
        int classRedefinedCount = 0;
        DynamicHubMetadata hubMetadata = null;
        ReflectionMetadata reflectionMetadata = null;

        /*
         * We cannot do the allocation via {@code new DynamicHub(...)} because we need to inject the
         * length for its vtable.
         */
        DynamicHub hub = SubstrateNewDynamicHubNode.allocate(DynamicHub.class, vTableEntries);

        DynamicHubOffsets dynamicHubOffsets = DynamicHubOffsets.singleton();
        /* Write fields in defining order. */
        writeObject(hub, dynamicHubOffsets.getNameOffset(), name);
        writeInt(hub, dynamicHubOffsets.getHubTypeOffset(), hubTybe);
        writeByte(hub, dynamicHubOffsets.getReferenceTypeOffset(), referenceType.getValue());

        writeInt(hub, dynamicHubOffsets.getLayoutEncodingOffset(), layoutEncoding);
        writeInt(hub, dynamicHubOffsets.getTypeIDOffset(), typeID);
        // skip typeCheckStart, typeCheckRange, typeCheckSlot and
        // closedTypeWorldTypeCheckSlots (closed-world only)
        writeInt(hub, dynamicHubOffsets.getTypeIDDepthOffset(), typeIDDepth);
        writeInt(hub, dynamicHubOffsets.getNumClassTypesOffset(), numClassTypes);

        writeInt(hub, dynamicHubOffsets.getNumInterfaceTypesOffset(), numInterfacesTypes);
        writeObject(hub, dynamicHubOffsets.getOpenTypeWorldTypeCheckSlotsOffset(), openTypeWorldTypeCheckSlots);

        writeChar(hub, dynamicHubOffsets.getMonitorOffsetOffset(), monitorOffset);
        writeChar(hub, dynamicHubOffsets.getIdentityHashOffsetOffset(), identityHashOffset);

        writeShort(hub, dynamicHubOffsets.getFlagsOffset(), flags);
        writeByte(hub, dynamicHubOffsets.getAdditionalFlagsOffset(), additionalFlags);
        writeInt(hub, dynamicHubOffsets.getModifiersOffset(), modifiers);

        writeObject(hub, dynamicHubOffsets.getSuperHubOffset(), superHub);
        writeObject(hub, dynamicHubOffsets.getComponentTypeOffset(), componentHub);
        writeObject(hub, dynamicHubOffsets.getArrayHubOffset(), arrayHub);

        writeObject(hub, dynamicHubOffsets.getDeclaringClassOffset(), declaringClass);
        writeObject(hub, dynamicHubOffsets.getInterfacesEncodingOffset(), interfacesEncoding);
        writeObject(hub, dynamicHubOffsets.getEnumConstantsReferenceOffset(), enumConstantsReference);

        writeInt(hub, dynamicHubOffsets.getReferenceMapIndexOffset(), referenceMapIndex);
        writeByte(hub, dynamicHubOffsets.getLayerIdOffset(), NumUtil.safeToByte(DynamicImageLayerInfo.CREMA_LAYER_ID));
        writeObject(hub, dynamicHubOffsets.getMetaTypeOffset(), metaType);

        writeObject(hub, dynamicHubOffsets.getSourceFileNameOffset(), sourceFileName);
        writeObject(hub, dynamicHubOffsets.getClassInitializationInfoOffset(), classInitializationInfo);
        // skip vtable (special treatment)
        writeObject(hub, dynamicHubOffsets.getModuleOffset(), module);

        writeObject(hub, dynamicHubOffsets.getNestHostOffset(), nestHost);
        writeObject(hub, dynamicHubOffsets.getSimpleBinaryNameOffset(), simpleBinaryName);
        writeObject(hub, dynamicHubOffsets.getCompanionOffset(), companion);

        writeObject(hub, dynamicHubOffsets.getSignatureOffset(), signature);
        writeInt(hub, dynamicHubOffsets.getClassRedefinedCountOffset(), classRedefinedCount);
        writeObject(hub, dynamicHubOffsets.getHubMetadataOffset(), hubMetadata);
        writeObject(hub, dynamicHubOffsets.getReflectionMetadataOffset(), reflectionMetadata);

        FinalFieldBarrierNode.finalFieldBarrier(hub);

        return hub;
    }

    public static short makeFlags(boolean isPrimitive, boolean isInterface, boolean isHidden, boolean isRecord, boolean assertionStatus, boolean hasDefaultMethods, boolean declaresDefaultMethods,
                    boolean isSealed, boolean isVMInternal, boolean isLambdaFormHidden, boolean isLinked, boolean isProxyClass) {
        return NumUtil.safeToUShort(makeFlag(IS_PRIMITIVE_FLAG_BIT, isPrimitive) |
                        makeFlag(IS_INTERFACE_FLAG_BIT, isInterface) |
                        makeFlag(IS_HIDDEN_FLAG_BIT, isHidden) |
                        makeFlag(IS_RECORD_FLAG_BIT, isRecord) |
                        makeFlag(ASSERTION_STATUS_FLAG_BIT, assertionStatus) |
                        makeFlag(HAS_DEFAULT_METHODS_FLAG_BIT, hasDefaultMethods) |
                        makeFlag(DECLARES_DEFAULT_METHODS_FLAG_BIT, declaresDefaultMethods) |
                        makeFlag(IS_SEALED_FLAG_BIT, isSealed) |
                        makeFlag(IS_VM_INTERNAL_FLAG_BIT, isVMInternal) |
                        makeFlag(IS_LAMBDA_FORM_HIDDEN_BIT, isLambdaFormHidden) |
                        makeFlag(IS_LINKED_BIT, isLinked) |
                        makeFlag(IS_PROXY_CLASS_BIT, isProxyClass));
    }

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
        assert classInitializationInfo != null;
        this.classInitializationInfo = classInitializationInfo;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setSharedData(int layoutEncoding, int monitorOffset, int identityHashOffset, long referenceMapIndex,
                    boolean isInstantiated) {
        VMError.guarantee(monitorOffset == (char) monitorOffset, "Class %s has an invalid monitor field offset. Most likely, its objects are larger than supported.", name);
        VMError.guarantee(identityHashOffset == (char) identityHashOffset, "Class %s has an invalid identity hash code field offset. Most likely, its objects are larger than supported.", name);

        this.layoutEncoding = layoutEncoding;
        this.monitorOffset = (char) monitorOffset;
        this.identityHashOffset = (char) identityHashOffset;

        if ((int) referenceMapIndex != referenceMapIndex) {
            throw VMError.shouldNotReachHere("Reference map index not within integer range, need to switch field from int to long");
        }
        this.referenceMapIndex = (int) referenceMapIndex;
        this.additionalFlags = NumUtil.safeToUByte(makeFlag(IS_INSTANTIATED_BIT, isInstantiated));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setClosedTypeWorldData(CFunctionPointer[] vtable, int typeID, short typeCheckStart, short typeCheckRange, short typeCheckSlot, short[] typeCheckSlots) {
        assert this.vtable == null : "Initialization must be called only once";

        this.typeID = typeID;
        this.typeCheckStart = typeCheckStart;
        this.typeCheckRange = typeCheckRange;
        this.typeCheckSlot = typeCheckSlot;
        this.closedTypeWorldTypeCheckSlots = typeCheckSlots;
        this.vtable = vtable;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setOpenTypeWorldData(CFunctionPointer[] vtable, int typeID,
                    int typeCheckDepth, int numClassTypes, int numInterfaceTypes, int[] typeCheckSlots) {
        assert this.vtable == null : "Initialization must be called only once";

        this.typeID = typeID;
        this.typeIDDepth = typeCheckDepth;
        this.numClassTypes = numClassTypes;
        this.numInterfaceTypes = numInterfaceTypes;
        this.openTypeWorldTypeCheckSlots = typeCheckSlots;
        this.vtable = vtable;
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
    public String getSignature() {
        return signature;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setHubMetadata(int enclosingMethodInfoIndex, int annotationsIndex, int typeAnnotationsIndex, int classesEncodingIndex, int permittedSubclassesEncodingIndex,
                    int nestMembersEncodingIndex, int signersEncodingIndex) {
        this.hubMetadata = new DynamicHubMetadata(enclosingMethodInfoIndex, annotationsIndex, typeAnnotationsIndex, classesEncodingIndex, permittedSubclassesEncodingIndex, nestMembersEncodingIndex,
                        signersEncodingIndex);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setReflectionMetadata(int fieldsEncodingIndex, int methodsEncodingIndex, int constructorsEncodingIndex, int recordComponentsEncodingIndex, int classFlags) {
        this.reflectionMetadata = new ReflectionMetadata(fieldsEncodingIndex, methodsEncodingIndex, constructorsEncodingIndex, recordComponentsEncodingIndex, classFlags);
    }

    private void checkClassFlag(int mask, String methodName) {
        if (throwMissingRegistrationErrors() && !(isClassFlagSet(mask) && getConditions().satisfied())) {
            MissingReflectionRegistrationUtils.forBulkQuery(DynamicHub.toClass(this), methodName);
        }
    }

    private boolean isClassFlagSet(int mask) {
        return (reflectionMetadata != null && (reflectionMetadata.classFlags & mask) != 0);
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

    public int getTypeIDDepth() {
        return typeIDDepth;
    }

    public int getNumClassTypes() {
        return numClassTypes;
    }

    public int getNumInterfaceTypes() {
        return numInterfaceTypes;
    }

    public int[] getOpenTypeWorldTypeCheckSlots() {
        return openTypeWorldTypeCheckSlots;
    }

    public int getMonitorOffset() {
        return monitorOffset;
    }

    /**
     * If possible, use {@link LayoutEncoding#getIdentityHashOffset(Object)} instead. If the hash
     * code field is optional, note that this method may return an offset that is outside the bounds
     * of a newly allocated object.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getIdentityHashOffset() {
        ObjectLayout ol = ConfigurationValues.getObjectLayout();
        if (ol.isIdentityHashFieldInObjectHeader()) { // enable elimination of our field
            return ol.getObjectHeaderIdentityHashOffset();
        }

        int result = identityHashOffset;
        if (GraalDirectives.inIntrinsic()) {
            ReplacementsUtil.dynamicAssert(result > 0, "must have an identity hash field");
        } else {
            assert result > 0 : "must have an identity hash field";
        }
        return result;
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getReferenceMapIndex() {
        return referenceMapIndex;
    }

    /**
     * The identifier of the {@linkplain com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport
     * layer} that introduces this type which is an index into the array returned by
     * {@link com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton#getAllLayers}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getLayerId() {
        return layerId;
    }

    public boolean isInstantiated() {
        return isFlagSet(additionalFlags, IS_INSTANTIATED_BIT);
    }

    public boolean canUnsafeInstantiateAsInstanceFastPath() {
        return companion.canUnsafeAllocate();
    }

    public boolean canUnsafeInstantiateAsInstanceSlowPath() {
        if (ClassForNameSupport.canUnsafeInstantiateAsInstance(this)) {
            companion.setUnsafeAllocate();
            return true;
        } else {
            return false;
        }
    }

    public boolean isProxyClass() {
        return isFlagSet(flags, IS_PROXY_CLASS_BIT);
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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
        return reflectionMetadata != null ? (reflectionMetadata.classFlags & CLASS_ACCESS_FLAGS_MASK) : modifiers;
    }

    @Substitute
    private DynamicHub getComponentType() {
        return componentType;
    }

    @Substitute
    private DynamicHub getSuperclass() {
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
        return toClass(getSuperclass()) == java.lang.Enum.class;
    }

    @KeepOriginal
    private native Object[] getEnumConstants();

    @Substitute
    public Object[] getEnumConstantsShared() {
        if (enumConstantsReference instanceof LazyFinalReference) {
            return (Object[]) ((LazyFinalReference<?>) enumConstantsReference).get();
        }
        return (Object[]) enumConstantsReference;
    }

    @KeepOriginal
    public native URL getResource(String resourceName);

    @Substitute
    public InputStream getResourceAsStream(String resourceName) {
        String resolvedName = resolveName(resourceName);
        return Resources.singleton().createInputStream(module, resolvedName);
    }

    @KeepOriginal
    private native String resolveName(String resourceName);

    @KeepOriginal
    private native boolean isOpenToCaller(String resourceName, Class<?> caller);

    @Substitute
    public ClassLoader getClassLoader() {
        return companion.getClassLoader();
    }

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

    @KeepOriginal
    @TargetElement(onlyWith = JDK21OrEarlier.class)
    private native boolean isUnnamedClass();

    @Substitute
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isHidden() {
        return isFlagSet(flags, IS_HIDDEN_FLAG_BIT);
    }

    @Substitute
    public boolean isRecord() {
        return isFlagSet(flags, IS_RECORD_FLAG_BIT);
    }

    @Substitute
    public boolean isSealed() {
        return isFlagSet(flags, IS_SEALED_FLAG_BIT);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isVMInternal() {
        return isFlagSet(flags, IS_VM_INTERNAL_FLAG_BIT);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isLambdaFormHidden() {
        return isFlagSet(flags, IS_LAMBDA_FORM_HIDDEN_BIT);
    }

    public boolean isLinked() {
        return isFlagSet(flags, IS_LINKED_BIT);
    }

    public boolean isRegisteredForSerialization() {
        return ImageSingletons.lookup(SerializationRegistry.class).isRegisteredForSerialization(DynamicHub.toClass(this));
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
            throw VMError.shouldNotReachHereUnexpectedInput(declaringClass); // ExcludeFromJacocoGeneratedReport
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
            DynamicHub superClass = this.getSuperclass();
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

    @Substitute
    private Field[] getFields() {
        checkClassFlag(ALL_FIELDS_FLAG, "getFields");
        return copyFields(privateGetPublicFields());
    }

    private RuntimeConditionSet getConditions() {
        return ClassForNameSupport.getConditionFor(DynamicHub.toClass(this));
    }

    @Substitute
    @CallerSensitive
    public Method[] getMethods() throws SecurityException {
        checkClassFlag(ALL_METHODS_FLAG, "getMethods");
        return copyMethods(privateGetPublicMethods());
    }

    @Substitute
    private Constructor<?>[] getConstructors() {
        checkClassFlag(ALL_CONSTRUCTORS_FLAG, "getConstructors");
        return copyConstructors(privateGetDeclaredConstructors(true));
    }

    @Substitute
    public Field getField(String fieldName) throws NoSuchFieldException, SecurityException {
        Objects.requireNonNull(fieldName);
        Field field = getField0(fieldName);
        checkField(fieldName, field, true);
        return getReflectionFactory().copyField(field);
    }

    private void checkField(String fieldName, Field field, boolean publicOnly) throws NoSuchFieldException {
        boolean throwMissingErrors = throwMissingRegistrationErrors();
        Class<?> clazz = DynamicHub.toClass(this);
        if (field == null) {
            if (throwMissingErrors && !allElementsRegistered(publicOnly, ALL_DECLARED_FIELDS_FLAG, ALL_FIELDS_FLAG)) {
                MissingReflectionRegistrationUtils.forField(clazz, fieldName);
            }
            /*
             * If getDeclaredFields (or getFields for a public field) is registered, we know for
             * sure that the field does indeed not exist if we don't find it.
             */
            throw new NoSuchFieldException(fieldName);
        } else {
            RuntimeMetadataDecoder decoder = ImageSingletons.lookup(RuntimeMetadataDecoder.class);
            int fieldModifiers = field.getModifiers();
            boolean negative = decoder.isNegative(fieldModifiers);
            boolean hiding = decoder.isHiding(fieldModifiers);
            if (throwMissingErrors && hiding) {
                MissingReflectionRegistrationUtils.forField(clazz, fieldName);
            }
            if (negative || hiding) {
                throw new NoSuchFieldException(fieldName);
            }
        }
    }

    @Substitute
    private Method getMethod(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Objects.requireNonNull(methodName);
        Method method = getMethod0(methodName, parameterTypes);
        checkMethod(methodName, parameterTypes, method, true);
        return getReflectionFactory().copyMethod(method);
    }

    private void checkMethod(String methodName, Class<?>[] parameterTypes, Method method, boolean publicOnly) throws NoSuchMethodException {
        if (!checkMethodExists(methodName, parameterTypes, method, publicOnly)) {
            throw new NoSuchMethodException(methodToString(methodName, parameterTypes));
        }
    }

    private boolean checkMethodExists(String methodName, Class<?>[] parameterTypes, Method method, boolean publicOnly) {
        if (CONSTRUCTOR_NAME.equals(methodName)) {
            return false;
        }
        return checkExecutableExists(methodName, parameterTypes, method, publicOnly);
    }

    private void checkConstructor(Class<?>[] parameterTypes, Constructor<?> constructor, boolean publicOnly) throws NoSuchMethodException {
        if (!checkExecutableExists(CONSTRUCTOR_NAME, parameterTypes, constructor, publicOnly)) {
            throw new NoSuchMethodException(methodToString(CONSTRUCTOR_NAME, parameterTypes));
        }
    }

    /**
     * Checks if the method exists and reports any missing reflection registration errors.
     *
     * @return true if the method exists and is visible, false if missing (NoSuchMethodException).
     */
    private boolean checkExecutableExists(String methodName, Class<?>[] parameterTypes, Executable method, boolean publicOnly) {
        boolean throwMissingErrors = throwMissingRegistrationErrors();
        Class<?> clazz = DynamicHub.toClass(this);
        if (method == null) {
            boolean isConstructor = methodName.equals(CONSTRUCTOR_NAME);
            int allDeclaredFlag = isConstructor ? ALL_DECLARED_CONSTRUCTORS_FLAG : ALL_DECLARED_METHODS_FLAG;
            int allPublicFlag = isConstructor ? ALL_CONSTRUCTORS_FLAG : ALL_METHODS_FLAG;
            if (throwMissingErrors && !allElementsRegistered(publicOnly, allDeclaredFlag, allPublicFlag) &&
                            !(isConstructor && isInterface())) {
                MissingReflectionRegistrationUtils.forMethod(clazz, methodName, parameterTypes);
            }
            /*
             * If getDeclaredMethods (or getMethods for a public method) is registered, we know for
             * sure that the method does indeed not exist if we don't find it. This is also the case
             * when querying an interface constructor.
             */
            return false;
        } else {
            RuntimeMetadataDecoder decoder = ImageSingletons.lookup(RuntimeMetadataDecoder.class);
            int methodModifiers = method.getModifiers();
            boolean negative = decoder.isNegative(methodModifiers);
            boolean hiding = decoder.isHiding(methodModifiers);
            if (throwMissingErrors && hiding) {
                MissingReflectionRegistrationUtils.forMethod(clazz, methodName, parameterTypes);
            }
            return !(negative || hiding);
        }
    }

    private boolean allElementsRegistered(boolean publicOnly, int allDeclaredElementsFlag, int allPublicElementsFlag) {
        return isClassFlagSet(allDeclaredElementsFlag) || (publicOnly && isClassFlagSet(allPublicElementsFlag));
    }

    @KeepOriginal
    private native Constructor<?> getConstructor(Class<?>... parameterTypes);

    @Substitute
    public Class<?>[] getDeclaredClasses() throws SecurityException {
        checkClassFlag(ALL_DECLARED_CLASSES_FLAG, "getDeclaredClasses");
        return getDeclaredClasses0();
    }

    @Substitute
    @SuppressWarnings("deprecation")
    public Class<?>[] getClasses() {
        checkClassFlag(ALL_CLASSES_FLAG, "getClasses");

        // Privileged so this implementation can look at DECLARED classes,
        // something the caller might not have privilege to do. The code here
        // is allowed to look at DECLARED classes because (1) it does not hand
        // out anything other than public members and (2) public member access
        // has already been ok'd by the SecurityManager.

        return java.security.AccessController.doPrivileged(
                        (PrivilegedAction<Class<?>[]>) () -> {
                            List<Class<?>> list = new ArrayList<>();
                            DynamicHub currentClass = DynamicHub.this;
                            while (currentClass != null) {
                                for (Class<?> m : currentClass.getDeclaredClasses0()) {
                                    if (Modifier.isPublic(m.getModifiers())) {
                                        list.add(m);
                                    }
                                }
                                currentClass = currentClass.getSuperHub();
                            }
                            return list.toArray(new Class<?>[0]);
                        });
    }

    @Substitute
    private Field[] getDeclaredFields() {
        checkClassFlag(ALL_DECLARED_FIELDS_FLAG, "getDeclaredFields");
        return copyFields(privateGetDeclaredFields(false));
    }

    @Substitute
    @CallerSensitive
    public Method[] getDeclaredMethods() throws SecurityException {
        checkClassFlag(ALL_DECLARED_METHODS_FLAG, "getDeclaredMethods");
        return copyMethods(privateGetDeclaredMethods(false));
    }

    @Substitute
    private Constructor<?>[] getDeclaredConstructors() {
        checkClassFlag(ALL_DECLARED_CONSTRUCTORS_FLAG, "getDeclaredConstructors");
        return copyConstructors(privateGetDeclaredConstructors(false));
    }

    /**
     * @see #filterFields(Field...)
     */
    @Substitute
    public Field getDeclaredField(String fieldName) throws NoSuchFieldException, SecurityException {
        Objects.requireNonNull(fieldName);
        Field field = searchFields(privateGetDeclaredFields(false), fieldName);
        checkField(fieldName, field, false);
        return getReflectionFactory().copyField(field);
    }

    @Substitute
    @CallerSensitive
    public Method getDeclaredMethod(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException, SecurityException {
        Objects.requireNonNull(methodName);
        Method method = searchMethods(privateGetDeclaredMethods(false), methodName, parameterTypes);
        checkMethod(methodName, parameterTypes, method, false);
        return getReflectionFactory().copyMethod(method);
    }

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
    private native RecordComponent[] getRecordComponents();

    @Substitute
    private RecordComponent[] getRecordComponents0() {
        checkClassFlag(ALL_RECORD_COMPONENTS_FLAG, "getRecordComponents");
        if (reflectionMetadata == null || reflectionMetadata.recordComponentsEncodingIndex == NO_DATA) {
            /* See ReflectionDataBuilder.buildRecordComponents() for details. */
            throw VMError.unsupportedFeature("Record components not available for record class " + getTypeName() + ". " +
                            "All record component accessor methods of this record class must be included in the reflection configuration at image build time, then this method can be called.");
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseRecordComponents(this, reflectionMetadata.recordComponentsEncodingIndex);
    }

    @KeepOriginal
    private native Class<?>[] getPermittedSubclasses();

    @TargetElement(onlyWith = JDK21OrEarlier.class)
    @Substitute
    @SuppressWarnings("unused")
    private void checkMemberAccess(SecurityManager sm, int which, Class<?> caller, boolean checkProxyInterfaces) {
        /* No runtime access checks. */
    }

    @TargetElement(onlyWith = JDK21OrEarlier.class)
    @Substitute
    @SuppressWarnings({"deprecation", "unused"})
    private void checkPackageAccess(SecurityManager sm, ClassLoader ccl, boolean checkProxyInterfaces) {
        /* No runtime access checks. */
    }

    /**
     * Never called as it is partially evaluated away due to SecurityManager.
     */
    @TargetElement(onlyWith = JDK21OrEarlier.class)
    @KeepOriginal
    @SuppressWarnings({"deprecation", "unused"})
    private static native void checkPackageAccessForPermittedSubclasses(@SuppressWarnings("removal") SecurityManager sm,
                    ClassLoader ccl, Class<?>[] subClasses);

    @Substitute
    private static ReflectionFactory getReflectionFactory() {
        return Target_jdk_internal_reflect_ReflectionFactory.getReflectionFactory();
    }

    @KeepOriginal
    private static native Field searchFields(Field[] fields, String name);

    @KeepOriginal
    private static native Method searchMethods(Method[] allMethods, String name, Class<?>[] parameterTypes);

    @Substitute
    private Constructor<?> getConstructor0(Class<?>[] parameterTypes, int which) throws NoSuchMethodException {
        ReflectionFactory fact = getReflectionFactory();
        Constructor<?>[] constructors = privateGetDeclaredConstructors((which == Member.PUBLIC));
        Constructor<?> candidate = null;
        for (Constructor<?> constructor : constructors) {
            if (arrayContentsEq(parameterTypes,
                            fact.getExecutableSharedParameterTypes(constructor))) {
                candidate = constructor;
            }
        }
        checkConstructor(parameterTypes, candidate, which == Member.PUBLIC);
        return candidate;
    }

    @KeepOriginal
    private static native boolean arrayContentsEq(Object[] a1, Object[] a2);

    /**
     * @see #filterFields(Field...)
     */
    @Substitute
    private static Field[] copyFields(Field[] original) {
        Field[] arg = filterFields(original);
        Field[] out = new Field[arg.length];
        ReflectionFactory fact = getReflectionFactory();
        for (int i = 0; i < arg.length; i++) {
            out[i] = fact.copyField(arg[i]);
        }
        return out;
    }

    /**
     * @see #filterMethods(Method...)
     */
    @Substitute
    private static Method[] copyMethods(Method[] original) {
        Method[] arg = filterMethods(original);
        Method[] out = new Method[arg.length];
        ReflectionFactory fact = getReflectionFactory();
        for (int i = 0; i < arg.length; i++) {
            out[i] = fact.copyMethod(arg[i]);
        }
        return out;
    }

    /**
     * @see #filterConstructors(Constructor[])
     */
    @Substitute
    private static Constructor<?>[] copyConstructors(Constructor<?>[] original) {
        Constructor<?>[] arg = filterConstructors(original);
        Constructor<?>[] out = new Constructor<?>[arg.length];
        ReflectionFactory fact = getReflectionFactory();
        for (int i = 0; i < arg.length; i++) {
            out[i] = fact.copyConstructor(arg[i]);
        }
        return out;
    }

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
    @NeverInlineTrivial("Used in metadata requiring call usage analysis: AnalyzeMethodsRequiringMetadataUsagePhase")
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    @CallerSensitive
    private static Class<?> forName(String className) throws Throwable {
        return forName(className, Reflection.getCallerClass());
    }

    @Substitute
    @NeverInlineTrivial("Used in metadata requiring call usage analysis: AnalyzeMethodsRequiringMetadataUsagePhase")
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    @CallerSensitiveAdapter
    private static Class<?> forName(String className, Class<?> caller) throws Throwable {
        return forName(className, true, caller.getClassLoader(), caller);
    }

    @Substitute
    @NeverInlineTrivial("Used in metadata requiring call usage analysis: AnalyzeMethodsRequiringMetadataUsagePhase")
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    @CallerSensitive
    private static Class<?> forName(Module module, String className) throws Throwable {
        return forName(module, className, Reflection.getCallerClass());
    }

    @Substitute
    @NeverInlineTrivial("Used in metadata requiring call usage analysis: AnalyzeMethodsRequiringMetadataUsagePhase")
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    @CallerSensitiveAdapter
    @TargetElement(onlyWith = JDK21OrEarlier.class)
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
    @NeverInlineTrivial("Used in metadata requiring call usage analysis: AnalyzeMethodsRequiringMetadataUsagePhase")
    @CallerSensitive
    private static Class<?> forName(String name, boolean initialize, ClassLoader loader) throws Throwable {
        return forName(name, initialize, loader, Reflection.getCallerClass());
    }

    @Substitute
    @NeverInlineTrivial("Used in metadata requiring call usage analysis: AnalyzeMethodsRequiringMetadataUsagePhase")
    @CallerSensitiveAdapter
    @TargetElement(onlyWith = JDK21OrEarlier.class)
    private static Class<?> forName(String name, boolean initialize, ClassLoader loader, @SuppressWarnings("unused") Class<?> caller) throws Throwable {
        if (name == null) {
            throw new NullPointerException();
        }
        Class<?> result;
        try {
            result = ClassForNameSupport.forName(name, loader);
        } catch (ClassNotFoundException e) {
            if (loader != null && PredefinedClassesSupport.hasBytecodeClasses()) {
                result = loader.loadClass(name); // may throw
            } else {
                throw e;
            }
        }
        if (initialize) {
            DynamicHub.fromClass(result).ensureInitialized();
        }
        return result;
    }

    @KeepOriginal
    @TargetElement(onlyWith = JDKLatest.class)
    public static native Class<?> forPrimitiveName(String primitiveName);

    @KeepOriginal
    private native Package getPackage();

    @Substitute //
    public String getPackageName() {
        if (SubstrateUtil.HOSTED) { // avoid eager initialization in image heap
            return computePackageName();
        }
        return companion.getPackageName(this);
    }

    private boolean isHybrid() {
        if (SubstrateUtil.HOSTED) {
            return AnnotationAccess.isAnnotationPresent(hostedJavaClass, Hybrid.class);
        } else {
            return LayoutEncoding.isHybrid(getLayoutEncoding());
        }
    }

    String computePackageName() {
        String pn = null;
        DynamicHub me = this;
        if (!isHybrid()) {
            while (me.hubIsArray()) {
                me = me.getComponentType();
            }
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
    @TargetElement(onlyWith = JDKLatest.class)
    private native void addSealingInfo(int modifiersParam, StringBuilder sb);

    @KeepOriginal
    @TargetElement(onlyWith = JDKLatest.class)
    private native boolean hasSealedAncestor(Class<?> clazz);

    @KeepOriginal
    public native boolean isSynthetic();

    @Substitute
    public Object[] getSigners() {
        if (isPrimitive()) {
            return null;
        }
        checkClassFlag(ALL_SIGNERS_FLAG, "getSigners");
        if (hubMetadata == null || hubMetadata.signersEncodingIndex == NO_DATA) {
            return null;
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseObjects(hubMetadata.signersEncodingIndex, this);
    }

    @Substitute
    public ProtectionDomain getProtectionDomain() {
        return companion.getProtectionDomain();
    }

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
        return describeMethod(getName() + "." + nameArg + "(", argTypes, ")");
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
     * Used by {@link java.util.ServiceLoader} to find any {@code public static provider()} method.
     *
     * @see #filterMethods(Method...)
     */
    @Substitute //
    @SuppressWarnings({"unused"})
    List<Method> getDeclaredPublicMethods(String methodName, Class<?>... parameterTypes) {
        Method[] methods = privateGetDeclaredMethods(/* publicOnly */ true);
        ReflectionFactory factory = getReflectionFactory();
        List<Method> result = new ArrayList<>();
        boolean matchedAnyRegistered = false;
        for (Method method : methods) {
            if (method.getName().equals(methodName) && Arrays.equals(factory.getExecutableSharedParameterTypes(method), parameterTypes)) {
                matchedAnyRegistered = true;
                /*
                 * We've matched a registered method query, but we still need to check it's not a
                 * negative method or hiding method.
                 */
                if (checkMethodExists(methodName, parameterTypes, method, /* publicOnly */ true)) {
                    result.add(factory.copyMethod(method));
                }
            }
        }
        if (!matchedAnyRegistered) {
            /*
             * No matching method was registered. Report a missing registration error if no bulk
             * query for all (public or declared) methods was registered for reflection either.
             */
            checkMethodExists(methodName, parameterTypes, null, /* publicOnly */ true);
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

    @KeepOriginal
    public native Class<?>[] getNestMembers();

    @Substitute
    @Override
    public DynamicHub componentType() {
        return componentType;
    }

    @Substitute
    @Override
    public DynamicHub arrayType() {
        if (toClass(this) == void.class) {
            throw new UnsupportedOperationException(new IllegalArgumentException());
        }
        if (arrayHub == null) {
            MissingReflectionRegistrationUtils.forClass(getTypeName() + "[]");
        }
        return arrayHub;
    }

    @KeepOriginal
    private native Class<?> elementType();

    @KeepOriginal
    @Override
    public native String descriptorString();

    @KeepOriginal
    @Override
    public native Optional<? extends ConstantDesc> describeConstable();

    @KeepOriginal
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
        Object[] enclosingMethod = ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseEnclosingMethod(hubMetadata.enclosingMethodInfoIndex, this);
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
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseByteArray(hubMetadata.annotationsIndex, this);
    }

    @Substitute
    byte[] getRawTypeAnnotations() {
        if (hubMetadata == null || hubMetadata.typeAnnotationsIndex == NO_DATA) {
            return null;
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseByteArray(hubMetadata.typeAnnotationsIndex, this);
    }

    @Substitute
    Target_jdk_internal_reflect_ConstantPool getConstantPool() {
        return null;
    }

    @Substitute
    private Field[] getDeclaredFields0(boolean publicOnly) {
        if (reflectionMetadata == null || reflectionMetadata.fieldsEncodingIndex == NO_DATA) {
            return new Field[0];
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseFields(this, reflectionMetadata.fieldsEncodingIndex, publicOnly);
    }

    @Substitute
    private Method[] getDeclaredMethods0(boolean publicOnly) {
        if (reflectionMetadata == null || reflectionMetadata.methodsEncodingIndex == NO_DATA) {
            return new Method[0];
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseMethods(this, reflectionMetadata.methodsEncodingIndex, publicOnly);
    }

    @Substitute
    private Constructor<?>[] getDeclaredConstructors0(boolean publicOnly) {
        if (reflectionMetadata == null || reflectionMetadata.constructorsEncodingIndex == NO_DATA) {
            return new Constructor<?>[0];
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseConstructors(this, reflectionMetadata.constructorsEncodingIndex, publicOnly);
    }

    @Substitute
    private Class<?>[] getDeclaredClasses0() {
        if (hubMetadata == null || hubMetadata.classesEncodingIndex == NO_DATA) {
            return new Class<?>[0];
        }
        Class<?>[] declaredClasses = ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseClasses(hubMetadata.classesEncodingIndex, this);
        for (Class<?> clazz : declaredClasses) {
            PredefinedClassesSupport.throwIfUnresolvable(clazz, getClassLoader0());
        }
        return declaredClasses;
    }

    @Delete
    private static native boolean desiredAssertionStatus0(Class<?> clazz);

    @Delete
    private native Class<?> getNestHost0();

    @Substitute
    private Class<?>[] getNestMembers0() {
        checkClassFlag(ALL_NEST_MEMBERS_FLAG, "getNestMembers");
        if (hubMetadata == null || hubMetadata.nestMembersEncodingIndex == NO_DATA) {
            return new Class<?>[]{DynamicHub.toClass(this)};
        }
        Class<?>[] nestMembers = ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseClasses(hubMetadata.nestMembersEncodingIndex, this);
        for (Class<?> clazz : nestMembers) {
            PredefinedClassesSupport.throwIfUnresolvable(clazz, getClassLoader0());
        }
        return nestMembers;
    }

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
    private Class<?>[] getPermittedSubclasses0() {
        if (!isSealed()) {
            return null;
        }
        checkClassFlag(ALL_PERMITTED_SUBCLASSES_FLAG, "getPermittedSubclasses");
        if (hubMetadata == null || hubMetadata.permittedSubclassesEncodingIndex == NO_DATA) {
            return new Class<?>[0];
        }
        Class<?>[] permittedSubclasses = ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseClasses(hubMetadata.permittedSubclassesEncodingIndex, this);
        for (Class<?> clazz : permittedSubclasses) {
            PredefinedClassesSupport.throwIfUnresolvable(clazz, getClassLoader0());
        }
        return permittedSubclasses;
    }

    @KeepOriginal
    private native GenericsFactory getFactory();

    @KeepOriginal
    @TargetElement(onlyWith = JDKLatest.class)
    native Method findMethod(boolean publicOnly, String nameParam, Class<?>... parameterTypes);

    @KeepOriginal
    private native Method getMethod0(String methodName, Class<?>[] parameterTypes);

    @KeepOriginal
    private static native void addAll(Collection<Field> c, Field[] o);

    @KeepOriginal
    @TargetElement(onlyWith = JDK21OrEarlier.class)
    private native Target_java_lang_PublicMethods_MethodList getMethodsRecursive(String methodName, Class<?>[] parameterTypes, boolean includeStatic);

    @KeepOriginal
    @TargetElement(onlyWith = JDKLatest.class)
    private native Target_java_lang_PublicMethods_MethodList getMethodsRecursive(String methodName, Class<?>[] parameterTypes, boolean includeStatic, boolean publicOnly);

    @KeepOriginal
    private native Field getField0(String fieldName);

    @KeepOriginal
    native AnnotationType getAnnotationType();

    @KeepOriginal
    static native byte[] getExecutableTypeAnnotationBytes(Executable ex);

    @KeepOriginal
    private native boolean isDirectSubType(Class<?> c);

    @KeepOriginal
    native boolean casAnnotationType(AnnotationType oldType, AnnotationType newType);

    /*
     * We need to filter out hiding and negative elements at the last moment. This ensures that the
     * JDK internals see them as regular methods and fields and ensure their visibility is correct,
     * but they should not be returned to application code.
     */
    private static Field[] filterFields(Field... fields) {
        List<Field> filtered = new ArrayList<>();
        RuntimeMetadataDecoder decoder = ImageSingletons.lookup(RuntimeMetadataDecoder.class);
        for (Field field : fields) {
            int modifiers = field.getModifiers();
            if (!decoder.isHiding(modifiers) && !decoder.isNegative(modifiers)) {
                filtered.add(field);
            }
        }
        return filtered.toArray(new Field[0]);
    }

    private static Method[] filterMethods(Method... methods) {
        List<Method> filtered = new ArrayList<>();
        RuntimeMetadataDecoder decoder = ImageSingletons.lookup(RuntimeMetadataDecoder.class);
        for (Method method : methods) {
            int modifiers = method.getModifiers();
            if (!decoder.isHiding(modifiers) && !decoder.isNegative(modifiers)) {
                filtered.add(method);
            }
        }
        return filtered.toArray(new Method[0]);
    }

    private static Constructor<?>[] filterConstructors(Constructor<?>... constructors) {
        List<Constructor<?>> filtered = new ArrayList<>();
        RuntimeMetadataDecoder decoder = ImageSingletons.lookup(RuntimeMetadataDecoder.class);
        for (Constructor<?> constructor : constructors) {
            if (!decoder.isNegative(constructor.getModifiers())) {
                filtered.add(constructor);
            }
        }
        return filtered.toArray(new Constructor<?>[0]);
    }

    public void setJrfEventConfiguration(Object configuration) {
        companion.setJfrEventConfiguration(configuration);
    }

    public Object getJfrEventConfiguration() {
        return companion.getJfrEventConfiguration();
    }

    public boolean isReached() {
        return classInitializationInfo.isTypeReached(this);
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

    private static final class DynamicHubMetadata {
        @UnknownPrimitiveField(availability = CompileQueueFinished.class) //
        final int enclosingMethodInfoIndex;

        @UnknownPrimitiveField(availability = CompileQueueFinished.class)//
        final int annotationsIndex;

        @UnknownPrimitiveField(availability = CompileQueueFinished.class)//
        final int typeAnnotationsIndex;

        @UnknownPrimitiveField(availability = CompileQueueFinished.class)//
        final int classesEncodingIndex;

        @UnknownPrimitiveField(availability = CompileQueueFinished.class)//
        final int permittedSubclassesEncodingIndex;

        @UnknownPrimitiveField(availability = CompileQueueFinished.class)//
        final int nestMembersEncodingIndex;

        @UnknownPrimitiveField(availability = CompileQueueFinished.class)//
        final int signersEncodingIndex;

        private DynamicHubMetadata(int enclosingMethodInfoIndex, int annotationsIndex, int typeAnnotationsIndex, int classesEncodingIndex, int permittedSubclassesEncodingIndex,
                        int nestMembersEncodingIndex, int signersEncodingIndex) {
            this.enclosingMethodInfoIndex = enclosingMethodInfoIndex;
            this.annotationsIndex = annotationsIndex;
            this.typeAnnotationsIndex = typeAnnotationsIndex;
            this.classesEncodingIndex = classesEncodingIndex;
            this.permittedSubclassesEncodingIndex = permittedSubclassesEncodingIndex;
            this.nestMembersEncodingIndex = nestMembersEncodingIndex;
            this.signersEncodingIndex = signersEncodingIndex;
        }
    }

    private static final class ReflectionMetadata {
        @UnknownPrimitiveField(availability = CompileQueueFinished.class)//
        final int fieldsEncodingIndex;

        @UnknownPrimitiveField(availability = CompileQueueFinished.class)//
        final int methodsEncodingIndex;

        @UnknownPrimitiveField(availability = CompileQueueFinished.class)//
        final int constructorsEncodingIndex;

        @UnknownPrimitiveField(availability = CompileQueueFinished.class)//
        final int recordComponentsEncodingIndex;

        @UnknownPrimitiveField(availability = CompileQueueFinished.class)//
        final int classFlags;

        private ReflectionMetadata(int fieldsEncodingIndex, int methodsEncodingIndex, int constructorsEncodingIndex, int recordComponentsEncodingIndex, int classFlags) {
            this.fieldsEncodingIndex = fieldsEncodingIndex;
            this.methodsEncodingIndex = methodsEncodingIndex;
            this.constructorsEncodingIndex = constructorsEncodingIndex;
            this.recordComponentsEncodingIndex = recordComponentsEncodingIndex;
            this.classFlags = classFlags;
        }
    }

    public FieldDescriptor[] getReachableFields() {
        if (reflectionMetadata == null || reflectionMetadata.fieldsEncodingIndex == NO_DATA) {
            return new FieldDescriptor[0];
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseReachableFields(this, reflectionMetadata.fieldsEncodingIndex);
    }

    public MethodDescriptor[] getReachableMethods() {
        if (reflectionMetadata == null || reflectionMetadata.methodsEncodingIndex == NO_DATA) {
            return new MethodDescriptor[0];
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseReachableMethods(this, reflectionMetadata.methodsEncodingIndex);
    }

    public ConstructorDescriptor[] getReachableConstructors() {
        if (reflectionMetadata == null || reflectionMetadata.constructorsEncodingIndex == NO_DATA) {
            return new ConstructorDescriptor[0];
        }
        return ImageSingletons.lookup(RuntimeMetadataDecoder.class).parseReachableConstructors(this, reflectionMetadata.constructorsEncodingIndex);
    }
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

    @Alias //
    JavaLangReflectAccess langReflectAccess;

    /**
     * This substitution eliminates the SecurityManager check in the original method, which would
     * make some build-time verifications fail.
     */
    @Substitute
    public static ReflectionFactory getReflectionFactory() {
        return soleInstance;
    }

    /**
     * Do not use the field handle based field accessor our own {@link UnsafeFieldAccessorFactory}.
     * It takes effect when {@code Target_java_lang_reflect_Field#fieldAccessor} is recomputed at
     * runtime. See also GR-39586 and GR-46732.
     */
    @Substitute
    public FieldAccessor newFieldAccessor(Field field0, boolean override) {
        Field field = field0;
        Field root = langReflectAccess.getRoot(field);
        if (root != null) {
            // FieldAccessor will use the root unless the modifiers have
            // been overridden
            if (root.getModifiers() == field.getModifiers() || !override) {
                field = root;
            }
        }
        boolean isFinal = Modifier.isFinal(field.getModifiers());
        boolean isReadOnly = isFinal && (!override || langReflectAccess.isTrustedFinalField(field));
        return UnsafeFieldAccessorFactory.newFieldAccessor(field, isReadOnly);
    }

    @Substitute
    @TargetElement(onlyWith = JDKLatest.class)
    private Constructor<?> generateConstructor(Class<?> cl, Constructor<?> constructorToCall) {
        SerializationRegistry serializationRegistry = ImageSingletons.lookup(SerializationRegistry.class);
        ConstructorAccessor acc = (ConstructorAccessor) serializationRegistry.getSerializationConstructorAccessor(cl, constructorToCall.getDeclaringClass());
        /*
         * Unlike other root constructors, this constructor is not copied for mutation but directly
         * mutated, as it is not cached. To cache this constructor, setAccessible call must be done
         * on a copy and return that copy instead.
         */
        Constructor<?> ctor = Helper_jdk_internal_reflect_ReflectionFactory.newConstructorWithAccessor(this, constructorToCall, acc);
        ctor.setAccessible(true);
        return ctor;
    }

}

/**
 * Reflectively access {@code JavaLangReflectAccess.newConstructorWithAccessor}. Once we drop JDK
 * 21, this can be replaced by a direct call to the method. (GR-55515)
 */
final class Helper_jdk_internal_reflect_ReflectionFactory {
    private static final Method NEW_CONSTRUCTOR_WITH_ACCESSOR = JavaVersionUtil.JAVA_SPEC > 21
                    ? ReflectionUtil.lookupMethod(JavaLangReflectAccess.class, "newConstructorWithAccessor", Constructor.class, ConstructorAccessor.class)
                    : null;

    static Constructor<?> newConstructorWithAccessor(Target_jdk_internal_reflect_ReflectionFactory reflectionFactory, Constructor<?> constructorToCall, ConstructorAccessor acc) {
        return ReflectionUtil.invokeMethod(NEW_CONSTRUCTOR_WITH_ACCESSOR, reflectionFactory.langReflectAccess, constructorToCall, acc);
    }
}

/**
 * Ensure that we are not accidentally using the method handle based constructor accessor.
 */
@Delete
@TargetClass(className = "jdk.internal.reflect.DirectConstructorHandleAccessor")
final class Target_jdk_internal_reflect_DirectConstructorHandleAccessor {
}

@TargetClass(className = "java.lang.Class", innerClass = "EnclosingMethodInfo")
final class Target_java_lang_Class_EnclosingMethodInfo {
}

@TargetClass(className = "java.lang.Class", innerClass = "AnnotationData")
final class Target_java_lang_Class_AnnotationData {
}

@TargetClass(className = "java.lang.PublicMethods", innerClass = "MethodList")
final class Target_java_lang_PublicMethods_MethodList {
}

@TargetClass(className = "java.lang.Class", innerClass = "Atomic")
final class Target_java_lang_Class_Atomic {
    @Delete static Unsafe unsafe;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, declClass = DynamicHubCompanion.class, name = "reflectionData") //
    private static long reflectionDataOffset;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, declClass = DynamicHubCompanion.class, name = "annotationType") //
    private static long annotationTypeOffset;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FieldOffset, declClass = DynamicHubCompanion.class, name = "annotationData") //
    private static long annotationDataOffset;

    @Substitute
    static <T> boolean casReflectionData(DynamicHub clazz,
                    SoftReference<Target_java_lang_Class_ReflectionData<T>> oldData,
                    SoftReference<Target_java_lang_Class_ReflectionData<T>> newData) {
        return Unsafe.getUnsafe().compareAndSetReference(clazz.getCompanion(), reflectionDataOffset, oldData, newData);
    }

    @Substitute
    static boolean casAnnotationType(DynamicHub clazz,
                    AnnotationType oldType,
                    AnnotationType newType) {
        return Unsafe.getUnsafe().compareAndSetReference(clazz.getCompanion(), annotationTypeOffset, oldType, newType);
    }

    @Substitute
    static boolean casAnnotationData(DynamicHub clazz,
                    Target_java_lang_Class_AnnotationData oldData,
                    Target_java_lang_Class_AnnotationData newData) {
        return Unsafe.getUnsafe().compareAndSetReference(clazz.getCompanion(), annotationDataOffset, oldData, newData);
    }
}
