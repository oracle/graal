/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;

import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.heap.HostedValuesProvider;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.meta.MethodOffset;
import com.oracle.svm.core.meta.MethodRef;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.hosted.classinitialization.SimulateClassInitializerSupport;
import com.oracle.svm.hosted.meta.PatchedWordConstant;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAccess;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PrimitiveConstant;

public class SVMHostedValueProvider extends HostedValuesProvider {

    private final FieldValueInterceptionSupport fieldValueInterceptionSupport = FieldValueInterceptionSupport.singleton();

    public SVMHostedValueProvider(AnalysisMetaAccess metaAccess, AnalysisUniverse universe) {
        super(metaAccess, universe);
    }

    /**
     * Read the field value and wrap it in a value supplier without performing any replacements. The
     * replacements are applied when the value is reached in the shadow heap. The shadow heap
     * doesn't directly store simulated values. The simulated values are only accessible via
     * {@link SimulateClassInitializerSupport#getSimulatedFieldValue(AnalysisField)}. The shadow
     * heap is a snapshot of the hosted state; simulated values are a level above the shadow heap.
     */
    @Override
    public ValueSupplier<JavaConstant> readFieldValue(AnalysisField field, JavaConstant receiver) {
        if (fieldValueInterceptionSupport.isValueAvailable(field, receiver)) {
            /* Materialize and return the value. */
            return ValueSupplier.eagerValue(doReadValue(field, receiver));
        }
        /*
         * Return a lazy value. First, this applies to fields that have a
         * FieldValueTransformerWithAvailability. Secondly, this applies to fields annotated
         * with @UnknownObjectField whose value is set directly either during analysis or in a later
         * phase. Attempts to materialize the value before it becomes available will result in an
         * error.
         */
        return ValueSupplier.lazyValue(() -> doReadValue(field, receiver), () -> fieldValueInterceptionSupport.isValueAvailable(field, receiver));
    }

    /** Returns the hosted field value with replacements applied. */
    @Override
    public JavaConstant readFieldValueWithReplacement(AnalysisField field, JavaConstant receiver) {
        return replaceObject(doReadValue(field, receiver));
    }

    private JavaConstant doReadValue(AnalysisField field, JavaConstant receiver) {
        VMError.guarantee(!(receiver instanceof SubstrateObjectConstant));
        return interceptHosted(fieldValueInterceptionSupport.readFieldValue(field, receiver));
    }

