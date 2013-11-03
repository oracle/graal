/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.nodes.ConstantNode.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

/**
 * Checks for illegal object constants in a graph processed for AOT compilation. The only legal
 * object constants are {@linkplain String#intern() interned} strings as they will be installed in
 * the Class Data Sharing (CDS) space.
 * 
 * @see LoadJavaMirrorWithKlassPhase
 */
public class AheadOfTimeVerificationPhase extends VerifyPhase<PhaseContext> {

    @Override
    protected boolean verify(StructuredGraph graph, PhaseContext context) {
        for (ConstantNode node : getConstantNodes(graph)) {
            if (node.recordsUsages() || !node.gatherUsages(graph).isEmpty()) {
                assert !isObject(node) || isNullReference(node) || isInternedString(node) : "illegal object constant: " + node;
            }
        }
        return true;
    }

    private static boolean isObject(ConstantNode node) {
        return node.kind() == Kind.Object;
    }

    private static boolean isNullReference(ConstantNode node) {
        return isObject(node) && node.asConstant().asObject() == null;
    }

    private static boolean isInternedString(ConstantNode node) {
        if (!isObject(node)) {
            return false;
        }

        Object o = node.asConstant().asObject();
        if (!(o instanceof String)) {
            return false;
        }

        String s = (String) o;
        return s == s.intern();
    }
}
