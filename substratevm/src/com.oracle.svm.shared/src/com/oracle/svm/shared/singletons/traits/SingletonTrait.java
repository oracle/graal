/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.shared.singletons.traits;

import java.util.Objects;

import com.oracle.svm.shared.singletons.Invariants;

/**
 * Describes a facet of a singleton's behavior. See {@link SingletonTraits} and
 * {@link SingletonTraitKind} for more details.
 */
public abstract class SingletonTrait<T> {

    public static final SingletonTrait<?>[] EMPTY_ARRAY = new SingletonTrait<?>[0];

    private final SingletonTraitKind kind;
    private final T metadata;

    public SingletonTrait(SingletonTraitKind kind, T metadata) {
        /* Guarantee the metadata for this trait is of the expected kind. */
        Invariants.guarantee(kind.getMetadataClass().isInstance(metadata), "Unexpected metadata kind.");
        this.kind = kind;
        this.metadata = metadata;
    }

    public SingletonTraitKind kind() {
        return kind;
    }

    public T metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SingletonTrait<?> that) {
            return kind == that.kind && Objects.equals(metadata, that.metadata);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, metadata);
    }

    @Override
    public String toString() {
        return "SingletonTrait[" + "kind=" + kind + ", " + "metadata=" + metadata + ']';
    }
}
