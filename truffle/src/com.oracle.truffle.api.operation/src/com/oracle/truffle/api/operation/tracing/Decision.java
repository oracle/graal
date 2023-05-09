/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.operation.tracing;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.truffle.api.operation.tracing.OperationsStatistics.OperationRootNodeStatistics;
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

    abstract String id(OperationRootNodeStatistics stats);

    protected abstract String prettyPrint(OperationRootNodeStatistics stats, double normalizationValue);

    protected String createsInstruction(OperationRootNodeStatistics stats) {
        return null;
    }

    @SuppressWarnings("unused")
    boolean acceptedBefore(Decision decision, OperationRootNodeStatistics stats) {
        return false;
    }

    JSONObject serialize(OperationRootNodeStatistics stats, double normalizationValue) {
        JSONObject obj = new JSONObject();
        obj.put("_comment", "value: " + value() / normalizationValue);
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
        String id(OperationRootNodeStatistics stats) {
            String s = Arrays.stream(specializations).mapToObj(x -> stats.specializationNames[instruction][x]).collect(Collectors.joining(","));
            return String.format("quicken:%s:%s", stats.instructionNames[instruction], s);
        }

        @Override
        JSONObject serialize(OperationRootNodeStatistics stats, double norm) {
            JSONObject result = super.serialize(stats, norm);
            String instrName = stats.instructionNames[instruction];
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
                specsData.put(stats.specializationNames[instruction][i]);
            }

            return result;
        }

        @Override
        protected String prettyPrint(OperationRootNodeStatistics stats, double normalizationValue) {
            StringBuilder sb = new StringBuilder();

            sb.append("Quicken ").append(id(stats)).append('\n');
            sb.append("    value: ").append(value() / normalizationValue).append('\n');
            sb.append("    total execution count: ").append(executionCount).append('\n');
            sb.append("    instruction: ").append(stats.instructionNames[instruction]).append('\n');
            for (int i = 0; i < specializations.length; i++) {
                sb.append("    specialization[").append(i).append("]: ").append(stats.specializationNames[instruction][specializations[i]]).append('\n');
            }

            return sb.toString();
        }

        @Override
        protected String createsInstruction(OperationRootNodeStatistics stats) {
            String s = stats.instructionNames[instruction] + ".q";

            List<String> specs = Arrays.stream(specializations).mapToObj(x -> stats.specializationNames[instruction][x]).collect(Collectors.toList());
            specs.sort(null);

            for (String spec : specs) {
                s += "." + spec;
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
        String id(OperationRootNodeStatistics stats) {
            return String.format("si:%s", Arrays.stream(instructions).mapToObj(x -> stats.instructionNames[x]).collect(Collectors.joining(",")));
        }

        @Override
        boolean acceptedBefore(Decision decision, OperationRootNodeStatistics stats) {
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
        JSONObject serialize(OperationRootNodeStatistics stats, double norm) {
            JSONObject result = super.serialize(stats, norm);

            JSONArray instrNames = new JSONArray();
            result.put("instructions", instrNames);
            for (int i : instructions) {
                instrNames.put(stats.instructionNames[i]);
            }

            return result;
        }

        @Override
        protected String prettyPrint(OperationRootNodeStatistics stats, double normalizationValue) {
            StringBuilder sb = new StringBuilder();

            sb.append("SuperInstruction ").append(id(stats)).append('\n');
            sb.append("    value: ").append(value() / normalizationValue).append('\n');
            sb.append("    total execution count: ").append(executionCount).append('\n');
            for (int i = 0; i < instructions.length; i++) {
                sb.append("    instruction[").append(i).append("]: ").append(stats.instructionNames[instructions[i]]).append('\n');
            }

            return sb.toString();
        }

        @Override
        protected String createsInstruction(OperationRootNodeStatistics stats) {
            String s = "si";

            for (int i = 0; i < instructions.length; i++) {
                s += "." + stats.instructionNames[instructions[i]];
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
        boolean acceptedBefore(Decision decision, OperationRootNodeStatistics stats) {
            if (instruction.equals(decision.createsInstruction(stats))) {
                doCount = true;
                return true;
            } else {
                return false;
            }
        }

        @Override
        JSONObject serialize(OperationRootNodeStatistics stats, double norm) {
            JSONObject result = super.serialize(stats, 1.0);
            result.put("instruction", instruction);
            return result;
        }

        @Override
        String id(OperationRootNodeStatistics stats) {
            return "c:" + instruction;
        }

        @Override
        protected String prettyPrint(OperationRootNodeStatistics stats, double normalizationValue) {
            StringBuilder sb = new StringBuilder();

            sb.append("Common ").append(id(stats)).append('\n');
            sb.append("    value: ").append(value()).append('\n');
            sb.append("    instruction: ").append(instruction).append('\n');
            sb.append("    nodes with instruction: ").append(numNodes).append('\n');

            return sb.toString();
        }

    }
}
