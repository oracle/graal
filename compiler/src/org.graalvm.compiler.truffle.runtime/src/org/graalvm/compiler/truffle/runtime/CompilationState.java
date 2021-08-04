/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import com.oracle.truffle.api.CompilerDirectives;

public enum CompilationState {

    INTERPRETED,

    FIRST_TIER_ROOT,
    LAST_TIER_ROOT,

    FIRST_TIER_INLINED,
    LAST_TIER_INLINED;

    boolean isCompilationRoot() {
        return this == FIRST_TIER_ROOT || this == LAST_TIER_ROOT;
    }

    boolean isCompiled() {
        return this != INTERPRETED;
    }

    int getTier() {
        switch (this) {
            case INTERPRETED:
                return 0;
            case FIRST_TIER_INLINED:
            case FIRST_TIER_ROOT:
                return 1;
            case LAST_TIER_INLINED:
            case LAST_TIER_ROOT:
                return 2;
            default:
                throw CompilerDirectives.shouldNotReachHere("invalid state");
        }
    }
}
