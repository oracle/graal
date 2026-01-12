/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.ameta;

import java.lang.reflect.Array;
import java.util.Objects;
import java.util.Set;
import java.util.function.ObjIntConsumer;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.heap.HostedValuesProvider;
import com.oracle.graal.pointsto.heap.ImageHeapArray;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageHeapObjectArray;
import com.oracle.graal.pointsto.heap.ImageHeapPrimitiveArray;
import com.oracle.graal.pointsto.heap.ImageHeapRelocatableConstant;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.classinitialization.TypeReachedProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.classinitialization.SimulateClassInitializerSupport;
import com.oracle.svm.hosted.meta.PatchedWordConstant;
import com.oracle.svm.util.GraalAccess;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

@Platforms(Platform.HOSTED_ONLY.class)
public class AnalysisConstantReflectionProvider implements ConstantReflectionProvider, TypeReachedProvider {
    private final AnalysisUniverse universe;
    protected final UniverseMetaAccess metaAccess;
    private final AnalysisMethodHandleAccessProvider methodHandleAccess;
    private final ClassInitializationSupport classInitializationSupport;
    private SimulateClassInitializerSupport simulateClassInitializerSupport;
    private final FieldValueInterceptionSupport fieldValueInterceptionSupport = FieldValueInterceptionSupport.singleton();

    public AnalysisConstantReflectionProvider(AnalysisUniverse universe, UniverseMetaAccess metaAccess, ClassInitializationSupport classInitializationSupport) {
        this.universe = universe;
        this.metaAccess = metaAccess;
        this.methodHandleAccess = new AnalysisMethodHandleAccessProvider(universe);
        this.classInitializationSupport = classInitializationSupport;
    }

    @Override
    public Boolean constantEquals(Constant x, Constant y) {
        VMError.guarantee(!(x instanceof JavaConstant constant) || isExpectedJavaConstant(constant));
        VMError.guarantee(!(y instanceof JavaConstant constant) || isExpectedJavaConstant(constant));
        if (x == y) {
            return true;
        } else {
            return x.equals(y);
        }
    }

    @Override
    public int identityHashCode(JavaConstant constant) {
        JavaKind kind = Objects.requireNonNull(constant).getJavaKind();
        if (kind != JavaKind.Object) {
            throw new IllegalArgumentException("Constant has unexpected kind " + kind + ": " + constant);
        }
        if (constant.isNull()) {
            /* System.identityHashCode is specified to return 0 when passed null. */
            return 0;
        } else if (constant instanceof PatchedWordConstant) {
            /* Kind of a primitive constant, so it does not have an identity hash code. */
            throw new IllegalArgumentException("PatchedWordConstant has no identity hash code: " + constant);
        }

        ImageHeapConstant imageHeapConstant = (ImageHeapConstant) constant;
        if (imageHeapConstant.hasIdentityHashCode()) {
            /*
             * If the ImageHeapConstant already has a hash code, that value has precedence over the
             * hosted object.
             */
            return imageHeapConstant.getIdentityHashCode();
        }

        Object hostedObject = Objects.requireNonNull(universe.getSnippetReflection().asObject(Object.class, constant));
        if (hostedObject instanceof DynamicHub hub) {
            /*
             * We need to use the identity hash code of the original java.lang.Class object and not
             * of the DynamicHub, so that hash maps that are filled during image generation and use
             * Class keys still work at run time.
             */
            hostedObject = hub.getHostedJavaClass();
        }
        return System.identityHashCode(hostedObject);
    }

