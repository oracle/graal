/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.spi;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;

public abstract class EscapeOp {

    public abstract boolean canAnalyze(Node node);

    public boolean escape(Node node, Node usage) {
        if (usage instanceof IsNullNode) {
            assert ((IsNullNode) usage).object() == node;
            return false;
        } else if (usage instanceof IsTypeNode) {
            assert ((IsTypeNode) usage).objectClass() == node;
            return false;
        } else if (usage instanceof VirtualState) {
            assert usage.inputs().contains(node);
            return true;
        } else if (usage instanceof AccessMonitorNode) {
            assert ((AccessMonitorNode) usage).object() == node;
            return false;
        } else if (usage instanceof LoadFieldNode) {
            assert ((LoadFieldNode) usage).object() == node;
            return false;
        } else if (usage instanceof StoreFieldNode) {
            StoreFieldNode x = (StoreFieldNode) usage;
            // self-references do escape
            return x.value() == node; // TODO (thomaswue) Check if we can add this condition? && x.object() != node;
        } else if (usage instanceof LoadIndexedNode) {
            LoadIndexedNode x = (LoadIndexedNode) usage;
            if (x.index() == node) {
                return true;
            } else {
                assert x.array() == node;
                return !isValidConstantIndex(x);
            }
        } else if (usage instanceof StoreIndexedNode) {
            StoreIndexedNode x = (StoreIndexedNode) usage;
            if (x.index() == node) {
                return true;
            } else {
                assert x.array() == node || x.value() == node;
                // in order to not escape the access needs to have a valid constant index and either a store into node or self-referencing
                return !isValidConstantIndex(x) || x.value() == node && x.array() != node;
            }
        } else if (usage instanceof RegisterFinalizerNode) {
            assert ((RegisterFinalizerNode) usage).object() == node;
            return false;
        } else if (usage instanceof ArrayLengthNode) {
            assert ((ArrayLengthNode) usage).array() == node;
            return false;
        } else if (usage instanceof ValueProxyNode) {
            for (Node vpnUsage : usage.usages().snapshot()) {
                if (escape(usage, vpnUsage)) {
                    return true;
                }
            }
            return false;
        } else {
            return true;
        }
    }

    public static boolean isValidConstantIndex(AccessIndexedNode x) {
        Constant index = x.index().asConstant();
        if (x.array() instanceof NewArrayNode) {
            Constant length = ((NewArrayNode) x.array()).dimension(0).asConstant();
            return index != null && length != null && index.asInt() >= 0 && index.asInt() < length.asInt();
        } else {
            return false;
        }
    }

    public abstract EscapeField[] fields(Node node);

    public void beforeUpdate(Node node, Node usage) {
        // IsNonNullNode and IsTypeNode should have been eliminated by the CanonicalizerPhase, but we can't rely on this
        if (usage instanceof IsNullNode) {
            IsNullNode x = (IsNullNode) usage;
            ((StructuredGraph) x.graph()).replaceFloating(x, ConstantNode.forBoolean(false, node.graph()));
        } else if (usage instanceof IsTypeNode) {
            IsTypeNode x = (IsTypeNode) usage;
            boolean result = ((ValueNode) node).objectStamp().type() == x.type();
            ((StructuredGraph) x.graph()).replaceFloating(x, ConstantNode.forBoolean(result, node.graph()));
        } else if (usage instanceof AccessMonitorNode) {
            ((AccessMonitorNode) usage).eliminate();
        }
    }

    public abstract int updateState(Node node, Node current, Map<Object, Integer> fieldIndex, ValueNode[] fieldState);

}
