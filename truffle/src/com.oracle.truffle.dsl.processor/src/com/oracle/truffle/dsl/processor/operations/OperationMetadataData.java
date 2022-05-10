package com.oracle.truffle.dsl.processor.operations;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.Template;

public class OperationMetadataData extends Template {

    private final OperationsData parent;

    private String name;
    private TypeMirror type;

    private final Element template;

    public OperationMetadataData(OperationsData parent, ProcessorContext context, Element template, AnnotationMirror annotation) {
        super(context, null, annotation);
        this.parent = parent;
        this.template = template;
    }

    @Override
    public MessageContainer getBaseContainer() {
        return parent;
    }

    @Override
    public Element getMessageElement() {
        return template;
    }

    public TypeMirror getType() {
        return type;
    }

    public void setType(TypeMirror type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
