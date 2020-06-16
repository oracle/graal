/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.java.transform;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.getQualifiedName;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.FilerException;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.AbstractAnnotationValueVisitor8;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeElementScanner;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeImport;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeKind;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.sun.org.apache.xpath.internal.functions.Function;

public abstract class AbstractCodeWriter extends CodeElementScanner<Void, Void> {

    private static final int MAX_LINE_LENGTH = Integer.MAX_VALUE; // line wrapping disabled
    private static final int LINE_WRAP_INDENTS = 3;
    private static final int MAX_JAVADOC_LINE_LENGTH = 100;
    private static final String IDENT_STRING = "    ";
    private static final String LN = System.lineSeparator(); /* unix style */

    protected Writer writer;
    private int indent;
    private boolean newLine;
    private int lineLength;
    private boolean lineWrapping = false;
    /* Use LINE_WRAP_INDENTS when wrapping lines as long as a line is wrapped. */
    private boolean indentLineWrapping = true;
    private boolean lineWrappingAtWords = false;

    private String linePrefix;
    private int maxLineLength = MAX_LINE_LENGTH;

    private OrganizedImports imports;

    protected abstract Writer createWriter(CodeTypeElement clazz) throws IOException;

    @Override
    public Void visitType(CodeTypeElement e, Void p) {
        if (e.isTopLevelClass()) {
            Writer w = null;
            try {
                imports = OrganizedImports.organize(e);
                w = new TrimTrailingSpaceWriter(createWriter(e));
                writer = w;
                writeRootClass(e);
            } catch (IOException ex) {
                if (ex instanceof FilerException) {
                    if (ex.getMessage().startsWith("Source file already created")) {
                        // ignore source file already created errors
                        return null;
                    }
                }
                throw new RuntimeException(ex);
            } finally {
                if (w != null) {
                    try {
                        w.close();
                    } catch (Throwable e1) {
                        // see eclipse bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=361378
                        // TODO temporary suppress errors on close.
                    }
                }
                writer = null;
            }
        } else {
            writeClassImpl(e);
        }
        return null;
    }

    private void writeRootClass(CodeTypeElement e) {
        writeHeader();
        if (e.getPackageName() != null && e.getPackageName().length() > 0) {
            write("package ").write(e.getPackageName()).write(";").writeLn();
            writeEmptyLn();
        }

        Set<CodeImport> generateImports = imports.generateImports();
        List<CodeImport> typeImports = new ArrayList<>();
        List<CodeImport> staticImports = new ArrayList<>();

        for (CodeImport codeImport : generateImports) {
            if (codeImport.isStaticImport()) {
                staticImports.add(codeImport);
            } else {
                typeImports.add(codeImport);
            }
        }
        Collections.sort(typeImports);
        Collections.sort(staticImports);

        for (CodeImport imp : staticImports) {
            imp.accept(this, null);
            writeLn();
        }
        if (!staticImports.isEmpty()) {
            writeEmptyLn();
        }

        for (CodeImport imp : typeImports) {
            imp.accept(this, null);
            writeLn();
        }
        if (!typeImports.isEmpty()) {
            writeEmptyLn();
        }

        writeClassImpl(e);
    }

    private String useImport(Element enclosedType, TypeMirror type, boolean rawType) {
        if (imports != null) {
            return imports.createTypeReference(enclosedType, type, rawType);
        } else {
            return ElementUtils.getSimpleName(type);
        }
    }

    static class Foobar<S extends Function, BiFunction> {

    }

