/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import com.oracle.truffle.api.instrumentation.ProvidedTags;

/**
 * Set of debugger-specific tags. Language should {@link ProvidedTags provide} an implementation of
 * these tags in order to support specific debugging features.
 *
 * @since 0.13
 */
public final class DebuggerTags {

    private DebuggerTags() {
        // No instances
    }

    /**
     * Marks program locations where debugger should always halt like being on a breakpoint.
     * <p>
     * Use case descriptions:
     * <ul>
     * <li><b>Debugger:</b> The debugger submits a default breakpoint on this tag, program locations
     * where execution should always halt (e.g. a <code>debugger</code> statement), should be marked
     * with this tag.</li>
     * </ul>
     *
     * @since 0.13
     */
    public final class AlwaysHalt {
        private AlwaysHalt() {
            /* No instances */
        }
    }

}
