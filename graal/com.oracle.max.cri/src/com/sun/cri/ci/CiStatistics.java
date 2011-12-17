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
package com.sun.cri.ci;

/**
 * Contains statistics gathered during the compilation of a method and reported back
 * from the compiler as the result of compilation.
 */
public class CiStatistics {

    /**
     * The total number of bytes of bytecode parsed during this compilation, including any inlined methods.
     */
    public int bytecodeCount;

    /**
     * The number of internal graph nodes created during this compilation.
     */
    public int nodeCount;

    /**
     * The number of basic blocks created during this compilation.
     */
    public int blockCount;

    /**
     * The number of loops in the compiled method.
     */
    public int loopCount;

    /**
     * The number of methods inlined.
     */
    public int inlineCount;

    /**
     * The number of methods folded (i.e. evaluated).
     */
    public int foldCount;

    /**
     * The number of intrinsics inlined in this compilation.
     */
    public int intrinsicCount;

}
