/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.code.ValueUtil;

import java.util.Map;
import java.util.Set;

/**
 * Mapping between a location and it's AllocationState.
 */
public class MergedAllocationStateMap {
    /**
     * Internal map maintaining the mapping.
     */
    protected Map<RAValue, AllocationState> internalMap;
    /**
     * Integer describing when was the value inserted,
     * the bigger it is, the newer the value is.
     */
    protected Map<RAValue, Integer> locationTimings;
    /**
     * Prioritized locations are ones made by the register allocator itself.
     * <p>
     * Whenever we are resolving phi variables, these are prioritized
     * because they are likely what the register allocator chose
     * to be used as phi locations.
     * <p>
     * If there's multiple, then time should make the difference.
     */
    protected Map<RAValue, Boolean> prioritizedLocations;
    /**
     * Internal constant for locationTimings.
     */
    protected int time;

    /**
     * Register allocation config describing which registers
     * can be used.
     */
    protected RegisterAllocationConfig registerAllocationConfig;

    public MergedAllocationStateMap(RegisterAllocationConfig registerAllocationConfig) {
        internalMap = new EconomicHashMap<>();
        locationTimings = new EconomicHashMap<>();
        prioritizedLocations = new EconomicHashMap<>();
        time = 0;

        this.registerAllocationConfig = registerAllocationConfig;
    }

    public MergedAllocationStateMap(MergedAllocationStateMap other) {
        internalMap = new EconomicHashMap<>(other.internalMap);
        locationTimings = new EconomicHashMap<>(other.locationTimings);
        prioritizedLocations = new EconomicHashMap<>(other.prioritizedLocations);
        time = other.time + 1;

        registerAllocationConfig = other.registerAllocationConfig;
    }

    public AllocationState get(RAValue key) {
        return this.get(key, AllocationState.getDefault());
    }

    public AllocationState get(RAValue key, AllocationState defaultValue) {
        var state = internalMap.get(key);
        if (state == null) {
            return defaultValue;
        }
        return state;
    }

    public void putAsPrioritized(RAValue key, AllocationState value) {
        this.put(key, value);
        this.prioritizedLocations.put(key, true);
    }

    /**
     * Put a new state for location to the map,
     * while checking if register can be allocated to.
     *
     * @param key   Location used
     * @param state State to store
     */
    public void put(RAValue key, AllocationState state) {
        this.checkRegisterDestinationValidity(key);
        putWithoutRegCheck(key, state);
    }

    /**
     * Put a new state for location to the map,
     * without checking if the register can actually be used.
     * <p>
     * This is useful for registers that are used by the abi
     * in the first label, but can actually never be changed,
     * like rbp.
     * </p>
     *
     * @param key   Location used
     * @param state State to store
     */
    public void putWithoutRegCheck(RAValue key, AllocationState state) {
        locationTimings.put(key, time++);
        internalMap.put(key, state);

        if (prioritizedLocations.containsKey(key)) {
            prioritizedLocations.put(key, false);
        }
    }

    /**
     * Put a copied state to a location, used when merging.
     *
     * @param key   Location used
     * @param state State to store
     */
    public void putClone(RAValue key, AllocationState state) {
        if (state.isUnknown()) {
            this.put(key, state);
            return;
        }

        this.put(key, state.clone());
    }

    public void putCloneAsPrioritized(RAValue key, AllocationState value) {
        this.putClone(key, value);
        this.prioritizedLocations.put(key, true);
    }

    public boolean isPrioritized(RAValue key) {
        return prioritizedLocations.containsKey(key);
    }

    /**
     * Get time when the location was inserted.
     *
     * @param key Location used
     * @return integer describing the insertion time - bigger = newer.
     */
    public int getKeyTime(RAValue key) {
        var time = locationTimings.get(key);
        assert time != null : "Time for key " + key + " not present.";
        return time;
    }

    /**
     * Get set of locations holding this particular variable/constant.
     *
     * @param value Symbol we are looking for
     * @return Set of locations that hold said symbol
     */
    public Set<RAValue> getValueLocations(RAValue value) {
        Set<RAValue> locations = new EconomicHashSet<>();
        for (var entry : this.internalMap.entrySet()) {
            if (entry.getValue() instanceof ValueAllocationState valState) {
                if (valState.getRAValue().equals(value)) {
                    locations.add(entry.getKey());
                }
            }
        }
        return locations;
    }

    /**
     * Merge two maps together, source is generally
     * the predecessor to the current block (this state map).
     *
     * @param source Predecessor merging to here
     * @return Was this map changed?
     */
    public boolean mergeWith(MergedAllocationStateMap source) {
        boolean changed = false;
        for (var entry : source.internalMap.entrySet()) {
            if (!this.internalMap.containsKey(entry.getKey())) {
                changed = true;

                this.putWithoutRegCheck(entry.getKey(), UnknownAllocationState.INSTANCE);
            }

            var currentValue = this.internalMap.get(entry.getKey());
            var result = this.internalMap.get(entry.getKey()).meet(entry.getValue());
            if (!currentValue.equals(result)) {
                changed = true;
            }

            this.putWithoutRegCheck(entry.getKey(), result);
        }

        return changed;
    }

    /**
     * Check if register can be used by the register allocator.
     * If not allowed, an exception is thrown.
     *
     * @param raLocation Value that could be a register.
     */
    protected void checkRegisterDestinationValidity(RAValue raLocation) {
        var location = raLocation.getValue();
        if (!ValueUtil.isRegister(location)) {
            return;
        }

        // Equality check so we know that this change was made by the register allocator.
        var register = ValueUtil.asRegister(location);
        if (!this.registerAllocationConfig.getAllocatableRegisters().contains(register)) {
            throw new InvalidRegisterUsedException(register);
        }
    }
}
