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
package org.graalvm.visualizer.util;

import jdk.graal.compiler.graphio.parsing.model.ChangedEvent;
import jdk.graal.compiler.graphio.parsing.model.ChangedEventProvider;

import java.awt.Color;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a range of states - positions, each with a certain color. A gap (space) can reside
 * between each of the two positions, and at the beginning (before 1st position). Positions are named.
 * Because list of positions and definition of gaps can be set independently, gaps are identified
 * by position names; translation to positional indexes is performed internally.
 * <p/>
 * Gaps correspond to graphs managed by other peer RangeSliderModel, or to graphs not
 * shown at all.
 */
public class RangeSliderModel implements ChangedEventProvider<RangeSliderModel> {
    /**
     * Property name for the "positions" property. It is fired if one of
     * {@link #getFirstPosition()} and/or {@link #getSecondPosition()} may have
     * changed.
     */
    public static final String PROP_POSITIONS = "positions"; // NOI18N
    public static final String PROP_GAPS = "gaps"; // NOI18N

    protected static final Color DEFAULT_COLOR = Color.BLACK;

    private final ChangedEvent<RangeSliderModel> changedEvent;
    private final ChangedEvent<RangeSliderModel> colorChangedEvent;

    // Warning: Update setData method if fields are added
    // @GuardedBy(this)
    private List<String> positions;

    // @GuardedBy(this)
    private int firstPosition;

    // @GuardedBy(this)
    private int secondPosition;

    // @GuardedBy(this)
    private List<Color> colors;
    private List<Color> hatches;

    /**
     * A tag value indicating first position in the position sequence.
     */
    @SuppressWarnings("RedundantStringConstructorCall")
    public static final String FIRST_POSITION = new String("first-position");

    private Map<String, Integer> slots = null;

    // @GuardedBy(this)
    private List<String> spacedPositions;

    // @GuardedBy(this)
    private int[] indices;

    // @GuardedBy(this)
    private int slotCount = -1;


    protected final PropertyChangeSupport propSupport = new PropertyChangeSupport(this);

    private RangeSliderModel() {
        this.changedEvent = new ChangedEvent<>(this);
        this.colorChangedEvent = new ChangedEvent<>(this);
    }

    protected RangeSliderModel(List<String> positions) {
        this();
        assert positions.size() > 0;
        setPositionsInternal(positions);
    }

    protected RangeSliderModel(RangeSliderModel model) {
        this();
        setDataInternal(model);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propSupport.removePropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propSupport.addPropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propSupport.removePropertyChangeListener(propertyName, listener);
    }

    public void setData(RangeSliderModel model) {
        boolean changed;
        boolean colorChanged;

        synchronized (this) {
            changed = getPositionsDiffers(model);
            colorChanged = getColorsDiffers(model);
            setDataInternal(model);
        }

        if (changed) {
            changedEvent.fire();
        }
        if (colorChanged) {
            colorChangedEvent.fire();
        }
    }

    protected synchronized final boolean getPositionsDiffers(RangeSliderModel model) {
        return getPositionsDiffers(model.firstPosition, model.secondPosition) || !positions.equals(model.positions);
    }

    private boolean getPositionsDiffers(int firstPosition, int secondPosition) {
        return (firstPosition != this.firstPosition) || (secondPosition != this.secondPosition);
    }

    protected synchronized final boolean getColorsDiffers(RangeSliderModel model) {
        return !this.colors.equals(model.colors);
    }

    // @GuardedBy(this)
    private void invalidate() {
        slotCount = -1;
        indices = null;
    }

    // @GuardedBy(this)
    private void updateIndices() {
        if (slotCount >= 0) {
            return;
        }
        if (positions.isEmpty()) {
            slotCount = 0;
            indices = null;
            return;
        }
        spacedPositions = null;
        int[] ni = new int[positions.size()];
        int i = 0;
        int c = 0;
        boolean missing = false;
        Map<String, Integer> sl = this.slots;
        if (sl == null) {
            for (int p = ni.length - 1; p >= 0; p--) {
                ni[p] = p;
            }
            c = ni.length;
        } else for (String s : positions) {
            Integer x = sl.get(s);
            if (x == null) {
                ni[i] = -1;
                missing = true;
            } else {
                ni[i] = x;
                if (c <= x) {
                    c = x + 1;
                }
            }
            i++;
        }
        if (missing) {
            // 2nd pass, for each missing position assign a position adjacent
            // to previous or next anchored position
            int x = -1;
            int delta = 0;
            for (int n = 0; n < ni.length; n++) {
                if (ni[n] == -1) {
                    ni[n] = x;
                    delta++;
                } else {
                    if (delta > 0) {
                        ni[n] += delta;
                    }
                    x = ni[n];
                }
                x++;
            }
            c += delta;
        }
        slotCount = c;
        if (c == positions.size()) {
            indices = null;
        } else {
            indices = ni;
        }
    }

    protected final void setIndices(Map<String, Integer> indices) {
        if (Objects.equals(slots, indices)) {
            return;
        }
        synchronized (this) {
            this.slots = indices;
            invalidate();
        }
        changedEvent.fire();
    }

    protected final synchronized void setDataInternal(RangeSliderModel model) {
        positions = model.positions;
        // FIXME - also copy indices
        firstPosition = model.firstPosition;
        secondPosition = model.secondPosition;
        colors = model.colors;
        slots = model.slots;
        hatches = model.hatches;
    }

    protected final void setPositionsInternal(List<String> positions) {
        synchronized (this) {
            this.positions = positions;
            invalidate();
        }
        resetColors();
    }

