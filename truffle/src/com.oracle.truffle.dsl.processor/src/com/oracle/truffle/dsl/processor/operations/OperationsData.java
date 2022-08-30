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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.Template;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;
import com.oracle.truffle.dsl.processor.operations.instructions.FrameKind;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction;

public class OperationsData extends Template {

    private final List<SingleOperationData> operations = new ArrayList<>();
    private final List<OperationMetadataData> metadatas = new ArrayList<>();
    private final OperationsContext context = new OperationsContext(this);

    private boolean tracing;
    private OperationDecisions decisions;
    private String decisionsFilePath;

    private TypeSystemData typeSystem;
    private final Set<TypeKind> boxingEliminatedTypes = new HashSet<>();

    private boolean isGenerateAOT;
    private boolean isGenerateUncached;

    public DeclaredType languageClass;

    private int numTosSlots;

    public boolean enableYield;

    public OperationsData(ProcessorContext context, TypeElement templateType, AnnotationMirror annotation) {
        super(context, templateType, annotation);
    }

    public OperationsContext getOperationsContext() {
        return context;
    }

    public int getNumTosSlots() {
        return numTosSlots;
    }

    public void addOperationData(SingleOperationData data) {
        operations.add(data);
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        ArrayList<MessageContainer> result = new ArrayList<>();
        result.addAll(operations);
        result.addAll(metadatas);

        return result;
    }

    public void setTracing(boolean tracing) {
        this.tracing = tracing;
    }

    public boolean isTracing() {
        return tracing;
    }

    public Collection<Instruction> getInstructions() {
        return context.instructions;
    }

    public Collection<SingleOperationData> getOperationData() {
        return operations;
    }

    public List<OperationMetadataData> getMetadatas() {
        return metadatas;
    }

    public Collection<Operation> getOperations() {
        return context.operations;
    }

    public void setTypeSystem(TypeSystemData typeSystem) {
        this.typeSystem = typeSystem;
    }

    public TypeSystemData getTypeSystem() {
        return typeSystem;
    }

    public void initializeContext() {
        context.initializeContext();
        for (SingleOperationData data : operations) {
            context.processOperation(data);
        }
        if (decisions != null) {
            assert !tracing;
            context.processDecisions(decisions);
        }
    }

    public boolean isGenerateAOT() {
        return true;
    }

    public void setGenerateUncached(boolean value) {
        isGenerateUncached = true;
    }

    public boolean isGenerateUncached() {
        return isGenerateUncached;
    }

    public void setDecisions(OperationDecisions decisions) {
        this.decisions = decisions;
    }

    public String getDecisionsFilePath() {
        return this.decisionsFilePath;
    }

    public void setDecisionsFilePath(String decisionsFilePath) {
        this.decisionsFilePath = decisionsFilePath;
    }

    public Set<TypeKind> getBoxingEliminatedTypes() {
        return boxingEliminatedTypes;
    }

    static FrameKind convertToFrameType(TypeKind kind) {
        switch (kind) {
            case BYTE:
                return FrameKind.BYTE;
            case BOOLEAN:
                return FrameKind.BOOLEAN;
            case INT:
                return FrameKind.INT;
            case FLOAT:
                return FrameKind.FLOAT;
            case LONG:
                return FrameKind.LONG;
            case DOUBLE:
                return FrameKind.DOUBLE;
            default:
                return FrameKind.OBJECT;
        }
    }

    public List<FrameKind> getFrameKinds() {
        List<FrameKind> kinds = new ArrayList<>();
        kinds.add(FrameKind.OBJECT);
        for (TypeKind beType : boxingEliminatedTypes) {
            kinds.add(convertToFrameType(beType));
        }

        return kinds;
    }

}
