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

package com.oracle.truffle.espresso.analysis.graph;

import com.oracle.truffle.espresso.runtime.EspressoException;

public final class EspressoBlockWithHandlers extends EspressoBlock implements ExceptionEdgeBlock<EspressoException> {

    // Contains the handlers index in method.getExceptionHandlers.
    private final int[] handlers;

    public EspressoBlockWithHandlers(EspressoExecutionGraph graph, int id, int start, int end, int last, int[] successors, int[] handlers, int[] predecessors) {
        super(graph, id, start, end, last, successors, predecessors);
        this.handlers = handlers;
    }

    @Override
    public int[] getExceptionHandlers() {
        return handlers;
    }
}
