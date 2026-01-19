package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DefinitionSet {
    protected Set<String> internalSet;
    protected Map<String, Value> valueMap;

    public DefinitionSet() {
        this.internalSet = new HashSet<>();
        this.valueMap = new HashMap<>();
    }

    public DefinitionSet(DefinitionSet defSet) {
        this.internalSet = new HashSet<>(defSet.internalSet);
        this.valueMap = new HashMap<>(defSet.valueMap);
    }

    public void add(Value value) {
        if (Value.ILLEGAL.equals(value)) {
            return;
        }

        String valueString = this.getValueKeyString(value);
        this.valueMap.put(valueString, value);
        this.internalSet.add(valueString);
    }

    public boolean contains(Value value) {
        String valueString = this.getValueKeyString(value);
        return this.internalSet.contains(valueString);
    }

    protected String getValueKeyString(Value value) {
        if (value instanceof RegisterValue regValue) {
            return regValue.getRegister().toString();
        }

        if (LIRValueUtil.isVariable(value)) {
            return "v" + LIRValueUtil.asVariable(value).index;
        }

        return value.toString();
    }
}
