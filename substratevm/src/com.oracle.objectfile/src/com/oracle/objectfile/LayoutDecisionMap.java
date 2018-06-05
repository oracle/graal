/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

public class LayoutDecisionMap implements Iterable<LayoutDecision> {

    /*
     * A note about reading versus writing: LayoutDecisionMap is only used for writing. As a
     * consequence, we can key on the Element to which our decisions relate, which we can assume has
     * always been instantiated at the time we store decisions. The whole layout, a WriteLayout, is
     * a map keyed on Elements and with LayoutDecisionMaps as values. The corresponding data
     * structure for read-in is ReadLayout, and that does not have this property, since we may read
     * in a decision about an element (say, the number of sections) before the corresponding element
     * object (say the section header table) has been instantiated. These two interfaces are
     * similar, but are formally unrelated for now. Instead, if we want to preserve a ReadLayout on
     * writing, we copy LayoutProperties (from the ReadLayout) into a fresh WriteLayout early in the
     * build process (see element.getDecisions()).
     */

    ObjectFile.Element e; // the element whose decisions we store
    HashMap<LayoutDecision.Kind, LayoutDecision> decisions = new HashMap<>();

    public LayoutDecisionMap(ObjectFile.Element e) {
        this.e = e;
    }

    public boolean containsKey(Object key) {
        return decisions.containsKey(key);
    }

    public Collection<LayoutDecision> getDecisions() {
        return decisions.values();
    }

    public LayoutDecision getDecision(LayoutDecision.Kind key) {
        return decisions.get(key);
    }

    public Object getDecidedValue(LayoutDecision.Kind key) {
        return getDecision(key).getValue();
    }

    public LayoutDecision putUndecided(LayoutDecision.Kind k) {
        return decisions.put(k, new LayoutDecision(k, e, null));
    }

    public LayoutDecision putDecidedValue(LayoutDecision.Kind k, Object v) {
        assert v != null;
        LayoutDecision value = new LayoutDecision(k, e, v);
        return decisions.put(k, value);
    }

    public void putDecidedValues(LayoutDecisionMap copyingIn) {
        assert !copyingIn.getDecisions().stream().filter(d -> d.getValue() == null).findAny().isPresent();
        decisions.putAll(copyingIn.decisions);
    }

    @Override
    public String toString() {
        return decisions.toString();
    }

    @Override
    public Iterator<LayoutDecision> iterator() {
        return decisions.values().iterator();
    }
}
