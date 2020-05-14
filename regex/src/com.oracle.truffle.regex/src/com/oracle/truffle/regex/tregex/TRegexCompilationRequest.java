/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.regex.tregex;

import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.regex.CompiledRegexObject;
import com.oracle.truffle.regex.RegexExecRootNode;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.analysis.RegexUnifier;
import com.oracle.truffle.regex.dead.DeadRegexExecRootNode;
import com.oracle.truffle.regex.literal.LiteralRegexEngine;
import com.oracle.truffle.regex.literal.LiteralRegexExecRootNode;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAGenerator;
import com.oracle.truffle.regex.tregex.nfa.NFATraceFinderGenerator;
import com.oracle.truffle.regex.tregex.nfa.PureNFA;
import com.oracle.truffle.regex.tregex.nfa.PureNFAGenerator;
import com.oracle.truffle.regex.tregex.nfa.PureNFAMap;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecRootNode;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexDFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexDFAExecutorProperties;
import com.oracle.truffle.regex.tregex.nodes.nfa.TRegexBacktrackingNFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.nfa.TRegexLiteralLookAroundExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.nfa.TRegexNFAExecutorNode;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.RegexProperties;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.ASTLaTexExportVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.PreCalcResultVisitor;
import com.oracle.truffle.regex.tregex.util.DFAExport;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.Loggers;
import com.oracle.truffle.regex.tregex.util.NFAExport;
import com.oracle.truffle.regex.tregex.util.json.Json;

/**
 * This class is responsible for compiling a single regex pattern. The compilation process is
 * designed to be single-threaded, but multiple {@link TRegexCompilationRequest}s can be compiled in
 * parallel.
 */
public final class TRegexCompilationRequest {

    private final DebugUtil.Timer timer = shouldLogPhases() ? new DebugUtil.Timer() : null;

    private final TRegexCompiler tRegexCompiler;

    private final RegexSource source;
    private RegexAST ast = null;
    private PureNFAMap pureNFA = null;
    private NFA nfa = null;
    private NFA traceFinderNFA = null;
    private TRegexExecRootNode root = null;
    private TRegexDFAExecutorNode executorNodeForward = null;
    private TRegexDFAExecutorNode executorNodeBackward = null;
    private TRegexDFAExecutorNode executorNodeCaptureGroups = null;
    private final CompilationBuffer compilationBuffer;

    TRegexCompilationRequest(TRegexCompiler tRegexCompiler, RegexSource source) {
        this.tRegexCompiler = tRegexCompiler;
        this.source = source;
        this.compilationBuffer = new CompilationBuffer(source.getEncoding());
        assert source.getEncoding() != null;
    }

    TRegexCompilationRequest(TRegexCompiler tRegexCompiler, NFA nfa) {
        this.tRegexCompiler = tRegexCompiler;
        this.source = nfa.getAst().getSource();
        this.ast = nfa.getAst();
        this.nfa = nfa;
        this.compilationBuffer = new CompilationBuffer(nfa.getAst().getEncoding());
        assert source.getEncoding() != null;
    }

    public TRegexExecRootNode getRoot() {
        return root;
    }

    @TruffleBoundary
    CompiledRegexObject compile() {
        try {
            RegexExecRootNode compiledRegex = compileInternal();
            logAutomatonSizes(compiledRegex);
            return new CompiledRegexObject(tRegexCompiler.getLanguage(), compiledRegex);
        } catch (UnsupportedRegexException e) {
            logAutomatonSizes(null);
            e.setReason("TRegex: " + e.getReason());
            e.setRegex(source);
            throw e;
        }
    }

    @TruffleBoundary
    private RegexExecRootNode compileInternal() {
        Loggers.LOG_TREGEX_COMPILATIONS.finer(() -> String.format("TRegex compiling %s\n%s", DebugUtil.jsStringEscape(source.toString()), new RegexUnifier(source).getUnifiedPattern()));
        RegexParser regexParser = createParser();
        phaseStart("Parser");
        try {
            ast = regexParser.parse();
            regexParser.prepareForDFA();
        } finally {
            phaseEnd("Parser");
        }
        debugAST();
        if (ast.getRoot().isDead()) {
            return new DeadRegexExecRootNode(tRegexCompiler.getLanguage(), source);
        }
        LiteralRegexExecRootNode literal = LiteralRegexEngine.createNode(tRegexCompiler.getLanguage(), ast);
        if (literal != null) {
            return literal;
        }
        if (canTransformToDFA(ast)) {
            try {
                createNFA();
                if (nfa.isDead()) {
                    return new DeadRegexExecRootNode(tRegexCompiler.getLanguage(), source);
                }
                return new TRegexExecRootNode(tRegexCompiler, ast, new TRegexNFAExecutorNode(nfa));
            } catch (UnsupportedRegexException e) {
                // fall back to backtracking executor
            }
        }
        return new TRegexExecRootNode(tRegexCompiler, ast, compileBacktrackingExecutor());
    }

