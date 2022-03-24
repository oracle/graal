package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.Template;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction;

public class OperationsData extends Template {

    private final List<SingleOperationData> operations = new ArrayList<>();
    private final OperationsContext context = new OperationsContext();

    private TypeMirror languageType;
    private TypeMirror parseContextType;
    private ExecutableElement parseMethod;

    private boolean tracing;

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

    public void initializeContext() {
        for (SingleOperationData data : operations) {
            context.processOperation(data);
        }
    }

}
