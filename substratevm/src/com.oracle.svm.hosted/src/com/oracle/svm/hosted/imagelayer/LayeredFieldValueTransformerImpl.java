/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.hosted.FieldValueTransformer;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithReceiverBasedAvailability;
import com.oracle.svm.core.layered.LayeredFieldValueTransformer;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.debug.Assertions;
import jdk.vm.ci.meta.JavaConstant;

/**
 * Provides logic for representing a {@link LayeredFieldValueTransformer} as a
 * {@link FieldValueTransformerWithReceiverBasedAvailability} and keeping track of all needed state
 * information. Because we cannot perform constant folding of updatable values, upon any call to
 * {@link #isAvailable(JavaConstant)} we must also eagerly perform the transformation to see if we
 * can expose the result. Hence, logic is needed for caching the result of updated. This logic is
 * contained with {@link TransformedValueState}.
 */
public class LayeredFieldValueTransformerImpl extends FieldValueTransformerWithReceiverBasedAvailability {
    final AnalysisField aField;
    final LayeredFieldValueTransformer<?> layerTransformer;

    /**
     * Set of ConstantID for which this field is updatable. We are using the integer ids, instead of
     * the actual image heap constants, so that we don't need to force load these values too
     * eagerly. This can be especially problematic for image heap constants because they may need to
     * be relinked to their backing host object, which we cannot do eagerly.
     **/
    final Set<Integer> priorLayerReceiversWithUpdatableValues;

    final Map<Object, TransformedValueState> receiverToValueStateMap = new ConcurrentHashMap<>();

    boolean currentLayerHasUpdatableValues = false;

    LayeredFieldValueTransformerImpl(AnalysisField aField, LayeredFieldValueTransformer<?> layerTransformer, Set<Integer> priorLayerReceiversWithUpdatableValues) {
        this.aField = aField;
        this.layerTransformer = layerTransformer;
        this.priorLayerReceiversWithUpdatableValues = priorLayerReceiversWithUpdatableValues;
    }

    boolean isUpdatableReceiver(Object receiver) {
        var valueState = receiverToValueStateMap.get(computeCanonicalReceiver(receiver));
        return valueState.isUpdatable();
    }

    List<ImageHeapConstant> computeUpdatableReceivers() {
        return receiverToValueStateMap.values().stream().filter(
                        TransformedValueState::isUpdatable)
                        .map(state -> Objects.requireNonNull(state.ihcReceiver))
                        .toList();
    }

    /**
     * Used to get the canonical representation of a receiver. This is needed because the receiver
     * can be represented as a {@link ImageHeapConstant}, {@link JavaConstant}, or a plain object,
     * but they all refer to the same underlying value.
     *
     * Note in extension layers we expect base layer {@link ImageHeapConstant}s to be relinked to
     * their Hosted Object.
     */
    private static Object computeCanonicalReceiver(Object receiver) {
        if (receiver instanceof ImageHeapConstant ihc) {
            return GraalAccess.getOriginalSnippetReflection().asObject(Object.class, Objects.requireNonNull(ihc.getHostedObject()));
        } else if (receiver instanceof JavaConstant jc) {
            return GraalAccess.getOriginalSnippetReflection().asObject(Object.class, jc);
        }
        return receiver;
    }

    /**
     * This method is called during image heap layouting. At this point all compiler optimization
     * have already been performed and so it is now legal to expose all values.
     * 
     * @return whether this value is updatable.
     */
    boolean finalizeFieldValue(ImageHeapConstant ihc) {
        // We assume this is single threaded
        var info = receiverToValueStateMap.get(computeCanonicalReceiver(ihc));
        info.maybeTransform();
        VMError.guarantee(!info.isUnresolved() && !info.exposeUpdatableResults);

        /*
         * At this point all constant folding is done. We now expose the value.
         */
        info.exposeUpdatableResults = true;

        if (info.isUpdatable()) {
            info.ihcReceiver = ihc;
            currentLayerHasUpdatableValues = true;
            return true;
        }

        return false;
    }

    /**
     * If this value was installed in a prior layer, then
     * {@link LayeredFieldValueTransformer#update} should be called instead of
     * {@link LayeredFieldValueTransformer#transform}.
     */
    private TransformedValueState createValueStatue(Object canonicalReceiver, Object receiver) {
        boolean useUpdate = false;
        if (receiver instanceof ImageHeapConstant ihc && ihc.isInBaseLayer()) {
            useUpdate = priorLayerReceiversWithUpdatableValues.contains(ImageHeapConstant.getConstantID(ihc));
        }
        return new TransformedValueState(canonicalReceiver, useUpdate);
    }

