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
 * Assemblers for 64-bit address spaces.
 */
public interface Assembler64 {

    /**
     * Gets the base address for relative labels.
     * 
     * @return the address at which the assembled object code will reside
     */
    long startAddress();

    void setStartAddress(long address);

    /**
     * Assigns a fixed, absolute 64-bit address to a given label.
     * 
     * @param label    the label to update
     * @param address  an absolute 64-bit address
     */
    void fixLabel(Label label, long address);

    /**
     * Gets the address to which a label has been bound.
     * 
     * @param label  the label to decode
     * @return the address to which {@code label} has been bound
     * @throws AssemblyException if {@code label} has not been bound to an address
     */
    long address(Label label) throws AssemblyException;

}
