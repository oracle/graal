/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.java.model;

import static com.oracle.truffle.dsl.processor.java.model.CodeTreeKind.GROUP;
import static com.oracle.truffle.dsl.processor.java.model.CodeTreeKind.NEW_LINE;
import static com.oracle.truffle.dsl.processor.java.model.CodeTreeKind.REMOVE_LAST;

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.java.ElementUtils;

public class CodeTreeBuilder {

    private BuilderCodeTree currentElement;
    private final BuilderCodeTree root;

    private int treeCount;
    private Element enclosingElement;

    public CodeTreeBuilder(CodeTreeBuilder parent) {
        this.root = new BuilderCodeTree(null, GROUP, null, null);
        this.currentElement = root;
        if (parent != null) {
            this.enclosingElement = parent.enclosingElement;
        }
    }

    public void setEnclosingElement(Element enclosingElement) {
        this.enclosingElement = enclosingElement;
    }

    @Override
    public String toString() {
        return root.toString();
    }

    public int getTreeCount() {
        return treeCount;
    }

    public boolean isEmpty() {
        return treeCount == 0;
    }

    public CodeTreeBuilder startJavadoc() {
        push(CodeTreeKind.JAVA_DOC);
        return this;
    }

    public CodeTreeBuilder javadocLink(Element element, String title) {
        string("{@link ");
        if (element.getKind().isClass() || element.getKind().isInterface()) {
            type(element.asType());
        } else if (element.getKind() == ElementKind.METHOD) {
            ExecutableElement e = (ExecutableElement) element;
            type(e.getEnclosingElement().asType());
            string("#");
            string(e.getSimpleName().toString());
            string("(");
            String sep = "";
            for (VariableElement var : e.getParameters()) {
                string(sep);
                type(var.asType());
                sep = ", ";
            }
            string(")");
        } else {
            throw new UnsupportedOperationException();
        }
        if (title != null && !title.isEmpty()) {
            string(" ", title);
        }
        string("}");
        return this;
    }

    public CodeTreeBuilder startDoc() {
        push(CodeTreeKind.DOC);
        return this;
    }

    public CodeTreeBuilder statement(String statement) {
        return startStatement().string(statement).end();
    }

    public CodeTreeBuilder statement(CodeTree statement) {
        return startStatement().tree(statement).end();
    }

    public static CodeTreeBuilder createBuilder() {
        return new CodeTreeBuilder(null);
    }

    public static CodeTree singleString(String s) {
        return createBuilder().string(s).build();
    }

    public static CodeTree singleType(TypeMirror s) {
        return createBuilder().type(s).build();
    }

    private CodeTreeBuilder push(CodeTreeKind kind) {
        return push(new BuilderCodeTree(currentElement, kind, null, null), kind == NEW_LINE);
    }

    private CodeTreeBuilder push(String string) {
        return push(new BuilderCodeTree(currentElement, CodeTreeKind.STRING, null, string), false);
    }

    private CodeTreeBuilder push(TypeMirror type, boolean literal) {
        return push(new BuilderCodeTree(currentElement, literal ? CodeTreeKind.TYPE_LITERAL : CodeTreeKind.TYPE, type, null), false);
    }

    private CodeTreeBuilder push(CodeTreeKind kind, TypeMirror type, String string) {
        return push(new BuilderCodeTree(currentElement, kind, type, string), kind == NEW_LINE);
    }

    private CodeTreeBuilder push(BuilderCodeTree tree, boolean removeLast) {
        if (currentElement != null) {
            if (removeLast && !removeLastIfEnqueued(tree)) {
                return this;
            }
            currentElement.add(tree);
        }
        switch (tree.getCodeKind()) {
            case COMMA_GROUP:
            case GROUP:
            case JAVA_DOC:
            case DOC:
            case INDENT:
                currentElement = tree;
                break;
        }
        treeCount++;
        return this;
    }

