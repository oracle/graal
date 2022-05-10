package com.oracle.truffle.dsl.processor.operations;

import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;

public class OperationMetadataParser extends AbstractParser<OperationMetadataData> {

    private final OperationsData parentData;

    public OperationMetadataParser(OperationsData parentData) {
        this.parentData = parentData;
    }

    @Override
    protected OperationMetadataData parse(Element element, List<AnnotationMirror> mirror) {
        AnnotationMirror mir = ElementUtils.findAnnotationMirror(element.getAnnotationMirrors(), getAnnotationType());
        OperationMetadataData data = new OperationMetadataData(parentData, context, element, mir);

        if (!(element instanceof VariableElement)) {
            data.addError(element, "@Metadata must be attached to a field");
            return data;
        }

        VariableElement varElement = (VariableElement) element;

        if (!varElement.getModifiers().containsAll(Set.of(Modifier.STATIC, Modifier.FINAL))) {
            data.addError(element, "@Metadata must be attached to a static final field");
        }

        if (varElement.getModifiers().contains(Modifier.PRIVATE)) {
            data.addError(element, "@Metadata field must not be private");
        }

        TypeMirror fieldType = varElement.asType();

        TypeMirror metadataType = null;

        if (fieldType.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredType = (DeclaredType) fieldType;
            if (declaredType.asElement().equals(types.MetadataKey.asElement())) {
                metadataType = declaredType.getTypeArguments().get(0);
            }
        }

        if (metadataType == null) {
            data.addError(element, "@Metadata field must be of type MetadataKey<>");
        }

        data.setType(metadataType);
        data.setName((String) ElementUtils.getAnnotationValue(mir, "value").getValue());

        return data;
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.GenerateOperations_Metadata;
    }

}
