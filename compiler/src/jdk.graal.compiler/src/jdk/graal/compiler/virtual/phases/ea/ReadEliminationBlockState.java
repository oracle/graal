/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.JavaKind;

/**
 * This class maintains a set of known values, identified by base object, locations and offset.
 */
public class ReadEliminationBlockState extends EffectsBlockState<ReadEliminationBlockState> {

    final EconomicMap<CacheEntry<?>, ValueNode> readCache;

    public abstract static class CacheEntry<T> {

        public final ValueNode object;
        public final T identity;

        protected CacheEntry(ValueNode object, T identity) {
            this.object = object;
            this.identity = identity;
        }

        public abstract CacheEntry<T> duplicateWithObject(ValueNode newObject);

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CacheEntry<?>)) {
                return false;
            }
            CacheEntry<?> other = (CacheEntry<?>) obj;
            return identity.equals(other.identity) && object == other.object;
        }

        @Override
        public int hashCode() {
            int result = 31 + ((identity == null) ? 0 : identity.hashCode());
            // we need to use the identity hash code for the object since the node may not yet have
            // a valid id and thus not have a stable hash code
            return 31 * result + ((object == null) ? 0 : System.identityHashCode(object));
        }

        @Override
        public String toString() {
            return object + ":" + identity;
        }

        public abstract boolean conflicts(LocationIdentity other, ValueNode index, ValueNode array);

        public abstract LocationIdentity getIdentity();
    }

    public static final class LoadCacheEntry extends CacheEntry<LocationIdentity> {

        public LoadCacheEntry(ValueNode object, LocationIdentity identity) {
            super(object, identity);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof LoadCacheEntry) {
                return super.equals(obj);
            }
            return false;
        }

        @Override
        public CacheEntry<LocationIdentity> duplicateWithObject(ValueNode newObject) {
            return new LoadCacheEntry(newObject, identity);
        }

        @Override
        public boolean conflicts(LocationIdentity other, ValueNode index, ValueNode array) {
            return identity.equals(other);
        }

        @Override
        public LocationIdentity getIdentity() {
            return identity;
        }
    }

    /**
     * CacheEntry describing an Unsafe memory reference. The memory location and the location
     * identity are separate so both must be considered when looking for optimizable memory
     * accesses.
     */
    public static final class UnsafeLoadCacheEntry extends CacheEntry<ValueNode> {

        private final LocationIdentity locationIdentity;
        private final JavaKind kind;

        public UnsafeLoadCacheEntry(ValueNode object, ValueNode location, LocationIdentity locationIdentity, JavaKind kind) {
            super(object, location);
            assert locationIdentity != null;
            this.locationIdentity = locationIdentity;
            this.kind = kind;
        }

        @Override
        public CacheEntry<ValueNode> duplicateWithObject(ValueNode newObject) {
            return new UnsafeLoadCacheEntry(newObject, identity, locationIdentity, kind);
        }

        @Override
        public boolean conflicts(LocationIdentity other, ValueNode index, ValueNode array) {
            return locationIdentity.equals(other);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + locationIdentity.hashCode();
            result = 31 * result + kind.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof UnsafeLoadCacheEntry) {
                UnsafeLoadCacheEntry other = (UnsafeLoadCacheEntry) obj;
                return super.equals(other) && locationIdentity.equals(other.locationIdentity) && kind == other.kind;
            }
            return false;
        }

        @Override
        public LocationIdentity getIdentity() {
            return locationIdentity;
        }

        @Override
        public String toString() {
            return "UNSAFE:" + super.toString() + " location:" + locationIdentity + " (" + kind + ")";
        }
    }

    public ReadEliminationBlockState() {
        readCache = EconomicMap.create(Equivalence.DEFAULT);
    }

    public ReadEliminationBlockState(ReadEliminationBlockState other) {
        super(other);
        readCache = EconomicMap.create(Equivalence.DEFAULT, other.readCache);
    }

    @Override
    public String toString() {
        return super.toString() + " " + readCache;
    }

    @Override
    public boolean equivalentTo(ReadEliminationBlockState other) {
        return isSubMapOf(readCache, other.readCache);
    }

    public void addCacheEntry(CacheEntry<?> identifier, ValueNode value) {
        readCache.put(identifier, value);
    }

    public ValueNode getCacheEntry(CacheEntry<?> identifier) {
        return readCache.get(identifier);
    }

    /**
     * Kill the cache for memory accesses established so far down the control flow graph.
     * {@code kill} represents a memory kill to location {@code identity}, potentially expressing an
     * array access. This method must implement Java semantic for regular fields, array accesses,
     * volatile operations etc.
     */
    public void killReadCache(@SuppressWarnings("unused") Node kill, LocationIdentity identity, ValueNode index, ValueNode array) {
        if (identity.isAny()) {
            /**
             * Kill all mutable locations.
             */
            Iterator<CacheEntry<?>> iterator = readCache.getKeys().iterator();
            while (iterator.hasNext()) {
                CacheEntry<?> entry = iterator.next();
                if (entry.getIdentity().isMutable()) {
                    iterator.remove();
                }
            }
            return;
        }
        Iterator<CacheEntry<?>> iterator = readCache.getKeys().iterator();
        while (iterator.hasNext()) {
            CacheEntry<?> entry = iterator.next();
            /*
             * We cover multiple cases here but in general index and array can only be !=null for
             * indexed nodes thus the location identity of other accesses (field and object
             * locations) will never be the same and will never alias with array accesses.
             *
             * Unsafe accesses will alias if they are writing to any location.
             */
            if (entry.conflicts(identity, index, array)) {
                iterator.remove();
            }
        }
    }

    public EconomicMap<CacheEntry<?>, ValueNode> getReadCache() {
        return readCache;
    }
}
