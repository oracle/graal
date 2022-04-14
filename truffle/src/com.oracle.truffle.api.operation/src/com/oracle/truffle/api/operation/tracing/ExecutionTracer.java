package com.oracle.truffle.api.operation.tracing;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.operation.OperationsNode;

public class ExecutionTracer {
    private static ThreadLocal<ExecutionTracer> INSTANCE = ThreadLocal.withInitial(ExecutionTracer::new);

    public static ExecutionTracer get() {
        return INSTANCE.get();
    }

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

    private final void resetLast() {
        for (int i = 2; i <= TRACE_LENGTH; i++) {
            last[i - 2] = new InstructionSequence(new int[i]);
        }
    }

    @SuppressWarnings("unused")
    public final void startFunction(OperationsNode node) {
        resetLast();
    }

    public final void endFunction() {
        resetLast();
    }

    @SuppressWarnings("unused")
    public final void traceInstruction(int bci, int id, Object... arguments) {
        // System.out.printf(" [TT] %04x %d %s\n", bci, id, List.of(a, arguments));
        for (int i = 0; i < last.length; i++) {
            last[i] = last[i].add(id);
            if (last[i].isValid()) {
                Long vo = occurences.get(last[i]);
                long v = vo == null ? 0 : vo;
                occurences.put(last[i], v + 1);
            }
        }
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

    public final void dump() {
        occurences.entrySet().stream()//
                        .sorted((e1, e2) -> Long.compare(score(e2), score(e1)))//
                        .limit(30)//
                        .forEachOrdered(e -> {
                            System.out.printf("  %s : %d\n", e.getKey(), e.getValue());
                        });
    }
}
