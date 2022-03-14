package com.oracle.truffle.dsl.processor.operations;

import java.util.HashSet;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.Template;

public class SingleOperationData extends Template {
    private final String name;
    private MethodProperties mainProperties;
    private NodeData nodeData;
    private OperationsData parent;
    private final Set<TypeMirror> throwDeclarations = new HashSet<>();

    static class MethodProperties {
        public final ExecutableElement element;
        public final int numParameters;
        public final boolean isVariadic;
        public final boolean returnsValue;

        public MethodProperties(ExecutableElement element, int numParameters, boolean isVariadic, boolean returnsValue) {
            this.element = element;
            this.numParameters = numParameters;
            this.isVariadic = isVariadic;
            this.returnsValue = returnsValue;
        }

        public void checkMatches(SingleOperationData data, MethodProperties other) {
            if (other.numParameters != numParameters) {
                data.addError(element, "All methods must have same number of arguments");
            }

            if (other.isVariadic != isVariadic) {
                data.addError(element, "All methods must (not) be variadic");
            }

            if (other.returnsValue != returnsValue) {
                data.addError(element, "All methods must (not) return value");
            }
        }

        @Override
        public String toString() {
            return "Props[parameters=" + numParameters + ", variadic=" + isVariadic + ", returns=" + returnsValue + "]";
        }
    }

    public SingleOperationData(ProcessorContext context, TypeElement templateType, AnnotationMirror annotation, OperationsData parent) {
        super(context, templateType, annotation);
        this.parent = parent;
        name = templateType.getSimpleName().toString();
    }

    @Override
    public MessageContainer getBaseContainer() {
        return parent;
    }

    public String getName() {
        return name;
    }

    public Set<TypeMirror> getThrowDeclarations() {
        return throwDeclarations;
    }

    public MethodProperties getMainProperties() {
        return mainProperties;
    }

    public void setMainProperties(MethodProperties mainProperties) {
        this.mainProperties = mainProperties;
    }

    public NodeData getNodeData() {
        return nodeData;
    }

    void setNodeData(NodeData data) {
        this.nodeData = data;
    }

}
