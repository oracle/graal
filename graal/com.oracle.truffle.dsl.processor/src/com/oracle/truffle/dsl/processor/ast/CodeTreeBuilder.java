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
package com.oracle.truffle.dsl.processor.ast;

import static com.oracle.truffle.dsl.processor.ast.CodeTreeKind.*;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.*;

public class CodeTreeBuilder {

    private final CodeTreeBuilder parent;

    private BuilderCodeTree currentElement;
    private final BuilderCodeTree root;

    private int treeCount;

    public CodeTreeBuilder(CodeTreeBuilder parent) {
        this.root = new BuilderCodeTree(GROUP, null, null);
        this.currentElement = root;
        this.parent = parent;
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
        return new CodeTreeBuilder(null).string(s).getTree();
    }

    public static CodeTree singleType(TypeMirror s) {
        return new CodeTreeBuilder(null).type(s).getTree();
    }

    private CodeTreeBuilder push(CodeTreeKind kind) {
        return push(new BuilderCodeTree(kind, null, null));
    }

    private CodeTreeBuilder push(String string) {
        return push(new BuilderCodeTree(CodeTreeKind.STRING, null, string));
    }

    private CodeTreeBuilder push(TypeMirror type) {
        return push(new BuilderCodeTree(CodeTreeKind.TYPE, type, null));
    }

    private CodeTreeBuilder push(CodeTreeKind kind, TypeMirror type, String string) {
        return push(new BuilderCodeTree(kind, type, string));
    }

