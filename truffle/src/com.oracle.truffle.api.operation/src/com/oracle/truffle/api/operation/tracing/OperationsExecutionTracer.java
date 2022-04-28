package com.oracle.truffle.api.operation.tracing;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.operation.OperationsNode;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

final class OperationsExecutionTracer extends ExecutionTracer {

    private static final String KEY_KEY = "key";
    private static final String KEY_ACTIVE_SPECIALIZATION_OCCURENCES = "activeSpecializations";
    private static final String KEY_COUNT = "count";

    private static class ActiveSpecializationOccurence {
        private static final String KEY_INSTRUCTION_ID = "i";
        private static final String KEY_ACTIVE_SPECIALIZATIONS = "a";

        private static final String KEY_LENGTH = "l";

        static ActiveSpecializationOccurence deserializeState(JSONObject o) {
            String instructionId = o.getString(KEY_INSTRUCTION_ID);
            int len = o.getInt(KEY_LENGTH);

            boolean[] activeSpecializations = new boolean[len];

            JSONArray arr = o.getJSONArray(KEY_ACTIVE_SPECIALIZATIONS);
            for (int i = 0; i < arr.length(); i++) {
                int idx = arr.getInt(i);
                activeSpecializations[idx] = true;
            }

            return new ActiveSpecializationOccurence(instructionId, activeSpecializations);
        }

        final String instructionId;

        final boolean[] activeSpecializations;

        ActiveSpecializationOccurence(String instructionId, boolean... activeSpecializations) {
            this.instructionId = instructionId;
            this.activeSpecializations = activeSpecializations;
        }

        JSONObject createDecision(String[] specNames) {
            assert specNames.length >= activeSpecializations.length : instructionId + "should have at least" + activeSpecializations.length + " specializations, but has " + specNames.length;
            JSONObject result = new JSONObject();
            result.put("type", "Quicken");
            assert instructionId.startsWith("c.");
            result.put("operation", instructionId.substring(2)); // drop the `c.` prefix

            JSONArray specs = new JSONArray();

            StringBuilder id = new StringBuilder("q-");
            id.append(instructionId.substring(2));

            for (int i = 0; i < activeSpecializations.length; i++) {
                if (activeSpecializations[i]) {
                    specs.put(specNames[i]);
                    id.append('-').append(specNames[i]);
                }
            }

            result.put("specializations", specs);
            result.put("id", id);

            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ActiveSpecializationOccurence other = (ActiveSpecializationOccurence) obj;
            return Arrays.equals(activeSpecializations, other.activeSpecializations) && instructionId.equals(other.instructionId);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(activeSpecializations);
            result = prime * result + Objects.hash(instructionId);
            return result;
        }

        JSONObject serializeState() {
            JSONObject result = new JSONObject();
            result.put(KEY_INSTRUCTION_ID, instructionId);
            result.put(KEY_LENGTH, activeSpecializations.length);

            JSONArray arr = new JSONArray();
            int i = 0;
            for (boolean act : activeSpecializations) {
                if (act) {
                    arr.put(i);
                }
                i++;
            }
            result.put(KEY_ACTIVE_SPECIALIZATIONS, arr);

            return result;
        }

        @Override
        public String toString() {
            return "ActiveSpecializationOccurence[instructionId=" + instructionId + ", activeSpecializations=" + Arrays.toString(activeSpecializations) + "]";
        }
    }

    private static class InstructionSequence {
        final int hash;
        final int[] instrs;

        public InstructionSequence(int[] instrs) {
            this.instrs = instrs;
            int h = 0;
            for (int i : instrs) {
                h = h * 31 + i;
            }
            hash = h;
        }

