package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.code.ValueUtil;

import java.util.Map;
import java.util.Set;

public class MergedAllocationStateMap {
    /**
     * These are instances of Value we need to keep for getValueLocations,
     * indexed by their string representation.
     * <p>
     * Because Values have their own toString implementation,
     * that the hash map uses and for some Values we do not
     * want this (especially due to kinds), which are irrelevant
     * for location indices, we have a getValueKeyString which
     * does what we need.
     */
    protected Map<RAValue, AllocationState> internalMap;
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
    protected int time;

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

    public void put(RAValue key, AllocationState state) {
        this.checkRegisterDestinationValidity(key);
        putWithoutRegCheck(key, state);
    }

    public void putWithoutRegCheck(RAValue key, AllocationState state) {
        locationTimings.put(key, time++);
        internalMap.put(key, state);

        if (prioritizedLocations.containsKey(key)) {
            prioritizedLocations.put(key, false);
        }
    }

    public void putClone(RAValue key, AllocationState value) {
        if (value.isUnknown()) {
            this.put(key, value);
            return;
        }

        this.put(key, value.clone());
    }

    public void putCloneAsPrioritized(RAValue key, AllocationState value) {
        this.putClone(key, value);
        this.prioritizedLocations.put(key, true);
    }

    public boolean isPrioritized(RAValue key) {
        return prioritizedLocations.containsKey(key);
    }

    public int getKeyTime(RAValue key) {
        var time = locationTimings.get(key);
        assert time != null : "Time for key " + key + " not present.";
        return time;
    }

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
