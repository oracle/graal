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
package org.graalvm.visualizer.graph;

import jdk.graal.compiler.graphio.parsing.model.InputBlock;
import org.graalvm.visualizer.layout.Cluster;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashSet;
import java.util.Set;

public final class Block implements Cluster, DiagramItem {
    private final Diagram diagram;
    private final InputBlock inputBlock;
    private Rectangle bounds;
    private boolean visible;

    public Block(InputBlock inputBlock, Diagram diagram) {
        this.inputBlock = inputBlock;
        this.diagram = diagram;
        visible = inputBlock.getNodes().isEmpty();
    }

    @Override
    public Cluster getOuter() {
        return null;
    }

    public InputBlock getInputBlock() {
        return inputBlock;
    }

    @Override
    public Set<? extends Cluster> getSuccessors() {
        Set<Block> succs = new HashSet<>();
        for (InputBlock b : inputBlock.getSuccessors()) {
            succs.add(diagram.getBlock(b));
        }
        return succs;
    }

    @Override
    public void setBounds(Rectangle r) {
        this.bounds = r;
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    @Override
    public int compareTo(Cluster o) {
        return toString().compareTo(o.toString());
    }

    @Override
    public String toString() {
        return inputBlock.getName();
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean b) {
        this.visible = b;
    }

    @Override
    public Point getPosition() {
        return bounds == null ? new Point(0, 0) : bounds.getLocation();
    }

    @Override
    public void setPosition(Point pt) {
        if (bounds == null) {
            bounds = new Rectangle(pt);
        } else {
            bounds.setLocation(pt);
        }
    }
}
