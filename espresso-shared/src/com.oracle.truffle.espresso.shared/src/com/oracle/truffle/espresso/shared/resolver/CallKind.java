/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.resolver;

/**
 * Indicates what dispatch behavior should be performed for a call-site.
 * <p>
 * In opposition to {@link CallSiteType}, these are not 1-to-1 mappings from the call-site
 * bytecodes, as, for example, a {@link CallSiteType#Virtual} may resolve to a {@link #DIRECT}
 * dispatch when the symbolic resolution points to a {@code final} method.
 */
public enum CallKind {
    /**
     * A static call: No receiver and no call-time resolution.
     */
    STATIC(Constants.IS_STATIC | Constants.IS_DIRECT_CALL),
    /**
     * A direct call: A receiver but no call-time resolution.
     */
    DIRECT(Constants.IS_DIRECT_CALL),
    /**
     * A virtual call: A receiver and a call-time lookup in the vtable.
     */
    VTABLE_LOOKUP(Constants.HAS_LOOKUP),
    /**
     * An interface call: A receiver and a call-time lookup in the itable.
     */
    ITABLE_LOOKUP(Constants.HAS_LOOKUP);

    private final byte tags;

    CallKind(int tags) {
        this.tags = (byte) tags;
    }

    public boolean isStatic() {
        return (tags & Constants.IS_STATIC) != 0;
    }

    public boolean isDirectCall() {
        return (tags & Constants.IS_DIRECT_CALL) != 0;
    }

    public boolean hasLookup() {
        return (tags & Constants.HAS_LOOKUP) != 0;
    }

    private static final class Constants {
        static final byte IS_STATIC = 0b0001;
        static final byte IS_DIRECT_CALL = 0b0010;
        static final byte HAS_LOOKUP = 0b0100;
    }
}
