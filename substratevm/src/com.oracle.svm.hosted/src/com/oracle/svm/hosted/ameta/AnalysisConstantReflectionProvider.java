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

import java.util.Objects;
import java.util.Set;
import java.util.function.ObjIntConsumer;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.heap.ImageHeapArray;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.graal.meta.SharedConstantReflectionProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.ObjectConstantEquality;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.classinitialization.SimulateClassInitializerSupport;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedLookupSnippetReflectionProvider;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.RelocatableConstant;

import jdk.graal.compiler.core.common.type.TypedConstant;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

@Platforms(Platform.HOSTED_ONLY.class)
public class AnalysisConstantReflectionProvider extends SharedConstantReflectionProvider {
    private final AnalysisUniverse universe;
    protected final UniverseMetaAccess metaAccess;
    private HostedMetaAccess hMetaAccess;
    private final ClassInitializationSupport classInitializationSupport;
    private final AnalysisMethodHandleAccessProvider methodHandleAccess;
    private SimulateClassInitializerSupport simulateClassInitializerSupport;

    public AnalysisConstantReflectionProvider(AnalysisUniverse universe, UniverseMetaAccess metaAccess, ClassInitializationSupport classInitializationSupport) {
        this.universe = universe;
        this.metaAccess = metaAccess;
        this.classInitializationSupport = classInitializationSupport;
        this.methodHandleAccess = new AnalysisMethodHandleAccessProvider(universe);
    }

    public void setHostedMetaAccess(HostedMetaAccess hMetaAccess) {
        this.hMetaAccess = hMetaAccess;
    }

