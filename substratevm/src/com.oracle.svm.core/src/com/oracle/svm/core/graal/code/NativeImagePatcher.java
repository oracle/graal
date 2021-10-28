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
package com.oracle.svm.core.graal.code;

/**
 * Patcher used during native image runtime.
 */
public interface NativeImagePatcher {
    /**
     * Patch directly in the code buffer with an offset relative to the start of this instruction.
     *
     * @param methodStartAddress address of method start within runtime installed code
     * @param relative pc-relative offset
     * @param code machine code generated for this method
     */
    void patchCode(long methodStartAddress, int relative, byte[] code);

    /**
     * The position from the beginning of the method where the patch is applied. This offset is used
     * in the reference map.
     */
    int getOffset();

    /**
     * The length of the value to patch in bytes, e.g., the size of an operand.
     */
    int getLength();
}
