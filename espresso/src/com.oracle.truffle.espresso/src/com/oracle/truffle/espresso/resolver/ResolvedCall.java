/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.resolver;

import java.util.Objects;

import com.oracle.truffle.espresso.impl.Method;

public final class ResolvedCall {
    private final CallKind callKind;
    private final Method resolved;

    public ResolvedCall(CallKind callKind, Method resolved) {
        this.callKind = Objects.requireNonNull(callKind);
        this.resolved = Objects.requireNonNull(resolved);
    }

    public Object call(Object... args) {
        return switch (callKind) {
            case STATIC ->
                resolved.invokeDirectStatic(args);
            case DIRECT ->
                resolved.invokeDirect(args);
            case VTABLE_LOOKUP ->
                resolved.invokeDirectVirtual(args);
            case ITABLE_LOOKUP ->
                resolved.invokeDirectInterface(args);
        };
    }

    public Method getResolvedMethod() {
        return resolved;
    }

    public CallKind getCallKind() {
        return callKind;
    }
}
