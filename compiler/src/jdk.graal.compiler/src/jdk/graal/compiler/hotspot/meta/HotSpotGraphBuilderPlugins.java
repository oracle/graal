/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.meta;

import static jdk.graal.compiler.hotspot.HotSpotBackend.ARRAY_PARTITION;
import static jdk.graal.compiler.hotspot.HotSpotBackend.ARRAY_SORT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.BASE64_DECODE_BLOCK;
import static jdk.graal.compiler.hotspot.HotSpotBackend.BASE64_ENCODE_BLOCK;
import static jdk.graal.compiler.hotspot.HotSpotBackend.CHACHA20Block;
import static jdk.graal.compiler.hotspot.HotSpotBackend.CRC_TABLE_LOCATION;
import static jdk.graal.compiler.hotspot.HotSpotBackend.DILITHIUM_ALMOST_INVERSE_NTT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.DILITHIUM_ALMOST_NTT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.DILITHIUM_DECOMPOSE_POLY;
import static jdk.graal.compiler.hotspot.HotSpotBackend.DILITHIUM_MONT_MUL_BY_CONSTANT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.DILITHIUM_NTT_MULT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.DOUBLE_KECCAK;
import static jdk.graal.compiler.hotspot.HotSpotBackend.ELECTRONIC_CODEBOOK_DECRYPT_AESCRYPT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.ELECTRONIC_CODEBOOK_ENCRYPT_AESCRYPT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.GALOIS_COUNTER_MODE_CRYPT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.INTPOLY_ASSIGN;
import static jdk.graal.compiler.hotspot.HotSpotBackend.INTPOLY_MONTGOMERYMULT_P256;
import static jdk.graal.compiler.hotspot.HotSpotBackend.KYBER_12_TO_16;
import static jdk.graal.compiler.hotspot.HotSpotBackend.KYBER_ADD_POLY_2;
import static jdk.graal.compiler.hotspot.HotSpotBackend.KYBER_ADD_POLY_3;
import static jdk.graal.compiler.hotspot.HotSpotBackend.KYBER_BARRETT_REDUCE;
import static jdk.graal.compiler.hotspot.HotSpotBackend.KYBER_INVERSE_NTT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.KYBER_NTT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.KYBER_NTT_MULT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.POLY1305_PROCESSBLOCKS;
import static jdk.graal.compiler.hotspot.HotSpotBackend.SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_END;
import static jdk.graal.compiler.hotspot.HotSpotBackend.SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_MOUNT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_START;
import static jdk.graal.compiler.hotspot.HotSpotBackend.SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_UNMOUNT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.UPDATE_BYTES_CRC32;
import static jdk.graal.compiler.hotspot.HotSpotBackend.UPDATE_BYTES_CRC32C;
import static jdk.graal.compiler.hotspot.replacements.HotSpotInvocationPluginHelper.HotSpotVMConfigField.HOTSPOT_CONTINUATION_ENTRY_PIN_COUNT;
import static jdk.graal.compiler.hotspot.replacements.HotSpotInvocationPluginHelper.HotSpotVMConfigField.HOTSPOT_JAVA_THREAD_CONT_ENTRY;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.HOTSPOT_CARRIER_THREAD_OOP_HANDLE_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.HOTSPOT_CONTINUATION_ENTRY_PIN_COUNT_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.HOTSPOT_CURRENT_THREAD_OOP_HANDLE_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.HOTSPOT_JAVA_THREAD_SCOPED_VALUE_CACHE_HANDLE_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_MONITOR_OWNER_ID_LOCATION;
import static jdk.graal.compiler.java.BytecodeParserOptions.InlineDuringParsing;
import static jdk.graal.compiler.nodes.ConstantNode.forBoolean;
import static jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData.injected;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static jdk.graal.compiler.replacements.StandardGraphBuilderPlugins.VectorizedHashCodeInvocationPlugin.T_DOUBLE;
import static jdk.graal.compiler.replacements.StandardGraphBuilderPlugins.VectorizedHashCodeInvocationPlugin.T_FLOAT;
import static jdk.graal.compiler.replacements.StandardGraphBuilderPlugins.VectorizedHashCodeInvocationPlugin.T_INT;
import static jdk.graal.compiler.replacements.StandardGraphBuilderPlugins.VectorizedHashCodeInvocationPlugin.T_LONG;
import static jdk.vm.ci.meta.DeoptimizationReason.TypeCheckedInliningViolated;

import java.lang.annotation.Annotation;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.VolatileCallSite;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.zip.CRC32;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotBackend;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.hotspot.nodes.CurrentJavaThreadNode;
import jdk.graal.compiler.hotspot.nodes.HotSpotLoadReservedReferenceNode;
import jdk.graal.compiler.hotspot.nodes.HotSpotStoreReservedReferenceNode;
import jdk.graal.compiler.hotspot.nodes.KlassFullyInitializedCheckNode;
import jdk.graal.compiler.hotspot.nodes.VirtualThreadUpdateJFRNode;
import jdk.graal.compiler.hotspot.replacements.CallSiteTargetNode;
import jdk.graal.compiler.hotspot.replacements.DigestBaseSnippets;
import jdk.graal.compiler.hotspot.replacements.FastNotifyNode;
import jdk.graal.compiler.hotspot.replacements.HotSpotIdentityHashCodeNode;
import jdk.graal.compiler.hotspot.replacements.HotSpotInvocationPluginHelper;
import jdk.graal.compiler.hotspot.replacements.HotSpotReflectionGetCallerClassNode;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.hotspot.replacements.HubGetClassNode;
import jdk.graal.compiler.hotspot.replacements.ObjectCloneNode;
import jdk.graal.compiler.hotspot.replacements.UnsafeCopyMemoryNode;
import jdk.graal.compiler.hotspot.replacements.UnsafeSetMemoryNode;
import jdk.graal.compiler.hotspot.replaycomp.ReplayCompilationSupport;
import jdk.graal.compiler.hotspot.word.HotSpotWordTypes;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ComputeObjectAddressNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IntegerTestNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.ObjectEqualsNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.calc.XorNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.extended.JavaWriteNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.extended.ObjectIsArrayNode;
import jdk.graal.compiler.nodes.extended.PublishWritesNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.graphbuilderconf.ForeignCallPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedPluginFactory;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.ConditionalInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.InlineOnlyConditionalInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.InlineOnlyInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.DynamicNewArrayNode;
import jdk.graal.compiler.nodes.java.DynamicNewInstanceNode;
import jdk.graal.compiler.nodes.java.DynamicNewInstanceWithExceptionNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.ValidateNewInstanceClassNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.CompilerConfiguration;
import jdk.graal.compiler.replacements.InlineDuringParsingPlugin;
import jdk.graal.compiler.replacements.IntrinsicGraphBuilder;
import jdk.graal.compiler.replacements.InvocationPluginHelper;
import jdk.graal.compiler.replacements.MethodHandlePlugin;
import jdk.graal.compiler.replacements.NodeIntrinsificationProvider;
import jdk.graal.compiler.replacements.ReplacementsImpl;
import jdk.graal.compiler.replacements.SnippetSubstitutionInvocationPlugin;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.StandardGraphBuilderPlugins;
import jdk.graal.compiler.replacements.StandardGraphBuilderPlugins.AESCryptDelegatePlugin;
import jdk.graal.compiler.replacements.StandardGraphBuilderPlugins.CounterModeCryptPlugin;
import jdk.graal.compiler.replacements.StandardGraphBuilderPlugins.ReachabilityFencePlugin;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyCallNode;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyForeignCalls;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopySnippets;
import jdk.graal.compiler.replacements.nodes.AESNode.CryptMode;
import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIIntrinsics;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Defines the {@link Plugins} used when running on HotSpot.
 */
public class HotSpotGraphBuilderPlugins {

    public static class Options {
        @Option(help = "Force an explicit compiler node for Reference.reachabilityFence, instead of relying on FrameState liveness", type = OptionType.Debug) //
        public static final OptionKey<Boolean> ForceExplicitReachabilityFence = new OptionKey<>(false);
    }

    /**
     * Creates a {@link Plugins} object that should be used when running on HotSpot.
     */
    @SuppressWarnings("try")
    public static Plugins create(HotSpotGraalRuntimeProvider graalRuntime,
                    CompilerConfiguration compilerConfiguration,
                    GraalHotSpotVMConfig config,
                    HotSpotWordTypes wordTypes,
                    MetaAccessProvider metaAccess,
                    ConstantReflectionProvider constantReflection,
                    SnippetReflectionProvider snippetReflection,
                    HotSpotHostForeignCallsProvider foreignCalls,
                    ReplacementsImpl replacements,
                    OptionValues options,
                    TargetDescription target,
                    BarrierSet barrierSet) {
        InvocationPlugins invocationPlugins = new HotSpotInvocationPlugins(graalRuntime, config, compilerConfiguration, options, target);

        Plugins plugins = new Plugins(invocationPlugins);
        plugins.appendNodePlugin(new HotSpotExceptionDispatchPlugin(config, wordTypes.getWordKind()));
        StandardGraphBuilderPlugins.registerConstantFieldLoadPlugin(plugins);
        if (!LibGraalSupport.inLibGraalRuntime()) {
            // In libgraal all word related operations have been fully processed so this is unneeded
            HotSpotWordOperationPlugin wordOperationPlugin = new HotSpotWordOperationPlugin(snippetReflection, constantReflection, wordTypes, barrierSet);
            HotSpotNodePlugin nodePlugin = new HotSpotNodePlugin(wordOperationPlugin);

            plugins.appendTypePlugin(nodePlugin);
            plugins.appendNodePlugin(nodePlugin);
        }
        plugins.appendNodePlugin(new MethodHandlePlugin(constantReflection.getMethodHandleAccess(), !config.supportsMethodHandleDeoptimizationEntry()));
        plugins.appendInlineInvokePlugin(replacements);
        if (InlineDuringParsing.getValue(options)) {
            plugins.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }

        if (config.instanceKlassInitThreadOffset != -1) {
            plugins.setClassInitializationPlugin(new HotSpotJITClassInitializationPlugin());
        }
        compilerConfiguration.registerGraphBuilderPlugins(target.arch, plugins, options);

        invocationPlugins.defer(new Runnable() {

            @Override
            public void run() {
                StandardGraphBuilderPlugins.registerInvocationPlugins(snippetReflection, invocationPlugins, true, false, true);

                registerObjectPlugins(invocationPlugins, config);
                registerClassPlugins(plugins, config);
                registerSystemPlugins(invocationPlugins);
                registerThreadPlugins(invocationPlugins, config, barrierSet);
                registerVirtualThreadPlugins(invocationPlugins, config);
                registerMathPlugins(invocationPlugins, target.arch);
                registerContinuationPlugins(invocationPlugins, config);
                registerCallSitePlugins(invocationPlugins);
                registerReflectionPlugins(invocationPlugins);
                registerAESPlugins(invocationPlugins, config);
                registerAdler32Plugins(invocationPlugins, config);
                registerCRC32Plugins(invocationPlugins, config);
                registerCRC32CPlugins(invocationPlugins, config);
                registerBigIntegerPlugins(invocationPlugins, config);
                registerSHAPlugins(invocationPlugins, config);
                registerMLPlugins(invocationPlugins, config);
                registerBase64Plugins(invocationPlugins, config, metaAccess);
                registerUnsafePlugins(invocationPlugins, config);
                registerArrayPlugins(invocationPlugins, config);
                registerStringPlugins(invocationPlugins, wordTypes, foreignCalls, config);
                registerArraysSupportPlugins(invocationPlugins, target.arch);
                registerReferencePlugins(invocationPlugins);
                registerTrufflePlugins(invocationPlugins, wordTypes, config);
                registerInstrumentationImplPlugins(invocationPlugins, config);
                registerPoly1305Plugins(invocationPlugins, config);
                registerChaCha20Plugins(invocationPlugins, config);
                registerP256Plugins(invocationPlugins, config);
                registerDualPivotQuicksortPlugins(invocationPlugins, config, target.arch);

                if (VectorAPIIntrinsics.intrinsificationSupported(options)) {
                    VectorAPIIntrinsics.registerPlugins(plugins.getInvocationPlugins());
                }
                for (HotSpotInvocationPluginProvider p : GraalServices.load(HotSpotInvocationPluginProvider.class)) {
                    p.registerInvocationPlugins(target.arch, plugins.getInvocationPlugins());
                }
            }

        });
        if (!LibGraalSupport.inLibGraalRuntime()) {
            // In libgraal, all NodeIntrinsics have already been converted into nodes.
            NodeIntrinsificationProvider nodeIntrinsificationProvider = new NodeIntrinsificationProvider(metaAccess, snippetReflection, foreignCalls, wordTypes, target);
            invocationPlugins.defer(() -> {
                try (DebugCloseable ignored = ReplayCompilationSupport.enterSnippetContext(graalRuntime.getReplayCompilationSupport())) {
                    for (GeneratedPluginFactory factory : GraalServices.load(GeneratedPluginFactory.class)) {
                        factory.registerPlugins(invocationPlugins, nodeIntrinsificationProvider);
                    }
                }
            });
        }
        return plugins;
    }

