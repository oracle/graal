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
package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.operations.Operation.ShortCircuitOperation;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.MethodProperties;
import com.oracle.truffle.dsl.processor.operations.instructions.BranchInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.CustomInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.DiscardInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.FrameKind;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction;
import com.oracle.truffle.dsl.processor.operations.instructions.InstrumentationEnterInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.InstrumentationExitInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.InstrumentationLeaveInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.LoadArgumentInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.LoadConstantInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.LoadLocalInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.QuickenedInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.ReturnInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.ShortCircuitInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.StoreLocalInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.ThrowInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.YieldInstruction;

public class OperationsContext {

    private int instructionId = 1;
    private int operationId = 1;

    public Instruction commonPop;
    public Instruction commonBranch;
    public Instruction commonBranchFalse;
    public Instruction commonThrow;

    public LoadArgumentInstruction[] loadArgumentInstructions;
    public LoadConstantInstruction[] loadConstantInstructions;

    public final ArrayList<Instruction> instructions = new ArrayList<>();
    public final ArrayList<Operation> operations = new ArrayList<>();
    private final Map<String, CustomInstruction> customInstructionNameMap = new HashMap<>();
    private final Map<String, SingleOperationData> opDataNameMap = new HashMap<>();
    private final OperationsData data;

    public CodeTypeElement outerType;

    private final Set<String> operationNames = new HashSet<>();
    public TypeMirror labelType;
    public TypeMirror exceptionType;

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
        commonPop = add(new DiscardInstruction("pop", instructionId++));
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

        if (data.enableYield) {
            add(new Operation.Simple(this, "Yield", operationId++, 1, add(new YieldInstruction(instructionId++))));
        }

        add(new Operation.Source(this, operationId++));
        add(new Operation.SourceSection(this, operationId++));

        add(new Operation.InstrumentTag(this, operationId++,
                        add(new InstrumentationEnterInstruction(instructionId++)),
                        add(new InstrumentationExitInstruction(instructionId++)),
                        add(new InstrumentationExitInstruction(instructionId++, true)),
                        add(new InstrumentationLeaveInstruction(instructionId++))));
    }

    private void createLoadStoreLocal() {
        StoreLocalInstruction storeLocal = add(new StoreLocalInstruction(this, instructionId++));
        add(new Operation.Simple(this, "StoreLocal", operationId++, 1, storeLocal));

        LoadLocalInstruction loadLocal = add(new LoadLocalInstruction(this, instructionId++));
        add(new Operation.Simple(this, "LoadLocal", operationId++, 0, loadLocal));
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
        loadConstantInstructions[FrameKind.OBJECT.ordinal()] = add(new LoadConstantInstruction(this, instructionId++, FrameKind.OBJECT));

        add(new Operation.LoadConstant(this, operationId++, loadConstantInstructions));
    }

    private void createReturn() {
        ReturnInstruction retInit = add(new ReturnInstruction(this, instructionId++));
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

        if (opData.isShortCircuit()) {
            ShortCircuitInstruction instr = add(new ShortCircuitInstruction("sc." + opData.getName(), getNextInstructionId(), opData));
            customInstructionNameMap.put(opData.getName(), instr);
            opDataNameMap.put(opData.getName(), opData);
            add(new ShortCircuitOperation(this, opData.getName(), getNextOperationId(), instr));
        } else {

            CustomInstruction instr = new CustomInstruction("c." + opData.getName(), getNextInstructionId(), opData);
            add(instr);
            customInstructionNameMap.put(opData.getName(), instr);
            opDataNameMap.put(opData.getName(), opData);

            int numChildren = props.isVariadic ? -1 : props.numStackValues;

            Operation.Simple op = new Operation.Simple(this, opData.getName(), getNextOperationId(), numChildren, instr);
            add(op);
        }
    }

    public void processDecisions(OperationDecisions decisions) {
        for (OperationDecisions.Quicken quicken : decisions.getQuicken()) {
            CustomInstruction cinstr = customInstructionNameMap.get(quicken.getOperation());
            if (cinstr == null) {
                // todo: better error reporting
                data.addWarning("Invalid Quicken decision: undefined operation %s.", quicken.getOperation());
                continue;
            }
            if (cinstr instanceof ShortCircuitInstruction) {
                // todo: make these work
                continue;
            }

            SingleOperationData opData = opDataNameMap.get(quicken.getOperation());

            add(new QuickenedInstruction(cinstr, instructionId++, opData, List.of(quicken.specializations)));
        }
    }
}
