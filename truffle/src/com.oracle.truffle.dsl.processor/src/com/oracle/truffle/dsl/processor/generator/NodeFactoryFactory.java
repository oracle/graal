/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.generator;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.modifiers;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeNames;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.InlineFieldData;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;

public class NodeFactoryFactory {

    private final ProcessorContext context;
    private final NodeData node;
    private final CodeTypeElement createdFactoryElement;
    private final TruffleTypes types;

    NodeFactoryFactory(ProcessorContext context, NodeData node, CodeTypeElement createdClass) {
        this.context = context;
        this.node = node;
        this.createdFactoryElement = createdClass;
        this.types = context.getTypes();
    }

    public static String factoryClassName(Element type) {
        return type.getSimpleName().toString() + "Factory";
    }

    public CodeTypeElement create() {
        Modifier visibility = node.getVisibility();
        TypeMirror nodeFactory = ElementUtils.getDeclaredType(ElementUtils.fromTypeMirror(context.getTypes().NodeFactory), node.getNodeType());

        CodeTypeElement clazz = GeneratorUtils.createClass(node, null, modifiers(), factoryClassName(node.getTemplateType()), null);
        if (visibility != null) {
            clazz.getModifiers().add(visibility);
        }
        clazz.getModifiers().add(Modifier.FINAL);

        if (createdFactoryElement != null) {
            clazz.getImplements().add(nodeFactory);

            clazz.add(createNodeFactoryConstructor());
            clazz.add(createCreateGetNodeClass());
            clazz.add(createCreateGetExecutionSignature());
            clazz.add(createCreateGetNodeSignatures());
            if (node.isGenerateCached()) {
                clazz.add(createCreateNodeMethod());
            }
            if (node.isGenerateUncached()) {
                clazz.addOptional(createGetUncached());
            }
            clazz.add(createGetInstanceMethod(visibility));
            clazz.add(createInstanceConstant(clazz.asType()));
            List<ExecutableElement> constructors = GeneratorUtils.findUserConstructors(createdFactoryElement.asType());
            List<CodeExecutableElement> factoryMethods = createFactoryMethods(node, constructors);
            for (CodeExecutableElement method : factoryMethods) {
                clazz.add(method);
            }
        }

        return clazz;
    }

