package com.oracle.truffle.api.operation.test.example;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.operation.OperationsNode;
import com.oracle.truffle.api.operation.OperationsRootNode;
import com.oracle.truffle.api.operation.tracing.ExecutionTracer;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class TestOperationsParserTest {

    private static class Tester {
        private final OperationsNode node;
        private final OperationsRootNode rootNode;

        Tester(String src, boolean withSourceInfo) {
            Source s = Source.newBuilder("test-operations", src, "test").build();

            if (withSourceInfo) {
                node = TestOperationsBuilder.parseWithSourceInfo(null, s)[0];
            } else {
                node = TestOperationsBuilder.parse(null, s)[0];
            }
            rootNode = node.createRootNode();
        }

        Tester(String src) {
            this(src, false);
        }

        public Tester test(Object expectedResult, Object... arguments) {
            Object result = rootNode.getCallTarget().call(arguments);
            Assert.assertEquals(expectedResult, result);
            return this;
        }

        public Tester then(Consumer<OperationsNode> action) {
            action.accept(node);
            return this;
        }
    }

    private static OperationsNode parse(Consumer<TestOperationsBuilder> builder) {
        return TestOperationsBuilder.parse(null, builder)[0];
    }

    @Test
    public void testAdd() {
        OperationsNode node = parse(b -> {
            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAddOperation();
            b.endReturn();

            b.build();
        });

        RootCallTarget root = node.createRootNode().getCallTarget();

        Assert.assertEquals(42L, root.call(20L, 22L));
        Assert.assertEquals("foobar", root.call("foo", "bar"));
        Assert.assertEquals(100L, root.call(120L, -20L));
    }

    @Test
    public void testMax() {
        OperationsNode node = parse(b -> {
            b.beginIfThenElse();

            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endLessThanOperation();

            b.beginReturn();
            b.emitLoadArgument(1);
            b.endReturn();

            b.beginReturn();
            b.emitLoadArgument(0);
            b.endReturn();

            b.endIfThenElse();

            b.build();
        });

        RootCallTarget root = node.createRootNode().getCallTarget();

        Assert.assertEquals(42L, root.call(42L, 13L));
        Assert.assertEquals(42L, root.call(42L, 13L));
        Assert.assertEquals(42L, root.call(42L, 13L));
        Assert.assertEquals(42L, root.call(13L, 42L));
    }

    @Test
    public void testIfThen() {
        OperationsNode node = parse(b -> {
            b.beginIfThen();

            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitConstObject(0L);
            b.endLessThanOperation();

            b.beginReturn();
            b.emitConstObject(0L);
            b.endReturn();

            b.endIfThen();

            b.beginReturn();
            b.emitLoadArgument(0);
            b.endReturn();

            b.build();
        });

        RootCallTarget root = node.createRootNode().getCallTarget();

        Assert.assertEquals(0L, root.call(-2L));
        Assert.assertEquals(0L, root.call(-1L));
        Assert.assertEquals(0L, root.call(0L));
        Assert.assertEquals(1L, root.call(1L));
        Assert.assertEquals(2L, root.call(2L));
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
        OperationsNode node = parse(b -> {
            b.beginTryCatch(0);

            b.beginIfThen();
            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitConstObject(0L);
            b.endLessThanOperation();

            b.emitThrowOperation();

            b.endIfThen();

            b.beginReturn();
            b.emitConstObject(1L);
            b.endReturn();

            b.endTryCatch();

            b.beginReturn();
            b.emitConstObject(0L);
            b.endReturn();

            b.build();
        });

        RootCallTarget root = node.createRootNode().getCallTarget();

        Assert.assertEquals(1L, root.call(-1L));
        Assert.assertEquals(0L, root.call(1L));
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

        Context context = Context.create("test-operations");
        long result = context.eval("test-operations", src).asLong();
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

        Context context = Context.create("test-operations");
        try {
            context.eval(org.graalvm.polyglot.Source.newBuilder("test-operations", src, "test-operations").build());
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

    @Test
    public void testCompilation() {
        Context context = Context.create("test-operations");

        Value v = context.parse("test-operations", "(return (add (arg 0) (arg 1)))");
        for (long i = 0; i < 1000000; i++) {
            v.execute(i, 1L);
        }

        Assert.assertEquals(Value.asValue(7L), v.execute(3L, 4L));
    }
}
