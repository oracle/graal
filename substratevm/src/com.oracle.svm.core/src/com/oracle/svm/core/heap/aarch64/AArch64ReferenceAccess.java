/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Arm Limited. All rights reserved.
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
package com.oracle.svm.core.heap.aarch64;

import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.heap.ReferenceAccess;

public interface AArch64ReferenceAccess extends ReferenceAccess {

    /**
     * Read the absolute address of the object referenced by the split object reference starting at
     * address {@code p} and return it as a word which is not tracked by garbage collection.
     */
    Word readSplitObjectAsUntrackedPointer(Pointer p, boolean compressed, int numPieces);

    /**
     * Write the location of object {@code value} to the split object reference starting at address
     * {@code p}.
     */
    void writeSplitObjectAt(Pointer p, Object value, boolean compressed, int numPieces);

}
