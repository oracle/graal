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
package com.oracle.truffle.llvm.parser.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

class ValueList<V extends ValueList.Value<V>> {

    interface Value<V> {

        void replace(V oldValue, V newValue);

    }

    interface PlaceholderFactory<V extends ValueList.Value<V>> {

        /**
         * Produce a new unique value used as placeholder for a forward referenced symbol.
         *
         * @return A new unique instance
         */
        V newValue();

    }

    private final ValueList<V> parent;

    private final PlaceholderFactory<V> placeholderFactory;
    private final HashMap<Integer, ForwardReference> forwardReferences;

    private final ArrayList<V> valueList;

    ValueList(PlaceholderFactory<V> placeholderFactory) {
        this(null, placeholderFactory);
    }

    ValueList(ValueList<V> parent, PlaceholderFactory<V> placeholderFactory) {
        this.parent = parent;
        this.placeholderFactory = placeholderFactory;
        this.valueList = new ArrayList<>();
        this.forwardReferences = new HashMap<>();
    }

    public void add(V newValue) {
        final int valueIndex = valueList.size();

        valueList.add(newValue);

        if (forwardReferences.containsKey(valueIndex)) {
            final ForwardReference fwdRef = forwardReferences.remove(valueIndex);
            fwdRef.resolve(newValue);
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

        final ForwardReference fwdRef;
        if (forwardReferences.containsKey(actualIndex)) {
            fwdRef = forwardReferences.get(actualIndex);

        } else {
            fwdRef = new ForwardReference(placeholderFactory.newValue());
            forwardReferences.put(actualIndex, fwdRef);
        }

        fwdRef.addDependent(dependent);
        return fwdRef.getPlaceholder();
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
