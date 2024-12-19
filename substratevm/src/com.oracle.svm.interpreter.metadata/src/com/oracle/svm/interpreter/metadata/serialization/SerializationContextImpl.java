/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata.serialization;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

abstract class SerializationContextImpl implements SerializationContext {
    // The following two objects represent a bidirectional map between references and indices.
    protected final List<Object> indexToReference;
    protected final IdentityHashMap<Object, Integer> referenceToIndex;

    protected final List<Class<?>> knownClasses;

    protected SerializationContextImpl(List<Class<?>> knownClasses) {
        if (knownClasses.contains(Class.class)) {
            throw new IllegalArgumentException("Known classes cannot contain Class.class");
        }

        this.knownClasses = knownClasses;
        this.referenceToIndex = new IdentityHashMap<>();
        this.indexToReference = new ArrayList<>();

        indexToReference.add(null); // index 0 == null

        int classIndex = recordReference(Class.class); // index 1 == Class.class
        assert classIndex == CLASS_REFERENCE_INDEX;

        for (Class<?> clazz : knownClasses) {
            recordReference(clazz);
        }
    }

    @Override
    public <T> int recordReference(T value) {
        if (value == null) {
            return NULL_REFERENCE_INDEX;
        }
        if (referenceToIndex.containsKey(value)) {
            throw new IllegalStateException("Duplicated reference: " + value);
        }
        int refIndex = indexToReference.size();
        indexToReference.add(value);
        referenceToIndex.put(value, refIndex);
        return refIndex;
    }
}
