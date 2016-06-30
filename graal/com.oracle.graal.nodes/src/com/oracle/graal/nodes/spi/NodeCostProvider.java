/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.graph.Node;
import com.oracle.graal.nodeinfo.NodeCycles;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodeinfo.NodeSize;

/**
 * A provider of node costs that allows different architectures to replace the default values for
 * {@link NodeCycles} and {@link NodeSize} for a {@link Node} IR node.
 */
public interface NodeCostProvider {

    /**
     * Returns a relative numeric value reflecting the estimated number of CPU cycles the execution
     * of the parameter takes in the generated code.
     */
    int sizeNumeric(Node n);

    /**
     * Returns a relative numeric value reflecting the code size that is necessary to represent this
     * instruction in machine language.
     */
    int cyclesNumeric(Node n);

    /**
     * {@linkplain NodeInfo#size()}.
     */
    NodeSize size(Node n);

    /**
     * {@linkplain NodeInfo#size()}.
     */
    NodeCycles cycles(Node n);

}