    public TRegexBacktrackingNFAExecutorNode compileBacktrackingExecutor() {
        assert ast != null;
        pureNFA = PureNFAGenerator.mapToNFA(ast);
        debugPureNFA();
        TRegexExecutorNode[] lookAroundExecutors = pureNFA.getLookArounds().size() == 0 ? TRegexBacktrackingNFAExecutorNode.NO_LOOK_AROUND_EXECUTORS
                        : new TRegexExecutorNode[pureNFA.getLookArounds().size()];
        for (int i = 0; i < pureNFA.getLookArounds().size(); i++) {
            PureNFA lookAround = pureNFA.getLookArounds().get(i);
            if (pureNFA.getASTSubtree(lookAround).asLookAroundAssertion().isLiteral()) {
                lookAroundExecutors[i] = new TRegexLiteralLookAroundExecutorNode(pureNFA.getASTSubtree(lookAround).asLookAroundAssertion(), compilationBuffer);
            } else {
                lookAroundExecutors[i] = new TRegexBacktrackingNFAExecutorNode(pureNFA, lookAround, lookAroundExecutors, compilationBuffer);
            }
        }
        return new TRegexBacktrackingNFAExecutorNode(pureNFA, pureNFA.getRoot(), lookAroundExecutors, compilationBuffer);
    }

    @TruffleBoundary
    TRegexExecRootNode.LazyCaptureGroupRegexSearchNode compileLazyDFAExecutor(TRegexExecRootNode rootNode, boolean allowSimpleCG) {
        assert ast != null;
        assert nfa != null;
        this.root = rootNode;
        RegexProperties properties = ast.getProperties();
        PreCalculatedResultFactory[] preCalculatedResults = null;
        if (!(properties.hasAlternations() || properties.hasLookAroundAssertions()) && properties.isFixedCodePointWidth()) {
            preCalculatedResults = new PreCalculatedResultFactory[]{PreCalcResultVisitor.createResultFactory(ast)};
        }
        if (allowSimpleCG && preCalculatedResults == null && TRegexOptions.TRegexEnableTraceFinder && !ast.getRoot().hasLoops() && properties.isFixedCodePointWidth()) {
            try {
                phaseStart("TraceFinder NFA");
                traceFinderNFA = NFATraceFinderGenerator.generateTraceFinder(nfa);
                preCalculatedResults = traceFinderNFA.getPreCalculatedResults();
                phaseEnd("TraceFinder NFA");
                debugTraceFinder();
            } catch (UnsupportedRegexException e) {
                phaseEnd("TraceFinder NFA Bailout");
                Loggers.LOG_BAILOUT_MESSAGES.fine(() -> "TraceFinder: " + e.getReason() + ": " + source);
                // handle with capture group aware DFA, bailout will always happen before
                // assigning preCalculatedResults
            }
        }
        executorNodeForward = createDFAExecutor(nfa, true, true, false, allowSimpleCG && preCalculatedResults == null && !(ast.getRoot().startsWithCaret() && !properties.hasCaptureGroups()));
        final boolean createCaptureGroupTracker = !executorNodeForward.isSimpleCG() && (properties.hasCaptureGroups() || properties.hasLookAroundAssertions()) &&
                        preCalculatedResults == null;
        if (createCaptureGroupTracker) {
            executorNodeCaptureGroups = createDFAExecutor(nfa, true, false, true, false);
        }
        if (preCalculatedResults != null && preCalculatedResults.length > 1) {
            executorNodeBackward = createDFAExecutor(traceFinderNFA, false, false, false, false);
        } else if (!executorNodeForward.isAnchored() && !executorNodeForward.isSimpleCG() && (preCalculatedResults == null || !nfa.hasReverseUnAnchoredEntry())) {
            executorNodeBackward = createDFAExecutor(nfa, false, false, false, allowSimpleCG && !(ast.getRoot().endsWithDollar() && !properties.hasCaptureGroups()));
        }
        logAutomatonSizes(rootNode);
        return new TRegexExecRootNode.LazyCaptureGroupRegexSearchNode(
                        tRegexCompiler.getLanguage(), source, ast.getFlags(), preCalculatedResults,
                        rootNode.createEntryNode(executorNodeForward),
                        rootNode.createEntryNode(executorNodeBackward),
                        rootNode.createEntryNode(executorNodeCaptureGroups),
                        rootNode);
    }

