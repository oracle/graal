/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.hotspot.replacements.SystemSubstitutions.*;

import java.lang.invoke.*;

import sun.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Receiver;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.nodes.ClassQueryNode.Query;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.hotspot.replacements.arraycopy.*;
import com.oracle.graal.hotspot.word.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.options.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.word.*;

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
        InvocationPlugins invocationPlugins = new HotSpotInvocationPlugins(config, metaAccess, constantReflection.getMethodHandleAccess());

        Plugins plugins = new Plugins(invocationPlugins);
        NodeIntrinsificationPhase nodeIntrinsification = new NodeIntrinsificationPhase(metaAccess, constantReflection, snippetReflection, foreignCalls, stampProvider);
        HotSpotWordOperationPlugin wordOperationPlugin = new HotSpotWordOperationPlugin(snippetReflection, wordTypes);

        plugins.setParameterPlugin(new HotSpotParameterPlugin(wordTypes));
        plugins.setLoadFieldPlugin(new HotSpotLoadFieldPlugin(metaAccess, constantReflection));
        plugins.setLoadIndexedPlugin(new HotSpotLoadIndexedPlugin(wordTypes));
        plugins.setInlineInvokePlugin(new DefaultInlineInvokePlugin(replacements));
        plugins.setGenericInvocationPlugin(new DefaultGenericInvocationPlugin(metaAccess, nodeIntrinsification, wordOperationPlugin));

        registerObjectPlugins(invocationPlugins);
        registerClassPlugins(invocationPlugins);
        registerSystemPlugins(invocationPlugins, foreignCalls);
        registerThreadPlugins(invocationPlugins, metaAccess, wordTypes, config);
        registerCallSitePlugins(invocationPlugins);
        registerReflectionPlugins(invocationPlugins);
        registerStableOptionPlugins(invocationPlugins);
        registerAESPlugins(invocationPlugins, config);
        StandardGraphBuilderPlugins.registerInvocationPlugins(metaAccess, invocationPlugins, !config.useHeapProfiler);

        return plugins;
    }

    private static void registerObjectPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Object.class);
        r.register1("clone", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode object = receiver.get();
                b.addPush(Kind.Object, new ObjectCloneNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnType(), object));
                return true;
            }
        });
        r.register1("hashCode", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(new IdentityHashCodeNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnType(), receiver.get()));
                return true;
            }
        });
    }

    private static void registerClassPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Class.class);

        for (Query query : Query.values()) {
            r.register1(query.name(), Receiver.class, new InvocationPlugin() {
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                    ValueNode javaClass = receiver.get();
                    ValueNode folded = ClassQueryNode.tryFold(GraphUtil.originalValue(javaClass), query, b.getMetaAccess(), b.getConstantReflection());
                    if (folded != null) {
                        b.addPush(query.returnKind, folded);
                    } else {
                        b.addPush(query.returnKind, new ClassQueryNode(b.getInvokeKind(), targetMethod, query, b.bci(), b.getInvokeReturnType(), javaClass));
                    }
                    return true;
                }
            });
        }
        r.register2("cast", Receiver.class, Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode object) {
                ValueNode javaClass = receiver.get();
                ValueNode folded = ClassCastNode.tryFold(GraphUtil.originalValue(javaClass), object, b.getConstantReflection(), b.getAssumptions());
                if (folded != null) {
                    b.addPush(Kind.Object, folded);
                } else {
                    b.addPush(Kind.Object, new ClassCastNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnType(), javaClass, object));
                }
                return true;
            }
        });
    }

    private static void registerCallSitePlugins(InvocationPlugins plugins) {
        InvocationPlugin plugin = new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode callSite = GraphUtil.originalValue(receiver.get());
                ValueNode folded = CallSiteTargetNode.tryFold(callSite, b.getMetaAccess(), b.getAssumptions());
                if (folded != null) {
                    b.addPush(Kind.Object, folded);
                } else {
                    b.addPush(Kind.Object, new CallSiteTargetNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnType(), callSite));
                }
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
                b.addPush(new ReflectionGetCallerClassNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnType()));
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
                b.addPush(new IdentityHashCodeNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnType(), object));
                return true;
            }
        });
        r.register5("arraycopy", Object.class, int.class, Object.class, int.class, int.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode src, ValueNode srcPos, ValueNode dst, ValueNode dstPos, ValueNode length) {
                b.add(new ArrayCopyNode(b.getInvokeKind(), targetMethod, b.bci(), b.getInvokeReturnType(), src, srcPos, dst, dstPos, length));
                return true;
            }
        });
    }

    private static void registerThreadPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, WordTypes wordTypes, HotSpotVMConfig config) {
        Registration r = new Registration(plugins, Thread.class);
        r.register0("currentThread", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                CurrentJavaThreadNode thread = b.add(new CurrentJavaThreadNode(wordTypes.getWordKind()));
                ConstantLocationNode location = b.add(new ConstantLocationNode(JAVA_THREAD_THREAD_OBJECT_LOCATION, config.threadObjectOffset));
                boolean compressible = false;
                ValueNode javaThread = WordOperationPlugin.readOp(b, Kind.Object, thread, location, BarrierType.NONE, compressible);
                boolean exactType = compressible;
                boolean nonNull = true;
                b.addPush(Kind.Object, new PiNode(javaThread, metaAccess.lookupJavaType(Thread.class), exactType, nonNull));
                return true;
            }
        });
    }

    private static void registerStableOptionPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, StableOptionValue.class);
        r.register1("getValue", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (receiver.isConstant()) {
                    Object object = ((HotSpotObjectConstantImpl) receiver.get().asConstant()).object();
                    StableOptionValue<?> option = (StableOptionValue<?>) object;
                    b.addPush(Kind.Object, ConstantNode.forConstant(HotSpotObjectConstantImpl.forObject(option.getValue()), b.getMetaAccess()));
                    return true;
                }
                return false;
            }
        });
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
                r.registerMethodSubstitution(CipherBlockChainingSubstitutions.class, "encrypt", Receiver.class, byte[].class, int.class, int.class, byte[].class, int.class);
                r.registerMethodSubstitution(CipherBlockChainingSubstitutions.class, "decrypt", Receiver.class, byte[].class, int.class, int.class, byte[].class, int.class);
            }
            c = MethodSubstitutionPlugin.resolveClass("com.sun.crypto.provider.AESCrypt", true);
            if (c != null) {
                Registration r = new Registration(plugins, c);
                r.registerMethodSubstitution(AESCryptSubstitutions.class, "encryptBlock", Receiver.class, byte[].class, int.class, byte[].class, int.class);
                r.registerMethodSubstitution(AESCryptSubstitutions.class, "decryptBlock", Receiver.class, byte[].class, int.class, byte[].class, int.class);
            }
        }
    }
}
