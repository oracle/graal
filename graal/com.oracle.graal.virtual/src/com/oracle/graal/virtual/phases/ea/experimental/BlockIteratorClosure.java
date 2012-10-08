/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.virtual.phases.ea.experimental;

import java.util.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.virtual.phases.ea.*;

public abstract class BlockIteratorClosure<T extends MergeableBlockState<T>> {

    public static class LoopInfo<T extends MergeableBlockState<T>> {

        public final List<T> endStates = new ArrayList<>();
        public final List<T> exitStates = new ArrayList<>();
    }

    protected abstract void processBlock(Block block, T currentState);

    protected abstract T merge(MergeNode merge, List<T> states);

    protected abstract T afterSplit(FixedNode node, T oldState);

    protected abstract List<T> processLoop(Loop loop, T initialState);
}