    private static void registerTrufflePlugins(InvocationPlugins plugins, WordTypes wordTypes, GraalHotSpotVMConfig config) {
        plugins.registerIntrinsificationPredicate(t -> t.getName().equals("Lcom/oracle/truffle/runtime/hotspot/HotSpotFastThreadLocal;"));
        Registration tl = new Registration(plugins, "com.oracle.truffle.runtime.hotspot.HotSpotFastThreadLocal");
        tl.register(new InvocationPlugin("get", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                receiver.get(true);
                int jvmciReservedReference0Offset = config.jvmciReservedReference0Offset;
                GraalError.guarantee(jvmciReservedReference0Offset != -1, "jvmciReservedReference0Offset is not available but used.");
                b.addPush(JavaKind.Object, new HotSpotLoadReservedReferenceNode(b.getMetaAccess(), wordTypes, jvmciReservedReference0Offset));
                return true;
            }
        });
        tl.register(new InvocationPlugin("set", Receiver.class, Object[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                receiver.get(true);
                int jvmciReservedReference0Offset = config.jvmciReservedReference0Offset;
                GraalError.guarantee(jvmciReservedReference0Offset != -1, "jvmciReservedReference0Offset is not available but used.");
                b.add(new HotSpotStoreReservedReferenceNode(wordTypes, value, jvmciReservedReference0Offset));
                return true;
            }
        });
    }

    private static void registerObjectPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, Object.class);
        r.register(new InlineOnlyInvocationPlugin("clone", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get(true);
                b.addPush(JavaKind.Object, new ObjectCloneNode(MacroParams.of(b, targetMethod, object)));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("hashCode", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get(true);
                b.addPush(JavaKind.Int, new HotSpotIdentityHashCodeNode(object, b.bci()));
                return true;
            }
        });
        r.register(new InlineOnlyConditionalInvocationPlugin("notify", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get(true);
                b.add(new FastNotifyNode(object, false, b.bci()));
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.inlineNotify();
            }
        });
        r.register(new InlineOnlyConditionalInvocationPlugin("notifyAll", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get(true);
                b.add(new FastNotifyNode(object, true, b.bci()));
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.inlineNotifyAll();
            }
        });
    }

    private static void registerClassPlugins(Plugins plugins, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins.getInvocationPlugins(), Class.class);

        r.register(new InvocationPlugin("getSuperclass", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    ValueNode klass = helper.readKlassFromClass(receiver.get(true));
                    ConstantNode nullValue = ConstantNode.defaultForKind(JavaKind.Object);

                    // Primitive Class case returns null
                    PiNode klassNonNull = helper.emitNullReturnGuard(klass, nullValue, GraalDirectives.UNLIKELY_PROBABILITY);

                    // if ((Klass::_access_flags & Modifer.INTERFACE) != 0) return null
                    ValueNode accessFlags = helper.readKlassAccessFlags(klassNonNull);
                    LogicNode test = IntegerTestNode.create(accessFlags, ConstantNode.forInt(Modifier.INTERFACE), NodeView.DEFAULT);
                    helper.emitReturnIfNot(test, nullValue, GraalDirectives.UNLIKELY_PROBABILITY);

                    // Handle array Class case
                    // if (Klass::_layout_helper < 0) return Object.class
                    ValueNode layoutHelper = helper.klassLayoutHelper(klassNonNull);
                    ResolvedJavaType objectType = b.getMetaAccess().lookupJavaType(Object.class);
                    ValueNode objectClass = ConstantNode.forConstant(b.getConstantReflection().asJavaClass(objectType), b.getMetaAccess());
                    helper.emitReturnIf(layoutHelper, Condition.LT, ConstantNode.forInt(config.klassLayoutHelperNeutralValue), objectClass,
                                    GraalDirectives.UNLIKELY_PROBABILITY);

                    // Read Klass::_super
                    ValueNode superKlass = helper.readKlassSuperKlass(klassNonNull);
                    // Return null if super is null
                    PiNode superKlassNonNull = helper.emitNullReturnGuard(superKlass, nullValue, GraalDirectives.UNLIKELY_PROBABILITY);
                    // Convert Klass to Class and return
                    helper.emitFinalReturn(JavaKind.Object, new HubGetClassNode(b.getMetaAccess(), superKlassNonNull));
                }
                return true;
            }
        });

        r.register(new InvocationPlugin("isHidden", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    ValueNode klass = helper.readKlassFromClass(receiver.get(true));
                    // Primitive Class case returns false
                    ValueNode nonNullKlass = helper.emitNullReturnGuard(klass, ConstantNode.forBoolean(false), GraalDirectives.UNLIKELY_PROBABILITY);
                    // return (Klass::_misc_flags & jvmAccIsHiddenClass) != 0
                    ValueNode flags = helper.readKlassMiscFlags(nonNullKlass);
                    LogicNode test = IntegerTestNode.create(flags, ConstantNode.forInt(config.jvmAccIsHiddenClass), NodeView.DEFAULT);
                    helper.emitFinalReturn(JavaKind.Boolean, ConditionalNode.create(test, ConstantNode.forBoolean(false), ConstantNode.forBoolean(true), NodeView.DEFAULT));
                }
                return true;
            }
        });
    }

    private static void registerMathPlugins(InvocationPlugins plugins, Architecture architecture) {
        Registration r = new Registration(plugins, Math.class);
        // On AArch64, the following Math plugins call into ShareRuntime utilities. While on AMD64,
        // the relevant Math plugins call into ported stubs.
        if (architecture instanceof AArch64) {
            registerUnaryMath(r, "sin", UnaryMathIntrinsicNode.UnaryOperation.SIN);
            registerUnaryMath(r, "cos", UnaryMathIntrinsicNode.UnaryOperation.COS);
            registerUnaryMath(r, "tan", UnaryMathIntrinsicNode.UnaryOperation.TAN);
            registerUnaryMath(r, "exp", UnaryMathIntrinsicNode.UnaryOperation.EXP);
            registerUnaryMath(r, "log", UnaryMathIntrinsicNode.UnaryOperation.LOG);
            registerUnaryMath(r, "log10", UnaryMathIntrinsicNode.UnaryOperation.LOG10);
            r.register(new InlineOnlyInvocationPlugin("pow", double.class, double.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                    b.push(JavaKind.Double, b.append(BinaryMathIntrinsicNode.create(x, y, BinaryMathIntrinsicNode.BinaryOperation.POW)));
                    return true;
                }
            });
        }
    }

    private static void registerUnaryMath(Registration r, String name, UnaryMathIntrinsicNode.UnaryOperation operation) {
        r.register(new InlineOnlyInvocationPlugin(name, double.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(UnaryMathIntrinsicNode.create(value, operation)));
                return true;
            }
        });
    }

    private static void registerCallSitePlugins(InvocationPlugins plugins) {
        InvocationPlugin plugin = new InlineOnlyInvocationPlugin("getTarget", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode callSite = receiver.get(true);
                ValueNode folded = CallSiteTargetNode.tryFold(GraphUtil.originalValue(callSite, true), b.getMetaAccess(), b.getAssumptions());
                if (folded != null) {
                    b.addPush(JavaKind.Object, folded);
                } else {
                    b.addPush(JavaKind.Object, new CallSiteTargetNode(MacroParams.of(b, targetMethod, callSite)));
                }
                return true;
            }
        };
        plugins.register(ConstantCallSite.class, plugin);
        plugins.register(MutableCallSite.class, plugin);
        plugins.register(VolatileCallSite.class, plugin);
    }

    private static void registerReflectionPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, "jdk.internal.reflect.Reflection");
        r.register(new InlineOnlyInvocationPlugin("getCallerClass") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, new HotSpotReflectionGetCallerClassNode(MacroParams.of(b, targetMethod)));
                return true;
            }
        });
    }

    private static final SpeculationReasonGroup JVMTI_NOTIFY_ALLOCATE_INSTANCE = new SpeculationReasonGroup("JvmtiNotifyAllocateInstance");

    private static void registerUnsafePlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, "jdk.internal.misc.Unsafe");
        r.register(new InvocationPlugin("copyMemory0", Receiver.class, Object.class, long.class, Object.class, long.class, long.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode srcBase, ValueNode srcOffset, ValueNode destBase,
                            ValueNode destOffset, ValueNode bytes) {
                b.add(new UnsafeCopyMemoryNode(receiver.get(true), srcBase, srcOffset, destBase, destOffset, bytes));
                return true;
            }
        });

        r.register(new ConditionalInvocationPlugin("setMemory0", Receiver.class, Object.class, long.class, long.class, byte.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode obj, ValueNode offset, ValueNode bytes, ValueNode value) {
                b.add(new UnsafeSetMemoryNode(receiver.get(true), obj, offset, bytes, value));
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.unsafeSetMemory != 0L;
            }
        });
        r.register(new InvocationPlugin("allocateInstance", Receiver.class, Class.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode clazz) {
                if (config.shouldNotifyObjectAllocAddress != 0) {
                    SpeculationLog speculationLog = b.getGraph().getSpeculationLog();
                    SpeculationReason speculationReason = JVMTI_NOTIFY_ALLOCATE_INSTANCE.createSpeculationReason();
                    if (speculationLog == null || !speculationLog.maySpeculate(speculationReason)) {
                        return false;
                    }
                    try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                        OffsetAddressNode address = OffsetAddressNode.create(helper.asWord(config.shouldNotifyObjectAllocAddress));
                        ValueNode shouldPostVMObjectAlloc = b.add(new JavaReadNode(JavaKind.Int, address, LocationIdentity.ANY_LOCATION, BarrierType.NONE, MemoryOrderMode.PLAIN, false));
                        LogicNode testShouldPostVMObjectAlloc = IntegerEqualsNode.create(shouldPostVMObjectAlloc, ConstantNode.forInt(0), NodeView.DEFAULT);
                        FixedGuardNode guard = new FixedGuardNode(testShouldPostVMObjectAlloc, DeoptimizationReason.RuntimeConstraint, DeoptimizationAction.InvalidateRecompile,
                                        speculationLog.speculate(speculationReason), false);
                        b.add(guard);
                    }
                }
                /* Emits a null-check for the otherwise unused receiver. */
                unsafe.get(true);

                ValidateNewInstanceClassNode clazzLegal = b.add(new ValidateNewInstanceClassNode(clazz));
                /*
                 * Note that the provided clazz might not be initialized. The lowering snippet for
                 * KlassFullyInitializedCheckNode performs the necessary initialization check.
                 */
                b.add(new KlassFullyInitializedCheckNode(clazzLegal));

                if (b.currentBlockCatchesOOME()) {
                    b.addPush(JavaKind.Object, new DynamicNewInstanceWithExceptionNode(clazzLegal, true, true));
                } else {
                    b.addPush(JavaKind.Object, new DynamicNewInstanceNode(clazzLegal, true, true));
                }
                return true;
            }
        });
    }

    private static void registerSystemPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, System.class);
        r.register(new ForeignCallPlugin(HotSpotHostForeignCallsProvider.JAVA_TIME_MILLIS, "currentTimeMillis"));
        r.register(new ForeignCallPlugin(HotSpotHostForeignCallsProvider.JAVA_TIME_NANOS, "nanoTime"));
        r.register(new InlineOnlyInvocationPlugin("identityHashCode", Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.addPush(JavaKind.Int, new HotSpotIdentityHashCodeNode(object, b.bci()));
                return true;
            }
        });
        ArrayCopySnippets.registerSystemArraycopyPlugin(r);
    }

    private static void registerArrayPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, Array.class);
        r.setAllowOverwrite(true);
        r.register(new InvocationPlugin("newArray", Class.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode componentType, ValueNode length) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    // If (componentType == null) then take the fallback path
                    GuardingNode guard = helper.doFallbackIf(IsNullNode.create(componentType), GraalDirectives.UNLIKELY_PROBABILITY);
                    ValueNode nonNullComponentType = helper.piCast(componentType, guard, ((AbstractPointerStamp) componentType.stamp(NodeView.DEFAULT)).asNonNull());
                    // Read Class.array_klass
                    ValueNode arrayClass = helper.loadArrayKlass(nonNullComponentType);
                    // Take the fallback path if the array klass is null
                    helper.doFallbackIf(IsNullNode.create(arrayClass), GraalDirectives.UNLIKELY_PROBABILITY);
                    // Otherwise perform the array allocation
                    helper.emitFinalReturn(JavaKind.Object, new DynamicNewArrayNode(nonNullComponentType, length, true));
                }
                return true;
            }
        });
        r.register(StandardGraphBuilderPlugins.newArrayPlugin("newInstance"));
    }

    private static void registerStringPlugins(InvocationPlugins plugins, WordTypes wordTypes, ArrayCopyForeignCalls foreignCalls, GraalHotSpotVMConfig vmConfig) {
        final Registration utf16r = new Registration(plugins, "java.lang.StringUTF16");
        utf16r.register(new InvocationPlugin("toBytes", char[].class, int.class, int.class) {
            private static final int MAX_LENGTH = Integer.MAX_VALUE >> 1;

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value, ValueNode srcBegin, ValueNode length) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, vmConfig)) {
                    helper.intrinsicRangeCheck(srcBegin, Condition.LT, ConstantNode.forInt(0));
                    helper.intrinsicRangeCheck(length, Condition.LT, ConstantNode.forInt(0));
                    helper.intrinsicRangeCheck(length, Condition.GT, ConstantNode.forInt(MAX_LENGTH));
                    ValueNode valueLength = b.add(new ArrayLengthNode(value));
                    ValueNode limit = b.add(new SubNode(valueLength, length));
                    helper.intrinsicRangeCheck(srcBegin, Condition.GT, limit);
                    ValueNode newArray = new NewArrayNode(b.getMetaAccess().lookupJavaType(Byte.TYPE), b.add(new LeftShiftNode(length, ConstantNode.forInt(1))), false);
                    b.addPush(JavaKind.Object, newArray);
                    // The stateAfter should include the value pushed, so push it first and then
                    // perform the call that fills in the array.
                    b.add(new ArrayCopyCallNode(foreignCalls, wordTypes, value, srcBegin, newArray, ConstantNode.forInt(0), length, JavaKind.Char, LocationIdentity.init(), false, true, true,
                                    vmConfig.heapWordSize));

                    // Writes to init must be protected by a PublishWritesNode so that floating
                    // reads don't bypass the ArrayCopyCallNode above.
                    b.pop(JavaKind.Object);
                    b.addPush(JavaKind.Object, new PublishWritesNode(newArray));
                    b.add(MembarNode.forInitialization());
                }
                return true;
            }
        });
        utf16r.register(new InvocationPlugin("getChars", byte[].class, int.class, int.class, char[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value, ValueNode srcBegin, ValueNode srcEnd, ValueNode dst,
                            ValueNode dstBegin) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, vmConfig)) {
                    // The required test is illustrated below. This is a flattened version of the
                    // tests from the original sources
                    //
                    // @formatter:off
                    // if (srcBegin >= srcEnd) {
                    //   return;
                    // }
                    // int size = srcEnd - srcBegin;
                    // int length = value.length >> 1;
                    // if ((srcBegin | size) < 0 || size > length - srcBegin)
                    //   throw exception
                    // @formatter:on

                    helper.emitReturnIf(srcBegin, Condition.GE, srcEnd, null, BranchProbabilityNode.SLOW_PATH_PROBABILITY);
                    ValueNode size = helper.sub(srcEnd, srcBegin);
                    ValueNode or = helper.or(srcBegin, size);
                    helper.intrinsicRangeCheck(or, Condition.LT, ConstantNode.forInt(0));
                    ValueNode srcLimit = helper.sub(helper.shr(helper.length(value), 1), srcBegin);
                    helper.intrinsicRangeCheck(size, Condition.GT, srcLimit);
                    ValueNode limit = helper.sub(helper.length(dst), size);
                    helper.intrinsicRangeCheck(dstBegin, Condition.GT, limit);
                    b.add(new ArrayCopyCallNode(foreignCalls, wordTypes, value, srcBegin, dst, dstBegin, size, JavaKind.Char, JavaKind.Byte, JavaKind.Char, false, true, true,
                                    vmConfig.heapWordSize));
                    helper.emitFinalReturn(JavaKind.Void, null);
                }
                return true;
            }
        });
    }

    private static boolean isAnnotatedByChangesCurrentThread(ResolvedJavaMethod method) {
        for (Annotation annotation : method.getAnnotations()) {
            if ("jdk.internal.vm.annotation.ChangesCurrentThread".equals(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    private static AddressNode getScopedValueCacheAddress(GraphBuilderContext b, HotSpotInvocationPluginHelper helper) {
        CurrentJavaThreadNode javaThread = b.add(new CurrentJavaThreadNode(helper.getWordKind()));
        ValueNode scopedValueCacheHandle = helper.readJavaThreadScopedValueCache(javaThread);
        return b.add(OffsetAddressNode.create(scopedValueCacheHandle));
    }

    private static void registerThreadPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, BarrierSet barrierSet) {
        Registration r = new Registration(plugins, Thread.class);
        r.register(new InvocationPlugin("currentThread") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    CurrentJavaThreadNode thread = b.add(new CurrentJavaThreadNode(helper.getWordKind()));
                    ValueNode vthreadHandle = helper.readJavaThreadVthread(thread);
                    // Read the Object from the OopHandle
                    AddressNode handleAddress = b.add(OffsetAddressNode.create(vthreadHandle));
                    // JavaThread::_vthread is never compressed
                    ObjectStamp threadStamp = StampFactory.objectNonNull(TypeReference.create(b.getAssumptions(), b.getMetaAccess().lookupJavaType(Thread.class)));
                    ValueNode read = new ReadNode(handleAddress, HOTSPOT_CURRENT_THREAD_OOP_HANDLE_LOCATION, threadStamp,
                                    barrierSet.readBarrierType(HOTSPOT_CURRENT_THREAD_OOP_HANDLE_LOCATION, handleAddress, threadStamp), MemoryOrderMode.PLAIN);
                    b.addPush(JavaKind.Object, read);
                }
                return true;
            }
        });

        r.register(new InvocationPlugin("currentCarrierThread") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    CurrentJavaThreadNode thread = b.add(new CurrentJavaThreadNode(helper.getWordKind()));
                    ValueNode cthreadHandle = helper.readJavaThreadThreadObj(thread);
                    // Read the Object from the OopHandle
                    AddressNode handleAddress = b.add(OffsetAddressNode.create(cthreadHandle));
                    // JavaThread::_threadObj is never compressed
                    ObjectStamp threadStamp = StampFactory.objectNonNull(TypeReference.create(b.getAssumptions(), b.getMetaAccess().lookupJavaType(Thread.class)));
                    ValueNode read = new ReadNode(handleAddress, HOTSPOT_CARRIER_THREAD_OOP_HANDLE_LOCATION, threadStamp,
                                    barrierSet.readBarrierType(HOTSPOT_CARRIER_THREAD_OOP_HANDLE_LOCATION, handleAddress, threadStamp), MemoryOrderMode.PLAIN);
                    b.addPush(JavaKind.Object, read);
                }
                return true;
            }
        });

        r.register(new InlineOnlyInvocationPlugin("setCurrentThread", Receiver.class, Thread.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode thread) {
                GraalError.guarantee(LibGraalSupport.inLibGraalRuntime() || isAnnotatedByChangesCurrentThread(b.getMethod()),
                                "method changes current Thread but is not annotated ChangesCurrentThread");
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    receiver.get(true);
                    CurrentJavaThreadNode javaThread = b.add(new CurrentJavaThreadNode(helper.getWordKind()));
                    ValueNode threadObjectHandle = helper.readJavaThreadVthread(javaThread);
                    AddressNode handleAddress = b.add(OffsetAddressNode.create(threadObjectHandle));
                    b.add(new WriteNode(handleAddress, HOTSPOT_CURRENT_THREAD_OOP_HANDLE_LOCATION, thread,
                                    barrierSet.writeBarrierType(HOTSPOT_CURRENT_THREAD_OOP_HANDLE_LOCATION), MemoryOrderMode.PLAIN));

                    GraalError.guarantee(config.javaThreadMonitorOwnerIDOffset != -1, "JavaThread::_lock_id should have been exported");
                    // Change the lock_id of the JavaThread
                    ValueNode monitorOwnerID = helper.loadField(thread, helper.getField(b.getMetaAccess().lookupJavaType(Thread.class), "tid"));
                    OffsetAddressNode address = b.add(new OffsetAddressNode(javaThread, helper.asWord(config.javaThreadMonitorOwnerIDOffset)));
                    b.add(new JavaWriteNode(JavaKind.Long, address, JAVA_THREAD_MONITOR_OWNER_ID_LOCATION, monitorOwnerID, BarrierType.NONE, false));
                    if (HotSpotReplacementsUtil.supportsVirtualThreadUpdateJFR(config)) {
                        b.add(new VirtualThreadUpdateJFRNode(thread));
                    }
                }
                return true;
            }
        });
        r.register(new InvocationPlugin("scopedValueCache") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    AddressNode handleAddress = getScopedValueCacheAddress(b, helper);
                    ObjectStamp stamp = StampFactory.object(TypeReference.create(b.getAssumptions(), b.getMetaAccess().lookupJavaType(Object[].class)));
                    b.push(JavaKind.Object, b.add(new ReadNode(handleAddress, HOTSPOT_JAVA_THREAD_SCOPED_VALUE_CACHE_HANDLE_LOCATION, stamp,
                                    barrierSet.readBarrierType(HOTSPOT_JAVA_THREAD_SCOPED_VALUE_CACHE_HANDLE_LOCATION, handleAddress, stamp),
                                    MemoryOrderMode.PLAIN)));
                }
                return true;
            }
        });
        r.register(new InvocationPlugin("setScopedValueCache", Object[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode cache) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    AddressNode handleAddress = getScopedValueCacheAddress(b, helper);
                    b.add(new WriteNode(handleAddress, HOTSPOT_JAVA_THREAD_SCOPED_VALUE_CACHE_HANDLE_LOCATION, cache,
                                    barrierSet.writeBarrierType(HOTSPOT_JAVA_THREAD_SCOPED_VALUE_CACHE_HANDLE_LOCATION),
                                    MemoryOrderMode.PLAIN));
                }
                return true;
            }
        });
    }

    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/8e4485699235caff0074c4d25ee78539e57da63a/src/hotspot/share/opto/library_call.cpp#L3029-L3083",
              sha1 = "353e0d45b0f63ac58af86dcab5b19777950da7e2")
    // @formatter:on
    private static void inlineNativeNotifyJvmtiFunctions(GraalHotSpotVMConfig config, GraphBuilderContext b, ResolvedJavaMethod targetMethod, ForeignCallDescriptor descriptor,
                    ValueNode virtualThread, ValueNode hide) {
        // When notifications are disabled then just update the VTMS transition bit and return.
        // Otherwise, the bit is updated in the given function call implementing JVMTI notification
        // protocol.
        try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
            GraalError.guarantee(config.virtualThreadVTMSNotifyJvmtiEvents != -1L, "JvmtiVTMSTransitionDisabler::_VTMS_notify_jvmti_events is not exported");
            OffsetAddressNode address = OffsetAddressNode.create(helper.asWord(config.virtualThreadVTMSNotifyJvmtiEvents));
            ValueNode notifyJvmtiEnabled = b.add(new JavaReadNode(JavaKind.Boolean, address, HotSpotReplacementsUtil.HOTSPOT_VTMS_NOTIFY_JVMTI_EVENTS, BarrierType.NONE, MemoryOrderMode.PLAIN, false));
            LogicNode testNotifyJvmtiEnabled = IntegerEqualsNode.create(notifyJvmtiEnabled, ConstantNode.forBoolean(true), NodeView.DEFAULT);

            StructuredGraph graph = b.getGraph();

            CurrentJavaThreadNode javaThread = graph.addOrUniqueWithInputs(new CurrentJavaThreadNode(helper.getWordKind()));
            BeginNode trueSuccessor = graph.add(new BeginNode());
            BeginNode falseSuccessor = graph.add(new BeginNode());

            b.add(new IfNode(testNotifyJvmtiEnabled, trueSuccessor, falseSuccessor, injected(NOT_FREQUENT_PROBABILITY)));

            // if notifyJvmti enabled then make a call to the given runtime function
            ForeignCallNode runtimeCall = graph.add(new ForeignCallNode(descriptor, virtualThread, hide, javaThread));
            trueSuccessor.setNext(runtimeCall);
            runtimeCall.setStateAfter(b.getInvocationPluginReturnState(JavaKind.Void, null));
            EndNode trueSuccessorEnd = graph.add(new EndNode());
            runtimeCall.setNext(trueSuccessorEnd);

            // else set hide value to the VTMS transition bit in current JavaThread and
            // VirtualThread object
            GraalError.guarantee(config.javaThreadIsInVTMSTransitionOffset != -1L, "JavaThread::_is_in_VTMS_transition is not exported");
            OffsetAddressNode jtAddress = graph.addOrUniqueWithInputs(new OffsetAddressNode(javaThread, helper.asWord(config.javaThreadIsInVTMSTransitionOffset)));
            JavaWriteNode jtWrite = b.add(new JavaWriteNode(JavaKind.Boolean, jtAddress, HotSpotReplacementsUtil.HOTSPOT_JAVA_THREAD_IS_IN_VTMS_TRANSITION, hide, BarrierType.NONE, false));
            falseSuccessor.setNext(jtWrite);

            GraalError.guarantee(config.javaLangThreadIsInVTMSTransitonOffset != -1L, "java_lang_Thread::_jvmti_is_in_VTMS_transition_offset is not exported");
            OffsetAddressNode vtAddress = graph.addOrUniqueWithInputs(new OffsetAddressNode(virtualThread, helper.asWord(config.javaLangThreadIsInVTMSTransitonOffset)));
            b.add(new JavaWriteNode(JavaKind.Boolean, vtAddress, HotSpotReplacementsUtil.HOTSPOT_JAVA_LANG_THREAD_IS_IN_VTMS_TRANSITION, hide, BarrierType.NONE, false));

            EndNode falseSuccessorEnd = b.add(new EndNode());

            MergeNode merge = b.add(new MergeNode());
            merge.addForwardEnd(trueSuccessorEnd);
            merge.addForwardEnd(falseSuccessorEnd);
        }
    }

    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/8e4485699235caff0074c4d25ee78539e57da63a/src/hotspot/share/opto/library_call.cpp#L3848-L3932",
              sha1 = "3e9cfba4d9554f7cd9ab392f0826a31ae6396193")
    // @formatter:on
    private static class ContinuationPinningPlugin extends InvocationPlugin {

        private final GraalHotSpotVMConfig config;
        private final boolean pin;

        ContinuationPinningPlugin(GraalHotSpotVMConfig config, boolean pin) {
            super(pin ? "pin" : "unpin");
            this.config = config;
            this.pin = pin;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
            try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                // @formatter:off
                // if (currentThread::_cont_entry != null)
                //   uint pin_count = currentThread::_cont_entry::_pin_count;
                //   if (pin_count == (pin ? 0xFFFFFFFFUL : 0)) deoptimize;
                //   else currentThread::_cont_entry::_pin_count = pin_count + (pin ? 1 : -1);
                // @formatter:on
                StructuredGraph graph = b.getGraph();
                CurrentJavaThreadNode javaThread = graph.addOrUniqueWithInputs(new CurrentJavaThreadNode(helper.getWordKind()));

                ValueNode lastContinuation = helper.readLocation(javaThread, HOTSPOT_JAVA_THREAD_CONT_ENTRY);
                AbstractBeginNode guard = helper.emitReturnIf(IntegerEqualsNode.create(lastContinuation, helper.asWord(0), NodeView.DEFAULT), null, NOT_FREQUENT_PROBABILITY);

                ValueNode pinCount = helper.readLocation(lastContinuation, HOTSPOT_CONTINUATION_ENTRY_PIN_COUNT, guard);

                LogicNode overFlow = IntegerEqualsNode.create(pinCount, pin ? ConstantNode.forInt(-1) : ConstantNode.forInt(0), NodeView.DEFAULT);
                // TypeCheckedInliningViolated (Reason_type_checked_inlining) is aliasing
                // Reason_intrinsic
                b.append(new FixedGuardNode(overFlow, TypeCheckedInliningViolated, DeoptimizationAction.None, true));
                ValueNode newPinCount = b.add(AddNode.create(pinCount, pin ? ConstantNode.forInt(1) : ConstantNode.forInt(-1), NodeView.DEFAULT));
                b.add(new RawStoreNode(lastContinuation, helper.asWord(config.pinCountOffset), newPinCount, JavaKind.Int, HOTSPOT_CONTINUATION_ENTRY_PIN_COUNT_LOCATION));
                helper.emitFinalReturn(JavaKind.Void, null);
            }
            return true;
        }
    }

    private static void registerContinuationPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, "jdk.internal.vm.Continuation");
        r.register(new ContinuationPinningPlugin(config, true));
        r.register(new ContinuationPinningPlugin(config, false));
    }

    private static void registerVirtualThreadPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, "java.lang.VirtualThread");
        r.register(new InvocationPlugin("notifyJvmtiStart", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (config.doJVMTIVirtualThreadTransitions) {
                    ValueNode nonNullReceiver = receiver.get(true);
                    inlineNativeNotifyJvmtiFunctions(config, b, targetMethod, SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_START, nonNullReceiver, ConstantNode.forBoolean(false, b.getGraph()));
                }
                return true;
            }
        });
        r.register(new InvocationPlugin("notifyJvmtiEnd", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (config.doJVMTIVirtualThreadTransitions) {
                    ValueNode nonNullReceiver = receiver.get(true);
                    inlineNativeNotifyJvmtiFunctions(config, b, targetMethod, SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_END, nonNullReceiver, ConstantNode.forBoolean(true, b.getGraph()));
                }
                return true;
            }
        });
        r.register(new InvocationPlugin("notifyJvmtiMount", Receiver.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode hide) {
                if (config.doJVMTIVirtualThreadTransitions) {
                    ValueNode nonNullReceiver = receiver.get(true);
                    inlineNativeNotifyJvmtiFunctions(config, b, targetMethod, SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_MOUNT, nonNullReceiver, hide);
                }
                return true;
            }
        });
        r.register(new InvocationPlugin("notifyJvmtiUnmount", Receiver.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode hide) {
                if (config.doJVMTIVirtualThreadTransitions) {
                    ValueNode nonNullReceiver = receiver.get(true);
                    inlineNativeNotifyJvmtiFunctions(config, b, targetMethod, SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_UNMOUNT, nonNullReceiver, hide);
                }
                return true;
            }
        });
        r.register(new InvocationPlugin("notifyJvmtiDisableSuspend", boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode enter) {
                if (config.doJVMTIVirtualThreadTransitions) {
                    try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                        // unconditionally update the is_disable_suspend bit in current JavaThread
                        CurrentJavaThreadNode javaThread = b.add(new CurrentJavaThreadNode(helper.getWordKind()));
                        OffsetAddressNode address = b.add(new OffsetAddressNode(javaThread, helper.asWord(config.javaThreadIsDisableSuspendOffset)));
                        b.add(new JavaWriteNode(JavaKind.Boolean, address, HotSpotReplacementsUtil.HOTSPOT_JAVA_THREAD_IS_DISABLE_SUSPEND, enter, BarrierType.NONE, false));
                    }
                }
                return true;
            }
        });
    }

    private static ResolvedJavaType resolveTypeAESCrypt(ResolvedJavaType context) {
        return UnresolvedJavaType.create("Lcom/sun/crypto/provider/AESCrypt;").resolve(context);
    }

    public static class HotSpotCipherBlockChainingCryptPlugin extends StandardGraphBuilderPlugins.CipherBlockChainingCryptPlugin {

        HotSpotCipherBlockChainingCryptPlugin(CryptMode mode) {
            super(mode);
        }

        @Override
        protected boolean canApply(GraphBuilderContext b) {
            return b instanceof BytecodeParser || b instanceof IntrinsicGraphBuilder;
        }

        @Override
        protected ResolvedJavaType getTypeAESCrypt(MetaAccessProvider metaAccess, ResolvedJavaType context) {
            return resolveTypeAESCrypt(context);
        }
    }

    public abstract static class ElectronicCodeBookCryptPlugin extends AESCryptDelegatePlugin {

        ElectronicCodeBookCryptPlugin(CryptMode mode) {
            super(mode, mode.isEncrypt() ? "implECBEncrypt" : "implECBDecrypt",
                            Receiver.class, byte[].class, int.class, int.class, byte[].class, int.class);
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode in, ValueNode inOffset, ValueNode len, ValueNode out, ValueNode outOffset) {
            try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                ResolvedJavaType receiverType = targetMethod.getDeclaringClass();
                ResolvedJavaType typeAESCrypt = getTypeAESCrypt(b.getMetaAccess(), receiverType);

                ValueNode nonNullReceiver = receiver.get(true);
                ValueNode inAddr = helper.arrayElementPointer(in, JavaKind.Byte, inOffset);
                ValueNode outAddr = helper.arrayElementPointer(out, JavaKind.Byte, outOffset);
                ValueNode kAddr = readEmbeddedAESCryptKArrayStart(b, helper, receiverType, typeAESCrypt, nonNullReceiver);
                ForeignCallNode call = new ForeignCallNode(mode.isEncrypt() ? ELECTRONIC_CODEBOOK_ENCRYPT_AESCRYPT : ELECTRONIC_CODEBOOK_DECRYPT_AESCRYPT,
                                inAddr, outAddr, kAddr, len);
                helper.emitFinalReturn(JavaKind.Int, call);
                return true;
            }
        }

        @Override
        protected ResolvedJavaType getTypeAESCrypt(MetaAccessProvider metaAccess, ResolvedJavaType context) {
            return resolveTypeAESCrypt(context);
        }
    }

    public abstract static class GaloisCounterModeCryptPlugin extends AESCryptDelegatePlugin {

        GaloisCounterModeCryptPlugin() {
            super(CryptMode.ENCRYPT, "implGCMCrypt0",
                            byte[].class, int.class, int.class, byte[].class, int.class, byte[].class, int.class,
                            new InvocationPlugins.OptionalLazySymbol("com.sun.crypto.provider.GCTR"),
                            new InvocationPlugins.OptionalLazySymbol("com.sun.crypto.provider.GHASH"));
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode in, ValueNode inOffset, ValueNode len,
                        ValueNode ct, ValueNode ctOffset, ValueNode out, ValueNode outOffset, ValueNode gctr, ValueNode ghash) {
            try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                ResolvedJavaType receiverType = targetMethod.getDeclaringClass();
                ResolvedJavaType typeAESCrypt = getTypeAESCrypt(b.getMetaAccess(), receiverType);
                ResolvedJavaType typeGCTR = getTypeGCTR(receiverType);
                ResolvedJavaType typeGHASH = getTypeGHASH(receiverType);

                ValueNode nonNullGCTR = b.nullCheckedValue(gctr);
                ValueNode nonNullGHASH = b.nullCheckedValue(ghash);

                ValueNode inAddr = helper.arrayElementPointer(in, JavaKind.Byte, inOffset);
                ValueNode ctAddr = helper.arrayElementPointer(ct, JavaKind.Byte, ctOffset);
                ValueNode outAddr = helper.arrayElementPointer(out, JavaKind.Byte, outOffset);

                // Read GCTR.K
                ValueNode kAddr = readEmbeddedAESCryptKArrayStart(b, helper, typeGCTR, typeAESCrypt, nonNullGCTR);
                // Read GCTR.counter
                ValueNode counterAddr = readFieldArrayStart(b, helper, typeGCTR, "counter", nonNullGCTR, JavaKind.Byte);
                // Read GHASH.state
                ValueNode stateAddr = readFieldArrayStart(b, helper, typeGHASH, "state", nonNullGHASH, JavaKind.Long);
                // Read GHASH.subkeyHtbl
                ValueNode subkeyHtblAddr = readFieldArrayStart(b, helper, typeGHASH, "subkeyHtbl", nonNullGHASH, JavaKind.Long);

                ForeignCallNode call = new ForeignCallNode(GALOIS_COUNTER_MODE_CRYPT,
                                inAddr, len, ctAddr, outAddr, kAddr, stateAddr, subkeyHtblAddr, counterAddr);
                helper.emitFinalReturn(JavaKind.Int, call);
                return true;
            }
        }

        private static ResolvedJavaType getTypeGCTR(ResolvedJavaType context) {
            return UnresolvedJavaType.create("Lcom/sun/crypto/provider/GCTR;").resolve(context);
        }

        private static ResolvedJavaType getTypeGHASH(ResolvedJavaType context) {
            return UnresolvedJavaType.create("Lcom/sun/crypto/provider/GHASH;").resolve(context);
        }

        @Override
        protected ResolvedJavaType getTypeAESCrypt(MetaAccessProvider metaAccess, ResolvedJavaType context) {
            return resolveTypeAESCrypt(context);
        }
    }

    private static void registerAESPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, "com.sun.crypto.provider.CipherBlockChaining");
        r.register(new HotSpotCipherBlockChainingCryptPlugin(CryptMode.ENCRYPT));
        r.register(new HotSpotCipherBlockChainingCryptPlugin(CryptMode.DECRYPT));

        r = new Registration(plugins, "com.sun.crypto.provider.ElectronicCodeBook");
        r.register(new ElectronicCodeBookCryptPlugin(CryptMode.ENCRYPT) {
            @Override
            public boolean isApplicable(Architecture arch) {
                return config.electronicCodeBookEncrypt != 0L;
            }
        });
        r.register(new ElectronicCodeBookCryptPlugin(CryptMode.DECRYPT) {
            @Override
            public boolean isApplicable(Architecture arch) {
                return config.electronicCodeBookDecrypt != 0L;
            }
        });

        r = new Registration(plugins, "com.sun.crypto.provider.GaloisCounterMode");
        r.register(new GaloisCounterModeCryptPlugin() {
            @Override
            public boolean isApplicable(Architecture arch) {
                return config.galoisCounterModeCrypt != 0L;
            }
        });

        r = new Registration(plugins, "com.sun.crypto.provider.CounterMode");

        r.register(new CounterModeCryptPlugin() {
            @Override
            protected boolean canApply(GraphBuilderContext b) {
                return b instanceof BytecodeParser || b instanceof IntrinsicGraphBuilder;
            }

            @Override
            protected ValueNode getFieldOffset(GraphBuilderContext b, ResolvedJavaField field) {
                return ConstantNode.forLong(field.getOffset());
            }

            @Override
            protected ResolvedJavaType getTypeAESCrypt(MetaAccessProvider metaAccess, ResolvedJavaType context) {
                return resolveTypeAESCrypt(context);
            }
        });
    }

    private static void registerAdler32Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, "java.util.zip.Adler32");
        r.register(new ConditionalInvocationPlugin("updateBytes", int.class, byte[].class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode adler, ValueNode src, ValueNode off, ValueNode len) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode addr = helper.arrayElementPointer(src, JavaKind.Byte, off);
                    ForeignCallNode call = new ForeignCallNode(HotSpotBackend.UPDATE_BYTES_ADLER32, adler, addr, len);
                    b.addPush(JavaKind.Int, call);
                }
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.updateBytesAdler32 != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("updateByteBuffer", int.class, long.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode adler, ValueNode addr, ValueNode off, ValueNode len) {
                ValueNode buff = b.add(new ComputeObjectAddressNode(addr, off));
                ForeignCallNode call = new ForeignCallNode(HotSpotBackend.UPDATE_BYTES_ADLER32, adler, buff, len);
                b.addPush(JavaKind.Int, call);
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.updateBytesAdler32 != 0L;
            }
        });
    }

    private static void registerBigIntegerPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, BigInteger.class);
        r.register(new ConditionalInvocationPlugin("implMontgomeryMultiply", int[].class, int[].class, int[].class, int.class, long.class, int[].class) {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a, ValueNode bObject, ValueNode n, ValueNode len, ValueNode inv,
                            ValueNode product) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    // The stub doesn't return the right value for the intrinsic so push it here
                    // and the proper after FrameState will be put on ForeignCallNode by add.
                    b.addPush(JavaKind.Object, product);
                    b.add(new ForeignCallNode(HotSpotBackend.MONTGOMERY_MULTIPLY, helper.arrayStart(a, JavaKind.Int), helper.arrayStart(bObject, JavaKind.Int),
                                    helper.arrayStart(n, JavaKind.Int), len, inv, helper.arrayStart(product, JavaKind.Int)));
                }
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.montgomeryMultiply != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("implMontgomerySquare", int[].class, int[].class, int.class, long.class, int[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a, ValueNode n, ValueNode len, ValueNode inv, ValueNode product) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    // The stub doesn't return the right value for the intrinsic so push it here
                    // and the proper after FrameState will be put on ForeignCallNode by add.
                    b.addPush(JavaKind.Object, product);
                    b.add(new ForeignCallNode(HotSpotBackend.MONTGOMERY_SQUARE, helper.arrayStart(a, JavaKind.Int), helper.arrayStart(n, JavaKind.Int), len, inv,
                                    helper.arrayStart(product, JavaKind.Int)));
                }
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.montgomerySquare != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("shiftLeftImplWorker", int[].class, int[].class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode newArr, ValueNode oldArr, ValueNode newIdx, ValueNode shiftCount,
                            ValueNode numIter) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    b.add(new ForeignCallNode(HotSpotBackend.BIGINTEGER_LEFT_SHIFT_WORKER, helper.arrayStart(newArr, JavaKind.Int), helper.arrayStart(oldArr, JavaKind.Int), newIdx, shiftCount,
                                    numIter));
                }
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.bigIntegerLeftShiftWorker != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("shiftRightImplWorker", int[].class, int[].class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode newArr, ValueNode oldArr, ValueNode newIdx, ValueNode shiftCount,
                            ValueNode numIter) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    b.add(new ForeignCallNode(HotSpotBackend.BIGINTEGER_RIGHT_SHIFT_WORKER, helper.arrayStart(newArr, JavaKind.Int), helper.arrayStart(oldArr, JavaKind.Int), newIdx, shiftCount,
                                    numIter));
                }
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.bigIntegerRightShiftWorker != 0L;
            }
        });
    }

    private static void registerSHAPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config) {
        boolean useMD5 = config.md5ImplCompressMultiBlock != 0L;
        boolean useSha1 = config.sha1ImplCompressMultiBlock != 0L;
        boolean useSha256 = config.sha256ImplCompressMultiBlock != 0L;
        boolean useSha512 = config.sha512ImplCompressMultiBlock != 0L;
        boolean useSha3 = config.sha3ImplCompressMultiBlock != 0L;

        boolean implCompressMultiBlock0Enabled = useMD5 || useSha1 || useSha256 || useSha512 || useSha3;
        Registration r = new Registration(plugins, "sun.security.provider.DigestBase");
        if (implCompressMultiBlock0Enabled) {
            r.register(new SnippetSubstitutionInvocationPlugin<>(DigestBaseSnippets.Templates.class,
                            "implCompressMultiBlock0", Receiver.class, byte[].class, int.class, int.class) {
                @Override
                protected Object[] getConstantArguments(ResolvedJavaMethod targetMethod) {
                    ResolvedJavaType declaringClass = targetMethod.getDeclaringClass();
                    return new Object[]{
                                    declaringClass,
                                    HotSpotReplacementsUtil.getType(declaringClass, "Lsun/security/provider/MD5;"),
                                    HotSpotReplacementsUtil.getType(declaringClass, "Lsun/security/provider/SHA;"),
                                    HotSpotReplacementsUtil.getType(declaringClass, "Lsun/security/provider/SHA2;"),
                                    HotSpotReplacementsUtil.getType(declaringClass, "Lsun/security/provider/SHA5;"),
                                    HotSpotReplacementsUtil.getType(declaringClass, "Lsun/security/provider/SHA3;")
                    };
                }

                @Override
                public SnippetTemplate.SnippetInfo getSnippet(DigestBaseSnippets.Templates templates) {
                    return templates.implCompressMultiBlock0;
                }
            });
        }

        r = new Registration(plugins, "sun.security.provider.SHA3Parallel");
        r.register(new ConditionalInvocationPlugin("doubleKeccak", long[].class, long[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode lanes0, ValueNode lanes1) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode nonNullLanes0 = b.nullCheckedValue(lanes0);
                    ValueNode nonNullLanes1 = b.nullCheckedValue(lanes1);

                    ValueNode lanes0Start = helper.arrayStart(nonNullLanes0, JavaKind.Long);
                    ValueNode lanes1Start = helper.arrayStart(nonNullLanes1, JavaKind.Long);

                    ForeignCallNode call = new ForeignCallNode(DOUBLE_KECCAK, lanes0Start, lanes1Start);
                    b.addPush(JavaKind.Int, call);
                    return true;
                }
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.stubDoubleKeccak != 0L;
            }
        });
    }

    private static void registerMLPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, "sun.security.provider.ML_DSA");
        r.register(new ConditionalInvocationPlugin("implDilithiumAlmostNtt", int[].class, int[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode coeffs, ValueNode zetas) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode nonNullCoeffs = b.nullCheckedValue(coeffs);
                    ValueNode nonNullZetas = b.nullCheckedValue(zetas);

                    ValueNode coeffsStart = helper.arrayStart(nonNullCoeffs, JavaKind.Int);
                    ValueNode zetasStart = helper.arrayStart(nonNullZetas, JavaKind.Int);

                    ForeignCallNode call = new ForeignCallNode(DILITHIUM_ALMOST_NTT, coeffsStart, zetasStart);
                    b.addPush(JavaKind.Int, call);
                    return true;
                }
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.stubDilithiumAlmostNtt != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("implDilithiumAlmostInverseNtt", int[].class, int[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode coeffs, ValueNode zetas) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode nonNullCoeffs = b.nullCheckedValue(coeffs);
                    ValueNode nonNullZetas = b.nullCheckedValue(zetas);

                    ValueNode coeffsStart = helper.arrayStart(nonNullCoeffs, JavaKind.Int);
                    ValueNode zetasStart = helper.arrayStart(nonNullZetas, JavaKind.Int);

                    ForeignCallNode call = new ForeignCallNode(DILITHIUM_ALMOST_INVERSE_NTT, coeffsStart, zetasStart);
                    b.addPush(JavaKind.Int, call);
                    return true;
                }
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.stubDilithiumAlmostInverseNtt != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("implDilithiumNttMult", int[].class, int[].class, int[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode product, ValueNode coeffs1, ValueNode coeffs2) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode nonNullProduct = b.nullCheckedValue(product);
                    ValueNode nonNullCoeffs1 = b.nullCheckedValue(coeffs1);
                    ValueNode nonNullCoeffs2 = b.nullCheckedValue(coeffs2);

                    ValueNode productStart = helper.arrayStart(nonNullProduct, JavaKind.Int);
                    ValueNode coeffs1Start = helper.arrayStart(nonNullCoeffs1, JavaKind.Int);
                    ValueNode coeffs2Start = helper.arrayStart(nonNullCoeffs2, JavaKind.Int);

                    ForeignCallNode call = new ForeignCallNode(DILITHIUM_NTT_MULT, productStart, coeffs1Start, coeffs2Start);
                    b.addPush(JavaKind.Int, call);
                    return true;
                }
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.stubDilithiumNttMult != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("implDilithiumMontMulByConstant", int[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode coeffs, ValueNode constant) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode nonNullCoeffs = b.nullCheckedValue(coeffs);

                    ValueNode coeffsStart = helper.arrayStart(nonNullCoeffs, JavaKind.Int);

                    ForeignCallNode call = new ForeignCallNode(DILITHIUM_MONT_MUL_BY_CONSTANT, coeffsStart, constant);
                    b.addPush(JavaKind.Int, call);
                    return true;
                }
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.stubDilithiumMontMulByConstant != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("implDilithiumDecomposePoly", int[].class, int[].class, int[].class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input, ValueNode lowPart, ValueNode highPart, ValueNode twoGamma2,
                            ValueNode multiplier) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode nonNullInput = b.nullCheckedValue(input);
                    ValueNode nonNullLowPart = b.nullCheckedValue(lowPart);
                    ValueNode nonNullHighPart = b.nullCheckedValue(highPart);

                    ValueNode inputStart = helper.arrayStart(nonNullInput, JavaKind.Int);
                    ValueNode lowPartStart = helper.arrayStart(nonNullLowPart, JavaKind.Int);
                    ValueNode highPartStart = helper.arrayStart(nonNullHighPart, JavaKind.Int);

                    ForeignCallNode call = new ForeignCallNode(DILITHIUM_DECOMPOSE_POLY, inputStart, lowPartStart, highPartStart, twoGamma2, multiplier);
                    b.addPush(JavaKind.Int, call);
                    return true;
                }
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.stubDilithiumDecomposePoly != 0L;
            }
        });

        r = new Registration(plugins, "com.sun.crypto.provider.ML_KEM");
        r.register(new ConditionalInvocationPlugin("implKyberNtt", short[].class, short[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode poly, ValueNode zetas) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode polyNonNull = b.nullCheckedValue(poly);
                    ValueNode zetasNonNull = b.nullCheckedValue(zetas);

                    ValueNode polyStart = helper.arrayStart(polyNonNull, JavaKind.Short);
                    ValueNode zetasStart = helper.arrayStart(zetasNonNull, JavaKind.Short);

                    ForeignCallNode call = new ForeignCallNode(KYBER_NTT, polyStart, zetasStart);
                    b.addPush(JavaKind.Int, call);
                    return true;
                }
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.stubKyberNtt != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("implKyberInverseNtt", short[].class, short[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode poly, ValueNode zetas) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode polyNonNull = b.nullCheckedValue(poly);
                    ValueNode zetasNonNull = b.nullCheckedValue(zetas);

                    ValueNode polyStart = helper.arrayStart(polyNonNull, JavaKind.Short);
                    ValueNode zetasStart = helper.arrayStart(zetasNonNull, JavaKind.Short);

                    ForeignCallNode call = new ForeignCallNode(KYBER_INVERSE_NTT, polyStart, zetasStart);
                    b.addPush(JavaKind.Int, call);
                    return true;
                }
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.stubKyberInverseNtt != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("implKyberNttMult", short[].class, short[].class, short[].class, short[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode result, ValueNode ntta, ValueNode nttb, ValueNode zetas) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode resultNonNull = b.nullCheckedValue(result);
                    ValueNode nttaNonNull = b.nullCheckedValue(ntta);
                    ValueNode nttbNonNull = b.nullCheckedValue(nttb);
                    ValueNode zetasNonNull = b.nullCheckedValue(zetas);

                    ValueNode resultStart = helper.arrayStart(resultNonNull, JavaKind.Short);
                    ValueNode nttaStart = helper.arrayStart(nttaNonNull, JavaKind.Short);
                    ValueNode nttbStart = helper.arrayStart(nttbNonNull, JavaKind.Short);
                    ValueNode zetasStart = helper.arrayStart(zetasNonNull, JavaKind.Short);

                    ForeignCallNode call = new ForeignCallNode(KYBER_NTT_MULT, resultStart, nttaStart, nttbStart, zetasStart);
                    b.addPush(JavaKind.Int, call);
                    return true;
                }
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.stubKyberNttMult != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("implKyberAddPoly", short[].class, short[].class, short[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode result, ValueNode aIn, ValueNode bIn) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode resultNonNull = b.nullCheckedValue(result);
                    ValueNode aNonNull = b.nullCheckedValue(aIn);
                    ValueNode bNonNull = b.nullCheckedValue(bIn);

                    ValueNode resultStart = helper.arrayStart(resultNonNull, JavaKind.Short);
                    ValueNode aStart = helper.arrayStart(aNonNull, JavaKind.Short);
                    ValueNode bStart = helper.arrayStart(bNonNull, JavaKind.Short);

                    ForeignCallNode call = new ForeignCallNode(KYBER_ADD_POLY_2, resultStart, aStart, bStart);
                    b.addPush(JavaKind.Int, call);
                    return true;
                }
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.stubKyberAddPoly2 != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("implKyberAddPoly", short[].class, short[].class, short[].class, short[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode result, ValueNode aIn, ValueNode bIn, ValueNode cIn) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode resultNonNull = b.nullCheckedValue(result);
                    ValueNode aNonNull = b.nullCheckedValue(aIn);
                    ValueNode bNonNull = b.nullCheckedValue(bIn);
                    ValueNode cNonNull = b.nullCheckedValue(cIn);

                    ValueNode resultStart = helper.arrayStart(resultNonNull, JavaKind.Short);
                    ValueNode aStart = helper.arrayStart(aNonNull, JavaKind.Short);
                    ValueNode bStart = helper.arrayStart(bNonNull, JavaKind.Short);
                    ValueNode cStart = helper.arrayStart(cNonNull, JavaKind.Short);

                    ForeignCallNode call = new ForeignCallNode(KYBER_ADD_POLY_3, resultStart, aStart, bStart, cStart);
                    b.addPush(JavaKind.Int, call);
                    return true;
                }
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.stubKyberAddPoly3 != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("implKyber12To16", byte[].class, int.class, short[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode condensed, ValueNode index, ValueNode parsed, ValueNode parsedLength) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode condensedNonNull = b.nullCheckedValue(condensed);
                    ValueNode parsedNonNull = b.nullCheckedValue(parsed);

                    ValueNode condensedStart = helper.arrayStart(condensedNonNull, JavaKind.Byte);
                    ValueNode parsedStart = helper.arrayStart(parsedNonNull, JavaKind.Short);

                    ForeignCallNode call = new ForeignCallNode(KYBER_12_TO_16, condensedStart, index, parsedStart, parsedLength);
                    b.addPush(JavaKind.Int, call);
                    return true;
                }
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.stubKyber12To16 != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("implKyberBarrettReduce", short[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode coeffs) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode coeffsNonNull = b.nullCheckedValue(coeffs);
                    ValueNode coeffsStart = helper.arrayStart(coeffsNonNull, JavaKind.Short);

                    ForeignCallNode call = new ForeignCallNode(KYBER_BARRETT_REDUCE, coeffsStart);
                    b.addPush(JavaKind.Int, call);
                    return true;
                }
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.stubKyberBarrettReduce != 0L;
            }
        });
    }

    private static void registerBase64Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, MetaAccessProvider metaAccess) {
        Registration r = new Registration(plugins, "java.util.Base64$Encoder");
        r.register(new ConditionalInvocationPlugin("encodeBlock", Receiver.class, byte[].class, int.class, int.class, byte[].class, int.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src,
                            ValueNode sp, ValueNode sl, ValueNode dst, ValueNode dp, ValueNode isURL) {
                if (receiver != null) {
                    // Side effect of call below is to add a receiver null check if required
                    receiver.get(true);
                }
                int byteArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Byte);
                ComputeObjectAddressNode srcAddress = b.add(new ComputeObjectAddressNode(src, ConstantNode.forInt(byteArrayBaseOffset)));
                ComputeObjectAddressNode dstAddress = b.add(new ComputeObjectAddressNode(dst, ConstantNode.forInt(byteArrayBaseOffset)));
                b.add(new ForeignCallNode(BASE64_ENCODE_BLOCK, srcAddress, sp, sl, dstAddress, dp, isURL));
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.base64EncodeBlock != 0L;
            }
        });
        r = new Registration(plugins, "java.util.Base64$Decoder");
        r.register(new ConditionalInvocationPlugin("decodeBlock", Receiver.class, byte[].class, int.class, int.class, byte[].class, int.class, boolean.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src,
                            ValueNode sp, ValueNode sl, ValueNode dst, ValueNode dp, ValueNode isURL, ValueNode isMime) {
                if (receiver != null) {
                    // Side effect of call below is to add a receiver null check if required
                    receiver.get(true);
                }
                int byteArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Byte);
                ComputeObjectAddressNode srcAddress = b.add(new ComputeObjectAddressNode(src, ConstantNode.forInt(byteArrayBaseOffset)));
                ComputeObjectAddressNode dstAddress = b.add(new ComputeObjectAddressNode(dst, ConstantNode.forInt(byteArrayBaseOffset)));
                ForeignCallNode call = new ForeignCallNode(BASE64_DECODE_BLOCK, srcAddress, sp, sl, dstAddress, dp, isURL, isMime);
                b.addPush(JavaKind.Int, call);
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.base64DecodeBlock != 0L;
            }
        });
    }

    private static void registerCRC32Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, CRC32.class);

        r.register(new ConditionalInvocationPlugin("update", int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode crc, ValueNode arg) {
                final ValueNode crcTableRawAddress = ConstantNode.forLong(config.crcTableAddress);
                ValueNode c = new XorNode(crc, ConstantNode.forInt(-1));
                ValueNode index = new AndNode(new XorNode(arg, c), ConstantNode.forInt(0xff));
                ValueNode offset = new LeftShiftNode(index, ConstantNode.forInt(2));
                AddressNode address = new OffsetAddressNode(crcTableRawAddress, new SignExtendNode(offset, 32, 64));
                ValueNode result = b.add(new JavaReadNode(JavaKind.Int, address, CRC_TABLE_LOCATION, BarrierType.NONE, MemoryOrderMode.PLAIN, false));
                result = new XorNode(result, new UnsignedRightShiftNode(c, ConstantNode.forInt(8)));
                b.addPush(JavaKind.Int, new XorNode(result, ConstantNode.forInt(-1)));
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.updateBytesCRC32Stub != 0L && config.crcTableAddress != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("updateBytes0", int.class, byte[].class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode crc, ValueNode buf, ValueNode off, ValueNode len) {
                int byteArrayBaseOffset = b.getMetaAccess().getArrayBaseOffset(JavaKind.Byte);
                ValueNode bufAddr = b.add(new ComputeObjectAddressNode(buf, new AddNode(ConstantNode.forInt(byteArrayBaseOffset), off)));
                b.addPush(JavaKind.Int, new ForeignCallNode(UPDATE_BYTES_CRC32, crc, bufAddr, len));
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.updateBytesCRC32Stub != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("updateByteBuffer0", int.class, long.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode crc, ValueNode addr, ValueNode off, ValueNode len) {
                ValueNode bufAddr = b.add(new AddNode(addr, new SignExtendNode(off, 32, 64)));
                b.addPush(JavaKind.Int, new ForeignCallNode(UPDATE_BYTES_CRC32, crc, bufAddr, len));
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.updateBytesCRC32Stub != 0L;
            }
        });
    }

    private static void registerCRC32CPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, "java.util.zip.CRC32C");
        r.register(new ConditionalInvocationPlugin("updateBytes", int.class, byte[].class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode crc, ValueNode buf, ValueNode off, ValueNode end) {
                int byteArrayBaseOffset = b.getMetaAccess().getArrayBaseOffset(JavaKind.Byte);
                ValueNode bufAddr = b.add(new ComputeObjectAddressNode(buf, new AddNode(ConstantNode.forInt(byteArrayBaseOffset), off)));
                b.addPush(JavaKind.Int, new ForeignCallNode(UPDATE_BYTES_CRC32C, crc, bufAddr, new SubNode(end, off)));
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.updateBytesCRC32C != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("updateDirectByteBuffer", int.class, long.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode crc, ValueNode addr, ValueNode off, ValueNode end) {
                ValueNode bufAddr = b.add(new AddNode(addr, new SignExtendNode(off, 32, 64)));
                b.addPush(JavaKind.Int, new ForeignCallNode(UPDATE_BYTES_CRC32C, crc, bufAddr, new SubNode(end, off)));
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.updateBytesCRC32C != 0L;
            }
        });
    }

    private static void registerPoly1305Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, "com.sun.crypto.provider.Poly1305");
        r.register(new ConditionalInvocationPlugin("processMultipleBlocks", Receiver.class, byte[].class, int.class, int.class, long[].class, long[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input, ValueNode offset, ValueNode length, ValueNode aLimbs,
                            ValueNode rLimbs) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    receiver.get(true);
                    ValueNode inputNotNull = b.nullCheckedValue(input);
                    ValueNode aLimbsNotNull = b.nullCheckedValue(aLimbs);
                    ValueNode rLimbsNotNull = b.nullCheckedValue(rLimbs);

                    ValueNode inputStart = helper.arrayElementPointer(inputNotNull, JavaKind.Byte, offset);
                    ValueNode aLimbsStart = helper.arrayStart(aLimbsNotNull, JavaKind.Long);
                    ValueNode rLimbsStart = helper.arrayStart(rLimbsNotNull, JavaKind.Long);

                    b.add(new ForeignCallNode(POLY1305_PROCESSBLOCKS, inputStart, length, aLimbsStart, rLimbsStart));
                }
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.poly1305ProcessBlocks != 0L;
            }
        });
    }

    private static void registerChaCha20Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, "com.sun.crypto.provider.ChaCha20Cipher");
        r.register(new ConditionalInvocationPlugin("implChaCha20Block", int[].class, byte[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode initState, ValueNode result) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode stateNotNull = b.nullCheckedValue(initState);
                    ValueNode resultNotNull = b.nullCheckedValue(result);

                    ValueNode stateStart = helper.arrayStart(stateNotNull, JavaKind.Int);
                    ValueNode resultStart = helper.arrayStart(resultNotNull, JavaKind.Byte);

                    ForeignCallNode call = new ForeignCallNode(CHACHA20Block, stateStart, resultStart);
                    b.addPush(JavaKind.Int, call);
                }
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.chacha20Block != 0L;
            }
        });
    }

    private static void registerP256Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, "sun.security.util.math.intpoly.MontgomeryIntegerPolynomialP256");
        r.register(new ConditionalInvocationPlugin("mult", Receiver.class, long[].class, long[].class, long[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode aIn, ValueNode bIn, ValueNode rOut) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    receiver.get(true);
                    ValueNode aNotNull = b.nullCheckedValue(aIn);
                    ValueNode bNotNull = b.nullCheckedValue(bIn);
                    ValueNode rNotNull = b.nullCheckedValue(rOut);

                    ValueNode aStart = helper.arrayStart(aNotNull, JavaKind.Long);
                    ValueNode bStart = helper.arrayStart(bNotNull, JavaKind.Long);
                    ValueNode rStart = helper.arrayStart(rNotNull, JavaKind.Long);

                    b.add(new ForeignCallNode(INTPOLY_MONTGOMERYMULT_P256, aStart, bStart, rStart));
                }
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.intpolyMontgomeryMultP256 != 0L;
            }
        });

        r = new Registration(plugins, "sun.security.util.math.intpoly.IntegerPolynomial");
        r.register(new ConditionalInvocationPlugin("conditionalAssign", int.class, long[].class, long[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode set, ValueNode aIn, ValueNode bIn) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode aNotNull = b.nullCheckedValue(aIn);
                    ValueNode bNotNull = b.nullCheckedValue(bIn);

                    ValueNode aStart = helper.arrayStart(aNotNull, JavaKind.Long);
                    ValueNode bStart = helper.arrayStart(bNotNull, JavaKind.Long);

                    ValueNode aLength = helper.arraylength(aNotNull);

                    b.add(new ForeignCallNode(INTPOLY_ASSIGN, set, aStart, bStart, aLength));
                }
                return true;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.intpolyAssign != 0L;
            }
        });
    }

    private static void registerArraysSupportPlugins(InvocationPlugins plugins, Architecture arch) {
        Registration r = new Registration(plugins, "jdk.internal.util.ArraysSupport");
        r.register(new StandardGraphBuilderPlugins.VectorizedMismatchInvocationPlugin() {
            @Override
            public boolean isGraalOnly() {
                // On AArch64 HotSpot, this intrinsic is not implemented and
                // UseVectorizedMismatchIntrinsic defaults to false.
                return arch instanceof AArch64;
            }
        });
        r.register(new StandardGraphBuilderPlugins.VectorizedHashCodeInvocationPlugin() {
            @Override
            public boolean isGraalOnly() {
                // On AArch64 HotSpot, this intrinsic is not implemented and
                // UseVectorizedHashCodeIntrinsic defaults to false.
                return arch instanceof AArch64;
            }
        });
    }

    private static void registerReferencePlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Reference.class);
        r.register(new ReachabilityFencePlugin() {
            @Override
            protected boolean useExplicitReachabilityFence(GraphBuilderContext b) {
                return Options.ForceExplicitReachabilityFence.getValue(b.getOptions());
            }
        });
        r.register(new InlineOnlyInvocationPlugin("get0", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode offset = b.add(ConstantNode.forLong(HotSpotReplacementsUtil.referentOffset(b.getMetaAccess())));
                AddressNode address = b.add(new OffsetAddressNode(receiver.get(true), offset));
                FieldLocationIdentity locationIdentity = new FieldLocationIdentity(HotSpotReplacementsUtil.referentField(b.getMetaAccess()));
                JavaReadNode read = b.add(new JavaReadNode(StampFactory.object(), JavaKind.Object, address, locationIdentity, BarrierType.REFERENCE_GET, MemoryOrderMode.PLAIN, true));
                b.add(new MembarNode(MembarNode.FenceKind.NONE, locationIdentity));
                b.addPush(JavaKind.Object, read);
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("refersTo0", Receiver.class, Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode o) {
                ValueNode offset = b.add(ConstantNode.forLong(HotSpotReplacementsUtil.referentOffset(b.getMetaAccess())));
                AddressNode address = b.add(new OffsetAddressNode(receiver.get(true), offset));
                FieldLocationIdentity locationIdentity = new FieldLocationIdentity(HotSpotReplacementsUtil.referentField(b.getMetaAccess()));
                JavaReadNode read = b.add(new JavaReadNode(StampFactory.object(), JavaKind.Object, address, locationIdentity, BarrierType.WEAK_REFERS_TO, MemoryOrderMode.PLAIN, true));
                b.add(new MembarNode(MembarNode.FenceKind.NONE, locationIdentity));
                LogicNode objectEquals = b.add(ObjectEqualsNode.create(b.getConstantReflection(), b.getMetaAccess(), b.getOptions(), read, o, NodeView.DEFAULT));
                b.addPush(JavaKind.Boolean, ConditionalNode.create(objectEquals, b.add(forBoolean(true)), b.add(forBoolean(false)), NodeView.DEFAULT));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("clear0", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode offset = b.add(ConstantNode.forLong(HotSpotReplacementsUtil.referentOffset(b.getMetaAccess())));
                    AddressNode address = b.add(new OffsetAddressNode(receiver.get(true), offset));
                    FieldLocationIdentity locationIdentity = new FieldLocationIdentity(HotSpotReplacementsUtil.referentField(b.getMetaAccess()));
                    JavaReadNode referent = b.add(new JavaReadNode(StampFactory.object(), JavaKind.Object, address, locationIdentity, BarrierType.WEAK_REFERS_TO, MemoryOrderMode.PLAIN, true));
                    helper.emitReturnIf(IsNullNode.create(referent), null, GraalDirectives.LIKELY_PROBABILITY);
                    b.add(new JavaWriteNode(JavaKind.Object, address, locationIdentity, ConstantNode.defaultForKind(JavaKind.Object), BarrierType.AS_NO_KEEPALIVE_WRITE, true));
                    helper.emitFinalReturn(JavaKind.Void, null);
                    return true;
                }
            }
        });
        r = new Registration(plugins, PhantomReference.class);
        r.register(new InlineOnlyInvocationPlugin("refersTo0", Receiver.class, Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode o) {
                ValueNode offset = b.add(ConstantNode.forLong(HotSpotReplacementsUtil.referentOffset(b.getMetaAccess())));
                AddressNode address = b.add(new OffsetAddressNode(receiver.get(true), offset));
                FieldLocationIdentity locationIdentity = new FieldLocationIdentity(HotSpotReplacementsUtil.referentField(b.getMetaAccess()));
                JavaReadNode read = b.add(new JavaReadNode(StampFactory.object(), JavaKind.Object, address, locationIdentity, BarrierType.PHANTOM_REFERS_TO, MemoryOrderMode.PLAIN, true));
                LogicNode objectEquals = b.add(ObjectEqualsNode.create(b.getConstantReflection(), b.getMetaAccess(), b.getOptions(), read, o, NodeView.DEFAULT));
                b.addPush(JavaKind.Boolean, ConditionalNode.create(objectEquals, b.add(forBoolean(true)), b.add(forBoolean(false)), NodeView.DEFAULT));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("clear0", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode offset = b.add(ConstantNode.forLong(HotSpotReplacementsUtil.referentOffset(b.getMetaAccess())));
                    AddressNode address = b.add(new OffsetAddressNode(receiver.get(true), offset));
                    FieldLocationIdentity locationIdentity = new FieldLocationIdentity(HotSpotReplacementsUtil.referentField(b.getMetaAccess()));
                    JavaReadNode referent = b.add(new JavaReadNode(StampFactory.object(), JavaKind.Object, address, locationIdentity, BarrierType.PHANTOM_REFERS_TO, MemoryOrderMode.PLAIN, true));
                    helper.emitReturnIf(IsNullNode.create(referent), null, GraalDirectives.LIKELY_PROBABILITY);
                    b.add(new JavaWriteNode(JavaKind.Object, address, locationIdentity, ConstantNode.defaultForKind(JavaKind.Object), BarrierType.AS_NO_KEEPALIVE_WRITE, true));
                    helper.emitFinalReturn(JavaKind.Void, null);
                    return true;
                }
            }
        });
    }

    private static void registerInstrumentationImplPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, "sun.instrument.InstrumentationImpl");
        r.register(new InlineOnlyInvocationPlugin("getObjectSize0", Receiver.class, long.class, Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode nativeAgent, ValueNode objectToSize) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    ValueNode objectNonNull = b.nullCheckedValue(objectToSize);
                    StructuredGraph graph = b.getGraph();
                    // Discharge receiver null check requirement
                    receiver.get(true);
                    LoadHubNode hub = b.add(new LoadHubNode(b.getStampProvider(), objectNonNull));
                    ValueNode layoutHelper = helper.klassLayoutHelper(hub);

                    LogicNode isArray = b.add(ObjectIsArrayNode.create(objectNonNull));

                    ArrayLengthNode arrayLengthNode = graph.add(new ArrayLengthNode(objectNonNull));
                    EndNode arrayBranch = graph.add(new EndNode());
                    arrayLengthNode.setNext(arrayBranch);

                    ValueNode arrayHeaderSizeInt = b.add(UnsignedRightShiftNode.create(layoutHelper,
                                    ConstantNode.forInt(config.layoutHelperHeaderSizeShift), NodeView.DEFAULT));
                    ValueNode arrayHeaderSizeMaskedInt = b.add(AndNode.create(arrayHeaderSizeInt,
                                    ConstantNode.forInt(config.layoutHelperHeaderSizeMask), NodeView.DEFAULT));
                    ValueNode arrayHeaderSizeMaskedLong = b.add(SignExtendNode.create(arrayHeaderSizeMaskedInt, JavaKind.Long.getBitCount(), NodeView.DEFAULT));

                    ValueNode arrayLengthLong = b.add(SignExtendNode.create(arrayLengthNode, JavaKind.Long.getBitCount(), NodeView.DEFAULT));
                    ValueNode arraySizeLong = b.add(LeftShiftNode.create(arrayLengthLong, layoutHelper, NodeView.DEFAULT));
                    ValueNode arrayInstanceSizeLong = b.add(AddNode.create(arrayHeaderSizeMaskedLong, arraySizeLong, NodeView.DEFAULT));

                    long objectAlignmentMask = config.objectAlignment - 1;
                    ValueNode arrayInstanceSizeMaskedLong = b.add(AndNode.create(
                                    AddNode.create(arrayInstanceSizeLong, ConstantNode.forLong(objectAlignmentMask), NodeView.DEFAULT),
                                    ConstantNode.forLong(~objectAlignmentMask), NodeView.DEFAULT));

                    EndNode instanceBranch = graph.add(new EndNode());
                    ValueNode layoutHelperLong = b.add(SignExtendNode.create(layoutHelper, JavaKind.Long.getBitCount(), NodeView.DEFAULT));
                    ValueNode instanceSizeLong = b.add(AndNode.create(layoutHelperLong, ConstantNode.forLong(-((long) JavaKind.Long.getByteCount())), NodeView.DEFAULT));

                    b.add(new IfNode(isArray, arrayLengthNode, instanceBranch, injected(0.5D)));
                    MergeNode merge = b.append(new MergeNode());
                    merge.addForwardEnd(arrayBranch);
                    merge.addForwardEnd(instanceBranch);
                    b.addPush(JavaKind.Long, new ValuePhiNode(StampFactory.forKind(JavaKind.Long), merge,
                                    arrayInstanceSizeMaskedLong, instanceSizeLong));
                    b.setStateAfter(merge);
                }
                return true;
            }
        });
    }

    private static boolean isSimdSortSupported(Architecture arch, GraalHotSpotVMConfig config, int jvmType) {
        if (config.stubArraySort == 0L || config.stubArrayPartition == 0L) {
            return false;
        }
        if (arch instanceof AMD64 && !config.supportsAvx512SimdSort) {
            return switch (jvmType) {
                case T_INT, T_FLOAT -> true;
                default -> false;
            };
        }
        return true;
    }

    private static void registerDualPivotQuicksortPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Architecture architecture) {
        Registration r = new Registration(plugins, "java.util.DualPivotQuicksort");
        r.register(new ConditionalInvocationPlugin("sort", Class.class, Object.class, long.class, int.class, int.class,
                        new InvocationPlugins.OptionalLazySymbol("java.util.DualPivotQuicksort$SortOperation")) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode elemType, ValueNode array,
                            ValueNode offset, ValueNode low, ValueNode high, ValueNode so) {
                if (elemType.isConstant()) {
                    ResolvedJavaType type = b.getConstantReflection().asJavaType(elemType.asJavaConstant());
                    int jvmType = switch (type.getJavaKind()) {
                        case Float -> T_FLOAT;
                        case Double -> T_DOUBLE;
                        case Int -> T_INT;
                        case Long -> T_LONG;
                        default ->
                            throw GraalError.shouldNotReachHere("Unexpected element type " + type.toJavaName());
                    };
                    if (isSimdSortSupported(architecture, config, jvmType)) {
                        ValueNode arrayNonNull = b.nullCheckedValue(array);
                        ValueNode arrayStart = b.add(new ComputeObjectAddressNode(arrayNonNull, offset));
                        b.add(new ForeignCallNode(ARRAY_SORT, arrayStart, ConstantNode.forInt(jvmType), low, high));
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.stubArraySort != 0L;
            }
        });
        r.register(new ConditionalInvocationPlugin("partition", Class.class, Object.class, long.class, int.class, int.class, int.class, int.class,
                        new InvocationPlugins.OptionalLazySymbol("java.util.DualPivotQuicksort$PartitionOperation")) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode elemType, ValueNode array,
                            ValueNode offset, ValueNode low, ValueNode high, ValueNode pivotIndex1, ValueNode pivotIndex2, ValueNode so) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    if (elemType.isConstant()) {
                        ResolvedJavaType type = b.getConstantReflection().asJavaType(elemType.asJavaConstant());
                        int jvmType = switch (type.getJavaKind()) {
                            case Float -> T_FLOAT;
                            case Double -> T_DOUBLE;
                            case Int -> T_INT;
                            case Long -> T_LONG;
                            default ->
                                throw GraalError.shouldNotReachHere("Unexpected element type " + type.toJavaName());
                        };
                        if (isSimdSortSupported(architecture, config, jvmType)) {
                            ValueNode arrayNonNull = b.nullCheckedValue(array);
                            ValueNode arrayStart = b.add(new ComputeObjectAddressNode(arrayNonNull, offset));
                            ValueNode pivotIndices = b.append(new NewArrayNode(b.getMetaAccess().lookupJavaType(Integer.TYPE), ConstantNode.forInt(2), false));
                            ValueNode pivotIndicesStart = helper.arrayStart(pivotIndices, JavaKind.Int);

                            ForeignCallNode call = b.append(new ForeignCallNode(ARRAY_PARTITION, arrayStart, ConstantNode.forInt(jvmType), low, high, pivotIndicesStart, pivotIndex1, pivotIndex2));
                            b.push(JavaKind.Object, pivotIndices);
                            b.setStateAfter(call);
                            return true;
                        }
                    }
                    return false;
                }
            }

            @Override
            public boolean isApplicable(Architecture arch) {
                return config.stubArrayPartition != 0L;
            }
        });
    }
}
