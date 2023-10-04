/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.compiler.graal.hotspot.meta;

import static jdk.compiler.graal.bytecode.Bytecodes.GETSTATIC;
import static jdk.compiler.graal.bytecode.Bytecodes.INVOKESTATIC;
import static jdk.compiler.graal.bytecode.Bytecodes.NEW;
import static jdk.compiler.graal.bytecode.Bytecodes.PUTSTATIC;

import java.util.function.Supplier;

import jdk.compiler.graal.hotspot.nodes.type.KlassPointerStamp;
import jdk.compiler.graal.hotspot.nodes.KlassBeingInitializedCheckNode;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.FrameState;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.graphbuilderconf.ClassInitializationPlugin;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderContext;

import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class HotSpotJITClassInitializationPlugin implements ClassInitializationPlugin {
    @Override
    public boolean apply(GraphBuilderContext builder, ResolvedJavaType type, Supplier<FrameState> frameState) {
        if (!type.isInitialized() && (type.isInstanceClass() || type.isInterface())) {
            int code = builder.getCode().getCode()[builder.bci()] & 0xff;
            switch (code) {
                case INVOKESTATIC:
                case GETSTATIC:
                case PUTSTATIC:
                case NEW:
                    ValueNode typeConstant = ConstantNode.forConstant(KlassPointerStamp.klass(), ((HotSpotResolvedObjectType) type).klass(), builder.getMetaAccess());
                    builder.add(new KlassBeingInitializedCheckNode(typeConstant));
                    return true;
                default:

            }
        }
        return false;
    }

    @Override
    public boolean supportsLazyInitialization(ConstantPool cp) {
        return false;
    }

    @Override
    public void loadReferencedType(GraphBuilderContext builder, ConstantPool cp, int cpi, int opcode) {
        cp.loadReferencedType(cpi, opcode);
    }

}
