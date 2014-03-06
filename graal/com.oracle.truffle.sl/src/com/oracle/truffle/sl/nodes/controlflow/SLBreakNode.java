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
package com.oracle.truffle.sl.nodes.controlflow;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.*;

/**
 * Implementation of the SL break statement. We need to unwind an unknown number of interpreter
 * frames that are between this {@link SLBreakNode} and the {@link SLWhileNode} of the loop we are
 * breaking out. This is done by throwing an {@link SLBreakException exception} that is caught by
 * the {@link SLWhileNode#executeVoid loop node}.
 */
@NodeInfo(shortName = "break")
public final class SLBreakNode extends SLStatementNode {

    @Override
    public void executeVoid(VirtualFrame frame) {
        throw SLBreakException.SINGLETON;
    }
}
