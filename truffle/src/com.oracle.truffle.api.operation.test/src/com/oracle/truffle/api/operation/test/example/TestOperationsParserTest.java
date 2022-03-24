package com.oracle.truffle.api.operation.test.example;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.operation.OperationsNode;
import com.oracle.truffle.api.operation.tracing.ExecutionTracer;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@RunWith(JUnit4.class)
public class TestOperationsParserTest {

    private static class Tester {
        private final OperationsNode node;

        Tester(String src, boolean withSourceInfo) {
            Source s = Source.newBuilder("test", src, "test").build();

            if (withSourceInfo) {
                node = TestOperationsBuilder.parseWithSourceInfo(null, s)[0];
            } else {
                node = TestOperationsBuilder.parse(null, s)[0];
            }
            System.out.println(node.dump());
        }

        Tester(String src) {
            this(src, false);
        }

        public Tester test(Object expectedResult, Object... arguments) {
            Object result = node.getCallTarget().call(arguments);
            Assert.assertEquals(expectedResult, result);
            return this;
        }

        public Tester then(Consumer<OperationsNode> action) {
            action.accept(node);
            return this;
        }
    }

    @Test
    public void testAdd() {
        new Tester("(return (add (arg 0) (arg 1)))")//
                        .test(42L, 20L, 22L) //
                        .test("foobar", "foo", "bar") //
                        .test(100L, 120L, -20L);
    }

    @Test
    public void testMax() {
        new Tester("(if (less (arg 0) (arg 1)) (return (arg 1)) (return (arg 0)))") //
                        .test(42L, 42L, 13L) //
                        .test(42L, 42L, 13L) //
                        .test(42L, 42L, 13L) //
                        .test(42L, 13L, 42L);
    }

    @Test
    public void testIfThen() {
        new Tester("(do (if (less (arg 0) 0) (return 0)) (return (arg 0)))") //
                        .test(0L, -2L) //
                        .test(0L, -1L) //
                        .test(0L, 0L) //
                        .test(1L, 1L) //
                        .test(2L, 2L);
    }

    @Test
    public void testSumLoop() {
        //@formatter:off
        String src = "(do"
                   + "  (setlocal 0 0)"
                   + "  (setlocal 1 0)"
                   + "  (while"
                   + "    (less (local 0) (arg 0))"
                   + "    (do"
                   + "      (inclocal 1 (local 0))"
                   + "      (inclocal 0 1)))"
                   + "  (return (local 1)))";
        //@formatter:on
        new Tester(src).test(45L, 10L);
    }

    @Test
    public void testTryCatch() {
        //@formatter:off
        String src = "(do"
                   + "  (try 0"
                   + "    (if"
                   + "      (less (arg 0) 0)"
                   + "      (fail))"
                   + "    (return 1))"
                   + "  (return 0))";
        //@formatter:on

        new Tester(src).test(0L, 1L).test(1L, -1L);
    }

    @Test
    public void testSourceInfo() {
        String src = "  (return (add 1 2))";
        new Tester(src).then(node -> {
            SourceSection ss = node.getSourceSection();
            Assert.assertNotNull(ss);
            Assert.assertEquals(2, ss.getCharIndex());
            Assert.assertEquals(src.length(), ss.getCharEndIndex());
        });
    }

    @Test
    public void testContextEval() {
        String src = "(return (add 1 2))";

        Context context = Context.create("test");
        long result = context.eval("test", src).asLong();
        Assert.assertEquals(3, result);
    }

    @Test
    public void testStacktrace() {
        //@formatter:off
        String src = "(return\n"
                   + "  (add\n"
                   + "    1\n"
                   + "    (fail)))";
        //@formatter:on

        Context context = Context.create("test");
        try {
            context.eval(org.graalvm.polyglot.Source.newBuilder("test", src, "test").build());
            fail();
        } catch (PolyglotException ex) {
            Assert.assertEquals(4, ex.getStackTrace()[0].getLineNumber());
        } catch (IOException e) {
            Assert.fail();
        }
    }

    // @Test
    public void testTracing() {
        //@formatter:off
        String src = "(do"
                   + "  (setlocal 0 0)"
                   + "  (setlocal 2 0)"
                   + "  (while"
                   + "    (less (local 0) 100)"
                   + "    (do"
                   + "      (setlocal 1 0)"
                   + "      (while"
                   + "        (less (local 1) 100)"
                   + "        (do"
                   + "          (setlocal 2 (add (local 2) (local 1)))"
                   + "          (setlocal 1 (add (local 1) 1))))"
                   + "      (setlocal 0 (add (local 0) 1))))"
                   + "  (return (local 2)))";
        //@formatter:on
        new Tester(src).test(495000L);
        ExecutionTracer.get().dump();
    }

    @Test
    public void testInstrumentation() {
        //@formatter:off
        String src = "(stmt"
                   + "  (return (add 1 2)))";
        //@formatter:on

        new Tester(src, true).test(3L);
        ExecutionTracer.get().dump();
    }
}
