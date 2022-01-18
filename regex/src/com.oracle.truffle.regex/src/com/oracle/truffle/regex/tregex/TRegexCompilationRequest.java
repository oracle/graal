/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.StringJoiner;
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.regex.RegexExecNode;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexLanguage.RegexContext;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.analysis.RegexUnifier;
import com.oracle.truffle.regex.dead.DeadRegexExecNode;
import com.oracle.truffle.regex.literal.LiteralRegexEngine;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAGenerator;
import com.oracle.truffle.regex.tregex.nfa.NFATraceFinderGenerator;
import com.oracle.truffle.regex.tregex.nfa.PureNFA;
import com.oracle.truffle.regex.tregex.nfa.PureNFAGenerator;
import com.oracle.truffle.regex.tregex.nfa.PureNFAMap;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecNode;
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

    private final RegexLanguage language;
    private final RegexSource source;
    private RegexAST ast = null;
    private PureNFAMap pureNFA = null;
    private NFA nfa = null;
    private NFA traceFinderNFA = null;
    private TRegexExecNode root = null;
    private TRegexDFAExecutorNode executorNodeForward = null;
    private TRegexDFAExecutorNode executorNodeBackward = null;
    private TRegexDFAExecutorNode executorNodeCaptureGroups = null;
    private final CompilationBuffer compilationBuffer;

    public TRegexCompilationRequest(RegexLanguage language, RegexSource source) {
        this.language = language;
        this.source = source;
        this.compilationBuffer = new CompilationBuffer(source.getEncoding());
        assert source.getEncoding() != null;
    }

    TRegexCompilationRequest(RegexLanguage language, NFA nfa) {
        this.language = language;
        this.source = nfa.getAst().getSource();
        this.ast = nfa.getAst();
        this.nfa = nfa;
        this.compilationBuffer = new CompilationBuffer(nfa.getAst().getEncoding());
        assert source.getEncoding() != null;
    }

    public TRegexExecNode getRoot() {
        return root;
    }

    public RegexAST getAst() {
        return ast;
    }

    @TruffleBoundary
    public RegexExecNode compile() {
        try {
            RegexExecNode compiledRegex = compileInternal();
            logAutomatonSizes(compiledRegex);
            return compiledRegex;
        } catch (UnsupportedRegexException e) {
            logAutomatonSizes(null);
            e.setReason("TRegex: " + e.getReason());
            e.setRegex(source);
            throw e;
        }
    }

    @TruffleBoundary
    private RegexExecNode compileInternal() {
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
            Loggers.LOG_MATCHING_STRATEGY.fine(() -> "regex cannot match, using dummy matcher");
            return new DeadRegexExecNode(language, source);
        }
        LiteralRegexExecNode literal = LiteralRegexEngine.createNode(language, ast);
        if (literal != null) {
            Loggers.LOG_MATCHING_STRATEGY.fine(() -> "using literal matcher " + literal.getClass().getSimpleName());
            return literal;
        }
        if (canTransformToDFA(ast)) {
            try {
                createNFA();
                if (nfa.isDead()) {
                    Loggers.LOG_MATCHING_STRATEGY.fine(() -> "regex cannot match, using dummy matcher");
                    return new DeadRegexExecNode(language, source);
                }
                return new TRegexExecNode(ast, new TRegexNFAExecutorNode(nfa, ast.getOptions().getFlavor().usesLastGroupResultField()));
            } catch (UnsupportedRegexException e) {
                // fall back to backtracking executor
                Loggers.LOG_MATCHING_STRATEGY.fine(() -> "NFA generator bailout: " + e.getReason() + ", using back-tracking matcher");
            }
        } else {
            Loggers.LOG_MATCHING_STRATEGY.fine(() -> "using back-tracking matcher, reason: " + canTransformToDFAFailureReason(ast));
        }
        return new TRegexExecNode(ast, compileBacktrackingExecutor());
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
                lookAroundExecutors[i] = new TRegexBacktrackingNFAExecutorNode(pureNFA, lookAround, lookAroundExecutors, false, compilationBuffer);
            }
        }
        return new TRegexBacktrackingNFAExecutorNode(pureNFA, pureNFA.getRoot(), lookAroundExecutors, ast.getOptions().isMustAdvance(), compilationBuffer);
    }

    @TruffleBoundary
    TRegexExecNode.LazyCaptureGroupRegexSearchNode compileLazyDFAExecutor(TRegexExecNode rootNode, boolean allowSimpleCG) {
        assert ast != null;
        assert nfa != null;
        this.root = rootNode;
        RegexProperties properties = ast.getProperties();
        PreCalculatedResultFactory[] preCalculatedResults = null;
        if (!(properties.hasAlternations() || properties.hasLookAroundAssertions()) && properties.isFixedCodePointWidth()) {
            preCalculatedResults = new PreCalculatedResultFactory[]{PreCalcResultVisitor.createResultFactory(ast)};
        }
        if (allowSimpleCG && preCalculatedResults == null && TRegexOptions.TRegexEnableTraceFinder && !ast.getRoot().hasLoops() && properties.isFixedCodePointWidth()) {
            if (nfa.isFixedCodePointWidth()) {
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
        }
        boolean traceFinder = preCalculatedResults != null;
        final boolean trackLastGroup = ast.getOptions().getFlavor().usesLastGroupResultField();
        executorNodeForward = createDFAExecutor(nfa, true, true, false, allowSimpleCG && !traceFinder && !(ast.getRoot().startsWithCaret() && !properties.hasCaptureGroups()), trackLastGroup);
        final boolean createCaptureGroupTracker = !executorNodeForward.isSimpleCG() && (properties.hasCaptureGroups() || properties.hasLookAroundAssertions()) && !traceFinder;
        if (createCaptureGroupTracker) {
            executorNodeCaptureGroups = createDFAExecutor(nfa, true, false, true, false, trackLastGroup);
        }
        if (traceFinder && preCalculatedResults.length > 1) {
            executorNodeBackward = createDFAExecutor(traceFinderNFA, false, false, false, false, false);
        } else if (!executorNodeForward.isAnchored() && !executorNodeForward.isSimpleCG() && (!traceFinder || !nfa.hasReverseUnAnchoredEntry())) {
            executorNodeBackward = createDFAExecutor(nfa, false, false, false, allowSimpleCG && !(ast.getRoot().endsWithDollar() && !properties.hasCaptureGroups()), trackLastGroup);
        }
        logAutomatonSizes(rootNode);
        return new TRegexExecNode.LazyCaptureGroupRegexSearchNode(
                        language, source, ast.getFlags(), preCalculatedResults,
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
        return createDFAExecutor(nfa, true, true, true, false, ast.getOptions().getFlavor().usesLastGroupResultField());
    }

    private static boolean canTransformToDFA(RegexAST ast) throws UnsupportedRegexException {
        RegexProperties p = ast.getProperties();
        boolean couldCalculateLastGroup = !ast.getOptions().getFlavor().usesLastGroupResultField() || !p.hasCaptureGroupsInLookAroundAssertions();
        return ast.getNumberOfNodes() <= TRegexOptions.TRegexMaxParseTreeSizeForDFA &&
                        ast.getNumberOfCaptureGroups() <= TRegexOptions.TRegexMaxNumberOfCaptureGroupsForDFA &&
                        !(ast.getRoot().hasBackReferences() ||
                                        p.hasLargeCountedRepetitions() ||
                                        p.hasNegativeLookAheadAssertions() ||
                                        p.hasNonLiteralLookBehindAssertions() ||
                                        p.hasNegativeLookBehindAssertions() ||
                                        ast.getRoot().hasQuantifiers()) &&
                        couldCalculateLastGroup;
    }

    @TruffleBoundary
    private static String canTransformToDFAFailureReason(RegexAST ast) throws UnsupportedRegexException {
        RegexProperties p = ast.getProperties();
        StringJoiner sb = new StringJoiner(", ");
        if (ast.getNumberOfNodes() > TRegexOptions.TRegexMaxParseTreeSizeForDFA) {
            sb.add(String.format("Parser tree has too many nodes: %d (threshold: %d)", ast.getNumberOfNodes(), TRegexOptions.TRegexMaxParseTreeSizeForDFA));
        }
        if (ast.getNumberOfCaptureGroups() > TRegexOptions.TRegexMaxNumberOfCaptureGroupsForDFA) {
            sb.add(String.format("regex has too many capture groups: %d (threshold: %d)", ast.getNumberOfCaptureGroups(), TRegexOptions.TRegexMaxNumberOfCaptureGroupsForDFA));
        }
        if (ast.getRoot().hasBackReferences()) {
            sb.add("regex has back-references");
        }
        if (p.hasLargeCountedRepetitions()) {
            sb.add(String.format("regex has large counted repetitions (threshold: %d for single CC, %d for groups)",
                            TRegexOptions.TRegexQuantifierUnrollThresholdSingleCC, TRegexOptions.TRegexQuantifierUnrollThresholdGroup));
        }
        if (p.hasNegativeLookAheadAssertions()) {
            sb.add("regex has negative look-ahead assertions");
        }
        if (p.hasNegativeLookBehindAssertions()) {
            sb.add("regex has negative look-behind assertions");
        }
        if (p.hasNonLiteralLookBehindAssertions()) {
            sb.add("regex has non-literal look-behind assertions");
        }
        if (ast.getRoot().hasQuantifiers()) {
            sb.add("could not unroll all quantifiers");
        }
        return sb.toString();
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
        return new RegexParser(language, source, RegexFlags.parseFlags(source), compilationBuffer);
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

    private TRegexDFAExecutorNode createDFAExecutor(NFA nfaArg, boolean forward, boolean searching, boolean genericCG, boolean allowSimpleCG, boolean trackLastGroup) {
        return createDFAExecutor(nfaArg, new TRegexDFAExecutorProperties(forward, searching, genericCG, allowSimpleCG,
                        source.getOptions().isRegressionTestMode(), trackLastGroup, nfaArg.getAst().getRoot().getMinPath()), null);
    }

    public TRegexDFAExecutorNode createDFAExecutor(NFA nfaArg, TRegexDFAExecutorProperties props, String debugDumpName) {
        DFAGenerator dfa = new DFAGenerator(this, nfaArg, props, compilationBuffer);
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
        if (source.getOptions().isDumpAutomata()) {
            Env env = RegexContext.get(null).getEnv();
            TruffleFile file = env.getPublicTruffleFile("./ast.tex");
            ASTLaTexExportVisitor.exportLatex(ast, file);
            file = env.getPublicTruffleFile("ast.json");
            ast.getWrappedRoot().toJson().dump(file);
        }
    }

    private void debugPureNFA() {
        if (source.getOptions().isDumpAutomata()) {
            Env env = RegexContext.get(null).getEnv();
            TruffleFile file = env.getPublicTruffleFile("pure_nfa.json");
            Json.obj(Json.prop("dfa", Json.obj(
                            Json.prop("pattern", source.toString()),
                            Json.prop("pureNfa", pureNFA.toJson())))).dump(file);
        }
    }

    private void debugNFA() {
        if (source.getOptions().isDumpAutomata()) {
            Env env = RegexContext.get(null).getEnv();
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
        if (source.getOptions().isDumpAutomata()) {
            Env env = RegexContext.get(null).getEnv();
            TruffleFile file = env.getPublicTruffleFile("./trace_finder.gv");
            NFAExport.exportDotReverse(traceFinderNFA, file, true, false);
            file = env.getPublicTruffleFile("nfa_trace_finder.json");
            traceFinderNFA.toJson().dump(file);
        }
    }

    private void debugDFA(DFAGenerator dfa, String debugDumpName) {
        if (source.getOptions().isDumpAutomata()) {
            Env env = RegexContext.get(null).getEnv();
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

    private void logAutomatonSizes(RegexExecNode result) {
        Loggers.LOG_AUTOMATON_SIZES.finer(() -> Json.obj(
                        Json.prop("pattern", source.getPattern().length() > 200 ? source.getPattern().substring(0, 200) + "..." : source.getPattern()),
                        Json.prop("flags", source.getFlags()),
                        Json.prop("props", ast == null ? new RegexProperties() : ast.getProperties()),
                        Json.prop("astNodes", ast == null ? 0 : ast.getNumberOfNodes()),
                        Json.prop("pureNfaStates", pureNFA == null ? 0 : pureNFA.getRoot().getNumberOfStates()),
                        Json.prop("nfaStates", nfa == null ? 0 : nfa.getNumberOfStates()),
                        Json.prop("nfaTransitions", nfa == null ? 0 : nfa.getNumberOfTransitions()),
                        Json.prop("dfaFwdStates", executorNodeForward == null ? 0 : executorNodeForward.getNumberOfStates()),
                        Json.prop("dfaFwdTransitions", executorNodeForward == null ? 0 : executorNodeForward.getNumberOfTransitions()),
                        Json.prop("dfaBckStates", executorNodeBackward == null ? 0 : executorNodeBackward.getNumberOfStates()),
                        Json.prop("dfaBckTransitions", executorNodeBackward == null ? 0 : executorNodeBackward.getNumberOfTransitions()),
                        Json.prop("dfaCGStates", executorNodeCaptureGroups == null ? 0 : executorNodeCaptureGroups.getNumberOfStates()),
                        Json.prop("dfaCGTransitions", executorNodeCaptureGroups == null ? 0 : executorNodeCaptureGroups.getNumberOfTransitions()),
                        Json.prop("traceFinder", traceFinderNFA != null),
                        Json.prop("compilerResult", compilerResultToString(result))) + ",");
    }

    private static String compilerResultToString(RegexExecNode result) {
        if (result instanceof TRegexExecNode) {
            return "tregex";
        } else if (result instanceof LiteralRegexExecNode) {
            return "literal";
        } else if (result instanceof DeadRegexExecNode) {
            return "dead";
        } else {
            return "bailout";
        }
    }
}
