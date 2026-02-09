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
package at.ssw.visualizer.graphhelper;

import java.util.LinkedList;

/**
 * Implements a node of the directed graph.
 *
 * @author Stefan Loidl
 */
public class Node {

    /**unique identifier*/
    public String ID;

    /** flag used within algorithms*/
    public boolean visited;

    /** all edges adjacent to the node*/
    public LinkedList<Edge> edges;

    /**
     * List of successors and predecessors of the node
     * these lists are not meant to be changed by the user.
     * they are modified when inserting edges
     */
    public LinkedList<Node> succ,pred;

    /** data field for the use with algorithms*/
    public Object data=null;

    /** Creates a new instance of Node- ID should be unique within the graph*/
    public Node(String ID) {
        this.ID=ID;
        visited=false;
        succ=new LinkedList<Node>();
        pred=new LinkedList<Node>();
        edges=new LinkedList<Edge>();
    }


}
