/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

public abstract class ValueList<V extends ValueList.Value<V, C>, C extends ValueList.ValueVisitor<V>> {

    public static void dropLocalScope(int localScopeStart, List<?> valueList) {
        if (localScopeStart >= 0 && valueList.size() - localScopeStart > 0) {
            valueList.subList(localScopeStart, valueList.size()).clear();
        }
    }

    public interface ValueVisitor<V> {

        default void defaultAction(@SuppressWarnings("unused") V v) {
        }
    }

    public interface Value<V, C extends ValueVisitor<V>> {

        void replace(V oldValue, V newValue);

        void accept(C visitor);

    }

    public interface PlaceholderFactory<V extends ValueList.Value<V, C>, C extends ValueList.ValueVisitor<V>> {

        /**
         * Produce a new unique value used as placeholder for a forward referenced symbol.
         *
         * @return A new unique instance
         */
        V newValue();

    }

    private static final int GLOBAL_SCOPE_START = -1;

    // forward references are generally rare except for metadata in some older ir versions, we try
    // to optimize handling them by always remembering which forward reference will be resolved next
    private final PlaceholderFactory<V, C> placeholderFactory;
    private final HashMap<Integer, ForwardReference> forwardReferences;
    private final BitSet unresolvedIndices;

    private int scopeStart;

    private final ArrayList<V> valueList;

    protected ValueList(PlaceholderFactory<V, C> placeholderFactory) {
        this.placeholderFactory = placeholderFactory;
        this.valueList = new ArrayList<>();
        this.forwardReferences = new HashMap<>();
        this.unresolvedIndices = new BitSet();
        this.scopeStart = GLOBAL_SCOPE_START;
    }

    private void resolveForwardReference(int valueIndex, V newValue) {
        forwardReferences.remove(valueIndex).resolve(newValue);
    }

    private ForwardReference getReference(int valueIndex) {
        ValueList<V, C>.ForwardReference fwdRef = forwardReferences.get(valueIndex);
        if (fwdRef != null) {
            return fwdRef;
        }
        forwardReferences.put(valueIndex, fwdRef = new ForwardReference(placeholderFactory.newValue()));
        unresolvedIndices.set(valueIndex);
        return fwdRef;
    }

    public void add(V newValue) {
        final int valueIndex = valueList.size();

        valueList.add(newValue);

        if (unresolvedIndices.get(valueIndex)) {
            unresolvedIndices.set(valueIndex, false);
            resolveForwardReference(valueIndex, newValue);
        }
    }

    public V getForwardReferenced(int index, V dependent) {
        if (index >= 0 && index < valueList.size()) {
            return valueList.get(index);
        } else {
            final ForwardReference ref = getReference(index);
            ref.addDependent(dependent);
            return ref.getPlaceholder();
        }
    }

    public V getOrNull(int index) {
        if (index >= 0 && index < valueList.size()) {
            return valueList.get(index);
        } else {
            return null;
        }
    }

    public void onParse(int index, Consumer<V> action) {
        if (index < valueList.size()) {
            action.accept(valueList.get(index));
        } else {
            getReference(index).addCallBack(action);
        }
    }

    public void startScope() {
        scopeStart = valueList.size();
    }

    public void endScope() {
        dropLocalScope(scopeStart, valueList);
        scopeStart = GLOBAL_SCOPE_START;
    }

    public int size() {
        return valueList.size();
    }

    public void accept(C visitor) {
        for (V value : valueList) {
            value.accept(visitor);
        }
    }

    private final class ForwardReference {

        private final V placeholder;

        private final HashSet<V> dependents;

        private final HashSet<Consumer<V>> callBacks;

        ForwardReference(V placeholder) {
            this.placeholder = placeholder;
            this.dependents = new HashSet<>();
            this.callBacks = new HashSet<>();
        }

        V getPlaceholder() {
            return placeholder;
        }

        void addDependent(V dependent) {
            dependents.add(dependent);
        }

        void addCallBack(Consumer<V> callBack) {
            callBacks.add(callBack);
        }

        void resolve(V value) {
            for (V dependent : dependents) {
                dependent.replace(placeholder, value);
            }
            for (Consumer<V> callBack : callBacks) {
                callBack.accept(value);
            }
        }
    }
}
