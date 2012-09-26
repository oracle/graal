/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.meta.Kind.*;

import com.oracle.graal.api.meta.*;

/**
 * Enumerates the calls that must be provided by the runtime system. The compiler may generate code that calls the
 * runtime services for unresolved and slow cases of some bytecodes.
 */
public class RuntimeCall {

    // TODO Move the singletons to projects where they are actually used. A couple of them
    // are HotSpot-specific.
    public static final RuntimeCall UnwindException = new RuntimeCall("UnwindException", Void, true, Object);
    public static final RuntimeCall Deoptimize = new RuntimeCall("Deoptimize", Void, true);
    public static final RuntimeCall RegisterFinalizer = new RuntimeCall("RegisterFinalizer", Void, true, Object);
    public static final RuntimeCall SetDeoptInfo = new RuntimeCall("SetDeoptInfo", Void, true, Object);
    public static final RuntimeCall CreateNullPointerException = new RuntimeCall("CreateNullPointerException", Object, true);
    public static final RuntimeCall CreateOutOfBoundsException = new RuntimeCall("CreateOutOfBoundsException", Object, true, Int);
    public static final RuntimeCall JavaTimeMillis = new RuntimeCall("JavaTimeMillis", Long, false);
    public static final RuntimeCall JavaTimeNanos = new RuntimeCall("JavaTimeNanos", Long, false);
    public static final RuntimeCall Debug = new RuntimeCall("Debug", Void, true);
    public static final RuntimeCall ArithmeticFrem = new RuntimeCall("ArithmeticFrem", Float, false, Float, Float);
    public static final RuntimeCall ArithmeticDrem = new RuntimeCall("ArithmeticDrem", Double, false, Double, Double);
    public static final RuntimeCall ArithmeticCos = new RuntimeCall("ArithmeticCos", Double, false, Double);
    public static final RuntimeCall ArithmeticTan = new RuntimeCall("ArithmeticTan", Double, false, Double);
    public static final RuntimeCall ArithmeticSin = new RuntimeCall("ArithmeticSin", Double, false, Double);
    public static final RuntimeCall GenericCallback = new RuntimeCall("GenericCallback", Object, true, Object, Object);
    public static final RuntimeCall LogPrimitive = new RuntimeCall("LogPrimitive", Void, false, Int, Long, Boolean);
    public static final RuntimeCall LogObject = new RuntimeCall("LogObject", Void, false, Object, Int);

    private final String name;
    private final Kind resultKind;
    private final Kind[] argumentKinds;
    private final boolean hasSideEffect;

    public RuntimeCall(String name, Kind resultKind, boolean hasSideEffect, Kind... args) {
        this.name = name;
        this.resultKind = resultKind;
        this.argumentKinds = args;
        this.hasSideEffect = hasSideEffect;
    }

    public String getName() {
        return name;
    }

    public Kind getResultKind() {
        return resultKind;
    }

    public Kind[] getArgumentKinds() {
        return argumentKinds;
    }

    public boolean hasSideEffect() {
        return hasSideEffect;
    }

    @Override
    public String toString() {
        return name;
    }
}
