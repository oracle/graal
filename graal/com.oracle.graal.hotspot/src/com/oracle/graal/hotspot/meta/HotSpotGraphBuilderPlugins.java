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

import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.java.GraphBuilderContext.*;
import static java.lang.Character.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.hotspot.word.*;
import com.oracle.graal.java.*;
import com.oracle.graal.java.GraphBuilderPlugin.InvocationPlugin;
import com.oracle.graal.java.InvocationPlugins.Registration;
import com.oracle.graal.java.InvocationPlugins.Registration.Receiver;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.options.*;
import com.oracle.graal.word.*;
import com.oracle.graal.word.nodes.*;

/**
 * Provides HotSpot specific {@link InvocationPlugin}s.
 */
public class HotSpotGraphBuilderPlugins {
    public static void registerInvocationPlugins(HotSpotProviders providers, InvocationPlugins plugins) {
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        SnippetReflectionProvider snippetReflection = providers.getSnippetReflection();
        Kind wordKind = providers.getCodeCache().getTarget().wordKind;

        registerObjectPlugins(plugins, metaAccess);
        registerClassPlugins(plugins, metaAccess);
        registerStableOptionPlugins(plugins, metaAccess);
        registerMetaspacePointerPlugins(plugins, metaAccess, snippetReflection, wordKind);
    }