    TransformedValueState maybeUpdateState(Object receiver) {
        Object canonicalReceiver = computeCanonicalReceiver(receiver);
        var valueState = receiverToValueStateMap.computeIfAbsent(canonicalReceiver, _ -> createValueStatue(canonicalReceiver, receiver));
        valueState.maybeTransform();
        return valueState;
    }

    /**
     * Returns {@link LayeredFieldValueTransformer#update} result if available.
     * 
     * @return the result of the update or {@code null} if an updated result is not available.
     */
    LayeredFieldValueTransformer.Result updateAndGetResult(ImageHeapConstant receiver) {
        var state = maybeUpdateState(receiver);
        assert state.useUpdate : Assertions.errorMessage("Wrong behavior associated with transformer", receiver);
        return state.transformerResult;
    }

    @Override
    public boolean isAvailable(JavaConstant receiver) {
        return maybeUpdateState(Objects.requireNonNull(receiver)).isAvailableAndExposed();
    }

    @Override
    public Object transform(Object receiver, Object originalValue) {
        var valueState = receiverToValueStateMap.get(computeCanonicalReceiver(Objects.requireNonNull(receiver)));
        VMError.guarantee(valueState.isAvailableAndExposed());
        return valueState.transformerResult.value();
    }

    /**
     * This class acts the adapter between the "normal" {@link FieldValueTransformer} world and
     * {@link LayeredFieldValueTransformer} for a given receiver value. In addition, it performs
     * caching of results and maintains logic for determining when to expose transformed values.
     */
    private class TransformedValueState {
        /**
         * This is the value stored as a key within {@link #priorLayerReceiversWithUpdatableValues}
         * and is passed to the transformation. This value is calculated via
         * {@link #computeCanonicalReceiver}.
         */
        final Object canonicalReceiver;
        /**
         * Flag indicating whether use to the update logic (e.g.
         * {@link LayeredFieldValueTransformer#isUpdateAvailable} and
         * {@link LayeredFieldValueTransformer#update}) or the "normal" transformation logic (e.g.
         * {@link LayeredFieldValueTransformer#isValueAvailable} and
         * {@link LayeredFieldValueTransformer#transform}). If the receiver object was installed in
         * a prior layer, then we must use the update logic; otherwise we use the normal
         * transformation logic.
         */
        final boolean useUpdate;

        ImageHeapConstant ihcReceiver;
        private LayeredFieldValueTransformer.Result transformerResult;
        /**
         * Because we cannot allow updatable results to be constant folded, we must wait to show
         * updatable results until {@link #finalizeFieldValue} is triggered.
         */
        private boolean exposeUpdatableResults = false;

        TransformedValueState(Object canonicalReceiver, boolean useUpdate) {
            this.canonicalReceiver = canonicalReceiver;
            this.useUpdate = useUpdate;
        }

        /**
         * If the result is not yet cached, do the transformation if it is available.
         */
        @SuppressWarnings("unchecked")
        void maybeTransform() {
            if (isUnresolved()) {
                var transformer = SubstrateUtil.cast(layerTransformer, LayeredFieldValueTransformer.class);
                boolean transformAvailable;
                if (useUpdate) {
                    transformAvailable = transformer.isUpdateAvailable(canonicalReceiver);
                } else {
                    transformAvailable = transformer.isValueAvailable(canonicalReceiver);
                }
                if (transformAvailable) {
                    doTransform();
                }
            }
        }

        @SuppressWarnings("unchecked")
        synchronized void doTransform() {
            if (isUnresolved()) {
                LayeredFieldValueTransformer.Result result;
                var transformer = SubstrateUtil.cast(layerTransformer, LayeredFieldValueTransformer.class);
                if (useUpdate) {
                    result = transformer.update(canonicalReceiver);
                } else {
                    result = transformer.transform(canonicalReceiver);
                }
                transformerResult = result;
            }
        }

        boolean isUnresolved() {
            return transformerResult == null;
        }

        /**
         * @return true if the transformation result is available and it is safe to expose the
         *         result.
         */
        boolean isAvailableAndExposed() {
            if (transformerResult != null) {
                return !transformerResult.updatable() || exposeUpdatableResults;
            }
            return false;
        }

        /**
         * @return true if the result may be updated by a subsequent layer.
         */
        boolean isUpdatable() {
            assert isAvailableAndExposed();
            return transformerResult.updatable();
        }
    }
}
