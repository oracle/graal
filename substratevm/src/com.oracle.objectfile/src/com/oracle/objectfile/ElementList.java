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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.TreeSet;

import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.ObjectFile.Section;

/**
 * This class is the data structure used internally by {@link ObjectFile} to maintain its list of
 * elements, the section->element index map and the name->element mapping.
 */
public class ElementList implements List<Element> {

    /*
     * This class was previously a static nested class of ObjectFile, but has been moved out for
     * manageability's sake.
     */

    private List<Element> entries = new ArrayList<>();

    // we also store named mappings; our Collection.* overrides must keep these maps up to date
    protected final Map<String, Element> elementForName = new HashMap<>();

    // protected final Map<Element, String> nameForElement = new HashMap<>();

    public Element forName(String name) {
        return elementForName.get(name);
    }

    /*
     * We also care about two sets of indices: section and element.
     *
     * We keep our elements in a list, so each element has its own index. We also keep a list of
     * only sections, and make it reasonably quick to get a section index. NOTE that the section
     * numbering needs to be adjusted by 1 to work as ELF SHT indices, since the latter start at 1.
     */
    private NavigableSet<Integer> nonSectionElementIndices = new TreeSet<>();
    private List<Integer> sectionElementIndices = new ArrayList<>();
    private List<Section> sections = new ArrayList<>();

    public int elementIndexToSectionIndex(int i) {
        // it's the element index, less the count of elements at lower element indices
        SortedSet<Integer> lowerNonSectionElements = nonSectionElementIndices.headSet(i);
        return i - lowerNonSectionElements.size();
    }

    public int sectionIndexToElementIndex(int sectionIndex) {
        if (sectionIndex == sections.size()) {
            return size();
        }
        return sectionElementIndices.get(sectionIndex);
    }

    Section getSection(int sectionIndex) {
        return sections.get(sectionIndex);
    }

    public Iterator<Section> sectionsIterator() {
        return sections.iterator();
    }

    public int sectionsCount() {
        return sections.size();
    }

    public int nonSectionsCount() {
        return nonSectionElementIndices.size();
    }

    private void decrementSectionCounters(Element removed, int pos) {
        if (removed instanceof Section) {
            int sectionIndex = elementIndexToSectionIndex(pos);
            sectionElementIndices.remove(sectionIndex);
            sections.remove(sectionIndex);
        } else {
            nonSectionElementIndices.remove(pos);
        }
    }

    private void incrementSectionCounters(Element added, int pos) {
        if (added instanceof Section) {
            int sectionIndex = elementIndexToSectionIndex(pos);
            sectionElementIndices.add(sectionIndex, pos);
            sections.add(sectionIndex, (Section) added);
        } else {
            nonSectionElementIndices.add(pos);
        }
    }

    @Override
    public boolean add(Element arg) {
        elementForName.put(arg.getName(), arg);
        boolean changed = entries.add(arg);
        if (changed) {
            int pos = size() - 1;
            incrementSectionCounters(arg, pos);
        }
        return changed;
    }

    @Override
    public boolean addAll(Collection<? extends Element> arg) {
        boolean changed = false;
        for (Element e : arg) {
            changed |= this.add(e);
        }
        return changed;
    }

    @Override
    public void clear() {
        entries.clear();
        elementForName.clear();
        sectionElementIndices.clear();
        nonSectionElementIndices.clear();
        sections.clear();
    }

    @Override
    public boolean contains(Object arg) {
        return entries.contains(arg);
    }

    @Override
    public boolean containsAll(Collection<?> arg) {
        return entries.containsAll(arg);
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public Iterator<Element> iterator() {
        return entries.iterator();
    }

    @Override
    public boolean remove(Object arg) {
        int pos = entries.indexOf(arg);
        boolean ret = entries.remove(arg);
        if (ret) {
            // remove its index from the appropriate set
            decrementSectionCounters((Element) arg, pos);
            // adjust other indices
            adjustAllGE(nonSectionElementIndices, pos, -1);
            adjustAllGE(sectionElementIndices, pos, -1);
            // now we can remove it
            elementForName.remove(((Element) arg).getName());

        }
        return ret;
    }

    @Override
    public boolean removeAll(Collection<?> arg0) {
        boolean removed = false;
        for (Object o : arg0) {
            removed |= this.remove(o);
        }
        return removed;
    }

    @Override
    public boolean retainAll(Collection<?> arg0) {
        boolean changed = false;
        for (Element e : this) {
            if (!arg0.contains(e)) {
                changed |= this.remove(e);
            }
        }
        return changed;
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public Object[] toArray() {
        return entries.toArray();
    }

    @Override
    public <T> T[] toArray(T[] arg) {
        return entries.toArray(arg);
    }

    static void adjustAllGE(SortedSet<Integer> t, int pos, int diff) {
        TreeSet<Integer> tmp = new TreeSet<>();
        SortedSet<Integer> tailSet = t.tailSet(pos);
        for (Integer i : tailSet) {
            tmp.add(i + diff);
        }
        t.removeAll(tailSet);
        t.addAll(tmp);
    }

    private static void adjustAllGE(List<Integer> indices, int pos, int diff) {
        for (ListIterator<Integer> iterator = indices.listIterator(); iterator.hasNext();) {
            Integer index = iterator.next();
            if (index >= pos) {
                iterator.set(index + diff);
            }
        }
    }

    @Override
    public void add(int pos, Element arg1) {
        // all numbers >= pos in the two sets must be incremented by one
        adjustAllGE(sectionElementIndices, pos, 1);
        adjustAllGE(nonSectionElementIndices, pos, 1);
        // now we can do the addition
        entries.add(pos, arg1);
        incrementSectionCounters(arg1, pos);
        elementForName.put(arg1.getName(), arg1);
    }

    @Override
    public boolean addAll(int arg0, Collection<? extends Element> arg1) {
        int pos = arg0;
        boolean changed = false;
        for (Element e : arg1) {
            this.add(pos++, e);
            changed = true;
        }
        return changed;
    }

    @Override
    public boolean equals(Object arg0) {
        return entries.equals(arg0);
    }

    @Override
    public Element get(int arg0) {
        return entries.get(arg0);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public int indexOf(Object arg0) {
        return entries.indexOf(arg0);
    }

    @Override
    public int lastIndexOf(Object arg0) {
        return entries.lastIndexOf(arg0);
    }

    @Override
    public ListIterator<Element> listIterator() {
        return entries.listIterator();
    }

    @Override
    public ListIterator<Element> listIterator(int arg0) {
        return entries.listIterator(arg0);
    }

    @Override
    public Element remove(int arg0) {
        Element e = entries.remove(arg0);
        decrementSectionCounters(e, arg0);
        adjustAllGE(sectionElementIndices, arg0, -1);
        adjustAllGE(nonSectionElementIndices, arg0, -1);
        elementForName.remove(e.getName());
        return e;
    }

    @Override
    public Element set(int arg0, Element arg1) {
        Element replaced = entries.set(arg0, arg1);
        elementForName.remove(replaced.getName());
        elementForName.put(arg1.getName(), arg1);
        decrementSectionCounters(replaced, arg0);
        incrementSectionCounters(arg1, arg0);

        return replaced;
    }

    @Override
    public List<Element> subList(int arg0, int arg1) {
        return entries.subList(arg0, arg1);
    }
}
