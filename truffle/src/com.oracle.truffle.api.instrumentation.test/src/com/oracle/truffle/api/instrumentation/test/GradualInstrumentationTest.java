package com.oracle.truffle.api.instrumentation.test;

import static com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.ID;
import static org.junit.Assert.assertEquals;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

public class GradualInstrumentationTest {
    private Context context;
    private TruffleInstrument.Env instrumentEnv;

    @Before
    public void setup() {
        context = Context.create(ID);
        instrumentEnv = context.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
    }

    @After
    public void teardown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    public void testOldTreeStillReachable() throws InterruptedException {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_EXPRESSION(LOOP(3, EXPRESSION)))");
        InstrumentationTestLanguage.RecordingExecutionEventListener listener1 = new InstrumentationTestLanguage.RecordingExecutionEventListener(true);
        EventBinding<ExecutionEventListener> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, listener1);
        Thread t = new Thread(() -> {
            listener1.start();
            try {
                context.eval(source);
            } finally {
                listener1.end();
            }
        });
        t.start();
        listener1.go("$START", "+R", "+S", "+E", "-E", "+L", "+E", "-E");
        InstrumentationTestLanguage.RecordingExecutionEventListener listener2 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, listener2);
        listener1.go("+E", "-E");
        InstrumentationTestLanguage.RecordingExecutionEventListener listener3 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, listener3);
        listener1.go("+E", "-E", "-L", "-S", "-R", "$END");
        t.join();
        binding.dispose();
        InstrumentationTestLanguage.RecordingExecutionEventListener listener4 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, listener4);
        context.eval(source);
        assertEquals("-E+E-E-L-S-R+R+S+E-E+L+E-E+E-E+E-E-L-S-R", listener2.getRecording());
        assertEquals("-E-L-S-R+R+S+E-E+L+E-E+E-E+E-E-L-S-R", listener3.getRecording());
        assertEquals("+R+S+E-E+L+E-E+E-E+E-E-L-S-R", listener4.getRecording());
    }

    @Test
    public void testRepeatedInstrumentationDoesNotChangeParentsInMaterializedTree() {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_EXPR_AND_STMT(MATERIALIZE_CHILD_EXPR_AND_STMT(EXPRESSION(EXPRESSION))))");
        InstrumentationTestLanguage.RecordingExecutionEventListener listener1 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        EventBinding<ExecutionEventListener> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), listener1);
        context.eval(source);
        assertEquals("+S+S-S+S+S-S-S-S", listener1.getRecording());
        binding.dispose();
        InstrumentationTestLanguage.RecordingExecutionEventListener listener2 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class).build(), listener2);
        context.eval(source);
        // Each materialized node, which itself is a statement, has an extra statement. If the
        // materialized node has an expression as a child and the children of that expression
        // consist of only one expression, then the nested expression is connected as a child
        // directly to the materialized node in place of its parent and for each expression which is
        // ommited this way, one child expression is added to the extra statement node.
        assertEquals("+S+S-S+S+S+E-E-S+E-E-S-S", listener2.getRecording());
    }

    @Test
    public void testRepeatedInstrumentationChangesParentsInMaterializedTreeIfSubtreesAreNotCloned() {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_EXPR_AND_STMT_NC(MATERIALIZE_CHILD_EXPR_AND_STMT_NC(EXPRESSION(EXPRESSION))))");
        InstrumentationTestLanguage.RecordingExecutionEventListener listener1 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        EventBinding<ExecutionEventListener> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(), listener1);
        context.eval(source);
        assertEquals("+S+S-S+S+S-S-S-S", listener1.getRecording());
        binding.dispose();
        InstrumentationTestLanguage.RecordingExecutionEventListener listener2 = new InstrumentationTestLanguage.RecordingExecutionEventListener();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class).build(), listener2);
        context.eval(source);
        // Each materialized node, which itself is a statement, has an extra statement. If the
        // materialized node has an expression as a child and the children of that expression
        // consist of only one expression, then the nested expression is connected as a child
        // directly to the materialized node in place of its parent and for each expression which is
        // ommited this way, one child expression is added to the extra statement node. Since
        // the NC node erroneously does not clone the subtree on materialization, the repeated
        // instrumentation changes the parent of the nested expression to its original expression
        // parent, and so in the new materialized tree, the nested expression node which is now
        // a direct child of the materialized NC node is not instrumented in the new tree, because
        // it was already instrumented in the old tree (it already has instrumentation wrapper).
        assertEquals("+S+S-S+S+S+E-E-S-S-S", listener2.getRecording());
    }
}
