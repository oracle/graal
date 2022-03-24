package com.oracle.truffle.dsl.processor.operations.instructions;

import javax.lang.model.element.ExecutableElement;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.MethodProperties;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.ParameterKind;

public class CustomInstruction extends Instruction {
    private final SingleOperationData data;
    private ExecutableElement executeMethod;

    public SingleOperationData getData() {
        return data;
    }

    public void setExecuteMethod(ExecutableElement executeMethod) {
        this.executeMethod = executeMethod;
    }

    private static InputType[] createInputs(SingleOperationData data) {
        MethodProperties props = data.getMainProperties();
        InputType[] inputs = new InputType[props.numStackValues];
        for (int i = 0; i < inputs.length; i++) {
            inputs[i] = InputType.STACK_VALUE;
        }

        if (props.isVariadic) {
            inputs[inputs.length - 1] = InputType.VARARG_VALUE;
        }

        return inputs;
    }

    public CustomInstruction(String name, int id, SingleOperationData data) {
        super(name, id, data.getMainProperties().returnsValue
                        ? new ResultType[]{ResultType.STACK_VALUE}
                        : new ResultType[]{}, createInputs(data));
        this.data = data;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        if (results.length > 0) {
            b.startAssign(vars.results[0]);
        } else {
            b.startStatement();
        }

        int inputIndex = 0;
        b.startCall("this", executeMethod);
        b.variable(vars.bci);
        for (ParameterKind kind : data.getMainProperties().parameters) {
            switch (kind) {
                case STACK_VALUE:
                case VARIADIC:
                    b.variable(vars.inputs[inputIndex++]);
                    break;
                case VIRTUAL_FRAME:
                    b.variable(vars.frame);
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected value: " + kind);
            }
        }

        b.end(2);

        return b.build();
    }
}