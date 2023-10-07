package com.oracle.graal.pointsto.reports.causality.events;

public final class ConfigurationCondition extends CausalityEvent {
    public final String typeName;

    ConfigurationCondition(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public boolean essential() {
        return false;
    }

    @Override
    public String toString() {
        return typeName + " [Configuration Condition]";
    }
}
