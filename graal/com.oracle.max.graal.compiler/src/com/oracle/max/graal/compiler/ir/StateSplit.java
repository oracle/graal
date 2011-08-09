/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.ir;

import java.util.*;

import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * The {@code StateSplit} class is the abstract base class of all instructions
 * that store an immutable copy of the frame state.
 */
public abstract class StateSplit extends FixedNodeWithNext {

    @Input private FrameState stateAfter;

    public FrameState stateAfter() {
        return stateAfter;
    }

    public void setStateAfter(FrameState x) {
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    /**
     * Creates a new state split with the specified value type.
     * @param kind the type of the value that this instruction produces
     * @param graph
     */
    public StateSplit(CiKind kind, Graph graph) {
        super(kind, graph);
    }

    public boolean needsStateAfter() {
        return true;
    }

    @Override
    public void delete() {
        FrameState stateAfter = stateAfter();
        super.delete();
        if (stateAfter != null) {
            if (stateAfter.usages().isEmpty()) {
                stateAfter.delete();
            }
        }
    }

    @Override
    public Iterable< ? extends Node> dataInputs() {
        final Iterator< ? extends Node> dataInputs = super.dataInputs().iterator();
        return new Iterable<Node>() {
            @Override
            public Iterator<Node> iterator() {
                return new FilteringIterator(dataInputs, FrameState.class);
            }
        };
    }

    public static final class FilteringIterator implements Iterator<Node> {

        private final Iterator< ? extends Node> input;
        private Node next;
        private Class< ? > clazz;

        public FilteringIterator(Iterator< ? extends Node> input, Class<?> clazz) {
            this.input = input;
            this.clazz = clazz;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Node next() {
            forward();
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Node res = next;
            next = null;
            return res;
        }

        @Override
        public boolean hasNext() {
            forward();
            return next != null;
        }

        private void forward() {
            while (next == null && input.hasNext()) {
                next = input.next();
                if (clazz.isInstance(next)) {
                    next = null;
                }
            }
        }
    }
}
