/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.graalvm.tools.api.lsp.LSPLibrary;
import org.graalvm.tools.lsp.server.ContextAwareExecutor;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.graalvm.tools.lsp.server.LanguageTriggerCharacters;
import org.graalvm.tools.lsp.server.types.ParameterInformation;
import org.graalvm.tools.lsp.server.types.SignatureHelp;
import org.graalvm.tools.lsp.server.types.SignatureInformation;
import org.graalvm.tools.lsp.server.utils.EvaluationResult;
import org.graalvm.tools.lsp.server.utils.InteropUtils;
import org.graalvm.tools.lsp.server.utils.SourceUtils;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class SignatureHelpRequestHandler extends AbstractRequestHandler {

    private static final String PROP_DOCUMENTATION = "documentation";
    private static final String PROP_PARAMETERS = "parameters";
    private static final String PROP_LABEL = "label";
    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();
    private static final LSPLibrary LSP_INTEROP = LSPLibrary.getFactory().getUncached();

    private final SourceCodeEvaluator sourceCodeEvaluator;
    private final CompletionRequestHandler completionHandler;
    private final LanguageTriggerCharacters signatureTriggerCharacters;

    public SignatureHelpRequestHandler(Env envMain, Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor contextAwareExecutor, SourceCodeEvaluator sourceCodeEvaluator,
                    CompletionRequestHandler completionHandler, LanguageTriggerCharacters signatureTriggerCharacters) {
        super(envMain, env, surrogateMap, contextAwareExecutor);
        this.sourceCodeEvaluator = sourceCodeEvaluator;
        this.completionHandler = completionHandler;
        this.signatureTriggerCharacters = signatureTriggerCharacters;
    }

    public SignatureHelp signatureHelpWithEnteredContext(URI uri, int line, int originalCharacter) throws DiagnosticsNotification {
        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        if (isSignatureHelpTriggerCharOfLanguage(surrogate, line, originalCharacter)) {
            InstrumentableNode nodeAtCaret = findNodeAtCaret(surrogate, line, originalCharacter, StandardTags.CallTag.class);
            if (nodeAtCaret != null) {
                SourceSection signatureSection = ((Node) nodeAtCaret).getSourceSection();
                SourceSectionFilter.Builder builder = SourceCodeEvaluator.createSourceSectionFilter(surrogate.getUri(), signatureSection);
                SourceSectionFilter eventFilter = builder.tagIs(StandardTags.CallTag.class).build();
                SourceSectionFilter inputFilter = SourceSectionFilter.ANY;
                EvaluationResult evalResult = sourceCodeEvaluator.runToSectionAndEval(surrogate, signatureSection, eventFilter, inputFilter);
                // TODO: Are we asking for the signature on the correct object?
                if (evalResult.isEvaluationDone() && !evalResult.isError()) {
                    Object result = evalResult.getResult();
                    if (INTEROP.accepts(result) && INTEROP.isExecutable(result)) {
                        try {
                            Object signature = LSP_INTEROP.getSignature(result);
                            LanguageInfo langInfo = surrogate.getLanguageInfo();
                            String label = INTEROP.asString(INTEROP.toDisplayString(env.getLanguageView(langInfo, signature)));
                            SignatureInformation info = SignatureInformation.create(label, null);
                            if (signature instanceof TruffleObject) {
                                if (INTEROP.isMemberReadable(signature, PROP_DOCUMENTATION)) {
                                    Object doc = INTEROP.readMember(signature, PROP_DOCUMENTATION);
                                    Object documentation = completionHandler.getDocumentation(doc, langInfo);
                                    if (documentation != null) {
                                        info.setDocumentation(documentation);
                                    }
                                }
                                if (INTEROP.isMemberReadable(signature, PROP_PARAMETERS)) {
                                    Object paramsObject = INTEROP.readMember(signature, PROP_PARAMETERS);
                                    if (paramsObject instanceof TruffleObject && INTEROP.hasArrayElements(paramsObject)) {
                                        long size = INTEROP.getArraySize(paramsObject);
                                        List<ParameterInformation> paramInfos = new ArrayList<>((int) size);
                                        for (long i = 0; i < size; i++) {
                                            if (!INTEROP.isArrayElementReadable(paramsObject, i)) {
                                                continue;
                                            }
                                            Object param = INTEROP.readArrayElement(paramsObject, i);
                                            if (param instanceof TruffleObject) {
                                                ParameterInformation paramInfo = getParameterInformation(param, label, langInfo);
                                                if (paramInfo != null) {
                                                    paramInfos.add(paramInfo);
                                                }
                                            }
                                        }
                                        info.setParameters(paramInfos);
                                    }
                                }
                            }
                            Object nodeObject = nodeAtCaret.getNodeObject();
                            Integer numberOfArguments = InteropUtils.getNumberOfArguments(nodeObject, logger);
                            // TODO: Support multiple signatures, the active one and find the active
                            // parameter
                            return SignatureHelp.create(Arrays.asList(info), 0, numberOfArguments != null ? numberOfArguments - 1 : 0);
                        } catch (UnsupportedMessageException e) {
                            logger.log(Level.FINEST, "GET_SIGNATURE message not supported for TruffleObject: {0}", result);
                        } catch (InteropException e) {
                            e.printStackTrace(err);
                        }
                    }
                }
            }
        }
        return SignatureHelp.create(Collections.emptyList(), null, null);
    }

    private ParameterInformation getParameterInformation(Object param, String label, LanguageInfo langInfo) throws UnsupportedMessageException, UnknownIdentifierException, InvalidArrayIndexException {
        Object paramLabelObject = INTEROP.isMemberReadable(param, PROP_LABEL) ? INTEROP.readMember(param, PROP_LABEL) : null;
        String paramLabel;
        if (paramLabelObject instanceof String) {
            paramLabel = (String) paramLabelObject;
        } else if (paramLabelObject instanceof TruffleObject && INTEROP.hasArrayElements(paramLabelObject)) {
            long size = INTEROP.getArraySize(paramLabelObject);
            if (size < 2) {
                logger.fine("ERROR: Insufficient number of label indexes: " + size + " from " + paramLabelObject);
                return null;
            }
            Object i1Obj = INTEROP.readArrayElement(paramLabelObject, 0);
            Object i2Obj = INTEROP.readArrayElement(paramLabelObject, 1);
            if (!INTEROP.fitsInInt(i1Obj) || !INTEROP.fitsInInt(i2Obj)) {
                logger.fine("ERROR: Label indexes of " + paramLabelObject + " are not numbers: " + i1Obj + ", " + i2Obj);
                return null;
            }
            paramLabel = label.substring(INTEROP.asInt(i1Obj), INTEROP.asInt(i2Obj));
        } else {
            logger.fine("ERROR: Unknown label object: " + paramLabelObject + " in " + param);
            return null;
        }
        ParameterInformation info = ParameterInformation.create(paramLabel, null);
        Object doc = INTEROP.isMemberReadable(param, PROP_DOCUMENTATION) ? INTEROP.readMember(param, PROP_DOCUMENTATION) : null;
        Object documentation = completionHandler.getDocumentation(doc, langInfo);
        if (documentation != null) {
            info.setDocumentation(documentation);
        }
        return info;
    }

    private boolean isSignatureHelpTriggerCharOfLanguage(TextDocumentSurrogate surrogate, int line, int charOffset) {
        Source source = surrogate.getSource();
        CharSequence characters = source.getCharacters(SourceUtils.zeroBasedLineToOneBasedLine(line, source));
        int triggerCharOffset = charOffset - 1;
        char signatureTirggerChar = characters.charAt(triggerCharOffset);

        List<String> signatureHelpTriggerCharacters = signatureTriggerCharacters.getTriggerCharacters(surrogate.getLanguageId());
        return signatureHelpTriggerCharacters.contains(String.valueOf(signatureTirggerChar));
    }

}
