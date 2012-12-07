/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.stubs.*;

/**
 * The details required to link a HotSpot runtime or stub call.
 */
public class HotSpotRuntimeCall implements RuntimeCall {

    /**
     * The descriptor of the stub. This is for informational purposes only.
     */
    public final Descriptor descriptor;

    /**
     * The entry point address of the stub.
     */
    private long address;

    /**
     * Non-null (eventually) iff this is a call to a snippet-based {@linkplain Stub stub}.
     */
    private Stub stub;

    /**
     * Where the stub gets its arguments and where it places its result.
     */
    public final CallingConvention cc;

    private final CompilerToVM vm;

    public HotSpotRuntimeCall(Descriptor descriptor, long address, CallingConvention cc, CompilerToVM vm) {
        this.address = address;
        this.descriptor = descriptor;
        this.cc = cc;
        this.vm = vm;
    }

    @Override
    public String toString() {
        return (stub == null ? descriptor.toString() : MetaUtil.format("%h.%n", stub.getMethod())) + "@0x" + Long.toHexString(address) + ":" + cc;
    }

    public CallingConvention getCallingConvention() {
        return cc;
    }

    public long getMaxCallTargetOffset() {
        return vm.getMaxCallTargetOffset(address);
    }

    public Descriptor getDescriptor() {
        return descriptor;
    }

    public void setStub(Stub stub) {
        assert address == 0L : "cannot stub for linkage that already has an address: " + this;
        this.stub = stub;
    }

    public void setAddress(long address) {
        assert this.address == 0L : "cannot re-initialize address of " + this;
        this.address = address;
    }

    public long getAddress() {
        assert address != 0L : "address not yet initialized for " + this;
        return address;
    }
}
