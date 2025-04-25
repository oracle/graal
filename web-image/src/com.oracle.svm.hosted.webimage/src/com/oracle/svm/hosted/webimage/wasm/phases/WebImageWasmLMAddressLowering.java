/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.phases;

import com.oracle.svm.hosted.webimage.wasm.nodes.WasmAddressNode;
import com.oracle.svm.webimage.wasm.types.WasmLMUtil;

import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.phases.common.AddressLoweringByNodePhase;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Lowers {@code base + offset} addresses to {@link WasmAddressNode}s with a 32-bit offset.
 * <p>
 * If possible, the offset will be a constant.
 * <p>
 * TODO GR-43486 also optimize if the base or offset are an addition with a constant. Similar to
 * what AMD64AddressLowering does.
 */
public class WebImageWasmLMAddressLowering extends AddressLoweringByNodePhase.AddressLowering {
    @Override
    public AddressNode lower(ValueNode base, ValueNode offset) {
        StructuredGraph graph = base.graph();

        ValueNode newBase;
        ValueNode newOffset;
        boolean isConstant;

        if (isCompatibleConstantOffset(offset)) {
            newBase = base;
            newOffset = offset;
            isConstant = true;
        } else if (isCompatibleConstantOffset(base)) {
            newBase = offset;
            newOffset = base;
            isConstant = true;
        } else {
            // There are no constant inputs.
            newBase = base;
            newOffset = offset;
            isConstant = false;
        }

        /*
         * Make sure numeric offsets are narrowed to 32-bit. If it already is, it will return the
         * original node.
         *
         * The only non-numeric constant offsets are object references which are already 32-bit.
         */
        if (newOffset.getStackKind().isNumericInteger()) {
            newOffset = graph.addOrUnique(NarrowNode.create(newOffset, WasmLMUtil.POINTER_TYPE.getBitCount(), NodeView.DEFAULT));
        }

        return graph.unique(new WasmAddressNode(newBase, newOffset, isConstant));
    }

    /**
     * Determines whether the given node is suitable to be used as an immediate offset value for
     * memory instructions.
     * <p>
     * The node needs to satisfy either of the following:
     * <ul>
     * <li>Be an object constant.</li>
     * <li>Be an integer constant in {@code [0, Integer.MAX_VALUE]}.</li>
     * </ul>
     */
    private static boolean isCompatibleConstantOffset(ValueNode node) {
        JavaConstant constant = node.asJavaConstant();

        if (constant == null) {
            return false;
        }

        JavaKind kind = constant.getJavaKind();
        if (kind.isNumericInteger()) {
            long value = constant.asLong();
            return value >= 0 && value <= Integer.MAX_VALUE;
        } else {
            return kind == JavaKind.Object;
        }
    }
}