    private static void registerObjectPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess) {
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
    }

    private static void registerClassPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess) {
        Registration r = new Registration(plugins, metaAccess, Class.class);
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
    }

    private static void registerStableOptionPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess) {
        Registration r = new Registration(plugins, metaAccess, StableOptionValue.class);
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

    private static void registerMetaspacePointerPlugins(InvocationPlugins plugins, MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection, Kind wordKind) {
        Registration r = new Registration(plugins, metaAccess, MetaspacePointer.class);
        r.register1("isNull", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode pointer) {
                assert pointer.stamp() instanceof MetaspacePointerStamp;
                IsNullNode isNull = builder.append(new IsNullNode(pointer));
                ConstantNode trueValue = builder.append(ConstantNode.forBoolean(true));
                ConstantNode falseValue = builder.append(ConstantNode.forBoolean(false));
                builder.push(Kind.Boolean.getStackKind(), builder.append(new ConditionalNode(isNull, trueValue, falseValue)));
                return true;
            }
        });
        r.register1("asWord", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode pointer) {
                builder.append(new PointerCastNode(StampFactory.forKind(wordKind), pointer));
                return true;
            }
        });
        r.register2("readObject", Receiver.class, int.class, new ReadOp(snippetReflection, wordKind, Kind.Object, BarrierType.NONE, true));
        r.register3("readObject", Receiver.class, int.class, LocationIdentity.class, new ReadOp(snippetReflection, wordKind, Kind.Object, BarrierType.NONE, true));
        r.register3("readObject", Receiver.class, int.class, BarrierType.class, new ReadOp(snippetReflection, wordKind, Kind.Object, BarrierType.NONE, true));
        r.register2("readObject", Receiver.class, WordBase.class, new ReadOp(snippetReflection, wordKind, Kind.Object, BarrierType.NONE, true));
        r.register3("readObject", Receiver.class, WordBase.class, LocationIdentity.class, new ReadOp(snippetReflection, wordKind, Kind.Object, BarrierType.NONE, true));
        r.register3("readObject", Receiver.class, WordBase.class, BarrierType.class, new ReadOp(snippetReflection, wordKind, Kind.Object, BarrierType.NONE, true));

        registerWordOpPlugins(r, snippetReflection, wordKind, Kind.Byte, Kind.Short, Kind.Char, Kind.Int, Kind.Float, Kind.Long, Kind.Double);
    }

    private static void registerWordOpPlugins(Registration r, SnippetReflectionProvider snippetReflection, Kind wordKind, Kind... kinds) {
        for (Kind kind : kinds) {
            String kindName = kind.getJavaName();
            kindName = toUpperCase(kindName.charAt(0)) + kindName.substring(1);
            String getName = "read" + kindName;
            // String putName = "write" + kindName;
            r.register2(getName, Receiver.class, int.class, new ReadOp(snippetReflection, wordKind, kind));
            r.register3(getName, Receiver.class, int.class, LocationIdentity.class, new ReadOp(snippetReflection, wordKind, kind));
        }
    }

    static class ReadOp implements InvocationPlugin {
        final SnippetReflectionProvider snippetReflection;
        final Kind wordKind;
        final Kind resultKind;
        final BarrierType barrierType;
        final boolean compressible;

        public ReadOp(SnippetReflectionProvider snippetReflection, Kind wordKind, Kind resultKind, BarrierType barrierType, boolean compressible) {
            this.snippetReflection = snippetReflection;
            this.wordKind = wordKind;
            this.resultKind = resultKind;
            this.barrierType = barrierType;
            this.compressible = compressible;
        }

        public ReadOp(SnippetReflectionProvider snippetReflection, Kind wordKind, Kind resultKind) {
            this(snippetReflection, wordKind, resultKind, BarrierType.NONE, false);
        }

        public boolean apply(GraphBuilderContext builder, ValueNode pointer, ValueNode offset) {
            LocationNode location = makeLocation(builder, offset, ANY_LOCATION, wordKind);
            builder.push(resultKind, builder.append(readOp(builder, resultKind, pointer, location, barrierType, compressible)));
            return true;
        }

        public boolean apply(GraphBuilderContext builder, ValueNode pointer, ValueNode offset, ValueNode locationIdentityArg) {
            assert locationIdentityArg.isConstant();
            LocationIdentity locationIdentity = snippetReflection.asObject(LocationIdentity.class, locationIdentityArg.asJavaConstant());
            LocationNode location = makeLocation(builder, offset, locationIdentity, wordKind);
            builder.push(resultKind, builder.append(readOp(builder, resultKind, pointer, location, barrierType, compressible)));
            return true;
        }
    }

    public static ValueNode readOp(GraphBuilderContext builder, Kind readKind, ValueNode base, LocationNode location, BarrierType barrierType, boolean compressible) {
        JavaReadNode read = builder.append(new JavaReadNode(readKind, base, location, barrierType, compressible));
        /*
         * The read must not float outside its block otherwise it may float above an explicit zero
         * check on its base address.
         */
        read.setGuard(builder.getCurrentBlockGuard());
        return read;
    }

    public static LocationNode makeLocation(GraphBuilderContext builder, ValueNode offset, LocationIdentity locationIdentity, Kind wordKind) {
        return builder.append(new IndexedLocationNode(locationIdentity, 0, fromSigned(builder, offset, wordKind), 1));
    }

    public static LocationNode makeLocation(GraphBuilderContext builder, ValueNode offset, ValueNode locationIdentity, Kind wordKind) {
        if (locationIdentity.isConstant()) {
            return makeLocation(builder, offset, builder.getSnippetReflection().asObject(LocationIdentity.class, locationIdentity.asJavaConstant()), wordKind);
        }
        return builder.append(new SnippetLocationNode(builder.getSnippetReflection(), locationIdentity, builder.append(ConstantNode.forLong(0)), fromSigned(builder, offset, wordKind),
                        builder.append(ConstantNode.forInt(1))));
    }

    public static ValueNode fromUnsigned(GraphBuilderContext builder, ValueNode value, Kind wordKind) {
        return convert(builder, value, wordKind, true);
    }

    public static ValueNode fromSigned(GraphBuilderContext builder, ValueNode value, Kind wordKind) {
        return convert(builder, value, wordKind, false);
    }

    public static ValueNode toUnsigned(GraphBuilderContext builder, ValueNode value, Kind toKind) {
        return convert(builder, value, toKind, true);
    }

    public static ValueNode convert(GraphBuilderContext builder, ValueNode value, Kind toKind, boolean unsigned) {
        if (value.getKind() == toKind) {
            return value;
        }

        if (toKind == Kind.Int) {
            assert value.getKind() == Kind.Long;
            return builder.append(new NarrowNode(value, 32));
        } else {
            assert toKind == Kind.Long;
            assert value.getKind().getStackKind() == Kind.Int;
            if (unsigned) {
                return builder.append(new ZeroExtendNode(value, 64));
            } else {
                return builder.append(new SignExtendNode(value, 64));
            }
        }
    }
}
