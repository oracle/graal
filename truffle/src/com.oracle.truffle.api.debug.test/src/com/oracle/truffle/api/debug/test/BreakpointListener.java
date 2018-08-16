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
package com.oracle.truffle.api.debug.test;

import java.util.function.Consumer;
import org.junit.Assert;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;

final class BreakpointListener implements Consumer<Breakpoint> {

    static BreakpointListener register(boolean[] notified, Debugger debugger, Breakpoint globalBreakpoint) {
        BreakpointListener newBPListener = new BreakpointListener(notified, debugger, globalBreakpoint);
        debugger.addBreakpointAddedListener(newBPListener);
        Consumer<Breakpoint> removedListener = (breakpoint) -> Assert.fail("No breakpoint is excpected to be removed. Breakpoint = " + breakpoint);
        newBPListener.removedListener = removedListener;
        debugger.addBreakpointRemovedListener(removedListener);
        return newBPListener;
    }

    private final boolean[] notified;
    private final Debugger debugger;
    private final Breakpoint globalBreakpoint;
    private Consumer<Breakpoint> removedListener;

    private BreakpointListener(boolean[] notified, Debugger debugger, Breakpoint globalBreakpoint) {
        this.notified = notified;
        this.debugger = debugger;
        this.globalBreakpoint = globalBreakpoint;
    }

    @Override
    public void accept(Breakpoint breakpoint) {
        notified[0] = true;
        Assert.assertNotEquals(globalBreakpoint, breakpoint);
        try {
            breakpoint.dispose();
            Assert.fail("Public dispose must not be possible for global breakpoints.");
        } catch (IllegalStateException ex) {
            // O.K.
        }
    }

    void unregister() {
        debugger.removeBreakpointAddedListener(this);
        debugger.removeBreakpointRemovedListener(removedListener);
    }

}
