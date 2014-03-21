/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.nodes.extended.*;

/**
 * Marks nodes which may be lowered in combination with a memory operation.
 */
public interface MemoryArithmeticLIRLowerable {

    /**
     * Attempt to generate a memory form of a node operation. On platforms that support it this will
     * be called when the merging is safe.
     * 
     * @param gen
     * @param access the memory input which can potentially merge into this operation.
     * @return null if it's not possible to emit a memory form of this operation. A non-null value
     *         will be set as the operand of this node.
     */
    boolean generate(MemoryArithmeticLIRLowerer gen, Access access);
}
