/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.vm;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * A cookie to be inserted into a {@link com.oracle.truffle.api.frame.Frame}. Espresso currently
 * uses a single {@link com.oracle.truffle.api.frame.FrameDescriptor#findOrAddAuxiliarySlot(Object)
 * auxiliary slot} for inserting cookies of different use cases. Before adding another use case to
 * this cookie class, make sure either:
 * <li>It does not conflict with other use cases (ie: no two use cases can inject a cookie in the
 * same frame)
 * <li>extend this class functionality to support multi-cookies.
 *
 * @see VM#JVM_DoPrivileged(StaticObject, StaticObject, StaticObject, boolean, Meta,
 *      com.oracle.truffle.espresso.substitutions.SubstitutionProfiler)
 * @see StackWalk.FrameWalker#doStackWalk(StaticObject)
 */
public final class FrameCookie {
    enum CookieKind {
        Privileged,
        StackWalk,
    }

    private final CookieKind kind;
    private final long data;

    public long getData() {
        return data;
    }

    private FrameCookie(CookieKind kind, long data) {
        this.kind = kind;
        this.data = data;
    }

    public static FrameCookie createPrivilegedCookie(long data) {
        return new FrameCookie(CookieKind.Privileged, data);
    }

    public static FrameCookie createStackWalkCookie(long data) {
        return new FrameCookie(CookieKind.StackWalk, data);
    }

    public boolean isPrivileged() {
        return kind == CookieKind.Privileged;
    }

    public boolean isStackWalk() {
        return kind == CookieKind.StackWalk;
    }
}
