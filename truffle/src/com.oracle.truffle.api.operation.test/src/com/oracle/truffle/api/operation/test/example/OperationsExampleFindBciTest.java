package com.oracle.truffle.api.operation.test.example;

import static com.oracle.truffle.api.operation.test.example.OperationsExampleCommon.parseNodeWithSource;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.OperationLocal;
import com.oracle.truffle.api.operation.OperationRootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@RunWith(Parameterized.class)
public class OperationsExampleFindBciTest {
    protected static final OperationsExampleLanguage LANGUAGE = null;

    @Parameters(name = "{0}")
    public static List<Class<? extends OperationsExample>> getInterpreterClasses() {
        return List.of(OperationsExampleBase.class, OperationsExampleWithBaseline.class);
    }

    @Parameter(0) public Class<? extends OperationsExample> interpreterClass;

    @Test
    public void testStacktrace() {
        /**
         * @formatter:off
         * def baz(arg0) {
         *   <trace>  // collects trace into frames
         *   4
         * }
         *
         * def bar() {
         *   (1 + arg0) + baz()
         * }
         *
         * def foo(arg0) {
         *   1 + bar(2)
         * }
         * @formatter:on
         */

        List<FrameInstance> frames = new ArrayList<>();

        Source bazSource = Source.newBuilder("test", "<trace>; 4", "baz").build();
        OperationsExample baz = parseNodeWithSource(interpreterClass, "baz", b -> {
            b.beginRoot(LANGUAGE);
            b.beginSource(bazSource);

            b.beginBlock();

            b.beginSourceSection(0, 7);
            b.beginInvoke();
            b.emitLoadConstant(new RootNode(LANGUAGE) {
                @Override
                public Object execute(VirtualFrame frame) {
                    Truffle.getRuntime().iterateFrames(f -> {
                        frames.add(f);
                        return null;
                    });
                    return null;
                }
            }.getCallTarget());
            b.endInvoke();
            b.endSourceSection();

            b.beginReturn();
            b.emitLoadConstant(4L);
            b.endReturn();

            b.endBlock();

            b.endSource();
            b.endRoot();
        });

        Source barSource = Source.newBuilder("test", "(1 + arg0) + baz()", "bar").build();
        OperationsExample bar = parseNodeWithSource(OperationsExampleBase.class, "bar", b -> {
            b.beginRoot(LANGUAGE);
            b.beginSource(barSource);

            b.beginReturn();
            b.beginAddOperation();

            b.beginAddOperation();
            b.emitLoadConstant(1L);
            b.emitLoadArgument(0);
            b.endAddOperation();

            b.beginSourceSection(13, 5);
            b.beginInvoke();
            b.emitLoadConstant(baz);
            b.endInvoke();
            b.endSourceSection();

            b.endAddOperation();
            b.endReturn();

            b.endSource();
            b.endRoot();
        });

        Source fooSource = Source.newBuilder("test", "1 + bar(2)", "foo").build();
        OperationsExample foo = parseNodeWithSource(OperationsExampleBase.class, "foo", b -> {
            b.beginRoot(LANGUAGE);
            b.beginSource(fooSource);

            b.beginReturn();
            b.beginAddOperation();

            b.emitLoadConstant(1L);

            b.beginSourceSection(4, 6);
            b.beginInvoke();
            b.emitLoadConstant(bar);
            b.emitLoadConstant(2L);
            b.endInvoke();
            b.endSourceSection();

            b.endAddOperation();
            b.endReturn();

            b.endSource();
            b.endRoot();
        });

        assertEquals(8L, foo.getCallTarget().call());
        assertEquals(4, frames.size());

        // <anon>
        assertNull(frames.get(0).getCallNode());
        assertEquals(-1, OperationRootNode.findBci(frames.get(0)));

        // baz
        int bazBci = OperationRootNode.findBci(frames.get(1));
        assertNotEquals(-1, bazBci);
        SourceSection bazSourceSection = baz.getSourceSectionAtBci(bazBci);
        assertEquals(bazSource, bazSourceSection.getSource());
        assertEquals("<trace>", bazSourceSection.getCharacters());

        // bar
        int barBci = OperationRootNode.findBci(frames.get(2));
        assertNotEquals(-1, barBci);
        SourceSection barSourceSection = bar.getSourceSectionAtBci(barBci);
        assertEquals(barSource, barSourceSection.getSource());
        assertEquals("baz()", barSourceSection.getCharacters());

        // foo
        int fooBci = OperationRootNode.findBci(frames.get(3));
        assertNotEquals(-1, fooBci);
        SourceSection fooSourceSection = foo.getSourceSectionAtBci(fooBci);
        assertEquals(fooSource, fooSourceSection.getSource());
        assertEquals("bar(2)", fooSourceSection.getCharacters());
    }

