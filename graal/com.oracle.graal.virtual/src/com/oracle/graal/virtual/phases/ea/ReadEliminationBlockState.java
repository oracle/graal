/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.virtual.phases.ea;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;

public class ReadEliminationBlockState extends EffectsBlockState<ReadEliminationBlockState> {

    final HashMap<CacheEntry<?>, ValueNode> readCache;

    abstract static class CacheEntry<T> {

        public final ValueNode object;
        public final T identity;

        public CacheEntry(ValueNode object, T identity) {
            this.object = object;
            this.identity = identity;
        }

        public abstract CacheEntry<T> duplicateWithObject(ValueNode newObject);

        @Override
        public int hashCode() {
            int result = 31 + ((identity == null) ? 0 : identity.hashCode());
            return 31 * result + ((object == null) ? 0 : object.hashCode());
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CacheEntry<?>)) {
                return false;
            }
            CacheEntry<?> other = (CacheEntry<?>) obj;
            return identity == other.identity && object == other.object;
        }

        @Override
        public String toString() {
            return object + ":" + identity;
        }

        public abstract boolean conflicts(LocationIdentity other);
    }

    static class LoadCacheEntry extends CacheEntry<LocationIdentity> {

        public LoadCacheEntry(ValueNode object, LocationIdentity identity) {
            super(object, identity);
        }

        @Override
        public CacheEntry<LocationIdentity> duplicateWithObject(ValueNode newObject) {
            return new LoadCacheEntry(newObject, identity);
        }

        @Override
        public boolean conflicts(LocationIdentity other) {
            return identity == other;
        }
    }

    static class ReadCacheEntry extends CacheEntry<LocationNode> {

        public ReadCacheEntry(ValueNode object, LocationNode identity) {
            super(object, identity);
        }

        @Override
        public CacheEntry<LocationNode> duplicateWithObject(ValueNode newObject) {
            return new ReadCacheEntry(newObject, identity);
        }

        @Override
        public boolean conflicts(LocationIdentity other) {
            return identity.getLocationIdentity() == other;
        }
    }

    public ReadEliminationBlockState() {
        readCache = new HashMap<>();
    }

    public ReadEliminationBlockState(ReadEliminationBlockState other) {
        readCache = new HashMap<>(other.readCache);
    }

    @Override
    public String toString() {
        return super.toString() + " " + readCache;
    }

    @Override
    public boolean equivalentTo(ReadEliminationBlockState other) {
        return compareMapsNoSize(readCache, other.readCache);
    }

    public void addCacheEntry(CacheEntry<?> identifier, ValueNode value) {
        readCache.put(identifier, value);
    }

    public ValueNode getCacheEntry(CacheEntry<?> identifier) {
        return readCache.get(identifier);
    }

    public void killReadCache() {
        readCache.clear();
    }

    public void killReadCache(LocationIdentity identity) {
        Iterator<Map.Entry<CacheEntry<?>, ValueNode>> iter = readCache.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<CacheEntry<?>, ValueNode> entry = iter.next();
            if (entry.getKey().conflicts(identity)) {
                iter.remove();
            }
        }
    }

    public Map<CacheEntry<?>, ValueNode> getReadCache() {
        return readCache;
    }
}
