package com.oracle.truffle.api.operation.test.example;

import java.util.List;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.Special;
import com.oracle.truffle.api.operation.Special.SpecialKind;
import com.oracle.truffle.api.operation.Variadic;
import com.oracle.truffle.api.source.Source;

@GenerateOperations
public class TestOperations {

    private static class TestException extends AbstractTruffleException {

        private static final long serialVersionUID = -9143719084054578413L;

        public TestException(String string, Node node) {
            super(string, node);
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
        public static String addStrings(String lhs, String rhs, @Cached("1") int test) {
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
        public static void perform(@Special(SpecialKind.NODE) Node node) {
            throw new TestException("fail", node);
        }
    }
}