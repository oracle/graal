/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import com.oracle.truffle.api.source.*;

/**
 * Language-agnostic access to AST-based debugging support.
 * <p>
 * <strong>WARNING:</strong> this interface is under development and will change substantially.
 */
public interface DebugManager {

    /**
     * Sets a breakpoint at a line-based location.
     */
    LineBreakpoint setBreakpoint(SourceLineLocation lineLocation);

    /**
     * Sets a breakpoint at a line-based location with a boolean expression in the guest language to
     * serve as a break condition.
     */
    LineBreakpoint setConditionalBreakpoint(SourceLineLocation lineLocation, String condition);

    /**
     * Gets a list of current breakpoints.
     */
    LineBreakpoint[] getBreakpoints();

    /**
     * Removes a breakpoint at a line-based location.
     */
    void removeBreakpoint(SourceLineLocation lineLocation);

    /**
     * Description of a line-based breakpoint.
     */
    interface LineBreakpoint {

        SourceLineLocation getSourceLineLocation();

        String getDebugStatus();
    }

}