    private boolean removeLastIfEnqueued(BuilderCodeTree tree) {
        if (tree.getCodeKind() == REMOVE_LAST) {
            return !clearLastRec(tree.removeLast, currentElement.getEnclosedElements());
        }
        List<CodeTree> childTree = tree.getEnclosedElements();
        if (childTree != null && !childTree.isEmpty()) {
            CodeTree last = childTree.get(0);
            if (last instanceof BuilderCodeTree) {
                if (!removeLastIfEnqueued((BuilderCodeTree) last)) {
                    childTree.remove(0);
                }
            }
        }
        return true;
    }

    private void clearLast(CodeTreeKind kind) {
        if (clearLastRec(kind, currentElement.getEnclosedElements())) {
            treeCount--;
        } else {
            // delay clearing the last
            BuilderCodeTree tree = new BuilderCodeTree(currentElement, REMOVE_LAST, null, null);
            tree.removeLast = kind;
            push(tree, false);
        }
    }

    public CodeTreeBuilder startStatement() {
        startGroup();
        registerCallBack(new EndCallback() {

            @Override
            public void beforeEnd() {
                string(";").newLine();
            }

            @Override
            public void afterEnd() {
            }
        });
        return this;
    }

    public CodeTreeBuilder startGroup() {
        return push(CodeTreeKind.GROUP);
    }

    public CodeTreeBuilder startCommaGroup() {
        return push(CodeTreeKind.COMMA_GROUP);
    }

    public CodeTreeBuilder startCall(String callSite) {
        return startCall((CodeTree) null, callSite);
    }

    public CodeTreeBuilder startCall(String receiver, ExecutableElement method) {
        if (receiver == null && method.getModifiers().contains(Modifier.STATIC)) {
            return startStaticCall(method.getEnclosingElement().asType(), method.getSimpleName().toString());
        }

        return startCall(receiver, method.getSimpleName().toString());
    }

    public CodeTreeBuilder startCall(String receiver, String callSite) {
        if (receiver != null) {
            return startCall(singleString(receiver), callSite);
        } else {
            return startCall(callSite);
        }
    }

    public CodeTreeBuilder startCall(CodeTree receiver, String callSite) {
        if (receiver == null) {
            return startGroup().string(callSite).startParanthesesCommaGroup().endAfter();
        } else {
            return startGroup().tree(receiver).string(".").string(callSite).startParanthesesCommaGroup().endAfter();
        }
    }

    public CodeTreeBuilder startStaticCall(TypeMirror type, String methodName) {
        return startGroup().push(CodeTreeKind.STATIC_METHOD_REFERENCE, type, methodName).startParanthesesCommaGroup().endAfter();
    }

    public CodeTreeBuilder startStaticCall(ExecutableElement method) {
        TypeElement parentType = ElementUtils.findNearestEnclosingType(method).orElseThrow(AssertionError::new);
        return startStaticCall(parentType.asType(), method.getSimpleName().toString());
    }

    public CodeTreeBuilder staticReference(TypeMirror type, String fieldName) {
        return push(CodeTreeKind.STATIC_FIELD_REFERENCE, type, fieldName);
    }

    public CodeTreeBuilder staticReference(VariableElement field) {
        if (field.getEnclosingElement() == null) {
            return string(field.getSimpleName().toString());
        } else {
            return staticReference(field.getEnclosingElement().asType(), field.getSimpleName().toString());
        }
    }

    private CodeTreeBuilder endAndWhitespaceAfter() {
        registerCallBack(new EndCallback() {

            @Override
            public void beforeEnd() {
            }

            @Override
            public void afterEnd() {
                string(" ");
                end();
            }
        });
        return this;
    }

    private CodeTreeBuilder endAfter() {
        registerCallBack(new EndCallback() {

            @Override
            public void beforeEnd() {
            }

            @Override
            public void afterEnd() {
                end();
            }
        });
        return this;
    }

    private CodeTreeBuilder startParanthesesCommaGroup() {
        startGroup();
        string("(").startCommaGroup();
        registerCallBack(new EndCallback() {

            @Override
            public void beforeEnd() {
            }

            @Override
            public void afterEnd() {
                string(")");
            }
        });
        endAfter();
        return this;
    }

    private CodeTreeBuilder startCurlyBracesCommaGroup() {
        startGroup();
        string("{").startCommaGroup();
        registerCallBack(new EndCallback() {

            @Override
            public void beforeEnd() {
            }

            @Override
            public void afterEnd() {
                string("}");
            }
        });
        endAfter();
        return this;
    }

