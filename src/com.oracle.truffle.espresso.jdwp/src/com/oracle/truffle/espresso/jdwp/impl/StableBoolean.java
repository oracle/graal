/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;

/**
 * Helper class that uses an assumption to switch between two "stable" states efficiently. Copied
 * from DebuggerSession with modifications to the set method to make it thread safe (but slower on
 * the slow path).
 */
public final class StableBoolean {

    @CompilerDirectives.CompilationFinal private volatile Assumption unchanged;
    @CompilerDirectives.CompilationFinal private volatile boolean value;

    public StableBoolean(boolean initialValue) {
        this.value = initialValue;
        this.unchanged = Truffle.getRuntime().createAssumption("Unchanged boolean");
    }

    @SuppressFBWarnings(value = "UG_SYNC_SET_UNSYNC_GET", justification = "The get method returns a volatile field.")
    public boolean get() {
        if (unchanged.isValid()) {
            return value;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return value;
        }
    }

    /**
     * This method needs to be behind a boundary due to the fact that compiled code will constant
     * fold the value, hence the first check might yield a wrong result.
     * 
     * @param value
     */
    @CompilerDirectives.TruffleBoundary
    public synchronized void set(boolean value) {
        if (this.value != value) {
            this.value = value;
            Assumption old = this.unchanged;
            unchanged = Truffle.getRuntime().createAssumption("Unchanged boolean");
            old.invalidate();
        }
    }
}
