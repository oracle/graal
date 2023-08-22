/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.meta;

import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;
import static org.graalvm.compiler.hotspot.HotSpotBackend.BASE64_DECODE_BLOCK;
import static org.graalvm.compiler.hotspot.HotSpotBackend.BASE64_ENCODE_BLOCK;
import static org.graalvm.compiler.hotspot.HotSpotBackend.CHACHA20Block;
import static org.graalvm.compiler.hotspot.HotSpotBackend.CRC_TABLE_LOCATION;
import static org.graalvm.compiler.hotspot.HotSpotBackend.ELECTRONIC_CODEBOOK_DECRYPT_AESCRYPT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.ELECTRONIC_CODEBOOK_ENCRYPT_AESCRYPT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.GALOIS_COUNTER_MODE_CRYPT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.POLY1305_PROCESSBLOCKS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA3_IMPL_COMPRESS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_END;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_MOUNT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_START;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_UNMOUNT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.UPDATE_BYTES_CRC32;
import static org.graalvm.compiler.hotspot.HotSpotBackend.UPDATE_BYTES_CRC32C;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.HOTSPOT_JAVA_LANG_THREAD_IS_IN_VTMS_TRANSITION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.HOTSPOT_JAVA_THREAD_IS_IN_TMP_VTMS_TRANSITION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.HOTSPOT_JAVA_THREAD_IS_IN_VTMS_TRANSITION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.HOTSPOT_VTMS_NOTIFY_JVMTI_EVENTS;
import static org.graalvm.compiler.java.BytecodeParserOptions.InlineDuringParsing;
import static org.graalvm.compiler.nodes.ConstantNode.forBoolean;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;

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

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.memory.BarrierType;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.nodes.CurrentJavaThreadNode;
import org.graalvm.compiler.hotspot.nodes.HotSpotLoadReservedReferenceNode;
import org.graalvm.compiler.hotspot.nodes.HotSpotStoreReservedReferenceNode;
import org.graalvm.compiler.hotspot.replacements.CallSiteTargetNode;
import org.graalvm.compiler.hotspot.replacements.DigestBaseSnippets;
import org.graalvm.compiler.hotspot.replacements.FastNotifyNode;
import org.graalvm.compiler.hotspot.replacements.HotSpotIdentityHashCodeNode;
import org.graalvm.compiler.hotspot.replacements.HotSpotInvocationPluginHelper;
import org.graalvm.compiler.hotspot.replacements.HotSpotReflectionGetCallerClassNode;
import org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import org.graalvm.compiler.hotspot.replacements.HubGetClassNode;
import org.graalvm.compiler.hotspot.replacements.ObjectCloneNode;
import org.graalvm.compiler.hotspot.replacements.UnsafeCopyMemoryNode;
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.lir.SyncPort;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ProfileData;
import org.graalvm.compiler.nodes.ProfileData.BranchProbabilityData;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerTestNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.nodes.calc.XorNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.extended.ObjectIsArrayNode;
import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.compiler.nodes.graphbuilderconf.ForeignCallPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedPluginFactory;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.InlineOnlyInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.DynamicNewArrayNode;
import org.graalvm.compiler.nodes.java.DynamicNewInstanceNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.replacements.InlineDuringParsingPlugin;
import org.graalvm.compiler.replacements.IntrinsicGraphBuilder;
import org.graalvm.compiler.replacements.InvocationPluginHelper;
import org.graalvm.compiler.replacements.MethodHandlePlugin;
import org.graalvm.compiler.replacements.NodeIntrinsificationProvider;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.compiler.replacements.SnippetSubstitutionInvocationPlugin;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.AESCryptDelegatePlugin;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.CounterModeCryptPlugin;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.ReachabilityFencePlugin;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyCallNode;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyForeignCalls;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopySnippets;
import org.graalvm.compiler.replacements.nodes.AESNode.CryptMode;
import org.graalvm.compiler.replacements.nodes.CipherBlockChainingAESNode;
import org.graalvm.compiler.replacements.nodes.CounterModeAESNode;
import org.graalvm.compiler.replacements.nodes.MacroNode.MacroParams;
import org.graalvm.compiler.replacements.nodes.VectorizedMismatchNode;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.VMIntrinsicMethod;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;
import jdk.vm.ci.services.Services;

/**
 * Defines the {@link Plugins} used when running on HotSpot.
 */
public class HotSpotGraphBuilderPlugins {

    public static class Options {
        @Option(help = "Force an explicit compiler node for Reference.reachabilityFence, instead of relying on FrameState liveness", type = OptionType.Expert) //
        public static final OptionKey<Boolean> ForceExplicitReachabilityFence = new OptionKey<>(false);
    }

    /**
     * Creates a {@link Plugins} object that should be used when running on HotSpot.
     */
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
        InvocationPlugins invocationPlugins = new HotSpotInvocationPlugins(graalRuntime, config, compilerConfiguration, target, options);