    private void writeClassImpl(CodeTypeElement e) {
        if (e.getDocTree() != null) {
            visitTree(e.getDocTree(), null, e);
        }

        for (AnnotationMirror annotation : e.getAnnotationMirrors()) {
            visitAnnotation(e, annotation);
            writeLn();
        }

        writeModifiers(e.getModifiers(), true);
        if (e.getKind() == ElementKind.ENUM) {
            write("enum ");
        } else if (e.getKind() == ElementKind.INTERFACE) {
            write("interface ");
        } else {
            write("class ");
        }
        write(e.getSimpleName());

        writeTypeParameters(e, e.getTypeParameters());

        if (e.getKind() == ElementKind.CLASS) {
            if (e.getSuperclass() != null && !getQualifiedName(e.getSuperclass()).equals("java.lang.Object")) {
                write(" extends ").write(useImport(e, e.getSuperclass(), false));
            }
            if (e.getImplements().size() > 0) {
                write(" implements ");
                for (int i = 0; i < e.getImplements().size(); i++) {
                    write(useImport(e, e.getImplements().get(i), false));
                    if (i < e.getImplements().size() - 1) {
                        write(", ");
                    }
                }
            }
        } else if (e.getKind() == ElementKind.INTERFACE) {
            if (e.getImplements().size() > 0) {
                write(" extends ");
                for (int i = 0; i < e.getImplements().size(); i++) {
                    write(useImport(e, e.getImplements().get(i), false));
                    if (i < e.getImplements().size() - 1) {
                        write(", ");
                    }
                }
            }
        }

        write(" {").writeLn();
        writeEmptyLn();
        indent(1);

        List<VariableElement> staticFields = getStaticFields(e);
        List<VariableElement> instanceFields = getInstanceFields(e);

        for (int i = 0; i < staticFields.size(); i++) {
            VariableElement field = staticFields.get(i);
            field.accept(this, null);
            if (e.getKind() == ElementKind.ENUM && i < staticFields.size() - 1) {
                write(",");
                writeLn();
            } else {
                write(";");
                writeLn();
            }
        }

        if (staticFields.size() > 0) {
            writeEmptyLn();
        }

        for (VariableElement field : instanceFields) {
            field.accept(this, null);
            write(";");
            writeLn();
        }
        if (instanceFields.size() > 0) {
            writeEmptyLn();
        }

        for (ExecutableElement method : ElementFilter.constructorsIn(e.getEnclosedElements())) {
            method.accept(this, null);
        }

        for (ExecutableElement method : getInstanceMethods(e)) {
            method.accept(this, null);
        }

        for (ExecutableElement method : getStaticMethods(e)) {
            method.accept(this, null);
        }

        for (TypeElement clazz : e.getInnerClasses()) {
            clazz.accept(this, null);
        }

        dedent(1);
        write("}");
        writeEmptyLn();
    }

    private void writeTypeParameters(Element enclosedType, List<? extends TypeParameterElement> parameters) {
        if (!parameters.isEmpty()) {
            write("<");
            String sep = "";
            for (TypeParameterElement typeParameter : parameters) {
                write(sep);
                write(typeParameter.getSimpleName().toString());
                if (!typeParameter.getBounds().isEmpty()) {
                    write(" extends ");
                    String genericBoundsSep = "";
                    for (TypeMirror type : typeParameter.getBounds()) {
                        write(genericBoundsSep);
                        write(useImport(enclosedType, type, false));
                        genericBoundsSep = ", ";
                    }
                }
                sep = ", ";
            }
            write(">");
        }
    }

    private static List<VariableElement> getStaticFields(CodeTypeElement clazz) {
        List<VariableElement> staticFields = new ArrayList<>();
        for (VariableElement field : clazz.getFields()) {
            if (field.getModifiers().contains(Modifier.STATIC)) {
                staticFields.add(field);
            }
        }
        return staticFields;
    }

    private static List<VariableElement> getInstanceFields(CodeTypeElement clazz) {
        List<VariableElement> instanceFields = new ArrayList<>();
        for (VariableElement field : clazz.getFields()) {
            if (!field.getModifiers().contains(Modifier.STATIC)) {
                instanceFields.add(field);
            }
        }
        return instanceFields;
    }

    private static List<ExecutableElement> getStaticMethods(CodeTypeElement clazz) {
        List<ExecutableElement> staticMethods = new ArrayList<>();
        for (ExecutableElement method : clazz.getMethods()) {
            if (method.getModifiers().contains(Modifier.STATIC)) {
                staticMethods.add(method);
            }
        }
        return staticMethods;
    }

