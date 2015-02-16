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

import static com.oracle.graal.java.GraphBuilderContext.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.java.*;
import com.oracle.graal.java.GraphBuilderPlugin.InvocationPlugin;
import com.oracle.graal.java.InvocationPlugins.Registration;
import com.oracle.graal.java.InvocationPlugins.Registration.Receiver;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.options.*;

/**
 * Provider of HotSpot specific {@link GraphBuilderPlugin}s.
 */
public class HotSpotGraphBuilderPlugins {
    public static void registerPlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins) {
        // Object.class
        Registration r = new Registration(plugins, metaAccess, Object.class);
        r.register1("getClass", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode rcvr) {
                ObjectStamp objectStamp = (ObjectStamp) rcvr.stamp();
                ValueNode mirror;
                if (objectStamp.isExactType() && objectStamp.nonNull()) {
                    mirror = builder.append(ConstantNode.forConstant(objectStamp.type().getJavaClass(), builder.getMetaAccess()));
                } else {
                    StampProvider stampProvider = builder.getStampProvider();
                    LoadHubNode hub = builder.append(new LoadHubNode(stampProvider, nullCheckedValue(builder, rcvr)));
                    mirror = builder.append(new HubGetClassNode(builder.getMetaAccess(), hub));
                }
                builder.push(Kind.Object, mirror);
                return true;
            }
        });

        // Class.class
        r = new Registration(plugins, metaAccess, Class.class);
        r.register2("cast", Receiver.class, Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode rcvr, ValueNode object) {
                if (rcvr.isConstant() && !rcvr.isNullConstant()) {
                    ResolvedJavaType type = builder.getConstantReflection().asJavaType(rcvr.asConstant());
                    if (type != null && !type.isPrimitive()) {
                        builder.push(Kind.Object, builder.append(CheckCastNode.create(type, object, null, false, builder.getAssumptions())));
                        return true;
                    }
                }
                return false;
            }
        });
        r.register2("isInstance", Receiver.class, Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode rcvr, ValueNode object) {
                if (rcvr.isConstant() && !rcvr.isNullConstant()) {
                    ResolvedJavaType type = builder.getConstantReflection().asJavaType(rcvr.asConstant());
                    if (type != null && !type.isPrimitive()) {
                        LogicNode node = builder.append(InstanceOfNode.create(type, object, null));
                        builder.push(Kind.Boolean.getStackKind(), builder.append(ConditionalNode.create(node)));
                        return true;
                    }
                }
                return false;
            }
        });

        // StableOptionValue.class
        r = new Registration(plugins, metaAccess, StableOptionValue.class);
        r.register1("getValue", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode rcvr) {
                if (rcvr.isConstant() && !rcvr.isNullConstant()) {
                    Object object = ((HotSpotObjectConstantImpl) rcvr.asConstant()).object();
                    StableOptionValue<?> option = (StableOptionValue<?>) object;
                    ConstantNode value = builder.append(ConstantNode.forConstant(HotSpotObjectConstantImpl.forObject(option.getValue()), builder.getMetaAccess()));
                    builder.push(Kind.Object, value);
                    return true;
                }
                return false;
            }
        });
    }
}
