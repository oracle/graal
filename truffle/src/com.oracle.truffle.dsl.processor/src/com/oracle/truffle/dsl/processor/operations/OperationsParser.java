package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

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

    private int opId = 0;
    private int opcodeId = 1;

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

    private void addPrimitives(OperationsData data) {
        Instruction[] commonOpcodes = Operation.createCommonOpcodes(opcodeId);
        opcodeId += commonOpcodes.length;

        addPrimitive(data, commonOpcodes, new Operation.Block(opId++));
        addPrimitive(data, commonOpcodes, new Operation.IfThen(opId++));
        addPrimitive(data, commonOpcodes, new Operation.IfThenElse(opId++));
        addPrimitive(data, commonOpcodes, new Operation.While(opId++));
        addPrimitive(data, commonOpcodes, new Operation.Label(opId++));
        addPrimitive(data, commonOpcodes, new Operation.SimpleOperation("LoadLocal", opId++, new Instruction.LoadLocal(opcodeId++)));
        addPrimitive(data, commonOpcodes, new Operation.SimpleOperation("StoreLocal", opId++, new Instruction.StoreLocal(opcodeId++)));
        addPrimitive(data, commonOpcodes, new Operation.SimpleOperation("LoadArgument", opId++, new Instruction.LoadArgument(opcodeId++)));
        addPrimitive(data, commonOpcodes, new Operation.SimpleOperation("ConstObject", opId++, new Instruction.ConstObject(opcodeId++)));
        addPrimitive(data, commonOpcodes, new Operation.SimpleOperation("Return", opId++, new Instruction.Return(opcodeId++)));
        addPrimitive(data, commonOpcodes, new Operation.SimpleOperation("Branch", opId++, commonOpcodes[Operation.COMMON_OPCODE_JUMP_UNCOND]));

    }

    private static void addPrimitive(OperationsData data, Instruction[] commonOpcodes, Operation op) {
        op.setCommonInstructions(commonOpcodes);
        data.getOperations().add(op);
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

        Operation op = new Operation.CustomOperation(te.getSimpleName().toString(), opId++, new Instruction.Custom(opcodeId++, te, first));
        data.getOperations().add(op);
    }

    private static boolean isOperationFunction(ExecutableElement el) {
        return el.getModifiers().contains(Modifier.PUBLIC) && el.getModifiers().contains(Modifier.STATIC);
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.GenerateOperations;
    }

}