    private Element createNodeFactoryConstructor() {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PRIVATE), null, factoryClassName(node.getTemplateType()));
        return method;
    }

    private CodeExecutableElement createCreateGetNodeClass() {
        TypeMirror returnValue = ElementUtils.getDeclaredType(ElementUtils.fromTypeMirror(context.getType(Class.class)), node.getNodeType());
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), returnValue, "getNodeClass");
        method.createBuilder().startReturn().typeLiteral(node.getNodeType()).end();
        return method;
    }

    private CodeExecutableElement createCreateGetNodeSignatures() {
        TypeMirror returnType = ElementUtils.findMethod(types.NodeFactory, "getNodeSignatures").getReturnType();
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), returnType, "getNodeSignatures");
        CodeTreeBuilder builder = method.createBuilder();
        builder.startReturn();

        builder.startGroup();
        builder.startStaticCall(context.getType(List.class), "of");
        List<ExecutableElement> constructors = GeneratorUtils.findUserConstructors(createdFactoryElement.asType());
        for (ExecutableElement constructor : constructors) {
            builder.startGroup();
            builder.startStaticCall(context.getType(List.class), "of");
            for (VariableElement var : constructor.getParameters()) {
                builder.typeLiteral(var.asType());
            }
            builder.end();
            builder.end();
        }
        builder.end();
        builder.end();

        builder.end();
        return method;
    }

    private CodeExecutableElement createCreateGetExecutionSignature() {
        ExecutableElement overriddenMethod = ElementUtils.findMethod(types.NodeFactory, "getExecutionSignature");
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), overriddenMethod.getReturnType(), "getExecutionSignature");
        CodeTreeBuilder builder = method.createBuilder();
        builder.startReturn();

        builder.startStaticCall(context.getType(List.class), "of");
        for (NodeExecutionData execution : node.getChildExecutions()) {
            TypeMirror nodeType = execution.getNodeType();
            if (nodeType != null) {
                builder.typeLiteral(nodeType);
            } else {
                builder.typeLiteral(types.Node);
            }
        }
        builder.end();

        builder.end();
        return method;
    }

    private CodeExecutableElement createGetUncached() {
        if (!node.isGenerateUncached()) {
            return null;
        }
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), node.getNodeType(), "getUncachedInstance");
        String className = createdFactoryElement.getSimpleName().toString();
        CodeTreeBuilder builder = method.createBuilder();
        builder.startReturn();
        if (node.isGenerateFactory()) {
            builder.string(className).string(".").string("UNCACHED");
        } else {
            builder.string("UNCACHED");
        }
        builder.end();
        return method;
    }

    private CodeExecutableElement createCreateNodeMethod() {
        CodeExecutableElement method = new CodeExecutableElement(modifiers(PUBLIC), node.getNodeType(), "createNode");
        CodeVariableElement arguments = new CodeVariableElement(context.getType(Object.class), "arguments");
        method.setVarArgs(true);
        method.addParameter(arguments);

        CodeTreeBuilder builder = method.createBuilder();
        List<ExecutableElement> signatures = GeneratorUtils.findUserConstructors(createdFactoryElement.asType());
        boolean ifStarted = false;

        for (ExecutableElement element : signatures) {
            ifStarted = builder.startIf(ifStarted);
            builder.string("arguments.length == " + element.getParameters().size());

            int index = 0;
            for (VariableElement param : element.getParameters()) {
                if (ElementUtils.isObject(param.asType())) {
                    index++;
                    continue;
                }
                builder.string(" && ");
                if (!param.asType().getKind().isPrimitive()) {
                    builder.string("(arguments[" + index + "] == null || ");
                }
                builder.string("arguments[" + index + "] instanceof ");
                builder.type(ElementUtils.eraseGenericTypes(ElementUtils.boxType(context, param.asType())));
                if (!param.asType().getKind().isPrimitive()) {
                    builder.string(")");
                }
                index++;
            }
            builder.end();
            builder.startBlock();

            builder.startReturn().startCall("create");
            index = 0;
            for (VariableElement param : element.getParameters()) {
                builder.startGroup();
                if (!ElementUtils.isObject(param.asType())) {
                    builder.string("(").type(param.asType()).string(") ");
                    if (ElementUtils.hasGenericTypes(param.asType())) {
                        GeneratorUtils.mergeSuppressWarnings(method, "unchecked");
                    }
                }
                builder.string("arguments[").string(String.valueOf(index)).string("]");
                builder.end();
                index++;
            }
            builder.end().end();

            builder.end(); // block
        }

        builder.startElseBlock();
        builder.startThrow().startNew(context.getType(IllegalArgumentException.class));
        builder.doubleQuote("Invalid create signature.");
        builder.end().end();
        builder.end(); // else block
        return method;
    }

    private ExecutableElement createGetInstanceMethod(Modifier visibility) {
        TypeElement nodeFactoryType = ElementUtils.fromTypeMirror(types.NodeFactory);
        TypeMirror returnType = ElementUtils.getDeclaredType(nodeFactoryType, node.getNodeType());

        CodeExecutableElement method = new CodeExecutableElement(modifiers(), returnType, "getInstance");
        if (visibility != null) {
            method.getModifiers().add(visibility);
        }
        method.getModifiers().add(Modifier.STATIC);
        method.createBuilder().startReturn().string(instanceVarName(node)).end();
        return method;
    }

    private static String instanceVarName(NodeData node) {
        if (node.getDeclaringNode() != null) {
            return ElementUtils.createConstantName(factoryClassName(node.getTemplateType())) + "_INSTANCE";
        } else {
            return "INSTANCE";
        }
    }

    private CodeVariableElement createInstanceConstant(TypeMirror factoryType) {
        String varName = instanceVarName(node);
        CodeVariableElement var = new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), factoryType, varName);
        var.createInitBuilder().startNew(factoryClassName(node.getTemplateType())).end();
        return var;
    }

    public static List<CodeExecutableElement> createFactoryMethods(NodeData node, List<ExecutableElement> constructors) {
        List<CodeExecutableElement> methods = new ArrayList<>();
        for (ExecutableElement constructor : constructors) {
            if (node.isGenerateCached()) {
                methods.add(createCreateMethod(node, constructor));
            }
            if (constructor instanceof CodeExecutableElement) {
                ElementUtils.setVisibility(constructor.getModifiers(), Modifier.PRIVATE);
            }
            if (node.isGenerateUncached()) {
                methods.add(createGetUncached(node, constructor));
            }
            if (node.isGenerateInline()) {
                // avoid compile errors in the error node.
                if (!node.hasErrors() || ElementUtils.findStaticMethod(node.getTemplateType(), "inline") == null) {
                    methods.add(createInlineMethod(node, constructor));
                }
            }
        }

        return methods;
    }

    public static CodeExecutableElement createInlineMethod(NodeData node, ExecutableElement constructorPrototype) {
        CodeExecutableElement method;
        if (constructorPrototype == null) {
            method = new CodeExecutableElement(node.getNodeType(), "inline");
        } else {
            method = CodeExecutableElement.clone(constructorPrototype);
            method.setSimpleName(CodeNames.of("inline"));
            method.getModifiers().clear();
            method.setReturnType(node.getNodeType());
        }
        method.getModifiers().add(Modifier.PUBLIC);
        method.getModifiers().add(Modifier.STATIC);
        method.getAnnotationMirrors().add(new CodeAnnotationMirror(ProcessorContext.getInstance().getTypes().NeverDefault));
        CodeTreeBuilder body = method.createBuilder();
        TypeMirror nodeType = NodeCodeGenerator.nodeType(node);
        body.startReturn();
        if (node.hasErrors()) {
            body.startNew(nodeType);
            for (VariableElement var : method.getParameters()) {
                body.defaultValue(var.asType());
            }
            body.end();
        } else {
            body.startNew(ElementUtils.getSimpleName(nodeType) + ".Inlined");
            TruffleTypes types = ProcessorContext.getInstance().getTypes();

            CodeVariableElement inlineTarget = new CodeVariableElement(types.InlineSupport_InlineTarget, "target");
            body.string(inlineTarget.getName());
            method.addParameter(inlineTarget);

            List<CodeAnnotationValue> requiredFields = new ArrayList<>();

            ExecutableElement value = ElementUtils.findMethod(types.InlineSupport_RequiredField, "value");
            ExecutableElement bits = ElementUtils.findMethod(types.InlineSupport_RequiredField, "bits");
            ExecutableElement type = ElementUtils.findMethod(types.InlineSupport_RequiredField, "type");
            ExecutableElement dimensions = ElementUtils.findMethod(types.InlineSupport_RequiredField, "dimensions");

            CodeTreeBuilder docBuilder = method.createDocBuilder();
            docBuilder.startJavadoc();
            docBuilder.string("Required Fields: ");
            docBuilder.string("<ul>");
            docBuilder.newLine();

            for (InlineFieldData field : FlatNodeGenFactory.createInlinedFields(node)) {
                CodeAnnotationMirror requiredField = new CodeAnnotationMirror(types.InlineSupport_RequiredField);
                requiredField.setElementValue(value, new CodeAnnotationValue(field.getFieldType()));

                if (field.hasBits()) {
                    requiredField.setElementValue(bits, new CodeAnnotationValue(field.getBits()));
                }
                if (!field.isPrimitive() && field.getType() != null) {
                    requiredField.setElementValue(type, new CodeAnnotationValue(field.getType()));
                }

                if (field.getDimensions() != 0) {
                    requiredField.setElementValue(dimensions, new CodeAnnotationValue(field.getDimensions()));
                }

                Element sourceElement = field.getSourceElement();
                docBuilder.string("<li>");
                if (sourceElement != null) {
                    if (sourceElement.getEnclosingElement() == null) {
                        docBuilder.string("{@link ");
                        docBuilder.string("Inlined#");
                        docBuilder.string(sourceElement.getSimpleName().toString());
                        docBuilder.string("}");
                    } else {
                        docBuilder.javadocLink(sourceElement, null);
                    }
                } else {
                    docBuilder.string("Unknown source");
                }
                docBuilder.newLine();

                requiredFields.add(new CodeAnnotationValue(requiredField));
            }
            docBuilder.string("</ul>");
            docBuilder.end();

            ExecutableElement fieldsValue = ElementUtils.findMethod(types.InlineSupport_RequiredFields, "value");
            CodeAnnotationMirror requiredFieldsMirror = new CodeAnnotationMirror(types.InlineSupport_RequiredFields);
            requiredFieldsMirror.setElementValue(fieldsValue, new CodeAnnotationValue(requiredFields));
            inlineTarget.getAnnotationMirrors().add(requiredFieldsMirror);
            body.end();

        }

        body.end();
        return method;
    }

    private static CodeExecutableElement createGetUncached(NodeData node, ExecutableElement constructor) {
        CodeExecutableElement method = CodeExecutableElement.clone(constructor);
        method.setSimpleName(CodeNames.of("getUncached"));
        method.getModifiers().clear();
        method.getModifiers().add(Modifier.PUBLIC);
        method.getModifiers().add(Modifier.STATIC);
        method.setReturnType(node.getNodeType());
        method.getAnnotationMirrors().add(new CodeAnnotationMirror(ProcessorContext.getInstance().getTypes().NeverDefault));
        CodeTreeBuilder body = method.createBuilder();
        body.startReturn();
        TypeMirror type = NodeCodeGenerator.nodeType(node);
        if (node.hasErrors()) {
            body.startNew(type);
            for (VariableElement var : method.getParameters()) {
                body.defaultValue(var.asType());
            }
            body.end();
        } else {
            TypeElement typeElement = ElementUtils.castTypeElement(type);
            body.string(typeElement.getSimpleName().toString(), ".UNCACHED");
        }
        body.end();
        method.getParameters().clear();
        return method;
    }

    private static CodeExecutableElement createCreateMethod(NodeData node, ExecutableElement constructor) {
        CodeExecutableElement method = CodeExecutableElement.clone(constructor);
        method.setSimpleName(CodeNames.of("create"));
        method.getModifiers().clear();
        method.getModifiers().add(Modifier.PUBLIC);
        method.getModifiers().add(Modifier.STATIC);
        method.setReturnType(node.getNodeType());
        method.getAnnotationMirrors().add(new CodeAnnotationMirror(ProcessorContext.getInstance().getTypes().NeverDefault));

        CodeTreeBuilder body = method.createBuilder();
        body.startReturn();
        if (node.getSpecializations().isEmpty()) {
            body.nullLiteral();
        } else {
            body.startNew(NodeCodeGenerator.nodeType(node));
            for (VariableElement var : method.getParameters()) {
                body.string(var.getSimpleName().toString());
            }
            body.end();

        }
        body.end();
        return method;
    }
}