    @Override
    public int makeIdentityHashCode(JavaConstant constant, int requestedValue) {
        throw VMError.unimplemented("makeIdentityHashCode");
    }

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        return EmptyMemoryAccessProvider.SINGLETON;
    }

    private static final Set<Class<?>> BOXING_CLASSES = Set.of(Boolean.class, Byte.class, Short.class, Character.class, Integer.class, Long.class, Float.class, Double.class);

    @Override
    public JavaConstant unboxPrimitive(JavaConstant source) {
        if (!source.getJavaKind().isObject() || source.isNull()) {
            return null;
        }
        ImageHeapConstant imageHeapConstant = (ImageHeapConstant) source;
        /*
         * Unbox by reading the known single field "value", which is a primitive field of the
         * correct unboxed type.
         */
        AnalysisType type = imageHeapConstant.getType();
        if (BOXING_CLASSES.contains(type.getJavaClass())) {
            imageHeapConstant.ensureReaderInstalled();
            ResolvedJavaField[] fields = type.getInstanceFields(true);
            assert fields.length == 1 && fields[0].getName().equals("value");
            return ((ImageHeapInstance) imageHeapConstant).readFieldValue((AnalysisField) fields[0]);
        }
        /* Not a valid boxed primitive. */
        return null;
    }

    @Override
    public MethodHandleAccessProvider getMethodHandleAccess() {
        return methodHandleAccess;
    }

    @Override
    public Integer readArrayLength(JavaConstant array) {
        if (array.getJavaKind() != JavaKind.Object || array.isNull()) {
            return null;
        }
        VMError.guarantee(array instanceof ImageHeapConstant);
        if (array instanceof ImageHeapArray heapArray) {
            return heapArray.getLength();
        }
        return null;
    }

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        if (array.getJavaKind() != JavaKind.Object || array.isNull()) {
            return null;
        }
        VMError.guarantee(array instanceof ImageHeapConstant);
        if (array instanceof ImageHeapArray heapArray) {
            if (index < 0 || index >= heapArray.getLength()) {
                return null;
            }
            heapArray.ensureReaderInstalled();
            JavaConstant element = heapArray.readElementValue(index);
            return checkExpectedValue(element);
        }
        return null;
    }

    /**
     * Attempts to read a value from an array that is not a full single array element. For example,
     * the number of bytes to read ({@code accessBytes}) may differ from the array element size.
     *
     * @param accessBytes number of bytes to read from the array (1, 2, 4, or 8)
     * @param accessedDataOffset offset in bytes, from the start of the first array element (not
     *            from the beginning of the array object)
     * @return a {@link JavaConstant} containing the read value
     *
     * @throws IllegalArgumentException if the value could not be read
     */
    public JavaConstant readArrayUnaligned(ImageHeapArray array, int accessBytes, long accessedDataOffset, int runtimeIndexScale) {
        if (accessBytes < 1 || accessBytes > 8 || !CodeUtil.isPowerOf2(accessBytes)) {
            throw new IllegalArgumentException(String.valueOf(accessBytes));
        }

        if (array.getJavaKind() != JavaKind.Object) {
            throw new IllegalArgumentException("Base of kind " + array.getJavaKind() + " is not supported.");
        } else if (array.isNull()) {
            throw new IllegalArgumentException("Base is null.");
        }

        if (array instanceof ImageHeapPrimitiveArray heapArray) {
            /* Unaligned accesses are only allowed for primitive arrays. */
            MetaAccessProvider originalMetaAccess = GraalAccess.getOriginalProviders().getMetaAccess();
            JavaKind arrayKind = JavaKind.fromJavaClass(heapArray.getType().getComponentType().getJavaClass());
            long hostedBaseOffset = originalMetaAccess.getArrayBaseOffset(arrayKind);
            long hostedIndexScale = originalMetaAccess.getArrayIndexScale(arrayKind);
            assert hostedIndexScale == runtimeIndexScale : "element size must match for primitive arrays";

            /* Bounds check. */
            Object primitiveArray = heapArray.getArray();
            long arrayDataSize = Array.getLength(primitiveArray) * hostedIndexScale;
            if (accessedDataOffset < 0 || accessedDataOffset + accessBytes > arrayDataSize) {
                throw new IllegalArgumentException("Reading outside array bounds.");
            }

            /* Compute the accessed offset relative to the start of the array. */
            long accessedHostedOffset = accessedDataOffset + hostedBaseOffset;

            /* Read from the array and convert the value to a constant. */
            Object value = readFromPrimitiveArray(accessBytes, primitiveArray, accessedHostedOffset);
            JavaConstant result = JavaConstant.forBoxedPrimitive(value);
            return checkExpectedValue(result);
        } else if (array instanceof ImageHeapObjectArray) {
            throw new IllegalArgumentException("Misaligned object read from array.");
        } else {
            throw VMError.shouldNotReachHere("Unexpected base: " + array.getClass());
        }
    }

    private static Object readFromPrimitiveArray(int accessBytes, Object primitiveArray, long hostedOffset) {
        return switch (accessBytes) {
            case 1 -> Unsafe.getUnsafe().getByte(primitiveArray, hostedOffset);
            case 2 -> Unsafe.getUnsafe().getShort(primitiveArray, hostedOffset);
            case 4 -> Unsafe.getUnsafe().getInt(primitiveArray, hostedOffset);
            case 8 -> Unsafe.getUnsafe().getLong(primitiveArray, hostedOffset);
            default -> throw VMError.shouldNotReachHere("Only 1 to 8 bytes can be accessed: " + accessBytes);
        };
    }

    public void forEachArrayElement(JavaConstant array, ObjIntConsumer<JavaConstant> consumer) {
        VMError.guarantee(array instanceof ImageHeapConstant);
        if (array instanceof ImageHeapArray heapArray) {
            heapArray.ensureReaderInstalled();
            for (int index = 0; index < heapArray.getLength(); index++) {
                JavaConstant element = heapArray.readElementValue(index);
                consumer.accept(checkExpectedValue(element), index);
            }
        }
    }

    private static JavaConstant checkExpectedValue(JavaConstant value) {
        VMError.guarantee(isExpectedJavaConstant(value));
        return value;
    }

    private static boolean isExpectedJavaConstant(JavaConstant value) {
        return value.isNull() || value.getJavaKind().isPrimitive() || value instanceof PatchedWordConstant || value instanceof ImageHeapConstant;
    }

    @Override
    public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        return readValue((AnalysisField) field, receiver, false, false);
    }

    @Override
    public JavaConstant boxPrimitive(JavaConstant source) {
        throw VMError.intentionallyUnimplemented();
    }

    public JavaConstant readValue(AnalysisField field, JavaConstant receiver, boolean returnSimulatedValues, boolean readRelocatableValues) {
        if (!field.isStatic()) {
            if (!(receiver instanceof ImageHeapInstance imageHeapInstance) || !field.getDeclaringClass().isAssignableFrom(imageHeapInstance.getType())) {
                /*
                 * During compiler optimizations, it is possible to see field loads with a constant
                 * receiver of a wrong type. The code will later be removed as dead code, and in
                 * most cases the field read would also be rejected as illegal by the HotSpot
                 * constant reflection provider doing the actual field load. But there are several
                 * other ways how a field can be accessed, e.g., fields of classes that are
                 * initialized at image run time. To avoid any surprises, we abort the field reading
                 * here early.
                 */
                return null;
            }
        }

        if (field.preventConstantFolding()) {
            /* Reading this value is prohibited. */
            return null;
        }

        if (receiver instanceof ImageHeapInstance imageHeapInstance && imageHeapInstance.isInSharedLayer() && imageHeapInstance.nullFieldValues()) {
            return null;
        }

        if (!fieldValueInterceptionSupport.isValueAvailable(field, receiver)) {
            /* Value is not yet available. */
            return null;
        }

        VMError.guarantee(receiver == null || receiver instanceof ImageHeapConstant, "Expected ImageHeapConstant, found: %s", receiver);
        JavaConstant value = null;
        if (returnSimulatedValues) {
            value = readSimulatedValue(field);
        }
        if (value == null && field.isStatic()) {
            /*
             * The shadow heap simply returns the hosted value for static fields, it doesn't
             * directly store simulated values. The simulated values are only accessible via
             * SimulateClassInitializerSupport.getSimulatedFieldValue().
             */
            if (SimulateClassInitializerSupport.singleton().isEnabled()) {
                /*
                 * The "late initialization" doesn't work with heap snapshots because the wrong
                 * value will be snapshot for classes proven late, so we only read via the shadow
                 * heap if simulation of class initializers is enabled. The check and this comment
                 * will be removed when the old initialization strategy is removed.
                 */
                value = field.getDeclaringClass().getOrComputeData().readFieldValue(field);
            }
        }
        if (value == null && receiver instanceof ImageHeapConstant heapConstant) {
            heapConstant.ensureReaderInstalled();
            ImageHeapInstance heapObject = (ImageHeapInstance) receiver;
            value = heapObject.readFieldValue(field);
        }
        if (value == null) {
            VMError.guarantee(!SimulateClassInitializerSupport.singleton().isEnabled());
            ImageHeapScanner heapScanner = universe.getHeapScanner();
            HostedValuesProvider hostedValuesProvider = universe.getHostedValuesProvider();
            value = heapScanner.createImageHeapConstant(hostedValuesProvider.readFieldValueWithReplacement(field, receiver), ObjectScanner.OtherReason.UNKNOWN);
        }

        if (!readRelocatableValues && value instanceof ImageHeapRelocatableConstant) {
            /*
             * During compilation we do not want to fold relocatable constants. However, they must
             * be seen during the heap scanning process.
             */
            return null;
        }
        return value;
    }

    /**
     * For classes that are simulated as initialized, provide the value of static fields to the
     * static analysis so that they are seen properly as roots in the image heap.
     * <p>
     * We cannot return such simulated field values for "normal" field value reads because then they
     * would be seen during bytecode parsing too. Therefore, we only return such values when
     * explicitly requested via a flag.
     */
    private JavaConstant readSimulatedValue(AnalysisField field) {
        if (!field.isStatic() || field.getDeclaringClass().isInitialized()) {
            return null;
        }
        field.getDeclaringClass().getInitializeMetaDataTask().ensureDone();
        if (simulateClassInitializerSupport == null) {
            simulateClassInitializerSupport = SimulateClassInitializerSupport.singleton();
        }
        return simulateClassInitializerSupport.getSimulatedFieldValue(field);
    }

    @Override
    public ResolvedJavaType asJavaType(Constant constant) {
        if (constant instanceof JavaConstant javaConstant && metaAccess.isInstanceOf(javaConstant, Class.class)) {
            /* All type constants must have a hosted object. */
            Object hostedObject = Objects.requireNonNull(universe.getSnippetReflection().asObject(Object.class, javaConstant));
            VMError.guarantee(!(hostedObject instanceof Class<?>), "Must not have java.lang.Class object: %s", hostedObject);
            return getHostVM().lookupType((DynamicHub) hostedObject);
        }
        return null;
    }

    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        return universe.getHeapScanner().createImageHeapConstant(getHostVM().dynamicHub(type), ObjectScanner.OtherReason.UNKNOWN);
    }

    @Override
    public Constant asObjectHub(ResolvedJavaType type) {
        /*
         * Substrate VM does not distinguish between the hub and the Class, they are both
         * represented by the DynamicHub.
         */
        return asJavaClass(type);
    }

    @Override
    public JavaConstant forString(String value) {
        if (value == null) {
            return JavaConstant.NULL_POINTER;
        }
        return universe.getHeapScanner().createImageHeapConstant(value, ObjectScanner.OtherReason.UNKNOWN);
    }

    @Override
    public boolean initializationCheckRequired(ResolvedJavaType type) {
        return classInitializationSupport.requiresInitializationNodeForTypeReached(type);
    }

    private SVMHost getHostVM() {
        return (SVMHost) universe.hostVM();
    }
}
