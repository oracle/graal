/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.objectfile;

import java.util.ArrayList;
import java.util.List;

public final class LayoutDecision implements Comparable<LayoutDecision> {

    public enum Kind {
        /**
         * NOTE that offsets are only useful as WriteLayoutDecisions, not ReadLayoutDecisions,
         * because we store ReadLayoutDecisions in a FileLayout, where they are keyed on offset,
         * i.e. the offset is already known and is stored outside the LayoutDecision.
         */
        OFFSET,
        SIZE,
        CONTENT,
        IDENTITY,
        VADDR,
    }

    private final Kind kind;
    private final ObjectFile.Element element;
    private final List<LayoutDecision> dependsOn;
    private final List<LayoutDecision> dependedOnBy;

    private Object value;

    LayoutDecision(Kind kind, ObjectFile.Element element, Object value) {
        dependsOn = new ArrayList<>();
        dependedOnBy = new ArrayList<>();
        this.kind = kind;
        // assert element != null;
        this.element = element;

        assert !(value instanceof LayoutDecision);
        this.value = value;
    }

    @Override
    public String toString() {
        return "LayoutDecision(" + element + ", " + kind + ", " + value + ")";
    }

    List<LayoutDecision> dependedOnBy() {
        return dependedOnBy;
    }

    List<LayoutDecision> dependsOn() {
        return dependsOn;
    }

    Kind getKind() {
        return kind;
    }

    void setValue(Object value) {
        assert this.value == null; // can't re-set
        assert value instanceof Integer || value instanceof byte[];
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    public boolean isTaken() {
        return value != null;
    }

    public ObjectFile.Element getElement() {
        return element;
    }

    @Override
    public int compareTo(LayoutDecision arg) {
        ObjectFile.Element ourElement = getElement();
        int ourElementIndex = ourElement == null ? -1 : ourElement.getOwner().getElements().indexOf(ourElement);
        int ourKindOrdinal = getKind().ordinal();

        ObjectFile.Element argElement = arg.getElement();
        int argElementIndex = argElement == null ? -1 : argElement.getOwner().getElements().indexOf(argElement);
        int argKindOrdinal = arg.getKind().ordinal();

        // we can only compare decisions about the same object file
        if (ourElement != null && argElement != null && ourElement.getOwner() != argElement.getOwner()) {
            throw new IllegalArgumentException("Cannot compare decisions across object files");
        }

        if (ourElementIndex < argElementIndex) {
            return -1;
        } else if (ourElementIndex > argElementIndex) {
            return 1;
        } else {
            if (ourKindOrdinal < argKindOrdinal) {
                return -1;
            } else if (ourKindOrdinal > argKindOrdinal) {
                return 1;
            } else {
                return 0;
            }
        }
    }
}
