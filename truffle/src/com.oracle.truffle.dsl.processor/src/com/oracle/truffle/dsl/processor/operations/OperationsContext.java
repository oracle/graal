package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.dsl.processor.operations.SingleOperationData.MethodProperties;
import com.oracle.truffle.dsl.processor.operations.instructions.BranchInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.CustomInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.DiscardInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.FrameKind;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.InputType;
import com.oracle.truffle.dsl.processor.operations.instructions.InstrumentationEnterInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.InstrumentationExitInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.InstrumentationLeaveInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.LoadArgumentInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.LoadConstantInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.LoadLocalInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.QuickenedInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.ReturnInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.StoreLocalInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.ThrowInstruction;

public class OperationsContext {

    private int instructionId = 1;
    private int operationId = 1;

    public Instruction commonPop;
    public Instruction commonBranch;
    public Instruction commonBranchFalse;
    public Instruction commonThrow;

    public LoadArgumentInstruction[] loadArgumentInstructions;
    public LoadConstantInstruction[] loadConstantInstructions;
    public LoadLocalInstruction[] loadLocalInstructions;

    public final ArrayList<Instruction> instructions = new ArrayList<>();
    public final ArrayList<Operation> operations = new ArrayList<>();
    private final Map<String, CustomInstruction> customInstructionNameMap = new HashMap<>();
    private final Map<String, SingleOperationData> opDataNameMap = new HashMap<>();
    private final OperationsData data;

    private final Set<String> operationNames = new HashSet<>();

    public OperationsContext(OperationsData data) {
        this.data = data;
    }

    public OperationsData getData() {
        return data;
    }

    public void initializeContext() {
        createCommonInstructions();
        createBuiltinOperations();
    }

    private void createCommonInstructions() {
        commonPop = add(new DiscardInstruction("pop", instructionId++, InputType.STACK_VALUE_IGNORED));
        commonBranch = add(new BranchInstruction(instructionId++));
        commonBranchFalse = add(new ConditionalBranchInstruction(this, instructionId++));
        commonThrow = add(new ThrowInstruction(instructionId++));
    }

    private void createBuiltinOperations() {
        add(new Operation.Block(this, operationId++));
        add(new Operation.IfThen(this, operationId++));
        add(new Operation.IfThenElse(this, operationId++, false));
        add(new Operation.IfThenElse(this, operationId++, true));
        add(new Operation.While(this, operationId++));
        add(new Operation.TryCatch(this, operationId++));
        add(new Operation.FinallyTry(this, operationId++, false));
        add(new Operation.FinallyTry(this, operationId++, true));

        add(new Operation.Label(this, operationId++));
        add(new Operation.Simple(this, "Branch", operationId++, 0, commonBranch));

        createLoadConstant();
        createLoadArgument();
        createLoadStoreLocal();
        createReturn();

        add(new Operation.InstrumentTag(this, operationId++,
                        add(new InstrumentationEnterInstruction(instructionId++)),
                        add(new InstrumentationExitInstruction(instructionId++)),
                        add(new InstrumentationExitInstruction(instructionId++, true)),
                        add(new InstrumentationLeaveInstruction(instructionId++))));

    }

    private void createLoadStoreLocal() {
        add(new Operation.Simple(this, "StoreLocal", operationId++, 1, add(new StoreLocalInstruction(instructionId++))));

        loadLocalInstructions = new LoadLocalInstruction[FrameKind.values().length];
        for (FrameKind kind : data.getFrameKinds()) {
            loadLocalInstructions[kind.ordinal()] = add(new LoadLocalInstruction(this, instructionId++, kind));
        }

        add(new Operation.Simple(this, "LoadLocal", operationId++, 0, loadLocalInstructions[FrameKind.OBJECT.ordinal()]));
    }

    private void createLoadArgument() {
        loadArgumentInstructions = new LoadArgumentInstruction[FrameKind.values().length];
        for (FrameKind kind : data.getFrameKinds()) {
            loadArgumentInstructions[kind.ordinal()] = add(new LoadArgumentInstruction(this, instructionId++, kind));
        }
        add(new Operation.Simple(this, "LoadArgument", operationId++, 0, loadArgumentInstructions[FrameKind.OBJECT.ordinal()]));
    }

    private void createLoadConstant() {
        loadConstantInstructions = new LoadConstantInstruction[FrameKind.values().length];
        for (FrameKind kind : data.getFrameKinds()) {
            loadConstantInstructions[kind.ordinal()] = add(new LoadConstantInstruction(this, instructionId++, kind));
        }

        add(new Operation.LoadConstant(this, operationId++, loadConstantInstructions));
    }

    private void createReturn() {
        ReturnInstruction retInit = add(new ReturnInstruction(instructionId++));
        add(new Operation.Simple(this, "Return", operationId++, 1, retInit));
    }

    public <T extends Instruction> T add(T elem) {
        instructions.add(elem);
        return elem;
    }

    public <T extends Operation> T add(T elem) {
        operations.add(elem);
        operationNames.add(elem.name);
        return elem;
    }

    public int getNextInstructionId() {
        return instructionId++;
    }

    public int getNextOperationId() {
        return operationId++;
    }

    public void processOperation(SingleOperationData opData) {

        if (operationNames.contains(opData.getName())) {
            opData.addError("Operation %s already defined", opData.getName());
        }

        MethodProperties props = opData.getMainProperties();

        if (props == null) {
            opData.addError("Operation %s not initialized", opData.getName());
            return;
        }

        CustomInstruction instr = new CustomInstruction("c." + opData.getName(), getNextInstructionId(), opData);
        add(instr);
        customInstructionNameMap.put(opData.getName(), instr);
        opDataNameMap.put(opData.getName(), opData);

        int numChildren = props.isVariadic ? -1 : props.numStackValues;

        Operation.Simple op = new Operation.Simple(this, opData.getName(), getNextOperationId(), numChildren, instr);
        add(op);
    }

    public void processDecisions(OperationDecisions decisions) {
        for (OperationDecisions.Quicken quicken : decisions.getQuicken()) {
            CustomInstruction cinstr = customInstructionNameMap.get(quicken.getOperation());
            if (cinstr == null) {
                // TODO line number or sth
                data.addWarning("Invalid Quicken decision: undefined operation %s.", quicken.getOperation());
                continue;
            }

            SingleOperationData opData = opDataNameMap.get(quicken.getOperation());

            add(new QuickenedInstruction(cinstr, instructionId++, opData, List.of(quicken.specializations)));
        }
    }
}
