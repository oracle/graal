package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

abstract class Operation {

    static class EmitterVariables {
        final CodeExecutableElement self;
        final CodeVariableElement bc;
        final CodeVariableElement bci;
        final CodeVariableElement consts;
        final CodeVariableElement children;
        final CodeVariableElement arguments;

        public EmitterVariables(CodeExecutableElement self, CodeVariableElement bc, CodeVariableElement bci, CodeVariableElement consts, CodeVariableElement children,
                        CodeVariableElement arguments) {
            this.self = self;
            this.bc = bc;
            this.bci = bci;
            this.consts = consts;
            this.children = children;
            this.arguments = arguments;
        }

    }

    public static final int VARIABLE_CHILDREN = -1;

    protected final String name;
    protected final int type;
    protected final int children;
    protected final Instruction[] instructions;

    public static final int COMMON_OPCODE_JUMP_UNCOND = 0;
    public static final int COMMON_OPCODE_JUMP_FALSE = 1;
    public static final int COMMON_OPCODE_POP = 2;
    protected static final int NUM_COMMON_OPCODES = 3;

    protected Instruction[] commonInstructions;

    static Instruction[] createCommonOpcodes(int start) {
        Instruction[] commonOpcodes = new Instruction[NUM_COMMON_OPCODES];
        commonOpcodes[COMMON_OPCODE_JUMP_UNCOND] = new Instruction.JumpUncond(start);
        commonOpcodes[COMMON_OPCODE_JUMP_FALSE] = new Instruction.JumpFalse(start + 1);
        commonOpcodes[COMMON_OPCODE_POP] = new Instruction.Pop(start + 2);
        return commonOpcodes;
    }

    protected Operation(String name, int type, int children, Instruction... opcodes) {
        this.name = name;
        this.type = type;
        this.children = children;
        this.instructions = opcodes;
    }

    public void setCommonInstructions(Instruction[] commonInstructions) {
        this.commonInstructions = commonInstructions;
    }

    public String getName() {
        return name;
    }

    public TypeMirror[] getBuilderArgumentTypes(ProcessorContext context, TruffleTypes types) {
        ArrayList<TypeMirror> arr = new ArrayList<>();

        for (Instruction inst : instructions) {
            for (Instruction.ArgumentType art : inst.arguments) {
                arr.add(art.toType(context, types));
            }
        }

        return arr.toArray(new TypeMirror[arr.size()]);
    }

    public boolean hasChildren() {
        return children != 0;
    }

    public int getType() {
        return type;
    }

    public abstract CodeTree createEmitterCode(TruffleTypes types, EmitterVariables vars);

    static class SimpleOperation extends Operation {

        SimpleOperation(String name, int type, Instruction opcode) {
            super(name, type, opcode.stackPops, opcode);
        }

        @Override
        public CodeTree createEmitterCode(TruffleTypes types, EmitterVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            for (int i = 0; i < children; i++) {
                OperationGeneratorUtils.buildChildCall(b, Integer.toString(i), vars);
            }

            String[] arguments = new String[instructions[0].arguments.length];

            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = vars.arguments.getName() + "[" + i + "]";
            }

            b.tree(instructions[0].createEmitterCode(types, vars, arguments));

