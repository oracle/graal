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
package com.oracle.graal.nodes;

import java.util.*;

import com.oracle.graal.nodes.type.*;

/**
 * Base class of all nodes that are fixed within the control flow graph and have an immediate successor.
 * This class also provides an implementation of {@link StateSplit} without being a {@link StateSplit} itself.
 */
public abstract class FixedWithNextNode extends FixedNode {

    @Input(notDataflow = true) private FrameState stateAfter;

    public FrameState stateAfter() {
        return stateAfter;
    }

    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        assert this instanceof StateSplit : getClass() + " does not implement " + StateSplit.class;
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    // Subclasses that implement StateSplit but do not represent side-effecting instructions must override this.
    public boolean hasSideEffect() {
        return true;
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> debugProperties = super.getDebugProperties();
        if (stateAfter() != null) {
            debugProperties.put("stateAfter", stateAfter().toString(Verbosity.Debugger));
        }
        return debugProperties;
    }

    public FixedNode next() {
        assert scheduledNext() == null || scheduledNext() instanceof FixedNode : "next() cannot be used while the graph is scheduled";
        return (FixedNode) scheduledNext();
    }

    public void setNext(FixedNode x) {
        setScheduledNext(x);
    }

    public FixedWithNextNode(Stamp stamp) {
        super(stamp);
    }
}
