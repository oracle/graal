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
     *
     * FIXME: this can probably be done in a much simpler way.
     */
    private NavigableSet<Integer> nonSectionElementIndices = new TreeSet<>();
    private NavigableSet<Integer> sectionElementIndices = new TreeSet<>();

    public int elementIndexToSectionIndex(int i) {
        // it's the element index, less the count of elements at lower element indices
        SortedSet<Integer> lowerNonSectionElements = nonSectionElementIndices.headSet(i);
        return i - lowerNonSectionElements.size();
    }

    public int sectionIndexToElementIndex(int sectionIndex) {
        /*
         * Something of a HACK: we bisect the NavigableSet until we find the nth in order. This
         * should be an O(log(n)) operation for a set of size n.
         */
        int pivot = (sectionElementIndices.first() + sectionElementIndices.last()) / 2;
        int leftOffset = 0;
        SortedSet<Integer> leftSet = sectionElementIndices.headSet(pivot);
        SortedSet<Integer> rightSet = sectionElementIndices.tailSet(pivot);
        // what is the section index of the first element in rightSet?
        int rightHeadIndex = leftOffset + leftSet.size();
        while (leftSet.size() + rightSet.size() > 1) {
            if (rightHeadIndex > sectionIndex) {
                // recurse left; if our left is empty, we fail
                if (leftSet.size() == 0) {
                    throw new IndexOutOfBoundsException();
                }
                pivot = (int) Math.floor((leftSet.first() + leftSet.last()) / 2.0);
                SortedSet<Integer> newLeftSet = leftSet.headSet(pivot);
                SortedSet<Integer> newRightSet = leftSet.tailSet(pivot);
                // assert progress
                assert !leftSet.equals(newLeftSet) || !rightSet.equals(newRightSet);
                leftSet = newLeftSet;
                rightSet = newRightSet;
            } else {
                // recurse right; if our right set is empty, we fail
                if (rightSet.size() == 0) {
                    throw new IndexOutOfBoundsException();
                }
                pivot = (int) Math.ceil((rightSet.first() + rightSet.last()) / 2.0);
                leftOffset += leftSet.size();
                SortedSet<Integer> newLeftSet = rightSet.headSet(pivot);
                SortedSet<Integer> newRightSet = rightSet.tailSet(pivot);
                // assert progress
                assert !leftSet.equals(newLeftSet) || !rightSet.equals(newRightSet);
                leftSet = newLeftSet;
                rightSet = newRightSet;
            }
            rightHeadIndex = leftOffset + leftSet.size();
        }
        if ((rightHeadIndex > sectionIndex && leftSet.size() == 0) || !(rightHeadIndex > sectionIndex) && rightSet.size() == 0) {
            throw new IndexOutOfBoundsException();
        }
        int toReturn = (rightHeadIndex > sectionIndex) ? leftSet.last() : rightSet.first();
        assert toReturn == sectionIndexToElementIndexNaive(sectionIndex);
        return toReturn;
    }

    Section getSection(int sectionIndex) {
        int elementIndex = sectionIndexToElementIndex(sectionIndex);
        if (elementIndex == -1) {
            return null;
        }
        Element found = get(elementIndex);
        assert found instanceof Section;
        return (Section) found;
    }

    public int sectionIndexToElementIndexNaive(int i) {
        // There are two apparent possible implementations.
        // 1. Get the ith element and search forwards
        // 2. Increment the section index once for every preceding non-section element
        // BUT actually we can't do 2, because how do we tell whether an element is
        // preceding a given section, if we don't know the section's element number?

        // the naive way to implement this function:
        // get element i
        // get the element i in the sections list
        // work forward in the elements list until we find it
        // PROBLEM 1: "get element i" may not be O(1) (but is for an ArrayList)
        // PROBLEM 2: can't get an iterator at this element! need to linear-search from start!
        // FIXME: get rid of this linear search
        Section s = null;
        int si = -1;
        Iterator<Section> iter = sectionsIterator();
        while (iter.hasNext()) {
            // we want the ith section
            Section cur = iter.next();
            ++si;
            if (i == si) {
                s = cur;
                break;
            }
        }
        if (s == null) {
            // there is no such section. that might be okay -- we're
            // looking for a position to insert at. In general, if we
            // have n sections, then the element index corresponding to
            // section position n (i.e. one after the last one)
            // is one after the element count
            assert i == sectionsCount();
            return size();
        }
        assert s != null;
        int ei = si;
        while (get(ei) != s) {
            ++ei;
        }
        return ei;
    }

    public Iterator<Section> sectionsIterator() {
        return entries.stream().filter(element -> element instanceof Section).map(element -> (Section) element).iterator();
    }

    public int sectionsCount() {
        return sectionElementIndices.size();
    }

    public int nonSectionsCount() {
        return nonSectionElementIndices.size();
    }

    private void decrementSectionCounters(Element removed, int pos) {
        if (removed instanceof Section) {
            sectionElementIndices.remove(pos);
        } else {
            nonSectionElementIndices.remove(pos);
        }
    }

    private void incrementSectionCounters(Element added, int pos) {
        if (added instanceof Section) {
            sectionElementIndices.add(pos);
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
        boolean ret = entries.remove(arg);
        int pos = entries.indexOf(arg);
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

    @Override
    public void add(int pos, Element arg1) {
        // all numbers >= pos in the two sets must be incremented by one
        adjustAllGE(sectionElementIndices, pos, 1);
        adjustAllGE(nonSectionElementIndices, pos, 1);
        // now we can do the addition
        entries.add(pos, arg1);
        ((arg1 instanceof Section) ? sectionElementIndices : nonSectionElementIndices).add(pos);
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
        adjustAllGE(sectionElementIndices, arg0, -1);
        adjustAllGE(nonSectionElementIndices, arg0, -1);
        Element e = entries.remove(arg0);
        elementForName.remove(e.getName());
        return e;
    }

    @Override
    public Element set(int arg0, Element arg1) {
        Element replaced = entries.set(arg0, arg1);
        elementForName.remove(replaced.getName());
        elementForName.put(arg1.getName(), arg1);
        if (replaced instanceof Section) {
            // we lost a section
            sectionElementIndices.remove(arg0);
        } else {
            // we lost a non-section
            nonSectionElementIndices.remove(arg0);
        }
        if (arg1 instanceof Section) {
            // we gained a section
            sectionElementIndices.add(arg0);
        } else {
            // we gained a non-section
            nonSectionElementIndices.add(arg0);
        }

        return replaced;
    }

    @Override
    public List<Element> subList(int arg0, int arg1) {
        return entries.subList(arg0, arg1);
    }
}
