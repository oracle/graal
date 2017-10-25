/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.ListIterator;

public class ValueList<V extends ValueList.Value<V>> {

    public interface Value<V> {

        void replace(V oldValue, V newValue);

    }

    public interface PlaceholderFactory<V extends ValueList.Value<V>> {

        /**
         * Produce a new unique value used as placeholder for a forward referenced symbol.
         *
         * @return A new unique instance
         */
        V newValue();

    }

    private static final int NO_UNRESOLVED_VALUES = -1;

    private final ValueList<V> parent;

    // forward references are generally rare except for metadata in some older ir versions, we try
    // to optimize handling them by always remembering which forward reference will be resolved next
    private final PlaceholderFactory<V> placeholderFactory;
    private final HashMap<Integer, ForwardReference> forwardReferences;
    private final LinkedList<Integer> unresolvedIndices;
    private int nextUnresolved;

    private final ArrayList<V> valueList;

    public ValueList(PlaceholderFactory<V> placeholderFactory) {
        this(null, placeholderFactory);
    }

    public ValueList(ValueList<V> parent, PlaceholderFactory<V> placeholderFactory) {
        this.parent = parent;
        this.placeholderFactory = placeholderFactory;
        this.valueList = new ArrayList<>();
        this.forwardReferences = new HashMap<>();
        this.nextUnresolved = NO_UNRESOLVED_VALUES;
        this.unresolvedIndices = new LinkedList<>();
    }

    private void resolveForwardReference(int valueIndex, V newValue) {
        final ForwardReference fwdRef = forwardReferences.remove(valueIndex);
        fwdRef.resolve(newValue);

        unresolvedIndices.removeLast();
        if (unresolvedIndices.isEmpty()) {
            nextUnresolved = NO_UNRESOLVED_VALUES;
        } else {
            nextUnresolved = unresolvedIndices.getLast();
        }
    }

    private V getReference(int valueIndex, V dependent) {
        final ForwardReference fwdRef;
        if (forwardReferences.containsKey(valueIndex)) {
            fwdRef = forwardReferences.get(valueIndex);

        } else {
            fwdRef = new ForwardReference(placeholderFactory.newValue());
            forwardReferences.put(valueIndex, fwdRef);
            addUnresolvedIndex(valueIndex);
        }

        fwdRef.addDependent(dependent);
        return fwdRef.getPlaceholder();
    }

    private void addUnresolvedIndex(int valueIndex) {
        // LLVM uses a depth-first approach to emit symbols. This list is sorted in
        // descending order, so we should always be inserting newly discovered forward
        // references in the beginning, but special cases may occur.
        if (unresolvedIndices.isEmpty() || valueIndex < nextUnresolved) {
            nextUnresolved = valueIndex;
            unresolvedIndices.addLast(valueIndex);

        } else if (valueIndex > unresolvedIndices.getFirst()) {
            unresolvedIndices.addFirst(valueIndex);

        } else {
            final ListIterator<Integer> it = unresolvedIndices.listIterator();
            while (it.hasNext()) {
                int next = it.next();
                if (valueIndex > next) {
                    it.previous();
                    it.add(valueIndex);
                    break;
                }
            }
        }
    }

    public void add(V newValue) {
        final int valueIndex = valueList.size();

        valueList.add(newValue);

        if (nextUnresolved == valueIndex) {
            resolveForwardReference(valueIndex, newValue);
        }
    }

    public V getForwardReferenced(int index, V dependent) {
        int actualIndex = index;
        if (parent != null) {
            final int parentSize = parent.size();
            if (index < parentSize) {
                return parent.getForwardReferenced(index, dependent);

            } else {
                actualIndex -= parentSize;
            }
        }

        if (actualIndex >= 0 && actualIndex < valueList.size()) {
            return valueList.get(actualIndex);
        }

        return getReference(actualIndex, dependent);
    }

    public V getOrNull(int index) {
        int actualIndex = index;
        if (parent != null) {
            final int parentSize = parent.size();
            if (index < parentSize) {
                return parent.getOrNull(index);

            } else {
                actualIndex -= parentSize;
            }
        }

        if (actualIndex >= 0 && actualIndex < valueList.size()) {
            return valueList.get(actualIndex);
        }

        return null;
    }

    public int size() {
        int size = valueList.size();
        if (parent != null) {
            size += parent.size();
        }
        return size;
    }

    private final class ForwardReference {

        private final V placeholder;

        private final HashSet<V> dependents;

        ForwardReference(V placeholder) {
            this.placeholder = placeholder;
            this.dependents = new HashSet<>();
        }

        V getPlaceholder() {
            return placeholder;
        }

        void addDependent(V dependent) {
            dependents.add(dependent);
        }

        void resolve(V value) {
            for (V dependent : dependents) {
                dependent.replace(placeholder, value);
            }
        }
    }
}
