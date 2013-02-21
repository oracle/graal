/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.code;

import com.oracle.graal.api.meta.*;

/**
 * Represents the target machine for a compiler, including the CPU architecture, the size of
 * pointers and references, alignment of stacks, caches, etc.
 */
public class TargetDescription {

    public final Architecture arch;

    /**
     * The OS page size.
     */
    public final int pageSize;

    /**
     * Specifies if this is a multi-processor system.
     */
    public final boolean isMP;

    /**
     * Specifies if this target supports encoding objects inline in the machine code.
     */
    public final boolean inlineObjects;

    /**
     * The machine word size on this target.
     */
    public final int wordSize;

    /**
     * The kind to be used for representing raw pointers and CPU registers.
     */
    public final Kind wordKind;

    /**
     * The stack alignment requirement of the platform. For example, from Appendix D of <a
     * href="http://www.intel.com/Assets/PDF/manual/248966.pdf">Intel 64 and IA-32 Architectures
     * Optimization Reference Manual</a>:
     * 
     * <pre>
     *     "It is important to ensure that the stack frame is aligned to a
     *      16-byte boundary upon function entry to keep local __m128 data,
     *      parameters, and XMM register spill locations aligned throughout
     *      a function invocation."
     * </pre>
     */
    public final int stackAlignment;

    /**
     * @see "http://docs.oracle.com/cd/E19455-01/806-0477/overview-4/index.html"
     */
    public final int stackBias;

    /**
     * The cache alignment.
     */
    public final int cacheAlignment;

    /**
     * Maximum constant displacement at which a memory access can no longer be an implicit null
     * check.
     */
    public final int implicitNullCheckLimit;

    public TargetDescription(Architecture arch, boolean isMP, int stackAlignment, int stackBias, int implicitNullCheckLimit, int pageSize, int cacheAlignment, boolean inlineObjects) {
        this.arch = arch;
        this.pageSize = pageSize;
        this.isMP = isMP;
        this.wordSize = arch.getWordSize();
        this.wordKind = Kind.fromWordSize(wordSize);
        this.stackAlignment = stackAlignment;
        this.stackBias = stackBias;
        this.implicitNullCheckLimit = implicitNullCheckLimit;
        this.cacheAlignment = cacheAlignment;
        this.inlineObjects = inlineObjects;
    }

    /**
     * Gets the size in bytes of the specified kind for this target.
     * 
     * @param kind the kind for which to get the size
     * @return the size in bytes of {@code kind}
     */
    public int sizeInBytes(Kind kind) {
        // Checkstyle: stop
        switch (kind) {
            case Boolean:
                return 1;
            case Byte:
                return 1;
            case Char:
                return 2;
            case Short:
                return 2;
            case Int:
                return 4;
            case Long:
                return 8;
            case Float:
                return 4;
            case Double:
                return 8;
            case Object:
                return wordSize;
            default:
                return 0;
        }
        // Checkstyle: resume
    }

    /**
     * Aligns the given frame size (without return instruction pointer) to the stack alignment size
     * and return the aligned size (without return instruction pointer).
     * 
     * @param frameSize the initial frame size to be aligned
     * @return the aligned frame size
     */
    public int alignFrameSize(int frameSize) {
        int x = frameSize + arch.getReturnAddressSize() + (stackAlignment - 1);
        return (x / stackAlignment) * stackAlignment - arch.getReturnAddressSize();
    }
}
