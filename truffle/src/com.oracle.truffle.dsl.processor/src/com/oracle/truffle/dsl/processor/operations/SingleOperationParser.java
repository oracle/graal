package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedPackageElement;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.MethodProperties;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.ParameterKind;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;
import com.oracle.truffle.dsl.processor.parser.NodeParser;

public class SingleOperationParser extends AbstractParser<SingleOperationData> {

    private final OperationsData parentData;
    private final AnnotationMirror proxyMirror;

    private final String NODE = "Node";
    private final boolean isShortCircuit;

    public SingleOperationParser(OperationsData parentData) {
        this(parentData, null, false);
    }

    public SingleOperationParser(OperationsData parentData, AnnotationMirror proxyMirror) {
        this(parentData, proxyMirror, false);
    }

    public SingleOperationParser(OperationsData parentData, AnnotationMirror proxyMirror, boolean isShortCircuit) {
        this.parentData = parentData;
        this.proxyMirror = proxyMirror;
        this.isShortCircuit = isShortCircuit;
    }

    @Override
    protected SingleOperationData parse(Element element, List<AnnotationMirror> mirror) {

        TypeElement te;
        String name;
        SingleOperationData data;

        if (element == null) {
            if (proxyMirror == null) {
                throw new AssertionError();
            }

            AnnotationValue proxyTypeValue;
            if (isShortCircuit) {
                proxyTypeValue = ElementUtils.getAnnotationValue(proxyMirror, "booleanConverter");
            } else {
                proxyTypeValue = ElementUtils.getAnnotationValue(proxyMirror, "value");
            }

            TypeMirror proxyType = (TypeMirror) proxyTypeValue.getValue();
            if (proxyType.getKind() != TypeKind.DECLARED) {
                parentData.addError(proxyMirror, proxyTypeValue, "@OperationProxy'ed type must be a class, not %s", proxyType);
            }

            te = context.getTypeElement((DeclaredType) proxyType);

            if (isShortCircuit) {
                name = (String) ElementUtils.getAnnotationValue(proxyMirror, "name").getValue();
            } else {
                AnnotationValue nameFromAnnot = ElementUtils.getAnnotationValue(proxyMirror, "operationName", false);

                if (nameFromAnnot == null) {
                    String nameFromType = te.getSimpleName().toString();
                    // strip the `Node' suffix
                    if (nameFromType.endsWith(NODE)) {
                        name = nameFromType.substring(0, nameFromType.length() - NODE.length());
                    } else {
                        name = nameFromType;
                    }
                } else {
                    name = (String) nameFromAnnot.getValue();
                }
            }

            data = new SingleOperationData(context, null, proxyMirror, parentData, name, isShortCircuit);

        } else {
            if (proxyMirror != null) {
                throw new AssertionError();
            }

            if (!(element instanceof TypeElement)) {
                parentData.addError(element, "@Operation can only be attached to a type");
                return null;
            }

            te = (TypeElement) element;
            if (isShortCircuit) {
                name = (String) ElementUtils.getAnnotationValue(proxyMirror, "name").getValue();
            } else {
                name = te.getSimpleName().toString();
            }

            data = new SingleOperationData(context, te, null, parentData, name, false);
        }

        List<ExecutableElement> operationFunctions = new ArrayList<>();

        if (proxyMirror == null) {
            // @Operation annotated type

            if (!te.getModifiers().containsAll(Set.of(Modifier.STATIC, Modifier.FINAL))) {
                data.addError("@Operation annotated class must be declared static and final");
            }

            if (te.getModifiers().contains(Modifier.PRIVATE)) {
                data.addError("@Operation annotated class must not be declared private");
            }

            if (!ElementUtils.isObject(te.getSuperclass()) || !te.getInterfaces().isEmpty()) {
                data.addError("@Operation annotated class must not extend/implement anything. Inheritance is not supported.");
            }

            for (Element el : te.getEnclosedElements()) {
                if (el.getModifiers().contains(Modifier.PRIVATE)) {
                    // ignore everything private
                    continue;
                }

                if (el.getKind() != ElementKind.CONSTRUCTOR && !el.getModifiers().contains(Modifier.STATIC)) {
                    data.addError(el, "@Operation annotated class must not contain non-static members");
                }

                operationFunctions.addAll(findSpecializations(te.getEnclosedElements()));
            }
        } else {
            // proxied node

            for (ExecutableElement cel : findSpecializations(te.getEnclosedElements())) {
                if (!cel.getModifiers().contains(Modifier.STATIC)) {
                    data.addError("@OperationProxy'ed class must have all its specializations static. Use @Bind(\"this\") parameter if you need a Node instance.");
                }

                operationFunctions.add(cel);
            }
        }

        if (operationFunctions.isEmpty()) {
            data.addError("Operation contains no specializations");
            return data;
        }

        MethodProperties props = processMethod(data, operationFunctions.get(0));
        boolean isVariadic = props.isVariadic;

        for (ExecutableElement fun : operationFunctions) {
            MethodProperties props2 = processMethod(data, fun);
            props2.checkMatches(data, props);
            data.getThrowDeclarations().addAll(fun.getThrownTypes());
        }

        if (isShortCircuit && (props.numStackValues != 1 || props.isVariadic || !props.returnsValue)) {
            data.addError("Boolean converter must take exactly one argument, not be variadic, and return a value");
        }

        if (data.hasErrors()) {
            return data;
        }

        data.setMainProperties(props);

        CodeTypeElement clonedType = cloneTypeHierarchy(te, ct -> {
            // remove NodeChild annotations
            ct.getAnnotationMirrors().removeIf(m -> ElementUtils.typeEquals(m.getAnnotationType(), types.NodeChild) || ElementUtils.typeEquals(m.getAnnotationType(), types.NodeChildren));
            // remove GenerateUncached annotations - we do not care
            ct.getAnnotationMirrors().removeIf(m -> ElementUtils.typeEquals(m.getAnnotationType(), types.GenerateUncached));

            // remove all non-static or private elements
            // this includes all the execute methods
            ct.getEnclosedElements().removeIf(e -> !e.getModifiers().contains(Modifier.STATIC) || e.getModifiers().contains(Modifier.PRIVATE));

            if (!isVariadic) {
                addBoxingEliminationNodeChildAnnotations(props, ct);
            }

        });

        if (proxyMirror == null || isShortCircuit) {
            clonedType.setSuperClass(types.Node);
        }

        clonedType.add(createExecuteMethod(props, isVariadic));

        NodeData nodeData = NodeParser.createOperationParser().parse(clonedType, false);

        if (nodeData == null) {
            data.addError("Could not parse invalid node: " + te.getSimpleName());
            return data;
        }

        // replace the default node type system with Operations one if we have it
        if (nodeData.getTypeSystem().isDefault() && parentData.getTypeSystem() != null) {
            nodeData.setTypeSystem(parentData.getTypeSystem());
        }

        nodeData.redirectMessagesOnGeneratedElements(data);
        data.setNodeData(nodeData);

        return data;
    }

