/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.graphbuilderconf;

import java.util.*;
import java.util.function.*;

import com.oracle.graal.api.meta.*;

/**
 * {@link ResolvedJavaMethod}s that can be assigned a globally unique identifier for use as keys in
 * an array based map. This should only be used where the cost of a {@link Map} with
 * {@link ResolvedJavaMethod}s as keys is too high.
 */
public interface MethodIdHolder extends ResolvedJavaMethod {
    /**
     * Sets the unique, positive, non-zero identifier for this method.
     */
    void setMethodId(int id);

    /**
     * Gets the identifier set by {@link #setMethodId(int)} or 0 if no identifier was assigned to
     * this method.
     */
    int getMethodId();

    /**
     * A singleton class for allocating globally unique method identifiers.
     */
    static final class MethodIdAllocator {

        /**
         * Ensures a given method has a unique identifier.
         */
        public int assignId(MethodIdHolder holder) {
            assert Thread.holdsLock(instance) : "must only be called from within MethodIdHolder.allocateIds";
            int id = holder.getMethodId();
            if (id == 0) {
                id = nextId++;
                holder.setMethodId(id);
                if (idVerifierMap != null) {
                    idVerifierMap.put(holder, id);
                }
            } else {
                assert idVerifierMap.get(holder) == id;
            }
            return id;
        }

        private int nextId = 1;
        private final Map<MethodIdHolder, Integer> idVerifierMap;

        @SuppressWarnings("all")
        private MethodIdAllocator() {
            boolean assertionsEnabled = false;
            assert assertionsEnabled = true;
            idVerifierMap = assertionsEnabled ? new HashMap<>() : null;
        }

        /**
         * Singleton instance.
         */
        private static final MethodIdAllocator instance = new MethodIdAllocator();
    }

    /**
     * Executes some given code that ensures some set of {@link ResolvedJavaMethod}s have unique ids
     * {@linkplain MethodIdHolder#setMethodId(int) assigned} to them. The
     * {@link Consumer#accept(Object)} method of the given object is called under a global lock.
     */
    static void assignIds(Consumer<MethodIdAllocator> methodIdConsumer) {
        synchronized (MethodIdAllocator.instance) {
            methodIdConsumer.accept(MethodIdAllocator.instance);
        }
    }
}
