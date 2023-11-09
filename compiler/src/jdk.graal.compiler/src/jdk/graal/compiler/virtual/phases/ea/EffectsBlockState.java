/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.virtual.phases.ea;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableMapCursor;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

public abstract class EffectsBlockState<T extends EffectsBlockState<T>> {

    /**
     * This flag specifies whether this block is unreachable, which can happen during analysis if
     * conditions turn constant or nodes canonicalize to cfg sinks.
     */
    private boolean dead;

    /**
     * Exception edges marked dead for this state by dominating {@link WithExceptionNode} control
     * flow split nodes.
     */
    protected EconomicSet<HIRBlock> exceptionEdgesToKill;

    public EffectsBlockState() {
        // emtpy
    }

    public EffectsBlockState(EffectsBlockState<T> other) {
        this.dead = other.dead;
        EconomicSet<HIRBlock> otherExceptionEdgesToKill = other.exceptionEdgesToKill;
        if (otherExceptionEdgesToKill != null) {
            this.exceptionEdgesToKill = EconomicSet.create(otherExceptionEdgesToKill);
        }
    }

    @Override
    public String toString() {
        return "";
    }

    protected abstract boolean equivalentTo(T other);

    public boolean isDead() {
        return dead;
    }

    public void markAsDead() {
        this.dead = true;
    }

    /**
     * Returns true if every value in subMap is also present in the superMap (according to "equals"
     * semantics).
     */
    protected static <K, V> boolean isSubMapOf(EconomicMap<K, V> superMap, EconomicMap<K, V> subMap) {
        if (superMap == subMap) {
            return true;
        }
        UnmodifiableMapCursor<K, V> cursor = subMap.getEntries();
        while (cursor.advance()) {
            K key = cursor.getKey();
            V value = cursor.getValue();
            assert value != null;
            V otherValue = superMap.get(key);
            if (otherValue != value && !value.equals(otherValue)) {
                return false;
            }
        }
        return true;
    }
}