    @TruffleBoundary
    TRegexDFAExecutorNode compileEagerDFAExecutor() {
        createAST();
        RegexProperties properties = ast.getProperties();
        assert canTransformToDFA(ast);
        assert properties.hasCaptureGroups() || properties.hasLookAroundAssertions();
        assert !ast.getRoot().isDead();
        createNFA();
        return createDFAExecutor(nfa, true, true, true, false);
    }

    private static boolean canTransformToDFA(RegexAST ast) throws UnsupportedRegexException {
        RegexProperties p = ast.getProperties();
        return ast.getNumberOfNodes() <= TRegexOptions.TRegexMaxParseTreeSizeForDFA &&
                        ast.getNumberOfCaptureGroups() <= TRegexOptions.TRegexMaxNumberOfCaptureGroupsForDFA &&
                        !(p.hasBackReferences() ||
                                        p.hasLargeCountedRepetitions() ||
                                        p.hasNegativeLookAheadAssertions() ||
                                        p.hasNonLiteralLookBehindAssertions() ||
                                        p.hasNegativeLookBehindAssertions() ||
                                        ast.getRoot().hasQuantifiers());
    }

    private void createAST() {
        RegexParser regexParser = createParser();
        phaseStart("Parser");
        try {
            ast = regexParser.parse();
            regexParser.prepareForDFA();
        } finally {
            phaseEnd("Parser");
        }
        debugAST();
    }

    private RegexParser createParser() {
        return new RegexParser(source, RegexFlags.parseFlags(source.getFlags()), tRegexCompiler.getOptions(), compilationBuffer);
    }

    private void createNFA() {
        phaseStart("NFA");
        try {
            nfa = NFAGenerator.createNFA(ast, compilationBuffer);
        } finally {
            phaseEnd("NFA");
        }
        debugNFA();
    }

    private TRegexDFAExecutorNode createDFAExecutor(NFA nfaArg, boolean forward, boolean searching, boolean genericCG, boolean allowSimpleCG) {
        return createDFAExecutor(nfaArg, new TRegexDFAExecutorProperties(forward, searching, genericCG, allowSimpleCG,
                        tRegexCompiler.getOptions().isRegressionTestMode(), nfaArg.getAst().getRoot().getMinPath()), null);
    }

    public TRegexDFAExecutorNode createDFAExecutor(NFA nfaArg, TRegexDFAExecutorProperties props, String debugDumpName) {
        DFAGenerator dfa = new DFAGenerator(this, nfaArg, props, compilationBuffer, tRegexCompiler.getOptions());
        phaseStart(dfa.getDebugDumpName(debugDumpName) + " DFA");
        TRegexDFAExecutorNode executorNode;
        try {
            dfa.calcDFA();
            executorNode = dfa.createDFAExecutor();
        } finally {
            phaseEnd(dfa.getDebugDumpName(debugDumpName) + " DFA");
        }
        debugDFA(dfa, debugDumpName);
        return executorNode;
    }

    private void debugAST() {
        if (tRegexCompiler.getOptions().isDumpAutomata()) {
            Env env = RegexLanguage.getCurrentContext().getEnv();
            TruffleFile file = env.getPublicTruffleFile("./ast.tex");
            ASTLaTexExportVisitor.exportLatex(ast, file);
            file = env.getPublicTruffleFile("ast.json");
            ast.getWrappedRoot().toJson().dump(file);
        }
    }

    private void debugPureNFA() {
        if (tRegexCompiler.getOptions().isDumpAutomata()) {
            Env env = RegexLanguage.getCurrentContext().getEnv();
            TruffleFile file = env.getPublicTruffleFile("pure_nfa.json");
            Json.obj(Json.prop("dfa", Json.obj(
                            Json.prop("pattern", source.toString()),
                            Json.prop("pureNfa", pureNFA.toJson())))).dump(file);
        }
    }

