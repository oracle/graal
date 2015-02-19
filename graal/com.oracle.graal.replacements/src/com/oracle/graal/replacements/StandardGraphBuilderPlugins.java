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
package com.oracle.graal.replacements;

import static com.oracle.graal.api.code.MemoryBarriers.*;
import static com.oracle.graal.java.GraphBuilderContext.*;
import static java.lang.Character.*;
import sun.misc.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.java.*;
import com.oracle.graal.java.GraphBuilderPlugin.InvocationPlugin;
import com.oracle.graal.java.InvocationPlugins.Registration;
import com.oracle.graal.java.InvocationPlugins.Registration.Receiver;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.replacements.nodes.*;

/**
 * Provides non-runtime specific {@link InvocationPlugin}s.
 */
public class StandardGraphBuilderPlugins {
    public static void registerInvocationPlugins(MetaAccessProvider metaAccess, InvocationPlugins plugins) {
        Registration r = new Registration(plugins, metaAccess, Object.class);
        r.register1("<init>", Receiver.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode object) {
                if (RegisterFinalizerNode.mayHaveFinalizer(object, builder.getAssumptions())) {
                    builder.append(new RegisterFinalizerNode(object));
                }
                return true;
            }
        });

        r = new Registration(plugins, metaAccess, Math.class);
        r.register1("abs", Float.TYPE, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode value) {
                builder.push(Kind.Float, builder.append(new AbsNode(value)));
                return true;
            }
        });
        r.register1("abs", Double.TYPE, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode value) {
                builder.push(Kind.Double, builder.append(new AbsNode(value)));
                return true;
            }
        });
        r.register1("sqrt", Double.TYPE, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext builder, ValueNode value) {
                builder.push(Kind.Double, builder.append(new SqrtNode(value)));
                return true;
            }
        });

        for (Kind kind : Kind.values()) {
            if (kind.isPrimitive() && kind != Kind.Void) {
                new BoxPlugin(kind).register(metaAccess, plugins);
                new UnboxPlugin(kind).register(metaAccess, plugins);
            }
        }

        r = new Registration(plugins, metaAccess, Unsafe.class);
        for (Kind kind : Kind.values()) {
            if ((kind.isPrimitive() && kind != Kind.Void) || kind == Kind.Object) {
                String kindName = kind.getJavaName();
                kindName = toUpperCase(kindName.charAt(0)) + kindName.substring(1);
                String getName = "get" + kindName;
                String putName = "put" + kindName;
                r.register3(getName, Receiver.class, Object.class, long.class, new UnsafeGetPlugin(kind, false));
                r.register4(putName, Receiver.class, Object.class, long.class, kind == Kind.Object ? Object.class : kind.toJavaClass(), new UnsafePutPlugin(kind, false));
                r.register3(getName + "Volatile", Receiver.class, Object.class, long.class, new UnsafeGetPlugin(kind, true));
                r.register4(putName + "Volatile", Receiver.class, Object.class, long.class, kind == Kind.Object ? Object.class : kind.toJavaClass(), new UnsafePutPlugin(kind, true));
                if (kind != Kind.Boolean && kind != Kind.Object) {
                    r.register2(getName, Receiver.class, long.class, new UnsafeGetPlugin(kind, false));
                    r.register3(putName, Receiver.class, long.class, kind.toJavaClass(), new UnsafePutPlugin(kind, false));
                }
            }
        }
        GraalDirectivePlugins.registerInvocationPlugins(metaAccess, plugins);
    }

    static class BoxPlugin implements InvocationPlugin {

        private final Kind kind;

        BoxPlugin(Kind kind) {
            this.kind = kind;
        }

        public boolean apply(GraphBuilderContext builder, ValueNode value) {
            ResolvedJavaType resultType = builder.getMetaAccess().lookupJavaType(kind.toBoxedJavaClass());
            builder.push(Kind.Object, builder.append(new BoxNode(value, resultType, kind)));
            return true;
        }

        void register(MetaAccessProvider metaAccess, InvocationPlugins plugins) {
            ResolvedJavaMethod method = Registration.resolve(metaAccess, kind.toBoxedJavaClass(), "valueOf", kind.toJavaClass());
            plugins.register(method, this);
        }
    }

    static class UnboxPlugin implements InvocationPlugin {

        private final Kind kind;

        UnboxPlugin(Kind kind) {
            this.kind = kind;
        }

        public boolean apply(GraphBuilderContext builder, ValueNode value) {
            ValueNode valueNode = UnboxNode.create(builder.getMetaAccess(), builder.getConstantReflection(), nullCheckedValue(builder, value), kind);
            builder.push(kind.getStackKind(), builder.append(valueNode));
            return true;
        }

        void register(MetaAccessProvider metaAccess, InvocationPlugins plugins) {
            String name = kind.toJavaClass().getSimpleName() + "Value";
            ResolvedJavaMethod method = Registration.resolve(metaAccess, kind.toBoxedJavaClass(), name);
            plugins.register(method, this);
        }
    }

    static class UnsafeGetPlugin implements InvocationPlugin {

        private final Kind returnKind;
        private final boolean isVolatile;

        public UnsafeGetPlugin(Kind returnKind, boolean isVolatile) {
            this.returnKind = returnKind;
            this.isVolatile = isVolatile;
        }

        public boolean apply(GraphBuilderContext builder, ValueNode ignoredUnsafe, ValueNode address) {
            builder.push(returnKind.getStackKind(), builder.append(new DirectReadNode(address, returnKind)));
            return true;
        }

        public boolean apply(GraphBuilderContext builder, ValueNode ignoredUnsafe, ValueNode object, ValueNode offset) {
            if (isVolatile) {
                builder.append(new MembarNode(JMM_PRE_VOLATILE_READ));
            }
            builder.push(returnKind.getStackKind(), builder.append(new UnsafeLoadNode(object, offset, returnKind, LocationIdentity.ANY_LOCATION)));
            if (isVolatile) {
                builder.append(new MembarNode(JMM_POST_VOLATILE_READ));
            }
            return true;
        }
    }

    static class UnsafePutPlugin implements InvocationPlugin {

        private final Kind kind;
        private final boolean isVolatile;

        public UnsafePutPlugin(Kind kind, boolean isVolatile) {
            this.kind = kind;
            this.isVolatile = isVolatile;
        }

        public boolean apply(GraphBuilderContext builder, ValueNode ignoredUnsafe, ValueNode address, ValueNode value) {
            builder.append(new DirectStoreNode(address, value, kind));
            return true;
        }

        public boolean apply(GraphBuilderContext builder, ValueNode ignoredUnsafe, ValueNode object, ValueNode offset, ValueNode value) {
            if (isVolatile) {
                builder.append(new MembarNode(JMM_PRE_VOLATILE_WRITE));
            }
            builder.append(new UnsafeStoreNode(object, offset, value, kind, LocationIdentity.ANY_LOCATION));
            if (isVolatile) {
                builder.append(new MembarNode(JMM_PRE_VOLATILE_WRITE));
            }
            return true;
        }

    }
}
