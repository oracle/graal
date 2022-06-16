package com.oracle.truffle.dsl.processor.operations.instructions;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;

import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;
import com.oracle.truffle.dsl.processor.operations.OperationsBytecodeNodeGeneratorPlugs;
import com.oracle.truffle.dsl.processor.operations.OperationsBytecodeNodeGeneratorPlugs.LocalRefHandle;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.MethodProperties;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.ParameterKind;

public class CustomInstruction extends Instruction {
    public enum DataKind {
        BITS,
        CONST,
        CHILD,
        CONTINUATION,
    }

    private final SingleOperationData data;
    protected ExecutableElement executeMethod;
    private DataKind[] dataKinds = null;
    private int numChildNodes;
    private int numConsts;
    private OperationsBytecodeNodeGeneratorPlugs plugs;
    private CodeExecutableElement prepareAOTMethod;
    private CodeExecutableElement getSpecializationBits;
    private final List<QuickenedInstruction> quickenedVariants = new ArrayList<>();
    private int boxingEliminationBitOffset;
    private int boxingEliminationBitMask;
    private ArrayList<Integer> localRefIndices;

    public SingleOperationData getData() {
        return data;
    }

    public String getUniqueName() {
        return data.getName();
    }

    public List<String> getSpecializationNames() {
        List<String> result = new ArrayList<>();
        for (SpecializationData spec : data.getNodeData().getSpecializations()) {
            result.add(spec.getId());
        }
        return result;
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

    protected CustomInstruction(String name, int id, SingleOperationData data, ResultType[] results, InputType[] inputs) {
        super(name, id, results, inputs);
        this.data = data;
    }

    @Override
    protected CodeTree createInitializeAdditionalStateBytes(BuilderVariables vars, CodeTree[] arguments) {
        if (getAdditionalStateBytes() == 0) {
            return null;
        }

        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        int lengthWithoutState = lengthWithoutState();

        b.lineComment("additionalData  = " + dataKinds.length + " bytes: " + List.of(dataKinds));
        b.lineComment("  numChildNodes = " + numChildNodes);
        b.lineComment("  numConsts     = " + numConsts);

        for (int i = 0; i < dataKinds.length; i++) {
            CodeTree index = b.create().variable(vars.bci).string(" + " + lengthWithoutState + " + " + i).build();
            switch (dataKinds[i]) {
                case BITS:
                    b.startStatement();
                    b.variable(vars.bc).string("[").tree(index).string("] = 0");
                    b.end();
                    break;
                case CHILD:
                    b.startStatement();
                    b.startCall("LE_BYTES", "putShort");
                    b.variable(vars.bc);
                    b.tree(index);
                    b.startCall("createChildNodes").string("" + numChildNodes).end();
                    b.end();
                    break;
                case CONST:
                    b.startAssign("int constantOffset").startCall(vars.consts, "reserve").string("" + numConsts).end(2);

                    b.startStatement();
                    b.startCall("LE_BYTES", "putShort");
                    b.variable(vars.bc);
                    b.tree(index);
                    b.startGroup().cast(new CodeTypeMirror(TypeKind.SHORT)).string("constantOffset").end();
                    b.end();
                    break;
                case CONTINUATION:
                    break;
                default:
                    throw new UnsupportedOperationException("unexpected value: " + dataKinds[i]);
            }

            b.end();
        }

        int iLocal = 0;
        for (int localIndex : localRefIndices) {
            b.startStatement().startCall(vars.consts, "setValue");
            b.string("constantOffset + " + localIndex);

            if (data.getMainProperties().numLocalReferences == -1) {
                b.startStaticCall(OperationGeneratorUtils.getTypes().LocalSetter, "createArray");
                b.startCall("getLocalIndices");
                b.startGroup().variable(vars.operationData).string(".localReferences").end();
                b.end(2);
            } else {
                b.startStaticCall(OperationGeneratorUtils.getTypes().LocalSetter, "create");
                b.startCall("getLocalIndex");
                b.startGroup().variable(vars.operationData).string(".localReferences[" + (iLocal++) + "]").end();
                b.end(2);
            }
            b.end(2);
        }

        return b.build();
    }

    @Override
    public boolean standardPrologue() {
        return false;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        createTracerCode(vars, b);

        if (data.getMainProperties().isVariadic) {

            b.declaration("int", "numVariadics", "LE_BYTES.getShort(bc, bci + " + getArgumentOffset(inputs.length - 1) + ")");

            int additionalInputs = inputs.length - 1;

            int inputIndex = 0;
            CodeTree[] inputTrees = new CodeTree[data.getMainProperties().parameters.size()];
            for (ParameterKind kind : data.getMainProperties().parameters) {
                String inputName = "input_" + inputIndex;
                switch (kind) {
                    case STACK_VALUE:
                        b.declaration("Object", inputName, "frame.getObject(sp - numVariadics - " + (additionalInputs + inputIndex) + ")");
                        inputTrees[inputIndex++] = CodeTreeBuilder.singleString(inputName);
                        break;
                    case VARIADIC:
                        b.declaration("Object[]", inputName, "new Object[numVariadics]");
                        b.startFor().string("int varIndex = 0; varIndex < numVariadics; varIndex++").end().startBlock();
                        b.startStatement().string(inputName, "[varIndex] = frame.getObject(sp - numVariadics + varIndex)").end();
                        b.end();
                        inputTrees[inputIndex++] = CodeTreeBuilder.singleString(inputName);
                        break;
                    case VIRTUAL_FRAME:
                        inputTrees[inputIndex++] = CodeTreeBuilder.singleVariable(vars.frame);
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected value: " + kind);
                }
            }

            if (results.length > 0) {
                b.startAssign("Object result");
            } else {
                b.startStatement();
            }

            b.startCall("this", executeMethod);
            b.variable(vars.frame);
            b.variable(vars.bci);
            b.variable(vars.sp);
            b.trees(inputTrees);
            b.end(2);

            b.startAssign(vars.sp).variable(vars.sp).string(" - " + additionalInputs + " - numVariadics + ", results.length > 0 ? "1" : "0").end();

            if (results.length > 0) {
                b.startStatement().startCall(vars.frame, "setObject");
                b.string("sp - 1");
                b.string("result");
                b.end(2);
            }

        } else {
            b.startStatement();
            b.startCall("this", executeMethod);
            b.variable(vars.frame);
            b.variable(vars.bci);
            b.variable(vars.sp);
            b.end(2);

            b.startAssign(vars.sp).variable(vars.sp).string(" - " + this.numPopStatic() + " + " + this.numPush()).end();
        }

        return b.build();
    }

    protected void createTracerCode(ExecutionVariables vars, CodeTreeBuilder b) {
        if (vars.tracer != null) {
            b.startStatement().startCall(vars.tracer, "traceActiveSpecializations");
            b.variable(vars.bci);
            b.variable(opcodeIdField);

            b.startStaticCall(getSpecializationBits);
            b.variable(vars.bc);
            b.variable(vars.bci);
            b.end();

            b.end(2);
        }
    }

    @Override
    public int getAdditionalStateBytes() {
        if (dataKinds == null) {
            throw new UnsupportedOperationException("state bytes not yet initialized");
        }

        return dataKinds.length;
    }

    public void setDataKinds(DataKind[] dataKinds) {
        this.dataKinds = dataKinds;
    }

    @Override
    public String dumpInfo() {
        StringBuilder sb = new StringBuilder(super.dumpInfo());

        sb.append("  Additional Data:\n");
        int ofs = -1;
        for (DataKind kind : dataKinds) {
            ofs += 1;
            if (kind == DataKind.CONTINUATION) {
                continue;
            }
            sb.append("    ").append(ofs).append(" ").append(kind).append("\n");
        }

        sb.append("  Specializations:\n");
        for (SpecializationData sd : data.getNodeData().getSpecializations()) {
            sb.append("    ").append(sd.getId()).append("\n");
        }

        return sb.toString();
    }

    public void setNumChildNodes(int numChildNodes) {
        this.numChildNodes = numChildNodes;
    }

    public void setNumConsts(List<Object> consts) {
        this.numConsts = consts.size();
        this.localRefIndices = new ArrayList<>();
        int i = 0;
        for (Object c : consts) {
            if (c instanceof LocalRefHandle) {
                localRefIndices.add(i);
            }
            i++;
        }
    }

    public void setPrepareAOTMethod(CodeExecutableElement prepareAOTMethod) {
        this.prepareAOTMethod = prepareAOTMethod;
    }

    public void setGetSpecializationBits(CodeExecutableElement getSpecializationBits) {
        this.getSpecializationBits = getSpecializationBits;
    }

    public void setBoxingEliminationData(int boxingEliminationBitOffset, int boxingEliminationBitMask) {
        this.boxingEliminationBitOffset = boxingEliminationBitOffset;
        this.boxingEliminationBitMask = boxingEliminationBitMask;
    }

    @Override
    public BoxingEliminationBehaviour boxingEliminationBehaviour() {
        return BoxingEliminationBehaviour.SET_BIT;
    }

    @Override
    public int boxingEliminationBitOffset() {
        return boxingEliminationBitOffset;
    }

    @Override
    public int boxingEliminationBitMask() {
        return boxingEliminationBitMask;
    }

    public OperationsBytecodeNodeGeneratorPlugs getPlugs() {
        return plugs;
    }

    public void setPlugs(OperationsBytecodeNodeGeneratorPlugs plugs) {
        this.plugs = plugs;
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        if (prepareAOTMethod == null) {
            return null;
        }

        return CodeTreeBuilder.createBuilder().startStatement()//
                        .startCall("this", prepareAOTMethod) //
                        .string("null") // frame
                        .variable(vars.bci) //
                        .string("-1") // stack pointer
                        .tree(language) //
                        .tree(root) //
                        .end(2).build();
    }

    @Override
    public CodeTree[] createTracingArguments(ExecutionVariables vars) {
        CodeTree[] result = new CodeTree[2];
        result[0] = CodeTreeBuilder.singleString("ExecutionTracer.INSTRUCTION_TYPE_CUSTOM");
        result[1] = CodeTreeBuilder.createBuilder().startStaticCall(getSpecializationBits).variable(vars.bc).variable(vars.bci).end().build();
        return result;
    }

    public void addQuickenedVariant(QuickenedInstruction quick) {
        quickenedVariants.add(quick);
    }

    public List<QuickenedInstruction> getQuickenedVariants() {
        return quickenedVariants;
    }

    @Override
    public int numLocalReferences() {
        return data.getMainProperties().numLocalReferences;
    }
}