/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.ast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.SequencedCollection;

/**
 * A list of WASM instructions.
 * <p>
 * Roughly equivalent to a block, but can also be used in other places (e.g. function arguments).
 */
public class Instructions implements Iterable<Instruction> {
    private final List<Instruction> instructionList = new ArrayList<>();

    public static Instructions asInstructions(Instruction... insts) {
        Instructions instructions = new Instructions();
        instructions.addAll(Arrays.asList(insts));
        return instructions;
    }

    /**
     * A mutable list of the instructions held in this class.
     */
    public List<Instruction> get() {
        return instructionList;
    }

    public void add(Instruction instruction) {
        instructionList.add(instruction);
    }

    public void addAll(SequencedCollection<? extends Instruction> instructions) {
        this.instructionList.addAll(instructions);
    }

    public Instruction[] toArray() {
        return instructionList.toArray(new Instruction[0]);
    }

    @Override
    public Iterator<Instruction> iterator() {
        return instructionList.iterator();
    }
}
