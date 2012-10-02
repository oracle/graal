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
     *
     * @param name name of the stub
     * @param address address of the stub
     * @param tempRegs temporary registers used (and killed) by the stub (null if none)
     * @param ret where the stub returns its result
     * @param args where arguments are passed to the stub
     */
    protected void addStub(String name, long address, Register[] tempRegs, Value ret, Value... args) {
        Value[] temps = tempRegs == null || tempRegs.length == 0 ? Value.NONE : new Value[tempRegs.length];
        for (int i = 0; i < temps.length; i++) {
            temps[i] = tempRegs[i].asValue();
        }
        HotSpotStub stub = new HotSpotStub(name, address, new CallingConvention(temps, 0, ret, args));
        stubsMap.put(name, stub);
    }
}
