/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfigAccess.JDK;
import static org.graalvm.compiler.hotspot.HotSpotBackend.BASE64_ENCODE_BLOCK;
import static org.graalvm.compiler.hotspot.HotSpotBackend.CRC_TABLE_LOCATION;
import static org.graalvm.compiler.hotspot.HotSpotBackend.GHASH_PROCESS_BLOCKS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.UPDATE_BYTES_CRC32;
import static org.graalvm.compiler.hotspot.HotSpotBackend.UPDATE_BYTES_CRC32C;
import static org.graalvm.compiler.hotspot.meta.HotSpotAOTProfilingPlugin.Options.TieredAOT;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_THREAD_OBJECT_LOCATION;
import static org.graalvm.compiler.java.BytecodeParserOptions.InlineDuringParsing;
import static org.graalvm.compiler.nodes.ConstantNode.forBoolean;

import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.VolatileCallSite;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.zip.CRC32;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.HotSpotMarkId;
import org.graalvm.compiler.hotspot.nodes.CurrentJavaThreadNode;
import org.graalvm.compiler.hotspot.nodes.GraalHotSpotVMConfigNode;
import org.graalvm.compiler.hotspot.replacements.AESCryptSubstitutions;
import org.graalvm.compiler.hotspot.replacements.BigIntegerSubstitutions;
import org.graalvm.compiler.hotspot.replacements.CallSiteTargetNode;
import org.graalvm.compiler.hotspot.replacements.CipherBlockChainingSubstitutions;
import org.graalvm.compiler.hotspot.replacements.ClassGetHubNode;
import org.graalvm.compiler.hotspot.replacements.CounterModeSubstitutions;
import org.graalvm.compiler.hotspot.replacements.DigestBaseSubstitutions;
import org.graalvm.compiler.hotspot.replacements.FastNotifyNode;
import org.graalvm.compiler.hotspot.replacements.HotSpotArraySubstitutions;
import org.graalvm.compiler.hotspot.replacements.HotSpotClassSubstitutions;
import org.graalvm.compiler.hotspot.replacements.HotSpotReflectionGetCallerClassNode;
import org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import org.graalvm.compiler.hotspot.replacements.IdentityHashCodeNode;
import org.graalvm.compiler.hotspot.replacements.ObjectCloneNode;
import org.graalvm.compiler.hotspot.replacements.ReflectionSubstitutions;
import org.graalvm.compiler.hotspot.replacements.ThreadSubstitutions;
import org.graalvm.compiler.hotspot.replacements.UnsafeCopyMemoryNode;
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerEqualsNode;
import org.graalvm.compiler.nodes.calc.IntegerLessThanNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.ObjectEqualsNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.nodes.calc.XorNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.graphbuilderconf.ForeignCallPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedPluginFactory;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.java.ArrayLengthNode;
import org.graalvm.compiler.nodes.java.DynamicNewInstanceNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.replacements.InlineDuringParsingPlugin;
import org.graalvm.compiler.replacements.MethodHandlePlugin;
import org.graalvm.compiler.replacements.NodeIntrinsificationProvider;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.compiler.replacements.StandardGraphBuilderPlugins;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyCallNode;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyForeignCalls;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyNode;
import org.graalvm.compiler.replacements.nodes.MacroNode.MacroParams;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.VMIntrinsicMethod;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.services.Services;
import sun.misc.Unsafe;

/**
 * Defines the {@link Plugins} used when running on HotSpot.
 */
public class HotSpotGraphBuilderPlugins {

