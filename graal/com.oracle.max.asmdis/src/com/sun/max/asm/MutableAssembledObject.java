/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm;

/**
 * An assembled object whose position and size may change.
 * Examples include span-dependent jump instructions to labels and padding bytes for memory alignment.
 */
public abstract class MutableAssembledObject extends AssembledObject {

    protected abstract void assemble() throws AssemblyException;

    private final Assembler assembler;
    private int variablePosition;
    protected int variableSize;

    protected MutableAssembledObject(Assembler assembler, int startPosition, int endPosition) {
        super(startPosition, endPosition);
        this.assembler = assembler;
        this.variablePosition = startPosition;
        this.variableSize = endPosition - startPosition;
    }

    protected Assembler assembler() {
        return assembler;
    }

    public final int initialStartPosition() {
        return super.startPosition();
    }

    public final int initialEndPosition() {
        return super.endPosition();
    }

    public final int initialSize() {
        return super.size();
    }

    public void adjust(int delta) {
        variablePosition += delta;
    }

    @Override
    public int startPosition() {
        return variablePosition;
    }

    @Override
    public int endPosition() {
        return variablePosition + variableSize;
    }

    @Override
    public int size() {
        return variableSize;
    }
}
