/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.parser;

import java.util.Collection;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import at.ssw.visualizer.model.interval.ChildInterval;
import at.ssw.visualizer.model.interval.Range;
import at.ssw.visualizer.model.interval.UsePosition;
import at.ssw.visualizer.modelimpl.interval.ChildIntervalImpl;
import at.ssw.visualizer.modelimpl.interval.IntervalImpl;
import at.ssw.visualizer.modelimpl.interval.IntervalListImpl;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 *
 * @author Christian Wimmer
 */
public class IntervalListHelper {
    protected String shortName;
    protected String name;
    protected LinkedHashMap<String, IntervalHelper> helpers = new LinkedHashMap<String, IntervalHelper>();

    public void add(IntervalHelper helper) {
        helpers.put(helper.regNum, helper);
    }


    public IntervalListImpl resolve(Parser parser, ControlFlowGraph controlFlowGraph) {
        for (IntervalHelper helper : helpers.values()) {
            ChildInterval registerHint = null;
            if (helpers.containsKey(helper.registerHint)) {
                registerHint = helpers.get(helper.registerHint).childInterval;
            }
            Range[] ranges = helper.ranges.toArray(new Range[helper.ranges.size()]);
            UsePosition[] usePositions = helper.usePositions.toArray(new UsePosition[helper.usePositions.size()]);

            helper.childInterval.setValues(helper.regNum, helper.type, helper.operand, helper.spillState, registerHint, ranges, usePositions);


            IntervalHelper parent = helpers.get(helper.splitParent);
            if (parent != null) {
                parent.splitChildren.add(helper.childInterval);
            } else {
                parser.SemErr("Unknown split parent: " + helper.splitParent);
            }
        }

        Collection<IntervalImpl> intervals = new ArrayList<IntervalImpl>();
        for (IntervalHelper helper : helpers.values()) {
            if (helper.splitChildren.size() > 0) {
                ChildIntervalImpl[] children = helper.splitChildren.toArray(new ChildIntervalImpl[helper.splitChildren.size()]);
                intervals.add(new IntervalImpl(children));
            }
        }

        return new IntervalListImpl(shortName, name, intervals.toArray(new IntervalImpl[intervals.size()]), controlFlowGraph);
    }
}
