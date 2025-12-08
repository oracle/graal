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

import java.util.SortedSet;
import java.util.TreeSet;
import at.ssw.visualizer.model.interval.Range;
import at.ssw.visualizer.model.interval.UsePosition;
import at.ssw.visualizer.modelimpl.interval.ChildIntervalImpl;

/**
 *
 * @author Christian Wimmer
 */
public class IntervalHelper implements Comparable<IntervalHelper> {
    protected String regNum;
    protected String type;
    protected String operand = ""; // avoid null values if not defined
    protected String splitParent;
    protected String spillState;
    protected String registerHint;

    protected SortedSet<Range> ranges = new TreeSet<Range>();
    protected SortedSet<UsePosition> usePositions = new TreeSet<UsePosition>();

    protected ChildIntervalImpl childInterval = new ChildIntervalImpl();
    protected SortedSet<ChildIntervalImpl> splitChildren = new TreeSet<ChildIntervalImpl>();

    public int compareTo(IntervalHelper other) {
        return ranges.first().getFrom() - other.ranges.first().getFrom();
    }
}
