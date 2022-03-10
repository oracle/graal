package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.operations.OperationsData.OperationData;
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

            processOperation(data, (TypeElement) e);

        }

        data.setTracing(true);

        return data;
    }

    private boolean isIgnoredParameter(VariableElement param) {
        if (ElementUtils.findAnnotationMirror(param, types.Cached) != null) {
            return true;
        } else if (ElementUtils.findAnnotationMirror(param, types.CachedLibrary) != null) {
            return true;
        } else if (ElementUtils.findAnnotationMirror(param, types.CachedLanguage) != null) {
            return true;
        }

        return false;
    }

    private boolean isVariadicParameter(VariableElement param) {
        return ElementUtils.findAnnotationMirror(param, types.Variadic) != null;
    }

    private OperationData processMethod(OperationsData data, ExecutableElement method) {
        List<DeclaredType> arguments = List.of(); // TODO

        int numChildren = 0;
        boolean isVariadic = false;

        for (VariableElement param : method.getParameters()) {
            if (isVariadicParameter(param)) {
                if (isVariadic) {
                    data.addError(method, "Multiple @Variadic arguments not allowed");
                }
                isVariadic = true;
                numChildren++;
            } else if (!isIgnoredParameter(param)) {
                if (isVariadic) {
                    data.addError(method, "Value arguments after @Variadic not allowed");
                }
                numChildren++;
            }
        }

        boolean returnsValue = method.getReturnType().getKind() != TypeKind.VOID;

        return new OperationData(method.getEnclosingElement().getSimpleName().toString(), numChildren, isVariadic, returnsValue);
    }

    private void processOperation(OperationsData data, TypeElement te) {
        List<ExecutableElement> operationFunctions = new ArrayList<>();
        for (Element el : te.getEnclosedElements()) {
            if (el.getKind() != ElementKind.CONSTRUCTOR && !el.getModifiers().contains(Modifier.STATIC)) {
                data.addError(el, "Operations must not contain non-static members");
            }
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

        OperationData opData = processMethod(data, operationFunctions.get(0));

        for (ExecutableElement fun : operationFunctions) {
            OperationData opData2 = processMethod(data, fun);

            if (opData2.isVariadic != opData.isVariadic) {
                data.addError(fun, "All operation functions should be variadic / non-variadic");
            }
            if (opData2.returnsValue != opData.returnsValue) {
                data.addError(fun, "All operation functions should return value / be void");
            }
            if (opData2.numParameters != opData.numParameters) {
                data.addError(fun, "All operation functions should have same number of parameters");
            }
        }

        OperationsBuilder builder = data.getOperationsBuilder();

        Instruction.Custom instr;
        if (opData.isVariadic) {
            instr = new Instruction.Custom(
                            "custom." + te.getSimpleName(),
                            builder.getNextInstructionId(),
                            opData.numParameters, true,
                            !opData.returnsValue, te,
                            new Argument.VarArgsCount(opData.numParameters - 1));
        } else {
            instr = new Instruction.Custom(
                            "custom." + te.getSimpleName(),
                            builder.getNextInstructionId(),
                            opData.numParameters, false,
                            !opData.returnsValue, te);
        }
        builder.add(instr);
        Operation.Custom op = new Operation.Custom(builder, te.getSimpleName().toString(), builder.getNextOperationId(), opData.numParameters, instr);
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
