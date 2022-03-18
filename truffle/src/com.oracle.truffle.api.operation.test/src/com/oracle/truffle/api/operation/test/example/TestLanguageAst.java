package com.oracle.truffle.api.operation.test.example;

public abstract class TestLanguageAst {
    final int startOffset;
    final int length;

    public TestLanguageAst(int startOffset, int length) {
        this.startOffset = startOffset;
        this.length = length;
    }

    public static class ListNode extends TestLanguageAst {
        final TestLanguageAst[] children;

        public ListNode(int startOffset, int length, TestLanguageAst[] children) {
            super(startOffset, length);
            this.children = children;
        }

        public boolean isEmpty() {
            return children.length == 0;
        }

        public TestLanguageAst get(int idx) {
            return children[idx];
        }

        public int size() {
            return children.length;
        }
    }

    public static class SymbolNode extends TestLanguageAst {
        final String name;

        public SymbolNode(int startOffset, int length, String name) {
            super(startOffset, length);
            this.name = name;
        }
    }

    public static class NumberNode extends TestLanguageAst {
        final long value;

        public NumberNode(int startOffset, int length, long value) {
            super(startOffset, length);
            this.value = value;
        }
    }
}