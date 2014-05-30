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
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

import edu.umd.cs.findbugs.annotations.*;

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
                if (isObject(node) && !isNullReference(node) && !isInternedString(node) && !isDirectMethodHandle(node) && !isBoundMethodHandle(node)) {
                    throw new VerificationError("illegal object constant: " + node);
                }
            }
        }
        return true;
    }

    private static boolean isObject(ConstantNode node) {
        return node.getKind() == Kind.Object;
    }

    private static boolean isNullReference(ConstantNode node) {
        return isObject(node) && node.asConstant().isNull();
    }

    private static boolean isDirectMethodHandle(ConstantNode node) {
        if (!isObject(node)) {
            return false;
        }
        return "Ljava/lang/invoke/DirectMethodHandle;".equals(StampTool.typeOrNull(node).getName());
    }

    private static boolean isBoundMethodHandle(ConstantNode node) {
        if (!isObject(node)) {
            return false;
        }
        return StampTool.typeOrNull(node).getName().startsWith("Ljava/lang/invoke/BoundMethodHandle");
    }

    @SuppressFBWarnings(value = "ES_COMPARING_STRINGS_WITH_EQ", justification = "reference equality is what we want")
    private static boolean isInternedString(ConstantNode node) {
        if (!isObject(node)) {
            return false;
        }

        Object o = HotSpotObjectConstant.asObject(node.asConstant());
        if (!(o instanceof String)) {
            return false;
        }

        String s = (String) o;
        return s == s.intern();
    }
}