    private static List<ExecutableElement> getInstanceMethods(CodeTypeElement clazz) {
        List<ExecutableElement> instanceMethods = new ArrayList<>();
        for (ExecutableElement method : clazz.getMethods()) {
            if (!method.getModifiers().contains(Modifier.STATIC)) {
                instanceMethods.add(method);
            }
        }
        return instanceMethods;
    }

    @Override
    public Void visitVariable(VariableElement f, Void p) {
        Element parent = f.getEnclosingElement();

        for (AnnotationMirror annotation : f.getAnnotationMirrors()) {
            visitAnnotation(f, annotation);
            write(" ");
        }

        CodeTree init = null;
        if (f instanceof CodeVariableElement) {
            init = ((CodeVariableElement) f).getInit();
        }

        if (parent != null && parent.getKind() == ElementKind.ENUM && f.getModifiers().contains(Modifier.STATIC)) {
            write(f.getSimpleName());
            if (init != null) {
                write("(");
                visitTree(init, p, f);
                write(")");
            }
        } else {
            writeModifiers(f.getModifiers(), true);

            boolean varArgs = false;
            if (parent != null && (parent.getKind() == ElementKind.METHOD || parent.getKind() == ElementKind.CONSTRUCTOR)) {
                ExecutableElement method = (ExecutableElement) parent;
                if (method.isVarArgs() && method.getParameters().indexOf(f) == method.getParameters().size() - 1) {
                    varArgs = true;
                }
            }

            TypeMirror varType = f.asType();
            if (varArgs) {
                if (varType.getKind() == TypeKind.ARRAY) {
                    varType = ((ArrayType) varType).getComponentType();
                }
                write(useImport(f, varType, false));
                write("...");
            } else {
                write(useImport(f, varType, false));
            }

            write(" ");
            write(f.getSimpleName());
            if (init != null) {
                write(" = ");
                visitTree(init, p, f);
            }
        }
        return null;
    }

    private void visitAnnotation(Element enclosedElement, AnnotationMirror e) {
        write("@").write(useImport(enclosedElement, e.getAnnotationType(), true));

        if (!e.getElementValues().isEmpty()) {
            write("(");
            final ExecutableElement defaultElement = findExecutableElement(e.getAnnotationType(), "value");

            Map<? extends ExecutableElement, ? extends AnnotationValue> values = e.getElementValues();
            if (defaultElement != null && values.size() == 1 && values.get(defaultElement) != null) {
                visitAnnotationValue(enclosedElement, values.get(defaultElement));
            } else {
                Set<? extends ExecutableElement> methodsSet = values.keySet();
                List<ExecutableElement> methodsList = new ArrayList<>();
                for (ExecutableElement method : methodsSet) {
                    if (values.get(method) == null) {
                        continue;
                    }
                    methodsList.add(method);
                }

                Collections.sort(methodsList, new Comparator<ExecutableElement>() {

                    @Override
                    public int compare(ExecutableElement o1, ExecutableElement o2) {
                        return o1.getSimpleName().toString().compareTo(o2.getSimpleName().toString());
                    }
                });

                for (int i = 0; i < methodsList.size(); i++) {
                    ExecutableElement method = methodsList.get(i);
                    AnnotationValue value = values.get(method);
                    write(method.getSimpleName().toString());
                    write(" = ");
                    visitAnnotationValue(enclosedElement, value);

                    if (i < methodsList.size() - 1) {
                        write(", ");
                    }
                }
            }

            write(")");
        }
    }

    private void visitAnnotationValue(Element enclosedElement, AnnotationValue e) {
        e.accept(new AnnotationValueWriterVisitor(enclosedElement), null);
    }

    private class AnnotationValueWriterVisitor extends AbstractAnnotationValueVisitor8<Void, Void> {

        private final Element enclosedElement;

        AnnotationValueWriterVisitor(Element enclosedElement) {
            this.enclosedElement = enclosedElement;
        }

        @Override
        public Void visitBoolean(boolean b, Void p) {
            write(Boolean.toString(b));
            return null;
        }