    public CodeTreeBuilder startParantheses() {
        startGroup();
        string("(").startGroup();
        registerCallBack(new EndCallback() {

            @Override
            public void beforeEnd() {
            }

            @Override
            public void afterEnd() {
                string(")");
            }
        });
        endAfter();
        return this;
    }

    public CodeTreeBuilder doubleQuote(String s) {
        return startGroup().string("\"" + s + "\"").end();
    }

    public CodeTreeBuilder string(String chunk1) {
        return push(chunk1);
    }

    public CodeTreeBuilder string(String chunk1, String chunk2) {
        return push(GROUP).string(chunk1).string(chunk2).end();
    }

    public CodeTreeBuilder string(String chunk1, String chunk2, String chunk3) {
        return push(GROUP).string(chunk1).string(chunk2).string(chunk3).end();
    }

    public CodeTreeBuilder string(String chunk1, String chunk2, String chunk3, String chunk4) {
        return push(GROUP).string(chunk1).string(chunk2).string(chunk3).string(chunk4).end();
    }

    public CodeTreeBuilder tree(CodeTree treeToAdd) {
        if (treeToAdd != null) {
            if (treeToAdd instanceof BuilderCodeTree) {
                return push((BuilderCodeTree) treeToAdd, true).end();
            } else {
                BuilderCodeTree tree = new BuilderCodeTree(currentElement, GROUP, null, null);
                currentElement.add(treeToAdd);
                return push(tree, true).end();
            }
        }
        return this;
    }

    public CodeTreeBuilder trees(CodeTree... trees) {
        for (CodeTree tree : trees) {
            if (tree != null) {
                tree(tree);
            }
        }
        return this;
    }

    public CodeTreeBuilder string(String chunk1, String chunk2, String chunk3, String chunk4, String... chunks) {
        push(GROUP).string(chunk1).string(chunk2).string(chunk3).string(chunk4);
        for (int i = 0; i < chunks.length; i++) {
            string(chunks[i]);
        }
        return end();
    }

    public CodeTreeBuilder newLine() {
        return push(NEW_LINE);
    }

    public CodeTreeBuilder startWhile() {
        return startGroup().string("while ").startParanthesesCommaGroup().endAndWhitespaceAfter().startGroup().endAfter();
    }

    public CodeTreeBuilder startDoBlock() {
        return startGroup().string("do ").startBlock();
    }

    public CodeTreeBuilder startDoWhile() {
        clearLast(CodeTreeKind.NEW_LINE);
        return startStatement().string(" while ").startParanthesesCommaGroup().endAfter().startGroup().endAfter();
    }

    public CodeTreeBuilder startIf() {
        return startGroup().string("if ").startParanthesesCommaGroup().endAndWhitespaceAfter().startGroup().endAfter();
    }

    public CodeTreeBuilder startFor() {
        return startGroup().string("for ").startParantheses().endAndWhitespaceAfter().startGroup().endAfter();
    }

    public boolean startIf(boolean elseIf) {
        if (elseIf) {
            startElseIf();
        } else {
            startIf();
        }
        return true;
    }

    public CodeTreeBuilder startElseIf() {
        clearLast(CodeTreeKind.NEW_LINE);
        return startGroup().string(" else if ").startParanthesesCommaGroup().endAndWhitespaceAfter().startGroup().endAfter();
    }

    public CodeTreeBuilder startElseBlock() {
        clearLast(CodeTreeKind.NEW_LINE);
        return startGroup().string(" else ").startBlock().endAfter();
    }

    private boolean clearLastRec(CodeTreeKind kind, List<CodeTree> children) {
        if (children == null) {
            return false;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            CodeTree child = children.get(i);
            if (child.getCodeKind() == kind) {
                children.remove(children.get(i));
                return true;
            } else {
                if (clearLastRec(kind, child.getEnclosedElements())) {
                    return true;
                }
            }
        }
        return false;
    }

    public CodeTreeBuilder startCase() {
        startGroup().string("case ");
        registerCallBack(new EndCallback() {

            @Override
            public void beforeEnd() {
                string(" :").newLine();
            }

            @Override
            public void afterEnd() {
            }
        });
        return this;
    }

