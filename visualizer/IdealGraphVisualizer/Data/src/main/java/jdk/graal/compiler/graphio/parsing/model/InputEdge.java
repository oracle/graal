/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graphio.parsing.model;

import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.Objects;
import java.util.WeakHashMap;

public class InputEdge {
    public static final String SUCCESSOR_EDGE_TYPE = "Successor";

    public enum State {
        IMMUTABLE,
        SAME,
        NEW,
        DELETED
    }

    public static final Comparator<InputEdge> OUTGOING_COMPARATOR = Comparator.comparingInt(InputEdge::getFromIndex).thenComparingInt(InputEdge::getTo);

    public static final Comparator<InputEdge> INGOING_COMPARATOR = Comparator.comparingInt(InputEdge::getToIndex).thenComparingInt(InputEdge::getFrom);

    private final char toIndex;
    private final char fromIndex;

    private final int from;
    private final int to;
    private final int listIndex;
    private final String label;
    private final String type;

    private State state;
    private int hashCode = -1;

    public InputEdge(char toIndex, int from, int to) {
        this((char) 0, toIndex, from, to, null, null);
    }

    public InputEdge(char fromIndex, char toIndex, int from, int to) {
        this(fromIndex, toIndex, from, to, null, null);
    }

    public InputEdge(char fromIndex, char toIndex, int from, int to, String label, String type) {
        this(fromIndex, toIndex, from, to, -1, label, type, State.SAME);
    }

    public InputEdge(char fromIndex, char toIndex, int from, int to, int listIndex, String label, String type) {
        this(fromIndex, toIndex, from, to, listIndex, label, type, State.SAME);
    }

    static WeakHashMap<InputEdge, WeakReference<InputEdge>> immutableCache = new WeakHashMap<>();

    public static synchronized InputEdge createImmutable(char fromIndex, char toIndex, int from, int to, int listIndex, String label, String type) {
        InputEdge edge = new InputEdge(fromIndex, toIndex, from, to, listIndex, label, type, State.IMMUTABLE);
        WeakReference<InputEdge> result = immutableCache.get(edge);
        if (result != null) {
            InputEdge edge2 = result.get();
            if (edge2 != null) {
                return edge2;
            }
        }
        immutableCache.put(edge, new WeakReference<>(edge));
        return edge;
    }

    public InputEdge(char fromIndex, char toIndex, int from, int to, int listIndex, String label, String type, State state) {
        this.toIndex = toIndex;
        this.fromIndex = fromIndex;
        this.from = from;
        this.to = to;
        this.listIndex = listIndex;
        this.state = state;
        this.label = label;
        this.type = type;

        int hash = Objects.hash(from, to, fromIndex, toIndex, listIndex);
        if (state == State.IMMUTABLE) {
            hash = Objects.hash(hash, label);
        }
        this.hashCode = hash;
    }

    public State getState() {
        return state;
    }

    public void setState(State x) {
        if (x == state) {
            return;
        }
        if (state == State.IMMUTABLE) {
            throw new InternalError("Can't change immutable instances");
        }
        this.state = x;
        // terminal state
        if (state == State.IMMUTABLE) {
            hashCode = Objects.hash(hashCode, label);
        }
    }

    public char getToIndex() {
        return toIndex;
    }

    public char getFromIndex() {
        return fromIndex;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }

    public int getListIndex() {
        return listIndex;
    }

    public String getLabel() {
        return label;
    }

    public String getDisplayLabel() {
        String ret = getLabel();
        if (listIndex >= 0) {
            ret = ret + "[" + listIndex + "]";
        }
        return ret;
    }

    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InputEdge conn2)) {
            return false;
        }
        boolean result = conn2.fromIndex == fromIndex && conn2.toIndex == toIndex && conn2.from == from && conn2.to == to && conn2.listIndex == listIndex;
        if (result && (state == State.IMMUTABLE || conn2.state == State.IMMUTABLE)) {
            // Immutable instances must be exactly the same
            return conn2.state == state && conn2.label.equals(label);
        }
        return result;
    }

    @Override
    public String toString() {
        return "Edge from " + from + " to " + to + "(" + (int) fromIndex + ", " + (int) toIndex + ") ";
    }

    @Override
    public int hashCode() {
        assert hashCode != -1;
        return hashCode;
    }
}