        @Override
        public Void visitByte(byte b, Void p) {
            write(Byte.toString(b));
            return null;
        }

        @Override
        public Void visitChar(char c, Void p) {
            write(Character.toString(c));
            return null;
        }

        @Override
        public Void visitDouble(double d, Void p) {
            write(Double.toString(d));
            return null;
        }

        @Override
        public Void visitFloat(float f, Void p) {
            write(Float.toString(f));
            return null;
        }

        @Override
        public Void visitInt(int i, Void p) {
            write(Integer.toString(i));
            return null;
        }

        @Override
        public Void visitLong(long i, Void p) {
            write(Long.toString(i));
            return null;
        }

        @Override
        public Void visitShort(short s, Void p) {
            write(Short.toString(s));
            return null;
        }

        @Override
        public Void visitString(String s, Void p) {
            write("\"");
            write(s);
            write("\"");
            return null;
        }

        @Override
        public Void visitType(TypeMirror t, Void p) {
            write(useImport(enclosedElement, t, true));
            write(".class");
            return null;
        }

        @Override
        public Void visitEnumConstant(VariableElement c, Void p) {
            write(useImport(enclosedElement, c.asType(), true));
            write(".");
            write(c.getSimpleName().toString());
            return null;
        }

        @Override
        public Void visitAnnotation(AnnotationMirror a, Void p) {
            AbstractCodeWriter.this.visitAnnotation(enclosedElement, a);
            return null;
        }

        @Override
        public Void visitArray(List<? extends AnnotationValue> vals, Void p) {
            write("{");
            for (int i = 0; i < vals.size(); i++) {
                AnnotationValue value = vals.get(i);
                AbstractCodeWriter.this.visitAnnotationValue(enclosedElement, value);
                if (i < vals.size() - 1) {
                    write(", ");
                }
            }
            write("}");
            return null;
        }
    }

    private static ExecutableElement findExecutableElement(DeclaredType type, String name) {
        List<? extends ExecutableElement> elements = ElementFilter.methodsIn(type.asElement().getEnclosedElements());
        for (ExecutableElement executableElement : elements) {
            if (executableElement.getSimpleName().toString().equals(name)) {
                return executableElement;
            }
        }
        return null;
    }

    @Override
    public void visitImport(CodeImport e, Void p) {
        write("import ");
        if (e.isStaticImport()) {
            write("static ");
        }
        write(e.getPackageName());
        write(".");
        write(e.getSymbolName());
        write(";");
    }

    @Override
    public Void visitExecutable(CodeExecutableElement e, Void p) {
        if (e.getDocTree() != null) {
            visitTree(e.getDocTree(), null, e);
        }

        for (AnnotationMirror annotation : e.getAnnotationMirrors()) {
            visitAnnotation(e, annotation);
            writeLn();
        }

        writeModifiers(e.getModifiers(), e.getEnclosingClass() == null || !e.getEnclosingClass().getModifiers().contains(Modifier.FINAL));

        String name = e.getSimpleName().toString();
        if (name.equals("<cinit>") || name.equals("<init>")) {
            // no name
        } else {

            List<TypeParameterElement> typeParameters = e.getTypeParameters();
            if (!typeParameters.isEmpty()) {
                write("<");
                for (int i = 0; i < typeParameters.size(); i++) {
                    TypeParameterElement param = typeParameters.get(i);
                    write(param.getSimpleName().toString());
                    List<? extends TypeMirror> bounds = param.getBounds();
                    if (!bounds.isEmpty()) {
                        write(" extends ");
                        for (int j = 0; j < bounds.size(); j++) {
                            TypeMirror bound = bounds.get(i);
                            write(useImport(e, bound, true));
                            if (j < bounds.size() - 1) {
                                write(" ");
                                write(", ");
                            }
                        }
                    }

                    if (i < typeParameters.size() - 1) {
                        write(" ");
                        write(", ");
                    }
                }
                write("> ");
            }

            if (e.getReturnType() != null) {
                write(useImport(e, e.getReturnType(), false));
                write(" ");
            }
            write(e.getSimpleName());
            write("(");

            for (int i = 0; i < e.getParameters().size(); i++) {
                VariableElement param = e.getParameters().get(i);
                param.accept(this, p);
                if (i < e.getParameters().size() - 1) {
                    write(", ");
                }
            }
            write(")");
            List<TypeMirror> throwables = e.getThrownTypes();
            if (throwables.size() > 0) {
                write(" throws ");
                for (int i = 0; i < throwables.size(); i++) {
                    write(useImport(e, throwables.get(i), true));
                    if (i < throwables.size() - 1) {
                        write(", ");
                    }
                }
            }
        }

        if (e.getModifiers().contains(Modifier.ABSTRACT)) {
            writeLn(";");
        } else if (e.getBodyTree() != null) {
            writeLn(" {");
            indent(1);
            visitTree(e.getBodyTree(), p, e);
            dedent(1);
            writeLn("}");
        } else if (e.getBody() != null) {
            write(" {");
            write(e.getBody());
            writeLn("}");
        } else {
            writeLn(" {");
            writeLn("}");
        }
        writeEmptyLn();
        return null;
    }