    public CodeTreeBuilder caseDefault() {
        return startGroup().string("default :").newLine().end();
    }

    public CodeTreeBuilder startSwitch() {
        return startGroup().string("switch ").startParantheses().endAndWhitespaceAfter();
    }

    public CodeTreeBuilder startReturn() {
        ExecutableElement method = findMethod();
        if (method != null && ElementUtils.isVoid(method.getReturnType())) {
            startGroup();
            registerCallBack(new EndCallback() {

                @Override
                public void beforeEnd() {
                    string(";").newLine(); // complete statement to execute
                }

                @Override
                public void afterEnd() {
                    string("return").string(";").newLine(); // emit a return;
                }
            });
            return this;
        } else {
            return startStatement().string("return ");
        }
    }

    public CodeTreeBuilder startAssert() {
        return startStatement().string("assert ");
    }

    public CodeTreeBuilder startNewArray(ArrayType arrayType, CodeTree size) {
        startGroup().string("new ").type(arrayType.getComponentType()).string("[");
        if (size != null) {
            tree(size);
        }
        string("]");
        if (size == null) {
            string(" ");
            startCurlyBracesCommaGroup().endAfter();
        }
        return this;
    }

    public CodeTreeBuilder lineComment(String text) {
        return string("// ").string(text).newLine();
    }

    public CodeTreeBuilder startNew(TypeMirror uninializedNodeClass) {
        return startGroup().string("new ").type(uninializedNodeClass).startParanthesesCommaGroup().endAfter();
    }

    public CodeTreeBuilder startNew(String typeName) {
        return startGroup().string("new ").string(typeName).startParanthesesCommaGroup().endAfter();
    }

    public CodeTreeBuilder startIndention() {
        return push(CodeTreeKind.INDENT);
    }

    public CodeTreeBuilder end(int times) {
        for (int i = 0; i < times; i++) {
            end();
        }
        return this;
    }

    public CodeTreeBuilder end() {
        BuilderCodeTree tree = currentElement;
        EndCallback callback = tree.getAtEndListener();
        if (callback != null) {
            callback.beforeEnd();
            toParent();
            callback.afterEnd();
        } else {
            toParent();
        }
        return this;
    }

    private void toParent() {
        CodeTree parentElement = currentElement.getParent();
        if (currentElement != root) {
            this.currentElement = (BuilderCodeTree) parentElement;
        } else {
            this.currentElement = root;
        }
    }

    public CodeTreeBuilder startBlock() {
        startGroup();
        string("{").newLine().startIndention();
        registerCallBack(new EndCallback() {

            @Override
            public void beforeEnd() {
            }

            @Override
            public void afterEnd() {
                string("}").newLine();
            }
        });
        endAfter();
        return this;
    }

    public CodeTreeBuilder startSynchronized(String object) {
        return startSynchronized(singleString(object));
    }

    public CodeTreeBuilder startSynchronized(CodeTree object) {
        return string("synchronized").startParantheses().tree(object).end().startBlock();
    }

    private void registerCallBack(EndCallback callback) {
        currentElement.registerAtEnd(callback);
    }

    public CodeTreeBuilder defaultDeclaration(TypeMirror type, String name) {
        if (!ElementUtils.isVoid(type)) {
            startStatement();
            type(type);
            string(" ");
            string(name);
            string(" = ");
            defaultValue(type);
            end(); // statement
        }
        return this;
    }

    public CodeTreeBuilder declaration(TypeMirror type, String name, String init) {
        return declaration(type, name, singleString(init));
    }

    public CodeTreeBuilder declarationDefault(TypeMirror type, String name) {
        return declaration(type, name, createBuilder().defaultValue(type).build());
    }

    public CodeTreeBuilder declaration(String type, String name, CodeTree init) {
        startStatement();
        string(type);
        string(" ");
        string(name);
        if (init != null) {
            string(" = ");
            tree(init);
        }
        end(); // statement
        return this;
    }

    public CodeTreeBuilder declaration(String type, String name, String init) {
        return declaration(type, name, singleString(init));
    }