    private void debugNFA() {
        if (tRegexCompiler.getOptions().isDumpAutomata()) {
            Env env = RegexLanguage.getCurrentContext().getEnv();
            TruffleFile file = env.getPublicTruffleFile("./nfa.gv");
            NFAExport.exportDot(nfa, file, true, false);
            file = env.getPublicTruffleFile("./nfa.tex");
            NFAExport.exportLaTex(nfa, file, false, true);
            file = env.getPublicTruffleFile("./nfa_reverse.gv");
            NFAExport.exportDotReverse(nfa, file, true, false);
            file = env.getPublicTruffleFile("nfa.json");
            Json.obj(Json.prop("dfa", Json.obj(Json.prop("pattern", source.toString()), Json.prop("nfa", nfa.toJson(true))))).dump(file);
        }
    }

    private void debugTraceFinder() {
        if (tRegexCompiler.getOptions().isDumpAutomata()) {
            Env env = RegexLanguage.getCurrentContext().getEnv();
            TruffleFile file = env.getPublicTruffleFile("./trace_finder.gv");
            NFAExport.exportDotReverse(traceFinderNFA, file, true, false);
            file = env.getPublicTruffleFile("nfa_trace_finder.json");
            traceFinderNFA.toJson().dump(file);
        }
    }

    private void debugDFA(DFAGenerator dfa, String debugDumpName) {
        if (tRegexCompiler.getOptions().isDumpAutomata()) {
            Env env = RegexLanguage.getCurrentContext().getEnv();
            TruffleFile file = env.getPublicTruffleFile("dfa_" + dfa.getDebugDumpName(debugDumpName) + ".gv");
            DFAExport.exportDot(dfa, file, false);
            file = env.getPublicTruffleFile("dfa_" + dfa.getDebugDumpName(debugDumpName) + ".json");
            Json.obj(Json.prop("dfa", dfa.toJson())).dump(file);
        }
    }

    private static boolean shouldLogPhases() {
        return Loggers.LOG_PHASES.isLoggable(Level.FINER);
    }

    private void phaseStart(String phase) {
        if (shouldLogPhases()) {
            Loggers.LOG_PHASES.finer(phase + " Start");
            timer.start();
        }
    }

    private void phaseEnd(String phase) {
        if (shouldLogPhases()) {
            Loggers.LOG_PHASES.finer(phase + " End, elapsed: " + timer.elapsedToString());
        }
    }

    private void logAutomatonSizes(RegexExecRootNode result) {
        Loggers.LOG_AUTOMATON_SIZES.finer(() -> Json.obj(
                        Json.prop("pattern", source.getPattern().length() > 200 ? source.getPattern().substring(0, 200) + "..." : source.getPattern()),
                        Json.prop("flags", source.getFlags()),
                        Json.prop("props", ast == null ? new RegexProperties() : ast.getProperties()),
                        Json.prop("astNodes", ast == null ? 0 : ast.getNumberOfNodes()),
                        Json.prop("pureNfaStates", pureNFA == null ? 0 : pureNFA.getRoot().getNumberOfStates()),
                        Json.prop("nfaStates", nfa == null ? 0 : nfa.getNumberOfStates()),
                        Json.prop("nfaTransitions", nfa == null ? 0 : nfa.getNumberOfTransitions()),
                        Json.prop("dfaStatesFwd", executorNodeForward == null ? 0 : executorNodeForward.getNumberOfStates()),
                        Json.prop("dfaStatesBck", executorNodeBackward == null ? 0 : executorNodeBackward.getNumberOfStates()),
                        Json.prop("dfaStatesCG", executorNodeCaptureGroups == null ? 0 : executorNodeCaptureGroups.getNumberOfStates()),
                        Json.prop("traceFinder", traceFinderNFA != null),
                        Json.prop("compilerResult", compilerResultToString(result))).toString() + ",");
    }

    private static String compilerResultToString(RegexExecRootNode result) {
        if (result instanceof TRegexExecRootNode) {
            return "tregex";
        } else if (result instanceof LiteralRegexExecRootNode) {
            return "literal";
        } else if (result instanceof DeadRegexExecRootNode) {
            return "dead";
        } else {
            return "bailout";
        }
    }
}
