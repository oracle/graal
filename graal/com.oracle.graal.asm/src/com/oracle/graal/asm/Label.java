/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.asm;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class represents a label within assembly code.
 */
public final class Label {

    /**
     * Counter used for sequential numbering.
     */
    private static AtomicInteger globalId = new AtomicInteger(0);

    /**
     * Unique identifier of this label.
     */
    private int id;

    /**
     * Position of this label in the code buffer.
     */
    private int position = -1;

    /**
     * References to instructions that jump to this unresolved label. These instructions need to be
     * patched when the label is bound using the {@link #patchInstructions(AbstractAssembler)}
     * method.
     */
    private ArrayList<Integer> patchPositions = new ArrayList<>(4);

    public Label() {
        id = globalId.getAndIncrement();
    }

    /**
     * Returns the position of this label in the code buffer.
     * 
     * @return the position
     */
    public int position() {
        assert position >= 0 : "Unbound label is being referenced";
        return position;
    }

    /**
     * Returns the unique identifier of this Label.
     * 
     * @return the unique identifier
     */
    public int id() {
        return id;
    }

    /**
     * Binds the label to the specified position.
     * 
     * @param pos the position
     */
    protected void bind(int pos) {
        this.position = pos;
        assert isBound();
    }

    public boolean isBound() {
        return position >= 0;
    }

    public void addPatchAt(int branchLocation) {
        assert !isBound() : "Label " + name() + " is already bound: position=" + position + ", branchLocation=" + branchLocation;
        patchPositions.add(branchLocation);
    }

    protected void patchInstructions(AbstractAssembler masm) {
        assert isBound() : "Label should be bound";
        int target = position;
        for (int i = 0; i < patchPositions.size(); ++i) {
            int pos = patchPositions.get(i);
            masm.patchJumpTarget(pos, target);
        }
    }

    @Override
    public String toString() {
        return isBound() ? String.valueOf(position()) : "?";
    }

    /**
     * Returns a unique name of this Label. The name is based on the unique identifier.
     * 
     * @return the unique name
     */
    public String name() {
        return "L" + id();
    }
}
