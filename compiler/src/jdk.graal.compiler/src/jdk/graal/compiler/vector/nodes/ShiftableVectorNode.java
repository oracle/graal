/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes;

import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * Vector node that can be shifted.
 */
public interface ShiftableVectorNode extends VectorNode {

    /**
     * Returns a new vector node with the same content but a different start index.
     *
     * @param index start index of the shifted node.
     * @param guard guarding node that ensures the validity of index and length (may be null)
     * @param insertBefore the position where any fixed nodes created by shifting should be inserted
     *            (may be null to allow shifting to decide where to insert fixed nodes)
     * @return a new vector node with the same content, but different start index
     */
    VectorNode shift(ValueNode index, GuardingNode guard, FixedNode insertBefore, ConstantReflectionProvider constantReflection);
}
