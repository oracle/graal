package com.oracle.truffle.api.dsl.internal;

/**
 * This is NOT public API. Do not use directly. This code may change without notice.
 */
public final class DSLMetadata {

    public static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[]{};
    public static final DSLMetadata NONE = new DSLMetadata(null, EMPTY_CLASS_ARRAY, EMPTY_CLASS_ARRAY, EMPTY_CLASS_ARRAY, 0, 0);

    private final Class<?> specializationClass;
    private final Class<?>[] includes;
    private final Class<?>[] excludedBy;
    private final Class<?>[] specializedTypes;

    private final int costs;
    private final int order;

    public DSLMetadata(Class<?> specializationClass, Class<?>[] includes, Class<?>[] excludes, Class<?>[] specializedTypes, int costs, int order) {
        this.specializationClass = specializationClass;
        this.includes = includes;
        this.excludedBy = excludes;
        this.specializedTypes = specializedTypes;
        this.costs = costs;
        this.order = order;
    }

    public Class<?> getSpecializationClass() {
        return specializationClass;
    }

    public Class<?>[] getSpecializedTypes() {
        return specializedTypes;
    }

    Class<?>[] getIncludes() {
        return includes;
    }

    Class<?>[] getExcludedBy() {
        return excludedBy;
    }

    int getCosts() {
        return costs;
    }

    int getOrder() {
        return order;
    }
}
