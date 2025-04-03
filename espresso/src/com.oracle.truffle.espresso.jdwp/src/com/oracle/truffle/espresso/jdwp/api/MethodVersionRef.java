/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.api;

public interface MethodVersionRef {
    /**
     * Returns the MethodRef corresponding to this method version.
     *
     * @return the MethodRef
     */
    MethodRef getMethod();

    /**
     * Determine if this method is obsolete. A method is obsolete if it has been replaced by a
     * non-equivalent method using the RedefineClasses command. The original and redefined methods
     * are considered equivalent if their bytecodes are the same except for indices into the
     * constant pool and the referenced constants are equal.
     *
     * @return true if the method is obsolete
     */
    boolean isObsolete();
}
