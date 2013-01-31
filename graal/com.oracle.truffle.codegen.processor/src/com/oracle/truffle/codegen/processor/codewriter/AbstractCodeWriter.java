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
package com.oracle.truffle.codegen.processor.codewriter;

import static com.oracle.truffle.codegen.processor.Utils.*;

import java.io.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.ast.*;

public abstract class AbstractCodeWriter extends CodeElementScanner<Void, Void> {

    protected Writer writer;
    private int indent;
    private boolean newLine;

    private OrganizedImports imports;

    public void visitCompilationUnit(CodeCompilationUnit e) {
        for (TypeElement clazz : e.getEnclosedElements()) {
            clazz.accept(this, null);
        }
    }

    protected abstract Writer createWriter(CodeTypeElement clazz) throws IOException;

    @Override
    public Void visitType(CodeTypeElement e, Void p) {
        if (e.isTopLevelClass()) {
            Writer w = null;
            try {
                imports = OrganizedImports.organize(e);

                w = createWriter(e);
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

    private void writeClassImpl(CodeTypeElement e) {
        for (AnnotationMirror annotation : e.getAnnotationMirrors()) {
            visitAnnotation(annotation);
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
            write(" extends ").write(typeSimpleName(e.getSuperclass()));
        }
        if (e.getImplements().size() > 0) {
            write(" implements ");
            for (int i = 0; i < e.getImplements().size(); i++) {
                write(typeSimpleName(e.getImplements().get(i)));
                if (i < e.getImplements().size() - 1) {
                    write(", ");
                }
            }
        }

        write(" {").writeLn();
        writeEmptyLn();
        indent();

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

        dedent();
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
            visitAnnotation(annotation);
            writeLn();
        }

        CodeTree init = null;
        if (f instanceof CodeVariableElement) {
            init = ((CodeVariableElement) f).getInit();
        }

        if (parent.getKind() == ElementKind.ENUM && f.getModifiers().contains(Modifier.STATIC)) {
            write(f.getSimpleName());
            if (init != null) {
                if (init != null) {
                    write("(");
                    init.acceptCodeElementScanner(this, p);
                    write(")");
                }
            }
        } else {
            writeModifiers(f.getModifiers());
            write(typeSimpleName(f.asType()));
            write(" ");
            write(f.getSimpleName());
            if (init != null) {
                write(" = ");
                init.acceptCodeElementScanner(this, p);
            }
        }
        return null;
    }

    public void visitAnnotation(AnnotationMirror e) {
        write("@").write(typeSimpleName(e.getAnnotationType()));

        if (!e.getElementValues().isEmpty()) {
            write("(");
            final ExecutableElement defaultElement = findExecutableElement(e.getAnnotationType(), "value");

            Map<? extends ExecutableElement, ? extends AnnotationValue> values = e.getElementValues();
            if (defaultElement != null && values.size() == 1 && values.get(defaultElement) != null) {
                visitAnnotationValue(values.get(defaultElement));
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
                    visitAnnotationValue(value);

                    if (i < methodsList.size() - 1) {
                        write(", ");
                    }
                }
            }

            write(")");
        }
    }

    public void visitAnnotationValue(AnnotationValue e) {
        e.accept(new AnnotationValueWriterVisitor(), null);
    }

    private class AnnotationValueWriterVisitor extends AbstractAnnotationValueVisitor7<Void, Void> {

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
            write(typeSimpleName(t));
            write(".class");
            return null;
        }

        @Override
        public Void visitEnumConstant(VariableElement c, Void p) {
            write(typeSimpleName(c.asType()));
            write(".");
            write(c.getSimpleName().toString());
            return null;
        }

        @Override
        public Void visitAnnotation(AnnotationMirror a, Void p) {
            AbstractCodeWriter.this.visitAnnotation(a);
            return null;
        }

        @Override
        public Void visitArray(List<? extends AnnotationValue> vals, Void p) {
            write("{");
            for (int i = 0; i < vals.size(); i++) {
                AnnotationValue value = vals.get(i);
                AbstractCodeWriter.this.visitAnnotationValue(value);
                if (i < vals.size() - 1) {
                    write(", ");
                }
            }
            write("}");
            return null;
        }
    }

    public ExecutableElement findExecutableElement(DeclaredType type, String name) {
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
            visitAnnotation(annotation);
            writeLn();
        }

        writeModifiers(e.getModifiers());

        if (e.getReturnType() != null) {
            write(typeSimpleName(e.getReturnType()));
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
                write(typeSimpleName(throwables.get(i)));
                if (i < throwables.size() - 1) {
                    write(", ");
                }
            }
        }

        if (e.getModifiers().contains(Modifier.ABSTRACT)) {
            writeLn(";");
        } else if (e.getBodyTree() != null) {
            writeLn(" {");
            indent();
            e.getBodyTree().acceptCodeElementScanner(this, p);
            dedent();
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
                indent();
                for (CodeTree tree : e.getEnclosedElements()) {
                    tree.acceptCodeElementScanner(this, p);
                }
                dedent();
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
                    write(imports.useStaticFieldImport(e.getType(), e.getString()));
                } else {
                    write("null");
                }
                break;
            case STATIC_METHOD_REFERENCE:
                if (e.getString() != null) {
                    write(imports.useStaticMethodImport(e.getType(), e.getString()));
                } else {
                    write("null");
                }
                break;
            case TYPE:
                write(imports.useImport(e.getType()));
                break;
            default:
                assert false;
                return;
        }
    }

    private static String typeSimpleName(TypeMirror type) {
        return Utils.getSimpleName(type);
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

    private static final String LN = "\n";

    protected void indent() {
        indent++;
    }

    protected void dedent() {
        indent--;
    }

    protected void writeLn() {
        write(LN);
        newLine = true;
    }

    protected void writeLn(String text) {
        write(text);
        write(LN);
        newLine = true;
    }

    protected void writeEmptyLn() {
        writeLn();
    }

    private AbstractCodeWriter write(Name name) {
        return write(name.toString());
    }

    private AbstractCodeWriter write(String m) {
        try {
            if (newLine && m != LN) {
                writeIndent();
                newLine = false;
            }
            writer.write(m);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    private void writeIndent() throws IOException {
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }
}
