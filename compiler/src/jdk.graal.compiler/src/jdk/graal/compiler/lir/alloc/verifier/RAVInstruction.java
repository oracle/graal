package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.lir.InstructionValueProcedure;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

public class RAVInstruction {
    public static class Base {
        protected LIRInstruction lirInstruction;
        protected List<VirtualMove> virtualMoveList;
        protected List<VirtualMove> speculativeMoveList;

        public Base(LIRInstruction lirInstruction) {
            // We do not actually need the original instruction here,
            // but is useful to have when debugging.
            this.lirInstruction = lirInstruction;
            this.virtualMoveList = new LinkedList<>();
            this.speculativeMoveList = new LinkedList<>();
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

        public void addSpeculativeMove(VirtualMove virtualMove) {
            this.speculativeMoveList.add(virtualMove);
        }

        public List<VirtualMove> getSpeculativeMoveList() {
            return speculativeMoveList;
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
            assert index < this.count : "Index out of bounds";
            return this.curr[index];
        }

        public Value getOrig(int index) {
            assert index < this.count : "Index out of bounds";
            return this.orig[index];
        }

        public void addCurrent(int index, Value value) {
            if (index >= this.count) {
                // TestCase: DerivedOopTest
                // liveBasePointers has extra item after register allocation
                // TODO: handle extra items here
                return;
            }

            this.curr[index] = value;
        }

        public void addOrig(int index, Value value) {
            assert index < this.orig.length : "Index out of bounds";
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

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder("[");
            for (int i = 0; i < this.count; i++) {
                if (this.curr[i] != null) {
                    result.append(this.orig[i].toString()).append(" -> ").append(this.curr[i].toString());
                } else {
                    result.append(this.orig[i].toString());
                }

                result.append(", ");
            }

            if (this.count > 0) {
                result.setLength(result.length() - 2);
            }

            result.append("]");
            return result.toString();
        }
    }

    public static class Op extends Base {
        public ValueArrayPair dests;
        public ValueArrayPair uses;
        public ValueArrayPair temp;
        public ValueArrayPair alive;

        public JavaKind[] kinds;
        public ValueArrayPair stateValues;

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

            instruction.forEachState(countValuesProc);
            this.stateValues = new ValueArrayPair(countValuesProc.getCount());
        }

        public boolean hasMissingDefinitions() {
            for (int i = 0; i < this.dests.count; i++) {
                if (this.dests.curr[i] == null) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return this.dests.toString() + " = Op " + this.uses.toString() + " " + this.alive.toString() + " " + this.temp.toString();
        }
    }

    public static class Move extends Base {
        public Value from;
        public Value to;

        public Move(LIRInstruction instr, Value from, Value to) {
            super(instr);
            this.from = from;
            this.to = to;
        }

        public String toString() {
            return to.toString() + " = MOVE " + from.toString();
        }
    }

    public static class RegMove extends Move {
        public RegisterValue from;
        public RegisterValue to;

        public RegMove(LIRInstruction instr, RegisterValue from, RegisterValue to) {
            super(instr, from, to);
            this.from = from;
            this.to = to;
        }

        public String toString() {
            return to.toString() + " = REGMOVE " + from.toString();
        }
    }

    // StackMove class to handle STACKMOVE instruction, temporary for now
    // before I decide how all Moves should be handled + all possible combinations.
    public static class StackMove extends Move {
        public Value from;
        public Value to;

        public StackMove(LIRInstruction instr, Value from, Value to) {
            super(instr, from, to);

            assert from instanceof StackSlot || from instanceof VirtualStackSlot : "StackMove needs to receive instanceof StackSlot or VirtualStackSlot";
            assert to instanceof StackSlot || to instanceof VirtualStackSlot : "StackMove needs to receive instanceof StackSlot or VirtualStackSlot";

            this.from = from;
            this.to = to;
        }

        public String toString() {
            return to.toString() + " = STACKMOVE " + from.toString();
        }
    }

    public static class Reload extends Move {
        public Value from;
        public RegisterValue to;

        public Reload(LIRInstruction instr, RegisterValue to, Value from) {
            super(instr, from, to);
            this.from = from;
            this.to = to;
        }

        public String toString() {
            return to.toString() + " = RELOAD " + from.toString();
        }
    }

    public static class Spill extends Move {
        public Value to;
        public RegisterValue from;

        public Spill(LIRInstruction instr, Value to, RegisterValue from) {
            super(instr, from, to);
            this.to = to;
            this.from = from;
        }

        public String toString() {
            return to.toString() + " = SPILL " + from.toString();
        }
    }

    public static class VirtualMove extends Move {
        public Value variableOrConstant;
        public Value location;

        public VirtualMove(LIRInstruction instr, Value variableOrConstant, Value location) {
            super(instr, variableOrConstant, location);
            this.variableOrConstant = variableOrConstant;
            this.location = location;
        }

        public String toString() {
            return location.toString() + " = VIRTMOVE " + variableOrConstant.toString();
        }
    }
}