    private void addBoxingEliminationNodeChildAnnotations(MethodProperties props, CodeTypeElement ct) {
        int i = 0;
        CodeTypeElement childType = createRegularNodeChild();
        CodeAnnotationMirror repAnnotation = new CodeAnnotationMirror(types.NodeChildren);
        List<CodeAnnotationValue> anns = new ArrayList<>();
        for (ParameterKind param : props.parameters) {
            if (param == ParameterKind.STACK_VALUE) {
                CodeAnnotationMirror ann = new CodeAnnotationMirror(types.NodeChild);
                // CodeTypeElement childType = createRegularNodeChild();
                ann.setElementValue("value", new CodeAnnotationValue("$child" + i));
                ann.setElementValue("type", new CodeAnnotationValue(new DeclaredCodeTypeMirror(childType)));
                anns.add(new CodeAnnotationValue(ann));
                i++;
            }
        }
        repAnnotation.setElementValue("value", new CodeAnnotationValue(anns));
        ct.addAnnotationMirror(repAnnotation);
    }

    private CodeExecutableElement createExecuteMethod(MethodProperties props, boolean isVariadic) {

        Class<?> resType;
        if (isShortCircuit) {
            resType = boolean.class;
        } else if (props.returnsValue) {
            resType = Object.class;
        } else {
            resType = void.class;
        }
        CodeExecutableElement metExecute = new CodeExecutableElement(
                        Set.of(Modifier.PUBLIC, Modifier.ABSTRACT),
                        context.getType(resType), "execute");

        metExecute.addParameter(new CodeVariableElement(types.Frame, "frame"));

        if (isVariadic) {
            int i = 0;
            for (ParameterKind param : props.parameters) {
                if (param == ParameterKind.STACK_VALUE) {
                    metExecute.addParameter(new CodeVariableElement(context.getType(Object.class), "arg" + i));
                } else if (param == ParameterKind.VARIADIC) {
                    metExecute.addParameter(new CodeVariableElement(context.getType(Object[].class), "arg" + i));
                }
                i++;
            }
        }
        return metExecute;
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.Operation;
    }

