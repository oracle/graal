/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.hierarchicallayout;

import org.graalvm.visualizer.layout.Link;
import org.graalvm.visualizer.layout.Port;
import java.awt.Point;
import java.util.List;

public class ClusterEdge implements Link {

    private final ClusterNode from;
    private final ClusterNode to;
    private List<Point> points;

    public ClusterEdge(ClusterNode from, ClusterNode to) {
        assert from != null;
        assert to != null;
        this.from = from;
        this.to = to;
    }

    @Override
    public Port getTo() {
        return to.getInputSlot();
    }

    @Override
    public Port getFrom() {
        return from.getOutputSlot();
    }

    @Override
    public void setControlPoints(List<Point> p) {
        this.points = p;
    }

    @Override
    public List<Point> getControlPoints() {
        return points;
    }

    @Override
    public boolean isVIP() {
        return false;
    }
}
