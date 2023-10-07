package com.oracle.graal.pointsto.reports.causality.events;

import com.oracle.graal.pointsto.meta.AnalysisField;

public final class FieldRead extends CausalityEvent {
    public final AnalysisField field;

    FieldRead(AnalysisField field) {
        this.field = field;
    }

    @Override
    public String toString() {
        return field.format("%H.%n [Read]");
    }
}
