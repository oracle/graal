/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Property change support is in separate file to let the rest of the debugger API run on JDK9 with
 * only java.base module.
 *
 */
final class BreakpointsPropertyChangeEvent extends java.beans.PropertyChangeEvent {
    // using FQN to avoid mx to generate dependency on java.desktop module

    private static final long serialVersionUID = 1L;

    static void firePropertyChange(Debugger source, Breakpoint oldBreakpoint, Breakpoint newBreakpoint) {
        if (source.propSupport.isEmpty()) {
            return;
        }
        final BreakpointsPropertyChangeEvent ev = new BreakpointsPropertyChangeEvent(source, oldBreakpoint, newBreakpoint);
        for (java.beans.PropertyChangeListener l : source.propSupport.toArray(new java.beans.PropertyChangeListener[0])) {
            l.propertyChange(ev);
        }
    }

    @SuppressWarnings("deprecation")
    private BreakpointsPropertyChangeEvent(Object source, Breakpoint oldBreakpoint, Breakpoint newBreakpoint) {
        super(source, Debugger.PROPERTY_BREAKPOINTS, oldBreakpoint, newBreakpoint);
    }

    @Override
    public Object getOldValue() {
        Breakpoint breakpoint = (Breakpoint) super.getOldValue();
        if (breakpoint != null) {
            breakpoint = breakpoint.getROWrapper();
        }
        return breakpoint;
    }

    @Override
    public Object getNewValue() {
        Breakpoint breakpoint = (Breakpoint) super.getNewValue();
        if (breakpoint != null) {
            breakpoint = breakpoint.getROWrapper();
        }
        return breakpoint;
    }

}