            return b.build();
        }
    }

    static class CustomOperation extends SimpleOperation {

        public CustomOperation(String name, int type, Instruction.Custom opcode) {
            super(name, type, opcode);
        }

        public Instruction.Custom getCustomInstruction() {
            return (Instruction.Custom) instructions[0];
        }

    }

    static abstract class PseudoOperation extends Operation {
        public PseudoOperation(String name, int type, int children) {
            super(name, type, children);
        }
    }

    static class Block extends PseudoOperation {
        public Block(int type) {
            super("Block", type, VARIABLE_CHILDREN);
        }

        @Override
        public CodeTree createEmitterCode(TruffleTypes types, EmitterVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.startFor().string("int i = 0; i < " + vars.children.getName() + ".length; i++").end();
            b.startBlock();
            OperationGeneratorUtils.buildChildCall(b, "i", vars);
            b.end();

            return b.build();
        }

    }

    static class IfThen extends PseudoOperation {
        public IfThen(int type) {
            super("IfThen", type, 2);
        }

        @Override
        public CodeTree createEmitterCode(TruffleTypes types, EmitterVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            // <<child0>>
            OperationGeneratorUtils.buildChildCall(b, "0", vars);

            // jump_false end
            OperationGeneratorUtils.buildWriteByte(b, "" + commonInstructions[COMMON_OPCODE_JUMP_FALSE].opcodeNumber, vars.bc, vars.bci);
            b.declaration("int", "fwdref_end", CodeTreeBuilder.singleVariable(vars.bci));
            b.startAssign(vars.bci).variable(vars.bci).string(" + 2").end();

            // <<child1>>
            OperationGeneratorUtils.buildChildCall(b, "1", vars);

            // end:
            OperationGeneratorUtils.buildWriteForwardReference(b, "fwdref_end", vars.bc, vars.bci);

            return b.build();
        }

    }

    static class IfThenElse extends PseudoOperation {
        public IfThenElse(int type) {
            super("IfThenElse", type, 3);
        }

        @Override
        public CodeTree createEmitterCode(TruffleTypes types, EmitterVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            // <<child0>>
            OperationGeneratorUtils.buildChildCall(b, "0", vars);

            // jump_false else
            OperationGeneratorUtils.buildWriteByte(b, "" + commonInstructions[COMMON_OPCODE_JUMP_FALSE].opcodeNumber, vars.bc, vars.bci);
            b.declaration("int", "fwdref_else", CodeTreeBuilder.singleVariable(vars.bci));
            b.startAssign(vars.bci).variable(vars.bci).string(" + 2").end();

            // <<child1>>
            OperationGeneratorUtils.buildChildCall(b, "1", vars);

            // jump_uncond end
            OperationGeneratorUtils.buildWriteByte(b, "" + commonInstructions[COMMON_OPCODE_JUMP_UNCOND].opcodeNumber, vars.bc, vars.bci);
            b.declaration("int", "fwdref_end", CodeTreeBuilder.singleVariable(vars.bci));
            b.startAssign(vars.bci).variable(vars.bci).string(" + 2").end();

            // else:
            OperationGeneratorUtils.buildWriteForwardReference(b, "fwdref_else", vars.bc, vars.bci);

            // <<child2>>
            OperationGeneratorUtils.buildChildCall(b, "2", vars);

            // end:
            OperationGeneratorUtils.buildWriteForwardReference(b, "fwdref_end", vars.bc, vars.bci);

            return b.build();
        }

    }

    static class While extends PseudoOperation {
        public While(int type) {
            super("While", type, 2);
        }

        @Override
        public CodeTree createEmitterCode(TruffleTypes types, EmitterVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            // start:
            b.declaration("int", "backref_start", CodeTreeBuilder.singleVariable(vars.bci));

            // <<child0>>
            OperationGeneratorUtils.buildChildCall(b, "0", vars);

            // jump_false end
            OperationGeneratorUtils.buildWriteByte(b, "" + commonInstructions[COMMON_OPCODE_JUMP_FALSE].opcodeNumber, vars.bc, vars.bci);
            b.declaration("int", "fwdref_end", CodeTreeBuilder.singleVariable(vars.bci));
            b.startAssign(vars.bci).variable(vars.bci).string(" + 2").end();

            // <<child1>>
            OperationGeneratorUtils.buildChildCall(b, "1", vars);

            // jump start
            OperationGeneratorUtils.buildWriteByte(b, "" + commonInstructions[COMMON_OPCODE_JUMP_UNCOND].opcodeNumber, vars.bc, vars.bci);
            OperationGeneratorUtils.buildWriteShort(b, "backref_start", vars.bc, vars.bci);

            // end:
            OperationGeneratorUtils.buildWriteForwardReference(b, "fwdref_end", vars.bc, vars.bci);

            return b.build();
        }
    }

    static class Label extends PseudoOperation {
        public Label(int type) {
            super("Label", type, 0);
        }

        @Override
        public CodeTree createEmitterCode(TruffleTypes types, EmitterVariables vars) {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

            b.declaration(types.BuilderOperationLabel, "lbl", CodeTreeBuilder.createBuilder().cast(types.BuilderOperationLabel).variable(vars.arguments).string("[0]").build());
            b.startStatement().startCall("lbl", "resolve").variable(vars.bc).variable(vars.bci).end(2);

            return b.build();
        }

        @Override
        public TypeMirror[] getBuilderArgumentTypes(ProcessorContext context, TruffleTypes types) {
            return new TypeMirror[]{types.OperationLabel};
        }
    }

}
