package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.compiler.CompilerFactory;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.MethodProperties;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;
import com.oracle.truffle.dsl.processor.parser.NodeParser;

public class SingleOperationParser extends AbstractParser<SingleOperationData> {

    private final OperationsData parentData;

    public SingleOperationParser(OperationsData parentData) {
        this.parentData = parentData;
    }

    @Override
    protected SingleOperationData parse(Element element, List<AnnotationMirror> mirror) {
        if (!(element instanceof TypeElement)) {
            parentData.addError(element, "@Operation can only be attached to a type");
            return null;
        }

        TypeElement te = (TypeElement) element;
        TypeElement proxyType;

        AnnotationMirror annOperation = ElementUtils.findAnnotationMirror(mirror, types.Operation);

        DeclaredType proxyDecl = ElementUtils.getAnnotationValue(DeclaredType.class, annOperation, "proxyNode");
        if (proxyDecl.equals(context.getDeclaredType(Void.class))) {
            proxyType = null;
        } else {
            proxyType = ((TypeElement) proxyDecl.asElement());
        }

        SingleOperationData data = new SingleOperationData(context, te, ElementUtils.findAnnotationMirror(mirror, getAnnotationType()), parentData);

        List<ExecutableElement> operationFunctions = new ArrayList<>();
        for (Element el : te.getEnclosedElements()) {
            if (el.getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }
            if (el.getKind() != ElementKind.CONSTRUCTOR && !el.getModifiers().contains(Modifier.STATIC)) {
                data.addError(el, "Operations must not contain non-static members");
            }
            if (el instanceof ExecutableElement) {
                ExecutableElement cel = (ExecutableElement) el;
                if (isOperationFunction(cel)) {
                    operationFunctions.add(cel);
                }
            }
        }

        if (proxyType != null) {
            CodeTypeElement teClone = CodeTypeElement.cloneShallow(te);
            te = teClone;
            for (Element el : CompilerFactory.getCompiler(proxyType).getEnclosedElementsInDeclarationOrder(proxyType)) {
                if (el instanceof ExecutableElement && isStaticAccessible(el)) {
                    teClone.add(el);
                    if (isOperationFunction((ExecutableElement) el)) {
                        operationFunctions.add((ExecutableElement) el);
                    }
                }
            }

        }

        if (operationFunctions.isEmpty()) {
            data.addError("Operation contains no operation functions (public static methods)");
            return data;
        }

        MethodProperties props = processMethod(data, te, operationFunctions.get(0));

        for (ExecutableElement fun : operationFunctions) {
            MethodProperties props2 = processMethod(data, te, fun);
            props2.checkMatches(data, props);
            data.getThrowDeclarations().addAll(fun.getThrownTypes());
        }

        if (data.hasErrors()) {
            return data;
        }

        data.setMainProperties(props);

        CodeTypeElement clonedType = te instanceof CodeTypeElement ? (CodeTypeElement) te : CodeTypeElement.cloneShallow(te);
        clonedType.setEnclosingElement(te.getEnclosingElement());
        clonedType.setSuperClass(types.Node);

        if (proxyType != null) {
            clonedType.addOptional(ElementUtils.findExecutableElement(proxyType, "execute"));
        }

        if (ElementUtils.findExecutableElement(te, "execute") == null) {

            CodeExecutableElement metExecute = new CodeExecutableElement(
                            Set.of(Modifier.PUBLIC, Modifier.ABSTRACT),
                            context.getType(props.returnsValue ? Object.class : void.class), "execute");

            for (int i = 0; i < props.numParameters; i++) {
                TypeMirror typParam = context.getType(Object.class);
                if (props.isVariadic && i == props.numParameters - 1) {
                    typParam = new ArrayCodeTypeMirror(context.getType(Object.class));
                }

                metExecute.addParameter(new CodeVariableElement(typParam, "arg" + i));
            }
            clonedType.add(metExecute);
        }

        if (ElementUtils.findAnnotationMirror(clonedType, types.GenerateUncached) == null) {
            clonedType.addAnnotationMirror(new CodeAnnotationMirror(types.GenerateUncached));
        }

        if (proxyType != null) {
            // add all the constants
            for (VariableElement el : ElementFilter.fieldsIn(proxyType.getEnclosedElements())) {
                if (el.getModifiers().containsAll(Set.of(Modifier.STATIC, Modifier.FINAL))) {
                    CodeVariableElement cel = new CodeVariableElement(Set.of(Modifier.STATIC, Modifier.FINAL), el.asType(), el.getSimpleName().toString());
                    cel.createInitBuilder().staticReference(el);
                    clonedType.add(cel);
                }
            }
        }

        NodeData nodeData = NodeParser.createOperationParser().parse(clonedType, false);

        if (nodeData == null) {
            data.addError(te, "Could not parse invalid node");
            return data;
        }

        nodeData.redirectMessages(data);
        data.setNodeData(nodeData);

        return data;
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.Operation;
    }

    private MethodProperties processMethod(SingleOperationData data, TypeElement te, ExecutableElement method) {
        List<DeclaredType> arguments = List.of(); // TODO

        int numChildren = 0;
        boolean isVariadic = false;

        for (VariableElement param : method.getParameters()) {
            if (isVariadicParameter(param)) {
                if (isVariadic) {
                    data.addError(method, "Multiple @Variadic arguments not allowed");
                }
                isVariadic = true;
                numChildren++;
            } else if (!isIgnoredParameter(param)) {
                if (isVariadic) {
                    data.addError(method, "Value arguments after @Variadic not allowed");
                }
                numChildren++;
            }
        }

        boolean returnsValue = method.getReturnType().getKind() != TypeKind.VOID;

        MethodProperties props = new MethodProperties(method, numChildren, isVariadic, returnsValue);

        return props;
    }

    private boolean isIgnoredParameter(VariableElement param) {
        if (ElementUtils.findAnnotationMirror(param, types.Cached) != null) {
            return true;
        } else if (ElementUtils.findAnnotationMirror(param, types.CachedLibrary) != null) {
            return true;
        } else if (ElementUtils.findAnnotationMirror(param, types.CachedLanguage) != null) {
            return true;
        }

        return false;
    }

    private boolean isVariadicParameter(VariableElement param) {
        return ElementUtils.findAnnotationMirror(param, types.Variadic) != null;
    }

    private static boolean isStaticAccessible(Element elem) {
        return !elem.getModifiers().contains(Modifier.PRIVATE) && elem.getModifiers().contains(Modifier.STATIC);
    }

    private boolean isOperationFunction(ExecutableElement el) {
        return ElementUtils.findAnnotationMirror(el, types.Specialization) != null;
    }

}
