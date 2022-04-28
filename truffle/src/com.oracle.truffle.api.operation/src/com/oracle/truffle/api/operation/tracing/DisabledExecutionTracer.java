package com.oracle.truffle.api.operation.tracing;

import java.io.PrintWriter;

import com.oracle.truffle.api.operation.OperationsNode;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

class DisabledExecutionTracer extends ExecutionTracer {

    DisabledExecutionTracer() {
        super(null);
    }

    @Override
    public void startFunction(OperationsNode node) {
    }

    @Override
    public void endFunction() {
    }

    @Override
    public void traceInstruction(int bci, String id, int instructionType, Object... arguments) {
    }

    @Override
    public void traceSpecialization(int bci, String id, int specializationId, Object... arguments) {
    }

    @Override
    public Object tracePop(Object value) {
        return value;
    }

    @Override
    public Object tracePush(Object value) {
        return value;
    }

    @Override
    public void traceException(Throwable ex) {
    }

    @Override
    public void dump(PrintWriter writer) {
    }

    @Override
    public JSONObject serializeState() {
        throw new AssertionError("should never be called");
    }

    @Override
    public JSONArray createDecisions() {
        throw new AssertionError("should never be called");
    }

}
