package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.Template;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;
import com.oracle.truffle.dsl.processor.operations.instructions.FrameKind;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction;

public class OperationsData extends Template {

    private final List<SingleOperationData> operations = new ArrayList<>();
    private final OperationsContext context = new OperationsContext(this);

    private TypeMirror languageType;
    private TypeMirror parseContextType;
    private ExecutableElement parseMethod;

    private boolean tracing;
    private OperationDecisions decisions;
    private String decisionsFilePath;

    private TypeSystemData typeSystem;
    private final Set<TypeKind> boxingEliminatedTypes = new HashSet<>();

    public OperationsData(ProcessorContext context, TypeElement templateType, AnnotationMirror annotation) {
        super(context, templateType, annotation);
    }

    public OperationsContext getOperationsContext() {
        return context;
    }

    public void addOperationData(SingleOperationData data) {
        operations.add(data);
    }

    public void setParseContext(TypeMirror languageType, TypeMirror parseContextType, ExecutableElement parseMethod) {
        this.languageType = languageType;
        this.parseContextType = parseContextType;
        this.parseMethod = parseMethod;
    }

    public TypeMirror getLanguageType() {
        return languageType;
    }

    public TypeMirror getParseContextType() {
        return parseContextType;
    }

    public ExecutableElement getParseMethod() {
        return parseMethod;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        return List.copyOf(operations);
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
