package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.Template;

public class OperationsData extends Template {

    enum OperationType {
        CUSTOM(null),
        PRIM_BLOCK("Block", 0),
        PRIM_IF_THEN("IfThen", 0),
        PRIM_IF_THEN_ELSE("IfThenElse", 0),
        PRIM_WHILE("While", 0),
        PRIM_CONST_OBJECT("ConstObject"),
        PRIM_LOAD_LOCAL("LoadLocal"),
        PRIM_STORE_LOCAL("StoreLocal"),
        PRIM_LOAD_ARGUMENT("LoadArgument"),
        PRIM_RETURN("Return"),
        PRIM_BRANCH("Branch", 0),
        PRIM_LABEL("Label", 0);

        final String name;
        final int numOpcodes;

        OperationType(String name) {
            this(name, 1);
        }

        OperationType(String name, int numOpcodes) {
            this.name = name;
            this.numOpcodes = numOpcodes;
        }
    }

    static class Operation {
        final OperationType type;
        final List<? extends TypeMirror> arguments;
        final int children;
        final TypeElement typeElement;
        final ExecutableElement mainMethod;
        final boolean returnsValue;

        CodeVariableElement typeConstant;
        CodeVariableElement[] opcodeConstant;

        Operation(OperationType type, List<? extends TypeMirror> arguments, int children, TypeElement typeElement, ExecutableElement mainMethod, boolean returnsValue) {
            this.type = type;
            this.arguments = arguments;
            this.children = children;
            this.typeElement = typeElement;
            this.mainMethod = mainMethod;
            this.returnsValue = returnsValue;
        }

        public String getName() {
            if (type == OperationType.CUSTOM) {
                return typeElement.getSimpleName().toString();
            } else {
                return type.name;
            }
        }

        public String getScreamCaseName() {
            if (type == OperationType.CUSTOM) {
                return "OP_" + typeElement.getSimpleName().toString().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
            } else {
                return type.toString();
            }
        }

        public CodeVariableElement getTypeConstant() {
            return typeConstant;
        }

        public void setTypeConstant(CodeVariableElement typeConstant) {
            this.typeConstant = typeConstant;
        }

        public CodeVariableElement[] getOpcodeConstant() {
            if (opcodeConstant == null) {
                throw new IllegalArgumentException("Opcode constant not defined for " + type);
            }
            return opcodeConstant;
        }

        public void setOpcodeConstant(CodeVariableElement[] opcodeConstant) {
            this.opcodeConstant = opcodeConstant;
        }

        public Collection<? extends TypeMirror> getArguments(TruffleTypes types, ProcessorContext ctx) {
            switch (type) {
                case CUSTOM:
                    return arguments;
                case PRIM_CONST_OBJECT:
                    return List.of(ctx.getType(Object.class));
                case PRIM_LOAD_LOCAL:
                case PRIM_STORE_LOCAL:
                case PRIM_LOAD_ARGUMENT:
                    return List.of(ctx.getType(short.class));
                case PRIM_BRANCH:
                    return List.of(types.OperationLabel);
                case PRIM_BLOCK:
                case PRIM_IF_THEN:
                case PRIM_IF_THEN_ELSE:
                case PRIM_WHILE:
                case PRIM_RETURN:
                case PRIM_LABEL:
                    return List.of();
                default:
                    throw new IllegalArgumentException("bad type: " + type);
            }
        }
    }

    private final List<Operation> operations = new ArrayList<>();

    public OperationsData(ProcessorContext context, TypeElement templateType, AnnotationMirror annotation) {
        super(context, templateType, annotation);
    }

    public List<Operation> getOperations() {
        return operations;
    }

}