    /**
     * Creates a {@link Plugins} object that should be used when running on HotSpot.
     *
     * @param constantReflection
     * @param snippetReflection
     * @param foreignCalls
     * @param options
     * @param target
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
        InvocationPlugins invocationPlugins = new HotSpotInvocationPlugins(graalRuntime, config, compilerConfiguration);

        Plugins plugins = new Plugins(invocationPlugins);
        if (!IS_IN_NATIVE_IMAGE) {
            // In libgraal all word related operations have been fully processed so this is unneeded
            HotSpotWordOperationPlugin wordOperationPlugin = new HotSpotWordOperationPlugin(snippetReflection, wordTypes);
            HotSpotNodePlugin nodePlugin = new HotSpotNodePlugin(wordOperationPlugin, config, wordTypes);

            plugins.appendTypePlugin(nodePlugin);
            plugins.appendNodePlugin(nodePlugin);
        }
        if (!GeneratePIC.getValue(options)) {
            plugins.appendNodePlugin(new MethodHandlePlugin(constantReflection.getMethodHandleAccess(), !config.supportsMethodHandleDeoptimizationEntry()));
        }
        plugins.appendInlineInvokePlugin(replacements);
        if (InlineDuringParsing.getValue(options)) {
            plugins.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }

        if (GeneratePIC.getValue(options)) {
            plugins.setClassInitializationPlugin(new HotSpotAOTClassInitializationPlugin());
            if (TieredAOT.getValue(options)) {
                plugins.setProfilingPlugin(new HotSpotAOTProfilingPlugin());
            }
        } else {
            if (config.instanceKlassInitThreadOffset != -1) {
                plugins.setClassInitializationPlugin(new HotSpotJITClassInitializationPlugin());
            }
        }

        invocationPlugins.defer(new Runnable() {

            @Override
            public void run() {
                registerObjectPlugins(invocationPlugins, options, config, replacements);
                registerClassPlugins(plugins, config, replacements);
                registerSystemPlugins(invocationPlugins);
                registerThreadPlugins(invocationPlugins, metaAccess, wordTypes, config, replacements);
                if (!GeneratePIC.getValue(options)) {
                    registerCallSitePlugins(invocationPlugins);
                }
                registerReflectionPlugins(invocationPlugins, replacements);
                registerAESPlugins(invocationPlugins, config, replacements);
                registerCRC32Plugins(invocationPlugins, config, replacements);
                registerCRC32CPlugins(invocationPlugins, config, replacements);
                registerBigIntegerPlugins(invocationPlugins, config, replacements);
                registerSHAPlugins(invocationPlugins, config, replacements);
                registerGHASHPlugins(invocationPlugins, config, metaAccess);
                registerCounterModePlugins(invocationPlugins, config, replacements);
                registerBase64Plugins(invocationPlugins, config, metaAccess);
                registerUnsafePlugins(invocationPlugins, config, replacements);
                StandardGraphBuilderPlugins.registerInvocationPlugins(metaAccess, snippetReflection, invocationPlugins, replacements, true, false, true);
                registerArrayPlugins(invocationPlugins, replacements);
                registerStringPlugins(invocationPlugins, replacements, wordTypes, foreignCalls, config);
                registerArraysSupportPlugins(invocationPlugins, config, replacements);
            }
        });
        if (!IS_IN_NATIVE_IMAGE) {
            // In libgraal all NodeIntrinsics been converted into special nodes so the plugins
            // aren't needed.
            NodeIntrinsificationProvider nodeIntrinsificationProvider = new NodeIntrinsificationProvider(metaAccess, snippetReflection, foreignCalls, wordTypes, target);
            invocationPlugins.defer(new Runnable() {

                @Override
                public void run() {

                    for (GeneratedPluginFactory factory : GraalServices.load(GeneratedPluginFactory.class)) {
                        factory.registerPlugins(invocationPlugins, nodeIntrinsificationProvider);
                    }

                }
            });
        }
        return plugins;
    }

    private static void registerObjectPlugins(InvocationPlugins plugins, OptionValues options, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, Object.class, replacements);
        if (!GeneratePIC.getValue(options)) {
            // FIXME: clone() requires speculation and requires a fix in here (to check that
            // b.getAssumptions() != null), and in ReplacementImpl.getSubstitution() where there is
            // an instantiation of IntrinsicGraphBuilder using a constructor that sets
            // AllowAssumptions to YES automatically. The former has to inherit the assumptions
            // settings from the root compile instead. So, for now, I'm disabling it for
            // GeneratePIC.
            r.register1("clone", Receiver.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    ValueNode object = receiver.get();
                    b.addPush(JavaKind.Object, new ObjectCloneNode(MacroParams.of(b, targetMethod, object)));
                    return true;
                }

                @Override
                public boolean inlineOnly() {
                    return true;
                }
            });
        }
        r.register1("hashCode", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get();
                b.addPush(JavaKind.Int, new IdentityHashCodeNode(object, b.bci()));
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
        if (config.inlineNotify()) {
            r.register1("notify", Receiver.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    ValueNode object = receiver.get();
                    b.add(new FastNotifyNode(object, false, b.bci()));
                    return true;
                }

                @Override
                public boolean inlineOnly() {
                    return true;
                }
            });
        }
        if (config.inlineNotifyAll()) {
            r.register1("notifyAll", Receiver.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    ValueNode object = receiver.get();
                    b.add(new FastNotifyNode(object, true, b.bci()));
                    return true;
                }

                @Override
                public boolean inlineOnly() {
                    return true;
                }
            });
        }
    }

    private static void registerClassPlugins(Plugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins.getInvocationPlugins(), Class.class, replacements);

        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "getModifiers", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "isInterface", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "isArray", Receiver.class);
        r.register1("isPrimitive", Receiver.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode hub = b.add(new ClassGetHubNode(receiver.get()));
                LogicNode isNull = b.add(IsNullNode.create(hub));
                b.addPush(JavaKind.Boolean, ConditionalNode.create(isNull, b.add(forBoolean(true)), b.add(forBoolean(false)), NodeView.DEFAULT));
                return true;
            }
        });
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "getSuperclass", Receiver.class);

        if (config.jvmAccIsHiddenClass != 0) {
            r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "isHidden", Receiver.class);
        }

        if (config.getFieldOffset("ArrayKlass::_component_mirror", Integer.class, "oop", Integer.MAX_VALUE, JDK <= 8) != Integer.MAX_VALUE) {
            r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "getComponentType", Receiver.class);
        }
    }

    private static void registerCallSitePlugins(InvocationPlugins plugins) {
        InvocationPlugin plugin = new InvocationPlugin() {
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

            @Override
            public boolean inlineOnly() {
                return true;
            }
        };
        plugins.register(plugin, ConstantCallSite.class, "getTarget", Receiver.class);
        plugins.register(plugin, MutableCallSite.class, "getTarget", Receiver.class);
        plugins.register(plugin, VolatileCallSite.class, "getTarget", Receiver.class);
    }

    private static void registerReflectionPlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, reflectionClass, replacements);
        r.register0("getCallerClass", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, new HotSpotReflectionGetCallerClassNode(MacroParams.of(b, targetMethod)));
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
        r.registerMethodSubstitution(ReflectionSubstitutions.class, "getClassAccessFlags", Class.class);
    }

    private static void registerUnsafePlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r;
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            r = new Registration(plugins, Unsafe.class, replacements);
        } else {
            r = new Registration(plugins, "jdk.internal.misc.Unsafe", replacements);
        }
        r.register6(HotSpotBackend.copyMemoryName, Receiver.class, Object.class, long.class, Object.class,
                        long.class, long.class, new InvocationPlugin() {
                            @Override
                            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode srcBase, ValueNode srcOffset, ValueNode destBase,
                                            ValueNode destOffset, ValueNode bytes) {
                                b.add(new UnsafeCopyMemoryNode(config.doingUnsafeAccessOffset == Integer.MAX_VALUE, receiver.get(), srcBase, srcOffset, destBase, destOffset, bytes));
                                return true;
                            }
                        });

        r.register2("allocateInstance", Receiver.class, Class.class, new InvocationPlugin() {
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
                b.addPush(JavaKind.Object, new DynamicNewInstanceNode(b.nullCheckedValue(clazz, DeoptimizationAction.None), true));
                return true;
            }
        });
    }

    private static void registerSystemPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, System.class);
        r.register0("currentTimeMillis", new ForeignCallPlugin(HotSpotHostForeignCallsProvider.JAVA_TIME_MILLIS));
        r.register0("nanoTime", new ForeignCallPlugin(HotSpotHostForeignCallsProvider.JAVA_TIME_NANOS));
        r.register1("identityHashCode", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.addPush(JavaKind.Int, new IdentityHashCodeNode(object, b.bci()));
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
        r.register5("arraycopy", Object.class, int.class, Object.class, int.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src, ValueNode srcPos, ValueNode dst, ValueNode dstPos, ValueNode length) {
                b.add(new ArrayCopyNode(b.bci(), src, srcPos, dst, dstPos, length));
                return true;
            }

            @Override
            public boolean inlineOnly() {
                return true;
            }
        });
    }

    private static void registerArrayPlugins(InvocationPlugins plugins, Replacements replacements) {
        Registration r = new Registration(plugins, Array.class, replacements);
        r.setAllowOverwrite(true);
        r.registerMethodSubstitution(HotSpotArraySubstitutions.class, "newInstance", Class.class, int.class);
    }

    private static void registerStringPlugins(InvocationPlugins plugins, Replacements replacements, WordTypes wordTypes, ArrayCopyForeignCalls foreignCalls, GraalHotSpotVMConfig vmConfig) {
        if (JavaVersionUtil.JAVA_SPEC > 8) {
            final Registration utf16r = new Registration(plugins, "java.lang.StringUTF16", replacements);
            utf16r.register3("toBytes", char[].class, int.class, int.class, new InvocationPlugin() {
                private static final int MAX_LENGTH = Integer.MAX_VALUE >> 1;

                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value, ValueNode srcBegin, ValueNode length) {
                    PluginHelper helper = new PluginHelper(b, wordTypes);
                    helper.guard(srcBegin, Condition.LT, ConstantNode.forInt(0), DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
                    helper.guard(length, Condition.LT, ConstantNode.forInt(0), DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
                    helper.guard(length, Condition.GT, ConstantNode.forInt(MAX_LENGTH), DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
                    ValueNode valueLength = b.add(new ArrayLengthNode(value));
                    ValueNode limit = b.add(new SubNode(valueLength, length));
                    helper.guard(srcBegin, Condition.GT, limit, DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
                    ValueNode newArray = b.add(new NewArrayNode(b.getMetaAccess().lookupJavaType(Byte.TYPE), b.add(new LeftShiftNode(length, ConstantNode.forInt(1))), false));
                    b.addPush(JavaKind.Object, newArray);
                    // The stateAfter should include the value pushed, so push it first and then
                    // perform the call that fills in the array.
                    b.add(new ArrayCopyCallNode(foreignCalls, wordTypes, value, srcBegin, newArray, ConstantNode.forInt(0), length, JavaKind.Char, LocationIdentity.init(), false, true, true,
                                    vmConfig.heapWordSize));
                    return true;
                }
            });
            utf16r.register5("getChars", byte[].class, int.class, int.class, char[].class, int.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value, ValueNode srcBegin, ValueNode srcEnd, ValueNode dst,
                                ValueNode dstBegin) {
                    PluginHelper helper = new PluginHelper(b, wordTypes);
                    ValueNode length = helper.sub(srcEnd, srcBegin);
                    helper.guard(srcBegin, Condition.LT, ConstantNode.forInt(0), DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
                    helper.guard(length, Condition.LT, ConstantNode.forInt(0), DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
                    ValueNode srcLimit = helper.sub(helper.rightShift(helper.length(value), 1), length);
                    helper.guard(srcBegin, Condition.GT, srcLimit, DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
                    ValueNode limit = helper.sub(helper.length(dst), length);
                    helper.guard(dstBegin, Condition.GT, limit, DeoptimizationAction.None, DeoptimizationReason.BoundsCheckException);
                    b.add(new ArrayCopyCallNode(foreignCalls, wordTypes, value, srcBegin, dst, dstBegin, length, JavaKind.Char, JavaKind.Byte, JavaKind.Char, false, true, true,
                                    vmConfig.heapWordSize));
                    return true;
                }
            });
        }
    }

    private static void registerThreadPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, WordTypes wordTypes, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, Thread.class, replacements);
        r.register0("currentThread", new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                CurrentJavaThreadNode thread = b.add(new CurrentJavaThreadNode(wordTypes.getWordKind()));
                ValueNode offset = b.add(ConstantNode.forLong(config.threadObjectOffset));
                AddressNode address = b.add(new OffsetAddressNode(thread, offset));
                // JavaThread::_threadObj is never compressed
                ObjectStamp stamp = StampFactory.objectNonNull(TypeReference.create(b.getAssumptions(), metaAccess.lookupJavaType(Thread.class)));
                b.addPush(JavaKind.Object, new ReadNode(address, JAVA_THREAD_THREAD_OBJECT_LOCATION, stamp, BarrierType.NONE));
                return true;
            }
        });

        if (config.osThreadInterruptedOffset != Integer.MAX_VALUE) {
            r.registerMethodSubstitution(ThreadSubstitutions.class, "isInterrupted", Receiver.class, boolean.class);
        }

    }

    public static final String reflectionClass;

    static {
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            reflectionClass = "sun.reflect.Reflection";
        } else {
            reflectionClass = "jdk.internal.reflect.Reflection";
        }
    }

    public static String lookupIntrinsicName(GraalHotSpotVMConfig config, String className, String name1, String name2) {
        return selectIntrinsicName(config, className, name1, name2).getLeft();
    }

    /**
     * Returns a pair of Strings where the left one represents the matched intrinsic name and the
     * right one represents the mismatched intrinsic name.
     */
    public static Pair<String, String> selectIntrinsicName(GraalHotSpotVMConfig config, String className, String name1, String name2) {
        boolean foundName1 = false;
        boolean foundName2 = false;
        for (VMIntrinsicMethod intrinsic : config.getStore().getIntrinsics()) {
            if (className.equals(intrinsic.declaringClass)) {
                if (name1.equals(intrinsic.name)) {
                    foundName1 = true;
                } else if (name2.equals(intrinsic.name)) {
                    foundName2 = true;
                }
            }
        }
        if (foundName1 && !foundName2) {
            return Pair.create(name1, name2);
        } else if (foundName2 && !foundName1) {
            return Pair.create(name2, name1);
        }
        throw GraalError.shouldNotReachHere();
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

    private static void registerAESPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        if (config.useAESIntrinsics) {
            assert config.aescryptEncryptBlockStub != 0L;
            assert config.aescryptDecryptBlockStub != 0L;
            assert config.cipherBlockChainingEncryptAESCryptStub != 0L;
            assert config.cipherBlockChainingDecryptAESCryptStub != 0L;
            String arch = config.osArch;
            String decryptSuffix = arch.equals("sparc") ? "WithOriginalKey" : "";

            Registration r = new Registration(plugins, "com.sun.crypto.provider.CipherBlockChaining", replacements);

            Pair<String, String> cbcEncryptName = selectIntrinsicName(config, "com/sun/crypto/provider/CipherBlockChaining", "implEncrypt", "encrypt");
            registerAndCheckMismatch(r, CipherBlockChainingSubstitutions.class, cbcEncryptName, Receiver.class, byte[].class, int.class, int.class,
                            byte[].class, int.class);

            Pair<String, String> cbcDecryptName = selectIntrinsicName(config, "com/sun/crypto/provider/CipherBlockChaining", "implDecrypt", "decrypt");
            registerAndCheckMismatch(r, CipherBlockChainingSubstitutions.class, cbcDecryptName, cbcDecryptName.getLeft() + decryptSuffix, Receiver.class, byte[].class, int.class, int.class,
                            byte[].class, int.class);

            r = new Registration(plugins, "com.sun.crypto.provider.AESCrypt", replacements);

            Pair<String, String> aesEncryptName = selectIntrinsicName(config, "com/sun/crypto/provider/AESCrypt", "implEncryptBlock", "encryptBlock");
            registerAndCheckMismatch(r, AESCryptSubstitutions.class, aesEncryptName, Receiver.class, byte[].class, int.class, byte[].class, int.class);

            Pair<String, String> aesDecryptName = selectIntrinsicName(config, "com/sun/crypto/provider/AESCrypt", "implDecryptBlock", "decryptBlock");
            registerAndCheckMismatch(r, AESCryptSubstitutions.class, aesDecryptName, aesDecryptName.getLeft() + decryptSuffix, Receiver.class, byte[].class, int.class, byte[].class, int.class);
        }
    }

