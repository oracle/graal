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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;

/**
 * Provider of non-runtime specific {@link GraphBuilderPlugin}s.
 */
@ServiceProvider(GraphBuilderPluginsProvider.class)
public class DefaultGraphBuilderPluginsProvider implements GraphBuilderPluginsProvider {
    public void registerPlugins(MetaAccessProvider metaAccess, GraphBuilderPlugins plugins) {
        plugins.register(metaAccess, ObjectPlugin.class);
        plugins.register(metaAccess, BoxingPlugin.class);
    }

    /**
     * Plugins for {@link Object}.
     */
    enum ObjectPlugin implements GraphBuilderPlugin {
        init() {
            public boolean handleInvocation(GraphBuilderContext builder, ValueNode[] args) {
                ValueNode object = args[0];
                if (RegisterFinalizerNode.mayHaveFinalizer(object, builder.getAssumptions())) {
                    builder.append(new RegisterFinalizerNode(object));
                }
                return true;
            }

            public ResolvedJavaMethod getInvocationTarget(MetaAccessProvider metaAccess) {
                return GraphBuilderPlugin.resolveTarget(metaAccess, Object.class, "<init>");
            }
        };

        @Override
        public String toString() {
            return Object.class.getName() + "." + name() + "()";
        }
    }

    /**
     * Plugins for the standard primitive box classes (e.g., {@link Integer} and friends).
     */
    enum BoxingPlugin implements GraphBuilderPlugin {
        valueOf$Boolean(Kind.Boolean),
        booleanValue$Boolean(Kind.Boolean),
        valueOf$Byte(Kind.Byte),
        byteValue$Byte(Kind.Byte),
        valueOf$Short(Kind.Short),
        shortValue$Short(Kind.Short),
        valueOf$Char(Kind.Char),
        charValue$Char(Kind.Char),
        valueOf$Int(Kind.Int),
        intValue$Int(Kind.Int),
        valueOf$Long(Kind.Long),
        longValue$Long(Kind.Long),
        valueOf$Float(Kind.Float),
        floatValue$Float(Kind.Float),
        valueOf$Double(Kind.Double),
        doubleValue$Double(Kind.Double);

        BoxingPlugin(Kind kind) {
            assert name().startsWith("valueOf$") || name().startsWith(kind.getJavaName() + "Value$");
            this.kind = kind;
            this.box = name().charAt(0) == 'v';
        }

        private final Kind kind;
        private final boolean box;

        public final boolean handleInvocation(GraphBuilderContext builder, ValueNode[] args) {
            if (box) {
                ResolvedJavaType resultType = builder.getMetaAccess().lookupJavaType(kind.toBoxedJavaClass());
                builder.push(Kind.Object, builder.append(new BoxNode(args[0], resultType, kind)));
            } else {
                builder.push(kind, builder.append(new UnboxNode(args[0], kind)));
            }
            return true;
        }

        public ResolvedJavaMethod getInvocationTarget(MetaAccessProvider metaAccess) {
            Class<?>[] parameterTypes = box ? new Class<?>[]{kind.toJavaClass()} : new Class<?>[0];
            return GraphBuilderPlugin.resolveTarget(metaAccess, kind.toBoxedJavaClass(), name(), parameterTypes);
        }
    }
}
