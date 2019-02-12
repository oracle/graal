/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.tools.lsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;
import org.graalvm.tools.lsp.api.ContextAwareExecutor;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.graalvm.tools.lsp.instrument.LSPInstrument;
import org.graalvm.tools.lsp.api.interop.LSPMessage;
import org.graalvm.tools.lsp.interop.ObjectStructures;
import org.graalvm.tools.lsp.interop.ObjectStructures.MessageNodes;
import org.graalvm.tools.lsp.server.utils.EvaluationResult;
import org.graalvm.tools.lsp.server.utils.InteropUtils;
import org.graalvm.tools.lsp.server.utils.SourceUtils;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;

import com.oracle.truffle.api.TruffleLogger;
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
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class SignatureHelpRequestHandler extends AbstractRequestHandler {
    private static final TruffleLogger LOG = TruffleLogger.getLogger(LSPInstrument.ID, SignatureHelpRequestHandler.class);

    private final MessageNodes messageNodes;
    private final Node nodeGetSignature = LSPMessage.GET_SIGNATURE.createNode();
    private final Node nodeInvoke = Message.INVOKE.createNode();
    private final SourceCodeEvaluator sourceCodeEvaluator;

    public SignatureHelpRequestHandler(Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor contextAwareExecutor, SourceCodeEvaluator sourceCodeEvaluator, MessageNodes messageNodes) {
        super(env, surrogateMap, contextAwareExecutor);
        this.messageNodes = messageNodes;
        this.sourceCodeEvaluator = sourceCodeEvaluator;
    }

    public SignatureHelp signatureHelpWithEnteredContext(URI uri, int line, int originalCharacter) throws DiagnosticsNotification {
        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        if (!isSignatureHelpTriggerCharOfLanguage(surrogate, line, originalCharacter)) {
            return new SignatureHelp();
        }
        InstrumentableNode nodeAtCaret = findNodeAtCaret(surrogate, line, originalCharacter, StandardTags.CallTag.class);
        if (nodeAtCaret != null) {
            SourceSection signatureSection = ((Node) nodeAtCaret).getSourceSection();
            SourceSectionFilter.Builder builder = SourceCodeEvaluator.createSourceSectionFilter(surrogate.getUri(), signatureSection);
            SourceSectionFilter eventFilter = builder.tagIs(StandardTags.CallTag.class).build();
            SourceSectionFilter inputFilter = SourceSectionFilter.ANY;
            EvaluationResult evalResult = sourceCodeEvaluator.runToSectionAndEval(surrogate, signatureSection, eventFilter, inputFilter);
            if (evalResult.isEvaluationDone() && !evalResult.isError()) {
                Object result = evalResult.getResult();
                if (result instanceof TruffleObject) {
                    try {
                        Object signature = ForeignAccess.send(nodeGetSignature, (TruffleObject) result);
                        if (signature == null) {
                            return new SignatureHelp();
                        }
                        String formattedSignature = ForeignAccess.sendInvoke(nodeInvoke, (TruffleObject) signature, "format").toString();
                        List<Object> params = ObjectStructures.asList((TruffleObject) signature, messageNodes);
                        SignatureInformation info = new SignatureInformation(formattedSignature);
                        List<ParameterInformation> paramInfos = new ArrayList<>();
                        for (Object param : params) {
                            if (param instanceof TruffleObject) {
                                Object formattedParam = ForeignAccess.sendInvoke(nodeInvoke, (TruffleObject) param, "format");
                                paramInfos.add(new ParameterInformation(formattedParam.toString()));
                            }
                        }
                        info.setParameters(paramInfos);
                        Object nodeObject = nodeAtCaret.getNodeObject();
                        Integer numberOfArguments = InteropUtils.getNumberOfArguments(nodeObject, messageNodes);

                        return new SignatureHelp(Arrays.asList(info), 0, numberOfArguments != null ? numberOfArguments - 1 : 0);
                    } catch (UnsupportedMessageException | UnsupportedTypeException e) {
                        LOG.log(Level.FINEST, "GET_SIGNATURE message not supported for TruffleObject: {0}", result);
                    } catch (InteropException e) {
                        e.printStackTrace(err);
                    }
                }
            }
        }
        return new SignatureHelp();
    }

    private boolean isSignatureHelpTriggerCharOfLanguage(TextDocumentSurrogate surrogate, int line, int charOffset) {
        Source source = surrogate.getSource();
        CharSequence characters = source.getCharacters(SourceUtils.zeroBasedLineToOneBasedLine(line, source));
        int triggerCharOffset = charOffset - 1;
        char signatureTirggerChar = characters.charAt(triggerCharOffset);

        List<String> signatureHelpTriggerCharacters = env.getSignatureHelpTriggerCharacters(surrogate.getLanguageInfo());
        return signatureHelpTriggerCharacters.contains(String.valueOf(signatureTirggerChar));
    }

    public List<String> getSignatureHelpTriggerCharactersWithEnteredContext() {
        //@formatter:off
        return env.getLanguages().values().stream()
                        .filter(lang -> !lang.isInternal())
                        .flatMap(info -> env.getSignatureHelpTriggerCharacters(info).stream())
                        .distinct()
                        .collect(Collectors.toList());
        //@formatter:on
    }
}
