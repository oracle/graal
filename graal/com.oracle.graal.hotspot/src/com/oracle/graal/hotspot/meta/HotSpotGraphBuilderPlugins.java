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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Receiver;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.hotspot.word.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
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
                    SnippetReflectionProvider snippetReflection, ForeignCallsProvider foreignCalls, StampProvider stampProvider, ReplacementsImpl replacements, Architecture arch) {
        InvocationPlugins invocationPlugins = new HotSpotInvocationPlugins(config, metaAccess);

        Plugins plugins = new Plugins(invocationPlugins);
        NodeIntrinsificationPhase nodeIntrinsification = new NodeIntrinsificationPhase(metaAccess, constantReflection, snippetReflection, foreignCalls, stampProvider);
        HotSpotWordOperationPlugin wordOperationPlugin = new HotSpotWordOperationPlugin(snippetReflection, wordTypes);

        plugins.setParameterPlugin(new HotSpotParameterPlugin(wordTypes));
        plugins.setLoadFieldPlugin(new HotSpotLoadFieldPlugin(metaAccess, constantReflection));
        plugins.setLoadIndexedPlugin(new HotSpotLoadIndexedPlugin(wordTypes));
        plugins.setInlineInvokePlugin(new HotSpotInlineInvokePlugin(nodeIntrinsification, replacements));
        plugins.setGenericInvocationPlugin(new DefaultGenericInvocationPlugin(nodeIntrinsification, wordOperationPlugin));

        registerObjectPlugins(invocationPlugins);
        registerSystemPlugins(invocationPlugins, foreignCalls);
        registerThreadPlugins(invocationPlugins, metaAccess, wordTypes, config);
        registerStableOptionPlugins(invocationPlugins);
        StandardGraphBuilderPlugins.registerInvocationPlugins(metaAccess, arch, invocationPlugins, !config.useHeapProfiler);

        return plugins;
    }

    private static void registerObjectPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Object.class);
        r.register1("getClass", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ValueNode rcvr = receiver.get();
                ObjectStamp objectStamp = (ObjectStamp) rcvr.stamp();
                ValueNode mirror;
                if (objectStamp.isExactType() && objectStamp.nonNull() && !GraalOptions.ImmutableCode.getValue()) {
                    mirror = b.append(ConstantNode.forConstant(objectStamp.type().getJavaClass(), b.getMetaAccess()));
                } else {
                    StampProvider stampProvider = b.getStampProvider();
                    LoadHubNode hub = b.append(new LoadHubNode(stampProvider, rcvr));
                    mirror = b.append(new HubGetClassNode(b.getMetaAccess(), hub));
                }
                b.push(Kind.Object, mirror);
                return true;
            }
        });
    }

    private static void registerSystemPlugins(InvocationPlugins plugins, ForeignCallsProvider foreignCalls) {
        Registration r = new Registration(plugins, System.class);
        r.register0("currentTimeMillis", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ForeignCallNode foreignCall = new ForeignCallNode(foreignCalls, SystemSubstitutions.JAVA_TIME_MILLIS, StampFactory.forKind(Kind.Long));
                b.push(Kind.Long, b.append(foreignCall));
                foreignCall.setStateAfter(b.createStateAfter());
                return true;
            }
        });
        r.register0("nanoTime", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                ForeignCallNode foreignCall = new ForeignCallNode(foreignCalls, SystemSubstitutions.JAVA_TIME_NANOS, StampFactory.forKind(Kind.Long));
                b.push(Kind.Long, b.append(foreignCall));
                foreignCall.setStateAfter(b.createStateAfter());
                return true;
            }
        });
    }

    private static void registerThreadPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, WordTypes wordTypes, HotSpotVMConfig config) {
        Registration r = new Registration(plugins, Thread.class);
        r.register0("currentThread", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                CurrentJavaThreadNode thread = b.append(new CurrentJavaThreadNode(wordTypes.getWordKind()));
                ConstantLocationNode location = b.append(new ConstantLocationNode(JAVA_THREAD_THREAD_OBJECT_LOCATION, config.threadObjectOffset));
                boolean compressible = false;
                ValueNode javaThread = WordOperationPlugin.readOp(b, Kind.Object, thread, location, BarrierType.NONE, compressible);
                boolean exactType = compressible;
                boolean nonNull = true;
                b.push(Kind.Object, b.append(new PiNode(javaThread, metaAccess.lookupJavaType(Thread.class), exactType, nonNull)));
                return true;
            }
        });
    }

    private static void registerStableOptionPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, StableOptionValue.class);
        r.register1("getValue", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                if (receiver.isConstant() && !receiver.isNullConstant()) {
                    Object object = ((HotSpotObjectConstantImpl) receiver.get().asConstant()).object();
                    StableOptionValue<?> option = (StableOptionValue<?>) object;
                    ConstantNode value = b.append(ConstantNode.forConstant(HotSpotObjectConstantImpl.forObject(option.getValue()), b.getMetaAccess()));
                    b.push(Kind.Object, value);
                    return true;
                }
                return false;
            }
        });
    }
}
