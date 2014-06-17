/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.phases;

import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.nodes.ConstantNode.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.hotspot.HotSpotVMConfig.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

/**
 * For AOT compilation we aren't allowed to use a {@link Class} reference ({@code javaMirror})
 * directly. Instead the {@link Class} reference should be obtained from the {@code Klass} object.
 * The reason for this is, that in Class Data Sharing (CDS) a {@code Klass} object is mapped to a
 * fixed address in memory, but the {@code javaMirror} is not (which lives in the Java heap).
 *
 * Lowering can introduce new {@link ConstantNode}s containing a {@link Class} reference, thus this
 * phase must be applied after {@link LoweringPhase}.
 *
 * @see AheadOfTimeVerificationPhase
 */
public class LoadJavaMirrorWithKlassPhase extends BasePhase<PhaseContext> {

    private final int classMirrorOffset;
    private final CompressEncoding oopEncoding;

    public LoadJavaMirrorWithKlassPhase(int classMirrorOffset, CompressEncoding oopEncoding) {
        this.classMirrorOffset = classMirrorOffset;
        this.oopEncoding = oopEncoding;
    }

    private ValueNode getClassConstantReplacement(StructuredGraph graph, PhaseContext context, Constant constant) {
        if (constant instanceof HotSpotObjectConstant && HotSpotObjectConstant.asObject(constant) instanceof Class<?>) {
            MetaAccessProvider metaAccess = context.getMetaAccess();
            ResolvedJavaType type = metaAccess.lookupJavaType((Class<?>) HotSpotObjectConstant.asObject(constant));
            Constant klass;
            LocationNode location;
            if (type instanceof HotSpotResolvedObjectType) {
                location = ConstantLocationNode.create(FINAL_LOCATION, Kind.Object, classMirrorOffset, graph);
                klass = ((HotSpotResolvedObjectType) type).klass();
            } else {
                /*
                 * Primitive classes are more difficult since they don't have a corresponding Klass*
                 * so get them from Class.TYPE for the java box type.
                 */
                HotSpotResolvedPrimitiveType primitive = (HotSpotResolvedPrimitiveType) type;
                ResolvedJavaType boxingClass = metaAccess.lookupJavaType(primitive.getKind().toBoxedJavaClass());
                klass = ((HotSpotResolvedObjectType) boxingClass).klass();
                HotSpotResolvedJavaField[] a = (HotSpotResolvedJavaField[]) boxingClass.getStaticFields();
                HotSpotResolvedJavaField typeField = null;
                for (HotSpotResolvedJavaField f : a) {
                    if (f.getName().equals("TYPE")) {
                        typeField = f;
                        break;
                    }
                }
                if (typeField == null) {
                    throw new GraalInternalError("Can't find TYPE field in class");
                }
                location = ConstantLocationNode.create(FINAL_LOCATION, Kind.Object, typeField.offset(), graph);
            }
            ConstantNode klassNode = ConstantNode.forConstant(klass, metaAccess, graph);

            Stamp stamp = StampFactory.exactNonNull(metaAccess.lookupJavaType(Class.class));
            FloatingReadNode freadNode = graph.unique(new FloatingReadNode(klassNode, location, null, stamp));

            if (HotSpotObjectConstant.isCompressed(constant)) {
                return CompressionNode.compress(freadNode, oopEncoding);
            } else {
                return freadNode;
            }
        }
        return null;
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        for (ConstantNode node : getConstantNodes(graph)) {
            Constant constant = node.asConstant();
            ValueNode freadNode = getClassConstantReplacement(graph, context, constant);
            if (freadNode != null) {
                node.replace(graph, freadNode);
            }
        }
    }
}