    private static void registerAndCheckMismatch(Registration r, Class<?> substitutionClass, Pair<String, String> intrinsicNames, Type... argumentTypes) {
        try {
            r.registerMethodSubstitution(substitutionClass, intrinsicNames.getLeft(), argumentTypes);
        } catch (NoSuchMethodError e) {
            throw new GraalError(e, "Found method named '%s' instead of '%s' in class '%s'. This is most likely because the JVMCI JDK in %s was built on an incompatible base JDK.",
                            intrinsicNames.getRight(), intrinsicNames.getLeft(), r.getDeclaringType().getTypeName(), Services.getSavedProperties().get("java.home"));
        }
    }

    private static void registerAndCheckMismatch(Registration r, Class<?> substitutionClass, Pair<String, String> intrinsicNames, String substituteName, Type... argumentTypes) {
        try {
            r.registerMethodSubstitution(substitutionClass, intrinsicNames.getLeft(), substituteName, argumentTypes);
        } catch (NoSuchMethodError e) {
            throw new GraalError(e, "Found method named '%s' instead of '%s' in class '%s'. This is most likely because the JVMCI JDK in %s was built on an incompatible base JDK.",
                            intrinsicNames.getRight(), intrinsicNames.getLeft(), r.getDeclaringType().getTypeName(), Services.getSavedProperties().get("java.home"));
        }
    }