    private MethodProperties processMethod(SingleOperationData data, ExecutableElement method) {
        boolean isVariadic = false;
        List<ParameterKind> parameters = new ArrayList<>();

        for (VariableElement param : method.getParameters()) {
            if (isVariadicParameter(param)) {
                if (isVariadic) {
                    data.addError(method, "Multiple @Variadic arguments not allowed");
                }
                isVariadic = true;
                parameters.add(ParameterKind.VARIADIC);
            } else if (!isIgnoredParameter(param)) {
                if (isVariadic) {
                    data.addError(method, "Value arguments after @Variadic not allowed");
                }
                if (ElementUtils.typeEquals(param.asType(), types.Frame) || ElementUtils.typeEquals(param.asType(), types.VirtualFrame)) {
                    parameters.add(ParameterKind.VIRTUAL_FRAME);
                } else {
                    parameters.add(ParameterKind.STACK_VALUE);
                }
            }
        }

        boolean returnsValue = method.getReturnType().getKind() != TypeKind.VOID;

        MethodProperties props = new MethodProperties(method, parameters, isVariadic, returnsValue);

        return props;
    }

    private boolean isIgnoredParameter(VariableElement param) {
        if (ElementUtils.findAnnotationMirror(param, types.Cached) != null) {
            return true;
        } else if (ElementUtils.findAnnotationMirror(param, types.CachedLibrary) != null) {
            return true;
        } else if (ElementUtils.findAnnotationMirror(param, types.CachedLanguage) != null) {
            return true;
        } else if (ElementUtils.findAnnotationMirror(param, types.CachedContext) != null) {
            return true;
        } else if (ElementUtils.findAnnotationMirror(param, types.Bind) != null) {
            return true;
        }

        return false;
    }

    private static final String GENERIC_EXECUTE_NAME = "Generic";

    private CodeTypeElement createRegularNodeChild() {

        CodeTypeElement result = new CodeTypeElement(Set.of(Modifier.PUBLIC, Modifier.ABSTRACT), ElementKind.CLASS, new GeneratedPackageElement("p"), "C");
        result.setSuperClass(types.Node);

        result.add(createChildExecuteMethod(GENERIC_EXECUTE_NAME, context.getType(Object.class)));

        for (TypeKind unboxKind : parentData.getBoxingEliminatedTypes()) {
            result.add(createChildExecuteMethod(unboxKind.name(), new CodeTypeMirror(unboxKind)));
        }

        return result;
    }

    private CodeExecutableElement createChildExecuteMethod(String name, TypeMirror retType) {
        CodeExecutableElement result = new CodeExecutableElement(Set.of(Modifier.PUBLIC, Modifier.ABSTRACT), retType, "execute" + name);
        // result.addParameter(new CodeVariableElement(types.Frame, "frame"));

        if (!GENERIC_EXECUTE_NAME.equals(name)) {
            result.addThrownType(types.UnexpectedResultException);
        }
        return result;
    }

    private boolean isVariadicParameter(VariableElement param) {
        return ElementUtils.findAnnotationMirror(param, types.Variadic) != null;
    }

    private boolean isSpecializationFunction(ExecutableElement el) {
        return ElementUtils.findAnnotationMirror(el, types.Specialization) != null;
    }

    private CodeTypeElement cloneTypeHierarchy(TypeElement element, Consumer<CodeTypeElement> mapper) {
        CodeTypeElement result = CodeTypeElement.cloneShallow(element);
        if (!ElementUtils.isObject(element.getSuperclass())) {
            result.setSuperClass(cloneTypeHierarchy(context.getTypeElement((DeclaredType) element.getSuperclass()), mapper).asType());
        }

        mapper.accept(result);

        return result;
    }

    private List<ExecutableElement> findSpecializations(Collection<? extends Element> elements) {
        return elements.stream() //
                        .filter(x -> x instanceof ExecutableElement) //
                        .map(x -> (ExecutableElement) x) //
                        .filter(this::isSpecializationFunction) //
                        .collect(Collectors.toUnmodifiableList());
    }
}
