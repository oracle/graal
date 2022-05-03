package com.oracle.truffle.api.operation.tracing;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.operation.tracing.OperationsStatistics.DisabledExecutionTracer;

// per-context per-ops per-thread
public abstract class ExecutionTracer {
    static final Map<Class<?>, String> DECISIONS_FILE_MAP = new HashMap<>();
    static final Map<Class<?>, String[]> INSTR_NAMES_MAP = new HashMap<>();
    static final Map<Class<?>, String[][]> SPECIALIZATION_NAMES_MAP = new HashMap<>();

    public static ExecutionTracer get(Class<?> operationsClass) {
        OperationsStatistics stats = OperationsStatistics.STATISTICS.get();
        if (stats == null) {
            return DisabledExecutionTracer.INSTANCE;
        } else {
            return stats.getStatsistics(operationsClass).getTracer();
        }
    }

    public static void initialize(Class<?> opsClass, String decisionsFile, String[] instrNames, String[][] specNames) {
        DECISIONS_FILE_MAP.put(opsClass, decisionsFile);
        INSTR_NAMES_MAP.put(opsClass, instrNames);
        SPECIALIZATION_NAMES_MAP.put(opsClass, specNames);
    }

    public abstract void startFunction(Node node);

    public abstract void endFunction(Node node);

    public abstract void traceInstruction(int bci, int id);

    public abstract void traceActiveSpecializations(int bci, int id, boolean[] activeSpecializations);

    public abstract void traceSpecialization(int bci, int id, int specializationId, Object... values);
}