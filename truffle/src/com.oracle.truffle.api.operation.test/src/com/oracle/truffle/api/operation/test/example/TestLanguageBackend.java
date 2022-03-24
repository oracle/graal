package com.oracle.truffle.api.operation.test.example;

import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.operation.test.example.TestLanguageAst.ListNode;
import com.oracle.truffle.api.operation.test.example.TestLanguageAst.NumberNode;
import com.oracle.truffle.api.operation.test.example.TestLanguageAst.SymbolNode;
import com.oracle.truffle.api.source.Source;

class TestLanguageBackend {

    private final TestOperationsBuilder b;

    public TestLanguageBackend(TestOperationsBuilder b) {
        this.b = b;
    }

    public void buildRoot(Source source, TestLanguageAst ast) {
        b.setNodeName("TestFunction");
        b.beginSource(source);
        build(ast);
        b.endSource();
        b.build();
    }

    private void build(TestLanguageAst ast) {
        b.beginSourceSection(ast.startOffset);
        if (ast instanceof ListNode) {
            buildList((ListNode) ast);
        } else if (ast instanceof NumberNode) {
            b.emitConstObject(getLong(ast));
        } else {
            throw new IllegalArgumentException("unexpected value");
        }
        b.endSourceSection(ast.length);
    }

    private static long getLong(TestLanguageAst ast) {
        if (ast instanceof NumberNode) {
            return ((NumberNode) ast).value;
        } else {
            throw new IllegalArgumentException("expected number");
        }
    }

    private void buildList(ListNode ast) {
        if (ast.isEmpty()) {
            throw new IllegalArgumentException("illegal empty list");
        }
        if (!(ast.get(0) instanceof SymbolNode)) {
            throw new IllegalArgumentException("lists should always have symbol head");
        }

        SymbolNode head = (SymbolNode) ast.get(0);

        switch (head.name) {
            case "add":
                if (ast.size() != 3) {
                    throw new IllegalArgumentException("add expects 2 arguments");
                }
                b.beginAddOperation();
                build(ast.get(1));
                build(ast.get(2));
                b.endAddOperation();
                break;
            case "less":
                if (ast.size() != 3) {
                    throw new IllegalArgumentException("add expects 2 arguments");
                }
                b.beginLessThanOperation();
                build(ast.get(1));
                build(ast.get(2));
                b.endLessThanOperation();
                break;
            case "if":
                if (ast.size() == 3) {
                    b.beginIfThen();
                    build(ast.get(1));
                    build(ast.get(2));
                    b.endIfThen();
                } else if (ast.size() == 4) {
                    b.beginIfThenElse();
                    build(ast.get(1));
                    build(ast.get(2));
                    build(ast.get(3));
                    b.endIfThenElse();
                } else {
                    throw new IllegalArgumentException("if expects 2 or 3 arguments");
                }
                break;
            case "cond":
                if (ast.size() != 4) {
                    throw new IllegalArgumentException("cond expects 3 arguments");
                }
                b.beginConditional();
                build(ast.get(1));
                build(ast.get(2));
                build(ast.get(3));
                b.endConditional();
                break;
            case "local":
                if (ast.size() != 2) {
                    throw new IllegalArgumentException("local expects 1 argument");
                }
                b.emitLoadLocal((int) getLong(ast.get(1)));
                break;
            case "arg":
                if (ast.size() != 2) {
                    throw new IllegalArgumentException("arg expects 1 argument");
                }
                b.emitLoadArgument((int) getLong(ast.get(1)));
                break;
            case "setlocal":
                if (ast.size() != 3) {
                    throw new IllegalArgumentException("local expects 1 argument");
                }
                b.beginStoreLocal((int) getLong(ast.get(1)));
                build(ast.get(2));
                b.endStoreLocal();
                break;
            case "inclocal":
                if (ast.size() != 3) {
                    throw new IllegalArgumentException("local expects 1 argument");
                }
                b.beginStoreLocal((int) getLong(ast.get(1)));
                b.beginAddOperation();
                b.emitLoadLocal((int) getLong(ast.get(1)));
                build(ast.get(2));
                b.endAddOperation();
                b.endStoreLocal();
                break;
            case "while":
                if (ast.size() != 3) {
                    throw new IllegalArgumentException("while expects 2 arguments");
                }
                b.beginWhile();
                build(ast.get(1));
                build(ast.get(2));
                b.endWhile();
                break;
            case "return":
                if (ast.size() != 2) {
                    throw new IllegalArgumentException("while expects 1 argument");
                }
                b.beginReturn();
                build(ast.get(1));
                b.endReturn();
                break;
            case "stmt":
                b.beginInstrumentation(StatementTag.class);
                for (int i = 1; i < ast.size(); i++) {
                    build(ast.get(i));
                }
                b.endInstrumentation();
                break;
            case "fail":
                if (ast.size() != 1) {
                    throw new IllegalArgumentException("fail expects no arguments");
                }
                b.emitThrowOperation();
                break;
            case "try":
                if (ast.size() != 4) {
                    throw new IllegalArgumentException("try expects 3 arguments");
                }
                b.beginTryCatch((int) getLong(ast.get(1)));
                build(ast.get(2));
                build(ast.get(3));
                b.endTryCatch();
                break;
            case "do":
                b.beginBlock();
                for (int i = 1; i < ast.size(); i++) {
                    build(ast.get(i));
                }
                b.endBlock();
                break;
        }
    }
}