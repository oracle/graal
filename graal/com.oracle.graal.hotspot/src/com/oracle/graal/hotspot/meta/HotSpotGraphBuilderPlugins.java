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
import static com.oracle.graal.java.GraphBuilderContext.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.hotspot.word.*;
import com.oracle.graal.java.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.java.*;
import com.oracle.graal.java.GraphBuilderPlugin.InvocationPlugin;
import com.oracle.graal.java.InvocationPlugins.Registration;
import com.oracle.graal.java.InvocationPlugins.Registration.Receiver;
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
     */
    public static Plugins create(HotSpotVMConfig config, HotSpotProviders providers) {

        MetaAccessProvider metaAccess = providers.getMetaAccess();
        HotSpotWordTypes wordTypes = providers.getWordTypes();
        InvocationPlugins invocationPlugins = new HotSpotInvocationPlugins(config, metaAccess);

        Plugins plugins = new Plugins(invocationPlugins);
        NodeIntrinsificationPhase nodeIntrinsification = new NodeIntrinsificationPhase(providers, providers.getSnippetReflection());
        ConstantReflectionProvider constantReflection = providers.getConstantReflection();
        HotSpotWordOperationPlugin wordOperationPlugin = new HotSpotWordOperationPlugin(providers.getSnippetReflection(), wordTypes);

        plugins.setParameterPlugin(new HotSpotParameterPlugin(wordTypes));
        plugins.setLoadFieldPlugin(new HotSpotLoadFieldPlugin(metaAccess, constantReflection));
        plugins.setLoadIndexedPlugin(new HotSpotLoadIndexedPlugin(wordTypes));
        plugins.setInlineInvokePlugin(new HotSpotInlineInvokePlugin(nodeIntrinsification, (ReplacementsImpl) providers.getReplacements()));
        plugins.setGenericInvocationPlugin(new DefaultGenericInvocationPlugin(nodeIntrinsification, wordOperationPlugin));

        registerObjectPlugins(invocationPlugins, metaAccess);
        registerSystemPlugins(invocationPlugins, metaAccess, providers.getForeignCalls());
        registerThreadPlugins(invocationPlugins, metaAccess, wordTypes, config);
        registerStableOptionPlugins(invocationPlugins, metaAccess);
        StandardGraphBuilderPlugins.registerInvocationPlugins(providers.getMetaAccess(), providers.getCodeCache().target.arch, invocationPlugins, !config.useHeapProfiler);
        return plugins;
    }

    private static void registerObjectPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess) {
        Registration r = new Registration(plugins, metaAccess, Object.class);
        r.register1("getClass", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode rcvr) {
                ObjectStamp objectStamp = (ObjectStamp) rcvr.stamp();
                ValueNode mirror;
                if (objectStamp.isExactType() && objectStamp.nonNull()) {
                    mirror = b.append(ConstantNode.forConstant(objectStamp.type().getJavaClass(), b.getMetaAccess()));
                } else {
                    StampProvider stampProvider = b.getStampProvider();
                    LoadHubNode hub = b.append(new LoadHubNode(stampProvider, nullCheckedValue(b, rcvr)));
                    mirror = b.append(new HubGetClassNode(b.getMetaAccess(), hub));
                }
                b.push(Kind.Object, mirror);
                return true;
            }
        });
    }

    private static void registerSystemPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls) {
        Registration r = new Registration(plugins, metaAccess, System.class);
        r.register0("currentTimeMillis", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod) {
                ForeignCallNode foreignCall = new ForeignCallNode(foreignCalls, SystemSubstitutions.JAVA_TIME_MILLIS, StampFactory.forKind(Kind.Long));
                b.push(Kind.Long, b.append(foreignCall));
                foreignCall.setStateAfter(b.createStateAfter());
                return true;
            }
        });
        r.register0("nanoTime", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod) {
                ForeignCallNode foreignCall = new ForeignCallNode(foreignCalls, SystemSubstitutions.JAVA_TIME_NANOS, StampFactory.forKind(Kind.Long));
                b.push(Kind.Long, b.append(foreignCall));
                foreignCall.setStateAfter(b.createStateAfter());
                return true;
            }
        });
    }

    private static void registerThreadPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, WordTypes wordTypes, HotSpotVMConfig config) {
        Registration r = new Registration(plugins, metaAccess, Thread.class);
        r.register0("currentThread", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod) {
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

    private static void registerStableOptionPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess) {
        Registration r = new Registration(plugins, metaAccess, StableOptionValue.class);
        r.register1("getValue", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode rcvr) {
                if (rcvr.isConstant() && !rcvr.isNullConstant()) {
                    Object object = ((HotSpotObjectConstantImpl) rcvr.asConstant()).object();
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
