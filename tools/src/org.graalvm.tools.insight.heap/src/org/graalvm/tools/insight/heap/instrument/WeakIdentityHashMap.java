/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight.heap.instrument;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple hash map that holds the keys weekly and uses identity comparison.
 */
public final class WeakIdentityHashMap<K, V> {

    private final Map<ObjectReference, V> map = new HashMap<>();
    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    public V get(Object key) {
        assert key != null;
        removeCollectedEntries();
        return map.get(new ObjectReference(key, null));
    }

    public V put(K key, V value) {
        assert key != null;
        assert value != null;
        removeCollectedEntries();
        return map.put(new ObjectReference(key, queue), value);
    }

    private void removeCollectedEntries() {
        ObjectReference r;
        while ((r = (ObjectReference) queue.poll()) != null) {
            map.remove(r);
        }
    }

    private static final class ObjectReference extends WeakReference<Object> {

        private final int hash;

        ObjectReference(Object referent, ReferenceQueue<Object> queue) {
            super(referent, queue);
            hash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof ObjectReference) {
                final ObjectReference other = (ObjectReference) obj;
                if (this.hash != other.hash) {
                    return false;
                }
                return get() == other.get();
            } else {
                return false;
            }
        }

    }

}
