package com.oracle.truffle.dsl.processor.bytecode.model;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.Template;

/**
 * A Template with one or more {@link BytecodeDSLModel} models.
 *
 * Typically (when using {@code @GenerateBytecode}, only a single model is produced, but for testing
 * (when using {@code @GenerateBytecodeTestVariants}) the parser may produce multiple models, which
 * allows us to generate multiple interpreters and reuse tests across configurations.
 */
public class BytecodeDSLModels extends Template {
    private final List<BytecodeDSLModel> models;

    public BytecodeDSLModels(ProcessorContext context, TypeElement templateType, AnnotationMirror annotation, List<BytecodeDSLModel> models) {
        super(context, templateType, annotation);
        this.models = models;
    }

    public List<BytecodeDSLModel> getModels() {
        return models;
    }

    @Override
    protected List<MessageContainer> findChildContainers() {
        return List.copyOf(models);
    }

}
