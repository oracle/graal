package de.hpi.swa.trufflelsp;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

import de.hpi.swa.trufflelsp.exceptions.EvaluationResultException;
import de.hpi.swa.trufflelsp.exceptions.InlineParsingNotSupportedException;

final class InlineEvaluationEventFactory implements ExecutionEventNodeFactory {
    private final TruffleInstrument.Env env;
    private final String codeToEval;

    public InlineEvaluationEventFactory(TruffleInstrument.Env env, String codeToEval) {
        this.env = env;
        this.codeToEval = codeToEval;
    }

    public ExecutionEventNode create(final EventContext eventContext) {
        return new ExecutionEventNode() {
            @Override
            protected void onEnter(VirtualFrame frame) {
                final LanguageInfo info = eventContext.getInstrumentedNode().getRootNode().getLanguageInfo();
                final Source source = Source.newBuilder(codeToEval).name("eval in context").language(info.getId()).mimeType("content/unknown").cached(false).build();
                ExecutableNode fragment = env.parseInline(source, eventContext.getInstrumentedNode(), frame.materialize());
                if (fragment != null) {
                    insert(fragment);
                    Object result;
                    try {
                        result = fragment.execute(frame);
                    } catch (Exception e) {
                        CompilerDirectives.transferToInterpreter();
                        Object resultException = e;
                        if (e instanceof TruffleException) {
                            // We need to translate the source section of the error back to the
                            // original Source so that the error will be displayed correctly in the
                            // LSP client
                            resultException = new TruffleException() {

                                public Node getLocation() {
                                    return eventContext.getInstrumentedNode();
                                }

                                @Override
                                public String toString() {
                                    return e.getMessage();
                                }
                            };
                        }
                        throw new EvaluationResultException(resultException, true);
                    }
                    CompilerDirectives.transferToInterpreter();
                    throw new EvaluationResultException(result);
                } else {
                    System.out.println("Inline-parsing not supported. Assuming code snippet is a frame slot identifier...");
                    FrameSlot frameSlot = frame.getFrameDescriptor().getSlots().stream().filter(slot -> slot.getIdentifier().equals(codeToEval)).findFirst().orElseGet(() -> null);
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