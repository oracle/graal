package de.hpi.swa.trufflelsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
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
import de.hpi.swa.trufflelsp.server.utils.SourceUtils.SourceFix;
import de.hpi.swa.trufflelsp.server.utils.SourceWrapper;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;

public class SignatureHelpRequestHandler extends AbstractRequestHandler {

    private static final Node GET_SIGNATURE = GetSignature.INSTANCE.createNode();
    private final SourceCodeEvaluator sourceCodeEvaluator;

    public SignatureHelpRequestHandler(Env env, Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate, ContextAwareExecutorWrapper contextAwareExecutor, SourceCodeEvaluator sourceCodeEvaluator) {
        super(env, uri2TextDocumentSurrogate, contextAwareExecutor);
        this.sourceCodeEvaluator = sourceCodeEvaluator;
    }

    public SignatureHelp signatureHelpWithEnteredContext(URI uri, int line, int originalCharacter) throws DiagnosticsNotification {
        System.out.println("signature " + line + " " + originalCharacter);

        TextDocumentSurrogate surrogate = uri2TextDocumentSurrogate.get(uri);
        SourceFix sourceFix = SourceUtils.removeLastTextInsertion(surrogate, originalCharacter);

        TextDocumentSurrogate revertedSurrogate = surrogate.copy();
        // TODO(ds) Should we reset coverage data etc? Or adjust the SourceLocations?
        revertedSurrogate.setEditorText(sourceFix.text);
        SourceWrapper sourceWrapper = revertedSurrogate.prepareParsing();
        CallTarget callTarget;
        try {
            callTarget = env.parse(sourceWrapper.getSource());
        } catch (Exception e) {
            err.println("Parsing a reverted source caused an exception: " + e.getClass().getSimpleName() + " > " + e.getLocalizedMessage());
            return new SignatureHelp();
        }
        revertedSurrogate.notifyParsingSuccessful(callTarget);

        uri2TextDocumentSurrogate.put(uri, revertedSurrogate);
        try {
            Source source = sourceWrapper.getSource();
            if (SourceUtils.isLineValid(line, source)) {
                NearestNodeHolder nearestNodeHolder = NearestSectionsFinder.findNearestNode(source, line, sourceFix.characterIdx, env);
                Node nearestNode = nearestNodeHolder.getNearestNode();

                EvaluationResult evalResult = sourceCodeEvaluator.runToSectionAndEval(revertedSurrogate, nearestNode);
                if (evalResult.isEvaluationDone() && !evalResult.isError()) {
                    Object result = evalResult.getResult();
                    if (result instanceof TruffleObject) {
                        try {
                            Object signature = ForeignAccess.send(GET_SIGNATURE, (TruffleObject) result, nearestNode.getSourceSection().getCharacters().toString());
                            List<Object> nameAndParams = ObjectStructures.asList(new ObjectStructures.MessageNodes(), (TruffleObject) signature);
                            String formattedSignature = nameAndParams.stream().reduce("", (a, b) -> a.toString() + b.toString()).toString();
                            SignatureInformation info = new SignatureInformation(formattedSignature);
                            List<ParameterInformation> paramInfos = new ArrayList<>();
                            for (int i = 1; i < nameAndParams.size(); i++) {
                                String param = nameAndParams.get(i).toString();
                                if (param.length() > 1) {
                                    paramInfos.add(new ParameterInformation(param));
                                }
                            }
                            info.setParameters(paramInfos);

                            return new SignatureHelp(Arrays.asList(info), 0, 0);
                        } catch (InteropException e) {
                            if (!(e instanceof UnsupportedMessageException)) {
                                e.printStackTrace(err);
                            }
                        }
                    }
                }
            }
        } finally {
            uri2TextDocumentSurrogate.put(uri, surrogate);
        }
        return new SignatureHelp();
    }
}