    private static void registerAndCheckMismatch(Registration r, Pair<String, String> intrinsicNames, Type arg1, Type arg2, Type arg3, InvocationPlugin plugin) {
        try {
            r.register3(intrinsicNames.getLeft(), arg1, arg2, arg3, plugin);
        } catch (NoSuchMethodError e) {
            throw new GraalError(e, "Found method named '%s' instead of '%s' in class '%s'. This is most likely because the JVMCI JDK in %s was built on an incompatible base JDK.",
                            intrinsicNames.getRight(), intrinsicNames.getLeft(), r.getDeclaringType().getTypeName(), Services.getSavedProperties().get("java.home"));
        }
    }

    private static ValueNode arrayStart(GraphBuilderContext b, ValueNode array, JavaKind kind) {
        int byteArrayBaseOffset = b.getMetaAccess().getArrayBaseOffset(kind);
        return b.add(new ComputeObjectAddressNode(array, ConstantNode.forInt(byteArrayBaseOffset)));
    }

    private static ValueNode byteArrayStart(GraphBuilderContext b, ValueNode array) {
        JavaKind kind = JavaKind.Byte;
        return arrayStart(b, array, kind);
    }

    static class PluginHelper {
        protected final GraphBuilderContext context;
        protected final WordTypes wordTypes;

