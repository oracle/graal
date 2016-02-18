/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.dsl.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;

/**
 * THIS IS NOT PUBLIC API.
 */
public final class InteropProcessor extends AbstractProcessor {

    private static final List<Message> KNOWN_MESSAGES = Arrays.asList(new Message[]{Message.READ, Message.WRITE, Message.IS_NULL, Message.IS_EXECUTABLE, Message.IS_BOXED, Message.HAS_SIZE,
                    Message.GET_SIZE, Message.UNBOX, Message.createExecute(0), Message.createInvoke(0), Message.createNew(0)});

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        annotations.add("com.oracle.truffle.api.interop.AcceptMessage");
        return annotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        Map<String, FactoryGenerator> factoryGenerators = new HashMap<>();
        List<String> generatedClasses = new LinkedList<>();

        top: for (Element e : roundEnv.getElementsAnnotatedWith(AcceptMessage.class)) {
            if (e.getKind() != ElementKind.CLASS) {
                continue;
            }
            AcceptMessage message = e.getAnnotation(AcceptMessage.class);
            if (message == null) {
                continue;
            }
            TypeMirror extending = ((TypeElement) e).getSuperclass();
            if (extending.getKind() != TypeKind.ERROR) {
                continue;
            }

            final String pkg = findPkg(e);
            String receiverTypeFullClassName = getReceiverTypeFullClassName(message);
            final String receiverTypeSimpleClass = receiverTypeFullClassName.substring(receiverTypeFullClassName.lastIndexOf(".") + 1);
            final String receiverTypePackage = receiverTypeFullClassName.substring(0, receiverTypeFullClassName.lastIndexOf("."));
            final String factoryShortClassName = receiverTypeSimpleClass + "Foreign";
            final String factoryFullClassName = receiverTypeFullClassName + "Foreign";

            String truffleLanguageFullClazzName = getTruffleLanguageFullClassName(message);

            final String clazzName = extending.toString();
            String fqn = pkg + "." + clazzName;
            String messageName = message.value();

            MessageGenerator currentGenerator = null;
            Object currentMessage = null;
            try {
                currentMessage = Message.valueOf(messageName);
            } catch (IllegalArgumentException ex) {
                TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(message.value());
                TypeElement messageElement = processingEnv.getElementUtils().getTypeElement(Message.class.getName());
                if (typeElement != null && processingEnv.getTypeUtils().isAssignable(typeElement.asType(), messageElement.asType())) {
                    currentMessage = messageName;
                }
            }
            if (currentMessage != null) {
                if (Message.READ.toString().equalsIgnoreCase(messageName)) {
                    currentGenerator = new ReadGenerator(processingEnv, e, pkg, clazzName, fqn, messageName, ((TypeElement) e).getSimpleName().toString(), truffleLanguageFullClazzName);
                } else if (Message.WRITE.toString().equalsIgnoreCase(messageName)) {
                    currentGenerator = new WriteGenerator(processingEnv, e, pkg, clazzName, fqn, messageName, ((TypeElement) e).getSimpleName().toString(), truffleLanguageFullClazzName);
                } else if (Message.IS_NULL.toString().equalsIgnoreCase(messageName) || Message.IS_EXECUTABLE.toString().equalsIgnoreCase(messageName) ||
                                Message.IS_BOXED.toString().equalsIgnoreCase(messageName) || Message.HAS_SIZE.toString().equalsIgnoreCase(messageName) ||
                                Message.GET_SIZE.toString().equalsIgnoreCase(messageName) || Message.UNBOX.toString().equalsIgnoreCase(messageName)) {
                    currentGenerator = new UnaryGenerator(processingEnv, e, pkg, clazzName, fqn, messageName, ((TypeElement) e).getSimpleName().toString(), truffleLanguageFullClazzName);
                } else if (Message.createExecute(0).toString().equalsIgnoreCase(messageName) || Message.createInvoke(0).toString().equalsIgnoreCase(messageName) ||
                                Message.createNew(0).toString().equalsIgnoreCase(messageName)) {
                    currentGenerator = new ExecuteGenerator(processingEnv, e, pkg, clazzName, fqn, messageName, ((TypeElement) e).getSimpleName().toString(), truffleLanguageFullClazzName);
                } else {
                    assert !KNOWN_MESSAGES.contains(currentMessage);
                    currentGenerator = new GenericGenerator(processingEnv, e, pkg, clazzName, fqn, messageName.substring(messageName.lastIndexOf('.') + 1),
                                    ((TypeElement) e).getSimpleName().toString(),
                                    truffleLanguageFullClazzName);
                }
            }

            if (currentGenerator == null) {
                generateErrorClass(e, pkg, fqn, clazzName, null);
                emitError("Unknown message type: " + message.value(), e);
                continue;
            }

            if (isInstanceMissing(receiverTypeFullClassName)) {
                generateErrorClass(e, pkg, fqn, clazzName, null);
                emitError("Missing isInstance method in class " + receiverTypeFullClassName, e);
                continue;
            }

            if (!e.getModifiers().contains(javax.lang.model.element.Modifier.FINAL)) {
                generateErrorClass(e, pkg, fqn, clazzName, null);
                emitError("Class must be final", e);
                continue;
            }

            List<ExecutableElement> methods = currentGenerator.getAccessMethods();
            if (methods.isEmpty()) {
                generateErrorClass(e, pkg, fqn, clazzName, null);
                emitError("There needs to be at least one access method.", e);
                continue;
            }

            List<? extends VariableElement> params = methods.get(0).getParameters();
            int argumentSize = params.size();
            for (ExecutableElement m : methods) {
                params = m.getParameters();
                if (argumentSize != params.size()) {
                    generateErrorClass(e, pkg, fqn, clazzName, methods);
                    emitError("Inconsistent argument length.", e);
                    continue top;
                }
            }

            for (ExecutableElement m : methods) {
                String errorMessage = currentGenerator.checkSignature(m);
                if (errorMessage != null) {
                    generateErrorClass(e, pkg, fqn, clazzName, null);
                    emitError(errorMessage, m);
                    continue top;
                }
            }

            if (generatedClasses.contains(fqn)) {
                emitError("Base class name already in use.", e);
                continue;
            }

            currentGenerator.generate(generatedClasses);

            if (!factoryGenerators.containsKey(receiverTypeFullClassName)) {
                try {
                    factoryGenerators.put(receiverTypeFullClassName, new FactoryGenerator(receiverTypePackage, factoryShortClassName, receiverTypeFullClassName,
                                    processingEnv.getFiler().createSourceFile(factoryFullClassName, e)));
                } catch (IOException e1) {
                    throw new IllegalStateException(e1);
                }
            }
            FactoryGenerator factoryGenerator = factoryGenerators.get(receiverTypeFullClassName);
            factoryGenerator.addMessageHandler(currentMessage, currentGenerator.getRootNodeFactoryInvokation());
        }

