/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.positionmanager.impl;

import at.ssw.positionmanager.Link;
import at.ssw.positionmanager.Port;
import java.awt.Point;
import java.util.LinkedList;
import java.util.List;

/**
 * Implements a Link in the open Layouter specification of SSW.
 *
 * @author Stefan Loidl
 */
public class GraphLink implements Link, Comparable<Link>{

    private Port from, to;
    List<Point> controlPoints;
    protected int index;

    /** Creates a new instance of GraphLink */
    public GraphLink(int index ,Port from, Port to) {
        this.index=index;
        this.from=from;
        this.to=to;
        controlPoints=new LinkedList<Point>();
    }

    public Port getFrom() {
        return from;
    }

    public Port getTo() {
        return to;
    }

    public List<Point> getControlPoints() {
        return controlPoints;
    }

    public void setControlPoints(List<Point> list) {
        controlPoints=list;
    }


    public int compareTo(Link o) {
        if(o instanceof GraphLink){
            return index-((GraphLink)o).index;
        }
        return this.hashCode()-o.hashCode();
    }
}
