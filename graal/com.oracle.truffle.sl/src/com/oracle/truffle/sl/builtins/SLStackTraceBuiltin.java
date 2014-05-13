/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.builtins;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.nodes.*;

/**
 * Returns a string representation of the current stack. This includes the {@link CallTarget}s and
 * the contents of the {@link Frame}. Note that this is implemented as a slow path by passing
 * {@code true} to {@link FrameInstance#getFrame(FrameAccess, boolean)}.
 */
@NodeInfo(shortName = "stacktrace")
public abstract class SLStackTraceBuiltin extends SLBuiltinNode {

    @Specialization
    public String trace() {
        return createStackTrace();
    }

    @SlowPath
    private static String createStackTrace() {
        StringBuilder str = new StringBuilder();
        Iterable<FrameInstance> frames = Truffle.getRuntime().getStackTrace();

        if (frames != null) {
            for (FrameInstance frame : frames) {
                dumpFrame(str, frame.getCallTarget(), frame.getFrame(FrameAccess.READ_ONLY, true), frame.isVirtualFrame());
            }
        }
        return str.toString();
    }

    private static void dumpFrame(StringBuilder str, CallTarget callTarget, Frame frame, boolean isVirtual) {
        if (str.length() > 0) {
            str.append("\n");
        }
        str.append("Frame: ").append(callTarget).append(isVirtual ? " (virtual)" : "");
        FrameDescriptor frameDescriptor = frame.getFrameDescriptor();
        for (FrameSlot s : frameDescriptor.getSlots()) {
            str.append(", ").append(s.getIdentifier()).append("=").append(frame.getValue(s));
        }
    }
}
