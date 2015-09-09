package com.oracle.graal.microbenchmarks.graal.util;

import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.schedule.SchedulePhase.SchedulingStrategy;

public class ScheduleState extends GraphState {

    public SchedulePhase schedule;

    private final SchedulingStrategy selectedStrategy;

    public ScheduleState(SchedulingStrategy selectedStrategy) {
        this.selectedStrategy = selectedStrategy;
    }

    public ScheduleState() {
        this(SchedulingStrategy.LATEST_OUT_OF_LOOPS);
    }

    @Override
    public void beforeInvocation() {
        schedule = new SchedulePhase(selectedStrategy);
        super.beforeInvocation();
    }
}
