/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.framemap;

import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ValueKind;

/**
 * A {@link FrameMapBuilder} is used to collect all information necessary to
 * {@linkplain #buildFrameMap create} a {@link FrameMap}.
 */
public abstract class FrameMapBuilder {

    /**
     * Reserves a spill slot in the frame of the method being compiled. The returned slot is aligned
     * on its natural alignment, i.e., an 8-byte spill slot is aligned at an 8-byte boundary, unless
     * overridden by a subclass.
     *
     * @param kind The kind of the spill slot to be reserved.
     * @return A spill slot denoting the reserved memory area.
     */
    public abstract VirtualStackSlot allocateSpillSlot(ValueKind<?> kind);

    /**
     * Reserves a contiguous and aligned range of memory in the frame of the method being compiled.
     *
     * @param sizeInBytes the number of bytes to reserve. Must be > 0.
     * @param alignmentInBytes the required alignment of the memory. Must be > 0, a power of 2, and
     *            not exceed the {@link TargetDescription#stackAlignment OS stack frame alignment}.
     * @return the first reserved stack slot (i.e., at the lowest address)
     */
    public abstract VirtualStackSlot allocateStackMemory(int sizeInBytes, int alignmentInBytes);

    public abstract RegisterConfig getRegisterConfig();

    public abstract CodeCacheProvider getCodeCache();

    /**
     * Informs the frame map that the compiled code calls a particular method, which may need stack
     * space for outgoing arguments.
     *
     * @param cc The calling convention for the called method.
     */
    public abstract void callsMethod(CallingConvention cc);

    /**
     * Creates a {@linkplain FrameMap} based on the information collected by this
     * {@linkplain FrameMapBuilder}.
     */
    public abstract FrameMap buildFrameMap(LIRGenerationResult result);
}
