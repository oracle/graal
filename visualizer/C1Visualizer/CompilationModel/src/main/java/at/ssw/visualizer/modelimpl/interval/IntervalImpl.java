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
package at.ssw.visualizer.modelimpl.interval;

import at.ssw.visualizer.model.interval.ChildInterval;
import at.ssw.visualizer.model.interval.Interval;
import at.ssw.visualizer.model.interval.IntervalList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Christian Wimmer
 */
public class IntervalImpl implements Interval {
    private IntervalList parent;
    private ChildInterval[] children;

    public IntervalImpl(ChildIntervalImpl[] children) {
        this.children = children;
        for (ChildIntervalImpl child : children) {
            child.setParent(this);
        }
    }

    public IntervalList getParent() {
        return parent;
    }

    protected void setParent(IntervalListImpl parent) {
        this.parent = parent;
    }

    public List<ChildInterval> getChildren() {
        return Collections.unmodifiableList(Arrays.asList(children));
    }

    public String getRegNum() {
        return children[0].getRegNum();
    }

    public int getFrom() {
        return children[0].getFrom();
    }

    public int getTo() {
        return children[children.length - 1].getTo();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < children.length; i++) {
            if (i > 0) {
                result.append("\n  ");
            }
            result.append(children[i]);
        }
        return result.toString();
    }
}