    private CodeTreeBuilder push(BuilderCodeTree tree) {
        if (currentElement != null) {
            if (!removeLastIfEnqueued(tree)) {
                return this;
            }
            currentElement.add(tree);
        }
        switch (tree.getCodeKind()) {
            case COMMA_GROUP:
            case GROUP:
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
        if (!childTree.isEmpty()) {
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
            BuilderCodeTree tree = new BuilderCodeTree(REMOVE_LAST, null, null);
            tree.removeLast = kind;
            push(tree);
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

    public CodeTreeBuilder startCall(String receiver, String callSite) {
        return startCall(singleString(receiver), callSite);
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
        return startStaticCall(Utils.findNearestEnclosingType(method).asType(), method.getSimpleName().toString());
    }

    public CodeTreeBuilder staticReference(TypeMirror type, String fieldName) {
        return push(CodeTreeKind.STATIC_FIELD_REFERENCE, type, fieldName);
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
        if (treeToAdd instanceof BuilderCodeTree) {
            return push((BuilderCodeTree) treeToAdd).end();
        } else {
            BuilderCodeTree tree = new BuilderCodeTree(GROUP, null, null);
            tree.add(treeToAdd);
            return push(tree).end();
        }
    }

    public CodeTreeBuilder string(String chunk1, String chunk2, String chunk3, String chunk4, String... chunks) {
        push(GROUP).string(chunk1).string(chunk2).string(chunk3).string(chunk4);
        for (int i = 0; i < chunks.length; i++) {
            string(chunks[i]);
        }
        return end();
    }

    public CodeTreeBuilder dot() {
        return string(".");
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
        return startGroup().string("switch ").startParanthesesCommaGroup().endAndWhitespaceAfter();
    }

    public CodeTreeBuilder startReturn() {
        ExecutableElement method = findMethod();
        if (method != null && Utils.isVoid(method.getReturnType())) {
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
        Element parentElement = currentElement.getEnclosingElement();
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

    private void registerCallBack(EndCallback callback) {
        currentElement.registerAtEnd(callback);
    }

    public CodeTreeBuilder defaultDeclaration(TypeMirror type, String name) {
        if (!Utils.isVoid(type)) {
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
        if (Utils.isVoid(type)) {
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

    public CodeTreeBuilder declaration(String type, String name, CodeTreeBuilder init) {
        if (init == this) {
            throw new IllegalArgumentException("Recursive builder usage.");
        }
        return declaration(type, name, init.getTree());
    }

    public CodeTreeBuilder declaration(TypeMirror type, String name) {
        return declaration(type, name, (CodeTree) null);
    }

    public CodeTreeBuilder create() {
        return new CodeTreeBuilder(this);
    }

    public CodeTreeBuilder type(TypeMirror type) {
        return push(type);
    }

    public CodeTreeBuilder typeLiteral(TypeMirror type) {
        return startGroup().type(type).string(".class").end();
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

    public CodeTree getRoot() {
        return root;
    }

    public CodeTreeBuilder cast(String baseClassName) {
        string("(").string(baseClassName).string(") ");
        return this;
    }

    public CodeTreeBuilder cast(TypeMirror type, CodeTree content) {
        if (Utils.isVoid(type)) {
            tree(content);
            return this;
        } else if (type.getKind() == TypeKind.DECLARED && Utils.getQualifiedName(type).equals("java.lang.Object")) {
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
        Element element = currentElement;
        while (element != null && (element.getKind() != ElementKind.METHOD && (element.getKind() != ElementKind.CONSTRUCTOR))) {
            element = element.getEnclosingElement();
        }
        ExecutableElement found = element != null ? (ExecutableElement) element : null;
        if (found == null && parent != null) {
            found = parent.findMethod();
        }
        return found;
    }

    public CodeTreeBuilder returnTrue() {
        return startReturn().string("true").end();
    }

    public CodeTreeBuilder instanceOf(CodeTree var, CodeTree type) {
        tree(var).string(" instanceof ").tree(type);
        return this;
    }

    public CodeTreeBuilder instanceOf(String var, String type) {
        return instanceOf(singleString(var), singleString(type));
    }

    public CodeTreeBuilder instanceOf(String var, TypeMirror type) {
        TypeElement element = Utils.fromTypeMirror(type);
        if (element == null) {
            throw new IllegalArgumentException("Cannot call instanceof for a non supported type: " + type.getKind());
        }
        return instanceOf(singleString(var), singleType(type));
    }

    public CodeTreeBuilder defaultValue(TypeMirror mirror) {
        switch (mirror.getKind()) {
            case VOID:
                return string("");
            case ARRAY:
            case DECLARED:
            case PACKAGE:
            case NULL:
                return string("null");
            case BOOLEAN:
                return string("false");
            case BYTE:
                return string("(byte) 0");
            case CHAR:
                return string("(char) 0");
            case DOUBLE:
                return string("0.0D");
            case LONG:
                return string("0L");
            case INT:
                return string("0");
            case FLOAT:
                return string("0.0F");
            case SHORT:
                return string("(short) 0");
            default:
                throw new AssertionError();
        }
    }

    public CodeTreeBuilder assertFalse() {
        return startAssert().string("false").end();
    }

    public CodeTreeBuilder breakStatement() {
        return statement("break");
    }

    public CodeTreeBuilder isNull() {
        return string(" == null");
    }

    public CodeTreeBuilder isNotNull() {
        return string(" != null");
    }

    public CodeTreeBuilder is(CodeTree tree) {
        return string(" == ").tree(tree);
    }

    public CodeTreeBuilder startTryBlock() {
        return string("try ").startBlock();
    }

    public CodeTreeBuilder startCatchBlock(TypeMirror exceptionType, String localVarName) {
        clearLast(CodeTreeKind.NEW_LINE);
        string(" catch (").type(exceptionType).string(" ").string(localVarName).string(") ");
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

        public BuilderCodeTree(CodeTreeKind kind, TypeMirror type, String string) {
            super(kind, type, string);
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
            acceptCodeElementScanner(new Printer(b), null);
            return b.toString();
        }

        private static class CompoundCallback implements EndCallback {

            private final EndCallback callback1;
            private final EndCallback callback2;

            public CompoundCallback(EndCallback callback1, EndCallback callback2) {
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
        private final String ln = "\n";

        private final StringBuilder b;

        Printer(StringBuilder b) {
            this.b = b;
        }

        @Override
        public void visitTree(CodeTree e, Void p) {
            switch (e.getCodeKind()) {
                case COMMA_GROUP:
                    List<CodeTree> children = e.getEnclosedElements();
                    for (int i = 0; i < children.size(); i++) {
                        children.get(i).acceptCodeElementScanner(this, p);
                        if (i < e.getEnclosedElements().size() - 1) {
                            b.append(", ");
                        }
                    }
                    break;
                case GROUP:
                    super.visitTree(e, p);
                    break;
                case INDENT:
                    indent();
                    super.visitTree(e, p);
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
                    write(Utils.getSimpleName(e.getType()));
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

}
