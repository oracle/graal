package com.oracle.truffle.api.operation.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import static com.oracle.truffle.api.operation.test.TestOperationsCommon.parseNodeWithSource;

import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class TestOperationsFindBciTest {
    protected static final TestOperationsLanguage LANGUAGE = null;

    @Test
    public void testStacktrace() {
        List<FrameInstance> frames = new ArrayList<>();

        Source bazSource = Source.newBuilder("test", "<dump> 4", "baz").build();
        TestOperations baz = parseNodeWithSource(TestOperationsBase.class, "baz", b -> {
            b.beginRoot(LANGUAGE);
            b.beginSource(bazSource);

            b.beginBlock();

            b.beginSourceSection(0, 6);
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
        TestOperations bar = parseNodeWithSource(TestOperationsBase.class, "bar", b -> {
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
        TestOperations foo = parseNodeWithSource(TestOperationsBase.class, "foo", b -> {
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

        /*
         * @formatter:off
         * The stack should look like:
         *
         * <anon>
         * baz
         * bar
         * foo
         *
         * Given a call node, we can look up a bci; this bci should correspond to a specific source section.
         * @formatter:on
         */

        assertEquals(4, frames.size());

        // <anon>
        assertNull(frames.get(0).getCallNode());
        assertEquals(-1, TestOperationsBase.findBci(frames.get(0).getCallNode()));

        // baz
        int bazBci = TestOperationsBase.findBci(frames.get(1).getCallNode());
        assertNotEquals(-1, bazBci);
        SourceSection bazSourceSection = baz.getSourceSectionAtBci(bazBci);
        assertEquals(bazSourceSection.getSource(), bazSource);
        assertEquals(bazSourceSection.getCharacters(), "<dump>");

        // bar
        int barBci = TestOperationsBase.findBci(frames.get(2).getCallNode());
        assertNotEquals(-1, barBci);
        SourceSection barSourceSection = bar.getSourceSectionAtBci(barBci);
        assertEquals(barSourceSection.getSource(), barSource);
        assertEquals(barSourceSection.getCharacters(), "baz()");

        // foo
        int fooBci = TestOperationsBase.findBci(frames.get(3).getCallNode());
        assertNotEquals(-1, fooBci);
        SourceSection fooSourceSection = foo.getSourceSectionAtBci(fooBci);
        assertEquals(fooSourceSection.getSource(), fooSource);
        assertEquals(fooSourceSection.getCharacters(), "bar(2)");
    }
}