    @Override
    public void visitTree(CodeTree e, Void p, Element enclosingElement) {
        CodeTreeKind kind = e.getCodeKind();

        switch (kind) {
            case COMMA_GROUP:
                List<CodeTree> children = e.getEnclosedElements();
                if (children != null) {
                    for (int i = 0; i < children.size(); i++) {
                        visitTree(children.get(i), p, enclosingElement);
                        if (i < e.getEnclosedElements().size() - 1) {
                            write(", ");
                        }
                    }
                }
                break;
            case GROUP:
                super.visitTree(e, p, enclosingElement);
                break;
            case INDENT:
                indent(1);
                super.visitTree(e, p, enclosingElement);
                dedent(1);
                break;
            case NEW_LINE:
                writeLn();
                break;
            case STRING:
                if (e.getString() != null) {
                    String s = e.getString();
                    if (lineWrappingAtWords) {
                        int index = -1;
                        int start = 0;
                        while ((index = s.indexOf(' ', start)) != -1) {
                            write(s.substring(start, index + 1));
                            start = index + 1;
                        }
                        if (start < s.length()) {
                            write(s.substring(start, s.length()));
                        }
                    } else {
                        write(e.getString());
                    }
                } else {
                    write("null");
                }
                break;
            case STATIC_FIELD_REFERENCE:
                if (e.getString() != null) {
                    write(imports.createStaticFieldReference(enclosingElement, e.getType(), e.getString()));
                } else {
                    write("null");
                }
                break;
            case STATIC_METHOD_REFERENCE:
                if (e.getString() != null) {
                    write(imports.createStaticMethodReference(enclosingElement, e.getType(), e.getString()));
                } else {
                    write("null");
                }
                break;
            case TYPE:
                write(useImport(enclosingElement, e.getType(), false));
                break;
            case TYPE_LITERAL:
                write(useImport(enclosingElement, e.getType(), true));
                write(".class");
                break;
            case JAVA_DOC:
            case DOC:
                write("/*");
                if (kind == CodeTreeKind.JAVA_DOC) {
                    write("*");
                }
                write(" ");
                writeLn();
                indentLineWrapping = false; // avoid wrapping indents
                int prevMaxLineLength = this.maxLineLength;
                maxLineLength = MAX_JAVADOC_LINE_LENGTH;
                linePrefix = " * ";
                lineWrappingAtWords = true;
                super.visitTree(e, p, enclosingElement);
                linePrefix = null;
                lineWrappingAtWords = false;
                maxLineLength = prevMaxLineLength;
                writeLn();
                indentLineWrapping = true;
                write(" */");
                writeLn();
                break;
            default:
                assert false;
                return;
        }
    }

    protected void writeHeader() {
        // default implementation does nothing
    }

