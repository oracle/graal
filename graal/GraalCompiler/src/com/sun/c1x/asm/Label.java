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
package com.sun.c1x.asm;

import com.sun.c1x.util.*;

/**
 * This class represents a label within assembly code.
 *
 * @author Marcelo Cintra
 */
public final class Label {

    private int position = -1;

    /**
     * References to instructions that jump to this unresolved label.
     * These instructions need to be patched when the label is bound
     * using the {@link #patchInstructions(AbstractAssembler)} method.
     */
    private IntList patchPositions = new IntList(4);

    /**
     * Returns the position of this label in the code buffer.
     * @return the position
     */
    public int position() {
        assert position >= 0 : "Unbound label is being referenced";
        return position;
    }

    public Label() {
    }

    public Label(int position) {
        bind(position);
    }

    /**
     * Binds the label to the specified position.
     * @param pos the position
     */
    public void bind(int pos) {
        this.position = pos;
        assert isBound();
    }

    public boolean isBound() {
        return position >= 0;
    }

    public void addPatchAt(int branchLocation) {
        assert !isBound() : "Label is already bound";
        patchPositions.add(branchLocation);
    }

    public void patchInstructions(AbstractAssembler masm) {
        assert isBound() : "Label should be bound";
        int target = position;
        for (int i = 0; i < patchPositions.size(); ++i) {
            int pos = patchPositions.get(i);
            masm.patchJumpTarget(pos, target);
        }
    }

    @Override
    public String toString() {
        return "label";
    }
}
