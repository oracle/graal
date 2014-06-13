/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
public abstract class TargetDescription {

    public final Architecture arch;

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
     * Maximum constant displacement at which a memory access can no longer be an implicit null
     * check.
     */
    public final int implicitNullCheckLimit;

    public TargetDescription(Architecture arch, boolean isMP, int stackAlignment, int implicitNullCheckLimit, boolean inlineObjects) {
        this.arch = arch;
        this.isMP = isMP;
        this.wordSize = arch.getWordSize();
        this.wordKind = Kind.fromWordSize(wordSize);
        this.stackAlignment = stackAlignment;
        this.implicitNullCheckLimit = implicitNullCheckLimit;
        this.inlineObjects = inlineObjects;
    }

    public int getSizeInBytes(PlatformKind kind) {
        return arch.getSizeInBytes(kind);
    }

    public LIRKind getLIRKind(Kind javaKind) {
        switch (javaKind) {
            case Boolean:
            case Byte:
            case Short:
            case Char:
            case Int:
            case Long:
            case Float:
            case Double:
                return LIRKind.value(javaKind);
            case Object:
                return LIRKind.reference(javaKind);
            default:
                return LIRKind.Illegal;
        }
    }

    public abstract ReferenceMap createReferenceMap(boolean hasRegisters, int stackSlotCount);
}
