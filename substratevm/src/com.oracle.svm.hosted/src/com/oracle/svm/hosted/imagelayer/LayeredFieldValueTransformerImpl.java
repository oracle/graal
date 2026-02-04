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
import com.oracle.svm.core.fieldvaluetransformer.JVMCIFieldValueTransformerWithAvailability;
import com.oracle.svm.core.fieldvaluetransformer.JVMCIFieldValueTransformerWithReceiverBasedAvailability;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.guest.staging.layered.LayeredFieldValueTransformer;
import com.oracle.svm.util.GraalAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.graal.compiler.debug.Assertions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Provides logic for representing a {@link LayeredFieldValueTransformer} as a
 * {@link JVMCIFieldValueTransformerWithAvailability} and keeping track of all needed state
 * information. Because we cannot perform constant folding of updatable values, upon any call to
 * {@link #isAvailable(JavaConstant)} we must also eagerly perform the transformation to see if we
 * can expose the result. Hence, logic is needed for caching the result of updated. This logic is
 * contained with {@link TransformedValueState}.
 */
public class LayeredFieldValueTransformerImpl extends JVMCIFieldValueTransformerWithReceiverBasedAvailability {
    private static final ResolvedJavaType OBJECT = GraalAccess.lookupType(Object.class);
    private static final ResolvedJavaType LAYERED_FIELD_VALUE_TRANSFORMER = GraalAccess.lookupType(LayeredFieldValueTransformer.class);
    private static final ResolvedJavaMethod IS_VALUE_AVAILABLE = JVMCIReflectionUtil.getUniqueDeclaredMethod(LAYERED_FIELD_VALUE_TRANSFORMER, "isValueAvailable", OBJECT);
    private static final ResolvedJavaMethod IS_UPDATE_AVAILABLE = JVMCIReflectionUtil.getUniqueDeclaredMethod(LAYERED_FIELD_VALUE_TRANSFORMER, "isUpdateAvailable", OBJECT);
    private static final ResolvedJavaMethod TRANSFORM = JVMCIReflectionUtil.getUniqueDeclaredMethod(LAYERED_FIELD_VALUE_TRANSFORMER, "transform", OBJECT);
    private static final ResolvedJavaMethod UPDATE = JVMCIReflectionUtil.getUniqueDeclaredMethod(LAYERED_FIELD_VALUE_TRANSFORMER, "update", OBJECT);

    private static final ResolvedJavaType LAYERED_FIELD_VALUE_TRANSFORMER_RESULT = GraalAccess.lookupType(LayeredFieldValueTransformer.Result.class);
    private static final ResolvedJavaMethod VALUE = JVMCIReflectionUtil.getUniqueDeclaredMethod(LAYERED_FIELD_VALUE_TRANSFORMER_RESULT, "value");
    private static final ResolvedJavaMethod UPDATABLE = JVMCIReflectionUtil.getUniqueDeclaredMethod(LAYERED_FIELD_VALUE_TRANSFORMER_RESULT, "updatable");

    final AnalysisField aField;
    final JavaConstant layerTransformer;

    /**
     * Set of ConstantID for which this field is updatable. We are using the integer ids, instead of
     * the actual image heap constants, so that we don't need to force load these values too
     * eagerly. This can be especially problematic for image heap constants because they may need to
     * be relinked to their backing host object, which we cannot do eagerly.
     **/
    final Set<Integer> priorLayerReceiversWithUpdatableValues;

    final Map<JavaConstant, TransformedValueState> receiverToValueStateMap = new ConcurrentHashMap<>();

    boolean currentLayerHasUpdatableValues = false;

    LayeredFieldValueTransformerImpl(AnalysisField aField, JavaConstant layerTransformer, Set<Integer> priorLayerReceiversWithUpdatableValues) {
        this.aField = aField;
        this.layerTransformer = layerTransformer;
        this.priorLayerReceiversWithUpdatableValues = priorLayerReceiversWithUpdatableValues;
    }

    boolean isUpdatableReceiver(JavaConstant receiver) {
        var valueState = receiverToValueStateMap.get(getHostedObject(receiver));
        return valueState.isUpdatable();
    }

    List<ImageHeapConstant> computeUpdatableReceivers() {
        return receiverToValueStateMap.values().stream().filter(
                        TransformedValueState::isUpdatable)
                        .map(state -> Objects.requireNonNull(state.ihcReceiver))
                        .toList();
    }

    /**
     * This method is called during image heap layouting. At this point all compiler optimization
     * have already been performed and so it is now legal to expose all values.
     *
     * @return whether this value is updatable.
     */
    boolean finalizeFieldValue(ImageHeapConstant ihc) {
        // We assume this is single threaded
        var info = receiverToValueStateMap.get(ihc.getHostedObject());
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
    private TransformedValueState createValueStatue(JavaConstant receiver) {
        boolean useUpdate = false;
        JavaConstant finalReceiver = receiver;
        if (receiver instanceof ImageHeapConstant ihc) {
            finalReceiver = ihc.getHostedObject();
            if (ihc.isInSharedLayer()) {
                useUpdate = priorLayerReceiversWithUpdatableValues.contains(ImageHeapConstant.getConstantID(ihc));
            }
        }
        return new TransformedValueState(finalReceiver, useUpdate);
    }

    TransformedValueState maybeUpdateState(JavaConstant receiver) {
        var valueState = receiverToValueStateMap.computeIfAbsent(getHostedObject(receiver), _ -> createValueStatue(receiver));
        valueState.maybeTransform();
        return valueState;
    }

    /**
     * Returns {@link LayeredFieldValueTransformer#update} result if available.
     *
     * @return the result of the update or {@code null} if an updated result is not available.
     */
    LayeredFieldValueTransformerImpl.TransformedValueState updateAndGetResult(ImageHeapConstant receiver) {
        var state = maybeUpdateState(receiver);
        assert state.useUpdate : Assertions.errorMessage("Wrong behavior associated with transformer", receiver);
        return state;
    }

    @Override
    public boolean isAvailable(JavaConstant receiver) {
        return maybeUpdateState(Objects.requireNonNull(receiver)).isAvailableAndExposed();
    }

    @Override
    public JavaConstant transform(JavaConstant receiver, JavaConstant originalValue) {
        var valueState = receiverToValueStateMap.get(Objects.requireNonNull(getHostedObject(receiver)));
        VMError.guarantee(valueState.isAvailableAndExposed());
        return valueState.transformerResultValue;
    }

    private static JavaConstant getHostedObject(JavaConstant receiver) {
        return receiver instanceof ImageHeapConstant ihc ? ihc.getHostedObject() : receiver;
    }

    /**
     * This class acts the adapter between the "normal" {@link FieldValueTransformer} world and
     * {@link LayeredFieldValueTransformer} for a given receiver value. In addition, it performs
     * caching of results and maintains logic for determining when to expose transformed values.
     */
    public class TransformedValueState {
        /**
         * This is the value stored as a key within {@link #priorLayerReceiversWithUpdatableValues}
         * and is passed to the transformation.
         */
        final JavaConstant receiver;
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
        private JavaConstant transformerResultValue;
        private boolean transformerResultUpdatable;
        /**
         * Because we cannot allow updatable results to be constant folded, we must wait to show
         * updatable results until {@link #finalizeFieldValue} is triggered.
         */
        private boolean exposeUpdatableResults = false;

        TransformedValueState(JavaConstant receiver, boolean useUpdate) {
            this.receiver = receiver;
            this.useUpdate = useUpdate;
        }

        /**
         * If the result is not yet cached, do the transformation if it is available.
         */
        void maybeTransform() {
            if (isUnresolved()) {
                boolean transformAvailable;
                if (useUpdate) {
                    transformAvailable = GraalAccess.getVMAccess().invoke(IS_UPDATE_AVAILABLE, layerTransformer, receiver).asBoolean();
                } else {
                    transformAvailable = GraalAccess.getVMAccess().invoke(IS_VALUE_AVAILABLE, layerTransformer, receiver).asBoolean();
                }
                if (transformAvailable) {
                    doTransform();
                }
            }
        }

        synchronized void doTransform() {
            if (isUnresolved()) {
                JavaConstant resultConstant;
                if (useUpdate) {
                    resultConstant = GraalAccess.getVMAccess().invoke(UPDATE, layerTransformer, receiver);
                } else {
                    resultConstant = GraalAccess.getVMAccess().invoke(TRANSFORM, layerTransformer, receiver);
                }
                transformerResultValue = getResultValue(resultConstant);
                transformerResultUpdatable = getResultUpdatable(resultConstant);
            }
        }

        private JavaConstant getResultValue(JavaConstant resultConstant) {
            return GraalAccess.getVMAccess().invoke(VALUE, resultConstant);
        }

        private boolean getResultUpdatable(JavaConstant resultConstant) {
            return GraalAccess.getVMAccess().invoke(UPDATABLE, resultConstant).asBoolean();
        }

        public boolean isUnresolved() {
            return transformerResultValue == null;
        }

        /**
         * @return true if the transformation result is available and it is safe to expose the
         *         result.
         */
        boolean isAvailableAndExposed() {
            if (transformerResultValue != null) {
                return !transformerResultUpdatable || exposeUpdatableResults;
            }
            return false;
        }

        /**
         * @return true if the result may be updated by a subsequent layer.
         */
        public boolean isUpdatable() {
            assert isAvailableAndExposed();
            return transformerResultUpdatable;
        }

        /**
         * @return the result value in the current layer.
         */
        public JavaConstant getResultValue() {
            assert isAvailableAndExposed();
            return transformerResultValue;
        }
    }
}
