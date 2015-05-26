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

import com.oracle.jvmci.meta.LocationIdentity;
import java.util.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.virtual.*;

public class PEReadEliminationBlockState extends PartialEscapeBlockState<PEReadEliminationBlockState> {

    final HashMap<ReadCacheEntry, ValueNode> readCache;

    static final class ReadCacheEntry {

        public final LocationIdentity identity;
        public final ValueNode object;
        public final int index;

        public ReadCacheEntry(LocationIdentity identity, ValueNode object, int index) {
            this.identity = identity;
            this.object = object;
            this.index = index;
        }

        @Override
        public int hashCode() {
            int result = 31 + ((identity == null) ? 0 : identity.hashCode());
            result = 31 * result + ((object == null) ? 0 : object.hashCode());
            return result * 31 + index;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ReadCacheEntry)) {
                return false;
            }
            ReadCacheEntry other = (ReadCacheEntry) obj;
            return identity.equals(other.identity) && object == other.object;
        }

        @Override
        public String toString() {
            return index == -1 ? (object + ":" + identity) : (object + "[" + index + "]:" + identity);
        }
    }

    public PEReadEliminationBlockState() {
        readCache = CollectionsFactory.newMap();
    }

    public PEReadEliminationBlockState(PEReadEliminationBlockState other) {
        super(other);
        readCache = CollectionsFactory.newMap(other.readCache);
    }

    @Override
    public String toString() {
        return super.toString() + " " + readCache;
    }

    @Override
    protected void objectMaterialized(VirtualObjectNode virtual, AllocatedObjectNode representation, List<ValueNode> values) {
        if (virtual instanceof VirtualInstanceNode) {
            VirtualInstanceNode instance = (VirtualInstanceNode) virtual;
            for (int i = 0; i < instance.entryCount(); i++) {
                readCache.put(new ReadCacheEntry(instance.field(i).getLocationIdentity(), representation, -1), values.get(i));
            }
        }
    }

    @Override
    public boolean equivalentTo(PEReadEliminationBlockState other) {
        if (!compareMapsNoSize(readCache, other.readCache)) {
            return false;
        }
        return super.equivalentTo(other);
    }

    public void addReadCache(ValueNode object, LocationIdentity identity, int index, ValueNode value, PartialEscapeClosure<?> closure) {
        ValueNode cacheObject;
        ObjectState obj = closure.getObjectState(this, object);
        if (obj != null) {
            assert !obj.isVirtual();
            cacheObject = obj.getMaterializedValue();
        } else {
            cacheObject = object;
        }
        readCache.put(new ReadCacheEntry(identity, cacheObject, index), value);
    }

    public ValueNode getReadCache(ValueNode object, LocationIdentity identity, int index, PartialEscapeClosure<?> closure) {
        ValueNode cacheObject;
        ObjectState obj = closure.getObjectState(this, object);
        if (obj != null) {
            assert !obj.isVirtual() : object;
            cacheObject = obj.getMaterializedValue();
        } else {
            cacheObject = object;
        }
        ValueNode cacheValue = readCache.get(new ReadCacheEntry(identity, cacheObject, index));
        obj = closure.getObjectState(this, cacheValue);
        if (obj != null) {
            assert !obj.isVirtual();
            cacheValue = obj.getMaterializedValue();
        } else {
            // assert !scalarAliases.containsKey(cacheValue);
            cacheValue = closure.getScalarAlias(cacheValue);
        }
        return cacheValue;
    }

    public void killReadCache() {
        readCache.clear();
    }

    public void killReadCache(LocationIdentity identity, int index) {
        Iterator<Map.Entry<ReadCacheEntry, ValueNode>> iter = readCache.entrySet().iterator();
        while (iter.hasNext()) {
            ReadCacheEntry entry = iter.next().getKey();
            if (entry.identity.equals(identity) && (index == -1 || entry.index == -1 || index == entry.index)) {
                iter.remove();
            }
        }
    }

    public Map<ReadCacheEntry, ValueNode> getReadCache() {
        return readCache;
    }
}
