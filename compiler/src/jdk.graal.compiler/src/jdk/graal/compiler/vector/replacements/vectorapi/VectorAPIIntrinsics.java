/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements.vectorapi;

import java.util.function.BiFunction;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.InlineOnlyInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.OptionalLazySymbol;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIBinaryOpNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIBlendNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIBroadcastIntNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPICompareNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPICompressExpandOpNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIConvertNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIExtractNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIFromBitsCoercedNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIIndexPartiallyInUpperRangeNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIInsertNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPILoadMaskedNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPILoadNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIMacroNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIMaskReductionCoercedNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIRearrangeOpNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIReductionCoercedNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIStoreMaskedNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIStoreNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPITernaryOpNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPITestNode;
import jdk.graal.compiler.vector.replacements.vectorapi.nodes.VectorAPIUnaryOpNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Graph builder plugins to intrinsify some Vector API operations. These plugins build the
 * {@link VectorAPIMacroNode} corresponding to each supported operation from the Vector API's
 * {@code VectorSupport} class. Shuffles, most masked operations, and gather/scatter operations are
 * not supported at the moment.
 */
public class VectorAPIIntrinsics {

    public static class Options {
        // @formatter:off
        @Option(help = "Expand Vector API operations to optimized machine instructions")
        public static final OptionKey<Boolean> OptimizeVectorAPI = new OptionKey<>(true);
        // @formatter:on
    }

    public static boolean intrinsificationSupported(OptionValues options) {
        return GraalOptions.TargetVectorLowering.getValue(options) && Options.OptimizeVectorAPI.getValue(options);
    }

    /**
     * Register the Vector API plugins.
     */
    public static void registerPlugins(InvocationPlugins plugins) {
        String vectorSupportPackage = "jdk.internal.vm.vector";
        Registration r = new Registration(plugins, vectorSupportPackage + ".Utils");
        r.register(new InlineOnlyInvocationPlugin("isNonCapturingLambda", Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                /*
                 * This method is only used in assertions in the Java slow paths of the Vector API
                 * operations. We constant fold it to true to avoid this overhead in the compiled
                 * slow path. If the assertion can ever fail, it will already fail in the
                 * interpreter, while running JDK tests; it is only meant to catch unexpected
                 * changes to the JDK code itself. When C2 generates inline code for the Vector API
                 * intrinsics, it ignores this assertion as well.
                 */
                b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
                return true;
            }
        });

        String vectorSupportName = vectorSupportPackage + ".VectorSupport";
        r = new Registration(plugins, vectorSupportName);

        /* Types of vectors and related data. */
        OptionalLazySymbol vectorSpecies = new OptionalLazySymbol(vectorSupportName + "$VectorSpecies");
        OptionalLazySymbol vector = new OptionalLazySymbol(vectorSupportName + "$Vector");
        OptionalLazySymbol vectorMask = new OptionalLazySymbol(vectorSupportName + "$VectorMask");
        OptionalLazySymbol vectorShuffle = new OptionalLazySymbol(vectorSupportName + "$VectorShuffle");
        OptionalLazySymbol vectorPayload = new OptionalLazySymbol(vectorSupportName + "$VectorPayload");