    @Test
    public void testStacktraceWithContinuation() {
        /**
         * @formatter:off
         * def baz(arg0) {
         *   if (arg0) <trace1> else <trace2>  // directly returns a trace
         * }
         *
         * def bar() {
         *   x = yield 1;
         *   baz(x)
         * }
         *
         * def foo(arg0) {
         *   c = bar();
         *   continue(c, arg0)
         * }
         * @formatter:on
         */
        Source bazSource = Source.newBuilder("test", "if (arg0) <trace1> else <trace2>", "baz").build();
        CallTarget collectFrames = new RootNode(LANGUAGE) {
            @Override
            public Object execute(VirtualFrame frame) {
                List<FrameInstance> frames = new ArrayList<>();
                Truffle.getRuntime().iterateFrames(f -> {
                    frames.add(f);
                    return null;
                });
                return frames;
            }
        }.getCallTarget();

        OperationsExample baz = parseNodeWithSource(interpreterClass, "baz", b -> {
            b.beginRoot(LANGUAGE);
            b.beginSource(bazSource);
            b.beginBlock();

            b.beginIfThenElse();

            b.emitLoadArgument(0);

            b.beginSourceSection(10, 8);
            b.beginReturn();
            b.beginInvoke();
            b.emitLoadConstant(collectFrames);
            b.endInvoke();
            b.endReturn();
            b.endSourceSection();

            b.beginSourceSection(24, 8);
            b.beginReturn();
            b.beginInvoke();
            b.emitLoadConstant(collectFrames);
            b.endInvoke();
            b.endReturn();
            b.endSourceSection();

            b.endIfThenElse();

            b.endBlock();
            b.endSource();
            b.endRoot();
        });

        Source barSource = Source.newBuilder("test", "x = yield 1; baz(x)", "bar").build();
        OperationsExample bar = parseNodeWithSource(OperationsExampleBase.class, "bar", b -> {
            b.beginRoot(LANGUAGE);
            b.beginSource(barSource);
            b.beginBlock();
            OperationLocal x = b.createLocal();

            b.beginStoreLocal(x);
            b.beginYield();
            b.emitLoadConstant(1);
            b.endYield();
            b.endStoreLocal();

            b.beginSourceSection(13, 6);
            b.beginReturn();
            b.beginInvoke();
            b.emitLoadConstant(baz);
            b.emitLoadLocal(x);
            b.endInvoke();
            b.endReturn();
            b.endSourceSection();

            b.endBlock();
            b.endSource();
            b.endRoot();
        });

        Source fooSource = Source.newBuilder("test", "c = bar(); continue(c, arg0)", "foo").build();
        OperationsExample foo = parseNodeWithSource(OperationsExampleBase.class, "foo", b -> {
            b.beginRoot(LANGUAGE);
            b.beginSource(fooSource);
            b.beginBlock();

            OperationLocal c = b.createLocal();

            b.beginStoreLocal(c);
            b.beginInvoke();
            b.emitLoadConstant(bar);
            b.endInvoke();
            b.endStoreLocal();

            b.beginSourceSection(11, 17);
            b.beginReturn();
            b.beginContinue();
            b.emitLoadLocal(c);
            b.emitLoadArgument(0);
            b.endContinue();
            b.endReturn();
            b.endSourceSection();

            b.endBlock();
            b.endSource();
            b.endRoot();
        });

        for (boolean continuationArgument : List.of(true, false)) {
            Object result = foo.getCallTarget().call(continuationArgument);
            assertTrue(result instanceof List<?>);

            @SuppressWarnings("unchecked")
            List<FrameInstance> frames = (List<FrameInstance>) result;
            assertEquals(4, frames.size());

            // <anon>
            assertNull(frames.get(0).getCallNode());
            assertEquals(-1, OperationRootNode.findBci(frames.get(0)));

            // baz
            int bazBci = OperationRootNode.findBci(frames.get(1));
            assertNotEquals(-1, bazBci);
            SourceSection bazSourceSection = baz.getSourceSectionAtBci(bazBci);
            assertEquals(bazSource, bazSourceSection.getSource());
            if (continuationArgument) {
                assertEquals("<trace1>", bazSourceSection.getCharacters());
            } else {
                assertEquals("<trace2>", bazSourceSection.getCharacters());
            }

            // bar
            int barBci = OperationRootNode.findBci(frames.get(2));
            assertNotEquals(-1, barBci);
            SourceSection barSourceSection = bar.getSourceSectionAtBci(barBci);
            assertEquals(barSource, barSourceSection.getSource());
            assertEquals("baz(x)", barSourceSection.getCharacters());

            // foo
            int fooBci = OperationRootNode.findBci(frames.get(3));
            assertNotEquals(-1, fooBci);
            SourceSection fooSourceSection = foo.getSourceSectionAtBci(fooBci);
            assertEquals(fooSource, fooSourceSection.getSource());
            assertEquals("continue(c, arg0)", fooSourceSection.getCharacters());
        }
    }
}
