package com.oracle.truffle.dsl.processor.operations.model;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.Template;

public class OperationsModelList extends Template {
    // TODO: do we need to forward messages or anything?

    private final List<OperationsModel> models;

    public OperationsModelList(ProcessorContext context, TypeElement templateType, AnnotationMirror annotation, List<OperationsModel> models) {
        super(context, templateType, annotation);
        this.models = models;
    }

    public List<OperationsModel> getModels() {
        return models;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        return List.copyOf(models);
    }

}