        /* Types of operations on vectors. */
        OptionalLazySymbol fromBitsCoercedOperation = new OptionalLazySymbol(vectorSupportName + "$FromBitsCoercedOperation");
        OptionalLazySymbol indexPartiallyInUpperRangeOp = new OptionalLazySymbol(vectorSupportName + "$IndexPartiallyInUpperRangeOperation");
        OptionalLazySymbol reductionOperation = new OptionalLazySymbol(vectorSupportName + "$ReductionOperation");
        OptionalLazySymbol extractOp = new OptionalLazySymbol(vectorSupportName + "$VecExtractOp");
        OptionalLazySymbol insertOp = new OptionalLazySymbol(vectorSupportName + "$VecInsertOp");
        OptionalLazySymbol unaryOperation = new OptionalLazySymbol(vectorSupportName + "$UnaryOperation");
        OptionalLazySymbol binaryOperation = new OptionalLazySymbol(vectorSupportName + "$BinaryOperation");
        OptionalLazySymbol ternaryOperation = new OptionalLazySymbol(vectorSupportName + "$TernaryOperation");
        OptionalLazySymbol loadOperation = new OptionalLazySymbol(vectorSupportName + "$LoadOperation");
        OptionalLazySymbol loadMaskedOperation = new OptionalLazySymbol(vectorSupportName + "$LoadVectorMaskedOperation");
        OptionalLazySymbol storeVectorOperation = new OptionalLazySymbol(vectorSupportName + "$StoreVectorOperation");
        OptionalLazySymbol storeMaskedOperation = new OptionalLazySymbol(vectorSupportName + "$StoreVectorMaskedOperation");
        OptionalLazySymbol vectorCompareOp = new OptionalLazySymbol(vectorSupportName + "$VectorCompareOp");
        OptionalLazySymbol vectorBlendOp = new OptionalLazySymbol(vectorSupportName + "$VectorBlendOp");
        OptionalLazySymbol vectorBroadcastIntOp = new OptionalLazySymbol(vectorSupportName + "$VectorBroadcastIntOp");
        OptionalLazySymbol vectorConvertOp = new OptionalLazySymbol(vectorSupportName + "$VectorConvertOp");
        OptionalLazySymbol vectorRearrangeOp = new OptionalLazySymbol(vectorSupportName + "$VectorRearrangeOp");
        OptionalLazySymbol compressExpandOperation = new OptionalLazySymbol(vectorSupportName + "$CompressExpandOperation");
        OptionalLazySymbol vectorMaskOp = new OptionalLazySymbol(vectorSupportName + "$VectorMaskOp");

