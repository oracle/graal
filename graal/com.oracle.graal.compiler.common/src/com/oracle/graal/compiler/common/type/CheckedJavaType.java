package com.oracle.graal.compiler.common.type;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class CheckedJavaType {
    private final ResolvedJavaType type;
    private final boolean exactType;

    private CheckedJavaType(ResolvedJavaType type, boolean exactType) {
        this.type = type;
        this.exactType = exactType;
    }

    public static CheckedJavaType create(Assumptions assumptions, ResolvedJavaType type) {
        ResolvedJavaType exactType = type.asExactType();
        if (exactType == null) {
            Assumptions.AssumptionResult<ResolvedJavaType> leafConcreteSubtype = type.findLeafConcreteSubtype();
            if (leafConcreteSubtype != null && leafConcreteSubtype.canRecordTo(assumptions)) {
                leafConcreteSubtype.recordTo(assumptions);
                exactType = leafConcreteSubtype.getResult();
            }
        }
        if (exactType == null) {
            return new CheckedJavaType(type, false);
        }
        return new CheckedJavaType(exactType, true);
    }

    public ResolvedJavaType getType() {
        return type;
    }

    public boolean isExactType() {
        return exactType;
    }

}