        PluginHelper(GraphBuilderContext context, WordTypes wordTypes) {
            this.context = context;
            this.wordTypes = wordTypes;
        }

        ValueNode xor(ValueNode x, ValueNode y) {
            return context.add(new XorNode(x, y));
        }

        ValueNode add(ValueNode x, ValueNode y) {
            return context.add(new AddNode(x, y));
        }

        ValueNode sub(ValueNode x, ValueNode y) {
            return context.add(new SubNode(x, y));
        }

        ValueNode length(ValueNode x) {
            return context.add(new ArrayLengthNode(x));
        }

        ValueNode byteArrayStart(ValueNode array) {
            return arrayIndex(array, JavaKind.Byte, null);
        }

        ValueNode arrayIndex(ValueNode array, JavaKind kind, ValueNode index) {
            int arrayBaseOffset = context.getMetaAccess().getArrayBaseOffset(kind);
            ValueNode offset = ConstantNode.forInt(arrayBaseOffset);
            if (index != null) {
                offset = add(offset, scale(index, kind));
            }

            return context.add(new ComputeObjectAddressNode(array, offset));
        }

        private ValueNode scale(ValueNode index, JavaKind kind) {
            int arrayIndexScale = context.getMetaAccess().getArrayIndexScale(kind);
            return shl(asWord(index), arrayIndexScale);
        }

        private ValueNode shl(ValueNode node, int arrayIndexScale) {
            if (arrayIndexScale == 1) {
                return node;
            }
            return context.add(new LeftShiftNode(node, ConstantNode.forInt(arrayIndexScale)));
        }

        private ValueNode asWord(ValueNode index) {
            assert index.getStackKind().isPrimitive();
            if (index.getStackKind() != wordTypes.getWordKind()) {
                return SignExtendNode.create(index, wordTypes.getWordKind().getBitCount(), NodeView.DEFAULT);
            }
            return index;
        }

        private LogicNode createCompare(CanonicalCondition cond, ValueNode a, ValueNode b) {
            assert !a.getStackKind().isNumericFloat();
            switch (cond) {
                case EQ:
                    if (a.getStackKind() == JavaKind.Object) {
                        return ObjectEqualsNode.create(getConstantReflection(), getMetaAccess(), b.getOptions(), a, b, NodeView.DEFAULT);
                    } else {
                        return IntegerEqualsNode.create(getConstantReflection(), getMetaAccess(), b.getOptions(), null, a, b, NodeView.DEFAULT);
                    }
                case LT:
                    assert a.getStackKind() != JavaKind.Object;
                    return IntegerLessThanNode.create(getConstantReflection(), getMetaAccess(), b.getOptions(), null, a, b, NodeView.DEFAULT);
                default:
                    throw GraalError.shouldNotReachHere("Unexpected condition: " + cond);
            }
        }

        private ConstantReflectionProvider getConstantReflection() {
            return context.getConstantReflection();
        }

        private MetaAccessProvider getMetaAccess() {
            return context.getMetaAccess();
        }

        void guard(ValueNode x, Condition condition, ValueNode y, DeoptimizationAction action, DeoptimizationReason deoptReason) {
            Condition.CanonicalizedCondition canonicalizedCondition = condition.canonicalize();

            // Check whether the condition needs to mirror the operands.
            ValueNode a = x;
            ValueNode b = y;
            if (canonicalizedCondition.mustMirror()) {
                a = y;
                b = x;
            }
            LogicNode compare = createCompare(canonicalizedCondition.getCanonicalCondition(), a, b);
            context.add(new FixedGuardNode(compare, deoptReason, action, !canonicalizedCondition.mustNegate()));
        }