        Plugins plugins = new Plugins(invocationPlugins);
        plugins.appendNodePlugin(new HotSpotExceptionDispatchPlugin(config, wordTypes.getWordKind()));
        StandardGraphBuilderPlugins.registerConstantFieldLoadPlugin(plugins);
        if (!IS_IN_NATIVE_IMAGE) {
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
        compilerConfiguration.registerGraphBuilderPlugins(target.arch, plugins, options, replacements);

        invocationPlugins.defer(new Runnable() {

            @Override
            public void run() {
                registerObjectPlugins(invocationPlugins, config, replacements);
                registerClassPlugins(plugins, config, replacements);
                registerSystemPlugins(invocationPlugins);
                registerThreadPlugins(invocationPlugins, config, replacements);
                registerVirtualThreadPlugins(invocationPlugins, config, replacements);
                registerCallSitePlugins(invocationPlugins);
                registerReflectionPlugins(invocationPlugins, replacements, config);
                registerAESPlugins(invocationPlugins, config, replacements, target.arch);
                registerAdler32Plugins(invocationPlugins, config, replacements);
                registerCRC32Plugins(invocationPlugins, config, replacements);
                registerCRC32CPlugins(invocationPlugins, config, replacements);
                registerBigIntegerPlugins(invocationPlugins, config, replacements);
                registerSHAPlugins(invocationPlugins, config, replacements);
                registerBase64Plugins(invocationPlugins, config, metaAccess, replacements);
                registerUnsafePlugins(invocationPlugins, config, replacements);
                StandardGraphBuilderPlugins.registerInvocationPlugins(snippetReflection, invocationPlugins, replacements, true, false, true, graalRuntime.getHostProviders().getLowerer());
                registerArrayPlugins(invocationPlugins, replacements, config);
                registerStringPlugins(invocationPlugins, replacements, wordTypes, foreignCalls, config);
                registerArraysSupportPlugins(invocationPlugins, replacements);
                registerReferencePlugins(invocationPlugins, replacements);
                registerTrufflePlugins(invocationPlugins, wordTypes, config);
                registerInstrumentationImplPlugins(invocationPlugins, config, replacements);
                for (HotSpotInvocationPluginProvider p : GraalServices.load(HotSpotInvocationPluginProvider.class)) {
                    p.registerInvocationPlugins(target.arch, plugins.getInvocationPlugins(), replacements);
                }
                registerPoly1305Plugins(invocationPlugins, config, replacements);
                registerChaCha20Plugins(invocationPlugins, config, replacements);
            }

        });
        if (!IS_IN_NATIVE_IMAGE) {
            // In libgraal all NodeIntrinsics been converted into special nodes so the plugins
            // aren't needed.
            NodeIntrinsificationProvider nodeIntrinsificationProvider = new NodeIntrinsificationProvider(metaAccess, snippetReflection, foreignCalls, wordTypes, target);
            invocationPlugins.defer(() -> {
                for (GeneratedPluginFactory factory : GraalServices.load(GeneratedPluginFactory.class)) {
                    factory.registerPlugins(invocationPlugins, nodeIntrinsificationProvider);
                }
            });
        }
        return plugins;
    }

