/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.debugentry.range;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.oracle.objectfile.debugentry.LocalEntry;
import com.oracle.objectfile.debugentry.LocalValueEntry;
import com.oracle.objectfile.debugentry.MethodEntry;

public class CallRange extends Range {

    /**
     * The direct callees whose ranges are wholly contained in this range. Empty if this is a leaf
     * range.
     */
    private List<Range> callees;

    protected CallRange(PrimaryRange primary, MethodEntry methodEntry, Map<LocalEntry, LocalValueEntry> localInfoList, int lo, int hi, int line, CallRange caller, int depth) {
        super(primary, methodEntry, localInfoList, lo, hi, line, caller, depth);
        this.callees = new ArrayList<>();
    }

    @Override
    public void seal() {
        super.seal();
        assert callees instanceof ArrayList<Range> : "CallRange should only be sealed once";
        callees = callees.stream().sorted().toList();
        callees.forEach(Range::seal);

    }

    @Override
    public List<Range> getCallees() {
        assert !(callees instanceof ArrayList<Range>) : "Can only access callee ranges after a CallRange is sealed.";
        return callees;
    }

    @Override
    public Stream<Range> rangeStream() {
        assert !(callees instanceof ArrayList<Range>) : "Can only access callee ranges after a CallRange is sealed.";
        return Stream.concat(super.rangeStream(), callees.stream().flatMap(Range::rangeStream));
    }

    protected void addCallee(Range callee) {
        assert callees instanceof ArrayList<Range> : "Can only add callee ranges before a CallRange is sealed";
        assert this.contains(callee);
        assert callee.getCaller() == this;
        synchronized (callees) {
            callees.add(callee);
        }
    }

    @Override
    public boolean isLeaf() {
        return callees.isEmpty();
    }

    public void removeCallee(Range callee) {
        assert callees instanceof ArrayList<Range> : "Can only remove callee ranges before a CallRange is sealed";
        synchronized (callees) {
            callees.remove(callee);
        }
    }
}
