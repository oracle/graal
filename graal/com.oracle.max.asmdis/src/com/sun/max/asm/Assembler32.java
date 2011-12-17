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
 * Assemblers for 32-bit address spaces.
 */
public interface Assembler32 {

    /**
     * Gets the base address for relative labels.
     * 
     * @return the address at which the assembled object code will reside
     */
    int startAddress();

    void setStartAddress(int addresss);

    /**
     * Assigns a fixed, absolute 32-bit address to a given label.
     * 
     * @param label    the label to update
     * @param address  an absolute 32-bit address
     */
    void fixLabel(Label label, int address);

    /**
     * Gets the address to which a label has been bound.
     * 
     * @param label  the label to decode
     * @return the address to which {@code label} has been bound
     * @throws AssemblyException if {@code label} has not been bound to an address
     */
    int address(Label label) throws AssemblyException;
}