    private static void registerTrufflePlugins(InvocationPlugins plugins, WordTypes wordTypes, GraalHotSpotVMConfig config) {
        if (config.jvmciReservedReference0Offset == -1) {
            // cannot install intrinsics without
            return;
        }
        plugins.registerIntrinsificationPredicate(t -> t.getName().equals("Lcom/oracle/truffle/runtime/hotspot/HotSpotFastThreadLocal;"));
        Registration tl = new Registration(plugins, "com.oracle.truffle.runtime.hotspot.HotSpotFastThreadLocal");
        tl.register(new InvocationPlugin("get", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                int jvmciReservedReference0Offset = config.jvmciReservedReference0Offset;
                GraalError.guarantee(jvmciReservedReference0Offset != -1, "jvmciReservedReference0Offset is not available but used.");
                b.addPush(JavaKind.Object, new HotSpotLoadReservedReferenceNode(b.getMetaAccess(), wordTypes, jvmciReservedReference0Offset));
                return true;
            }
        });
        tl.register(new InvocationPlugin("set", Receiver.class, Object[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode value) {
                int jvmciReservedReference0Offset = config.jvmciReservedReference0Offset;
                GraalError.guarantee(jvmciReservedReference0Offset != -1, "jvmciReservedReference0Offset is not available but used.");
                b.add(new HotSpotStoreReservedReferenceNode(wordTypes, value, jvmciReservedReference0Offset));
                return true;
            }
        });
    }

    private static void registerObjectPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, Object.class, replacements);
        r.register(new InlineOnlyInvocationPlugin("clone", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get();
                b.addPush(JavaKind.Object, new ObjectCloneNode(MacroParams.of(b, targetMethod, object)));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("hashCode", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get();
                b.addPush(JavaKind.Int, new HotSpotIdentityHashCodeNode(object, b.bci()));
                return true;
            }
        });
        if (config.inlineNotify()) {
            r.register(new InlineOnlyInvocationPlugin("notify", Receiver.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    ValueNode object = receiver.get();
                    b.add(new FastNotifyNode(object, false, b.bci()));
                    return true;
                }
            });
        }
        if (config.inlineNotifyAll()) {
            r.register(new InlineOnlyInvocationPlugin("notifyAll", Receiver.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    ValueNode object = receiver.get();
                    b.add(new FastNotifyNode(object, true, b.bci()));
                    return true;
                }
            });
        }
    }

    private static void registerClassPlugins(Plugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins.getInvocationPlugins(), Class.class, replacements);

        r.register(new InvocationPlugin("getModifiers", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    ValueNode klass = helper.readKlassFromClass(receiver.get());
                    // Primitive Class case
                    ValueNode nonNullKlass = helper.emitNullReturnGuard(klass, ConstantNode.forInt(Modifier.ABSTRACT | Modifier.FINAL | Modifier.PUBLIC), GraalDirectives.UNLIKELY_PROBABILITY);
                    // other return Klass::_modifier_flags
                    helper.emitFinalReturn(JavaKind.Int, helper.readKlassModifierFlags(nonNullKlass));
                }
                return true;
            }
        });
        r.register(new InvocationPlugin("isInterface", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    ValueNode klass = helper.readKlassFromClass(receiver.get());
                    // Primitive Class case returns false
                    ValueNode klassNonNull = helper.emitNullReturnGuard(klass, ConstantNode.forBoolean(false), GraalDirectives.UNLIKELY_PROBABILITY);
                    ValueNode accessFlags = helper.readKlassAccessFlags(klassNonNull);
                    // return (Klass::_access_flags & Modifier.INTERFACE) == 0 ? false : true
                    LogicNode test = IntegerTestNode.create(accessFlags, ConstantNode.forInt(Modifier.INTERFACE), NodeView.DEFAULT);
                    helper.emitFinalReturn(JavaKind.Boolean, ConditionalNode.create(test, ConstantNode.forBoolean(false), ConstantNode.forBoolean(true), NodeView.DEFAULT));
                }
                return true;
            }
        });
        r.register(new InvocationPlugin("isPrimitive", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    ValueNode klass = helper.readKlassFromClass(receiver.get());
                    LogicNode isNull = b.add(IsNullNode.create(klass));
                    b.addPush(JavaKind.Boolean, ConditionalNode.create(isNull, b.add(forBoolean(true)), b.add(forBoolean(false)), NodeView.DEFAULT));
                }
                return true;
            }
        });
        r.register(new InvocationPlugin("getSuperclass", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    ValueNode klass = helper.readKlassFromClass(receiver.get());
                    ConstantNode nullValue = ConstantNode.defaultForKind(JavaKind.Object);

                    // Primitive Class case returns null
                    PiNode klassNonNull = helper.emitNullReturnGuard(klass, nullValue, GraalDirectives.UNLIKELY_PROBABILITY);

                    // if ((Klass::_access_flags & Modifer.INTERCAE) != 0) return null
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

        r.registerConditional(config.jvmAccIsHiddenClass != 0, new InvocationPlugin("isHidden", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    ValueNode klass = helper.readKlassFromClass(receiver.get());
                    // Primitive Class case returns false
                    ValueNode nonNullKlass = helper.emitNullReturnGuard(klass, ConstantNode.forBoolean(false), GraalDirectives.UNLIKELY_PROBABILITY);
                    // return (Klass::_access_flags & jvmAccIsHiddenClass) == 0 ? false : true
                    ValueNode accessFlags = helper.readKlassAccessFlags(nonNullKlass);
                    LogicNode test = IntegerTestNode.create(accessFlags, ConstantNode.forInt(config.jvmAccIsHiddenClass), NodeView.DEFAULT);
                    helper.emitFinalReturn(JavaKind.Boolean, ConditionalNode.create(test, ConstantNode.forBoolean(false), ConstantNode.forBoolean(true), NodeView.DEFAULT));
                }
                return true;
            }
        });
    }

    private static void registerCallSitePlugins(InvocationPlugins plugins) {
        InvocationPlugin plugin = new InlineOnlyInvocationPlugin("getTarget", Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode callSite = receiver.get();
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

    private static void registerReflectionPlugins(InvocationPlugins plugins, Replacements replacements, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, "jdk.internal.reflect.Reflection", replacements);
        r.register(new InlineOnlyInvocationPlugin("getCallerClass") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, new HotSpotReflectionGetCallerClassNode(MacroParams.of(b, targetMethod)));
                return true;
            }
        });
        r.register(new InvocationPlugin("getClassAccessFlags", Class.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    ValueNode klass = helper.readKlassFromClass(b.nullCheckedValue(arg));
                    // Primitive Class case
                    ValueNode klassNonNull = helper.emitNullReturnGuard(klass, ConstantNode.forInt(Modifier.ABSTRACT | Modifier.FINAL | Modifier.PUBLIC), GraalDirectives.UNLIKELY_PROBABILITY);
                    // Return (Klass::_access_flags & jvmAccWrittenFlags)
                    ValueNode accessFlags = helper.readKlassAccessFlags(klassNonNull);
                    helper.emitFinalReturn(JavaKind.Int, new AndNode(accessFlags, ConstantNode.forInt(config.jvmAccWrittenFlags)));
                }
                return true;
            }
        });
    }

    private static void registerUnsafePlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, "jdk.internal.misc.Unsafe", replacements);
        r.register(new InvocationPlugin("copyMemory0", Receiver.class, Object.class, long.class, Object.class, long.class, long.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode srcBase, ValueNode srcOffset, ValueNode destBase,
                            ValueNode destOffset, ValueNode bytes) {
                b.add(new UnsafeCopyMemoryNode(config.doingUnsafeAccessOffset != Integer.MAX_VALUE, receiver.get(), srcBase, srcOffset, destBase, destOffset, bytes));
                return true;
            }
        });
        r.register(new InvocationPlugin("allocateInstance", Receiver.class, Class.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver unsafe, ValueNode clazz) {
                /* Emits a null-check for the otherwise unused receiver. */
                unsafe.get();
                /*
                 * Note that the provided clazz might not be initialized. The HotSpot lowering
                 * snippet for DynamicNewInstanceNode performs the necessary class initialization
                 * check. Such a DynamicNewInstanceNode is also never constant folded to a
                 * NewInstanceNode.
                 */
                DynamicNewInstanceNode.createAndPush(b, clazz);
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

    private static void registerArrayPlugins(InvocationPlugins plugins, Replacements replacements, GraalHotSpotVMConfig config) {
        Registration r = new Registration(plugins, Array.class, replacements);
        r.setAllowOverwrite(true);
        r.register(new InvocationPlugin("newArray", Class.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode componentType, ValueNode length) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    // If (componentType == null) then deopt
                    ValueNode nonNullComponentType = b.nullCheckedValue(componentType);
                    // Read Class.array_klass
                    ValueNode arrayClass = helper.loadArrayKlass(nonNullComponentType);
                    // Take the fallback path is the array klass is null
                    helper.doFallbackIf(IsNullNode.create(arrayClass), GraalDirectives.UNLIKELY_PROBABILITY);
                    // Otherwise perform the array allocation
                    helper.emitFinalReturn(JavaKind.Object, new DynamicNewArrayNode(nonNullComponentType, length,
                                    true));
                }
                return true;
            }
        });
    }

    private static void registerStringPlugins(InvocationPlugins plugins, Replacements replacements, WordTypes wordTypes, ArrayCopyForeignCalls foreignCalls, GraalHotSpotVMConfig vmConfig) {
        final Registration utf16r = new Registration(plugins, "java.lang.StringUTF16", replacements);
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

    private static void registerThreadPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, Thread.class, replacements);
        r.register(new InvocationPlugin("currentThread") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    ValueNode value = helper.readCurrentThreadObject(true);
                    b.push(JavaKind.Object, value);
                }
                return true;
            }
        });

        if (JavaVersionUtil.JAVA_SPEC >= 19) {
            r.register(new InvocationPlugin("currentCarrierThread") {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                        ValueNode value = helper.readCurrentThreadObject(false);
                        b.push(JavaKind.Object, value);
                    }
                    return true;
                }
            });

            r.register(new InlineOnlyInvocationPlugin("setCurrentThread", Receiver.class, Thread.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode thread) {
                    GraalError.guarantee(Services.IS_IN_NATIVE_IMAGE || isAnnotatedByChangesCurrentThread(b.getMethod()), "method changes current Thread but is not annotated ChangesCurrentThread");
                    try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                        receiver.get();
                        helper.setCurrentThread(thread);
                    }
                    return true;
                }
            });
        }

        if (JavaVersionUtil.JAVA_SPEC >= 20) {
            r.registerConditional(config.threadScopedValueCacheOffset != -1, new InvocationPlugin("scopedValueCache") {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                        b.push(JavaKind.Object, helper.readThreadScopedValueCache());
                    }
                    return true;
                }
            });

            r.registerConditional(config.threadScopedValueCacheOffset != -1, new InvocationPlugin("setScopedValueCache", Object[].class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode cache) {
                    try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                        helper.setThreadScopedValueCache(cache);
                    }
                    return true;
                }
            });
        }
    }

    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/1fc726a8b34fcd41dae12a6d7c63232f9ccef3f4/src/hotspot/share/opto/library_call.cpp#L2861-L2922",
              sha1 = "c2d981ab918e2ca607835df010221ba0503a0cb2")
    // @formatter:on
    private static void inlineNativeNotifyJvmtiFunctions(GraalHotSpotVMConfig config, GraphBuilderContext b, ResolvedJavaMethod targetMethod, ForeignCallDescriptor descriptor,
                    ValueNode virtualThread, ValueNode hide) {
        // When notifications are disabled then just update the VTMS transition bit and return.
        // Otherwise, the bit is updated in the given function call implementing JVMTI notification
        // protocol.
        try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
            GraalError.guarantee(config.virtualThreadVTMSNotifyJvmtiEvents != -1L, "JvmtiVTMSTransitionDisabler::_VTMS_notify_jvmti_events is not exported");
            OffsetAddressNode address = OffsetAddressNode.create(helper.asWord(config.virtualThreadVTMSNotifyJvmtiEvents));
            ValueNode notifyJvmtiEnabled = b.add(new JavaReadNode(JavaKind.Boolean, address, HOTSPOT_VTMS_NOTIFY_JVMTI_EVENTS, BarrierType.NONE, MemoryOrderMode.PLAIN, false));
            LogicNode testNotifyJvmtiEnabled = IntegerEqualsNode.create(notifyJvmtiEnabled, ConstantNode.forBoolean(true), NodeView.DEFAULT);

            StructuredGraph graph = b.getGraph();

            CurrentJavaThreadNode javaThread = graph.addOrUniqueWithInputs(new CurrentJavaThreadNode(helper.getWordKind()));
            BeginNode trueSuccessor = graph.add(new BeginNode());
            BeginNode falseSuccessor = graph.add(new BeginNode());

            ProfileData.BranchProbabilityData probability = ProfileData.BranchProbabilityData.injected(NOT_FREQUENT_PROBABILITY);
            b.add(new IfNode(testNotifyJvmtiEnabled, trueSuccessor, falseSuccessor, probability));

            // if notifyJvmti enabled then make a call to the given runtime function
            ForeignCallNode runtimeCall = graph.add(new ForeignCallNode(descriptor, virtualThread, hide, javaThread));
            trueSuccessor.setNext(runtimeCall);
            runtimeCall.setStateAfter(b.getInvocationPluginReturnState(JavaKind.Void, null));
            EndNode trueSuccessorEnd = graph.add(new EndNode());
            runtimeCall.setNext(trueSuccessorEnd);

            // else set hide value to the VTMS transition bit in current JavaThread and
            // VirtualThread object
            GraalError.guarantee(config.threadIsInVTMSTransitionOffset != -1L, "JavaThread::_is_in_VTMS_transition is not exported");
            OffsetAddressNode jtAddress = graph.addOrUniqueWithInputs(new OffsetAddressNode(javaThread, helper.asWord(config.threadIsInVTMSTransitionOffset)));
            JavaWriteNode jtWrite = b.add(new JavaWriteNode(JavaKind.Boolean, jtAddress, HOTSPOT_JAVA_THREAD_IS_IN_VTMS_TRANSITION, hide, BarrierType.NONE, false));
            falseSuccessor.setNext(jtWrite);

            GraalError.guarantee(config.javaLangThreadIsInVTMSTransitonOffset != -1L, "java_lang_Thread::_jvmti_is_in_VTMS_transition_offset is not exported");
            OffsetAddressNode vtAddress = graph.addOrUniqueWithInputs(new OffsetAddressNode(virtualThread, helper.asWord(config.javaLangThreadIsInVTMSTransitonOffset)));
            b.add(new JavaWriteNode(JavaKind.Boolean, vtAddress, HOTSPOT_JAVA_LANG_THREAD_IS_IN_VTMS_TRANSITION, hide, BarrierType.NONE, false));

            EndNode falseSuccessorEnd = b.add(new EndNode());

            MergeNode merge = b.add(new MergeNode());
            merge.addForwardEnd(trueSuccessorEnd);
            merge.addForwardEnd(falseSuccessorEnd);
        }
    }

    private static void registerVirtualThreadPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        if (JavaVersionUtil.JAVA_SPEC >= 21 && config.supportJVMTIVThreadNotification()) {
            Registration r = new Registration(plugins, "java.lang.VirtualThread", replacements);
            r.register(new InvocationPlugin("notifyJvmtiStart", Receiver.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    if (config.doJVMTIVirtualThreadTransitions) {
                        ValueNode nonNullReceiver = receiver.get();
                        inlineNativeNotifyJvmtiFunctions(config, b, targetMethod, SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_START, nonNullReceiver, ConstantNode.forBoolean(false, b.getGraph()));
                    }
                    return true;
                }
            });
            r.register(new InvocationPlugin("notifyJvmtiEnd", Receiver.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    if (config.doJVMTIVirtualThreadTransitions) {
                        ValueNode nonNullReceiver = receiver.get();
                        inlineNativeNotifyJvmtiFunctions(config, b, targetMethod, SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_END, nonNullReceiver, ConstantNode.forBoolean(true, b.getGraph()));
                    }
                    return true;
                }
            });
            r.register(new InvocationPlugin("notifyJvmtiMount", Receiver.class, boolean.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode hide) {
                    if (config.doJVMTIVirtualThreadTransitions) {
                        ValueNode nonNullReceiver = receiver.get();
                        inlineNativeNotifyJvmtiFunctions(config, b, targetMethod, SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_MOUNT, nonNullReceiver, hide);
                    }
                    return true;
                }
            });
            r.register(new InvocationPlugin("notifyJvmtiUnmount", Receiver.class, boolean.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode hide) {
                    if (config.doJVMTIVirtualThreadTransitions) {
                        ValueNode nonNullReceiver = receiver.get();
                        inlineNativeNotifyJvmtiFunctions(config, b, targetMethod, SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_UNMOUNT, nonNullReceiver, hide);
                    }
                    return true;
                }
            });
            r.register(new InvocationPlugin("notifyJvmtiHideFrames", Receiver.class, boolean.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode hide) {
                    if (config.doJVMTIVirtualThreadTransitions) {
                        try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                            // unconditionally update the temporary VTMS transition bit in current
                            // JavaThread
                            GraalError.guarantee(config.threadIsInTmpVTMSTransitionOffset != -1L, "JavaThread::_is_in_tmp_VTMS_transition is not exported");
                            CurrentJavaThreadNode javaThread = b.add(new CurrentJavaThreadNode(helper.getWordKind()));
                            OffsetAddressNode address = b.add(new OffsetAddressNode(javaThread, helper.asWord(config.threadIsInTmpVTMSTransitionOffset)));
                            b.add(new JavaWriteNode(JavaKind.Boolean, address, HOTSPOT_JAVA_THREAD_IS_IN_TMP_VTMS_TRANSITION, hide, BarrierType.NONE, false));
                        }
                    }
                    return true;
                }
            });
        }
    }

    public static boolean isIntrinsicName(GraalHotSpotVMConfig config, String className, String name) {
        for (VMIntrinsicMethod intrinsic : config.getStore().getIntrinsics()) {
            if (className.equals(intrinsic.declaringClass)) {
                if (name.equals(intrinsic.name)) {
                    return true;
                }
            }
        }
        return false;
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

    public static class ElectronicCodeBookCryptPlugin extends AESCryptDelegatePlugin {

        ElectronicCodeBookCryptPlugin(CryptMode mode) {
            super(mode, mode.isEncrypt() ? "implECBEncrypt" : "implECBDecrypt",
                            Receiver.class, byte[].class, int.class, int.class, byte[].class, int.class);
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode in, ValueNode inOffset, ValueNode len, ValueNode out, ValueNode outOffset) {
            try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                ResolvedJavaType receiverType = targetMethod.getDeclaringClass();
                ResolvedJavaType typeAESCrypt = getTypeAESCrypt(b.getMetaAccess(), receiverType);

                ValueNode nonNullReceiver = receiver.get();
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

    public static class GaloisCounterModeCryptPlugin extends AESCryptDelegatePlugin {

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

    private static void registerAESPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements, Architecture arch) {
        Registration r = new Registration(plugins, "com.sun.crypto.provider.CipherBlockChaining", replacements);
        r.registerConditional(CipherBlockChainingAESNode.isSupported(arch), new HotSpotCipherBlockChainingCryptPlugin(CryptMode.ENCRYPT));
        r.registerConditional(CipherBlockChainingAESNode.isSupported(arch), new HotSpotCipherBlockChainingCryptPlugin(CryptMode.DECRYPT));

        r = new Registration(plugins, "com.sun.crypto.provider.ElectronicCodeBook", replacements);
        r.registerConditional(config.electronicCodeBookEncrypt != 0L, new ElectronicCodeBookCryptPlugin(CryptMode.ENCRYPT));
        r.registerConditional(config.electronicCodeBookDecrypt != 0L, new ElectronicCodeBookCryptPlugin(CryptMode.DECRYPT));

        if (JavaVersionUtil.JAVA_SPEC >= 18) {
            r = new Registration(plugins, "com.sun.crypto.provider.GaloisCounterMode", replacements);
            r.registerConditional(config.galoisCounterModeCrypt != 0L, new GaloisCounterModeCryptPlugin());
        }

        r = new Registration(plugins, "com.sun.crypto.provider.CounterMode", replacements);
        r.registerConditional(CounterModeAESNode.isSupported(arch), new CounterModeCryptPlugin() {
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

    private static void registerAdler32Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, "java.util.zip.Adler32", replacements);
        r.registerConditional(config.updateBytesAdler32 != 0L, new InlineOnlyInvocationPlugin("updateBytes", int.class, byte[].class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode adler, ValueNode src, ValueNode off, ValueNode len) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode addr = helper.arrayElementPointer(src, JavaKind.Byte, off);
                    ForeignCallNode call = new ForeignCallNode(HotSpotBackend.UPDATE_BYTES_ADLER32, adler, addr, len);
                    b.addPush(JavaKind.Int, call);
                }
                return true;
            }
        });
        r.registerConditional(config.updateBytesAdler32 != 0L, new InlineOnlyInvocationPlugin("updateByteBuffer", int.class, long.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode adler, ValueNode addr, ValueNode off, ValueNode len) {
                ValueNode buff = b.add(new ComputeObjectAddressNode(addr, off));
                ForeignCallNode call = new ForeignCallNode(HotSpotBackend.UPDATE_BYTES_ADLER32, adler, buff, len);
                b.addPush(JavaKind.Int, call);
                return true;
            }
        });
    }

    private static void registerBigIntegerPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, BigInteger.class, replacements);
        r.registerConditional(config.useMontgomeryMultiplyIntrinsic(), new InvocationPlugin("implMontgomeryMultiply", int[].class, int[].class, int[].class, int.class, long.class, int[].class) {

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
        });
        r.registerConditional(config.useMontgomerySquareIntrinsic(), new InvocationPlugin("implMontgomerySquare", int[].class, int[].class, int.class, long.class, int[].class) {
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
        });
        r.registerConditional(config.bigIntegerLeftShiftWorker != 0L, new InvocationPlugin("shiftLeftImplWorker", int[].class, int[].class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode newArr, ValueNode oldArr, ValueNode newIdx, ValueNode shiftCount,
                            ValueNode numIter) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    b.add(new ForeignCallNode(HotSpotBackend.BIGINTEGER_LEFT_SHIFT_WORKER, helper.arrayStart(newArr, JavaKind.Int), helper.arrayStart(oldArr, JavaKind.Int), newIdx, shiftCount,
                                    numIter));
                }
                return true;
            }
        });
        r.registerConditional(config.bigIntegerRightShiftWorker != 0L, new InvocationPlugin("shiftRightImplWorker", int[].class, int[].class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode newArr, ValueNode oldArr, ValueNode newIdx, ValueNode shiftCount,
                            ValueNode numIter) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    b.add(new ForeignCallNode(HotSpotBackend.BIGINTEGER_RIGHT_SHIFT_WORKER, helper.arrayStart(newArr, JavaKind.Int), helper.arrayStart(oldArr, JavaKind.Int), newIdx, shiftCount,
                                    numIter));
                }
                return true;
            }
        });
    }

    static class DigestInvocationPlugin extends InvocationPlugin {
        private final ForeignCallDescriptor descriptor;

        DigestInvocationPlugin(ForeignCallDescriptor descriptor) {
            super("implCompress0", Receiver.class, byte[].class, int.class);
            this.descriptor = descriptor;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode buf, ValueNode ofs) {
            try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                ValueNode realReceiver = b.add(new PiNode(receiver.get(), targetMethod.getDeclaringClass(), false, true));
                ResolvedJavaField stateField = helper.getField(targetMethod.getDeclaringClass(), "state");
                ValueNode state = helper.loadField(realReceiver, stateField);
                ValueNode bufAddr = helper.arrayElementPointer(buf, JavaKind.Byte, ofs);
                ValueNode stateAddr = helper.arrayStart(state, stateField.getType().getComponentType().getJavaKind());
                if (descriptor.equals(SHA3_IMPL_COMPRESS)) {
                    ResolvedJavaField blockSizeField = helper.getField(targetMethod.getDeclaringClass(), "blockSize");
                    ValueNode blockSize = helper.loadField(realReceiver, blockSizeField);
                    b.add(new ForeignCallNode(descriptor, bufAddr, stateAddr, blockSize));
                } else {
                    b.add(new ForeignCallNode(descriptor, bufAddr, stateAddr));
                }
            }
            return true;
        }
    }

    private static void registerSHAPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        boolean useMD5 = config.md5ImplCompressMultiBlock != 0L;
        boolean useSha1 = config.useSHA1Intrinsics();
        boolean useSha256 = config.useSHA256Intrinsics();
        boolean useSha512 = config.useSHA512Intrinsics();
        boolean useSha3 = config.sha3ImplCompressMultiBlock != 0L;

        boolean implCompressMultiBlock0Enabled = isIntrinsicName(config, "sun/security/provider/DigestBase", "implCompressMultiBlock0") && (useMD5 || useSha1 || useSha256 || useSha512 || useSha3);
        Registration r = new Registration(plugins, "sun.security.provider.DigestBase", replacements);
        r.registerConditional(implCompressMultiBlock0Enabled, new SnippetSubstitutionInvocationPlugin<>(DigestBaseSnippets.Templates.class,
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

    private static void registerBase64Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, MetaAccessProvider metaAccess, Replacements replacements) {
        Registration r = new Registration(plugins, "java.util.Base64$Encoder", replacements);
        r.registerConditional(config.base64EncodeBlock != 0L, new InvocationPlugin("encodeBlock", Receiver.class, byte[].class, int.class, int.class, byte[].class, int.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src,
                            ValueNode sp, ValueNode sl, ValueNode dst, ValueNode dp, ValueNode isURL) {
                int byteArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Byte);
                ComputeObjectAddressNode srcAddress = b.add(new ComputeObjectAddressNode(src, ConstantNode.forInt(byteArrayBaseOffset)));
                ComputeObjectAddressNode dstAddress = b.add(new ComputeObjectAddressNode(dst, ConstantNode.forInt(byteArrayBaseOffset)));
                b.add(new ForeignCallNode(BASE64_ENCODE_BLOCK, srcAddress, sp, sl, dstAddress, dp, isURL));
                return true;
            }
        });
        r = new Registration(plugins, "java.util.Base64$Decoder", replacements);
        if (config.base64DecodeBlock != 0L) {
            if (GraalHotSpotVMConfig.base64DecodeBlockHasIsMIMEParameter()) {
                // JDK-8268276 - added isMIME parameter
                r.register(new InvocationPlugin("decodeBlock", Receiver.class, byte[].class, int.class, int.class, byte[].class, int.class, boolean.class, boolean.class) {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src,
                                    ValueNode sp, ValueNode sl, ValueNode dst, ValueNode dp, ValueNode isURL, ValueNode isMime) {
                        int byteArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Byte);
                        ComputeObjectAddressNode srcAddress = b.add(new ComputeObjectAddressNode(src, ConstantNode.forInt(byteArrayBaseOffset)));
                        ComputeObjectAddressNode dstAddress = b.add(new ComputeObjectAddressNode(dst, ConstantNode.forInt(byteArrayBaseOffset)));
                        ForeignCallNode call = new ForeignCallNode(BASE64_DECODE_BLOCK, srcAddress, sp, sl, dstAddress, dp, isURL, isMime);
                        b.addPush(JavaKind.Int, call);
                        return true;
                    }
                });
            } else {
                r.register(new InvocationPlugin("decodeBlock", Receiver.class, byte[].class, int.class, int.class, byte[].class, int.class, boolean.class) {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src,
                                    ValueNode sp, ValueNode sl, ValueNode dst, ValueNode dp, ValueNode isURL) {
                        int byteArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Byte);
                        ComputeObjectAddressNode srcAddress = b.add(new ComputeObjectAddressNode(src, ConstantNode.forInt(byteArrayBaseOffset)));
                        ComputeObjectAddressNode dstAddress = b.add(new ComputeObjectAddressNode(dst, ConstantNode.forInt(byteArrayBaseOffset)));
                        ForeignCallNode call = new ForeignCallNode(BASE64_DECODE_BLOCK, srcAddress, sp, sl, dstAddress, dp, isURL);
                        b.addPush(JavaKind.Int, call);
                        return true;
                    }
                });
            }
        }
    }

    private static void registerCRC32Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, CRC32.class, replacements);
        r.registerConditional(config.useCRC32Intrinsics() && config.crcTableAddress != 0, new InvocationPlugin("update", int.class, int.class) {
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
        });
        r.registerConditional(config.useCRC32Intrinsics(), new InvocationPlugin("updateBytes0", int.class, byte[].class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode crc, ValueNode buf, ValueNode off, ValueNode len) {
                int byteArrayBaseOffset = b.getMetaAccess().getArrayBaseOffset(JavaKind.Byte);
                ValueNode bufAddr = b.add(new ComputeObjectAddressNode(buf, new AddNode(ConstantNode.forInt(byteArrayBaseOffset), off)));
                b.addPush(JavaKind.Int, new ForeignCallNode(UPDATE_BYTES_CRC32, crc, bufAddr, len));
                return true;
            }
        });
        r.registerConditional(config.useCRC32Intrinsics(), new InvocationPlugin("updateByteBuffer0", int.class, long.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode crc, ValueNode addr, ValueNode off, ValueNode len) {
                ValueNode bufAddr = b.add(new AddNode(addr, new SignExtendNode(off, 32, 64)));
                b.addPush(JavaKind.Int, new ForeignCallNode(UPDATE_BYTES_CRC32, crc, bufAddr, len));
                return true;
            }
        });
    }

    private static void registerCRC32CPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, "java.util.zip.CRC32C", replacements);
        r.registerConditional(config.useCRC32CIntrinsics(), new InvocationPlugin("updateBytes", int.class, byte[].class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode crc, ValueNode buf, ValueNode off, ValueNode end) {
                int byteArrayBaseOffset = b.getMetaAccess().getArrayBaseOffset(JavaKind.Byte);
                ValueNode bufAddr = b.add(new ComputeObjectAddressNode(buf, new AddNode(ConstantNode.forInt(byteArrayBaseOffset), off)));
                b.addPush(JavaKind.Int, new ForeignCallNode(UPDATE_BYTES_CRC32C, crc, bufAddr, new SubNode(end, off)));
                return true;
            }
        });
        r.registerConditional(config.useCRC32CIntrinsics(), new InvocationPlugin("updateDirectByteBuffer", int.class, long.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode crc, ValueNode addr, ValueNode off, ValueNode end) {
                ValueNode bufAddr = b.add(new AddNode(addr, new SignExtendNode(off, 32, 64)));
                b.addPush(JavaKind.Int, new ForeignCallNode(UPDATE_BYTES_CRC32C, crc, bufAddr, new SubNode(end, off)));
                return true;
            }
        });
    }

    private static void registerPoly1305Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, "com.sun.crypto.provider.Poly1305", replacements);
        r.registerConditional(config.poly1305ProcessBlocks != 0L, new InvocationPlugin("processMultipleBlocks", Receiver.class, byte[].class, int.class, int.class, long[].class, long[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode input, ValueNode offset, ValueNode length, ValueNode aLimbs, ValueNode rLimbs) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
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
        });
    }

    private static void registerChaCha20Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, "com.sun.crypto.provider.ChaCha20Cipher", replacements);
        r.registerConditional(config.chacha20Block != 0L, new InvocationPlugin("implChaCha20Block", int[].class, byte[].class) {
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
        });
    }

    private static void registerArraysSupportPlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, "jdk.internal.util.ArraysSupport", replacements);
        r.register(new InvocationPlugin("vectorizedMismatch", Object.class, long.class, Object.class, long.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver,
                            ValueNode aObject, ValueNode aOffset, ValueNode bObject, ValueNode bOffset, ValueNode length, ValueNode log2ArrayIndexScale) {
                ValueNode aAddr = b.add(new ComputeObjectAddressNode(aObject, aOffset));
                ValueNode bAddr = b.add(new ComputeObjectAddressNode(bObject, bOffset));
                b.addPush(JavaKind.Int, new VectorizedMismatchNode(aAddr, bAddr, length, log2ArrayIndexScale));
                return true;
            }
        });
    }

    private static void registerReferencePlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, Reference.class, replacements);
        r.register(new ReachabilityFencePlugin() {
            @Override
            protected boolean useExplicitReachabilityFence(GraphBuilderContext b) {
                return Options.ForceExplicitReachabilityFence.getValue(b.getOptions());
            }
        });
        r.register(new InlineOnlyInvocationPlugin("refersTo0", Receiver.class, Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode o) {
                ValueNode offset = b.add(ConstantNode.forLong(HotSpotReplacementsUtil.referentOffset(b.getMetaAccess())));
                AddressNode address = b.add(new OffsetAddressNode(receiver.get(), offset));
                FieldLocationIdentity locationIdentity = new FieldLocationIdentity(HotSpotReplacementsUtil.referentField(b.getMetaAccess()));
                JavaReadNode read = b.add(new JavaReadNode(StampFactory.object(), JavaKind.Object, address, locationIdentity, BarrierType.WEAK_REFERS_TO, MemoryOrderMode.PLAIN, true));
                LogicNode objectEquals = b.add(ObjectEqualsNode.create(b.getConstantReflection(), b.getMetaAccess(), b.getOptions(), read, o, NodeView.DEFAULT));
                b.addPush(JavaKind.Boolean, ConditionalNode.create(objectEquals, b.add(forBoolean(true)), b.add(forBoolean(false)), NodeView.DEFAULT));
                return true;
            }
        });
        r = new Registration(plugins, PhantomReference.class, replacements);
        r.register(new InlineOnlyInvocationPlugin("refersTo0", Receiver.class, Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode o) {
                ValueNode offset = b.add(ConstantNode.forLong(HotSpotReplacementsUtil.referentOffset(b.getMetaAccess())));
                AddressNode address = b.add(new OffsetAddressNode(receiver.get(), offset));
                FieldLocationIdentity locationIdentity = new FieldLocationIdentity(HotSpotReplacementsUtil.referentField(b.getMetaAccess()));
                JavaReadNode read = b.add(new JavaReadNode(StampFactory.object(), JavaKind.Object, address, locationIdentity, BarrierType.PHANTOM_REFERS_TO, MemoryOrderMode.PLAIN, true));
                LogicNode objectEquals = b.add(ObjectEqualsNode.create(b.getConstantReflection(), b.getMetaAccess(), b.getOptions(), read, o, NodeView.DEFAULT));
                b.addPush(JavaKind.Boolean, ConditionalNode.create(objectEquals, b.add(forBoolean(true)), b.add(forBoolean(false)), NodeView.DEFAULT));
                return true;
            }
        });
    }

    private static void registerInstrumentationImplPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, "sun.instrument.InstrumentationImpl", replacements);
        r.register(new InlineOnlyInvocationPlugin("getObjectSize0", Receiver.class, long.class, Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode nativeAgent, ValueNode objectToSize) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    ValueNode objectNonNull = b.nullCheckedValue(objectToSize);
                    StructuredGraph graph = b.getGraph();
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

                    b.add(new IfNode(isArray, arrayLengthNode, instanceBranch, BranchProbabilityData.injected(0.5D)));
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
}
