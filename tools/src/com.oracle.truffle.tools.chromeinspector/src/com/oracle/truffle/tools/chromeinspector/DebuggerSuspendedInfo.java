/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector;

import java.util.concurrent.atomic.AtomicReference;

import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.SuspendedEvent;

import com.oracle.truffle.tools.chromeinspector.domains.DebuggerDomain;
import com.oracle.truffle.tools.chromeinspector.domains.RuntimeDomain;
import com.oracle.truffle.tools.chromeinspector.types.CallArgument;
import com.oracle.truffle.tools.chromeinspector.types.CallFrame;
import com.oracle.truffle.tools.chromeinspector.types.RemoteObject;

import org.graalvm.collections.Pair;

final class DebuggerSuspendedInfo {

    private final InspectorDebugger debugger;
    private final SuspendedEvent se;
    private volatile CallFrame[] callFrames;
    /**
     * Holder of the last evaluated value, if any. It's expected to be used for non
     * {@link RemoteObject#isReplicable() replicable} values, while assuming that
     * {@link DebuggerDomain#setVariableValue(int, String, CallArgument, String)} is called after
     * {@link RuntimeDomain#evaluate(String, String, boolean, boolean, int, boolean, boolean, boolean)}
     */
    final AtomicReference<Pair<DebugValue, Object>> lastEvaluatedValue = new AtomicReference<>();

    DebuggerSuspendedInfo(InspectorDebugger debugger, SuspendedEvent se, CallFrame[] callFrames) {
        this.debugger = debugger;
        this.se = se;
        this.callFrames = callFrames;
    }

    public SuspendedEvent getSuspendedEvent() {
        return se;
    }

    public CallFrame[] getCallFrames() {
        return callFrames;
    }

    void refreshFrames() {
        this.callFrames = debugger.refreshCallFrames(se.getStackFrames(), se.getSuspendAnchor(), callFrames);
    }
}