        public InstructionSequence add(int next) {
            int[] created = new int[instrs.length];
            System.arraycopy(instrs, 1, created, 0, instrs.length - 1);
            created[created.length - 1] = next;
            return new InstructionSequence(created);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof InstructionSequence))
                return false;
            InstructionSequence other = (InstructionSequence) obj;
            if (other.hash != hash || instrs.length != other.instrs.length) {
                return false;
            }
            for (int i = 0; i < instrs.length; i++) {
                if (instrs[i] != other.instrs[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        public boolean isValid() {
            for (int i : instrs) {
                if (i == 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (int i = 0; i < instrs.length; i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(String.format("%3d", instrs[i]));
            }
            sb.append(']');
            return sb.toString();
        }
    }

    private static final int TRACE_LENGTH = 8;

    private static final long score(Map.Entry<InstructionSequence, Long> ent) {
        return ent.getValue() * ent.getKey().instrs.length;
    }

    private final Map<InstructionSequence, Long> occurences = new HashMap<>();
    private final Map<ActiveSpecializationOccurence, Long> activeSpecializationsMap = new HashMap<>();
    private InstructionSequence[] last = new InstructionSequence[TRACE_LENGTH - 1];

    OperationsExecutionTracer(String key) {
        super(key);
    }

    @Override
    public JSONArray createDecisions() {
        int numQuicken = 10;

        JSONArray result = new JSONArray();

        activeSpecializationsMap.entrySet().stream() //
                        .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue())) //
                        .limit(numQuicken) //
                        .forEachOrdered(e -> {
                            result.put(e.getKey().createDecision(specializationNames.get(e.getKey().instructionId)));
                        });

        return result;
    }

    @Override
    public final void dump(PrintWriter writer) {
        writer.println("-------------------------------------------------------------");
        activeSpecializationsMap.entrySet().stream() //
                        .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue())) //
                        .limit(50) //
                        .forEachOrdered(e -> {
                            writer.printf(" %s: %d%n", e.getKey(), e.getValue());
                        });
        writer.println("-------------------------------------------------------------");
        writer.flush();
    }

    @Override
    public JSONObject serializeState() {
        JSONObject result = new JSONObject();
        result.put(KEY_KEY, key);

        JSONArray arr = new JSONArray();
        for (Map.Entry<ActiveSpecializationOccurence, Long> entry : activeSpecializationsMap.entrySet()) {
            ActiveSpecializationOccurence as = entry.getKey();

            JSONObject o = as.serializeState();
            o.put(KEY_COUNT, entry.getValue());

            arr.put(o);
        }

        result.put(KEY_ACTIVE_SPECIALIZATION_OCCURENCES, arr);
        return result;
    }

    public static OperationsExecutionTracer deserializeState(JSONObject tracer) {
        String key = tracer.getString(KEY_KEY);
        OperationsExecutionTracer result = new OperationsExecutionTracer(key);

        JSONArray arr = tracer.getJSONArray(KEY_ACTIVE_SPECIALIZATION_OCCURENCES);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);

            long count = o.getLong(KEY_COUNT);
            o.remove(KEY_COUNT);

            ActiveSpecializationOccurence as = ActiveSpecializationOccurence.deserializeState(o);
            result.activeSpecializationsMap.put(as, count);
        }

        return result;
    }

    private final void resetLast() {
        for (int i = 2; i <= TRACE_LENGTH; i++) {
            last[i - 2] = new InstructionSequence(new int[i]);
        }
    }

    @Override
    @TruffleBoundary
    public final void startFunction(OperationsNode node) {
        resetLast();
    }

    @Override
    @TruffleBoundary
    public final void endFunction() {
        resetLast();
    }

    @Override
    public void traceException(Throwable ex) {
    }

    @Override
    @TruffleBoundary
    public void traceInstruction(int bci, String id, int instructionType, Object... arguments) {
        if (instructionType == INSTRUCTION_TYPE_CUSTOM) {
            assert arguments.length == 1;
            boolean[] activeSpecs = (boolean[]) arguments[0];

            ActiveSpecializationOccurence occ = new ActiveSpecializationOccurence(id, activeSpecs);
            Long cur = activeSpecializationsMap.get(occ);
            long next = cur == null ? 1 : cur + 1;
            activeSpecializationsMap.put(occ, next);
        }
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
    @TruffleBoundary
    public void traceSpecialization(int bci, String id, int specializationId, Object... arguments) {
    }

}