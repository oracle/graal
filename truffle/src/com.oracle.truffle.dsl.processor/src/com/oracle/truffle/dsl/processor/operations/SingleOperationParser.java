package com.oracle.truffle.dsl.processor.operations;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.CodeWriter;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.compiler.CompilerFactory;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeNames;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedPackageElement;
import com.oracle.truffle.dsl.processor.java.model.GeneratedTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.DeclaredCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.transform.AbstractCodeWriter;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.MethodProperties;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData.ParameterKind;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;
import com.oracle.truffle.dsl.processor.parser.NodeParser;

public class SingleOperationParser extends AbstractParser<SingleOperationData> {

    private static final Set<Modifier> MOD_PUBLIC_STATIC = Set.of(Modifier.PUBLIC, Modifier.STATIC);
    private static final Set<Modifier> MOD_STATIC_FINAL = Set.of(Modifier.STATIC, Modifier.FINAL);
    private final OperationsData parentData;
    private TypeElement proxyType;

    private Set<DeclaredType> OPERATION_PROXY_IGNORED_ANNOTATIONS = Set.of(
                    types.GenerateOperations,
                    types.OperationProxy,
                    context.getDeclaredType("com.oracle.truffle.api.operation.OperationProxies"),
                    types.Operation);

    public SingleOperationParser(OperationsData parentData) {
        this.parentData = parentData;
    }

    public SingleOperationParser(OperationsData parentData, TypeElement proxyType) {
        this.parentData = parentData;
        this.proxyType = proxyType;
    }

