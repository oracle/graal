/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.impl;

public final class SteppingInfo {

    private final int requestId;
    private final byte suspendPolicy;
    private final boolean isPopFrames;
    private final boolean isForceEarlyReturn;
    private final DebuggerCommand.Kind stepKind;
    private boolean submitted;

    SteppingInfo(int requestId, byte suspendPolicy, boolean isPopFrames, boolean isForceEarlyReturn, DebuggerCommand.Kind stepKind) {
        this.requestId = requestId;
        this.suspendPolicy = suspendPolicy;
        this.isPopFrames = isPopFrames;
        this.isForceEarlyReturn = isForceEarlyReturn;
        this.stepKind = stepKind;
    }

    public int getRequestId() {
        return requestId;
    }

    public byte getSuspendPolicy() {
        return suspendPolicy;
    }

    public boolean isPopFrames() {
        return isPopFrames;
    }

    public boolean isForceEarlyReturn() {
        return isForceEarlyReturn;
    }

    public DebuggerCommand.Kind getStepKind() {
        return stepKind;
    }

    public void submit() {
        submitted = true;
    }

    public boolean isSubmitted() {
        return submitted;
    }
}
