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

import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.graalvm.tools.lsp.server.ContextAwareExecutor;
import org.graalvm.tools.lsp.server.utils.CoverageData;
import org.graalvm.tools.lsp.server.utils.NearestSectionsFinder;
import org.graalvm.tools.lsp.server.utils.SourcePredicateBuilder;
import org.graalvm.tools.lsp.server.utils.SourceUtils;
import org.graalvm.tools.lsp.server.utils.SourceWrapper;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;
import org.graalvm.tools.lsp.server.utils.NearestSectionsFinder.NearestSections;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public abstract class AbstractRequestHandler {

    protected final TruffleInstrument.Env env;
    protected final TextDocumentSurrogateMap surrogateMap;
    protected final PrintWriter err;
    protected final ContextAwareExecutor contextAwareExecutor;
    protected final TruffleLogger logger;

    AbstractRequestHandler(TruffleInstrument.Env mainEnv, TruffleInstrument.Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor contextAwareExecutor) {
        this.env = env;
        this.err = new PrintWriter(mainEnv.err(), true);
        this.surrogateMap = surrogateMap;
        this.contextAwareExecutor = contextAwareExecutor;
        this.logger = mainEnv.getLogger("");
    }

    public final InstrumentableNode findNodeAtCaret(TextDocumentSurrogate surrogate, int line, int character, Class<?>... tag) {
        if (surrogate != null) {
            SourceWrapper sourceWrapper = surrogate.getSourceWrapper();
            if (sourceWrapper.isParsingSuccessful()) {
                Source source = sourceWrapper.getSource();
                if (SourceUtils.isLineValid(line, source)) {
                    int oneBasedLineNumber = SourceUtils.zeroBasedLineToOneBasedLine(line, source);
                    int oneBasedColumn = SourceUtils.zeroBasedColumnToOneBasedColumn(line, oneBasedLineNumber, character, source);
                    NearestSections nearestSections = NearestSectionsFinder.findNearestSections(source, env, oneBasedLineNumber, oneBasedColumn, true, tag);
                    if (nearestSections.getNextSourceSection() != null) {
                        SourceSection nextNodeSection = nearestSections.getNextSourceSection();
                        if (nextNodeSection.getStartLine() == oneBasedLineNumber && nextNodeSection.getStartColumn() == oneBasedColumn) {
                            // nextNodeSection is directly before the caret, so we use that one
                            return nearestSections.getInstrumentableNextNode();
                        }
                    }
                    return nearestSections.getInstrumentableContainsNode();
                }
            }
        }
        return null;
    }

    protected final <T> T getFutureResultOrHandleExceptions(Future<T> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException && e instanceof TruffleException) {
                throw (RuntimeException) e.getCause();
            } else {
                e.printStackTrace(err);
            }
        } catch (InterruptedException e) {
        }
        return null;
    }

    protected final LinkedList<Scope> getScopesOuterToInner(TextDocumentSurrogate surrogate, InstrumentableNode node) {
        List<CoverageData> coverageData = surrogate.getCoverageData(((Node) node).getSourceSection());
        MaterializedFrame frame = null;
        if (coverageData != null) {
            CoverageData data = coverageData.stream().findFirst().orElse(null);
            if (data != null) {
                frame = data.getFrame();
            }
        }
        Iterable<Scope> scopesInnerToOuter = env.findLocalScopes((Node) node, frame);
        LinkedList<Scope> scopesOuterToInner = new LinkedList<>();
        for (Scope scope : scopesInnerToOuter) {
            scopesOuterToInner.addFirst(scope);
        }
        return scopesOuterToInner;
    }

    protected final SourcePredicateBuilder newDefaultSourcePredicateBuilder() {
        return SourcePredicateBuilder.newBuilder().excludeInternal(env.getOptions()).newestSource(surrogateMap);
    }

}
