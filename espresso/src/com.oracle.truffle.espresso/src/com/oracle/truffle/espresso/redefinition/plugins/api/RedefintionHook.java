/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.redefinition.plugins.api;

import com.oracle.truffle.espresso.jdwp.api.MethodHook;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.MethodVariable;

public final class RedefintionHook implements MethodHook {

    private final MethodExitHook onExitHook;
    private final MethodEntryHook onEntryHook;
    private final Kind kind;
    private boolean hasFired = false;

    public RedefintionHook(MethodEntryHook onEntryHook, Kind kind) {
        this.onExitHook = null;
        this.onEntryHook = onEntryHook;
        this.kind = kind;
    }

    public RedefintionHook(MethodExitHook onExitHook, Kind kind) {
        this.onExitHook = onExitHook;
        this.onEntryHook = null;
        this.kind = kind;
    }

    @Override
    public Kind getKind() {
        return kind;
    }

    @Override
    public boolean onMethodEnter(MethodRef method, MethodVariable[] variables) {
        if (onEntryHook != null) {
            onEntryHook.onMethodEnter(method, variables);
            hasFired = true;
        }
        return false;
    }

    @Override
    public boolean onMethodExit(MethodRef method, Object returnValue) {
        if (onExitHook != null) {
            onExitHook.onMethodExit(method, returnValue);
            hasFired = true;
        }
        return false;
    }

    @Override
    public boolean hasFired() {
        return hasFired;
    }
}
