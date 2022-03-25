package org.graalvm.bisect.util;

public class Pair<T> {
    public Pair(T lhs, T rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    public T getLhs() {
        return lhs;
    }

    public T getRhs() {
        return rhs;
    }

    public boolean bothNotNull() {
        return rhs != null && lhs != null;
    }

    private final T lhs;
    private final T rhs;

}