    @Override
    protected SingleOperationData parse(Element element, List<AnnotationMirror> mirror) {
        if (element != null && !(element instanceof TypeElement)) {
            parentData.addError(element, "@Operation can only be attached to a type");
            return null;
        }

        TypeElement te = (TypeElement) element;

        boolean proxyOnParent = proxyType != null;

        if (!proxyOnParent) {
            AnnotationMirror annOperation = ElementUtils.findAnnotationMirror(mirror, types.Operation);
            DeclaredType proxyDecl = ElementUtils.getAnnotationValue(DeclaredType.class, annOperation, "proxyNode");
            if (!proxyDecl.equals(context.getDeclaredType(Void.class))) {
                proxyType = ((TypeElement) proxyDecl.asElement());
            }
        } else {
            String name = proxyType.getSimpleName().toString();
            if (name.endsWith("Node")) {
                name = name.substring(0, name.length() - 4);
            }
            name += "Operation";
            CodeTypeElement tgt = new CodeTypeElement(MOD_PUBLIC_STATIC, ElementKind.CLASS, null, name);
            tgt.setEnclosingElement(proxyType.getEnclosingElement());

            for (AnnotationMirror mir : parentData.getMessageElement().getAnnotationMirrors()) {
                if (!OPERATION_PROXY_IGNORED_ANNOTATIONS.contains(mir.getAnnotationType())) {
                    tgt.addAnnotationMirror(mir);
                }
            }
            te = tgt;
        }

        SingleOperationData data = new SingleOperationData(context, te, ElementUtils.findAnnotationMirror(te.getAnnotationMirrors(), getAnnotationType()), parentData);

        List<ExecutableElement> operationFunctions = new ArrayList<>();
        if (!proxyOnParent) {
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
        }

        if (proxyType != null) {
            CodeTypeElement teClone = te instanceof CodeTypeElement ? (CodeTypeElement) te : CodeTypeElement.cloneShallow(te);
            te = teClone;

            for (Element el : CompilerFactory.getCompiler(proxyType).getEnclosedElementsInDeclarationOrder(proxyType)) {
                if (el.getKind() == ElementKind.METHOD && isStaticAccessible(el)) {
                    CodeExecutableElement cel = CodeExecutableElement.clone((ExecutableElement) el);
                    cel.setEnclosingElement(el.getEnclosingElement());
                    teClone.add(cel);
                    if (isOperationFunction(cel)) {
                        operationFunctions.add(cel);
                    }
                }
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

        if (data.hasErrors()) {
            return data;
        }

        data.setMainProperties(props);

        CodeTypeElement clonedType;
        if (te instanceof CodeTypeElement) {
            clonedType = (CodeTypeElement) te;
        } else {
            clonedType = CodeTypeElement.cloneShallow(te);

            clonedType.getEnclosedElements().clear();
            for (Element e : CompilerFactory.getCompiler(te).getEnclosedElementsInDeclarationOrder(te)) {
                if (e.getModifiers().contains(Modifier.PRIVATE) || !e.getModifiers().contains(Modifier.STATIC)) {
                    continue;
                }

                CodeElement<? extends Element> ce;
                if (e.getKind() == ElementKind.METHOD) {
                    ce = CodeExecutableElement.clone((ExecutableElement) e);
                } else if (e.getKind() == ElementKind.FIELD) {
                    ce = CodeVariableElement.clone((VariableElement) e);
                } else {
                    throw new UnsupportedOperationException("unknown enclosed kind: " + e.getKind());
                }

                ce.setEnclosingElement(e.getEnclosingElement());
                clonedType.add(ce);
            }
        }

        clonedType.setEnclosingElement(te.getEnclosingElement());
        // clonedType.setSuperClass(new DeclaredCodeTypeMirror(createRegularNodeChild()));
        clonedType.setSuperClass(types.Node);
        // clonedType.setSuperClass(new DeclaredCodeTypeMirror(childType));

        if (!isVariadic) {
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
            clonedType.addAnnotationMirror(repAnnotation);
        }

        CodeExecutableElement metExecute = new CodeExecutableElement(
                        Set.of(Modifier.PUBLIC, Modifier.ABSTRACT),
                        context.getType(props.returnsValue ? Object.class : void.class), "execute");

        metExecute.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));

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

        clonedType.add(metExecute);

        if (proxyType != null) {
            // add all the constants
            for (VariableElement el : ElementFilter.fieldsIn(proxyType.getEnclosedElements())) {
                if (el.getModifiers().containsAll(Set.of(Modifier.STATIC, Modifier.FINAL))) {
                    // CodeVariableElement cel = new CodeVariableElement(MOD_STATIC_FINAL,
                    // el.asType(), el.getSimpleName().toString());
                    // cel.createInitBuilder().staticReference(el);
                    // clonedType.add(cel);
                    clonedType.add(el);
                }
            }

            clonedType.setSimpleName(proxyType.getSimpleName());
            clonedType.setEnclosingElement(proxyType.getEnclosingElement());
        }

        // if (data.getName().equals("SLEvalRootOperation")) {
        // throw new AssertionError(OperationGeneratorUtils.printCode(clonedType));
        // }

        NodeData nodeData = NodeParser.createOperationParser().parse(clonedType, false);

        if (nodeData == null) {
            data.addError(te, "Could not parse invalid node");
            return data;
        }

        nodeData.redirectMessagesOnGeneratedElements(data);
        data.setNodeData(nodeData);

        return data;
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
                if (ElementUtils.isAssignable(param.asType(), types.VirtualFrame)) {
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

    private CodeTypeElement createRegularNodeChild() {

        CodeTypeElement result = new CodeTypeElement(Set.of(Modifier.PUBLIC, Modifier.ABSTRACT), ElementKind.CLASS, new GeneratedPackageElement("p"), "C");
        result.setSuperClass(types.Node);

        result.add(createExecuteMethod("Generic", context.getType(Object.class)));
        result.add(createExecuteMethod("Long", context.getType(long.class)));
        result.add(createExecuteMethod("Integer", context.getType(int.class)));
        result.add(createExecuteMethod("Byte", context.getType(byte.class)));
        result.add(createExecuteMethod("Boolean", context.getType(boolean.class)));
        result.add(createExecuteMethod("Float", context.getType(float.class)));
        result.add(createExecuteMethod("Double", context.getType(double.class)));
        result.add(createExecuteMethod("Void", context.getType(void.class)));

        return result;
    }

    private CodeExecutableElement createExecuteMethod(String name, TypeMirror retType) {
        CodeExecutableElement result = new CodeExecutableElement(Set.of(Modifier.PUBLIC, Modifier.ABSTRACT), retType, "execute" + name);
        // result.addParameter(new CodeVariableElement(types.VirtualFrame, "frame"));

        if (!ElementUtils.isObject(retType) && !ElementUtils.isVoid(retType)) {
            result.addThrownType(types.UnexpectedResultException);
        }
        return result;
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
