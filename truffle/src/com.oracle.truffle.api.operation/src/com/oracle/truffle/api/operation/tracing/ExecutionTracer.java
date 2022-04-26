package com.oracle.truffle.api.operation.tracing;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.operation.OperationsNode;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import com.oracle.truffle.tools.utils.json.JSONTokener;

public class ExecutionTracer {
    private static final Map<String, ExecutionTracer> TRACERS = new HashMap<>();

    private static final String KEY_TRACERS = "tracers";
    private static final String KEY_KEY = "key";
    private static final String KEY_ACTIVE_SPECIALIZATION_OCCURENCES = "activeSpecializations";
    private static final String KEY_COUNT = "count";

    public static ExecutionTracer get(String key) {
        return TRACERS.computeIfAbsent(key, ExecutionTracer::new);
    }

    private final String key;
    private String outputPath;
    private Map<String, String[]> specializationNames = new HashMap<>();

    private ExecutionTracer(String key) {
        assert TRACERS.get(key) == null;
        this.key = key;
    }

    static {

        // deser
        String stateFile = "/tmp/state.json";

        try {
            FileInputStream fi = new FileInputStream(new File(stateFile));
            JSONTokener tok = new JSONTokener(fi);
            JSONObject o = new JSONObject(tok);

            JSONArray tracers = o.getJSONArray("tracers");

            for (int i = 0; i < tracers.length(); i++) {
                JSONObject tracer = tracers.getJSONObject(i);
                ExecutionTracer tr = ExecutionTracer.deserializeState(tracer);
                TRACERS.put(tr.key, tr);
            }
        } catch (FileNotFoundException ex) {
            System.err.println("not found");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (OutputStreamWriter fo = new OutputStreamWriter(new FileOutputStream(new File(stateFile)))) {
                JSONObject result = new JSONObject();
                JSONArray tracers = new JSONArray();

                for (Map.Entry<String, ExecutionTracer> ent : TRACERS.entrySet()) {
                    ExecutionTracer tracer = ent.getValue();
                    tracers.put(tracer.serializeState());
                    tracer.dump(new PrintWriter(System.out));
                    System.out.flush();
                }

                result.put(KEY_TRACERS, tracers);
                fo.append(result.toString(2));

            } catch (Exception e) {
                // failing to write the state is a critical exception
                throw new RuntimeException(e);
            }

            for (Map.Entry<String, ExecutionTracer> ent : TRACERS.entrySet()) {
                ExecutionTracer tracer = ent.getValue();
                try (OutputStreamWriter fo = new OutputStreamWriter(new FileOutputStream(tracer.outputPath))) {
                    JSONArray decisions = tracer.createDecisions();
                    fo.append(decisions.toString(2));
                } catch (Exception e) {
                    // failing to write the decisions is not as big of an exception
                    e.printStackTrace();
                }
            }
        }));
    }

    public static final int INSTRUCTION_TYPE_OTHER = 0;
    public static final int INSTRUCTION_TYPE_BRANCH = 1;
    public static final int INSTRUCTION_TYPE_BRANCH_COND = 2;
    public static final int INSTRUCTION_TYPE_LOAD_LOCAL = 3;
    public static final int INSTRUCTION_TYPE_STORE_LOCAL = 4;
    public static final int INSTRUCTION_TYPE_LOAD_ARGUMENT = 5;
    public static final int INSTRUCTION_TYPE_LOAD_CONSTANT = 6;
    public static final int INSTRUCTION_TYPE_RETURN = 7;
    public static final int INSTRUCTION_TYPE_CUSTOM = 8;

    private static final int TRACE_LENGTH = 8;

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

        public boolean isValid() {
            for (int i : instrs) {
                if (i == 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hash;
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

    final Map<InstructionSequence, Long> occurences = new HashMap<>();
    InstructionSequence[] last = new InstructionSequence[TRACE_LENGTH - 1];

    private static class SpecializationOccurence {
        final int instructionId;
        final int specializationId;

        SpecializationOccurence(int instructionId, int specializationId) {
            this.instructionId = instructionId;
            this.specializationId = specializationId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(instructionId, specializationId);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SpecializationOccurence other = (SpecializationOccurence) obj;
            return instructionId == other.instructionId && specializationId == other.specializationId;
        }

        @Override
        public String toString() {
            return "SpecializationOccurence [instructionId=" + instructionId + ", specializationId=" + specializationId + "]";
        }
    }

    private static class ActiveSpecializationOccurence {
        final String instructionId;
        final boolean[] activeSpecializations;

        private static final String KEY_INSTRUCTION_ID = "i";
        private static final String KEY_ACTIVE_SPECIALIZATIONS = "a";
        private static final String KEY_LENGTH = "l";

        ActiveSpecializationOccurence(String instructionId, boolean... activeSpecializations) {
            this.instructionId = instructionId;
            this.activeSpecializations = activeSpecializations;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(activeSpecializations);
            result = prime * result + Objects.hash(instructionId);
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
        public String toString() {
            return "ActiveSpecializationOccurence[instructionId=" + instructionId + ", activeSpecializations=" + Arrays.toString(activeSpecializations) + "]";
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

        JSONObject createDecision(String[] specNames) {
            assert specNames.length >= activeSpecializations.length : instructionId + "should have at least" + activeSpecializations.length + " specializations, but has " + specNames.length;
            JSONObject result = new JSONObject();
            result.put("type", "Quicken");
            assert instructionId.startsWith("c.");
            result.put("operation", instructionId.substring(2)); // drop the `c.` prefix

            JSONArray specs = new JSONArray();
            for (int i = 0; i < activeSpecializations.length; i++) {
                if (activeSpecializations[i]) {
                    specs.put(specNames[i]);
                }
            }

            result.put("specializations", specs);

            return result;
        }

        static ActiveSpecializationOccurence deserializeState(JSONObject o) {
            String instructionId = o.getString(KEY_INSTRUCTION_ID);
            int len = o.getInt(KEY_LENGTH);

            boolean[] activeSpecializations = new boolean[len];

            JSONArray arr = o.getJSONArray(KEY_ACTIVE_SPECIALIZATIONS);
            for (int i = 0; i < arr.length(); i++) {
                int idx = arr.getInt(i);
                activeSpecializations[i] = true;
            }

            return new ActiveSpecializationOccurence(instructionId, activeSpecializations);
        }
    }

    private final Map<ActiveSpecializationOccurence, Long> activeSpecializationsMap = new HashMap<>();

    private final void resetLast() {
        for (int i = 2; i <= TRACE_LENGTH; i++) {
            last[i - 2] = new InstructionSequence(new int[i]);
        }
    }

    @SuppressWarnings("unused")
    @TruffleBoundary
    public final void startFunction(OperationsNode node) {
        resetLast();
    }

    @TruffleBoundary
    public final void endFunction() {
        resetLast();
    }

    @SuppressWarnings("unused")
    @TruffleBoundary
    public final void traceInstruction(int bci, String id, int instructionType, Object... arguments) {
        if (instructionType == INSTRUCTION_TYPE_CUSTOM) {
            assert arguments.length == 1;
            boolean[] activeSpecs = (boolean[]) arguments[0];

            ActiveSpecializationOccurence occ = new ActiveSpecializationOccurence(id, activeSpecs);
            Long cur = activeSpecializationsMap.get(occ);
            long next = cur == null ? 1 : cur + 1;
            activeSpecializationsMap.put(occ, next);
        }
    }

    @TruffleBoundary
    public final void traceSpecialization(int bci, String id, int specializationId, Object... arguments) {
    }

    @SuppressWarnings({"unused", "static-method"})
    public final Object tracePop(Object value) {
        return value;
    }

    @SuppressWarnings({"unused", "static-method"})
    public final Object tracePush(Object value) {
        return value;
    }

    @SuppressWarnings("unused")
    public final void traceException(Throwable ex) {
    }

    private static final long score(Map.Entry<InstructionSequence, Long> ent) {
        return ent.getValue() * ent.getKey().instrs.length;
    }

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
        // occurences.entrySet().stream()//
        // .sorted((e1, e2) -> Long.compare(score(e2), score(e1)))//
        // .limit(30)//
        // .forEachOrdered(e -> {
        // writer.printf(" %s : %d%n", e.getKey(), e.getValue());
        // });
    }

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

    public static ExecutionTracer deserializeState(JSONObject tracer) {
        String key = tracer.getString(KEY_KEY);
        ExecutionTracer result = new ExecutionTracer(key);

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
    public String toString() {
        return "ExecutionTracer [key=" + key + ", activeSpecializationsMap=" + activeSpecializationsMap + "]";
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public void setInstructionSpecializationNames(String instructionName, String... specializationNames) {
        this.specializationNames.put(instructionName, specializationNames);
    }

}
