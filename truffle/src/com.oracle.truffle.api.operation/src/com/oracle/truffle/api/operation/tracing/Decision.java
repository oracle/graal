package com.oracle.truffle.api.operation.tracing;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

import com.oracle.truffle.api.operation.tracing.OperationsStatistics.GlobalOperationStatistics;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

abstract class Decision {
    static final Comparator<Decision> COMPARATOR = (o1, o2) -> -Double.compare(o1.value(), o2.value());

    private final String type;
    int appliedSort;

    private Decision(String type) {
        this.type = type;
    }

    abstract double value();

    abstract String id(GlobalOperationStatistics stats);

    protected abstract String prettyPrint(GlobalOperationStatistics stats);

    protected String createsInstruction(GlobalOperationStatistics stats) {
        return null;
    }

    @SuppressWarnings("unused")
    boolean acceptedBefore(Decision decision, GlobalOperationStatistics stats) {
        return false;
    }

    JSONObject serialize(GlobalOperationStatistics stats) {
        JSONObject obj = new JSONObject();
        obj.put("_comment", "value: " + value());
        obj.put("type", type);
        obj.put("id", id(stats));
        return obj;
    }

    static final class Quicken extends Decision {
        private final int instruction;
        private final int[] specializations;
        private long executionCount;

        Quicken(int instruction, int[] specializations, long executionCount) {
            super("Quicken");
            this.instruction = instruction;
            this.specializations = specializations;
            this.executionCount = executionCount;
        }

        @Override
        double value() {
            return executionCount;
        }

        @Override
        String id(GlobalOperationStatistics stats) {
            String s = Arrays.stream(specializations).mapToObj(x -> stats.specNames[instruction][x]).collect(Collectors.joining(","));
            return String.format("quicken:%s:%s", stats.instrNames[instruction], s);
        }

        @Override
        JSONObject serialize(GlobalOperationStatistics stats) {
            JSONObject result = super.serialize(stats);
            String instrName = stats.instrNames[instruction];
            String shortName;
            if (instrName.startsWith("c.")) {
                shortName = instrName.substring(2);
            } else {
                assert instrName.startsWith("sc.");
                shortName = instrName.substring(3);
            }
            result.put("operation", shortName);

            JSONArray specsData = new JSONArray();
            result.put("specializations", specsData);
            for (int i : specializations) {
                specsData.put(stats.specNames[instruction][i]);
            }

            return result;
        }

        @Override
        protected String prettyPrint(GlobalOperationStatistics stats) {
            StringBuilder sb = new StringBuilder();

            sb.append("Quicken ").append(id(stats)).append('\n');
            sb.append("    value: ").append(value()).append('\n');
            sb.append("    total execution count: ").append(executionCount).append('\n');
            sb.append("    instruction: ").append(stats.instrNames[instruction]).append('\n');
            for (int i = 0; i < specializations.length; i++) {
                sb.append("    specialization[").append(i).append("]: ").append(stats.specNames[instruction][specializations[i]]).append('\n');
            }

            return sb.toString();
        }

        @Override
        protected String createsInstruction(GlobalOperationStatistics stats) {
            String s = stats.instrNames[instruction] + ".q";

            for (int spec : specializations) {
                s += "." + stats.specNames[instruction][spec];
            }

            return s;
        }
    }

    static final class SuperInstruction extends Decision {
        private final int[] instructions;
        private long executionCount;

        SuperInstruction(int[] instructions, long executionCount) {
            super("SuperInstruction");
            this.instructions = instructions;
            this.executionCount = executionCount;
        }

        @Override
        double value() {
            return (instructions.length - 1) * executionCount;
        }

        @Override
        String id(GlobalOperationStatistics stats) {
            return String.format("si:%s", Arrays.stream(instructions).mapToObj(x -> stats.instrNames[x]).collect(Collectors.joining(",")));
        }

        @Override
        boolean acceptedBefore(Decision decision, GlobalOperationStatistics stats) {
            boolean changed = false;
            if (decision instanceof SuperInstruction) {
                SuperInstruction si = (SuperInstruction) decision;

                outer: for (int start = 0; start <= si.instructions.length - instructions.length; start++) {
                    for (int i = 0; i < instructions.length; i++) {
                        if (si.instructions[start + i] != instructions[i]) {
                            continue outer;
                        }
                    }

                    executionCount -= si.executionCount;
                    changed = true;
                }
            }

            return changed;
        }

        @Override
        JSONObject serialize(GlobalOperationStatistics stats) {
            JSONObject result = super.serialize(stats);

            JSONArray instrNames = new JSONArray();
            result.put("instructions", instrNames);
            for (int i : instructions) {
                instrNames.put(stats.instrNames[i]);
            }

            return result;
        }

        @Override
        protected String prettyPrint(GlobalOperationStatistics stats) {
            StringBuilder sb = new StringBuilder();

            sb.append("SuperInstruction ").append(id(stats)).append('\n');
            sb.append("    value: ").append(value()).append('\n');
            sb.append("    total execution count: ").append(executionCount).append('\n');
            for (int i = 0; i < instructions.length; i++) {
                sb.append("    instruction[").append(i).append("]: ").append(stats.instrNames[instructions[i]]).append('\n');
            }

            return sb.toString();
        }

        @Override
        protected String createsInstruction(GlobalOperationStatistics stats) {
            String s = "si";

            for (int i = 0; i < instructions.length; i++) {
                s += "." + stats.instrNames[instructions[i]];
            }

            return s;
        }
    }

    static final class CommonInstruction extends Decision {

        private final String instruction;
        private final long numNodes;

        // the decision has any value only if its a regular instr
        // or a decision-based one that has been accepted
        private boolean doCount;

        CommonInstruction(String instruction, long numNodes, boolean regular) {
            super("CommonInstruction");
            this.instruction = instruction;
            this.numNodes = numNodes;
            doCount = regular;
        }

        @Override
        double value() {
            return doCount ? numNodes : 0;
        }

        @Override
        boolean acceptedBefore(Decision decision, GlobalOperationStatistics stats) {
            if (instruction.equals(decision.createsInstruction(stats))) {
                doCount = true;
                return true;
            } else {
                return false;
            }
        }

        @Override
        JSONObject serialize(GlobalOperationStatistics stats) {
            JSONObject result = super.serialize(stats);
            result.put("instruction", instruction);
            return result;
        }

        @Override
        String id(GlobalOperationStatistics stats) {
            return "c:" + instruction;
        }

        @Override
        protected String prettyPrint(GlobalOperationStatistics stats) {
            StringBuilder sb = new StringBuilder();

            sb.append("Common ").append(id(stats)).append('\n');
            sb.append("    value: ").append(value()).append('\n');
            sb.append("    instruction: ").append(instruction).append('\n');
            sb.append("    nodes with instruction: ").append(numNodes).append('\n');

            return sb.toString();
        }

    }
}
