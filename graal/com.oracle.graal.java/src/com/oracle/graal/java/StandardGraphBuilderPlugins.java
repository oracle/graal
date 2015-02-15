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
package com.oracle.graal.java;

import static com.oracle.graal.java.GraphBuilderContext.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.java.GraphBuilderPlugins.InvocationPlugin;
import com.oracle.graal.java.GraphBuilderPlugins.Registration;
import com.oracle.graal.java.GraphBuilderPlugins.Registration.Receiver;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;

/**
 * Provider of non-runtime specific {@link GraphBuilderPlugin}s.
 */
public class StandardGraphBuilderPlugins {
    public static void registerPlugins(MetaAccessProvider metaAccess, GraphBuilderPlugins plugins) {
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

        GraalDirectivePlugins.registerPlugins(metaAccess, plugins);
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

        void register(MetaAccessProvider metaAccess, GraphBuilderPlugins plugins) {
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

        void register(MetaAccessProvider metaAccess, GraphBuilderPlugins plugins) {
            String name = kind.toJavaClass().getSimpleName() + "Value";
            ResolvedJavaMethod method = Registration.resolve(metaAccess, kind.toBoxedJavaClass(), name);
            plugins.register(method, this);
        }
    }
}
