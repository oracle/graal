/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.nodes.spi;

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.base.*;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.java.*;


public abstract class EscapeOp implements Op {

    public abstract boolean canAnalyze(Node node);

    public boolean escape(Node node, Node usage) {
        if (usage instanceof IsNonNullNode) {
            IsNonNullNode x = (IsNonNullNode) usage;
            assert x.object() == node;
            return false;
        } else if (usage instanceof IsTypeNode) {
            IsTypeNode x = (IsTypeNode) usage;
            assert x.object() == node;
            return false;
        } else if (usage instanceof FrameState) {
            FrameState x = (FrameState) usage;
            assert x.inputContains(node);
            return true;
        } else if (usage instanceof AccessMonitorNode) {
            AccessMonitorNode x = (AccessMonitorNode) usage;
            assert x.object() == node;
            return false;
        } else {
            return true;
        }
    }

    public abstract EscapeField[] fields(Node node);

    public void beforeUpdate(Node node, Node usage) {
        if (usage instanceof IsNonNullNode) {
            IsNonNullNode x = (IsNonNullNode) usage;
            // TODO (ls) not sure about this...
            x.replaceAndDelete(ConstantNode.forBoolean(true, node.graph()));
        } else if (usage instanceof IsTypeNode) {
            IsTypeNode x = (IsTypeNode) usage;
            assert x.type() == ((ValueNode) node).exactType();
            // TODO (ls) not sure about this...
            x.replaceAndDelete(ConstantNode.forBoolean(true, node.graph()));
        } else if (usage instanceof AccessMonitorNode) {
            AccessMonitorNode x = (AccessMonitorNode) usage;
            x.replaceAndDelete(x.next());
        }
    }

    public abstract int updateState(Node node, Node current, Map<Object, Integer> fieldIndex, ValueNode[] fieldState);

}
