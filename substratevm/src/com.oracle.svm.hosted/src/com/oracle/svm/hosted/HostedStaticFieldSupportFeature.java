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
package com.oracle.svm.hosted;

import java.lang.reflect.Field;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.StaticFieldsSupport.HostedStaticFieldSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.fieldvaluetransformer.JavaConstantWrapper;
import com.oracle.svm.core.imagelayer.DynamicImageLayerInfo;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport;
import com.oracle.svm.hosted.imagelayer.LayeredStaticFieldSupport;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
final class HostedStaticFieldSupportFeature extends HostedStaticFieldSupport implements InternalFeature {

    private enum State {
        UNUSED,
        CURRENT_LAYER,
        PRIOR_LAYER,
        FUTURE_APP_LAYER,
    }

    private HostedMetaAccess hostedMetaAccess;

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(HostedStaticFieldSupport.class, this);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        hostedMetaAccess = ((FeatureImpl.BeforeCompilationAccessImpl) access).getMetaAccess();
    }

    private State determineState(int layerNum) {
        if (layerNum == MultiLayeredImageSingleton.UNUSED_LAYER_NUMBER) {
            return State.UNUSED;
        } else {
            int currentLayerNum = getCurrentLayerNumber();
            if (currentLayerNum == layerNum) {
                return State.CURRENT_LAYER;
            } else if (layerNum < currentLayerNum) {
                assert layerNum == 0 && currentLayerNum == 1;
                return State.PRIOR_LAYER;
            } else {
                assert layerNum == LayeredStaticFieldSupport.getAppLayerNumber() && currentLayerNum == 0;
                return State.FUTURE_APP_LAYER;
            }
        }
    }

    @Override
    protected Object getStaticFieldBaseTransformation(int layerNum, boolean primitive) {
        return switch (determineState(layerNum)) {
            case UNUSED, CURRENT_LAYER ->
                primitive ? StaticFieldsSupport.getCurrentLayerStaticPrimitiveFields() : StaticFieldsSupport.getCurrentLayerStaticObjectFields();
            case PRIOR_LAYER -> {
                var value = primitive ? HostedImageLayerBuildingSupport.singleton().getLoader().getBaseLayerStaticPrimitiveFields()
                                : HostedImageLayerBuildingSupport.singleton().getLoader().getBaseLayerStaticObjectFields();
                yield new JavaConstantWrapper(value);
            }
            case FUTURE_APP_LAYER ->
                new JavaConstantWrapper(LayeredStaticFieldSupport.singleton().getAppLayerStaticFieldBaseConstant(primitive));
        };
    }

    @Override
    protected FloatingNode getStaticFieldsBaseReplacement(int layerNum, boolean primitive, LoweringTool tool, StructuredGraph graph) {
        return switch (determineState(layerNum)) {
            case UNUSED, CURRENT_LAYER -> {
                Object hostedObject = primitive ? StaticFieldsSupport.getCurrentLayerStaticPrimitiveFields() : StaticFieldsSupport.getCurrentLayerStaticObjectFields();
                JavaConstant constant = tool.getSnippetReflection().forObject(hostedObject);
                yield ConstantNode.forConstant(constant, tool.getMetaAccess(), graph);
            }
            case PRIOR_LAYER -> {
                var constant = primitive ? HostedImageLayerBuildingSupport.singleton().getLoader().getBaseLayerStaticPrimitiveFields()
                                : HostedImageLayerBuildingSupport.singleton().getLoader().getBaseLayerStaticObjectFields();
                yield ConstantNode.forConstant(constant, tool.getMetaAccess(), graph);
            }
            case FUTURE_APP_LAYER ->
                LayeredStaticFieldSupport.singleton().getAppLayerStaticFieldsBaseReplacement(primitive, tool, graph);
        };
    }

    @Override
    protected boolean isPrimitive(ResolvedJavaField field) {
        if (field instanceof AnalysisField aField) {
            return aField.getStorageKind().isPrimitive();
        }
        return ((HostedField) field).getStorageKind().isPrimitive();
    }

    private int currentLayerCache = MultiLayeredImageSingleton.LAYER_NUM_UNINSTALLED;

    private int getCurrentLayerNumber() {
        if (currentLayerCache == MultiLayeredImageSingleton.LAYER_NUM_UNINSTALLED) {
            int newLayerNumber = DynamicImageLayerInfo.getCurrentLayerNumber();
            assert newLayerNumber != MultiLayeredImageSingleton.LAYER_NUM_UNINSTALLED;
            currentLayerCache = newLayerNumber;
        }
        return currentLayerCache;
    }

    @Override
    protected int getInstalledLayerNum(ResolvedJavaField field) {
        assert ImageLayerBuildingSupport.buildingImageLayer();
        if (field instanceof SharedField sField) {
            return sField.getInstalledLayerNum();
        } else {
            AnalysisField aField = (AnalysisField) field;
            return switch (LayeredStaticFieldSupport.singleton().getAssignmentStatus(aField)) {
                case UNSPECIFIED -> getCurrentLayerNumber();
                case PRIOR_LAYER -> LayeredStaticFieldSupport.singleton().getPriorInstalledLayerNum(aField);
                case APP_LAYER_REQUESTED, APP_LAYER_DEFERRED -> LayeredStaticFieldSupport.getAppLayerNumber();
            };
        }
    }

    @Override
    protected ResolvedJavaField toResolvedField(Field field) {
        return hostedMetaAccess.lookupJavaField(field);
    }
}
