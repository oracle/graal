package de.hpi.swa.trufflelsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.exceptions.DiagnosticsNotification;
import de.hpi.swa.trufflelsp.interop.GetSignature;
import de.hpi.swa.trufflelsp.interop.ObjectStructures;
import de.hpi.swa.trufflelsp.server.utils.EvaluationResult;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
import de.hpi.swa.trufflelsp.server.utils.SurrogateMap;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;

public class SignatureHelpRequestHandler extends AbstractRequestHandler {

    private static final Node GET_SIGNATURE = GetSignature.INSTANCE.createNode();
    private static final Node INVOKE = Message.INVOKE.createNode();
    private final SourceCodeEvaluator sourceCodeEvaluator;

    public SignatureHelpRequestHandler(Env env, SurrogateMap surrogateMap, ContextAwareExecutorWrapper contextAwareExecutor, SourceCodeEvaluator sourceCodeEvaluator) {
        super(env, surrogateMap, contextAwareExecutor);
        this.sourceCodeEvaluator = sourceCodeEvaluator;
    }

    public SignatureHelp signatureHelpWithEnteredContext(URI uri, int line, int originalCharacter) throws DiagnosticsNotification {
        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        InstrumentableNode nodeAtCaret = findNodeAtCaret(surrogate, line, originalCharacter, StandardTags.CallTag.class);
        if (nodeAtCaret != null) {
            SourceSection signatureSection = ((Node) nodeAtCaret).getSourceSection();
            SourceSectionFilter.Builder builder = SourceUtils.createSourceSectionFilter(surrogate, signatureSection);
            SourceSectionFilter eventFilter = builder.tagIs(StandardTags.CallTag.class).build();
            SourceSectionFilter inputFilter = SourceSectionFilter.ANY;
            EvaluationResult evalResult = sourceCodeEvaluator.runToSectionAndEval(surrogate, signatureSection, eventFilter, inputFilter);
            if (evalResult.isEvaluationDone() && !evalResult.isError()) {
                Object result = evalResult.getResult();
                if (result instanceof TruffleObject) {
                    try {
                        Object signature = ForeignAccess.send(GET_SIGNATURE, (TruffleObject) result);
                        String formattedSignature = ForeignAccess.sendInvoke(INVOKE, (TruffleObject) signature, "format").toString();
                        List<Object> params = ObjectStructures.asList(new ObjectStructures.MessageNodes(), (TruffleObject) signature);
                        SignatureInformation info = new SignatureInformation(formattedSignature);
                        List<ParameterInformation> paramInfos = new ArrayList<>();
                        for (Object param : params) {
                            if (param instanceof TruffleObject) {
                                Object formattedParam = ForeignAccess.sendInvoke(INVOKE, (TruffleObject) param, "format");
                                paramInfos.add(new ParameterInformation(formattedParam.toString()));
                            }
                        }
                        info.setParameters(paramInfos);

                        return new SignatureHelp(Arrays.asList(info), 0, 0);
                    } catch (UnsupportedMessageException | UnsupportedTypeException e) {
                    } catch (InteropException e) {
                        e.printStackTrace(err);
                    }
                }
            }
        }
        return new SignatureHelp();
    }
}
