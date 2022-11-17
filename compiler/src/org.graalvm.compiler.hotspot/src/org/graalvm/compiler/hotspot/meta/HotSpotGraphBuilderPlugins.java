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
package org.graalvm.compiler.hotspot.meta;

import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;
import static org.graalvm.compiler.core.common.calc.Condition.EQ;
import static org.graalvm.compiler.core.common.calc.Condition.NE;
import static org.graalvm.compiler.hotspot.HotSpotBackend.BASE64_DECODE_BLOCK;
import static org.graalvm.compiler.hotspot.HotSpotBackend.BASE64_ENCODE_BLOCK;
import static org.graalvm.compiler.hotspot.HotSpotBackend.CRC_TABLE_LOCATION;
import static org.graalvm.compiler.hotspot.HotSpotBackend.ELECTRONIC_CODEBOOK_DECRYPT_AESCRYPT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.ELECTRONIC_CODEBOOK_ENCRYPT_AESCRYPT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.UPDATE_BYTES_CRC32;
import static org.graalvm.compiler.hotspot.HotSpotBackend.UPDATE_BYTES_CRC32C;
import static org.graalvm.compiler.java.BytecodeParserOptions.InlineDuringParsing;
import static org.graalvm.compiler.nodes.ConstantNode.forBoolean;

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
import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.calc.Condition;
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
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ProfileData.BranchProbabilityData;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerTestNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.nodes.calc.XorNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.LoadHubNode;
import org.graalvm.compiler.nodes.extended.ObjectIsArrayNode;
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
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
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
                    TargetDescription target) {
        InvocationPlugins invocationPlugins = new HotSpotInvocationPlugins(graalRuntime, config, compilerConfiguration, target, options);

        Plugins plugins = new Plugins(invocationPlugins);
        plugins.appendNodePlugin(new HotSpotExceptionDispatchPlugin(config, wordTypes.getWordKind()));
        if (!IS_IN_NATIVE_IMAGE) {
            // In libgraal all word related operations have been fully processed so this is unneeded
            HotSpotWordOperationPlugin wordOperationPlugin = new HotSpotWordOperationPlugin(snippetReflection, wordTypes);
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
        compilerConfiguration.registerGraphBuilderPlugins(plugins, options);

        invocationPlugins.defer(new Runnable() {

            @Override
            public void run() {
                registerObjectPlugins(invocationPlugins, config, replacements);
                registerClassPlugins(plugins, config, replacements);
                registerSystemPlugins(invocationPlugins);
                registerThreadPlugins(invocationPlugins, config, replacements);
                registerCallSitePlugins(invocationPlugins);
                registerReflectionPlugins(invocationPlugins, replacements, config);
                registerAESPlugins(invocationPlugins, config, replacements, target.arch);
                registerAdler32Plugins(invocationPlugins, config, replacements);
                registerCRC32Plugins(invocationPlugins, config, replacements);
                registerCRC32CPlugins(invocationPlugins, config, replacements);
                registerBigIntegerPlugins(invocationPlugins, config, replacements);
                registerSHAPlugins(invocationPlugins, config, replacements);
                registerMD5Plugins(invocationPlugins, config, replacements);
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
                registerContinuationPlugins(invocationPlugins, config, replacements);
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

        Registration tl = new Registration(plugins, "org.graalvm.compiler.truffle.runtime.hotspot.HotSpotFastThreadLocal");
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
                    ValueNode newArray = b.add(new NewArrayNode(b.getMetaAccess().lookupJavaType(Byte.TYPE), b.add(new LeftShiftNode(length, ConstantNode.forInt(1))), false));
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
                    ValueNode length = helper.sub(srcEnd, srcBegin);
                    helper.intrinsicRangeCheck(srcBegin, Condition.LT, ConstantNode.forInt(0));
                    helper.intrinsicRangeCheck(length, Condition.LT, ConstantNode.forInt(0));
                    ValueNode srcLimit = helper.sub(helper.shr(helper.length(value), 1), length);
                    helper.intrinsicRangeCheck(srcBegin, Condition.GT, srcLimit);
                    ValueNode limit = helper.sub(helper.length(dst), length);
                    helper.intrinsicRangeCheck(dstBegin, Condition.GT, limit);
                    b.add(new ArrayCopyCallNode(foreignCalls, wordTypes, value, srcBegin, dst, dstBegin, length, JavaKind.Char, JavaKind.Byte, JavaKind.Char, false, true, true,
                                    vmConfig.heapWordSize));
                }
                return true;
            }
        });
    }

    private static void registerThreadPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, Thread.class, replacements);
        r.register(new InvocationPlugin("currentThread") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    ValueNode value = helper.readCurrentThreadObject();
                    b.push(JavaKind.Object, value);
                }
                return true;
            }
        });

        // This substitution is no longer in used when threadObj is a handle
        r.registerConditional(config.osThreadInterruptedOffset != Integer.MAX_VALUE && !config.threadObjectFieldIsHandle, new InvocationPlugin("isInterrupted", Receiver.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode clearInterrupted) {
                try (HotSpotInvocationPluginHelper helper = new HotSpotInvocationPluginHelper(b, targetMethod, config)) {
                    ValueNode receiverThreadObject = receiver.get();
                    CurrentJavaThreadNode thread = b.add(new CurrentJavaThreadNode(helper.getWordKind()));
                    ValueNode currentThreadObject = helper.readCurrentThreadObject(thread);

                    // if (this != Thread.currentThread()) do fallback
                    helper.doFallbackIf(receiverThreadObject, NE, currentThreadObject, GraalDirectives.UNLIKELY_PROBABILITY);
                    ValueNode osThread = helper.readOsThread(thread);
                    ValueNode interrupted = helper.readOsThreadInterrupted(osThread);

                    // if (thread._osthread._isinterrupted == 0) return false
                    helper.emitReturnIf(interrupted, EQ, ConstantNode.forInt(0), ConstantNode.forBoolean(false), GraalDirectives.LIKELY_PROBABILITY);

                    // if (clearInterrupted) fallback to invoke
                    helper.doFallbackIf(clearInterrupted, EQ, ConstantNode.forBoolean(true), GraalDirectives.UNLIKELY_PROBABILITY);

                    // return interrupted == 0 ? false : true
                    LogicNode test = helper.createCompare(interrupted, CanonicalCondition.EQ, ConstantNode.forInt(0));
                    helper.emitFinalReturn(JavaKind.Boolean, ConditionalNode.create(test, ConstantNode.forBoolean(false), ConstantNode.forBoolean(true), NodeView.DEFAULT));
                }
                return true;
            }
        });
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
                ForeignCallNode call = b.add(new ForeignCallNode(mode.isEncrypt() ? ELECTRONIC_CODEBOOK_ENCRYPT_AESCRYPT : ELECTRONIC_CODEBOOK_DECRYPT_AESCRYPT,
                                inAddr, outAddr, kAddr, len));
                helper.emitFinalReturn(JavaKind.Int, call);
                return true;
            }
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
        r.registerConditional(config.useMulAddIntrinsic(), new InvocationPlugin("implMulAdd", int[].class, int[].class, int.class, int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode out, ValueNode in, ValueNode offset, ValueNode len, ValueNode k) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    ValueNode outNonNull = b.nullCheckedValue(out);
                    ValueNode outNonNullLength = b.add(new ArrayLengthNode(outNonNull));
                    ValueNode newOffset = new SubNode(outNonNullLength, offset);
                    ForeignCallNode call = new ForeignCallNode(HotSpotBackend.MUL_ADD, helper.arrayStart(outNonNull, JavaKind.Int), helper.arrayStart(in, JavaKind.Int), newOffset, len, k);
                    b.addPush(JavaKind.Int, call);
                }
                return true;
            }
        });
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
        r.registerConditional(config.useSquareToLenIntrinsic(), new InvocationPlugin("implSquareToLen", int[].class, int.class, int[].class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode len, ValueNode z, ValueNode zlen) {
                try (InvocationPluginHelper helper = new InvocationPluginHelper(b, targetMethod)) {
                    // The stub doesn't return the right value for the intrinsic so push it here
                    // and the proper after FrameState will be put on ForeignCallNode by add.
                    b.addPush(JavaKind.Object, z);
                    b.add(new ForeignCallNode(HotSpotBackend.SQUARE_TO_LEN, helper.arrayStart(x, JavaKind.Int), len, helper.arrayStart(z, JavaKind.Int), zlen));
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

            @Override
            public boolean isOptional() {
                return JavaVersionUtil.JAVA_SPEC < 14;
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

            @Override
            public boolean isOptional() {
                return JavaVersionUtil.JAVA_SPEC < 14;
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
                b.add(new ForeignCallNode(descriptor, bufAddr, stateAddr));
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

        Registration rSha1 = new Registration(plugins, "sun.security.provider.SHA", replacements);
        rSha1.registerConditional(useSha1, new DigestInvocationPlugin(HotSpotBackend.SHA_IMPL_COMPRESS));

        Registration rSha256 = new Registration(plugins, "sun.security.provider.SHA2", replacements);
        rSha256.registerConditional(useSha256, new DigestInvocationPlugin(HotSpotBackend.SHA2_IMPL_COMPRESS));

        Registration rSha512 = new Registration(plugins, "sun.security.provider.SHA5", replacements);
        rSha512.registerConditional(useSha512, new DigestInvocationPlugin(HotSpotBackend.SHA5_IMPL_COMPRESS));

        Registration rSha3 = new Registration(plugins, "sun.security.provider.SHA3", replacements);
        rSha3.registerConditional(config.sha3ImplCompress != 0L, new DigestInvocationPlugin(HotSpotBackend.SHA5_IMPL_COMPRESS));
    }

    private static void registerMD5Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, "sun.security.provider.MD5", replacements);
        r.registerConditional(config.md5ImplCompress != 0L, new DigestInvocationPlugin(HotSpotBackend.MD5_IMPL_COMPRESS));
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
            if (JavaVersionUtil.JAVA_SPEC >= 18) {
                // JDK-8268276 - added isMIME parameter
                r.register(new InvocationPlugin("decodeBlock", Receiver.class, byte[].class, int.class, int.class, byte[].class, int.class, boolean.class, boolean.class) {
                    @Override
                    public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src,
                                    ValueNode sp, ValueNode sl, ValueNode dst, ValueNode dp, ValueNode isURL, ValueNode isMime) {
                        int byteArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Byte);
                        ComputeObjectAddressNode srcAddress = b.add(new ComputeObjectAddressNode(src, ConstantNode.forInt(byteArrayBaseOffset)));
                        ComputeObjectAddressNode dstAddress = b.add(new ComputeObjectAddressNode(dst, ConstantNode.forInt(byteArrayBaseOffset)));
                        ForeignCallNode call = b.add(new ForeignCallNode(BASE64_DECODE_BLOCK, srcAddress, sp, sl, dstAddress, dp, isURL, isMime));
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
                        ForeignCallNode call = b.add(new ForeignCallNode(BASE64_DECODE_BLOCK, srcAddress, sp, sl, dstAddress, dp, isURL));
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
                JavaReadNode read = b.add(new JavaReadNode(StampFactory.object(), JavaKind.Object, address, locationIdentity, BarrierType.WEAK_FIELD, MemoryOrderMode.PLAIN, true));
                LogicNode objectEquals = b.add(ObjectEqualsNode.create(b.getConstantReflection(), b.getMetaAccess(), b.getOptions(), read, o, NodeView.DEFAULT));
                b.addPush(JavaKind.Boolean, ConditionalNode.create(objectEquals, b.add(forBoolean(true)), b.add(forBoolean(false)), NodeView.DEFAULT));
                return true;
            }

            @Override
            public boolean isOptional() {
                return JavaVersionUtil.JAVA_SPEC < 16;
            }
        });
        r = new Registration(plugins, PhantomReference.class, replacements);
        r.register(new InlineOnlyInvocationPlugin("refersTo0", Receiver.class, Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode o) {
                ValueNode offset = b.add(ConstantNode.forLong(HotSpotReplacementsUtil.referentOffset(b.getMetaAccess())));
                AddressNode address = b.add(new OffsetAddressNode(receiver.get(), offset));
                FieldLocationIdentity locationIdentity = new FieldLocationIdentity(HotSpotReplacementsUtil.referentField(b.getMetaAccess()));
                JavaReadNode read = b.add(new JavaReadNode(StampFactory.object(), JavaKind.Object, address, locationIdentity, BarrierType.PHANTOM_FIELD, MemoryOrderMode.PLAIN, true));
                LogicNode objectEquals = b.add(ObjectEqualsNode.create(b.getConstantReflection(), b.getMetaAccess(), b.getOptions(), read, o, NodeView.DEFAULT));
                b.addPush(JavaKind.Boolean, ConditionalNode.create(objectEquals, b.add(forBoolean(true)), b.add(forBoolean(false)), NodeView.DEFAULT));
                return true;
            }

            @Override
            public boolean isOptional() {
                return JavaVersionUtil.JAVA_SPEC < 16;
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

                    int objectAlignmentMask = config.objectAlignment - 1;
                    ValueNode arrayHeaderSize = b.add(AndNode.create(new UnsignedRightShiftNode(layoutHelper, ConstantNode.forInt(config.layoutHelperHeaderSizeShift)),
                                    ConstantNode.forInt(config.layoutHelperHeaderSizeMask), NodeView.DEFAULT));
                    ValueNode arraySize = b.add(AddNode.create(arrayHeaderSize, LeftShiftNode.create(arrayLengthNode, layoutHelper, NodeView.DEFAULT), NodeView.DEFAULT));
                    ValueNode arraySizeMasked = b.add(AndNode.create(AddNode.create(arraySize, ConstantNode.forInt(objectAlignmentMask), NodeView.DEFAULT),
                                    ConstantNode.forInt(~objectAlignmentMask), NodeView.DEFAULT));

                    EndNode instanceBranch = graph.add(new EndNode());
                    ValueNode instanceSize = b.add(AndNode.create(layoutHelper, ConstantNode.forInt(~(Long.BYTES - 1)), NodeView.DEFAULT));

                    b.add(new IfNode(isArray, arrayLengthNode, instanceBranch, BranchProbabilityData.unknown()));
                    MergeNode merge = b.add(new MergeNode());
                    merge.addForwardEnd(arrayBranch);
                    merge.addForwardEnd(instanceBranch);
                    b.addPush(JavaKind.Long, SignExtendNode.create(new ValuePhiNode(StampFactory.positiveInt(), merge, new ValueNode[]{arraySizeMasked, instanceSize}), 64, NodeView.DEFAULT));
                }
                return true;
            }
        });
    }

    private static void registerContinuationPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, "jdk.internal.vm.Continuation", replacements);
        r.registerConditional(config.contDoYield != 0L, new ForeignCallPlugin(HotSpotBackend.CONTINUATION_DO_YIELD, "doYield"));
    }
}
