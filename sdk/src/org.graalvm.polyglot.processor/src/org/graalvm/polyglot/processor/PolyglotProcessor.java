/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import org.graalvm.nativebridge.processor.AbstractProcessor;
import org.graalvm.nativebridge.processor.CodeBuilder;
import org.graalvm.nativebridge.processor.CodeBuilder.Parameter;
import org.graalvm.nativebridge.processor.Utilities;

@SupportedAnnotationTypes({
                PolyglotProcessor.ANNOTATION_TO_MODULE,
})
public class PolyglotProcessor extends AbstractProcessor {

    static final String ANNOTATION_TO_MODULE = "org.graalvm.polyglot.impl.GenerateMethodHandleBridge";

    record BridgeMethod(String id, ExecutableElement executable, ExecutableType type) {
    }

    record BridgeType(String id, TypeMirror type, String localName, String mapFromName, String mapToName) {
    }

    @Override
    protected boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        TypeElement annotationType = getTypeElement(PolyglotProcessor.ANNOTATION_TO_MODULE);
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotationType);
        if (roundEnv.processingOver()) {
            return false;
        }
        PolyglotTypeCache types;
        try {
            types = new PolyglotTypeCache(this);
        } catch (Throwable t) {
            for (Element e : annotatedElements) {
                handleThrowable(t, e);
            }
            return true;
        }
        for (Element e : annotatedElements) {
            try {
                generate(types, parse(types, e));
            } catch (Throwable t) {
                handleThrowable(t, e);
            }
        }
        return true;
    }

    record Model(TypeElement templateType,
                    DeclaredType mappingClass,
                    TypeElement mappingTypeElement,
                    List<BridgeMethod> methods,
                    Map<String, BridgeType> bridgeTypes,
                    PackageElement owner) {
    }

    private Model parse(PolyglotTypeCache types, Element e) {
        TypeElement templateType = (TypeElement) e;
        DeclaredType templateDeclaredType = (DeclaredType) templateType.asType();
        ExecutableElement[] members = ElementFilter.methodsIn(processingEnv.getElementUtils().getAllMembers(templateType)).stream().filter(
                        (m) -> m.getModifiers().contains(Modifier.PUBLIC)).toArray(ExecutableElement[]::new);

        // sort members alphabetically
        Arrays.sort(members, (e1, e2) -> {
            int compare = e1.getSimpleName().toString().compareTo(e2.getSimpleName().toString());
            if (compare == 0) {
                compare = Integer.compare(e1.getParameters().size(), e2.getParameters().size());
                if (compare == 0) {
                    for (int i = 0; i < e1.getParameters().size(); i++) {
                        VariableElement v1 = e1.getParameters().get(i);
                        VariableElement v2 = e2.getParameters().get(i);
                        compare = Utilities.getQualifiedName(v1.asType()).compareTo(Utilities.getQualifiedName(v2.asType()));
                        if (compare != 0) {
                            break;
                        }
                    }
                }
            }
            return compare;
        });

        DeclaredType mappingClass = (DeclaredType) templateType.asType();
        TypeElement mappingTypeElement = (TypeElement) mappingClass.asElement();

        Map<String, Integer> usedNames = new HashMap<>();
        List<BridgeMethod> methods = new ArrayList<>();
        for (ExecutableElement executable : members) {
            Set<Modifier> modifiers = executable.getModifiers();
            if (modifiers.contains(Modifier.FINAL)) {
                continue;
            } else if (modifiers.contains(Modifier.NATIVE)) {
                continue;
            } else if (modifiers.contains(Modifier.STATIC)) {
                continue;
            } else if (typeUtils().isSameType(executable.getEnclosingElement().asType(), types.object)) {
                continue;
            }
            String name = uniqueName(usedNames, executable.getSimpleName().toString());
            ExecutableType methodToGenerateType = (ExecutableType) typeUtils().asMemberOf(templateDeclaredType, executable);
            methods.add(new BridgeMethod(name + "_", executable, methodToGenerateType));
        }

        Map<String, BridgeType> bridgeTypes = new LinkedHashMap<>();
        for (BridgeMethod method : methods) {
            ensureBridgeTypeCreated(usedNames, bridgeTypes, method.type.getReturnType());
            for (TypeMirror signatureType : method.type.getParameterTypes()) {
                ensureBridgeTypeCreated(usedNames, bridgeTypes, signatureType);
            }
        }

        PackageElement owner = Utilities.getEnclosingPackageElement(templateType);

        Model result = new Model(templateType, mappingClass, mappingTypeElement, methods, bridgeTypes, owner);
        return result;
    }

    private void generate(PolyglotTypeCache types, Model model) throws IOException {

        String cacheClassName = "Handles";
        CodeBuilder builder = new CodeBuilder(model.owner, env().getTypeUtils(), types);

        builder.line("/**");
        builder.line(" * Generated code used for Truffle unnamed to named module bridge.");
        builder.line(" * This code allows polyglot on the classpath to delegate to polyglot on the module-path.");
        builder.line(" * This code is generated by the polyglot annotation processor.asdf");
        builder.line(" */");
        CharSequence targetClassSimpleName = model.templateType.getSimpleName() + "Gen";
        builder.classStart(EnumSet.of(Modifier.FINAL), targetClassSimpleName, (DeclaredType) model.templateType.asType(),
                        List.of());
        builder.indent();

        builder.emptyLine();

        builder.lineStart();
        builder.writeModifiers(Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL));
        builder.write(" ").write(cacheClassName).write(" HANDLES").lineEnd(";");

        builder.line("static {");
        builder.indent();
        builder.lineStart();
        builder.write(types.lookup).write(" lookup = methodHandleLookup()").lineEnd(";");
        builder.line("if (lookup != null) {").indent();
        builder.line("try {").indent();
        builder.lineStart("HANDLES = new ").write(cacheClassName).write("(lookup)").lineEnd(";");

        builder.dedent().line("} catch (ReflectiveOperationException e) {").indent();
        builder.line("throw new InternalError(\"Failed to initialize method handles for module bridge.\", e);");
        builder.dedent().line("}");

        builder.dedent().line("} else {").indent(); // end if
        builder.line("HANDLES = null;");
        builder.dedent().line("}");

        builder.dedent().line("}"); // end static

        builder.emptyLine();
        builder.lineStart();
        builder.writeModifiers(Set.of(Modifier.FINAL));
        builder.write(" ").write(types.object).write(" receiver").lineEnd(";");

        for (ExecutableElement c : ElementFilter.constructorsIn(model.templateType.getEnclosedElements())) {
            builder.emptyLine();
            List<Parameter> superParams = CodeBuilder.newParameters(c.getParameters(), ((ExecutableType) c.asType()).getParameterTypes(), c.isVarArgs());
            List<Parameter> params = new ArrayList<>(superParams);
            params.add(CodeBuilder.newParameter(types.object, "receiver"));
            builder.methodStart(Set.of(Modifier.PUBLIC), targetClassSimpleName,
                            null, params, c.getThrownTypes(),
                            c.getTypeParameters());
            builder.indent();
            if (!superParams.isEmpty()) {
                builder.lineStart();
                builder.write("super(");
                builder.writeCommaList(superParams, (b, param) -> {
                    b.write(param.name);
                });
                builder.write(")");
                builder.lineEnd(";");
            }
            builder.lineStart();
            builder.write("this.receiver = ").write(types.objects).write(".requireNonNull(receiver)");
            builder.lineEnd(";");
            builder.dedent().line("}");
        }

        Set<BridgeType> convertedFrom = new LinkedHashSet<>();
        Set<BridgeType> convertedTo = new LinkedHashSet<>();

        for (BridgeMethod bridgeMethod : model.methods) {
            ExecutableElement executable = bridgeMethod.executable();
            ExecutableType type = bridgeMethod.type();

            List<Parameter> parameters = new ArrayList<>();
            int index = 0;
            for (VariableElement var : executable.getParameters()) {
                TypeMirror paramType = type.getParameterTypes().get(index);
                String paramName = var.getSimpleName().toString() + "_";
                boolean isVarArgs = index == executable.getParameters().size() - 1 && executable.isVarArgs();
                parameters.add(CodeBuilder.newParameter(paramType, paramName, isVarArgs));
                index++;
            }

            builder.emptyLine();
            builder.lineStart().annotation(types.override, null).lineEnd("");
            builder.methodStart(Set.of(Modifier.PUBLIC), executable.getSimpleName(),
                            bridgeMethod.type.getReturnType(), parameters, List.of(),
                            bridgeMethod.executable.getTypeParameters());
            builder.indent();

            TypeMirror returnType = bridgeMethod.type().getReturnType();
            boolean isVoid = returnType.getKind() == TypeKind.VOID;

            builder.line("try {");
            builder.indent();

            builder.lineStart();
            if (!isVoid) {
                if (isPolyglotType(returnType)) {
                    builder.write(types.object);
                    builder.write(" result = ");
                } else {
                    builder.write(returnType);
                    builder.write(" result = ");
                    if (!typeUtils().isSameType(types.object, returnType)) {
                        builder.write("(").write(returnType).write(") ");
                    }
                }
            }
            builder.write("HANDLES.").write(bridgeMethod.id());
            builder.write(".invoke(");
            builder.write("receiver");
            if (!parameters.isEmpty()) {
                builder.write(", ");
            }
            builder.writeCommaList(parameters, (b, param) -> {
                if (isPolyglotType(param.type)) {
                    BridgeType bridgeType = model.bridgeTypes.get(getPolyglotQualifiedName(param.type));
                    convertedTo.add(bridgeType);
                    b.write(model.mappingClass).write(".");
                    b.write(bridgeType.mapToName).write("(");
                    b.write(param.name);
                    b.write(")");
                } else {
                    b.write(param.name);
                }
            });
            builder.write(")");
            builder.lineEnd(";");

            if (!isVoid) {
                builder.lineStart("return ");
                if (isPolyglotType(returnType)) {
                    BridgeType bridgeType = model.bridgeTypes.get(getPolyglotQualifiedName(returnType));
                    convertedFrom.add(bridgeType);
                    builder.write(model.mappingClass).write(".");
                    builder.write(bridgeType.mapFromName());
                    builder.write("(result)");
                } else {
                    builder.write("result");
                }
                builder.lineEnd(";");
            }

            builder.dedent().line("} catch (Throwable t) {");
            builder.indent();
            builder.line("throw handleException_(t);");
            builder.dedent().line("}");

            builder.dedent();
            builder.line("}");
        }

        builder.emptyLine();
        builder.lineStart().annotation(types.suppressWarnings, "unchecked").lineEnd("");
        builder.lineStart();
        builder.writeModifiers(Set.of(Modifier.PRIVATE, Modifier.STATIC));
        builder.write(" ").write("<T extends ").write(types.throwable).write(">");
        builder.write(" ").write(types.runtimeException);
        builder.write(" ").write("handleException_(").write(types.throwable).write(" t) throws T ").lineEnd("{");
        builder.indent();
        builder.line("throw (T)t;");
        builder.dedent();
        builder.line("}"); // constructor end
        builder.emptyLine();

        emitMissingConverterMethodsError(model.owner, types, convertedFrom, convertedTo, model.templateType, model.mappingTypeElement);

        // Cache Class
        builder.classStart(EnumSet.of(Modifier.FINAL, Modifier.STATIC), cacheClassName, types.object,
                        List.of());
        builder.indent();

        builder.emptyLine();
        for (BridgeMethod bridgeMethod : model.methods) {
            builder.lineStart();
            builder.writeModifiers(Set.of(Modifier.PRIVATE, Modifier.FINAL));
            builder.write(" ").write(types.methodHandle).write(" ");
            builder.write(bridgeMethod.id()).lineEnd(";");
        }
        builder.emptyLine();

        List<Parameter> params = new ArrayList<>();
        params.add(CodeBuilder.newParameter(types.lookup, "lookup"));
        builder.methodStart(Set.of(), cacheClassName, null, params, List.of(types.reflectiveOperationException));
        builder.indent();

        for (BridgeType bridgeType : model.bridgeTypes.values()) {
            builder.lineStart();
            builder.write(types.clazz).write(" ").write(bridgeType.localName()).write(" = ");
            builder.write("lookup.findClass(");
            builder.classLiteral(bridgeType.type()).write(".getName()");
            builder.write(")");
            builder.lineEnd(";");
        }

        Map<String, Integer> names = new HashMap<>();
        Map<String, String> lookupTypes = new HashMap<>();
        for (BridgeMethod bridgeMethod : model.methods) {
            ExecutableElement executable = bridgeMethod.executable();
            ExecutableElement override = findRootOverride(executable);
            Element enclosing = override.getEnclosingElement();

            String qualifiedName = Utilities.getQualifiedName(enclosing.asType());
            String prev = lookupTypes.get(qualifiedName);
            if (prev == null) {
                String localName = "type" + uniqueName(names, enclosing.getSimpleName().toString());
                builder.lineStart();
                builder.write(types.clazz).write(" ").write(localName).write(" = lookup.findClass(");
                builder.classLiteral(enclosing.asType()).write(".getName())");
                builder.lineEnd(";");
                lookupTypes.put(qualifiedName, localName);
            }
        }

        for (BridgeMethod bridgeMethod : model.methods) {
            ExecutableElement executable = bridgeMethod.executable();
            ExecutableElement override = findRootOverride(executable);
            Element enclosing = override.getEnclosingElement();

            ExecutableType type = bridgeMethod.type();
            String lookupTypeLocalName = lookupTypes.get(Utilities.getQualifiedName(enclosing.asType()));
            builder.lineStart();
            builder.write("this.").write(bridgeMethod.id()).write(" = ");
            builder.write("lookup.findVirtual(").write(lookupTypeLocalName).write(", \"");
            builder.write(executable.getSimpleName()).write("\", ");
            builder.write(types.methodType).write(".methodType(");
            mapPolyglotType(builder, model.bridgeTypes, type.getReturnType());
            builder.write(", ");
            builder.write(typeUtils().erasure(types.list)).write(".of(");
            builder.writeCommaList(type.getParameterTypes(), (b, parameter) -> {
                mapPolyglotType(b, model.bridgeTypes, parameter);
            });
            builder.write(")))");
            builder.lineEnd(";");
        }

        builder.dedent();
        builder.line("}"); // constructor end

        builder.dedent();
        builder.line("}");  // Cache Class End
        builder.emptyLine();

        builder.dedent();
        builder.line("}");  // Enclosing class end
        writeSourceFile(model.templateType, targetClassSimpleName, builder.build());
    }

    private ExecutableElement findRootOverride(ExecutableElement executable) {
        TypeElement type = (TypeElement) executable.getEnclosingElement();
        TypeMirror superType = type.getSuperclass();
        ExecutableElement currentExecutable = executable;
        while (superType != null) {
            TypeElement superTypeElement = (TypeElement) processingEnv.getTypeUtils().asElement(superType);
            if (superTypeElement != null) {
                for (ExecutableElement method : ElementFilter.methodsIn(superTypeElement.getEnclosedElements())) {
                    if (processingEnv.getElementUtils().overrides(executable, method, type)) {
                        currentExecutable = method;
                        break;
                    }
                }
            }
            superType = superTypeElement != null ? superTypeElement.getSuperclass() : null;
        }
        return currentExecutable;

    }

    private void emitMissingConverterMethodsError(PackageElement owner, PolyglotTypeCache types, Set<BridgeType> convertedFrom, Set<BridgeType> convertedTo, TypeElement templateType,
                    TypeElement mappingTypeElement) {
        CodeBuilder warning = new CodeBuilder(owner, typeUtils(), types);

        Map<String, ExecutableElement> staticMethods = ElementFilter.methodsIn(processingEnv.getElementUtils().getAllMembers(mappingTypeElement)).stream().//
                        filter((ExecutableElement executable) -> executable.getModifiers().contains(Modifier.STATIC)).//
                        filter((ExecutableElement executable) -> !executable.getModifiers().contains(Modifier.PRIVATE)).//
                        collect(Collectors.toMap((ExecutableElement executable) -> executable.getSimpleName().toString(), Function.identity()));

        Set<ExecutableElement> usedMethods = new HashSet<>();

        for (BridgeType type : convertedFrom) {
            ExecutableElement method = staticMethods.get(type.mapFromName);
            if (method == null) {
                warning.methodStart(Set.of(Modifier.STATIC), type.mapFromName, type.type,
                                List.of(CodeBuilder.newParameter(types.object, "value")), List.of());
                warning.indent();
                warning.line("return null;");
                warning.dedent().line("}");
            } else {
                usedMethods.add(method);
            }
        }

        for (BridgeType type : convertedTo) {
            ExecutableElement method = staticMethods.get(type.mapToName);
            if (method == null) {
                warning.methodStart(Set.of(Modifier.STATIC), type.mapToName, types.object,
                                List.of(CodeBuilder.newParameter(type.type, "value")), List.of());
                warning.indent();
                warning.line("return null;");
                warning.dedent().line("}");
            } else {
                usedMethods.add(method);
            }
        }

        Set<ExecutableElement> unusedMethods = new HashSet<>(staticMethods.values());
        unusedMethods.removeAll(usedMethods);

        for (ExecutableElement unusedMethod : unusedMethods) {
            String name = unusedMethod.getSimpleName().toString();
            if (name.startsWith("from") || name.startsWith("to")) {
                message(Kind.WARNING, unusedMethod, "This converter method is not used by bridge generator. Remove it.");
            }
        }

        unusedMethods.removeAll(usedMethods);

        String missingConverters = warning.buildBody();
        if (!missingConverters.isEmpty()) {
            message(Kind.ERROR, templateType, "The generated type is missing the following methods in %s:%n%s", templateType.getSimpleName().toString(), missingConverters);
        }
    }

    private static void ensureBridgeTypeCreated(Map<String, Integer> usedNames, Map<String, BridgeType> bridgeTypes, TypeMirror signatureType) {
        if (isPolyglotType(signatureType)) {
            String qualifiedName = getPolyglotQualifiedName(signatureType);
            if (!bridgeTypes.containsKey(qualifiedName)) {
                String id = uniqueName(usedNames, getPolyglotSimpleName(signatureType));
                String fieldName = "type" + id + "_";
                String fromName = "from" + id;
                String toName = "to" + id;
                bridgeTypes.put(qualifiedName, new BridgeType(id, signatureType, fieldName, fromName, toName));
            }
        }
    }

    private static CodeBuilder mapPolyglotType(CodeBuilder builder, Map<String, BridgeType> bridgeTypes, TypeMirror type) {
        if (isPolyglotType(type)) {
            String qualifiedName = getPolyglotQualifiedName(type);
            BridgeType bridgeType = bridgeTypes.get(qualifiedName);
            Objects.requireNonNull(bridgeType);
            builder.write(bridgeType.localName());
        } else {
            builder.classLiteral(type);
        }
        return builder;
    }

    private static String getPolyglotQualifiedName(TypeMirror type) {
        switch (type.getKind()) {
            case DECLARED:
                return Utilities.getQualifiedName(type);
            case ARRAY:
                return getPolyglotQualifiedName(((ArrayType) type).getComponentType()) + "Array";
        }
        throw new AssertionError();
    }

    private static String getPolyglotSimpleName(TypeMirror type) {
        switch (type.getKind()) {
            case DECLARED:
                return Utilities.getSimpleName(type);
            case ARRAY:
                return getPolyglotSimpleName(((ArrayType) type).getComponentType()) + "Array";
        }
        throw new AssertionError();
    }

    private static boolean isPolyglotType(TypeMirror type) {
        switch (type.getKind()) {
            case DECLARED:
                String qualifiedName = Utilities.getQualifiedName(type);
                if (qualifiedName.startsWith("org.graalvm.polyglot")) {
                    return true;
                } else if (qualifiedName.startsWith("org.graalvm.options")) {
                    return true;
                } else if (qualifiedName.startsWith("org.graalvm.home")) {
                    return true;
                } else if (qualifiedName.startsWith("org.graalvm.collections")) {
                    return true;
                }
                break;
            case ARRAY:
                return isPolyglotType(((ArrayType) type).getComponentType());
        }
        return false;
    }

    private void handleThrowable(Throwable t, Element e) {
        String message = "Uncaught error while processing " + e + " ";
        message(Kind.ERROR, e, message + ": " + printException(t));
    }

    private static String printException(Throwable e) {
        StringWriter string = new StringWriter();
        PrintWriter writer = new PrintWriter(string);
        e.printStackTrace(writer);
        writer.flush();
        string.flush();
        return e.getMessage() + System.lineSeparator() + string.toString();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(ANNOTATION_TO_MODULE);
    }

    public void message(Kind kind, Element element, String format, Object... args) {
        AnnotationMirror usedMirror = null;
        Element usedElement = element;
        AnnotationValue usedValue = null;
        String message = String.format(format, args);
        processingEnv.getMessager().printMessage(kind, message, usedElement, usedMirror, usedValue);
    }

    public void message(Kind kind, Element element, AnnotationMirror mirror, AnnotationValue value, String format, Object... args) {
        AnnotationMirror usedMirror = mirror;
        Element usedElement = element;
        AnnotationValue usedValue = value;
        String message = String.format(format, args);
        processingEnv.getMessager().printMessage(kind, message, usedElement, usedMirror, usedValue);
    }

    static String uniqueName(Map<String, Integer> usedNames, String name) {
        Integer value = usedNames.get(name);
        if (value == null) {
            value = 0;
        } else {
            value++;
        }
        usedNames.put(name, value);
        String idSuffix = value == 0 ? "" : value.toString();
        return name + idSuffix;
    }

    private void writeSourceFile(TypeElement annotatedElement, CharSequence targetClassSimpleName, String content) throws IOException {
        String sourceFileFQN = String.format("%s.%s",
                        Utilities.getEnclosingPackageElement(annotatedElement).getQualifiedName().toString(),
                        targetClassSimpleName);
        JavaFileObject sourceFile = env().getFiler().createSourceFile(sourceFileFQN, annotatedElement);
        try (PrintWriter out = new PrintWriter(sourceFile.openWriter())) {
            out.print(content);
        }
    }

}
