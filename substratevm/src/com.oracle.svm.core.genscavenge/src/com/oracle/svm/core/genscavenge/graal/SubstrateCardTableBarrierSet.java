/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.graal;

import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.meta.SharedType;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.gc.CardTableBarrierSet;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.spi.ArrayLengthProvider;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Static fields in SVM are represented as two arrays in the native image heap: one for Object
 * fields and one for all primitive fields (see {@link StaticFieldsSupport}). Therefore, we must
 * emit array write barriers for static fields.
 */
public class SubstrateCardTableBarrierSet extends CardTableBarrierSet {
    public SubstrateCardTableBarrierSet(ResolvedJavaType objectArrayType) {
        super(objectArrayType);
    }

    @Override
    public BarrierType fieldWriteBarrierType(ResolvedJavaField field, JavaKind storageKind) {
        if (field.isStatic() && storageKind == JavaKind.Object) {
            return arrayWriteBarrierType(storageKind);
        }
        return super.fieldWriteBarrierType(field, storageKind);
    }

    /**
     * On SVM, precise write barriers are only needed for object arrays that are located in
     * unaligned chunks.
     * <p>
     * This method is conservative, that is it can return {@code true} even if in practice a precise
     * barrier is not needed.
     */
    @Override
    protected boolean barrierPrecise(FixedAccessNode node, ValueNode base, CoreProviders context) {
        if (!super.barrierPrecise(node, base, context)) {
            return false;
        }

        ResolvedJavaType baseType = StampTool.typeOrNull(base);
        if (baseType == null) {
            return true;
        }

        /*
         * We know that instances (in contrast to arrays) are always in aligned chunks, except for
         * StoredContinuation objects, but these are immutable and do not need barriers.
         *
         * Note that arrays can be assigned to values that have the type java.lang.Object, so that
         * case is excluded. Arrays can also implement some interfaces, like Serializable. For
         * simplicity, we exclude all interface types.
         */
        if (!baseType.isArray()) {
            return baseType.isInterface() || baseType.isJavaLangObject();
        }

        /*
         * Arrays smaller than HeapParameters.getLargeArrayThreshold() are allocated in the aligned
         * chunks.
         */
        ValueNode length = GraphUtil.arrayLength(base, ArrayLengthProvider.FindLengthMode.SEARCH_ONLY, context.getConstantReflection());
        if (length == null) {
            return true;
        }

        IntegerStamp lengthStamp = (IntegerStamp) length.stamp(NodeView.DEFAULT);
        GraalError.guarantee(lengthStamp.getBits() == Integer.SIZE, "unexpected length %s", lengthStamp);
        int lengthBound = NumUtil.safeToInt(lengthStamp.upperBound());
        SharedType componentType = (SharedType) baseType.getComponentType();
        UnsignedWord sizeBound = LayoutEncoding.getArrayAllocationSize(componentType.getHub().getLayoutEncoding(), lengthBound);
        return !GenScavengeAllocationSupport.arrayAllocatedInAlignedChunk(sizeBound);
    }
}