    public void setPositions(List<String> positions) {
        assert positions.size() > 0;
        List<String> oldPos;
        synchronized (this) {
            if (this.positions.equals(positions)) {
                return;
            }
            oldPos = this.positions;
            setPositionsInternal(positions);
            // also fires change event
            remapPositions(oldPos);
        }
        colorChangedEvent.fire();
        propSupport.firePropertyChange(PROP_POSITIONS, oldPos, positions);
    }

    // @GuardedBy(this)
    private boolean remapPositions(List<String> prevPos) {
        int p1 = getFirstPosition();
        int p2 = getSecondPosition();

        int f = findMatchingPosition(prevPos, p1);
        int s = findMatchingPosition(prevPos, p2);

        if (f > s) {
            int a = s;
            s = f;
            f = a;
        }

        boolean r = f != p1 || s != p2;
        if (r) {
            // will fire change event
            setPositions(f, s);
        } else {
            changedEvent.fire();
        }
        return r;
    }

    // @GuardedBy(this)
    int findMatchingPosition(List<String> prev, int index) {
        if (index < 0) {
            return 0;
        }
        if (index >= prev.size()) {
            if (index >= positions.size()) {
                return 0;
            } else {
                return index;
            }
        }
        String s = prev.get(index);
        int n = positions.indexOf(s);
        while (n == -1 && index > 0) {
            index--;
            s = prev.get(index);
            n = positions.indexOf(s);
        }
        if (n >= 0) {
            return n;
        } else if (index < positions.size()) {
            return index;
        }
        return 0;
    }

    public int gapSizeBefore(String name) {
        synchronized (this) {
            int posIndex = positions.indexOf(name);
            if (posIndex == -1) {
                return 0;
            }
            updateIndices();
            int ind1 = indices[posIndex];
            if (posIndex == 0) {
                return ind1;
            }
            int ind0 = indices[posIndex - 1];
            return (ind1 - ind0) - 1;
        }
    }

    // @GuardedBy(this)
    private void resetColors() {
        colors = new ArrayList<>(Collections.nCopies(positions.size(), DEFAULT_COLOR));
        hatches = Collections.emptyList();
    }

    public void setColors(List<Color> colors) {
        synchronized (this) {
            if (this.colors.equals(colors)) {
                return;
            }
            this.colors = new ArrayList<>(colors);
            this.hatches = Collections.emptyList();
        }
        colorChangedEvent.fire();
    }

    /**
     * Sets up hatch colors for positions. Each position can have {@code null} (no hatch) or a specific Color in the list.
     * The list may be shorter than the current number of positions. <b>Note</b>: call this method
     * <b>after</b> {@link #setColors} - hatch is reset by {@code setColors} call.
     *
     * @param colors new hatch colors.
     */
    public synchronized void setHatchColors(List<Color> colors) {
        if (this.hatches.equals(colors)) {
            return;
        }
        this.hatches = new ArrayList<>(colors);
        colorChangedEvent.fire();
    }

    public synchronized List<Color> getHatchColors() {
        return Collections.unmodifiableList(hatches);
    }

    public synchronized List<Color> getColors() {
        return Collections.unmodifiableList(colors);
    }

    public synchronized RangeSliderModel copy() {
        return new RangeSliderModel(this);
    }

    public synchronized List<String> getPositions() {
        return Collections.unmodifiableList(positions);
    }

    public synchronized int findPosition(int slot) {
        if (slots == null) {
            return slot;
        }
        for (Map.Entry<String, Integer> s : slots.entrySet()) {
            if (s.getValue() == slot) {
                return positions.indexOf(s.getKey());
            }
        }
        return -1;
    }

    public synchronized int getSlot(int position) {
        if (slots == null) {
            return position;
        }
        if (position >= positions.size()) {
            return (position - positions.size()) + slotCount;
        }
        String n = positions.get(position);
        Integer ind = slots.get(n);
        return ind == null ? position : ind;
    }

    public List<String> getPositions(boolean withSpaces) {
        if (!withSpaces) {
            return getPositions();
        }
        List<String> res;
        synchronized (this) {
            if (positions.isEmpty()) {
                return getPositions();
            } else if (spacedPositions != null) {
                return spacedPositions;
            }
            updateIndices();
            List<String> arr = new ArrayList<>(positions);
            res = new ArrayList<>(slotCount);
            if (indices == null) {
                res.addAll(arr);
            } else for (int i = arr.size() - 1; i >= 0; i--) {
                String n = arr.get(i);
                int pos = indices[i];
                if (pos >= res.size()) {
                    while (pos > res.size()) {
                        res.add(null);
                    }
                    res.add(n);
                } else {
                    res.set(pos, n);
                }
            }
            this.spacedPositions = res;
        }
        return res;
    }

    public synchronized int getPositionCount(boolean includingGaps) {
        updateIndices();
        return includingGaps ? slotCount : positions.size();
    }

    public synchronized int getFirstPosition() {
        return firstPosition;
    }

    public synchronized int getSecondPosition() {
        return secondPosition;
    }

    public void setPositions(int fp, int sp) {
        assert fp <= sp;
        synchronized (this) {
            if (!getPositionsDiffers(fp, sp)) {
                return;
            }
            firstPosition = fp;
            secondPosition = sp;
            changedEvent.fire();
        }
        propSupport.firePropertyChange(PROP_POSITIONS, null, null);
    }

    public ChangedEvent<RangeSliderModel> getColorChangedEvent() {
        return colorChangedEvent;
    }

    @Override
    public ChangedEvent<RangeSliderModel> getChangedEvent() {
        return changedEvent;
    }
}
