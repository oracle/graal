package de.hpi.swa.trufflelsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;

import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.exceptions.DiagnosticsNotification;
import de.hpi.swa.trufflelsp.interop.GetSignature;
import de.hpi.swa.trufflelsp.interop.ObjectStructures;
import de.hpi.swa.trufflelsp.server.utils.EvaluationResult;
import de.hpi.swa.trufflelsp.server.utils.NearestNodeHolder;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
import de.hpi.swa.trufflelsp.server.utils.SourceWrapper;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;

public class SignatureHelpRequestHandler extends AbstractRequestHandler {

    private static final Node GET_SIGNATURE = GetSignature.INSTANCE.createNode();
    private static final Node INVOKE = Message.createInvoke(0).createNode();
    private final SourceCodeEvaluator sourceCodeEvaluator;

    public SignatureHelpRequestHandler(Env env, Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate, ContextAwareExecutorWrapper contextAwareExecutor, SourceCodeEvaluator sourceCodeEvaluator) {
        super(env, uri2TextDocumentSurrogate, contextAwareExecutor);
        this.sourceCodeEvaluator = sourceCodeEvaluator;
    }

    public SignatureHelp signatureHelpWithEnteredContext(URI uri, int line, int originalCharacter) throws DiagnosticsNotification {
        TextDocumentSurrogate surrogate = uri2TextDocumentSurrogate.get(uri);
        SourceWrapper sourceWrapper = surrogate.getSourceWrapper();
        Source source = sourceWrapper.getSource();
        if (SourceUtils.isLineValid(line, source)) {
            NearestNodeHolder nearestNodeHolder = NearestSectionsFinder.findNearestNode(source, line, originalCharacter, env);
            Node nearestNode = nearestNodeHolder.getNearestNode();

            EvaluationResult evalResult = sourceCodeEvaluator.runToSectionAndEval(surrogate, nearestNode);
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
