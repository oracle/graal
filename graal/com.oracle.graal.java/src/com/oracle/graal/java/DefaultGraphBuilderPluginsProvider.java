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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;

@ServiceProvider(GraphBuilderPluginsProvider.class)
public class DefaultGraphBuilderPluginsProvider implements GraphBuilderPluginsProvider {
    public void registerPlugins(MetaAccessProvider metaAccess, GraphBuilderPlugins plugins) {
        plugins.register(metaAccess, ObjectPlugin.class);
    }

    enum ObjectPlugin implements GraphBuilderPlugin {
        init() {
            public boolean handleInvocation(GraphBuilderContext builder, ValueNode[] args) {
                assert args.length == 1;
                ValueNode rcvr = args[0];
                ObjectStamp objectStamp = (ObjectStamp) rcvr.stamp();

                boolean needsCheck = true;
                if (objectStamp.isExactType()) {
                    needsCheck = objectStamp.type().hasFinalizer();
                } else if (objectStamp.type() != null && !objectStamp.type().hasFinalizableSubclass()) {
                    // if either the declared type of receiver or the holder
                    // can be assumed to have no finalizers
                    Assumptions assumptions = builder.getAssumptions();
                    if (assumptions.useOptimisticAssumptions()) {
                        assumptions.recordNoFinalizableSubclassAssumption(objectStamp.type());
                        needsCheck = false;
                    }
                }

                if (needsCheck) {
                    builder.append(new RegisterFinalizerNode(rcvr));
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
}
