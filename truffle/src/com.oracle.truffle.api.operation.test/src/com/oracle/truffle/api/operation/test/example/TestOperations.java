package com.oracle.truffle.api.operation.test.example;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.operation.AbstractOperationsTruffleException;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationsNode;
import com.oracle.truffle.api.operation.Variadic;
import com.oracle.truffle.api.source.Source;

@GenerateOperations
public class TestOperations {

    private static class TestException extends AbstractOperationsTruffleException {

        private static final long serialVersionUID = -9143719084054578413L;

        public TestException(String string, OperationsNode node, int bci) {
            super(string, node, bci);
        }
    }

    public static void parse(TestLanguage language, Source source, TestOperationsBuilder builder) {
        TestLanguageAst ast = new TestLanguageParser(source).parse();
        System.out.println(ast);
        new TestLanguageBackend(builder).buildRoot(source, ast);
    }

    @Operation
    static class AddOperation {
        // @Cached(inline = true) MyOtherNode node;

        @Specialization
        public static long add(long lhs, long rhs) {
            return lhs + rhs;
        }

        @Specialization
        public static String addStrings(String lhs, String rhs) {
            return lhs + rhs;
        }
    }

    @Operation
    static class LessThanOperation {
        @Specialization
        public static boolean lessThan(long lhs, long rhs) {
            return lhs < rhs;
        }
    }

    @Operation
    static class VeryComplexOperation {
        @Specialization
        public static long bla(long a1, @Variadic Object[] a2) {
            return a1 + a2.length;
        }
    }

    @Operation
    static class ThrowOperation {
        @Specialization
        public static void perform(@Bind("$bci") int bci, @Bind("this") OperationsNode node) {
            throw new TestException("fail", node, bci);
        }
    }
}