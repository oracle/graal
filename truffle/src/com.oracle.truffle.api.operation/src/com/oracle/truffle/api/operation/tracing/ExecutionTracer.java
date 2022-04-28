package com.oracle.truffle.api.operation.tracing;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.operation.OperationsNode;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import com.oracle.truffle.tools.utils.json.JSONTokener;

public abstract class ExecutionTracer {

    private static final boolean ENABLED;
    private static final ExecutionTracer DISABLED_INSTANCE;

    private static final Map<String, ExecutionTracer> TRACERS = new HashMap<>();

    private static final String KEY_TRACERS = "tracers";

    public static ExecutionTracer get(String key) {
        if (ENABLED) {
            return TRACERS.computeIfAbsent(key, OperationsExecutionTracer::new);
        } else {
            return DISABLED_INSTANCE;
        }
    }

    final String key;
    private String outputPath;
    protected Map<String, String[]> specializationNames = new HashMap<>();

    ExecutionTracer(String key) {
        assert TRACERS.get(key) == null;
        this.key = key;
    }

    private static void loadState(String stateFile) {
        try {
            // TODO stateFile should be locked while we are running to prevent concurrent
            // modification

            FileInputStream fi = new FileInputStream(new File(stateFile));
            JSONTokener tok = new JSONTokener(fi);
            if (!tok.more()) {
                // empty file
                return;
            }

            JSONObject o = new JSONObject(tok);
            JSONArray tracers = o.getJSONArray("tracers");
            for (int i = 0; i < tracers.length(); i++) {
                JSONObject tracer = tracers.getJSONObject(i);
                ExecutionTracer tr = OperationsExecutionTracer.deserializeState(tracer);
                TRACERS.put(tr.key, tr);
            }
        } catch (FileNotFoundException ex) {
            throw new RuntimeException("Operations execution tracer file not found. Make sure tracer file exists.");
        }
    }

    private static void saveState(String stateFile) {
        try (OutputStreamWriter fo = new OutputStreamWriter(new FileOutputStream(new File(stateFile)))) {
            JSONObject result = new JSONObject();
            JSONArray tracers = new JSONArray();

            for (Map.Entry<String, ExecutionTracer> ent : TRACERS.entrySet()) {
                ExecutionTracer tracer = ent.getValue();
                tracers.put(tracer.serializeState());
                // tracer.dump(new PrintWriter(System.out));
                // System.out.flush();
            }

            result.put(KEY_TRACERS, tracers);
            fo.append(result.toString());

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
    }

    static {
        String stateFile = TruffleOptions.OperationDecisionsFile;

        if (stateFile != null && !stateFile.isEmpty()) {
            loadState(stateFile);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> saveState(stateFile)));

            ENABLED = true;
            DISABLED_INSTANCE = null;
        } else {
            ENABLED = false;
            DISABLED_INSTANCE = new DisabledExecutionTracer();
        }
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

    @TruffleBoundary
    public abstract void startFunction(OperationsNode node);

    @TruffleBoundary
    public abstract void endFunction();

    public abstract void traceInstruction(int bci, String id, int instructionType, Object... arguments);

    public abstract void traceSpecialization(int bci, String id, int specializationId, Object... arguments);

    public abstract Object tracePop(Object value);

    public abstract Object tracePush(Object value);

    public abstract void traceException(Throwable ex);

    public abstract void dump(PrintWriter writer);

    public abstract JSONObject serializeState();

    public abstract JSONArray createDecisions();

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public void setInstructionSpecializationNames(String instructionName, String... specializationNames) {
        this.specializationNames.put(instructionName, specializationNames);
    }

}
