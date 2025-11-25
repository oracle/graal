package jdk.graal.compiler.lir.alloc.verifier;


import jdk.graal.compiler.lir.InstructionValueProcedure;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

public class RAVInstruction {
    public static class Base {
        protected LIRInstruction lirInstruction;
        protected List<VirtualMove> virtualMoveList;

        public Base(LIRInstruction lirInstruction) {
            this.lirInstruction = lirInstruction;
            this.virtualMoveList = new LinkedList<>();
        }

        public LIRInstruction getLIRInstruction() {
            return lirInstruction;
        }

        public void addVirtualMove(VirtualMove virtualMove) {
            this.virtualMoveList.add(virtualMove);
        }

        public List<VirtualMove> getVirtualMoveList() {
            return virtualMoveList;
        }
    }

    private static class GetCountProcedure implements InstructionValueProcedure {
        protected int count = 0;

        public int getCount() {
            int count = this.count;
            // Reset the count and go again for different argument type
            this.count = 0;
            return count;
        }

        @Override
        public Value doValue(LIRInstruction instruction, Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
            count++;
            return value;
        }
    }

    public static class ValueArrayPair {
        protected Value[] orig;
        protected Value[] curr;
        protected int count;

        public ValueArrayPair(int count) {
            this.count = count;
            this.curr = new Value[count];
            this.orig = new Value[count];
        }

        public InstructionValueProcedure copyOriginalProc = new InstructionValueProcedure() {
            private int index = 0;

            @Override
            public Value doValue(LIRInstruction instruction, Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
                ValueArrayPair.this.addOrig(index, value);
                index++;
                return value;
            }
        };

        public InstructionValueProcedure copyCurrentProc = new InstructionValueProcedure() {
            private int index = 0;

            @Override
            public Value doValue(LIRInstruction instruction, Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
                ValueArrayPair.this.addCurrent(index, value);
                index++;
                return value;
            }
        };

        public Value getCurrent(int index) {
            assert index < this.count;
            return this.curr[index];
        }

        public Value getOrig(int index) {
            assert index < this.count;
            return this.orig[index];
        }

        public void addCurrent(int index, Value value) {
            assert index < this.count;
            this.curr[index] = value;
        }

        public void addOrig(int index, Value value) {
            assert index < this.orig.length;
            this.orig[index] = value;
        }

        /**
         * Verify that all presumed values are present and that both sides have it.
         *
         * @return true, if contents have been successfully verified, false if there's null in either array.
         */
        public boolean verifyContents() {
            for (int i = 0; i < this.curr.length; i++) {
                if (this.curr[i] == null || this.orig[i] == null) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class Op extends Base {
        public ValueArrayPair dests;
        public ValueArrayPair uses;
        public ValueArrayPair temp;
        public ValueArrayPair alive;

        public Op(LIRInstruction instruction) {
            super(instruction);

            var countValuesProc = new GetCountProcedure();

            instruction.forEachInput(countValuesProc);
            this.uses = new ValueArrayPair(countValuesProc.getCount());

            instruction.forEachOutput(countValuesProc);
            this.dests = new ValueArrayPair(countValuesProc.getCount());

            instruction.forEachTemp(countValuesProc);
            this.temp = new ValueArrayPair(countValuesProc.getCount());

            instruction.forEachAlive(countValuesProc);
            this.alive = new ValueArrayPair(countValuesProc.getCount());
        }

        public boolean verifyContents() {
            return this.uses.verifyContents() && this.dests.verifyContents() && this.temp.verifyContents() && this.alive.verifyContents();
        }

        public boolean hasMissingDefinitions() {
            return this.dests.count > 0 && this.dests.orig[0] instanceof Variable && this.dests.curr[0] == null;
        }
    }

    public static class Move extends Base {
        public RegisterValue from;
        public RegisterValue to;

        public Move(LIRInstruction instr, RegisterValue from, RegisterValue to) {
            super(instr);
            this.from = from;
            this.to = to;
        }
    }

    public static class Reload extends Base {
        public VirtualStackSlot from;
        public RegisterValue to;

        public Reload(LIRInstruction instr, RegisterValue to, VirtualStackSlot from) {
            super(instr);
            this.from = from;
            this.to = to;
        }
    }

    public static class Spill extends Base {
        public VirtualStackSlot to;
        public RegisterValue from;

        public Spill(LIRInstruction instr, VirtualStackSlot to, RegisterValue from) {
            super(instr);
            this.to = to;
            this.from = from;
        }
    }

    public static class VirtualMove extends Base {
        public Variable to;
        public RegisterValue from;

        public VirtualMove(LIRInstruction instr, Variable to, RegisterValue from) {
            super(instr);
            this.to = to;
            this.from = from;
        }
    }
}
