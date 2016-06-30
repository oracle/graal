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

public enum NodeSize {
    SIZE_UNKOWN(0),
    SIZE_UNSET(0),
    SIZE_0(0),
    SIZE_1(1),
    SIZE_2(2),
    SIZE_3(3),
    SIZE_4(4),
    SIZE_6(6),
    SIZE_8(8),
    SIZE_10(10),
    SIZE_15(15),
    SIZE_20(20),
    SIZE_30(30),
    SIZE_40(40),
    SIZE_50(50),
    SIZE_80(80),
    SIZE_100(100),
    SIZE_200(200),
    SIZE_INFINITY(1000);

    final int relativeSize;

    NodeSize(int relativeSize) {
        this.relativeSize = relativeSize;
    }

    public static int relativeSize(NodeSizeSupplier supplier) {
        return supplier.getNodeSize().relativeSize;
    }

    public interface NodeSizeSupplier {
        NodeSize getNodeSize();
    }

    public static final int IGNORE_SIZE_CHECK_FACTOR = 0xFFFF;
}
