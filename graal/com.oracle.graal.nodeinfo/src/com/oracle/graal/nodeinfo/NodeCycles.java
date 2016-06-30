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
package com.oracle.graal.nodeinfo;

public enum NodeCycles {
    CYCLES_UNKOWN(0),
    CYCLES_UNSET(0),
    CYCLES_0(0),
    CYCLES_1(1),
    CYCLES_2(2),
    CYCLES_3(3),
    CYCLES_4(4),
    CYCLES_5(5),
    CYCLES_6(6),
    CYCLES_8(8),
    CYCLES_10(10),
    CYCLES_15(15),
    CYCLES_20(20),
    CYCLES_30(30),
    CYCLES_40(40),
    CYCLES_50(50),
    CYCLES_80(80),
    CYCLES_100(100),
    CYCLES_200(200),
    CYCLES_500(500),
    CYCLES_INFINITY(1000);

    final int relativeCycles;

    NodeCycles(int relativeCycles) {
        this.relativeCycles = relativeCycles;
    }

    public static int relativeCycles(NodeCyclesSupplier supplier) {
        return supplier.getNodeCycles().relativeCycles;
    }

    public interface NodeCyclesSupplier {
        NodeCycles getNodeCycles();
    }

    public static final int IGNORE_CYCLES_CHECK_FACTOR = 0xFFFF;

}
