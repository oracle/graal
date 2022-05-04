package com.oracle.truffle.api.operation.test.example;

import java.util.List;
import java.util.function.Consumer;

import org.junit.Assert;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.operation.AbstractOperationsTruffleException;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationsNode;
import com.oracle.truffle.api.operation.Variadic;
import com.oracle.truffle.api.source.Source;

@GenerateOperations
@GenerateAOT
public class TestOperations {

    private static class TestException extends AbstractOperationsTruffleException {

        private static final long serialVersionUID = -9143719084054578413L;

        public TestException(String string, OperationsNode node, int bci) {
            super(string, node, bci);
        }
    }

    @SuppressWarnings("unused")
    public static void parse(TestLanguage language, Object input, TestOperationsBuilder builder) {
        if (input instanceof Source) {
            Source source = (Source) input;
            TestLanguageAst ast = new TestLanguageParser(source).parse();
            new TestLanguageBackend(builder).buildRoot(source, ast);
        } else if (input instanceof Consumer<?>) {
            @SuppressWarnings("unchecked")
            Consumer<TestOperationsBuilder> callback = (Consumer<TestOperationsBuilder>) input;
            callback.accept(builder);
        } else {
            Assert.fail("invalid parser");
        }
    }

    @Operation
    @GenerateAOT
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
    @GenerateAOT
    static class LessThanOperation {
        @Specialization
        public static boolean lessThan(long lhs, long rhs) {
            return lhs < rhs;
        }
    }

    @Operation
    @GenerateAOT
    static class VeryComplexOperation {
        @Specialization
        public static long bla(long a1, @Variadic Object[] a2) {
            return a1 + a2.length;
        }
    }

    @Operation
    @GenerateAOT
    static class ThrowOperation {
        @Specialization
        public static Object perform(@Bind("$bci") int bci, @Bind("this") OperationsNode node) {
            throw new TestException("fail", node, bci);
        }
    }

    @Operation
    static class AlwaysBoxOperation {
        @Specialization
        public static Object perform(Object value) {
            return value;
        }
    }

    @Operation
    static class AppenderOperation {
        @Specialization
        public static void perform(List<Object> list, Object value) {
            list.add(value);
        }
    }
}