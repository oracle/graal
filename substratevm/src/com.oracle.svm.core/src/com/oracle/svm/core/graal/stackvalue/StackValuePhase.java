/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.stackvalue;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.Phase;

import com.oracle.svm.core.graal.stackvalue.StackValueNode.StackSlotHolder;

public class StackValuePhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        Map<Object, StackSlotHolder> slots = new HashMap<>();

        for (StackValueNode node : graph.getNodes(StackValueNode.TYPE)) {
            StackSlotHolder slotHolder = slots.get(node.slotIdentity);
            if (slotHolder == null) {
                slotHolder = new StackSlotHolder(node.size);
                slots.put(node.slotIdentity, slotHolder);
            }

            assert node.stackSlotHolder == null;
            node.stackSlotHolder = slotHolder;
        }
    }
}
