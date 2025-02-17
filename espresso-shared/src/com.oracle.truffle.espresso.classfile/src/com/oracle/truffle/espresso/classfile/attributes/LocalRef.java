/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.attributes;

/**
 * Interface representing a local variable, e.g. a method parameter or a variable declared within a
 * method.
 */
public interface LocalRef {

    /**
     * Returned the first code index within the declaring method, for which the local variable is
     * visible.
     * 
     * @return the start bci
     */
    int getStartBCI();

    /**
     * Returns a String representation of the name of the local.
     * 
     * @return the variable name
     */
    String getNameAsString();

    /**
     * Returns the String representation of the type of the local according to the field descriptor
     * grammar. See https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2
     * 
     * @return the variable type descriptor name
     */
    String getTypeAsString();

    /**
     * Returned the last code index within the declaring method, for which the local variable is
     * visible.
     * 
     * @return the end bci
     */
    int getEndBCI();

    /**
     * Returns the index of the frameslot used to store the local.
     * 
     * @return the slot index
     */
    int getSlot();
}
