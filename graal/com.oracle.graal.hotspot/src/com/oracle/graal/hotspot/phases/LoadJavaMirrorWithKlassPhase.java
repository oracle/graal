/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.nodes.ConstantNode.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.hotspot.jvmci.*;
import com.oracle.graal.hotspot.jvmci.HotSpotVMConfig.CompressEncoding;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.memory.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.jvmci.common.*;

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

    private ValueNode getClassConstantReplacement(StructuredGraph graph, PhaseContext context, JavaConstant constant) {
        if (constant instanceof HotSpotObjectConstant) {
            ConstantReflectionProvider constantReflection = context.getConstantReflection();
            ResolvedJavaType type = constantReflection.asJavaType(constant);
            if (type != null) {
                MetaAccessProvider metaAccess = context.getMetaAccess();
                Stamp stamp = StampFactory.exactNonNull(metaAccess.lookupJavaType(Class.class));

                if (type instanceof HotSpotResolvedObjectType) {
                    ConstantNode klass = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), ((HotSpotResolvedObjectType) type).klass(), metaAccess, graph);
                    LocationNode location = graph.unique(new ConstantLocationNode(CLASS_MIRROR_LOCATION, classMirrorOffset));
                    ValueNode read = graph.unique(new FloatingReadNode(klass, location, null, stamp));

                    if (((HotSpotObjectConstant) constant).isCompressed()) {
                        return CompressionNode.compress(read, oopEncoding);
                    } else {
                        return read;
                    }
                } else {
                    /*
                     * Primitive classes are more difficult since they don't have a corresponding
                     * Klass* so get them from Class.TYPE for the java box type.
                     */
                    HotSpotResolvedPrimitiveType primitive = (HotSpotResolvedPrimitiveType) type;
                    ResolvedJavaType boxingClass = metaAccess.lookupJavaType(primitive.getKind().toBoxedJavaClass());
                    ConstantNode clazz = ConstantNode.forConstant(boxingClass.getJavaClass(), metaAccess, graph);
                    HotSpotResolvedJavaField[] a = (HotSpotResolvedJavaField[]) boxingClass.getStaticFields();
                    HotSpotResolvedJavaField typeField = null;
                    for (HotSpotResolvedJavaField f : a) {
                        if (f.getName().equals("TYPE")) {
                            typeField = f;
                            break;
                        }
                    }
                    if (typeField == null) {
                        throw new JVMCIError("Can't find TYPE field in class");
                    }

                    LocationNode location = graph.unique(new ConstantLocationNode(FINAL_LOCATION, typeField.offset()));
                    if (oopEncoding != null) {
                        stamp = NarrowOopStamp.compressed((AbstractObjectStamp) stamp, oopEncoding);
                    }
                    ValueNode read = graph.unique(new FloatingReadNode(clazz, location, null, stamp));

                    if (oopEncoding == null || ((HotSpotObjectConstant) constant).isCompressed()) {
                        return read;
                    } else {
                        return CompressionNode.uncompress(read, oopEncoding);
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        for (ConstantNode node : getConstantNodes(graph)) {
            JavaConstant constant = node.asJavaConstant();
            ValueNode freadNode = getClassConstantReplacement(graph, context, constant);
            if (freadNode != null) {
                node.replace(graph, freadNode);
            }
        }
    }
}
