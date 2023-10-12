package com.oracle.truffle.dsl.processor.bytecode.model;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.model.Template;

/**
 * Model for a user-defined operation.
 *
 * We define this class using composition rather than inheritance because a custom operation is
 * generated based on some template type (an {@link Operation} or {@link OperationProxy}), and it
 * needs to accept warning/error messages when the operation is validated.
 */
public class CustomOperationModel extends Template {

    public OperationModel operation;

    public CustomOperationModel(ProcessorContext context, TypeElement templateType, AnnotationMirror mirror, OperationModel operation) {
        super(context, templateType, mirror);
        this.operation = operation;
    }

}
