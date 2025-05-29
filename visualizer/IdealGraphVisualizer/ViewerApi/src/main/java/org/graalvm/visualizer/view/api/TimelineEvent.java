/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.view.api;

import org.graalvm.visualizer.util.RangeSliderModel;

import java.beans.PropertyChangeEvent;
import java.util.EventObject;

/**
 * @author sdedic
 */
public final class TimelineEvent extends EventObject {
    private final RangeSliderModel originRange;
    private final String partitionType;
    private final int oldPos1;
    private final int oldPos2;
    private final PropertyChangeEvent propEvent;

    public TimelineEvent(TimelineModel source) {
        this(source, null, null, null);
    }

    public TimelineEvent(TimelineModel source, RangeSliderModel sl, String pt, PropertyChangeEvent event) {
        super(source);
        assert sl == null || (pt != null && source.getPartitionRange(pt) == sl);

        originRange = sl;
        partitionType = pt;
        oldPos1 = -1;
        oldPos2 = -1;
        propEvent = event;
    }

    public TimelineEvent(TimelineModel source, RangeSliderModel sl, String type, int p1, int p2) {
        super(source);
        this.partitionType = type;
        this.originRange = sl;
        this.oldPos1 = p1;
        this.oldPos2 = p2;
        this.propEvent = null;
    }

    public PropertyChangeEvent getPropertyEvent() {
        return propEvent;
    }

    public RangeSliderModel getSlider() {
        return originRange;
    }

    @Override
    public TimelineModel getSource() {
        return (TimelineModel) source;
    }

    public RangeSliderModel getOriginRange() {
        return originRange;
    }

    public String getPartitionType() {
        return partitionType;
    }

    public int getFirstGraph() {
        return oldPos1;
    }

    public int getSecondGraph() {
        return oldPos2;
    }
}