        public ValueNode leftShift(ValueNode node, int i) {
            return context.add(new LeftShiftNode(node, ConstantNode.forInt(i)));
        }

        public ValueNode rightShift(ValueNode node, int i) {
            return context.add(new RightShiftNode(node, ConstantNode.forInt(i)));
        }

    }

    private static void registerBigIntegerPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        Registration r = new Registration(plugins, BigInteger.class, replacements);
        assert !config.useMultiplyToLenIntrinsic() || config.multiplyToLen != 0L;
        if (JavaVersionUtil.JAVA_SPEC <= 8) {
            r.registerConditionalMethodSubstitution(config.useMultiplyToLenIntrinsic(), BigIntegerSubstitutions.class, "multiplyToLen", "multiplyToLenStatic", int[].class, int.class, int[].class,
                            int.class, int[].class);
        } else {
            r.registerConditionalMethodSubstitution(config.useMultiplyToLenIntrinsic(), BigIntegerSubstitutions.class, "implMultiplyToLen", "multiplyToLenStatic", int[].class, int.class, int[].class,
                            int.class, int[].class);
        }
        if (config.useMulAddIntrinsic()) {
            r.register5("implMulAdd", int[].class, int[].class, int.class, int.class, int.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode out, ValueNode in, ValueNode offset, ValueNode len, ValueNode k) {
                    ValueNode outNonNull = b.nullCheckedValue(out);
                    ValueNode outNonNullLength = b.add(new ArrayLengthNode(outNonNull));
                    ValueNode newOffset = new SubNode(outNonNullLength, offset);
                    ForeignCallNode call = new ForeignCallNode(HotSpotBackend.MUL_ADD, byteArrayStart(b, outNonNull), byteArrayStart(b, in), newOffset, len, k);
                    b.addPush(JavaKind.Int, call);
                    b.setStateAfter(call);
                    return true;
                }
            });
        }
        if (config.useMontgomeryMultiplyIntrinsic()) {
            /*
             * static int[] implMontgomeryMultiply(int[] a, int[] b, int[] n, int len, long inv,
             * int[] product)
             */
            r.register6("implMontgomeryMultiply", int[].class, int[].class, int[].class, int.class, long.class, int[].class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a, ValueNode bObject, ValueNode n, ValueNode len, ValueNode inv,
                                ValueNode product) {
                    ForeignCallNode call = new ForeignCallNode(HotSpotBackend.MONTGOMERY_MULTIPLY, byteArrayStart(b, a), byteArrayStart(b, bObject), byteArrayStart(b, n), len, inv,
                                    byteArrayStart(b, product));
                    b.add(call);
                    b.addPush(JavaKind.Object, product);
                    b.setStateAfter(call);
                    return true;
                }
            });
        }
        if (config.useMontgomerySquareIntrinsic()) {
            /*
             * static int[] implMontgomerySquare(int[] a, int[] n, int len, long inv, int[] product)
             */
            r.register5("implMontgomerySquare", int[].class, int[].class, int.class, long.class, int[].class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a, ValueNode n, ValueNode len, ValueNode inv, ValueNode product) {
                    ForeignCallNode call = new ForeignCallNode(HotSpotBackend.MONTGOMERY_SQUARE, byteArrayStart(b, a), byteArrayStart(b, n), len, inv, byteArrayStart(b, product));
                    b.add(call);
                    b.addPush(JavaKind.Object, product);
                    b.setStateAfter(call);
                    return true;
                }
            });
        }
        if (config.useSquareToLenIntrinsic()) {
            /*
             * static int[] implSquareToLen(int[] x, int len, int[] z, int zLen)
             */
            r.register4("implSquareToLen", int[].class, int.class, int[].class, int.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode len, ValueNode z, ValueNode zlen) {
                    ForeignCallNode call = new ForeignCallNode(HotSpotBackend.SQUARE_TO_LEN, byteArrayStart(b, x), len, byteArrayStart(b, z), zlen);
                    b.add(call);
                    b.addPush(JavaKind.Object, z);
                    b.setStateAfter(call);
                    return true;
                }
            });
        }
    }

    static class SHAInvocationPlugin implements InvocationPlugin {
        private final ForeignCallDescriptor descriptor;

        SHAInvocationPlugin(ForeignCallDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode buf, ValueNode ofs) {
            ValueNode realReceiver = b.add(new PiNode(receiver.get(), targetMethod.getDeclaringClass(), false, true));
            JavaKind wordKind = JavaKind.Long;
            int stateOffset = HotSpotReplacementsUtil.getFieldOffset(targetMethod.getDeclaringClass(), "state");
            ValueNode state = b.add(new RawLoadNode(realReceiver, b.add(ConstantNode.forIntegerKind(wordKind, stateOffset)), JavaKind.Object, LocationIdentity.any()));
            int intArrayBaseOffset = b.getMetaAccess().getArrayBaseOffset(JavaKind.Int);
            int byteArrayBaseOffset = b.getMetaAccess().getArrayBaseOffset(JavaKind.Byte);
            ValueNode bufAddr = b.add(new ComputeObjectAddressNode(buf, new AddNode(ConstantNode.forInt(byteArrayBaseOffset), ofs)));
            ValueNode stateAddr = b.add(new ComputeObjectAddressNode(state, ConstantNode.forInt(intArrayBaseOffset)));
            b.add(new ForeignCallNode(descriptor, bufAddr, stateAddr));
            return true;
        }
    }

    private static void registerSHAPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        boolean useSha1 = config.useSHA1Intrinsics();
        boolean useSha256 = config.useSHA256Intrinsics();
        boolean useSha512 = config.useSHA512Intrinsics();

        if (isIntrinsicName(config, "sun/security/provider/DigestBase", "implCompressMultiBlock0") && (useSha1 || useSha256 || useSha512)) {
            Registration r = new Registration(plugins, "sun.security.provider.DigestBase", replacements);
            r.registerMethodSubstitution(DigestBaseSubstitutions.class, "implCompressMultiBlock0", Receiver.class, byte[].class, int.class, int.class);
        }

        Pair<String, String> implCompressName = selectIntrinsicName(config, "sun/security/provider/SHA", "implCompress", "implCompress0");
        if (useSha1) {
            assert config.sha1ImplCompress != 0L;
            Registration r = new Registration(plugins, "sun.security.provider.SHA", replacements);
            InvocationPlugin plugin = new SHAInvocationPlugin(HotSpotBackend.SHA_IMPL_COMPRESS);
            registerAndCheckMismatch(r, implCompressName, Receiver.class, byte[].class, int.class, plugin);
        }
        if (useSha256) {
            assert config.sha256ImplCompress != 0L;
            Registration r = new Registration(plugins, "sun.security.provider.SHA2", replacements);
            InvocationPlugin plugin = new SHAInvocationPlugin(HotSpotBackend.SHA2_IMPL_COMPRESS);
            registerAndCheckMismatch(r, implCompressName, Receiver.class, byte[].class, int.class, plugin);
        }
        if (useSha512) {
            assert config.sha512ImplCompress != 0L;
            Registration r = new Registration(plugins, "sun.security.provider.SHA5", replacements);
            InvocationPlugin plugin = new SHAInvocationPlugin(HotSpotBackend.SHA5_IMPL_COMPRESS);
            registerAndCheckMismatch(r, implCompressName, Receiver.class, byte[].class, int.class, plugin);
        }
    }

    private static void registerGHASHPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, MetaAccessProvider metaAccess) {
        if (config.useGHASHIntrinsics()) {
            assert config.ghashProcessBlocks != 0L;
            Registration r = new Registration(plugins, "com.sun.crypto.provider.GHASH");
            r.register5("processBlocks",
                            byte[].class,
                            int.class,
                            int.class,
                            long[].class,
                            long[].class,
                            new InvocationPlugin() {
                                @Override
                                public boolean apply(GraphBuilderContext b,
                                                ResolvedJavaMethod targetMethod,
                                                Receiver receiver,
                                                ValueNode data,
                                                ValueNode inOffset,
                                                ValueNode blocks,
                                                ValueNode state,
                                                ValueNode hashSubkey) {
                                    int longArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Long);
                                    int byteArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Byte);
                                    ValueNode dataOffset = AddNode.create(ConstantNode.forInt(byteArrayBaseOffset), inOffset, NodeView.DEFAULT);
                                    ComputeObjectAddressNode dataAddress = b.add(new ComputeObjectAddressNode(data, dataOffset));
                                    ComputeObjectAddressNode stateAddress = b.add(new ComputeObjectAddressNode(state, ConstantNode.forInt(longArrayBaseOffset)));
                                    ComputeObjectAddressNode hashSubkeyAddress = b.add(new ComputeObjectAddressNode(hashSubkey, ConstantNode.forInt(longArrayBaseOffset)));
                                    b.add(new ForeignCallNode(GHASH_PROCESS_BLOCKS, stateAddress, hashSubkeyAddress, dataAddress, blocks));
                                    return true;
                                }
                            });
        }
    }

    private static void registerCounterModePlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        if (isIntrinsicName(config, "com/sun/crypto/provider/CounterMode", "implCrypt")) {
            assert !config.useAESCTRIntrinsics || config.counterModeAESCrypt != 0L;
            Registration r = new Registration(plugins, "com.sun.crypto.provider.CounterMode", replacements);
            r.registerConditionalMethodSubstitution(config.useAESCTRIntrinsics, CounterModeSubstitutions.class, "implCrypt", Receiver.class, byte[].class, int.class, int.class, byte[].class,
                            int.class);
        }
    }

    private static void registerBase64Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, MetaAccessProvider metaAccess) {
        if (config.useBase64Intrinsics()) {
            Registration r = new Registration(plugins, "java.util.Base64$Encoder");
            r.register7("encodeBlock",
                            Receiver.class,
                            byte[].class,
                            int.class,
                            int.class,
                            byte[].class,
                            int.class,
                            boolean.class,
                            new InvocationPlugin() {
                                @Override
                                public boolean apply(GraphBuilderContext b,
                                                ResolvedJavaMethod targetMethod,
                                                Receiver receiver,
                                                ValueNode src,
                                                ValueNode sp,
                                                ValueNode sl,
                                                ValueNode dst,
                                                ValueNode dp,
                                                ValueNode isURL) {
                                    int byteArrayBaseOffset = metaAccess.getArrayBaseOffset(JavaKind.Byte);
                                    ComputeObjectAddressNode srcAddress = b.add(new ComputeObjectAddressNode(src, ConstantNode.forInt(byteArrayBaseOffset)));
                                    ComputeObjectAddressNode dstAddress = b.add(new ComputeObjectAddressNode(dst, ConstantNode.forInt(byteArrayBaseOffset)));
                                    b.add(new ForeignCallNode(BASE64_ENCODE_BLOCK, srcAddress, sp, sl, dstAddress, dp, isURL));
                                    return true;
                                }
                            });
        }
    }

    private static void registerCRC32Plugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        if (config.useCRC32Intrinsics) {
            Registration r = new Registration(plugins, CRC32.class, replacements);
            r.register2("update", int.class, int.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode crc, ValueNode arg) {
                    final ValueNode crcTableRawAddress = b.add(new GraalHotSpotVMConfigNode(config, HotSpotMarkId.CRC_TABLE_ADDRESS, JavaKind.Long));
                    ValueNode c = new XorNode(crc, ConstantNode.forInt(-1));
                    ValueNode index = new AndNode(new XorNode(arg, c), ConstantNode.forInt(0xff));
                    ValueNode offset = new LeftShiftNode(index, ConstantNode.forInt(2));
                    AddressNode address = new OffsetAddressNode(crcTableRawAddress, new SignExtendNode(offset, 32, 64));
                    ValueNode result = b.add(new JavaReadNode(JavaKind.Int, address, CRC_TABLE_LOCATION, BarrierType.NONE, false));
                    result = new XorNode(result, new UnsignedRightShiftNode(c, ConstantNode.forInt(8)));
                    b.addPush(JavaKind.Int, new XorNode(result, ConstantNode.forInt(-1)));
                    return true;
                }
            });
            String updateBytesName = JavaVersionUtil.JAVA_SPEC <= 8 ? "updateBytes" : "updateBytes0";
            r.register4(updateBytesName, int.class, byte[].class, int.class, int.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode crc, ValueNode buf, ValueNode off, ValueNode len) {
                    int byteArrayBaseOffset = b.getMetaAccess().getArrayBaseOffset(JavaKind.Byte);
                    ValueNode bufAddr = b.add(new ComputeObjectAddressNode(buf, new AddNode(ConstantNode.forInt(byteArrayBaseOffset), off)));
                    b.addPush(JavaKind.Int, new ForeignCallNode(UPDATE_BYTES_CRC32, crc, bufAddr, len));
                    return true;
                }
            });
            String updateByteBufferName = JavaVersionUtil.JAVA_SPEC <= 8 ? "updateByteBuffer" : "updateByteBuffer0";
            r.register4(updateByteBufferName, int.class, long.class, int.class, int.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode crc, ValueNode addr, ValueNode off, ValueNode len) {
                    ValueNode bufAddr = b.add(new AddNode(addr, new SignExtendNode(off, 32, 64)));
                    b.addPush(JavaKind.Int, new ForeignCallNode(UPDATE_BYTES_CRC32, crc, bufAddr, len));
                    return true;
                }
            });
        }
    }

    private static void registerCRC32CPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        if (JavaVersionUtil.JAVA_SPEC > 8 && config.useCRC32CIntrinsics) {
            Registration r = new Registration(plugins, "java.util.zip.CRC32C", replacements);
            r.register4("updateBytes", int.class, byte[].class, int.class, int.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode crc, ValueNode buf, ValueNode off, ValueNode end) {
                    int byteArrayBaseOffset = b.getMetaAccess().getArrayBaseOffset(JavaKind.Byte);
                    ValueNode bufAddr = b.add(new ComputeObjectAddressNode(buf, new AddNode(ConstantNode.forInt(byteArrayBaseOffset), off)));
                    b.addPush(JavaKind.Int, new ForeignCallNode(UPDATE_BYTES_CRC32C, crc, bufAddr, new SubNode(end, off)));
                    return true;
                }
            });
            r.register4("updateDirectByteBuffer", int.class, long.class, int.class, int.class, new InvocationPlugin() {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode crc, ValueNode addr, ValueNode off, ValueNode end) {
                    ValueNode bufAddr = b.add(new AddNode(addr, new SignExtendNode(off, 32, 64)));
                    b.addPush(JavaKind.Int, new ForeignCallNode(UPDATE_BYTES_CRC32C, crc, bufAddr, new SubNode(end, off)));
                    return true;
                }
            });
        }
    }

    private static void registerArraysSupportPlugins(InvocationPlugins plugins, GraalHotSpotVMConfig config, Replacements replacements) {
        if (JavaVersionUtil.JAVA_SPEC > 8 && config.useVectorizedMismatchIntrinsic) {
            Registration r = new Registration(plugins, "jdk.internal.util.ArraysSupport", replacements);
            r.register6("vectorizedMismatch", Object.class, long.class, Object.class, long.class,
                            int.class, int.class, new InvocationPlugin() {
                                @Override
                                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode a, ValueNode aOffset, ValueNode bObject, ValueNode bOffset,
                                                ValueNode length, ValueNode log2ArrayIndexScale) {
                                    ValueNode aAddr = b.add(new ComputeObjectAddressNode(a, aOffset));
                                    ValueNode bAddr = b.add(new ComputeObjectAddressNode(bObject, bOffset));
                                    b.addPush(JavaKind.Int, new ForeignCallNode(HotSpotBackend.VECTORIZED_MISMATCH, aAddr, bAddr, length, log2ArrayIndexScale));
                                    return true;
                                }
                            });

        }
    }
}
