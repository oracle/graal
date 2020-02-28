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
    public void testOneStatament() {
        Source source = Source.create(ID, "ROOT(MATERIALIZE_CHILD_EXPR_AND_STMT(MATERIALIZE_CHILD_EXPR_AND_STMT(EXPRESSION(EXPRESSION))))");
        StringBuilder sb = new StringBuilder();
        EventBinding<ExecutionEventListener> binding = instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).build(),
                        new ExecutionEventListener() {
                            @Override
                            public void onEnter(EventContext context, VirtualFrame frame) {
                            }

                            @Override
                            public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                            }

                            @Override
                            public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                            }
                        });
        context.eval(source);
        binding.dispose();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class, StandardTags.ExpressionTag.class).build(),
                        new ExecutionEventListener() {
                            @Override
                            public void onEnter(EventContext context, VirtualFrame frame) {
                                if (context.getInstrumentedNode() instanceof InstrumentationTestLanguage.StatementNode) {
                                    sb.append("+S");
                                } else if (context.getInstrumentedNode() instanceof InstrumentationTestLanguage.ExpressionNode) {
                                    sb.append("+E");
                                }
                            }

                            @Override
                            public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                                if (context.getInstrumentedNode() instanceof InstrumentationTestLanguage.StatementNode) {
                                    sb.append("-S");
                                } else if (context.getInstrumentedNode() instanceof InstrumentationTestLanguage.ExpressionNode) {
                                    sb.append("-E");
                                }
                            }

                            @Override
                            public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                                sb.append("+X");
                            }
                        });
        context.eval(source);
        // Each materialized node, which itself is a statement, has an extra statement. If the
        // materialized node has an expression as a child and the children of that expression
        // consist of only one expression, then the nested expression is connected as a child
        // directly to the materialized node in place of its parent and for each expression which is
        // ommited this way, one child expression is added to the extra statement node
        assertEquals("+S+S-S+S+S+E-E-S+E-E-S-S", sb.toString());
    }
}
