/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.vm.continuation.UnwindContinuationException;

import java.util.Set;

/**
 * All methods in this class that can be overridden in subclasses must be abstract. If a generic
 * implementation should be provided it should be in {@link EspressoInstrumentableRootNodeImpl}.
 */
@GenerateWrapper(yieldExceptions = UnwindContinuationException.class)
public abstract class EspressoInstrumentableRootNode extends EspressoInstrumentableNode {

    abstract Object execute(VirtualFrame frame);

    abstract Method.MethodVersion getMethodVersion();

    // the wrapper must delegate this

    @Override
    public abstract SourceSection getSourceSection();

    abstract boolean canSplit();

    abstract EspressoInstrumentableRootNode split();

    // this shouldn't be delegated so that wrappers are not considered trivial
    boolean isTrivial() {
        return false;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probeNode) {
        return new EspressoInstrumentableRootNodeWrapper(this, probeNode);
    }

    @Override
    public abstract String toString();

    public void prepareForInstrumentation(@SuppressWarnings("unused") Set<Class<?>> tags) {
        // do nothing by default, only method nodes with bytecode needs to take action
    }
}
