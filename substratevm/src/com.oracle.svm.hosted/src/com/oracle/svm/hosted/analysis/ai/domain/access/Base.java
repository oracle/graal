package com.oracle.svm.hosted.analysis.ai.domain.access;

import java.util.Objects;

public class Base {
    private final String var;
    private final String type;

    public Base(String var, String type) {
        this.var = var;
        this.type = type;
    }

    public String getVar() {
        return var;
    }

    public String getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Base base = (Base) o;
        return Objects.equals(var, base.var) && Objects.equals(type, base.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(var, type);
    }

    @Override
    public String toString() {
        return "Base{" +
                "var='" + var + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}