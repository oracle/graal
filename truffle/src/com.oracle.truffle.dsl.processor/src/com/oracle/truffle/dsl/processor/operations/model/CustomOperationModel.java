package com.oracle.truffle.dsl.processor.operations.model;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.model.Template;

public class CustomOperationModel extends Template {

    public OperationModel operation;

    public CustomOperationModel(ProcessorContext context, TypeElement templateType, AnnotationMirror mirror, OperationModel operation) {
        super(context, templateType, mirror);
        this.operation = operation;
    }

}
