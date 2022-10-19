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

    @SuppressWarnings("unused")
    boolean acceptedBefore(Decision decision) {
        return false;
    }

    JSONObject serialize(GlobalOperationStatistics stats) {
        JSONObject obj = new JSONObject();
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
        boolean acceptedBefore(Decision decision) {
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
    }
}
