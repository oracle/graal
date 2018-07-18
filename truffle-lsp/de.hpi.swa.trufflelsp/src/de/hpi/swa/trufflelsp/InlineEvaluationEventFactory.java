package de.hpi.swa.trufflelsp;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;

import de.hpi.swa.trufflelsp.exceptions.EvaluationResultException;

final class InlineEvaluationEventFactory implements ExecutionEventNodeFactory {
    private final TruffleInstrument.Env env;

    public InlineEvaluationEventFactory(TruffleInstrument.Env env) {
        this.env = env;
    }

    public ExecutionEventNode create(final EventContext eventContext) {
        return new ExecutionEventNode() {
            @Override
            protected void onEnter(VirtualFrame frame) {
                final LanguageInfo info = eventContext.getInstrumentedNode().getRootNode().getLanguageInfo();
                final String code = eventContext.getInstrumentedSourceSection().getCharacters().toString();
                final Source source = Source.newBuilder(code).name("eval in context").language(info.getId()).mimeType("content/unknown").build();
                ExecutableNode fragment = env.parseInline(source, eventContext.getInstrumentedNode(), frame.materialize());
                if (fragment != null) {
                    insert(fragment);
                    Object result;
                    try {
                        result = fragment.execute(frame);
                    } catch (Exception e) {
                        e.printStackTrace(); // TODO(ds)
                        CompilerDirectives.transferToInterpreter();
                        throw new EvaluationResultException(null, true);
                    }
                    CompilerDirectives.transferToInterpreter();
                    throw new EvaluationResultException(result);
                }
            }
        };
    }
}