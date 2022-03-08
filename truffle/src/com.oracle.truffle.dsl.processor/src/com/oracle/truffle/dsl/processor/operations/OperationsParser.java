package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;

public class OperationsParser extends AbstractParser<OperationsData> {

    private static AnnotationMirror getAnnotationMirror(List<? extends AnnotationMirror> mirror, DeclaredType type) {
        for (AnnotationMirror m : mirror) {
            if (m.getAnnotationType().equals(type)) {
                return m;
            }
        }
        return null;
    }

    @Override
    protected OperationsData parse(Element element, List<AnnotationMirror> mirror) {

        TypeElement typeElement = (TypeElement) element;
        AnnotationMirror generateOperationsMirror = getAnnotationMirror(mirror, types.GenerateOperations);

        OperationsData data = new OperationsData(ProcessorContext.getInstance(), typeElement, generateOperationsMirror);

        for (Element e : typeElement.getEnclosedElements()) {
            AnnotationMirror operationMirror = getAnnotationMirror(e.getAnnotationMirrors(), types.Operation);
            if (operationMirror == null) {
                continue;
            }

            if (!(e instanceof TypeElement)) {
                data.addError(e, "@Operation can only be attached to classes");
                continue;
            }

            processOperation(data, (TypeElement) e);

        }

        return data;
    }

    private void processOperation(OperationsData data, TypeElement te) {
        List<ExecutableElement> operationFunctions = new ArrayList<>();
        for (Element el : te.getEnclosedElements()) {
            if (el instanceof ExecutableElement) {
                ExecutableElement cel = (ExecutableElement) el;
                if (isOperationFunction(cel)) {
                    operationFunctions.add(cel);
                }
            }
        }

        if (operationFunctions.isEmpty()) {
            data.addWarning(te, "Operation contains no operation functions (public static methods)");
            return;
        }

        ExecutableElement first = operationFunctions.get(0);
        List<DeclaredType> arguments = List.of(); // TODO
        int numParams = first.getParameters().size();
        boolean returnsValue = first.getReturnType().getKind() != TypeKind.VOID;

        for (ExecutableElement fun : operationFunctions) {
            // check all functions have the same number of parameters
            int numChildParameters = fun.getParameters().size();
            boolean funReturnsValue = fun.getReturnType().getKind() != TypeKind.VOID;
            if (numChildParameters != numParams) {
                data.addWarning(fun, "Expected %d child parameters, found %d", numParams, numChildParameters);
            }
            if (funReturnsValue != returnsValue) {
                data.addWarning(fun, "Not all functions return values!");
            }
        }

        OperationsBuilder builder = data.getOperationsBuilder();

        Instruction.Custom instr;
        if (first.isVarArgs()) {
            instr = new Instruction.Custom(
                            "custom." + te.getSimpleName(),
                            builder.getNextInstructionId(),
                            numParams, first.isVarArgs(), !returnsValue, te,
                            new Argument.VarArgsCount(numParams - 1));
        } else {
            instr = new Instruction.Custom(
                            "custom." + te.getSimpleName(),
                            builder.getNextInstructionId(),
                            numParams, first.isVarArgs(), !returnsValue, te);
        }
        builder.add(instr);
        Operation.Custom op = new Operation.Custom(builder, te.getSimpleName().toString(), builder.getNextOperationId(), numParams, instr);
        builder.add(op);
    }

    private static boolean isOperationFunction(ExecutableElement el) {
        return el.getModifiers().contains(Modifier.PUBLIC) && el.getModifiers().contains(Modifier.STATIC);
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.GenerateOperations;
    }

}