    public CodeTreeBuilder declaration(TypeMirror type, String name, CodeTree init) {
        if (ElementUtils.isVoid(type)) {
            startStatement();
            tree(init);
            end();
        } else {
            startStatement();
            type(type);
            string(" ");
            string(name);
            if (init != null) {
                string(" = ");
                tree(init);
            }
            end(); // statement
        }
        return this;
    }

    public CodeTreeBuilder declaration(TypeMirror type, String name, CodeTreeBuilder init) {
        if (init == this) {
            throw new IllegalArgumentException("Recursive builder usage.");
        }
        return declaration(type, name, init.getTree());
    }

    public CodeTreeBuilder create() {
        return new CodeTreeBuilder(this);
    }

    public CodeTreeBuilder type(TypeMirror type) {
        return push(type, false);
    }

    public CodeTreeBuilder typeLiteral(TypeMirror type) {
        return startGroup().push(type, true).end();
    }

    private void assertRoot() {
        if (currentElement != root) {
            throw new IllegalStateException("CodeTreeBuilder was not ended properly.");
        }
    }

    public CodeTreeBuilder startCaseBlock() {
        return startIndention();
    }

    public CodeTreeBuilder startThrow() {
        return startStatement().string("throw ");
    }

    public CodeTree getTree() {
        assertRoot();
        return root;
    }

    public CodeTree build() {
        return root;
    }

    public CodeTreeBuilder cast(TypeMirror type) {
        string("(").type(type).string(") ");
        return this;
    }

    public CodeTreeBuilder maybeCast(TypeMirror sourceType, TypeMirror targetType) {
        if (ElementUtils.needsCastTo(sourceType, targetType)) {
            cast(targetType);
        }
        return this;
    }

    public CodeTreeBuilder maybeCast(TypeMirror sourceType, TypeMirror targetType, String receiver) {
        if (ElementUtils.needsCastTo(sourceType, targetType)) {
            string("(");
            cast(targetType);
            string(receiver, ") ");
        } else {
            string(receiver);
        }
        return this;
    }

    public CodeTreeBuilder cast(TypeMirror type, CodeTree content) {
        if (ElementUtils.isVoid(type)) {
            tree(content);
            return this;
        } else {
            return startGroup().string("(").type(type).string(")").string(" ").tree(content).end();
        }
    }

    public CodeTreeBuilder startSuperCall() {
        return string("super").startParanthesesCommaGroup();
    }

    public CodeTreeBuilder returnFalse() {
        return startReturn().string("false").end();
    }

    public CodeTreeBuilder returnStatement() {
        return statement("return");
    }

    public ExecutableElement findMethod() {
        if (enclosingElement != null && (enclosingElement.getKind() == ElementKind.METHOD || enclosingElement.getKind() == ElementKind.CONSTRUCTOR)) {
            return (ExecutableElement) enclosingElement;
        }
        return null;
    }

    public CodeTreeBuilder returnNull() {
        return startReturn().string("null").end();
    }

    public CodeTreeBuilder returnTrue() {
        return startReturn().string("true").end();
    }

    public CodeTreeBuilder instanceOf(CodeTree var, TypeMirror type) {
        return tree(var).string(" instanceof ").type(type);
    }

    public CodeTreeBuilder instanceOf(TypeMirror type) {
        return string(" instanceof ").type(type);
    }

    public CodeTreeBuilder defaultValue(TypeMirror mirror) {
        return string(ElementUtils.defaultValue(mirror));
    }

    public CodeTreeBuilder startTryBlock() {
        return string("try ").startBlock();
    }

    public CodeTreeBuilder startCatchBlock(TypeMirror exceptionType, String localVarName) {
        clearLast(CodeTreeKind.NEW_LINE);
        string(" catch (").type(exceptionType).string(" ").string(localVarName).string(") ");
        return startBlock();
    }

    public CodeTreeBuilder startCatchBlock(TypeMirror[] exceptionTypes, String localVarName) {
        clearLast(CodeTreeKind.NEW_LINE);
        string(" catch (");

        for (int i = 0; i < exceptionTypes.length; i++) {
            if (i != 0) {
                string(" | ");
            }
            type(exceptionTypes[i]);
        }

        string(" ").string(localVarName).string(") ");
        return startBlock();
    }

