/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.graph;

import static java.lang.reflect.Modifier.*;

import java.util.*;

import com.sun.c1x.ir.*;
import com.sun.cri.ri.*;

/**
 * The {@code MemoryMap} class is an approximation of memory that is used redundant load and
 * store elimination. In C1, tracking of fields of new objects' fields was precise,
 * while tracking of other fields is managed at the offset granularity (i.e. a write of a field with offset
 * {@code off} will "overwrite" all fields with the offset {@code off}. However, C1X distinguishes all
 * loaded fields as separate locations. Static fields have just one location, while instance fields are
 * tracked for at most one instance object. Loads or stores of unloaded fields kill all memory locations.
 * An object is no longer "new" if it is stored into a field or array.
 *
 * @author Ben L. Titzer
 */
public class MemoryMap {

    private final HashMap<RiField, Value> objectMap = new HashMap<RiField, Value>();
    private final HashMap<RiField, Value> valueMap = new HashMap<RiField, Value>();
    private final IdentityHashMap<Value, Value> newObjects = new IdentityHashMap<Value, Value>();

    /**
     * Kills all memory locations.
     */
    public void kill() {
        objectMap.clear();
        valueMap.clear();
        newObjects.clear();
    }

    /**
     * The specified instruction has just escaped, it can no longer be considered a "new object".
     * @param x the instruction that just escaped
     */
    public void storeValue(Value x) {
        newObjects.remove(x);
    }

    /**
     * Record a newly allocated object.
     * @param n the instruction generating the new object
     */
    public void newInstance(NewInstance n) {
        newObjects.put(n, n);
    }

    /**
     * Look up a load for load elimination, and put this load into the load elimination map.
     * @param load the instruction representing the load
     * @return a reference to the previous instruction that already loaded the value, if it is available; the
     * {@code load} parameter otherwise
     */
    public Value load(LoadField load) {
        if (!load.isLoaded()) {
            // the field is not loaded, kill everything, because it will need to be resolved
            kill();
            return load;
        }
        RiField field = load.field();
        if (load.isStatic()) {
            // the field is static, look in the static map
            Value r = valueMap.get(field);
            if (r != null) {
                return r;
            }
            valueMap.put(field, load);
        } else {
            // see if the value for this object for this field is in the map
            if (objectMap.get(field) == load.object()) {
                return valueMap.get(field);
            }
            objectMap.put(field, load.object());
            valueMap.put(field, load);
        }

        return load; // load cannot be eliminated
    }

    /**
     * Insert a new result for a load into the memory map.
     * @param load the load instruction
     * @param result the result that the load instruction should produce
     */
    public void setResult(LoadField load, Value result) {
        if (load.isLoaded()) {
            RiField field = load.field();
            if (load.isStatic()) {
                // the field is static, put it in the static map
                valueMap.put(field, result);
            } else {
                // put the result for the loaded object into the map
                objectMap.put(field, load.object());
                valueMap.put(field, result);
            }
        }
    }

    /**
     * Look up a store for store elimination, and put this store into the load elimination map.
     * @param store the store instruction to put into the map
     * @return {@code null} if the store operation is redundant; the {@code store} parameter
     * otherwise
     */
    public StoreField store(StoreField store) {
        if (!store.isLoaded()) {
            // the field is not loaded, kill everything, because it will need to be resolved
            kill();
            return store;
        }
        RiField field = store.field();
        Value value = store.value();
        if (store.isStatic()) {
            // the field is static, overwrite it into the static map
            valueMap.put(field, value);
        } else {
            if (newObjects.containsKey(store.object())) {
                // this is a store to a new object's field
                if (fieldHasNoStores(field) && value.isConstant() && value.asConstant().isDefaultValue()) {
                    // this is a redundant initialization of a new object's field that has not been assigned to
                    return null;
                }
            }
            Value obj = objectMap.get(field);
            if (obj == store.object()) {
                // is this a redundant store?
                if (value == valueMap.get(field) && !isVolatile(field.accessFlags())) {
                    return null;
                }
            }
            objectMap.put(field, store.object());
            valueMap.put(field, value);
        }
        storeValue(value); // the value stored just escaped
        return store; // the store cannot be eliminated
    }

    private boolean fieldHasNoStores(RiField field) {
        return objectMap.get(field) == null;
    }
}
