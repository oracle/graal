package com.oracle.truffle.dsl.processor.operations;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
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

        boolean hasSome = false;

        for (Element inner : typeElement.getEnclosedElements()) {
            if (!(inner instanceof TypeElement)) {
                continue;
            }

            if (ElementUtils.findAnnotationMirror(inner, types.Operation) == null) {
                continue;
            }

            hasSome = true;

            SingleOperationData opData = new SingleOperationParser(data).parse(inner, false);

            if (opData != null) {
// opData.redirectMessages(data);
                data.addOperationData(opData);
            } else {
                data.addError("Could not generate operation: " + inner.getSimpleName());
            }

        }

        if (!hasSome) {
            data.addWarning("No operations found");
            return data;
        }

        data.setTracing(true);

        if (data.hasErrors()) {
            return data;
        }

        data.initializeContext();

        return data;
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.GenerateOperations;
    }

}
