/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;

public class LoopBegin extends Merge {
    public LoopBegin(Graph graph) {
        super(graph);
    }

    public LoopEnd loopEnd() {
        for (Node usage : usages()) {
            if (usage instanceof LoopEnd) {
                LoopEnd end = (LoopEnd) usage;
                if (end.loopBegin() == this) {
                    return end;
                }
            }
        }
        return null;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitLoopBegin(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("loopBegin");
    }

    @Override
    public String shortName() {
        return "LoopBegin";
    }

    @Override
    public Node copy(Graph into) {
        return new LoopBegin(into);
    }

    @Override
    public int phiPredecessorCount() {
        return 2;
    }

    @Override
    public int phiPredecessorIndex(Node pred) {
        if (pred == forwardEdge()) {
            return 0;
        } else if (pred == this.loopEnd()) {
            return 1;
        }
        throw Util.shouldNotReachHere("unknown pred : " + pred + "(sp=" + forwardEdge() + ", le=" + this.loopEnd() + ")");
    }

    @Override
    public Node phiPredecessorAt(int index) {
        if (index == 0) {
            return forwardEdge();
        } else if (index == 1) {
            return loopEnd();
        }
        throw Util.shouldNotReachHere();
    }

    public Collection<LoopCounter> counters() {
        return Util.filter(this.usages(), LoopCounter.class);
    }

    @Override
    public List<Node> phiPredecessors() {
        return Arrays.asList(new Node[]{this.forwardEdge(), this.loopEnd()});
    }

    public EndNode forwardEdge() {
        return this.endAt(0);
    }

    @Override
    public boolean verify() {
        assertTrue(loopEnd() != null);
        assertTrue(forwardEdge() != null);
        return true;
    }

    @Override
    public String toString() {
        return "LoopBegin: " + super.toString();
    }

    @Override
    public Node singlePredecessor() {
        assert endCount() == 1;
        return endAt(0).singlePredecessor();
    }

    @Override
    public Iterable< ? extends Node> dataUsages() {
        final Iterator< ? extends Node> dataUsages = super.dataUsages().iterator();
        return new Iterable<Node>() {
            @Override
            public Iterator<Node> iterator() {
                return new StateSplit.FilteringIterator(dataUsages, LoopBegin.class);
            }
        };
    }
}