    private void writeModifiers(Set<Modifier> modifiers, boolean includeFinal) {
        if (modifiers != null && !modifiers.isEmpty()) {
            Modifier[] modArray = modifiers.toArray(new Modifier[modifiers.size()]);
            Arrays.sort(modArray);
            for (Modifier mod : modArray) {
                if (mod == Modifier.FINAL && !includeFinal) {
                    continue;
                }
                write(mod.toString());
                write(" ");
            }
        }
    }

    private void indent(int count) {
        indent += count;
    }

    private void dedent(int count) {
        indent -= count;
    }

    private void writeLn() {
        writeLn("");
    }

    protected void writeLn(String text) {
        write(text);
        write(LN);
        lineLength = 0;
        newLine = true;
        if (lineWrapping && indentLineWrapping) {
            dedent(LINE_WRAP_INDENTS);
        }
        lineWrapping = false;
    }

    private void writeEmptyLn() {
        writeLn();
    }

    private AbstractCodeWriter write(Name name) {
        return write(name.toString());
    }

    private AbstractCodeWriter write(String m) {
        if (m.isEmpty()) {
            return this;
        }
        try {
            String s = m;
            lineLength += s.length();
            if (newLine && s != LN) {
                writeIndent();
                newLine = false;
            }
            if (lineLength > maxLineLength) {
                s = wrapLine(s);
            }
            writer.write(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    private String wrapLine(String m) throws IOException {
        assert !m.isEmpty();

        char firstCharacter = m.charAt(0);
        char lastCharacter = m.charAt(m.length() - 1);
        if (firstCharacter == '\"' && lastCharacter == '\"') {
            // string line wrapping
            String string = m.substring(1, m.length() - 1);
            if (string.isEmpty()) {
                return m;
            }

            // restore original line length
            lineLength = lineLength - m.length();
            int size = 0;
            for (int i = 0; i < string.length(); i += size) {
                if (i != 0) {
                    write("+ ");
                }

                int nextSize = maxLineLength - lineLength - 2;
                if (nextSize <= 0) {
                    writeLn();
                    nextSize = maxLineLength - lineLength - 2;
                }

                int end = Math.min(i + nextSize, string.length());

                // TODO(CH): fails in normal usage - output ok though
                // assert lineLength + (end - i) + 2 < maxLineLength;
                write("\"");
                write(string.substring(i, end));
                write("\"");
                size = nextSize;
            }

            return "";
        } else if (!Character.isAlphabetic(firstCharacter) && firstCharacter != '+') {
            return m;
        }

        if (!lineWrapping && indentLineWrapping) {
            indent(LINE_WRAP_INDENTS);
        }
        lineWrapping = true;
        lineLength = 0;
        write(LN);
        writeIndent();
        return m;
    }

    private void writeIndent() throws IOException {
        lineLength += indentSize();
        for (int i = 0; i < indent; i++) {
            writer.write(IDENT_STRING);
        }
        if (linePrefix != null) {
            lineLength += linePrefix.length();
            writer.write(linePrefix);
        }
    }

    private int indentSize() {
        return IDENT_STRING.length() * indent;
    }

    private static class TrimTrailingSpaceWriter extends Writer {

        private final Writer delegate;
        private final StringBuilder buffer = new StringBuilder();

        TrimTrailingSpaceWriter(Writer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() throws IOException {
            this.delegate.close();
        }

        @Override
        public void flush() throws IOException {
            this.delegate.flush();
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            buffer.append(cbuf, off, len);
            int newLinePoint = buffer.indexOf(LN);

            if (newLinePoint != -1) {
                String lhs = trimTrailing(buffer.substring(0, newLinePoint));
                delegate.write(lhs);
                delegate.write(LN);
                buffer.delete(0, newLinePoint + 1);
            }
        }

        private static String trimTrailing(String s) {
            int cut = 0;
            for (int i = s.length() - 1; i >= 0; i--) {
                if (Character.isWhitespace(s.charAt(i))) {
                    cut++;
                } else {
                    break;
                }
            }
            if (cut > 0) {
                return s.substring(0, s.length() - cut);
            }
            return s;
        }
    }

}
