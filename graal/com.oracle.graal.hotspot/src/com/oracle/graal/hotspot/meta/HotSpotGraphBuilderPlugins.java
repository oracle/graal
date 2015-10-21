/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.JAVA_THREAD_THREAD_OBJECT_LOCATION;
import static com.oracle.graal.hotspot.replacements.SystemSubstitutions.JAVA_TIME_MILLIS;
import static com.oracle.graal.hotspot.replacements.SystemSubstitutions.JAVA_TIME_NANOS;
import static com.oracle.graal.java.BytecodeParserOptions.InlineDuringParsing;

import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.VolatileCallSite;
import java.util.zip.CRC32;

import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.options.StableOptionValue;
import sun.reflect.Reflection;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.graphbuilderconf.ForeignCallPlugin;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.graphbuilderconf.InvocationPlugin.Receiver;
import com.oracle.graal.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.graphbuilderconf.MethodSubstitutionPlugin;
import com.oracle.graal.hotspot.nodes.ClassCastNode;
import com.oracle.graal.hotspot.nodes.CurrentJavaThreadNode;
import com.oracle.graal.hotspot.replacements.AESCryptSubstitutions;
import com.oracle.graal.hotspot.replacements.CRC32Substitutions;
import com.oracle.graal.hotspot.replacements.CallSiteTargetNode;
import com.oracle.graal.hotspot.replacements.CipherBlockChainingSubstitutions;
import com.oracle.graal.hotspot.replacements.HotSpotClassSubstitutions;
import com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil;
import com.oracle.graal.hotspot.replacements.IdentityHashCodeNode;
import com.oracle.graal.hotspot.replacements.ObjectCloneNode;
import com.oracle.graal.hotspot.replacements.ObjectSubstitutions;
import com.oracle.graal.hotspot.replacements.ReflectionGetCallerClassNode;
import com.oracle.graal.hotspot.replacements.arraycopy.ArrayCopyNode;
import com.oracle.graal.hotspot.word.HotSpotWordTypes;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.memory.HeapAccess.BarrierType;
import com.oracle.graal.nodes.memory.address.AddressNode;
import com.oracle.graal.nodes.memory.address.OffsetAddressNode;
import com.oracle.graal.nodes.spi.StampProvider;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.replacements.InlineDuringParsingPlugin;
import com.oracle.graal.replacements.MethodHandlePlugin;
import com.oracle.graal.replacements.NodeIntrinsificationPhase;
import com.oracle.graal.replacements.NodeIntrinsificationPlugin;
import com.oracle.graal.replacements.ReplacementsImpl;
import com.oracle.graal.replacements.StandardGraphBuilderPlugins;
import com.oracle.graal.replacements.WordOperationPlugin;
import com.oracle.graal.word.WordTypes;

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
     * @param stampProvider
     */
    public static Plugins create(HotSpotVMConfig config, HotSpotWordTypes wordTypes, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection,
                    SnippetReflectionProvider snippetReflection, ForeignCallsProvider foreignCalls, StampProvider stampProvider, ReplacementsImpl replacements) {
        InvocationPlugins invocationPlugins = new HotSpotInvocationPlugins(config, metaAccess);

        Plugins plugins = new Plugins(invocationPlugins);
        NodeIntrinsificationPhase nodeIntrinsificationPhase = new NodeIntrinsificationPhase(metaAccess, constantReflection, snippetReflection, foreignCalls, stampProvider);
        NodeIntrinsificationPlugin nodeIntrinsificationPlugin = new NodeIntrinsificationPlugin(metaAccess, nodeIntrinsificationPhase, wordTypes, false);
        HotSpotWordOperationPlugin wordOperationPlugin = new HotSpotWordOperationPlugin(snippetReflection, wordTypes);
        HotSpotNodePlugin nodePlugin = new HotSpotNodePlugin(wordOperationPlugin, nodeIntrinsificationPlugin);

        plugins.appendParameterPlugin(nodePlugin);
        plugins.appendNodePlugin(nodePlugin);
        plugins.appendNodePlugin(new MethodHandlePlugin(constantReflection.getMethodHandleAccess(), true));

        plugins.appendInlineInvokePlugin(replacements);
        if (InlineDuringParsing.getValue()) {
            plugins.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }

        registerObjectPlugins(invocationPlugins);
        registerClassPlugins(plugins);
        registerSystemPlugins(invocationPlugins, foreignCalls);
        registerThreadPlugins(invocationPlugins, metaAccess, wordTypes, config);
        registerCallSitePlugins(invocationPlugins);
        registerReflectionPlugins(invocationPlugins);
        registerStableOptionPlugins(invocationPlugins, snippetReflection);
        registerAESPlugins(invocationPlugins, config);
        registerCRC32Plugins(invocationPlugins, config);
        StandardGraphBuilderPlugins.registerInvocationPlugins(metaAccess, invocationPlugins, true);

        return plugins;
    }

    private static void registerObjectPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Object.class);
        r.register1("clone", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get();
                b.addPush(JavaKind.Object, new ObjectCloneNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnType(), object));
                return true;
            }

            public boolean inlineOnly() {
                return true;
            }
        });
        r.registerMethodSubstitution(ObjectSubstitutions.class, "hashCode", Receiver.class);
    }

    private static void registerClassPlugins(Plugins plugins) {
        Registration r = new Registration(plugins.getInvocationPlugins(), Class.class);

        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "getModifiers", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "isInterface", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "isArray", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "isPrimitive", Receiver.class);
        r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "getSuperclass", Receiver.class);

        if (HotSpotReplacementsUtil.arrayKlassComponentMirrorOffsetExists()) {
            r.registerMethodSubstitution(HotSpotClassSubstitutions.class, "getComponentType", Receiver.class);
        }

        r.register2("cast", Receiver.class, Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                ValueNode javaClass = receiver.get();
                ValueNode folded = ClassCastNode.tryFold(GraphUtil.originalValue(javaClass), object, b.getConstantReflection(), b.getAssumptions());
                if (folded != null) {
                    b.addPush(JavaKind.Object, folded);
                } else {
                    b.addPush(JavaKind.Object, new ClassCastNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnType(), javaClass, object));
                }
                return true;
            }

            public boolean inlineOnly() {
                return true;
            }
        });
    }

    private static void registerCallSitePlugins(InvocationPlugins plugins) {
        InvocationPlugin plugin = new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode callSite = receiver.get();
                ValueNode folded = CallSiteTargetNode.tryFold(GraphUtil.originalValue(callSite), b.getMetaAccess(), b.getAssumptions());
                if (folded != null) {
                    b.addPush(JavaKind.Object, folded);
                } else {
                    b.addPush(JavaKind.Object, new CallSiteTargetNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnType(), callSite));
                }
                return true;
            }

            public boolean inlineOnly() {
                return true;
            }
        };
        plugins.register(plugin, ConstantCallSite.class, "getTarget", Receiver.class);
        plugins.register(plugin, MutableCallSite.class, "getTarget", Receiver.class);
        plugins.register(plugin, VolatileCallSite.class, "getTarget", Receiver.class);
    }

    private static void registerReflectionPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Reflection.class);
        r.register0("getCallerClass", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, new ReflectionGetCallerClassNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnType()));
                return true;
            }

            public boolean inlineOnly() {
                return true;
            }
        });
    }

    private static void registerSystemPlugins(InvocationPlugins plugins, ForeignCallsProvider foreignCalls) {
        Registration r = new Registration(plugins, System.class);
        r.register0("currentTimeMillis", new ForeignCallPlugin(foreignCalls, JAVA_TIME_MILLIS));
        r.register0("nanoTime", new ForeignCallPlugin(foreignCalls, JAVA_TIME_NANOS));
        r.register1("identityHashCode", Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                b.addPush(JavaKind.Int, new IdentityHashCodeNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnType(), object));
                return true;
            }

            public boolean inlineOnly() {
                return true;
            }
        });
        r.register5("arraycopy", Object.class, int.class, Object.class, int.class, int.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src, ValueNode srcPos, ValueNode dst, ValueNode dstPos, ValueNode length) {
                b.add(new ArrayCopyNode(b.bci(), src, srcPos, dst, dstPos, length));
                return true;
            }

            public boolean inlineOnly() {
                return true;
            }
        });
    }

    private static void registerThreadPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, WordTypes wordTypes, HotSpotVMConfig config) {
        Registration r = new Registration(plugins, Thread.class);
        r.register0("currentThread", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                CurrentJavaThreadNode thread = b.add(new CurrentJavaThreadNode(wordTypes.getWordKind()));
                boolean compressible = false;
                ValueNode offset = b.add(ConstantNode.forLong(config.threadObjectOffset));
                AddressNode address = b.add(new OffsetAddressNode(thread, offset));
                ValueNode javaThread = WordOperationPlugin.readOp(b, JavaKind.Object, address, JAVA_THREAD_THREAD_OBJECT_LOCATION, BarrierType.NONE, compressible);
                boolean exactType = compressible;
                boolean nonNull = true;
                b.addPush(JavaKind.Object, new PiNode(javaThread, metaAccess.lookupJavaType(Thread.class), exactType, nonNull));
                return true;
            }
        });
    }

    private static void registerStableOptionPlugins(InvocationPlugins plugins, SnippetReflectionProvider snippetReflection) {
        Registration r = new Registration(plugins, StableOptionValue.class);
        r.register1("getValue", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (receiver.isConstant()) {
                    StableOptionValue<?> option = snippetReflection.asObject(StableOptionValue.class, (JavaConstant) receiver.get().asConstant());
                    b.addPush(JavaKind.Object, ConstantNode.forConstant(snippetReflection.forObject(option.getValue()), b.getMetaAccess()));
                    return true;
                }
                return false;
            }
        });
    }

    public static final String cbcEncryptName;
    public static final String cbcDecryptName;
    public static final String aesEncryptName;
    public static final String aesDecryptName;

    static {
        if (System.getProperty("java.specification.version").compareTo("1.9") < 0) {
            cbcEncryptName = "encrypt";
            cbcDecryptName = "decrypt";
            aesEncryptName = "encryptBlock";
            aesDecryptName = "decryptBlock";
        } else {
            cbcEncryptName = "implEncrypt";
            cbcDecryptName = "implDecrypt";
            aesEncryptName = "implEncryptBlock";
            aesDecryptName = "implDecryptBlock";
        }
    }

    private static void registerAESPlugins(InvocationPlugins plugins, HotSpotVMConfig config) {
        if (config.useAESIntrinsics) {
            assert config.aescryptEncryptBlockStub != 0L;
            assert config.aescryptDecryptBlockStub != 0L;
            assert config.cipherBlockChainingEncryptAESCryptStub != 0L;
            assert config.cipherBlockChainingDecryptAESCryptStub != 0L;
            Class<?> c = MethodSubstitutionPlugin.resolveClass("com.sun.crypto.provider.CipherBlockChaining", true);
            if (c != null) {
                Registration r = new Registration(plugins, c);
                r.registerMethodSubstitution(CipherBlockChainingSubstitutions.class, cbcEncryptName, Receiver.class, byte[].class, int.class, int.class, byte[].class, int.class);
                r.registerMethodSubstitution(CipherBlockChainingSubstitutions.class, cbcDecryptName, Receiver.class, byte[].class, int.class, int.class, byte[].class, int.class);
            }
            c = MethodSubstitutionPlugin.resolveClass("com.sun.crypto.provider.AESCrypt", true);
            if (c != null) {
                Registration r = new Registration(plugins, c);
                r.registerMethodSubstitution(AESCryptSubstitutions.class, aesEncryptName, Receiver.class, byte[].class, int.class, byte[].class, int.class);
                r.registerMethodSubstitution(AESCryptSubstitutions.class, aesDecryptName, Receiver.class, byte[].class, int.class, byte[].class, int.class);
            }
        }
    }

    private static void registerCRC32Plugins(InvocationPlugins plugins, HotSpotVMConfig config) {
        if (config.useCRC32Intrinsics) {
            assert config.aescryptEncryptBlockStub != 0L;
            assert config.aescryptDecryptBlockStub != 0L;
            assert config.cipherBlockChainingEncryptAESCryptStub != 0L;
            assert config.cipherBlockChainingDecryptAESCryptStub != 0L;
            Registration r = new Registration(plugins, CRC32.class);
            r.registerMethodSubstitution(CRC32Substitutions.class, "update", int.class, int.class);
            if (System.getProperty("java.specification.version").compareTo("1.9") < 0) {
                r.registerMethodSubstitution(CRC32Substitutions.class, "updateBytes", int.class, byte[].class, int.class, int.class);
                r.registerMethodSubstitution(CRC32Substitutions.class, "updateByteBuffer", int.class, long.class, int.class, int.class);
            } else {
                r.registerMethodSubstitution(CRC32Substitutions.class, "updateBytes0", int.class, byte[].class, int.class, int.class);
                r.registerMethodSubstitution(CRC32Substitutions.class, "updateByteBuffer0", int.class, long.class, int.class, int.class);
            }
        }
    }
}
