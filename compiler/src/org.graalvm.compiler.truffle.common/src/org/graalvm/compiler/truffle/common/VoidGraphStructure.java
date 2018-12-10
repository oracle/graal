/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.common;

import java.util.Collection;
import java.util.Map;
import org.graalvm.graphio.GraphStructure;

public final class VoidGraphStructure implements GraphStructure<Void, Void, Void, Void> {

    public static final GraphStructure<Void, Void, Void, Void> INSTANCE = new VoidGraphStructure();

    private VoidGraphStructure() {
    }

    @Override
    public Void graph(Void currentGraph, Object obj) {
        return null;
    }

    @Override
    public Iterable<? extends Void> nodes(Void graph) {
        return null;
    }

    @Override
    public int nodesCount(Void graph) {
        return 0;
    }

    @Override
    public int nodeId(Void node) {
        return 0;
    }

    @Override
    public boolean nodeHasPredecessor(Void node) {
        return false;
    }

    @Override
    public void nodeProperties(Void graph, Void node, Map<String, ? super Object> properties) {
    }

    @Override
    public Void node(Object obj) {
        return null;
    }

    @Override
    public Void nodeClass(Object obj) {
        return null;
    }

    @Override
    public Void classForNode(Void node) {
        return null;
    }

    @Override
    public String nameTemplate(Void nodeClass) {
        return null;
    }

    @Override
    public Object nodeClassType(Void nodeClass) {
        return null;
    }

    @Override
    public Void portInputs(Void nodeClass) {
        return null;
    }

    @Override
    public Void portOutputs(Void nodeClass) {
        return null;
    }

    @Override
    public int portSize(Void port) {
        return 0;
    }

    @Override
    public boolean edgeDirect(Void port, int index) {
        return false;
    }

    @Override
    public String edgeName(Void port, int index) {
        return null;
    }

    @Override
    public Object edgeType(Void port, int index) {
        return null;
    }

    @Override
    public Collection<? extends Void> edgeNodes(Void graph, Void node, Void port, int index) {
        return null;
    }

}
