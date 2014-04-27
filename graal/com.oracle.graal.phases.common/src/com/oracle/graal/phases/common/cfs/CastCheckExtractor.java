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
package com.oracle.graal.phases.common.cfs;

import com.oracle.graal.api.meta.ResolvedJavaType;
import com.oracle.graal.nodes.LogicNode;
import com.oracle.graal.nodes.ShortCircuitOrNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.java.InstanceOfNode;

/**
 * @see #extract(com.oracle.graal.nodes.LogicNode)
 */
class CastCheckExtractor {

    public final ResolvedJavaType type;
    public final ValueNode subject;

    CastCheckExtractor(ResolvedJavaType type, ValueNode subject) {
        this.type = type;
        this.subject = subject;
    }

    static CastCheckExtractor extractCastCheckInfo(LogicNode x, LogicNode y) {
        if (x instanceof IsNullNode) {
            IsNullNode isNull = (IsNullNode) x;
            ValueNode subject = isNull.object();
            if (isInstanceOfCheckOn(y, subject)) {
                InstanceOfNode iOf = (InstanceOfNode) y;
                return new CastCheckExtractor(iOf.type(), subject);
            }
        }
        return null;
    }

    /**
     * This method detects whether the argument realizes the CheckCast pattern. If so, distills and
     * returns the essentials of such check, otherwise returns null.
     */
    static CastCheckExtractor extract(LogicNode cond) {
        if (!(cond instanceof ShortCircuitOrNode)) {
            return null;
        }
        ShortCircuitOrNode orNode = (ShortCircuitOrNode) cond;
        if (orNode.isXNegated() || orNode.isYNegated()) {
            return null;
        }
        CastCheckExtractor result = extractCastCheckInfo(orNode.getX(), orNode.getY());
        if (result != null) {
            return result;
        }
        result = extractCastCheckInfo(orNode.getY(), orNode.getX());
        return result;
    }

    /**
     * Porcelain method.
     */
    public static boolean isInstanceOfCheckOn(LogicNode cond, ValueNode subject) {
        if (!(cond instanceof InstanceOfNode)) {
            return false;
        }
        InstanceOfNode io = (InstanceOfNode) cond;
        return io.object() == subject;
    }
}