    @Override
    public Boolean constantEquals(Constant x, Constant y) {
        if (x == y) {
            return true;
        } else if (x instanceof SubstrateObjectConstant && y instanceof SubstrateObjectConstant) {
            return ObjectConstantEquality.get().test((SubstrateObjectConstant) x, (SubstrateObjectConstant) y);
        } else if (x instanceof ImageHeapConstant cx && cx.isBackedByHostedObject() && y instanceof SubstrateObjectConstant) {
            return ObjectConstantEquality.get().test((SubstrateObjectConstant) cx.getHostedObject(), (SubstrateObjectConstant) y);
        } else if (y instanceof ImageHeapConstant cy && cy.isBackedByHostedObject() && x instanceof SubstrateObjectConstant) {
            return ObjectConstantEquality.get().test((SubstrateObjectConstant) cy.getHostedObject(), (SubstrateObjectConstant) x);
        } else {
            return x.equals(y);
        }
    }

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        return EmptyMemoryAccessProvider.SINGLETON;
    }

    private static final Set<Class<?>> BOXING_CLASSES = Set.of(Boolean.class, Byte.class, Short.class, Character.class, Integer.class, Long.class, Float.class, Double.class);

    @Override
    public JavaConstant unboxPrimitive(JavaConstant source) {
        if (!source.getJavaKind().isObject()) {
            return null;
        }
        if (source instanceof ImageHeapConstant imageHeapConstant) {
            /*
             * Unbox by reading the known single field "value", which is a primitive field of the
             * correct unboxed type.
             */
            AnalysisType type = imageHeapConstant.getType(metaAccess);
            if (BOXING_CLASSES.contains(type.getJavaClass())) {
                imageHeapConstant.ensureReaderInstalled();
                ResolvedJavaField[] fields = type.getInstanceFields(true);
                assert fields.length == 1 && fields[0].getName().equals("value");
                return ((ImageHeapInstance) imageHeapConstant).readFieldValue((AnalysisField) fields[0]);
            }
            /* Not a valid boxed primitive. */
            return null;
        }
        return JavaConstant.forBoxedPrimitive(SubstrateObjectConstant.asObject(source));
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
        if (array instanceof ImageHeapConstant) {
            if (array instanceof ImageHeapArray heapArray) {
                return heapArray.getLength();
            }
            return null;
        }
        return super.readArrayLength(array);
    }

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        if (array.getJavaKind() != JavaKind.Object || array.isNull()) {
            return null;
        }
        if (array instanceof ImageHeapConstant) {
            if (array instanceof ImageHeapArray heapArray) {
                if (index < 0 || index >= heapArray.getLength()) {
                    return null;
                }
                heapArray.ensureReaderInstalled();
                return replaceObject(heapArray.readElementValue(index));
            }
            return null;
        }
        JavaConstant element = super.readArrayElement(array, index);
        return element == null ? null : replaceObject(element);
    }

    @Override
    public void forEachArrayElement(JavaConstant array, ObjIntConsumer<JavaConstant> consumer) {
        if (array instanceof ImageHeapConstant) {
            if (array instanceof ImageHeapArray heapArray) {
                heapArray.ensureReaderInstalled();
                for (int index = 0; index < heapArray.getLength(); index++) {
                    JavaConstant element = heapArray.readElementValue(index);
                    consumer.accept(replaceObject(element), index);
                }
            }
            return;
        }
        /* Intercept the original consumer and apply object replacement. */
        super.forEachArrayElement(array, (element, index) -> consumer.accept(replaceObject(element), index));
    }

    @Override
    public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        return readValue(metaAccess, (AnalysisField) field, receiver, false);
    }

    public JavaConstant readValue(UniverseMetaAccess suppliedMetaAccess, AnalysisField field, JavaConstant receiver, boolean returnSimulatedValues) {
        return readValue(suppliedMetaAccess, field, receiver, returnSimulatedValues, true);
    }

    public JavaConstant readValue(UniverseMetaAccess suppliedMetaAccess, AnalysisField field, JavaConstant receiver, boolean returnSimulatedValues, boolean readFromShadowHeap) {
        if (!field.isStatic()) {
            if (receiver.isNull() || !field.getDeclaringClass().isAssignableFrom(((TypedConstant) receiver).getType(metaAccess))) {
                /*
                 * During compiler optimizations, it is possible to see field loads with a constant
                 * receiver of a wrong type. The code will later be removed as dead code, and in
                 * most cases the field read would also be rejected as illegal by the HotSpot
                 * constant reflection provider doing the actual field load. But there are several
                 * other ways how a field can be accessed, e.g., our ReadableJavaField mechanism or
                 * fields of classes that are initialized at image run time. To avoid any surprises,
                 * we abort the field reading here early.
                 */
                return null;
            }
        }

        JavaConstant value = null;
        if (returnSimulatedValues) {
            value = readSimulatedValue(field);
        }
        if (value == null && field.isStatic() && returnSimulatedValues && readFromShadowHeap) {
            /*
             * The shadow heap uses simulated values for static fields by default. So, only when
             * simulated values are explicitly requested we can read via the shadow heap. Otherwise,
             * this will lead to recursive parsing request errors.
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
            AnalysisError.guarantee(ReadableJavaField.isValueAvailable(field), "Value not yet available for %s", field);
            ImageHeapInstance heapObject = (ImageHeapInstance) receiver;
            value = heapObject.readFieldValue(field);
        }
        if (value == null) {
            value = doReadValue(field, universe.toHosted(receiver), suppliedMetaAccess);
        }
        return interceptValue(suppliedMetaAccess, field, value);
    }

    /** Read the field value and wrap it in a value supplier without performing any replacements. */
    public ValueSupplier<JavaConstant> readHostedFieldValue(AnalysisField field, JavaConstant receiver, boolean returnSimulatedValues) {
        if (returnSimulatedValues) {
            var simulatedValue = readSimulatedValue(field);
            if (simulatedValue != null) {
                return ValueSupplier.eagerValue(simulatedValue);
            }
        }

        if (ReadableJavaField.isValueAvailable(field)) {
            /* Materialize and return the value. */
            return ValueSupplier.eagerValue(doReadValue(field, receiver, metaAccess));
        }
        /*
         * Return a lazy value. First, this applies to fields annotated with
         * RecomputeFieldValue.Kind.FieldOffset and RecomputeFieldValue.Kind.Custom whose value
         * becomes available during hosted universe building and is installed by calling
         * ComputedValueField.processSubstrate() or ComputedValueField.readValue(). Secondly, this
         * applies to fields annotated with @UnknownObjectField whose value is set directly either
         * during analysis or in a later phase. Attempts to materialize the value before it becomes
         * available will result in an error.
         */
        return ValueSupplier.lazyValue(() -> doReadValue(field, receiver), () -> ReadableJavaField.isValueAvailable(field));
    }

    /**
     * The {@link HostedMetaAccess} is used to access the {@link HostedField} in the re-computation
     * of {@link RecomputeFieldValue.Kind#AtomicFieldUpdaterOffset} and
     * {@link RecomputeFieldValue.Kind#TranslateFieldOffset} annotated fields .
     */
    private JavaConstant doReadValue(AnalysisField field, JavaConstant receiver) {
        Objects.requireNonNull(hMetaAccess);
        return doReadValue(field, receiver, hMetaAccess);
    }

    private JavaConstant doReadValue(AnalysisField field, JavaConstant receiver, UniverseMetaAccess access) {
        return universe.fromHosted(ReadableJavaField.readFieldValue(access, classInitializationSupport, field.wrapped, receiver));
    }

    /**
     * For classes that are simulated as initialized, provide the value of static fields to the
     * static analysis so that they are seen properly as roots in the image heap.
     *
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

    public JavaConstant interceptValue(UniverseMetaAccess suppliedMetaAccess, AnalysisField field, JavaConstant value) {
        JavaConstant result = value;
        if (result != null) {
            result = filterInjectedAccessor(field, result);
            result = replaceObject(result);
            result = interceptAssertionStatus(field, result);
            result = interceptWordField(suppliedMetaAccess, field, result);
        }
        return result;
    }

    private static JavaConstant filterInjectedAccessor(AnalysisField field, JavaConstant value) {
        if (field.getAnnotation(InjectAccessors.class) != null) {
            /*
             * Fields whose accesses are intercepted by injected accessors are not actually present
             * in the image. Ideally they should never be read, but there are corner cases where
             * this happens. We intercept the value and return 0 / null.
             */
            assert !field.isAccessed();
            return JavaConstant.defaultForKind(value.getJavaKind());
        }
        return value;
    }

    /**
     * Run all registered object replacers.
     */
    private JavaConstant replaceObject(JavaConstant value) {
        if (value == JavaConstant.NULL_POINTER) {
            return JavaConstant.NULL_POINTER;
        }
        if (value instanceof ImageHeapConstant) {
            /* The value is replaced when the object is snapshotted. */
            return value;
        }
        if (value.getJavaKind() == JavaKind.Object) {
            Object oldObject = universe.getSnippetReflection().asObject(Object.class, value);
            Object newObject = universe.replaceObject(oldObject);
            if (newObject != oldObject) {
                return universe.getSnippetReflection().forObject(newObject);
            }
        }
        return value;
    }

    /**
     * Intercept assertion status: the value of the field during image generation does not matter at
     * all (because it is the hosted assertion status), we instead return the appropriate runtime
     * assertion status. Field loads are also intrinsified early in
     * {@link com.oracle.svm.hosted.phases.EarlyConstantFoldLoadFieldPlugin}, but we could still see
     * such a field here if user code, e.g., accesses it via reflection.
     */
    private static JavaConstant interceptAssertionStatus(AnalysisField field, JavaConstant value) {
        if (field.isStatic() && field.isSynthetic() && field.getName().startsWith("$assertionsDisabled")) {
            Class<?> clazz = field.getDeclaringClass().getJavaClass();
            boolean assertionsEnabled = RuntimeAssertionsSupport.singleton().desiredAssertionStatus(clazz);
            return JavaConstant.forBoolean(!assertionsEnabled);
        }
        return value;
    }

    /**
     * Intercept {@link Word} fields. {@link Word} values are boxed objects in the hosted world, but
     * primitive values in the runtime world, so the default value of {@link Word} fields is 0.
     * 
     * {@link HostedLookupSnippetReflectionProvider} replaces relocatable pointers with
     * {@link RelocatableConstant} and regular {@link WordBase} values with
     * {@link PrimitiveConstant}. No other {@link WordBase} values can be reachable at this point.
     */
    private JavaConstant interceptWordField(UniverseMetaAccess suppliedMetaAccess, AnalysisField field, JavaConstant value) {
        if (value.getJavaKind() == JavaKind.Object) {
            VMError.guarantee(value instanceof RelocatableConstant || !suppliedMetaAccess.isInstanceOf(value, WordBase.class));
            if (value.isNull() && field.getType().isWordType()) {
                return JavaConstant.forIntegerKind(universe.getWordKind(), 0);
            }
        }
        return value;
    }

    @Override
    public AnalysisType asJavaType(Constant constant) {
        if (constant instanceof SubstrateObjectConstant substrateConstant) {
            Object obj = universe.getSnippetReflection().asObject(Object.class, substrateConstant);
            if (obj instanceof DynamicHub hub) {
                return getHostVM().lookupType(hub);
            } else if (obj instanceof Class) {
                throw VMError.shouldNotReachHere("Must not have java.lang.Class object: " + obj);
            }
        } else if (constant instanceof ImageHeapConstant imageHeapConstant) {
            if (metaAccess.isInstanceOf((JavaConstant) constant, Class.class)) {
                /* All constants of type DynamicHub/java.lang.Class must have a hosted object. */
                return asJavaType(Objects.requireNonNull(imageHeapConstant.getHostedObject()));
            }
        }
        return null;
    }

    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        return universe.getHeapScanner().createImageHeapConstant(super.forObject(getHostVM().dynamicHub(type)), ObjectScanner.OtherReason.UNKNOWN);
    }

    @Override
    public JavaConstant forString(String value) {
        if (value == null) {
            return JavaConstant.NULL_POINTER;
        }
        return universe.getHeapScanner().createImageHeapConstant(super.forString(value), ObjectScanner.OtherReason.UNKNOWN);
    }

    @Override
    public JavaConstant forObject(Object object) {
        validateRawObjectConstant(object);
        if (object instanceof RelocatedPointer pointer) {
            return new RelocatableConstant(pointer);
        } else if (object instanceof WordBase word) {
            return JavaConstant.forIntegerKind(FrameAccess.getWordKind(), word.rawValue());
        }
        /* Redirect constant lookup through the shadow heap. */
        return universe.getHeapScanner().createImageHeapConstant(super.forObject(object), ObjectScanner.OtherReason.UNKNOWN);
    }

    /**
     * The raw object may never be an {@link ImageHeapConstant}. However, it can be a
     * {@link SubstrateObjectConstant} coming from graphs prepared for run time compilation. In that
     * case we'll get a double wrapping: the {@link SubstrateObjectConstant} parameter value will be
     * wrapped in another {@link SubstrateObjectConstant} which will then be stored in a
     * {@link ImageHeapConstant} in the shadow heap.
     */
    public static void validateRawObjectConstant(Object object) {
        AnalysisError.guarantee(!(object instanceof ImageHeapConstant), "Unexpected ImageHeapConstant %s", object);
    }

    private SVMHost getHostVM() {
        return (SVMHost) universe.hostVM();
    }
}
