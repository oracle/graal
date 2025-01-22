package com.oracle.svm.hosted.analysis.ai.domain.access;

import java.util.Objects;

public class Access {
    private final String field;

    public Access(String field) {
        this.field = field;
    }

    public String getField() {
        return field;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Access access = (Access) o;
        return Objects.equals(field, access.field);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(field);
    }

    @Override
    public String toString() {
        return "Access{" +
                "field='" + field + '\'' +
                '}';
    }
}
