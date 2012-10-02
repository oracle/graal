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
package com.oracle.graal.hotspot.target;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.hotspot.*;

/**
 * HotSpot specific backend.
 */
public abstract class HotSpotBackend extends Backend {

    private final Map<String, HotSpotStub> stubsMap = new HashMap<>();

    public HotSpotBackend(CodeCacheProvider runtime, TargetDescription target) {
        super(runtime, target);
    }

    /**
     * Gets the linkage information for a global stub.
     */
    public HotSpotStub getStub(String name) {
        HotSpotStub stub = stubsMap.get(name);
        assert stub != null : "no stub named " + name;
        return stub;
    }

    /**
     * Registers the details for linking a global stub.
     */
    protected HotSpotStub addStub(String name, long address, CallingConvention cc) {
        HotSpotStub stub = new HotSpotStub(name, address, cc);
        stubsMap.put(name, stub);
        return stub;
    }

    protected static Value reg(@SuppressWarnings("unused") String name, Register reg, Kind kind) {
        return reg.asValue(kind);
    }

    protected static Register[] temps(Register... regs) {
        return regs;
    }

    protected static CallingConvention cc(Value ret, Value... args) {
        return new CallingConvention(0, ret, args);
    }

    protected static CallingConvention cc(Register[] tempRegs, Value ret, Value... args) {
        Value[] temps = new Value[tempRegs.length];
        for (int i = 0; i < temps.length; i++) {
            temps[i] = tempRegs[i].asValue();
        }
        return new CallingConvention(temps, 0, ret, args);
    }

}
