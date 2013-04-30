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
import com.oracle.graal.api.code.RuntimeCallTarget.Descriptor;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.hotspot.meta.*;

/**
 * HotSpot specific backend.
 */
public abstract class HotSpotBackend extends Backend {

    /**
     * Descriptor for SharedRuntime::deopt_blob()->uncommon_trap().
     */
    public static final Descriptor UNCOMMON_TRAP = new Descriptor("deoptimize", true, void.class);

    /**
     * Descriptor for GraalRuntime::handle_exception_nofpu_id.
     */
    public static final Descriptor EXCEPTION_HANDLER = new Descriptor("exceptionHandler", true, void.class);

    /**
     * Descriptor for SharedRuntime::deopt_blob()->unpack().
     */
    public static final Descriptor DEOPT_HANDLER = new Descriptor("deoptHandler", true, void.class);

    /**
     * Descriptor for SharedRuntime::get_ic_miss_stub().
     */
    public static final Descriptor IC_MISS_HANDLER = new Descriptor("icMissHandler", true, void.class);

    public HotSpotBackend(HotSpotRuntime runtime, TargetDescription target) {
        super(runtime, target);
    }

    @Override
    public HotSpotRuntime runtime() {
        return (HotSpotRuntime) super.runtime();
    }
}
