package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.operations.OperationsData.OperationType;
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

        addPrimitives(data);

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

    private static void addPrimitives(OperationsData data) {
        addPrimitiveOperation(data, OperationType.PRIM_BLOCK, -1, true);
        addPrimitiveOperation(data, OperationType.PRIM_IF_THEN, 2, true);
        addPrimitiveOperation(data, OperationType.PRIM_IF_THEN_ELSE, 3, true);
        addPrimitiveOperation(data, OperationType.PRIM_WHILE, 2, false);
        addPrimitiveOperation(data, OperationType.PRIM_CONST_OBJECT, 0, true);
        addPrimitiveOperation(data, OperationType.PRIM_LOAD_LOCAL, 0, true);
        addPrimitiveOperation(data, OperationType.PRIM_STORE_LOCAL, 1, false);
        addPrimitiveOperation(data, OperationType.PRIM_LOAD_ARGUMENT, 0, true);
        addPrimitiveOperation(data, OperationType.PRIM_RETURN, 1, true);
        addPrimitiveOperation(data, OperationType.PRIM_BRANCH, 0, false);
        addPrimitiveOperation(data, OperationType.PRIM_LABEL, 0, false);
    }

    private static void addPrimitiveOperation(OperationsData data, OperationType type, int numChildren, boolean returnsValue) {
        data.getOperations().add(new OperationsData.Operation(type, List.of(), numChildren, null, null, returnsValue));
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
        int numChildren = first.getParameters().size();
        boolean returnsValue = !first.getReturnType().equals(context.getType(void.class));

        for (ExecutableElement fun : operationFunctions) {
            // check all functions have the same number of parameters
            int numChildParameters = fun.getParameters().size();
            boolean funReturnsValue = !fun.getReturnType().equals(context.getType(void.class));
            if (numChildParameters != numChildren) {
                data.addWarning(fun, "Expected %d child parameters, found %d", numChildren, numChildParameters);
            }
            if (funReturnsValue != returnsValue) {
                data.addWarning(fun, "Not all functions return values!");
            }
        }

        data.getOperations().add(new OperationsData.Operation(OperationType.CUSTOM, arguments, numChildren, te, first, true));
    }

    private static boolean isOperationFunction(ExecutableElement el) {
        return el.getModifiers().contains(Modifier.PUBLIC) && el.getModifiers().contains(Modifier.STATIC);
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.GenerateOperations;
    }

}