        r.register(new InlineOnlyInvocationPlugin("fromBitsCoerced", Class.class, Class.class, int.class, long.class, int.class, vectorSpecies, fromBitsCoercedOperation) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode vmClass, ValueNode eClass, ValueNode length, ValueNode bits, ValueNode mode,
                            ValueNode s, ValueNode defaultImpl) {
                ObjectStamp speciesStamp = VectorAPIUtils.nonNullStampForClassValue(b, vmClass);
                StampPair returnStamp = makeReturnStamp(b, speciesStamp);
                MacroParams params = MacroParams.of(b, targetMethod, returnStamp, vmClass, eClass, length, bits, mode, s, defaultImpl);
                b.addPush(JavaKind.Object, VectorAPIFromBitsCoercedNode.create(params, b));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("indexPartiallyInUpperRange", Class.class, Class.class, int.class, long.class, long.class, indexPartiallyInUpperRangeOp) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode mClass, ValueNode eClass, ValueNode length, ValueNode offset, ValueNode limit,
                            ValueNode defaultImpl) {
                ObjectStamp speciesStamp = VectorAPIUtils.nonNullStampForClassValue(b, mClass);
                StampPair returnStamp = makeReturnStamp(b, speciesStamp);
                MacroParams params = MacroParams.of(b, targetMethod, returnStamp, mClass, eClass, length, offset, limit, defaultImpl);
                b.addPush(JavaKind.Object, VectorAPIIndexPartiallyInUpperRangeNode.create(params, b));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("reductionCoerced", int.class, Class.class, Class.class, Class.class, int.class, vector, vectorMask, reductionOperation) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode oprId, ValueNode vClass, ValueNode mClass, ValueNode eClass, ValueNode length,
                            ValueNode v, ValueNode m, ValueNode defaultImpl) {
                if (maskIsPresent(m)) {
                    /* Masked operations are not supported yet. */
                    return false;
                }
                MacroParams params = MacroParams.of(b, targetMethod, oprId, vClass, mClass, eClass, length, b.nullCheckedValue(v), m, defaultImpl);
                b.addPush(JavaKind.Long, VectorAPIReductionCoercedNode.create(params, b));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("extract", Class.class, Class.class, int.class, vectorPayload, int.class, extractOp) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode vmClass, ValueNode eClass, ValueNode length, ValueNode vm, ValueNode i,
                            ValueNode defaultImpl) {
                MacroParams params = MacroParams.of(b, targetMethod, vmClass, eClass, length, b.nullCheckedValue(vm), i, defaultImpl);
                b.addPush(JavaKind.Long, VectorAPIExtractNode.create(params, VectorAPIType.ofConstant(vmClass, b)));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("insert", Class.class, Class.class, int.class, vector, int.class, long.class, insertOp) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode vClass, ValueNode eClass, ValueNode length, ValueNode v, ValueNode i, ValueNode val,
                            ValueNode defaultImpl) {
                ObjectStamp speciesStamp = VectorAPIUtils.nonNullStampForClassValue(b, vClass);
                StampPair returnStamp = makeReturnStamp(b, speciesStamp);
                MacroParams params = MacroParams.of(b, targetMethod, returnStamp, vClass, eClass, length, b.nullCheckedValue(v), i, val, defaultImpl);
                b.addPush(JavaKind.Object, VectorAPIInsertNode.create(params, b));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("unaryOp", int.class, Class.class, Class.class, Class.class, int.class, vector, vectorMask, unaryOperation) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode oprId, ValueNode vClass, ValueNode mClass, ValueNode eClass, ValueNode length,
                            ValueNode v, ValueNode m, ValueNode defaultImpl) {
                ObjectStamp speciesStamp = VectorAPIUtils.nonNullStampForClassValue(b, vClass);
                StampPair returnStamp = makeReturnStamp(b, speciesStamp);
                ValueNode mask = m.isNullConstant() ? m : b.nullCheckedValue(m);
                MacroParams params = MacroParams.of(b, targetMethod, returnStamp, oprId, vClass, mClass, eClass, length, b.nullCheckedValue(v), mask, defaultImpl);
                b.addPush(JavaKind.Object, VectorAPIUnaryOpNode.create(params, b));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("binaryOp", int.class, Class.class, Class.class, Class.class, int.class, vectorPayload, vectorPayload, vectorMask, binaryOperation) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode oprId, ValueNode vmClass, ValueNode mClass, ValueNode eClass, ValueNode length,
                            ValueNode v1, ValueNode v2, ValueNode m, ValueNode defaultImpl) {
                ObjectStamp speciesStamp = VectorAPIUtils.nonNullStampForClassValue(b, vmClass);
                StampPair returnStamp = makeReturnStamp(b, speciesStamp);
                ValueNode mask = m.isNullConstant() ? m : b.nullCheckedValue(m);
                MacroParams params = MacroParams.of(b, targetMethod, returnStamp, oprId, vmClass, mClass, eClass, length, b.nullCheckedValue(v1), b.nullCheckedValue(v2), mask, defaultImpl);
                b.addPush(JavaKind.Object, VectorAPIBinaryOpNode.create(params, b));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("ternaryOp", int.class, Class.class, Class.class, Class.class, int.class, vector, vector, vector, vectorMask, ternaryOperation) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode oprId, ValueNode vClass, ValueNode mClass, ValueNode eClass, ValueNode length,
                            ValueNode v1, ValueNode v2, ValueNode v3, ValueNode m, ValueNode defaultImpl) {
                ObjectStamp speciesStamp = VectorAPIUtils.nonNullStampForClassValue(b, vClass);
                StampPair returnStamp = makeReturnStamp(b, speciesStamp);
                ValueNode mask = m.isNullConstant() ? m : b.nullCheckedValue(m);
                MacroParams params = MacroParams.of(b, targetMethod, returnStamp, oprId, vClass, mClass, eClass, length, b.nullCheckedValue(v1), b.nullCheckedValue(v2), b.nullCheckedValue(v3), mask,
                                defaultImpl);
                b.addPush(JavaKind.Object, VectorAPITernaryOpNode.create(params, b));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("load", Class.class, Class.class, int.class, Object.class, long.class, boolean.class, Object.class, long.class, vectorSpecies, loadOperation) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode vmClass, ValueNode eClass, ValueNode length, ValueNode base, ValueNode offset,
                            ValueNode fromSegment, ValueNode container, ValueNode index, ValueNode s, ValueNode defaultImpl) {
                ObjectStamp speciesStamp = VectorAPIUtils.nonNullStampForClassValue(b, vmClass);
                VectorAPIType loadType = VectorAPIType.ofConstant(vmClass, b);
                AddressNode address = VectorAPIUtils.buildAddress(base, offset, container, index);
                LocationIdentity location = VectorAPIUtils.containerLocationIdentity(container);
                if (!fromSegment.isDefaultConstant()) {
                    GraalError.guarantee(location.isAny(), "we don't know anything about the location of a possible memory segment access");
                }
                StampPair returnStamp = makeReturnStamp(b, speciesStamp);
                MacroParams params = MacroParams.of(b, targetMethod, returnStamp, vmClass, eClass, length, base, offset, fromSegment, container, index, s, defaultImpl);
                b.addPush(JavaKind.Object, VectorAPILoadNode.create(params, loadType, address, location, b));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("loadMasked", Class.class, Class.class, Class.class, int.class, Object.class, long.class, boolean.class, vectorMask, int.class, Object.class,
                        long.class, vectorSpecies, loadMaskedOperation) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode vClass, ValueNode mClass, ValueNode eClass, ValueNode length, ValueNode base,
                            ValueNode offset, ValueNode fromSegment, ValueNode m, ValueNode offsetInRange, ValueNode container, ValueNode index, ValueNode s, ValueNode defaultImpl) {
                ObjectStamp speciesStamp = VectorAPIUtils.nonNullStampForClassValue(b, vClass);
                VectorAPIType loadType = VectorAPIType.ofConstant(vClass, b);
                AddressNode address = VectorAPIUtils.buildAddress(base, offset, container, index);
                LocationIdentity location = VectorAPIUtils.containerLocationIdentity(container);
                if (!fromSegment.isDefaultConstant()) {
                    GraalError.guarantee(location.isAny(), "we don't know anything about the location of a possible memory segment access");
                }
                StampPair returnStamp = makeReturnStamp(b, speciesStamp);
                MacroParams params = MacroParams.of(b, targetMethod, returnStamp, vClass, mClass, eClass, length, base, offset, fromSegment, m, offsetInRange, container, index, s, defaultImpl);
                b.addPush(JavaKind.Object, VectorAPILoadMaskedNode.create(params, loadType, address, location, b));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("store", Class.class, Class.class, int.class, Object.class, long.class, boolean.class, vectorPayload, Object.class, long.class,
                        storeVectorOperation) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode vClass, ValueNode eClass, ValueNode length, ValueNode base, ValueNode offset,
                            ValueNode fromSegment, ValueNode v, ValueNode container, ValueNode index, ValueNode defaultImpl) {
                SimdStamp inputStamp = VectorAPIUtils.stampForVectorClass(vClass, eClass, length, b);
                VectorAPIType storeType = VectorAPIType.ofConstant(vClass, b);
                AddressNode address = VectorAPIUtils.buildAddress(base, offset, container, index);
                LocationIdentity location = VectorAPIUtils.containerLocationIdentity(container);
                if (!fromSegment.isDefaultConstant()) {
                    GraalError.guarantee(location.isAny(), "we don't know anything about the location of a possible memory segment access");
                }
                MacroParams params = MacroParams.of(b, targetMethod, vClass, eClass, length, base, offset, fromSegment, b.nullCheckedValue(v), container, index, defaultImpl);
                b.add(new VectorAPIStoreNode(params, inputStamp, storeType, address, location));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("storeMasked", Class.class, Class.class, Class.class, int.class, Object.class, long.class, boolean.class, vector, vectorMask, Object.class,
                        long.class, storeMaskedOperation) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode vClass, ValueNode mClass, ValueNode eClass, ValueNode length, ValueNode base,
                            ValueNode offset, ValueNode fromSegment, ValueNode v, ValueNode m, ValueNode container, ValueNode index, ValueNode defaultImpl) {
                VectorAPIType storeType = VectorAPIType.ofConstant(vClass, b);
                AddressNode address = VectorAPIUtils.buildAddress(base, offset, container, index);
                LocationIdentity location = VectorAPIUtils.containerLocationIdentity(container);
                if (!fromSegment.isDefaultConstant()) {
                    GraalError.guarantee(location.isAny(), "we don't know anything about the location of a possible memory segment access");
                }
                MacroParams params = MacroParams.of(b, targetMethod, vClass, mClass, eClass, length, base, offset, fromSegment, b.nullCheckedValue(v), b.nullCheckedValue(m), container, index,
                                defaultImpl);
                b.add(VectorAPIStoreMaskedNode.create(params, storeType, address, location));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("test", int.class, Class.class, Class.class, int.class, vectorMask, vectorMask, BiFunction.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode cond, ValueNode mClass, ValueNode eClass, ValueNode length, ValueNode m1,
                            ValueNode m2, ValueNode defaultImpl) {
                ObjectStamp speciesStamp = VectorAPIUtils.nonNullStampForClassValue(b, mClass);
                if (speciesStamp == null) {
                    return false;
                }
                SimdStamp maskStamp = VectorAPIUtils.stampForMaskClass(mClass, eClass, length, b);
                if (maskStamp == null) {
                    return false;
                }
                MacroParams params = MacroParams.of(b, targetMethod, cond, mClass, eClass, length, b.nullCheckedValue(m1), b.nullCheckedValue(m2), defaultImpl);
                b.addPush(JavaKind.Boolean, VectorAPITestNode.create(params));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("compare", int.class, Class.class, Class.class, Class.class, int.class, vector, vector, vectorMask, vectorCompareOp) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode cond, ValueNode vectorClass, ValueNode mClass, ValueNode eClass, ValueNode length,
                            ValueNode v1, ValueNode v2, ValueNode m, ValueNode defaultImpl) {
                ObjectStamp maskStamp = VectorAPIUtils.nonNullStampForClassValue(b, mClass);
                StampPair returnStamp = makeReturnStamp(b, maskStamp);
                ValueNode mask = m.isNullConstant() ? m : b.nullCheckedValue(m);
                MacroParams params = MacroParams.of(b, targetMethod, returnStamp, cond, vectorClass, mClass, eClass, length, b.nullCheckedValue(v1), b.nullCheckedValue(v2), mask, defaultImpl);
                b.addPush(JavaKind.Object, VectorAPICompareNode.create(params, b));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("blend", Class.class, Class.class, Class.class, int.class, vector, vector, vectorMask, vectorBlendOp) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode vClass, ValueNode mClass, ValueNode eClass, ValueNode length, ValueNode v1,
                            ValueNode v2, ValueNode m, ValueNode defaultImpl) {
                ObjectStamp speciesStamp = VectorAPIUtils.nonNullStampForClassValue(b, vClass);
                StampPair returnStamp = makeReturnStamp(b, speciesStamp);
                MacroParams params = MacroParams.of(b, targetMethod, returnStamp, vClass, mClass, eClass, length, b.nullCheckedValue(v1), b.nullCheckedValue(v2), b.nullCheckedValue(m), defaultImpl);
                b.addPush(JavaKind.Object, VectorAPIBlendNode.create(params, b));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("broadcastInt", int.class, Class.class, Class.class, Class.class, int.class, vector, int.class, vectorMask, vectorBroadcastIntOp) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode opr, ValueNode vClass, ValueNode mClass, ValueNode eClass, ValueNode length,
                            ValueNode v, ValueNode n, ValueNode m, ValueNode defaultImpl) {
                if (maskIsPresent(m)) {
                    /* Masked operations are not supported yet. */
                    return false;
                }
                ObjectStamp speciesStamp = VectorAPIUtils.nonNullStampForClassValue(b, vClass);
                StampPair returnStamp = makeReturnStamp(b, speciesStamp);
                MacroParams params = MacroParams.of(b, targetMethod, returnStamp, opr, vClass, mClass, eClass, length, b.nullCheckedValue(v), n, m, defaultImpl);
                b.addPush(JavaKind.Object, VectorAPIBroadcastIntNode.create(params, b));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("convert", int.class, Class.class, Class.class, int.class, Class.class, Class.class, int.class, vectorPayload, vectorSpecies, vectorConvertOp) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode oprId, ValueNode fromVectorClass, ValueNode fromeClass, ValueNode fromVLen,
                            ValueNode toVectorClass, ValueNode toeClass, ValueNode toVLen, ValueNode v, ValueNode s, ValueNode defaultImpl) {
                ObjectStamp speciesStamp = VectorAPIUtils.nonNullStampForClassValue(b, fromVectorClass);
                if (speciesStamp == null) {
                    return false;
                }
                ObjectStamp destinationStamp = VectorAPIUtils.nonNullStampForClassValue(b, toVectorClass);
                StampPair returnStamp = makeReturnStamp(b, destinationStamp);
                MacroParams params = MacroParams.of(b, targetMethod, returnStamp, oprId, fromVectorClass, fromeClass, fromVLen, toVectorClass, toeClass, toVLen, b.nullCheckedValue(v), s, defaultImpl);
                b.addPush(JavaKind.Object, VectorAPIConvertNode.create(params, b));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("rearrangeOp", Class.class, Class.class, Class.class, Class.class, int.class, vector, vectorShuffle, vectorMask, vectorRearrangeOp) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode vClass, ValueNode shClass, ValueNode mClass, ValueNode eClass, ValueNode length,
                            ValueNode v, ValueNode sh, ValueNode m, ValueNode defaultImpl) {
                ObjectStamp speciesStamp = VectorAPIUtils.nonNullStampForClassValue(b, vClass);
                StampPair returnStamp = makeReturnStamp(b, speciesStamp);
                ValueNode mask = m.isNullConstant() ? m : b.nullCheckedValue(m);
                MacroParams params = MacroParams.of(b, targetMethod, returnStamp, vClass, shClass, mClass, eClass, length, b.nullCheckedValue(v), b.nullCheckedValue(sh), mask, defaultImpl);
                b.addPush(JavaKind.Object, VectorAPIRearrangeOpNode.create(params, b));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("compressExpandOp", int.class, Class.class, Class.class, Class.class, int.class, vector, vectorMask, compressExpandOperation) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode opr, ValueNode vClass, ValueNode mClass, ValueNode eClass, ValueNode length,
                            ValueNode v, ValueNode m, ValueNode defaultImpl) {
                MacroParams params = MacroParams.of(b, targetMethod, opr, vClass, mClass, eClass, length, v.isNullConstant() ? v : b.nullCheckedValue(v), b.nullCheckedValue(m), defaultImpl);
                b.addPush(JavaKind.Object, VectorAPICompressExpandOpNode.create(params, b));
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("maskReductionCoerced", int.class, Class.class, Class.class, int.class, vectorMask, vectorMaskOp) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver type, ValueNode oper, ValueNode mClass, ValueNode eClass, ValueNode length, ValueNode m,
                            ValueNode defaultImpl) {
                MacroParams params = MacroParams.of(b, targetMethod, oper, mClass, eClass, length, b.nullCheckedValue(m), defaultImpl);
                b.addPush(JavaKind.Long, VectorAPIMaskReductionCoercedNode.create(params));
                return true;
            }
        });
    }

    /**
     * Builds the return stamp for an intrinsic. If the {@code speciesStamp} is known from the
     * context, it is used. Otherwise, the invoke's declared return stamp is used. In the latter
     * case, the intrinsic node must later try to canonicalize itself with a more precise species
     * stamp.
     */
    private static StampPair makeReturnStamp(GraphBuilderContext b, ObjectStamp speciesStamp) {
        return speciesStamp != null ? StampPair.createSingle(speciesStamp) : b.getInvokeReturnStamp(b.getAssumptions());
    }

    /** Return {@code true} if the provided {@code mask} value is not a null pointer constant. */
    private static boolean maskIsPresent(ValueNode mask) {
        boolean maskAbsent = mask.isJavaConstant() && mask.asJavaConstant().isNull();
        return !maskAbsent;
    }
}
