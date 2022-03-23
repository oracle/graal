package com.oracle.truffle.dsl.processor.operations;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

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
        {
            ExecutableElement parseMethod = ElementUtils.findExecutableElement(typeElement, "parse");
            if (parseMethod == null) {
                data.addError(typeElement,
                                "Parse method not found. You must provide a method named 'parse' with following signature: void parse({Language}, {Context}, %sBuilder)",
                                typeElement.getSimpleName());
                return data;
            }

            if (parseMethod.getParameters().size() != 3) {
                data.addError(parseMethod, "Parse method must have exactly three arguments: the language, source and the builder");
                return data;
            }

            TypeMirror languageType = parseMethod.getParameters().get(0).asType();
            TypeMirror contextType = parseMethod.getParameters().get(1).asType();

            data.setParseContext(languageType, contextType, parseMethod);
        }

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