    public CodeTreeBuilder startFinallyBlock() {
        clearLast(CodeTreeKind.NEW_LINE);
        string(" finally ");
        return startBlock();
    }

    public CodeTreeBuilder nullLiteral() {
        return string("null");
    }

    private static class BuilderCodeTree extends CodeTree {

        private EndCallback atEndListener;
        private CodeTreeKind removeLast;

        BuilderCodeTree(CodeTree parent, CodeTreeKind kind, TypeMirror type, String string) {
            super(parent, kind, type, string);
        }

        public void registerAtEnd(EndCallback atEnd) {
            if (this.atEndListener != null) {
                this.atEndListener = new CompoundCallback(this.atEndListener, atEnd);
            } else {
                this.atEndListener = atEnd;
            }
        }

        public EndCallback getAtEndListener() {
            return atEndListener;
        }

        @Override
        public String toString() {
            final StringBuilder b = new StringBuilder();
            new Printer(b).visitTree(this, null, null);
            return b.toString();
        }

        private static class CompoundCallback implements EndCallback {

            private final EndCallback callback1;
            private final EndCallback callback2;

            CompoundCallback(EndCallback callback1, EndCallback callback2) {
                this.callback1 = callback1;
                this.callback2 = callback2;
            }

            @Override
            public void afterEnd() {
                callback1.afterEnd();
                callback2.afterEnd();
            }

            @Override
            public void beforeEnd() {
                callback1.beforeEnd();
                callback1.beforeEnd();
            }
        }

    }

    private interface EndCallback {

        void beforeEnd();

        void afterEnd();
    }

    private static class Printer extends CodeElementScanner<Void, Void> {

        private int indent;
        private boolean newLine;
        private final String ln = System.lineSeparator();

        private final StringBuilder b;

        Printer(StringBuilder b) {
            this.b = b;
        }

        @Override
        public void visitTree(CodeTree e, Void p, Element enclosingElement) {
            switch (e.getCodeKind()) {
                case COMMA_GROUP:
                    List<CodeTree> children = e.getEnclosedElements();
                    if (children != null) {
                        for (int i = 0; i < children.size(); i++) {
                            visitTree(children.get(i), p, enclosingElement);
                            if (i < e.getEnclosedElements().size() - 1) {
                                b.append(", ");
                            }
                        }
                    }
                    break;
                case GROUP:
                    super.visitTree(e, p, enclosingElement);
                    break;
                case INDENT:
                    indent();
                    super.visitTree(e, p, enclosingElement);
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
                case TYPE:
                    write(ElementUtils.getSimpleName(e.getType()));
                    break;
                case STATIC_METHOD_REFERENCE:
                    write(ElementUtils.getSimpleName(e.getType()) + "." + e.getString());
                    break;
                default:
                    assert false;
                    return;
            }
        }

        private void indent() {
            indent++;
        }

        private void dedent() {
            indent--;
        }

        private void writeLn() {
            write(ln);
            newLine = true;
        }

        private void write(String m) {
            if (newLine && m != ln) {
                writeIndent();
                newLine = false;
            }
            b.append(m);
        }

        private void writeIndent() {
            for (int i = 0; i < indent; i++) {
                b.append("    ");
            }
        }
    }

    public CodeTreeBuilder returnDefault() {
        ExecutableElement method = findMethod();
        if (ElementUtils.isVoid(method.getReturnType())) {
            returnStatement();
        } else {
            startReturn().defaultValue(method.getReturnType()).end();
        }
        return this;

    }

    public CodeTreeBuilder startAssign(String receiver, VariableElement field) {
        return startStatement().field(receiver, field).string(" = ");
    }

    public CodeTreeBuilder field(String receiver, VariableElement field) {
        if (receiver == null && field.getModifiers().contains(Modifier.STATIC)) {
            return staticReference(field);
        } else {
            return string(receiver, ".", field.getSimpleName().toString());
        }
    }

    public CodeTreeBuilder constantLiteral(TypeMirror type, int index) {
        if (type.getKind() == TypeKind.BYTE) {
            return string("(byte) ", String.valueOf(index));
        }
        return null;
    }

}
