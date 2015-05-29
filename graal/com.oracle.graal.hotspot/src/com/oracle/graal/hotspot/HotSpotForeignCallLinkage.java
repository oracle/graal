/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import com.oracle.jvmci.code.ForeignCallLinkage;
import com.oracle.jvmci.code.CallingConvention;
import com.oracle.jvmci.meta.Value;
import com.oracle.jvmci.meta.LocationIdentity;
import com.oracle.jvmci.meta.InvokeTarget;
import com.oracle.jvmci.meta.ForeignCallDescriptor;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.hotspot.stubs.*;

/**
 * The details required to link a HotSpot runtime or stub call.
 */
public interface HotSpotForeignCallLinkage extends ForeignCallLinkage, InvokeTarget {

    /**
     * Constants for specifying whether a foreign call destroys or preserves registers. A foreign
     * call will always destroy {@link HotSpotForeignCallLinkage#getOutgoingCallingConvention() its}
     * {@linkplain ForeignCallLinkage#getTemporaries() temporary} registers.
     */
    public enum RegisterEffect {
        DESTROYS_REGISTERS,
        PRESERVES_REGISTERS
    }

    /**
     * Constants for specifying whether a call is a leaf or not. A leaf function does not lock, GC
     * or throw exceptions. That is, the thread's execution state during the call is never inspected
     * by another thread.
     */
    public enum Transition {
        LEAF_NOFP,
        LEAF,
        LEAF_SP,
        NOT_LEAF;
    }

    /**
     * Sentinel marker for a computed jump address.
     */
    long JUMP_ADDRESS = 0xDEADDEADBEEFBEEFL;

    boolean isReexecutable();

    LocationIdentity[] getKilledLocations();

    CallingConvention getOutgoingCallingConvention();

    CallingConvention getIncomingCallingConvention();

    Value[] getTemporaries();

    long getMaxCallTargetOffset();

    ForeignCallDescriptor getDescriptor();

    void setCompiledStub(Stub stub);

    /**
     * Determines if this is a call to a compiled {@linkplain Stub stub}.
     */
    boolean isCompiledStub();

    void finalizeAddress(Backend backend);

    long getAddress();

    @Override
    boolean destroysRegisters();

    @Override
    boolean canDeoptimize();

    boolean mayContainFP();

    boolean needsJavaFrameAnchor();

    /**
     * Gets the VM symbol associated with the target {@linkplain #getAddress() address} of the call.
     */
    String getSymbol();
}
