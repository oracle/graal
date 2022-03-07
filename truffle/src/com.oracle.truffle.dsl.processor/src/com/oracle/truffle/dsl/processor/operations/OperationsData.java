package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.model.Template;

public class OperationsData extends Template {
    private final List<Operation> operations = new ArrayList<>();

    public OperationsData(ProcessorContext context, TypeElement templateType, AnnotationMirror annotation) {
        super(context, templateType, annotation);
    }

    public List<Operation> getOperations() {
        return operations;
    }

    public Collection<Instruction> getInstructions() {
        Set<Instruction> instrs = new HashSet<>();

        for (Instruction insn : getOperations().get(0).commonInstructions) {
            instrs.add(insn);
        }

        for (Operation op : getOperations()) {
            for (Instruction insn : op.instructions) {
                instrs.add(insn);
            }
        }

        return instrs;
    }

    public Collection<Operation.CustomOperation> getCustomOperations() {
        return operations.stream()//
                        .filter(x -> x instanceof Operation.CustomOperation)//
                        .map(x -> (Operation.CustomOperation) x)//
                        .toList();
    }

}
