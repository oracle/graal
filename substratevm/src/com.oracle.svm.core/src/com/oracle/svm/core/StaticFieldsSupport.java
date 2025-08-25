/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Function;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.RequiredInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Static fields are represented as two arrays in the native image heap: one for Object fields and
 * one for all primitive fields. The byte-offset into these arrays is stored in
 * {@link SharedField#getLocation}.
 * <p>
 * Implementation notes: The arrays are created after static analysis, but before compilation. We
 * need to know how many static fields are reachable in order to compute the appropriate size for
 * the arrays, which is only available after static analysis.
 * <p>
 * When bytecode is parsed before static analysis, the arrays are not available yet. Therefore, the
 * accessor functions {@link StaticFieldsSupport#getStaticObjectFieldsAtRuntime},
 * {@link StaticFieldsSupport#getStaticPrimitiveFieldsAtRuntime} may be intrinsified to
 * {@link StaticFieldResolvedBaseNode}, or a {@link StaticFieldBaseProxyNode} is created. These
 * nodes are then during compilation either lowered to the constant arrays or folded away. This also
 * solves memory graph problems in the Graal compiler: Direct loads/stores using the arrays, for
 * example via Unsafe or VarHandle, alias with static field loads/stores that have dedicated
 * {@link LocationIdentity}. If the arrays are already exposed in the high-level optimization phases
 * of Graal, the compiler would miss the alias since the location identities for arrays are
 * considered non-aliasing with location identities for fields. Replacing the
 * {@link StaticFieldResolvedBaseNode} with a {@link ConstantNode} and/or removing
 * {@link StaticFieldBaseProxyNode} only in the low tier of the compiler solves this problem.
 */
public final class StaticFieldsSupport {

    @Platforms(Platform.HOSTED_ONLY.class)
    public interface HostedStaticFieldSupport {

        static HostedStaticFieldSupport singleton() {
            return ImageSingletons.lookup(HostedStaticFieldSupport.class);
        }

        JavaConstant getStaticFieldsBaseConstant(int layerNum, boolean primitive, Function<Object, JavaConstant> toConstant);

        FloatingNode getStaticFieldsBaseReplacement(int layerNum, boolean primitive, LoweringTool tool, StructuredGraph graph);

        boolean isPrimitive(ResolvedJavaField field);

        int getInstalledLayerNum(ResolvedJavaField field);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private StaticFieldsSupport() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setData(Object[] staticObjectFields, byte[] staticPrimitiveFields) {
        var singleton = MultiLayeredStaticFieldsBase.currentLayer();
        singleton.setData(staticObjectFields, staticPrimitiveFields);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static Object getCurrentLayerStaticObjectFields() {
        var result = MultiLayeredStaticFieldsBase.currentLayer().getObjectFields();
        VMError.guarantee(result != null, "arrays that hold static fields are only available after static analysis");
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static Object getCurrentLayerStaticPrimitiveFields() {
        var result = MultiLayeredStaticFieldsBase.currentLayer().getPrimitiveFields();
        VMError.guarantee(result != null, "arrays that hold static fields are only available after static analysis");
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static JavaConstant getStaticFieldsConstant(ResolvedJavaField field, Function<Object, JavaConstant> toConstant) {
        var hostedSupport = HostedStaticFieldSupport.singleton();
        boolean primitive = hostedSupport.isPrimitive(field);
        int layerNum = getInstalledLayerNum(field);
        return hostedSupport.getStaticFieldsBaseConstant(layerNum, primitive, toConstant);
    }

    public static int getInstalledLayerNum(ResolvedJavaField field) {
        if (!ImageLayerBuildingSupport.buildingImageLayer()) {
            return MultiLayeredImageSingleton.UNUSED_LAYER_NUMBER;
        }
        if (field instanceof SharedField sField) {
            return sField.getInstalledLayerNum();
        } else {
            return HostedStaticFieldSupport.singleton().getInstalledLayerNum(field);
        }
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static Object getStaticObjectFieldsAtRuntime(int layerNum) {
        return StaticFieldBaseProxyNode.staticFieldBaseProxyNode(MultiLayeredStaticFieldsBase.forLayer(layerNum).getObjectFields());
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static Object getStaticPrimitiveFieldsAtRuntime(int layerNum) {
        return StaticFieldBaseProxyNode.staticFieldBaseProxyNode(MultiLayeredStaticFieldsBase.forLayer(layerNum).getPrimitiveFields());
    }

    public static FloatingNode createStaticFieldBaseNode(ResolvedJavaField field) {
        if (field instanceof SharedField sField) {
            boolean primitive = sField.getStorageKind().isPrimitive();
            return new StaticFieldResolvedBaseNode(primitive, sField.getInstalledLayerNum());
        } else {
            if (!SubstrateUtil.HOSTED) {
                throw VMError.shouldNotReachHere("Not possible at runtime");
            }
            boolean primitive = HostedStaticFieldSupport.singleton().isPrimitive(field);
            int layerNumber = getInstalledLayerNum(field);
            return new StaticFieldResolvedBaseNode(primitive, layerNumber);
        }
    }

    /**
     * Represents a proxy for static field base node. See the comment on {@link StaticFieldsSupport}
     * for why this node and {@link StaticFieldResolvedBaseNode} must exist.
     */
    @NodeInfo(cycles = CYCLES_0, size = SIZE_0)
    static final class StaticFieldBaseProxyNode extends ValueNode implements Lowerable {
        public static final NodeClass<StaticFieldBaseProxyNode> TYPE = NodeClass.create(StaticFieldBaseProxyNode.class);

        @Input ValueNode staticFieldsArray;

        /**
         * We must not expose that the stamp will eventually be an array, to avoid memory graph
         * problems. See the comment on {@link StaticFieldsSupport}.
         */
        protected StaticFieldBaseProxyNode(ValueNode staticFieldsArray) {
            super(TYPE, StampFactory.objectNonNull());
            assert ImageLayerBuildingSupport.buildingImageLayer();
            this.staticFieldsArray = staticFieldsArray;
        }

        @Override
        public void lower(LoweringTool tool) {
            if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
                /*
                 * Removing this node must only happen after the memory graph has been built, i.e.,
                 * when the information that static fields are stored in an array is no longer
                 * misleading alias analysis.
                 */
                return;
            }

            replaceAtUsagesAndDelete(staticFieldsArray);
        }

        @NodeIntrinsic
        public static native Object staticFieldBaseProxyNode(Object array);
    }

    @NodeInfo(cycles = CYCLES_0, size = SIZE_1)
    public static final class StaticFieldResolvedBaseNode extends FloatingNode implements Lowerable {
        public static final NodeClass<StaticFieldResolvedBaseNode> TYPE = NodeClass.create(StaticFieldResolvedBaseNode.class);

        public final boolean primitive;
        public final int layerNum;

        /**
         * We must not expose that the stamp will eventually be an array, to avoid memory graph
         * problems. See the comment on {@link StaticFieldsSupport}.
         */
        protected StaticFieldResolvedBaseNode(boolean primitive, int layerNum) {
            super(TYPE, StampFactory.objectNonNull());
            this.primitive = primitive;
            this.layerNum = layerNum;
        }

        /**
         * 
         * Note when lowering during JIT compilation this code will be AOT compiled so that there is
         * not a circular dependency on the intrinsification of
         * {@link StaticFieldsSupport#getStaticPrimitiveFieldsAtRuntime} and
         * {@link StaticFieldsSupport#getStaticObjectFieldsAtRuntime}. While this code is being AOT
         * compiled the HOSTED variant of the lowering will be triggered, so an actual constant will
         * be returned.
         */
        @Override
        public void lower(LoweringTool tool) {
            if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
                /*
                 * Lowering to a ConstantNode must only happen after the memory graph has been
                 * built, i.e., when the information that static fields are stored in an array is no
                 * longer misleading alias analysis.
                 */
                return;
            }
            FloatingNode replacement;
            if (SubstrateUtil.HOSTED) {
                /*
                 * Build-time version of lowering.
                 */
                HostedStaticFieldSupport hostedSupport = HostedStaticFieldSupport.singleton();
                replacement = hostedSupport.getStaticFieldsBaseReplacement(layerNum, primitive, tool, graph());
            } else {
                /*
                 * JIT version of lowering.
                 */
                JavaConstant constant = tool.getSnippetReflection()
                                .forObject(primitive ? StaticFieldsSupport.getStaticPrimitiveFieldsAtRuntime(layerNum) : StaticFieldsSupport.getStaticObjectFieldsAtRuntime(layerNum));
                replacement = ConstantNode.forConstant(constant, tool.getMetaAccess(), graph());
            }
            replaceAndDelete(graph().addOrUniqueWithInputs(replacement));
        }
    }

    /**
     * We must ensure we are not querying the offset of a static field of a type assignable from
     * {@link org.graalvm.word.WordBase}.
     */
    public interface StaticFieldValidator {
        static void checkFieldOffsetAllowed(ResolvedJavaField field) {
            if (field.isStatic()) {
                if (SubstrateUtil.HOSTED) {
                    ImageSingletons.lookup(StaticFieldValidator.class).hostedCheckFieldOffsetAllowed(field);
                } else {
                    SharedField sField = (SharedField) field;
                    boolean wordType = ((SharedType) sField.getType()).isWordType();
                    VMError.guarantee(!wordType, "static Word field offsets cannot be queried");
                }
            }
        }

        void hostedCheckFieldOffsetAllowed(ResolvedJavaField field);
    }
}

@AutomaticallyRegisteredImageSingleton
class MultiLayeredStaticFieldsBase implements MultiLayeredImageSingleton, UnsavedSingleton {

    @UnknownObjectField(availability = BuildPhaseProvider.AfterHostedUniverse.class) private Object[] staticObjectFields = null;

    @UnknownObjectField(availability = BuildPhaseProvider.AfterHostedUniverse.class) private byte[] staticPrimitiveFields = null;

    @Platforms(Platform.HOSTED_ONLY.class)
    static MultiLayeredStaticFieldsBase currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(MultiLayeredStaticFieldsBase.class, false, true);
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static MultiLayeredStaticFieldsBase forLayer(int layerNum) {
        return MultiLayeredImageSingleton.getForLayer(MultiLayeredStaticFieldsBase.class, layerNum);
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    Object[] getObjectFields() {
        return staticObjectFields;
    }

    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    byte[] getPrimitiveFields() {
        return staticPrimitiveFields;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void setData(Object[] objectFields, byte[] primitiveFields) {
        this.staticObjectFields = Objects.requireNonNull(objectFields);
        this.staticPrimitiveFields = Objects.requireNonNull(primitiveFields);
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.ALL_ACCESS;
    }

}

/**
 * When the base is known, then we create a {@link StaticFieldsSupport.StaticFieldResolvedBaseNode}.
 * See {@link StaticFieldsSupport} for how this prevents aliasing issues.
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
@AutomaticallyRegisteredFeature
final class StaticFieldsFeature implements InternalFeature {

    @Override
    public void registerInvocationPlugins(Providers providers, Plugins plugins, ParsingReason reason) {
        Registration r = new Registration(plugins.getInvocationPlugins(), StaticFieldsSupport.class);
        r.register(new RequiredInvocationPlugin("getStaticObjectFieldsAtRuntime", int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode layerNum) {
                if (!ImageLayerBuildingSupport.buildingImageLayer()) {
                    b.addPush(JavaKind.Object, new StaticFieldsSupport.StaticFieldResolvedBaseNode(false, MultiLayeredImageSingleton.UNUSED_LAYER_NUMBER));
                    return true;
                } else if (layerNum.isJavaConstant()) {
                    int num = layerNum.asJavaConstant().asInt();
                    b.addPush(JavaKind.Object, new StaticFieldsSupport.StaticFieldResolvedBaseNode(false, num));
                    return true;
                }
                return false;
            }
        });
        r.register(new RequiredInvocationPlugin("getStaticPrimitiveFieldsAtRuntime", int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unused, ValueNode layerNum) {
                if (!ImageLayerBuildingSupport.buildingImageLayer()) {
                    b.addPush(JavaKind.Object, new StaticFieldsSupport.StaticFieldResolvedBaseNode(true, MultiLayeredImageSingleton.UNUSED_LAYER_NUMBER));
                    return true;
                } else if (layerNum.isJavaConstant()) {
                    int num = layerNum.asJavaConstant().asInt();
                    b.addPush(JavaKind.Object, new StaticFieldsSupport.StaticFieldResolvedBaseNode(true, num));
                    return true;
                }
                return false;
            }
        });
    }
}
