package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.Value;

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
    protected Map<String, Value> valueMap;
    protected Map<String, AllocationState> internalMap;
    protected Map<String, Integer> locationTimings;
    /**
     * Prioritized locations are ones made by the register allocator itself.
     * <p>
     * Whenever we are resolving phi variables, these are prioritized
     * because they are likely what the register allocator chose
     * to be used as phi locations.
     * <p>
     * If there's multiple, then time should make the difference.
     */
    protected Map<String, Boolean> prioritizedLocations;
    protected int time;

    protected RegisterAllocationConfig registerAllocationConfig;

    public MergedAllocationStateMap(RegisterAllocationConfig registerAllocationConfig) {
        valueMap = new EconomicHashMap<>();
        internalMap = new EconomicHashMap<>();
        locationTimings = new EconomicHashMap<>();
        prioritizedLocations = new EconomicHashMap<>();
        time = 0;

        this.registerAllocationConfig = registerAllocationConfig;
    }

    public MergedAllocationStateMap(MergedAllocationStateMap other) {
        valueMap = new EconomicHashMap<>(other.valueMap);
        internalMap = new EconomicHashMap<>(other.internalMap);
        locationTimings = new EconomicHashMap<>(other.locationTimings);
        prioritizedLocations = new EconomicHashMap<>(other.prioritizedLocations);
        time = other.time + 1;

        registerAllocationConfig = other.registerAllocationConfig;
    }

    public AllocationState get(Value key) {
        return this.get(key, AllocationState.getDefault());
    }

    public AllocationState get(Value key, AllocationState defaultValue) {
        String keyString = this.getValueKeyString(key);
        var state = internalMap.get(keyString);
        if (state == null) {
            return defaultValue;
        }
        return state;
    }

    public AllocationState get(Register register) {
        return this.get(register.asValue());
    }

    public void putAsPrioritized(Value key, AllocationState value) {
        this.put(key, value);
        this.prioritizedLocations.put(this.getValueKeyString(key), true);
    }

    public void put(Register reg, AllocationState value) {
        this.put(reg.asValue(), value);
    }

    public void put(Value key, AllocationState state) {
        this.checkRegisterDestinationValidity(key);
        putWithoutRegCheck(key, state);
    }

    public void putWithoutRegCheck(Value key, AllocationState state) {
        String keyString = this.getValueKeyString(key);
        locationTimings.put(keyString, time++);
        internalMap.put(keyString, state);
        valueMap.put(keyString, key);

        if (prioritizedLocations.containsKey(keyString)) {
            prioritizedLocations.put(keyString, false);
        }
    }

    public void putClone(Value key, AllocationState value) {
        if (value.isUnknown()) {
            this.put(key, value);
            return;
        }

        this.put(key, value.clone());
    }

    public void putClone(Register reg, AllocationState value) {
        this.put(reg.asValue(), value.clone());
    }

    public void putCloneAsPrioritized(Value key, AllocationState value) {
        this.putClone(key, value);
        this.prioritizedLocations.put(this.getValueKeyString(key), true);
    }

    public boolean isPrioritized(Value key) {
        String keyString = this.getValueKeyString(key);
        return prioritizedLocations.containsKey(keyString);
    }

    public int getKeyTime(Value key) {
        String keyString = this.getValueKeyString(key);
        var time = locationTimings.get(keyString);
        assert time != null : "Time for key " + keyString + " not present.";
        return time;
    }

    public Set<Value> getValueLocations(Value value) {
        Set<Value> locations = new EconomicHashSet<>();
        for (var entry : this.internalMap.entrySet()) {
            if (entry.getValue() instanceof ValueAllocationState valState) {
                if (valState.getValue().equals(value)) {
                    var location = this.valueMap.get(entry.getKey());
                    assert location != null : "Value not present in ValueMap: " + entry.getKey();
                    locations.add(location);
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

                this.putWithoutRegCheck(source.valueMap.get(entry.getKey()), UnknownAllocationState.INSTANCE);
            }

            var currentValue = this.internalMap.get(entry.getKey());
            var result = this.internalMap.get(entry.getKey()).meet(entry.getValue());
            if (!currentValue.equals(result)) {
                changed = true;
            }

            this.putWithoutRegCheck(source.valueMap.get(entry.getKey()), result);
        }

        return changed;
    }

    protected String getValueKeyString(Value value) {
        if (value instanceof RegisterValue regValue) {
            return regValue.getRegister().toString();
        }

        assert !Value.ILLEGAL.equals(value) : "Cannot use ILLEGAL as key in AllocationStateMap";

        return value.toString();
    }

    protected void checkRegisterDestinationValidity(Value location) {
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