        for (FactoryGenerator fg : factoryGenerators.values()) {
            fg.generate();
        }

        return true;
    }

    private void generateErrorClass(Element e, final String pkg, final String fqn, final String clazzName, List<ExecutableElement> methods) {
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(fqn, e);
            Writer w = file.openWriter();
            w.append("package ").append(pkg).append(";\n");

            w.append("abstract class ").append(clazzName).append(" {\n");
            w.append("  // An error occured, fix ").append(e.getSimpleName()).append(" first.\n");
            if (methods != null) {
                for (ExecutableElement m : methods) {
                    generateAbstractMethod(w, m, null);
                }
            }
            w.append("}\n");
            w.close();
        } catch (IOException ex1) {
            emitError(ex1.getMessage(), e);
        }
    }

    private boolean isInstanceMissing(String receiverTypeFullClassName) {
        for (Element elem : this.processingEnv.getElementUtils().getTypeElement(receiverTypeFullClassName).getEnclosedElements()) {
            if (elem.getKind().equals(ElementKind.METHOD)) {
                ExecutableElement method = (ExecutableElement) elem;
                if (method.getSimpleName().toString().equals("isInstance") && method.getParameters().size() == 1 &&
                                method.getParameters().get(0).asType().toString().equals(TruffleObject.class.getName())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String getReceiverTypeFullClassName(AcceptMessage message) {
        String receiverTypeFullClassName;
        try {
            receiverTypeFullClassName = message.receiverType().getName();
        } catch (MirroredTypeException mte) {
            // wow, annotations processors are strange
            receiverTypeFullClassName = mte.getTypeMirror().toString();
        }
        return receiverTypeFullClassName;
    }

    private static String getTruffleLanguageFullClassName(AcceptMessage message) {
        String truffleLanguageFullClazzName;
        try {
            truffleLanguageFullClazzName = message.language().getName();
        } catch (MirroredTypeException mte) {
            // wow, annotations processors are strange
            truffleLanguageFullClazzName = mte.getTypeMirror().toString();
        }
        return truffleLanguageFullClazzName;
    }

    private static String findPkg(Element e) {
        Element curr = e;
        for (;;) {
            if (curr.getKind() == ElementKind.PACKAGE) {
                return ((PackageElement) curr).getQualifiedName().toString();
            }
            curr = curr.getEnclosingElement();
        }
    }

    private void emitError(String msg, Element e) {
        if (ExpectError.isExpectedError(processingEnv, e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.ERROR, msg, e);
    }

    static void generateAbstractMethod(Writer w, ExecutableElement method, String extraName) throws IOException {
        String methodName = extraName == null ? method.getSimpleName().toString() : extraName;
        w.append("  protected abstract ").append(method.getReturnType().toString()).append(" ").append(methodName);
        w.append("(");
        final List<? extends VariableElement> params = method.getParameters();
        String sep = "";
        for (VariableElement p : params) {
            w.append(sep).append(p.asType().toString()).append(" ").append(p.getSimpleName());
            sep = ", ";
        }
        w.append(");\n");
    }

    private abstract static class MessageGenerator {
        protected static final String ACCESS_METHOD_NAME = "access";

        protected final Element e;
        protected final String pkg;
        protected final String clazzName;
        protected final String methodName;
        protected final String fullClazzName;
        protected final String userClassName;
        protected final String truffleLanguageFullClazzName;
        protected final ProcessingEnvironment processingEnv;

        MessageGenerator(ProcessingEnvironment processingEnv, Element e, final String pkg, final String clazzName, String fullClazzName, String methodName, String userClassName,
                        String truffleLanguageFullClazzName) {
            this.processingEnv = processingEnv;
            this.e = e;
            this.pkg = pkg;
            this.clazzName = clazzName;
            this.fullClazzName = fullClazzName;
            this.methodName = methodName;
            this.userClassName = userClassName;
            this.truffleLanguageFullClazzName = truffleLanguageFullClazzName;
        }

        final void generate(List<String> generatedClasses) {
            try {
                generatedClasses.add(fullClazzName);
                JavaFileObject file = processingEnv.getFiler().createSourceFile(fullClazzName, e);
                Writer w = file.openWriter();
                w.append("package ").append(pkg).append(";\n");
                appendImports(w);

                w.append("abstract class ").append(clazzName).append(" extends Node {\n");
                appendAbstractMethods(w);
                appendTargetableNode(w);
                appendRootNode(w);
                appendRootNodeFactory(w);

                w.append("}\n");
                w.close();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        final List<ExecutableElement> getAccessMethods() {
            List<ExecutableElement> methods = new ArrayList<>();
            for (Element m : e.getEnclosedElements()) {
                if (m.getKind() != ElementKind.METHOD) {
                    continue;
                }
                if (!m.getSimpleName().contentEquals(ACCESS_METHOD_NAME)) {
                    continue;
                }
                ExecutableElement method = (ExecutableElement) m;
                methods.add(method);
            }
            return methods;
        }

        void appendImports(Writer w) throws IOException {
            w.append("import com.oracle.truffle.api.nodes.Node;").append("\n");
            w.append("import com.oracle.truffle.api.frame.VirtualFrame;").append("\n");
            w.append("import com.oracle.truffle.api.dsl.Specialization;").append("\n");
            w.append("import com.oracle.truffle.api.nodes.RootNode;").append("\n");
            w.append("import com.oracle.truffle.api.TruffleLanguage;").append("\n");
            w.append("import com.oracle.truffle.api.interop.ForeignAccess;").append("\n");
            w.append("import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;").append("\n");
            w.append("import com.oracle.truffle.api.interop.UnsupportedTypeException;").append("\n");
        }

        abstract int getParameterCount();

        void appendAbstractMethods(Writer w) throws IOException {
            for (ExecutableElement method : getAccessMethods()) {
                generateAbstractMethod(w, method, ACCESS_METHOD_NAME);
            }
        }

        abstract String checkSignature(ExecutableElement method);

        abstract String getTargetableNodeName();

        void appendTargetableNode(Writer w) throws IOException {
            String sep = "";
            w.append("    protected abstract static class ").append(getTargetableNodeName()).append(" extends Node {\n");
            w.append("\n");
            w.append("        @Child ").append(clazzName).append(" child = new ").append(userClassName).append("();\n");
            w.append("\n");
            w.append("        public abstract Object executeWithTarget(VirtualFrame frame, ");
            sep = "";
            for (int i = 0; i < getParameterCount() - 1; i++) {
                w.append(sep).append("Object ").append("o").append(String.valueOf(i));
                sep = ", ";
            }
            w.append(");\n");

            for (ExecutableElement method : getAccessMethods()) {
                final List<? extends VariableElement> params = method.getParameters();

                w.append("        @Specialization\n");
                w.append("        protected Object ").append(ACCESS_METHOD_NAME).append("WithTarget");
                w.append("(");

                sep = "";
                for (VariableElement p : params) {
                    w.append(sep).append(p.asType().toString()).append(" ").append(p.getSimpleName());
                    sep = ", ";
                }
                w.append(") {\n");
                w.append("            return child.").append(ACCESS_METHOD_NAME).append("(");
                sep = "";
                for (VariableElement p : params) {
                    w.append(sep).append(p.getSimpleName());
                    sep = ", ";
                }
                w.append(");\n");
                w.append("        }\n");
            }
            w.append("    }\n");
        }

        abstract void appendRootNode(Writer w) throws IOException;

        abstract String getRootNodeName();

        void appendRootNodeFactory(Writer w) throws IOException {
            w.append("    public static RootNode createRoot(Class<? extends TruffleLanguage<?>> language) {\n");
            w.append("        return new ").append(getRootNodeName()).append("(language);\n");
            w.append("    }\n");

        }

        String getRootNodeFactoryInvokation() {
            return ((TypeElement) e).asType().toString() + ".createRoot(" + truffleLanguageFullClazzName + ".class)";
        }

        @Override
        public String toString() {
            return clazzName;
        }

    }

    // IsNull IsExecutable IsBoxed HasSize
    // GetSize Unbox
    private static class UnaryGenerator extends MessageGenerator {

        private static final int NUMBER_OF_UNARY = 2; // VirtualFrame frame, TruffleObject receiver
        private final String targetableUnaryNode;
        private final String unaryRootNode;

        UnaryGenerator(ProcessingEnvironment processingEnv, Element e, String pkg, String clazzName, String fullClazzName, String methodName, String userClassName,
                        String truffleLanguageFullClazzName) {
            super(processingEnv, e, pkg, clazzName, fullClazzName, methodName, userClassName, truffleLanguageFullClazzName);
            this.targetableUnaryNode = (new StringBuilder(methodName)).replace(0, 1, methodName.substring(0, 1).toUpperCase()).append("Node").insert(0, "Targetable").toString();
            this.unaryRootNode = (new StringBuilder(methodName)).replace(0, 1, methodName.substring(0, 1).toUpperCase()).append("RootNode").toString();
        }

        @Override
        int getParameterCount() {
            return NUMBER_OF_UNARY;
        }

        @Override
        String getTargetableNodeName() {
            return targetableUnaryNode;
        }

        @Override
        void appendRootNode(Writer w) throws IOException {
            w.append("    private final static class ").append(unaryRootNode).append(" extends RootNode {\n");
            w.append("        protected ").append(unaryRootNode).append("(Class<? extends TruffleLanguage<?>> language) {\n");
            w.append("            super(language, null, null);\n");
            w.append("        }\n");
            w.append("\n");
            w.append("        @Child private ").append(targetableUnaryNode).append(" node = ").append(pkg).append(".").append(clazzName).append("Factory.").append(targetableUnaryNode).append(
                            "Gen.create();\n");
            w.append("\n");
            w.append("        @Override\n");
            w.append("        public Object execute(VirtualFrame frame) {\n");
            w.append("            Object receiver = ForeignAccess.getReceiver(frame);\n");
            w.append("            try {\n");
            w.append("                return node.executeWithTarget(frame, receiver);\n");
            w.append("            } catch (UnsupportedSpecializationException e) {\n");
            w.append("                throw UnsupportedTypeException.raise(e.getSuppliedValues());\n");
            w.append("            }\n");
            w.append("        }\n");
            w.append("\n");
            w.append("    }\n");
        }

        @Override
        String getRootNodeName() {
            return unaryRootNode;
        }

        @Override
        String checkSignature(ExecutableElement method) {
            final List<? extends VariableElement> params = method.getParameters();
            if (params.size() != NUMBER_OF_UNARY) {
                return ACCESS_METHOD_NAME + " method has to have " + getParameterCount() + " arguments";
            }
            if (!params.get(0).asType().toString().equals(VirtualFrame.class.getName())) {
                return "The first argument of " + ACCESS_METHOD_NAME + " must be of type " + VirtualFrame.class.getName() + "- but is " + params.get(0).asType().toString();
            }
            return null;
        }
    }

    private static class ExecuteGenerator extends MessageGenerator {

        private final int numberOfArguments;
        // Execute: VirtualFrame frame, TruffleObject receiver, Object[] args
        // Invoke: VirtualFrame frame, TruffleObject receiver, String identifier, Object[] args
        // New: VirtualFrame frame, TruffleObject receiver, Object[] args
        private final String targetableExecuteNode;
        private final String executeRootNode;

        ExecuteGenerator(ProcessingEnvironment processingEnv, Element e, String pkg, String clazzName, String fullClazzName, String methodName, String userClassName,
                        String truffleLanguageFullClazzName) {
            super(processingEnv, e, pkg, clazzName, fullClazzName, methodName, userClassName, truffleLanguageFullClazzName);
            this.targetableExecuteNode = (new StringBuilder(methodName)).replace(0, 1, methodName.substring(0, 1).toUpperCase()).append("Node").insert(0, "Targetable").toString();
            this.executeRootNode = (new StringBuilder(methodName)).replace(0, 1, methodName.substring(0, 1).toUpperCase()).append("RootNode").toString();
            if (Message.createExecute(0).toString().equalsIgnoreCase(methodName)) {
                numberOfArguments = 3;
            } else if (Message.createInvoke(0).toString().equalsIgnoreCase(methodName)) {
                numberOfArguments = 4;
            } else if (Message.createNew(0).toString().equalsIgnoreCase(methodName)) {
                numberOfArguments = 3;
            } else {
                throw new AssertionError();
            }
        }

        @Override
        void appendImports(Writer w) throws IOException {
            super.appendImports(w);
            w.append("import java.util.List;").append("\n");
            w.append("import com.oracle.truffle.api.nodes.ExplodeLoop;").append("\n");
        }

        @Override
        int getParameterCount() {
            return numberOfArguments;
        }

        @Override
        String getTargetableNodeName() {
            return targetableExecuteNode;
        }

        @Override
        void appendRootNode(Writer w) throws IOException {
            w.append("    private final static class ").append(executeRootNode).append(" extends RootNode {\n");
            w.append("        protected ").append(executeRootNode).append("(Class<? extends TruffleLanguage<?>> language) {\n");
            w.append("            super(language, null, null);\n");
            w.append("        }\n");
            w.append("\n");
            w.append("        @Child private ").append(targetableExecuteNode).append(" node = ").append(pkg).append(".").append(clazzName).append("Factory.").append(targetableExecuteNode).append(
                            "Gen.create();\n");
            w.append("\n");
            w.append("        @Override\n");
            w.append("        @ExplodeLoop\n");
            w.append("        public Object execute(VirtualFrame frame) {\n");
            w.append("            try {\n");
            w.append("              Object receiver = ForeignAccess.getReceiver(frame);\n");
            if (Message.createInvoke(0).toString().equalsIgnoreCase(methodName)) {
                w.append("              List<Object> arguments = ForeignAccess.getArguments(frame);\n");
                w.append("              Object identifier = arguments.get(0);\n");
                w.append("              Object[] args = new Object[arguments.size() - 1];\n");
                w.append("              for (int i = 0; i < arguments.size() - 1; i++) {\n");
                w.append("                args[i] = arguments.get(i + 1);\n");
                w.append("              }\n");
                w.append("              return node.executeWithTarget(frame, receiver, identifier, args);\n");
            } else {
                w.append("              List<Object> arguments = ForeignAccess.getArguments(frame);\n");
                w.append("              Object[] args = new Object[arguments.size()];\n");
                w.append("              for (int i = 0; i < arguments.size(); i++) {\n");
                w.append("                args[i] = arguments.get(i);\n");
                w.append("              }\n");
                w.append("              return node.executeWithTarget(frame, receiver, args);\n");

            }
            w.append("            } catch (UnsupportedSpecializationException e) {\n");
            w.append("                throw UnsupportedTypeException.raise(e.getSuppliedValues());\n");
            w.append("            }\n");
            w.append("        }\n");
            w.append("\n");
            w.append("    }\n");
        }

        @Override
        String getRootNodeName() {
            return executeRootNode;
        }

        @Override
        String checkSignature(ExecutableElement method) {
            final List<? extends VariableElement> params = method.getParameters();
            if (params.size() != numberOfArguments) {
                return ACCESS_METHOD_NAME + " method has to have " + getParameterCount() + " arguments";
            }
            if (!params.get(0).asType().toString().equals(VirtualFrame.class.getName())) {
                return "The first argument must be a " + VirtualFrame.class.getName() + "- but is " + params.get(0).asType().toString();
            }
            if (Message.createInvoke(0).toString().equalsIgnoreCase(methodName)) {
                if (!params.get(2).asType().toString().equals(String.class.getName())) {
                    return "The third argument must be a " + String.class.getName() + "- but is " + params.get(2).asType().toString();
                }
            }
            VariableElement variableElement = params.get(params.size() - 1);
            if (!variableElement.asType().toString().equals("java.lang.Object[]")) {
                return "The last argument must be the arguments array. Required type: java.lang.Object[]" + "- but is " + params.get(params.size() - 1).asType().toString();
            }
            return null;
        }

    }

    private static class ReadGenerator extends MessageGenerator {

        private static final int NUMBER_OF_READ = 3; // VirtualFrame frame, TruffleObject receiver,
                                                     // Object identifier
        private static final String TARGETABLE_READ_NODE = "TargetableReadNode";
        private static final String READ_ROOT_NODE = "ReadRootNode";

        ReadGenerator(ProcessingEnvironment processingEnv, Element e, String pkg, String clazzName, String fullClazzName, String methodName, String userClassName,
                        String truffleLanguageFullClazzName) {
            super(processingEnv, e, pkg, clazzName, fullClazzName, methodName, userClassName, truffleLanguageFullClazzName);
        }

        @Override
        void appendRootNode(Writer w) throws IOException {
            w.append("    private final static class ").append(READ_ROOT_NODE).append(" extends RootNode {\n");
            w.append("        protected ").append(READ_ROOT_NODE).append("(Class<? extends TruffleLanguage<?>> language) {\n");
            w.append("            super(language, null, null);\n");
            w.append("        }\n");
            w.append("\n");
            w.append("        @Child private ").append(TARGETABLE_READ_NODE).append(" node = ").append(pkg).append(".").append(clazzName).append("Factory.").append(TARGETABLE_READ_NODE).append(
                            "Gen.create();\n");
            w.append("\n");
            w.append("        @Override\n");
            w.append("        public Object execute(VirtualFrame frame) {\n");
            w.append("            Object receiver = ForeignAccess.getReceiver(frame);\n");
            w.append("            Object identifier = ForeignAccess.getArguments(frame).get(0);\n");
            w.append("            try {\n");
            w.append("                return node.executeWithTarget(frame, receiver, identifier);\n");
            w.append("            } catch (UnsupportedSpecializationException e) {\n");
            w.append("                throw UnsupportedTypeException.raise(e.getSuppliedValues());\n");
            w.append("            }\n");
            w.append("        }\n");
            w.append("\n");
            w.append("    }\n");
        }

        @Override
        int getParameterCount() {
            return NUMBER_OF_READ;
        }

        @Override
        String getTargetableNodeName() {
            return TARGETABLE_READ_NODE;
        }

        @Override
        String getRootNodeName() {
            return READ_ROOT_NODE;
        }

        @Override
        String checkSignature(ExecutableElement method) {
            final List<? extends VariableElement> params = method.getParameters();
            if (params.size() != NUMBER_OF_READ) {
                return ACCESS_METHOD_NAME + " method has to have " + getParameterCount() + " arguments";
            }
            if (!params.get(0).asType().toString().equals(VirtualFrame.class.getName())) {
                return "The first argument must be a " + VirtualFrame.class.getName() + "- but is " + params.get(0).asType().toString();
            }
            return null;
        }

    }

    private static class WriteGenerator extends MessageGenerator {

        private static final int NUMBER_OF_WRITE = 4; // VirtualFrame frame, TruffleObject receiver,
                                                      // Object identifier, Object value
        private static final String TARGETABLE_WRITE_NODE = "TargetableWriteNode";
        private static final String WRITE_ROOT_NODE = "WriteRootNode";

        WriteGenerator(ProcessingEnvironment processingEnv, Element e, String pkg, String clazzName, String fullClazzName, String methodName, String userClassName,
                        String truffleLanguageFullClazzName) {
            super(processingEnv, e, pkg, clazzName, fullClazzName, methodName, userClassName, truffleLanguageFullClazzName);
        }

        @Override
        void appendRootNode(Writer w) throws IOException {
            w.append("    private final static class ").append(WRITE_ROOT_NODE).append(" extends RootNode {\n");
            w.append("        protected ").append(WRITE_ROOT_NODE).append("(Class<? extends TruffleLanguage<?>> language) {\n");
            w.append("            super(language, null, null);\n");
            w.append("        }\n");
            w.append("\n");
            w.append("        @Child private ").append(TARGETABLE_WRITE_NODE).append(" node = ").append(pkg).append(".").append(clazzName).append("Factory.").append(TARGETABLE_WRITE_NODE).append(
                            "Gen.create();\n");
            w.append("\n");
            w.append("        @Override\n");
            w.append("        public Object execute(VirtualFrame frame) {\n");
            w.append("            Object receiver = ForeignAccess.getReceiver(frame);\n");
            w.append("            Object identifier = ForeignAccess.getArguments(frame).get(0);\n");
            w.append("            Object value = ForeignAccess.getArguments(frame).get(1);\n");
            w.append("            try {\n");
            w.append("                return node.executeWithTarget(frame, receiver, identifier, value);\n");
            w.append("            } catch (UnsupportedSpecializationException e) {\n");
            w.append("                throw UnsupportedTypeException.raise(e.getSuppliedValues());\n");
            w.append("            }\n");
            w.append("        }\n");
            w.append("\n");
            w.append("    }\n");
        }

        @Override
        int getParameterCount() {
            return NUMBER_OF_WRITE;
        }

        @Override
        String getTargetableNodeName() {
            return TARGETABLE_WRITE_NODE;
        }

        @Override
        String getRootNodeName() {
            return WRITE_ROOT_NODE;
        }

        @Override
        String checkSignature(ExecutableElement method) {
            final List<? extends VariableElement> params = method.getParameters();
            if (params.size() != NUMBER_OF_WRITE) {
                return ACCESS_METHOD_NAME + " method has to have " + getParameterCount() + " arguments";
            }
            if (!params.get(0).asType().toString().equals(VirtualFrame.class.getName())) {
                return "The first argument must be a " + VirtualFrame.class.getName() + "- but is " + params.get(0).asType().toString();
            }
            return null;
        }

    }

    private static class GenericGenerator extends MessageGenerator {

        private final String targetableExecuteNode;
        private final String executeRootNode;

        GenericGenerator(ProcessingEnvironment processingEnv, Element e, String pkg, String clazzName, String fullClazzName, String methodName, String userClassName,
                        String truffleLanguageFullClazzName) {
            super(processingEnv, e, pkg, clazzName, fullClazzName, methodName, userClassName, truffleLanguageFullClazzName);
            this.targetableExecuteNode = (new StringBuilder(methodName)).replace(0, 1, methodName.substring(0, 1).toUpperCase()).append("Node").insert(0, "Targetable").toString();
            this.executeRootNode = (new StringBuilder(methodName)).replace(0, 1, methodName.substring(0, 1).toUpperCase()).append("RootNode").toString();
        }

        @Override
        int getParameterCount() {
            return getAccessMethods().get(0).getParameters().size();
        }

        @Override
        String getTargetableNodeName() {
            return targetableExecuteNode;
        }

        @Override
        void appendImports(Writer w) throws IOException {
            super.appendImports(w);
            w.append("import java.util.List;").append("\n");
        }

        @Override
        void appendRootNode(Writer w) throws IOException {
            w.append("    private final static class ").append(executeRootNode).append(" extends RootNode {\n");
            w.append("        protected ").append(executeRootNode).append("(Class<? extends TruffleLanguage<?>> language) {\n");
            w.append("            super(language, null, null);\n");
            w.append("        }\n");
            w.append("\n");
            w.append("        @Child private ").append(targetableExecuteNode).append(" node = ").append(pkg).append(".").append(clazzName).append("Factory.").append(targetableExecuteNode).append(
                            "Gen.create();\n");
            w.append("\n");
            w.append("        @Override\n");
            w.append("        public Object execute(VirtualFrame frame) {\n");
            w.append("            try {\n");
            w.append("              Object receiver = ForeignAccess.getReceiver(frame);\n");
            w.append("              List<Object> arguments = ForeignAccess.getArguments(frame);\n");
            for (int i = 0; i < getParameterCount() - 2; i++) {
                String index = String.valueOf(i);
                w.append("              Object arg").append(index).append(" = arguments.get(").append(index).append(");\n");
            }
            w.append("              return node.executeWithTarget(frame, receiver, ");
            String sep = "";
            for (int i = 0; i < getParameterCount() - 2; i++) {
                String index = String.valueOf(i);
                w.append(sep).append("arg").append(index);
                sep = ", ";
            }
            w.append(");\n");

            w.append("            } catch (UnsupportedSpecializationException e) {\n");
            w.append("                throw UnsupportedTypeException.raise(e.getSuppliedValues());\n");
            w.append("            }\n");
            w.append("        }\n");
            w.append("\n");
            w.append("    }\n");
        }

        @Override
        String getRootNodeName() {
            return executeRootNode;
        }

        @Override
        String checkSignature(ExecutableElement method) {
            final List<? extends VariableElement> params = method.getParameters();
            if (!params.get(0).asType().toString().equals(VirtualFrame.class.getName())) {
                return "The first argument must be a " + VirtualFrame.class.getName() + "- but is " + params.get(0).asType().toString();
            }
            return null;
        }

    }

    private static class FactoryGenerator {

        private final String receiverTypeClass;
        private final String packageName;
        private final String className;
        private final JavaFileObject factoryFile;

        private final Map<Object, String> messageHandlers;

        FactoryGenerator(String packageName, String className, String receiverTypeClass, JavaFileObject factoryFile) {
            this.receiverTypeClass = receiverTypeClass;
            this.className = className;
            this.packageName = packageName;
            this.factoryFile = factoryFile;
            this.messageHandlers = new HashMap<>();
        }

        public void addMessageHandler(Object message, String factoryMethodInvocation) {
            messageHandlers.put(message, factoryMethodInvocation);
        }

        public void generate() {
            try {
                Writer w = factoryFile.openWriter();
                w.append("package ").append(packageName).append(";\n");
                appendImports(w);
                w.append("final class ").append(className).append(" implements Factory10, Factory {\n");

                appendSingelton(w);
                appendPrivateConstructor(w);
                appendFactoryCanHandle(w);

                appendFactory10accessIsNull(w);
                appendFactory10accessIsExecutable(w);
                appendFactory10accessIsBoxed(w);
                appendFactory10accessHasSize(w);
                appendFactory10accessGetSize(w);
                appendFactory10accessUnbox(w);
                appendFactory10accessRead(w);
                appendFactory10accessWrite(w);
                appendFactory10accessExecute(w);
                appendFactory10accessInvoke(w);
                appendFactory10accessNew(w);
                appendFactoryAccessMessage(w);

                w.append("}\n");
                w.close();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private static void appendImports(Writer w) throws IOException {
            w.append("import com.oracle.truffle.api.interop.UnsupportedMessageException;").append("\n");
            w.append("import com.oracle.truffle.api.interop.ForeignAccess.Factory10;").append("\n");
            w.append("import com.oracle.truffle.api.interop.ForeignAccess.Factory;").append("\n");
            w.append("import com.oracle.truffle.api.interop.Message;").append("\n");
            w.append("import com.oracle.truffle.api.interop.ForeignAccess;").append("\n");
            w.append("import com.oracle.truffle.api.interop.TruffleObject;").append("\n");
            w.append("import com.oracle.truffle.api.CallTarget;").append("\n");
            w.append("import com.oracle.truffle.api.Truffle;").append("\n");
            w.append("import com.oracle.truffle.api.nodes.RootNode;").append("\n");
        }

        private void appendSingelton(Writer w) throws IOException {
            w.append("  public static final ForeignAccess ACCESS = ForeignAccess.create(null, new ").append(className).append("());").append("\n");
            w.append("\n");
        }

        private void appendPrivateConstructor(Writer w) throws IOException {
            w.append("  private ").append(className).append("(){}").append("\n");
            w.append("\n");
        }

        private void appendFactoryCanHandle(Writer w) throws IOException {
            w.append("  public boolean canHandle(TruffleObject obj) {").append("\n");
            w.append("    return ").append(receiverTypeClass).append(".isInstance(obj);").append("\n");
            w.append("  }").append("\n");
            w.append("\n");
        }

        private void appendFactory10accessIsNull(Writer w) throws IOException {
            w.append("    public CallTarget accessIsNull() {").append("\n");
            appendOptionalDefaultHandlerBody(w, Message.IS_NULL);
            w.append("    }").append("\n");
        }

        private void appendFactory10accessIsExecutable(Writer w) throws IOException {
            w.append("    public CallTarget accessIsExecutable() {").append("\n");
            appendOptionalDefaultHandlerBody(w, Message.IS_EXECUTABLE);
            w.append("    }").append("\n");
        }

        private void appendFactory10accessIsBoxed(Writer w) throws IOException {
            w.append("    public CallTarget accessIsBoxed() {").append("\n");
            appendOptionalDefaultHandlerBody(w, Message.IS_BOXED);
            w.append("    }").append("\n");
        }

        private void appendFactory10accessHasSize(Writer w) throws IOException {
            w.append("    public CallTarget accessHasSize() {").append("\n");
            appendOptionalDefaultHandlerBody(w, Message.HAS_SIZE);
            w.append("    }").append("\n");
        }

        private void appendOptionalDefaultHandlerBody(Writer w, Message message) throws IOException {
            if (!messageHandlers.containsKey(message)) {
                w.append("      return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));").append("\n");
            } else {
                w.append("      return Truffle.getRuntime().createCallTarget(").append(messageHandlers.get(message)).append(");").append("\n");
            }
        }

        private void appendFactory10accessGetSize(Writer w) throws IOException {
            w.append("    public CallTarget accessGetSize() {").append("\n");
            appendOptionalHandlerBody(w, Message.GET_SIZE, "Message.GET_SIZE");
            w.append("    }").append("\n");
        }

        private void appendFactory10accessUnbox(Writer w) throws IOException {
            w.append("    public CallTarget accessUnbox() {").append("\n");
            appendOptionalHandlerBody(w, Message.UNBOX, "Message.UNBOX");
            w.append("    }").append("\n");
        }

        private void appendFactory10accessRead(Writer w) throws IOException {
            w.append("    public CallTarget accessRead() {").append("\n");
            appendOptionalHandlerBody(w, Message.READ, "Message.READ");
            w.append("    }").append("\n");
        }

        private void appendFactory10accessWrite(Writer w) throws IOException {
            w.append("    public CallTarget accessWrite() {").append("\n");
            appendOptionalHandlerBody(w, Message.WRITE, "Message.WRITE");
            w.append("    }").append("\n");
        }

        private void appendFactory10accessExecute(Writer w) throws IOException {
            w.append("    public CallTarget accessExecute(int argumentsLength) {").append("\n");
            appendOptionalHandlerBody(w, Message.createExecute(0), "Message.createExecute(argumentsLength)");
            w.append("    }").append("\n");
        }

        private void appendFactory10accessInvoke(Writer w) throws IOException {
            w.append("    public CallTarget accessInvoke(int argumentsLength) {").append("\n");
            appendOptionalHandlerBody(w, Message.createInvoke(0), "Message.createInvoke(argumentsLength)");
            w.append("    }").append("\n");
        }

        private void appendFactory10accessNew(Writer w) throws IOException {
            w.append("    public CallTarget accessNew(int argumentsLength) {").append("\n");
            appendOptionalHandlerBody(w, Message.createNew(0), "Message.createNew(argumentsLength)");
            w.append("    }").append("\n");
        }

        private void appendOptionalHandlerBody(Writer w, Message message, String messageObjectAsString) throws IOException {
            if (!messageHandlers.containsKey(message)) {
                w.append("      throw UnsupportedMessageException.raise(").append(messageObjectAsString).append(");").append("\n");
            } else {
                w.append("      return com.oracle.truffle.api.Truffle.getRuntime().createCallTarget(").append(messageHandlers.get(message)).append(");").append("\n");
            }
        }

        private void appendFactoryAccessMessage(Writer w) throws IOException {
            w.append("    public CallTarget accessMessage(Message unknown) {").append("\n");
            for (Object m : messageHandlers.keySet()) {
                if (!KNOWN_MESSAGES.contains(m)) {
                    String msg = m instanceof Message ? Message.toString((Message) m) : (String) m;
                    w.append("      if (unknown instanceof ").append(msg).append(") {").append("\n");
                    w.append("        return Truffle.getRuntime().createCallTarget(").append(messageHandlers.get(m)).append(");").append("\n");
                    w.append("      }").append("\n");
                }
            }
            w.append("      throw UnsupportedMessageException.raise(unknown);").append("\n");
            w.append("    }").append("\n");
        }

        @Override
        public String toString() {
            return "FactoryGenerator: " + className;
        }
    }
}
