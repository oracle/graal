/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.java.transform;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.*;

import java.io.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;

public abstract class AbstractCodeWriter extends CodeElementScanner<Void, Void> {

    private static final int MAX_LINE_LENGTH = 200;
    private static final int LINE_WRAP_INDENTS = 3;
    private static final String IDENT_STRING = "    ";
    private static final String LN = "\n"; /* unix style */

    protected Writer writer;
    private int indent;
    private boolean newLine;
    private int lineLength;
    private boolean lineWrapping = false;

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
        write("package ").write(e.getPackageName()).write(";").writeLn();
        writeEmptyLn();

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

    private String useImport(Element enclosedType, TypeMirror type) {
        if (imports != null) {
            return imports.createTypeReference(enclosedType, type);
        } else {
            return ElementUtils.getSimpleName(type);
        }
    }

    private void writeClassImpl(CodeTypeElement e) {
        for (AnnotationMirror annotation : e.getAnnotationMirrors()) {
            visitAnnotation(e, annotation);
            writeLn();
        }

        writeModifiers(e.getModifiers());
        if (e.getKind() == ElementKind.ENUM) {
            write("enum ");
        } else {
            write("class ");
        }
        write(e.getSimpleName());
        if (e.getSuperclass() != null && !getQualifiedName(e.getSuperclass()).equals("java.lang.Object")) {
            write(" extends ").write(useImport(e, e.getSuperclass()));
        }
        if (e.getImplements().size() > 0) {
            write(" implements ");
            for (int i = 0; i < e.getImplements().size(); i++) {
                write(useImport(e, e.getImplements().get(i)));
                if (i < e.getImplements().size() - 1) {
                    write(", ");
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

        if (parent.getKind() == ElementKind.ENUM && f.getModifiers().contains(Modifier.STATIC)) {
            write(f.getSimpleName());
            if (init != null) {
                write("(");
                init.acceptCodeElementScanner(this, p);
                write(")");
            }
        } else {
            Element enclosing = f.getEnclosingElement();
            writeModifiers(f.getModifiers());

            boolean varArgs = false;
            if (enclosing.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosing;
                if (method.isVarArgs() && method.getParameters().indexOf(f) == method.getParameters().size() - 1) {
                    varArgs = true;
                }
            }

            TypeMirror varType = f.asType();
            if (varArgs) {
                if (varType.getKind() == TypeKind.ARRAY) {
                    varType = ((ArrayType) varType).getComponentType();
                }
                write(useImport(f, varType));
                write("...");
            } else {
                write(useImport(f, varType));
            }

            write(" ");
            write(f.getSimpleName());
            if (init != null) {
                write(" = ");
                init.acceptCodeElementScanner(this, p);
            }
        }
        return null;
    }

    private void visitAnnotation(Element enclosedElement, AnnotationMirror e) {
        write("@").write(useImport(enclosedElement, e.getAnnotationType()));

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

    private class AnnotationValueWriterVisitor extends AbstractAnnotationValueVisitor7<Void, Void> {

        private final Element enclosedElement;

        public AnnotationValueWriterVisitor(Element enclosedElement) {
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
            write(useImport(enclosedElement, t));
            write(".class");
            return null;
        }

        @Override
        public Void visitEnumConstant(VariableElement c, Void p) {
            write(useImport(enclosedElement, c.asType()));
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
        if (e.isStaticImport()) {
            write("import static ").write(e.getImportString()).write(";");
        } else {
            write("import ").write(e.getImportString()).write(";");
        }
    }

    @Override
    public Void visitExecutable(CodeExecutableElement e, Void p) {
        for (AnnotationMirror annotation : e.getAnnotationMirrors()) {
            visitAnnotation(e, annotation);
            writeLn();
        }

        writeModifiers(e.getModifiers());

        if (e.getReturnType() != null) {
            write(useImport(e, e.getReturnType()));
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
                write(useImport(e, throwables.get(i)));
                if (i < throwables.size() - 1) {
                    write(", ");
                }
            }
        }

        if (e.getModifiers().contains(Modifier.ABSTRACT)) {
            writeLn(";");
        } else if (e.getBodyTree() != null) {
            writeLn(" {");
            indent(1);
            e.getBodyTree().acceptCodeElementScanner(this, p);
            dedent(1);
            writeLn("}");
        } else if (e.getBody() != null) {
            write(" {");
            write(e.getBody());
            writeLn("}");
        } else {
            writeLn("{ }");
        }
        writeEmptyLn();
        return null;
    }

    @Override
    public void visitTree(CodeTree e, Void p) {
        CodeTreeKind kind = e.getCodeKind();

        switch (kind) {
            case COMMA_GROUP:
                List<CodeTree> children = e.getEnclosedElements();
                for (int i = 0; i < children.size(); i++) {
                    children.get(i).acceptCodeElementScanner(this, p);
                    if (i < e.getEnclosedElements().size() - 1) {
                        write(", ");
                    }
                }
                break;
            case GROUP:
                for (CodeTree tree : e.getEnclosedElements()) {
                    tree.acceptCodeElementScanner(this, p);
                }
                break;
            case INDENT:
                indent(1);
                for (CodeTree tree : e.getEnclosedElements()) {
                    tree.acceptCodeElementScanner(this, p);
                }
                dedent(1);
                break;
            case NEW_LINE:
                writeLn();
                break;
            case STRING:
                if (e.getString() != null) {
                    write(e.getString());
                } else {
                    write("null");
                }
                break;
            case STATIC_FIELD_REFERENCE:
                if (e.getString() != null) {
                    write(imports.createStaticFieldReference(e, e.getType(), e.getString()));
                } else {
                    write("null");
                }
                break;
            case STATIC_METHOD_REFERENCE:
                if (e.getString() != null) {
                    write(imports.createStaticMethodReference(e, e.getType(), e.getString()));
                } else {
                    write("null");
                }
                break;
            case TYPE:
                write(useImport(e, e.getType()));
                break;
            default:
                assert false;
                return;
        }
    }

    protected void writeHeader() {
        // default implementation does nothing
    }

    private void writeModifiers(Set<Modifier> modifiers) {
        if (modifiers != null) {
            for (Modifier modifier : modifiers) {
                write(modifier.toString());
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
        if (lineWrapping) {
            dedent(LINE_WRAP_INDENTS);
            lineWrapping = false;
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
            if (lineLength > MAX_LINE_LENGTH) {
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

                int nextSize = MAX_LINE_LENGTH - lineLength - 2;
                if (nextSize <= 0) {
                    writeLn();
                    nextSize = MAX_LINE_LENGTH - lineLength - 2;
                }

                int end = Math.min(i + nextSize, string.length());

                // TODO(CH): fails in normal usage - output ok though
                // assert lineLength + (end - i) + 2 < MAX_LINE_LENGTH;

                write("\"" + string.substring(i, end) + "\"");
                size = nextSize;
            }

            return "";
        } else if (!Character.isAlphabetic(firstCharacter) && firstCharacter != '+') {
            return m;
        }

        if (!lineWrapping) {
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
    }

    private int indentSize() {
        return IDENT_STRING.length() * indent;
    }

    private static class TrimTrailingSpaceWriter extends Writer {

        private final Writer delegate;
        private final StringBuilder buffer = new StringBuilder();

        public TrimTrailingSpaceWriter(Writer delegate) {
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