    /**
     * This method returns the hosted array element value without any replacement. The replacements
     * are applied when the value is reached in the shadow heap.
     */
    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        /*
         * GR-79043: Keep ordinary array elements constant-native and avoid materializing them
         * to detect values that require provider-specific word canonicalization.
         */
        JavaConstant element = super.readArrayElement(array, index);
        return interceptWordType(super.asObject(Object.class, element)).orElse(element);
    }

    /**
     * Canonicalizes a raw guest {@link WordBase} returned by an ordinary object replacer to the
     * representation expected by Native Image. Ordinary guest constants and already canonical
     * {@link PatchedWordConstant}s are left unchanged so that arbitrary guest objects are not
     * materialized through {@link #interceptHosted}.
     */
    @Override
    public JavaConstant canonicalizeReplacedConstant(JavaConstant value) {
        boolean needsWordCanonicalization = !(value instanceof PatchedWordConstant) && isWord(value);
        if (!needsWordCanonicalization) {
            return value;
        }
        JavaConstant canonicalValue = interceptHosted(value);
        VMError.guarantee(canonicalValue instanceof PatchedWordConstant || !isWord(canonicalValue));
        return canonicalValue;
    }

    /** Returns whether {@code value} represents a guest {@link WordBase}. */
    private static boolean isWord(JavaConstant value) {
        var valueType = GuestAccess.get().getProviders().getMetaAccess().lookupJavaType(value);
        return valueType != null && GuestAccess.elements().WordBase.isAssignableFrom(valueType);
    }

    /**
     * Replaces patched words such as {@link MethodRef} with {@link PatchedWordConstant}, and regular
     * {@link WordBase} values with {@link PrimitiveConstant}. No other {@link WordBase} values can be
     * reachable at this point.
     */
    @Override
    public JavaConstant forObject(Object object) {
        /* The raw object may never be an ImageHeapConstant. */
        AnalysisError.guarantee(!(object instanceof ImageHeapConstant), "Unexpected ImageHeapConstant %s", object);
        return interceptWordType(object).orElse(super.forObject(object));
    }

    /**
     * Keeps an ordinary {@link WordBase} as a materializable object constant while replacers are
     * still running. Relocatable words retain their {@link PatchedWordConstant} representation so
     * JVMCI replacers can handle them symbolically. The final ordinary word result is converted to
     * a {@link PrimitiveConstant} by {@link #canonicalizeReplacedConstant}.
     */
    @Override
    public JavaConstant forObjectReplacerResult(Object object) {
        AnalysisError.guarantee(!(object instanceof ImageHeapConstant), "Unexpected ImageHeapConstant %s", object);
        if (object instanceof WordBase && !(object instanceof RelocatedPointer) && !(object instanceof MethodOffset)) {
            return super.forObjectReplacerResult(object);
        }
        return forObject(object);
    }

    @Override
    public <T> T asObject(Class<T> type, JavaConstant constant) {
        if (constant instanceof PatchedWordConstant pwc) {
            return type.cast(pwc.getWord());
        }
        return super.asObject(type, constant);
    }

    /**
     * Intercept hosted objects that need special treatment.
     * <ul>
     * <li>First, we allow hosted objects to reference {@link ImageHeapConstant} directly. This is
     * useful for example when encoding heap partition limits. Instead of referencing the raw hosted
     * object from ImageHeapInfo we reference a {@link ImageHeapConstant} which allows using
     * simulated constant as partition limits. However, since the original
     * {@link ConstantReflectionProvider} is not aware of {@link ImageHeapConstant} it always treats
     * them as hosted objects and wraps them into a host {@link JavaConstant}. Therefore, we need
     * intercept the host {@link JavaConstant} and if it wraps an {@link ImageHeapConstant} unwrap
     * it and return the original constant.</li>
     * <li>Second, intercept {@link WordBase} constants. See {@link #interceptWordType(Object)} for
     * details.</li>
     * </ul>
     * This method will return null if the input constant is null.
     */
    @Override
    public JavaConstant interceptHosted(JavaConstant constant) {
        if (constant != null && constant.getJavaKind().isObject() && !constant.isNull()) {
            /*
             * GR-79043: Avoid materializing ordinary guest constants solely to detect values that
             * require provider-specific word canonicalization. Existing hosted data structures
             * still require the ImageHeapConstant unwrapping performed here.
             */
            Object original = super.asObject(Object.class, constant);
            if (original instanceof ImageHeapConstant heapConstant) {
                return heapConstant;
            }
            /* Intercept WordBase and RelocatedPointer objects, otherwise return input constant. */
            return interceptWordType(original).orElse(constant);
        }
        return constant;
    }

    /**
     * Intercept {@link WordBase} constants and:
     * <ul>
     * <li>replace {@link MethodRef} constants with {@link PatchedWordConstant} to easily and
     * reliably distinguish them from other {@link WordBase} values during image build.</li>
     * <li>replace regular {@link WordBase} values with corresponding integer kind
     * {@link PrimitiveConstant}.</li>
     * </ul>
     */
    private Optional<JavaConstant> interceptWordType(Object object) {
        if (object instanceof WordBase word) {
            if (object instanceof RelocatedPointer || object instanceof MethodOffset) {
                return Optional.of(new PatchedWordConstant(word, metaAccess.lookupJavaType(object.getClass())));
            }
            return Optional.of(JavaConstant.forIntegerKind(SubstrateTarget.getWordKind(), word.rawValue()));
        }
        return Optional.empty();
    }

}
