package com.oracle.truffle.dsl.processor.operations.instructions;

import java.util.ArrayList;
import java.util.Stack;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

public class SuperInstruction extends Instruction {
    private static void createResults(Instruction[] instrs, ArrayList<InputType> inputs, ArrayList<ResultType> results) {
        int stackSize = 0;

        for (int i = 0; i < instrs.length; i++) {
            for (int j = 0; j < instrs[i].inputs.length; j++) {
                switch (instrs[i].inputs[j]) {
                    case STACK_VALUE:
                    case STACK_VALUE_IGNORED:
                        if (stackSize > 0) {
                            stackSize--;
                            break;
                        }
                        // fall-through
                    case ARGUMENT:
                    case BRANCH_TARGET:
                    case CONST_POOL:
                    case LOCAL:
                        if (inputs != null) {
                            inputs.add(instrs[i].inputs[j]);
                        }
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected value: " + instrs[i].inputs[j]);
                }
            }

            for (int j = 0; j < instrs[i].results.length; j++) {
                switch (instrs[i].results[j]) {
                    case SET_LOCAL:
                        if (results != null) {
                            results.add(ResultType.SET_LOCAL);
                        }
                        break;
                    case STACK_VALUE:
                        stackSize++;
                        break;
                    default:
                        throw new UnsupportedOperationException("not yet implemented");
                }
            }
        }

        if (results != null) {
            for (int i = 0; i < stackSize; i++) {
                results.add(ResultType.STACK_VALUE);
            }
        }
    }

    private static ResultType[] createResults(Instruction[] instrs) {
        ArrayList<ResultType> results = new ArrayList<>();
        createResults(instrs, null, results);
        return results.toArray(new ResultType[results.size()]);
    }

    private static InputType[] createInputs(Instruction[] instrs) {
        ArrayList<InputType> results = new ArrayList<>();
        createResults(instrs, results, null);
        return results.toArray(new InputType[results.size()]);
    }

    private static String createName(Instruction[] instrs) {
        StringBuilder sb = new StringBuilder("super.");
        for (int i = 0; i < instrs.length; i++) {
            if (i != 0) {
                sb.append('.');
            }
            sb.append(instrs[i].name);
        }
        return sb.toString();
    }

    Instruction[] instrs;

    public SuperInstruction(int id, Instruction[] instrs) {
        super(createName(instrs), id, createResults(instrs), createInputs(instrs));
        this.instrs = instrs;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        ProcessorContext context = ProcessorContext.getInstance();

        CodeVariableElement[] realInputs = vars.inputs;
        CodeVariableElement[] realResults = vars.results;

        Stack<CodeVariableElement> tmpStack = new Stack<>();
        int realInputIndex = 0;
        int realResultIndex = 0;
        for (int i = 0; i < instrs.length; i++) {

            CodeVariableElement[] innerInputs = new CodeVariableElement[instrs[i].inputs.length];
            CodeVariableElement[] innerResults = new CodeVariableElement[instrs[i].results.length];

            for (int j = 0; j < instrs[i].inputs.length; j++) {
                switch (instrs[i].inputs[j]) {
                    case STACK_VALUE:
                        if (!tmpStack.isEmpty()) {
                            innerInputs[j] = tmpStack.pop();
                            break;
                        }
                        // fall-through
                    case ARGUMENT:
                    case BRANCH_TARGET:
                    case CONST_POOL:
                    case LOCAL:
                        innerInputs[j] = realInputs[realInputIndex++];
                        break;
                    case STACK_VALUE_IGNORED:
                        if (!tmpStack.isEmpty()) {
                            tmpStack.pop();
                        } else {
                            realInputIndex++;
                        }
                        break;
                }
            }

            for (int j = 0; j < instrs[i].results.length; j++) {
                switch (instrs[i].results[j]) {
                    case SET_LOCAL:
                        innerResults[j] = realResults[realResultIndex++];
                        break;
                    case STACK_VALUE:
                        innerResults[j] = new CodeVariableElement(context.getType(Object.class), "inner_result_" + i + "_" + j);
                        b.statement("Object " + innerResults[j].getName());
                        tmpStack.add(innerResults[j]);
                        break;
                }
            }

            vars.inputs = innerInputs;
            vars.results = innerResults;
            b.tree(instrs[i].createExecuteCode(vars));
        }

        for (int i = 0; i < tmpStack.size(); i++) {
            b.startAssign(realResults[realResultIndex++]).variable(tmpStack.get(i)).end();
        }

        return b.build();
    }

    @Override
    public BoxingEliminationBehaviour boxingEliminationBehaviour() {
        return BoxingEliminationBehaviour.DO_NOTHING;
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        return null;
    }
}