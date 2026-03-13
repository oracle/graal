package com.oracle.svm.hosted.analysis.ai.domain.memory;

import java.util.Objects;

/**
 * Lightweight abstraction of a program variable used as an environment key.
 */
public record Var(Var.Kind kind, String name) {

    public enum Kind {LOCAL, PARAM, TEMP}

    public Var(Kind kind, String name) {
        this.kind = Objects.requireNonNull(kind);
        this.name = Objects.requireNonNull(name);
    }

    public static Var local(String name) {
        return new Var(Kind.LOCAL, name);
    }

    public static Var param(String name) {
        return new Var(Kind.PARAM, name);
    }

    public static Var temp(String name) {
        return new Var(Kind.TEMP, name);
    }

    @Override
    public String toString() {
        return kind + ":" + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Var(Kind kind1, String name1))) return false;
        return kind == kind1 && name.equals(name1);
    }

}
