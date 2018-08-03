package de.hpi.swa.trufflelsp;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;

import de.hpi.swa.trufflelsp.exceptions.EvaluationResultException;
import de.hpi.swa.trufflelsp.exceptions.InlineParsingNotSupportedException;

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
                final Source source = Source.newBuilder(code).name("eval in context").language(info.getId()).mimeType("content/unknown").cached(false).build();
                ExecutableNode fragment = env.parseInline(source, eventContext.getInstrumentedNode(), frame.materialize());
                if (fragment != null) {
                    insert(fragment);
                    Object result;
                    try {
                        result = fragment.execute(frame);
                    } catch (Exception e) {
                        CompilerDirectives.transferToInterpreter();
                        throw new EvaluationResultException(e, true);
                    }
                    CompilerDirectives.transferToInterpreter();
                    throw new EvaluationResultException(result);
                } else {
                    System.out.println("Inline-parsing not supported. Assuming code snippet is a frame slot identifier...");
                    FrameSlot frameSlot = frame.getFrameDescriptor().getSlots().stream().filter(slot -> slot.getIdentifier().equals(code)).findFirst().orElseGet(() -> null);
                    if (frameSlot != null) {
                        try {
                            throw new EvaluationResultException(frame.getObject(frameSlot));
                        } catch (FrameSlotTypeException e) {
                        }
                    }
                    throw new InlineParsingNotSupportedException();
                }
            }
        };
    }
}