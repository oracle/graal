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

import static com.oracle.truffle.regex.tregex.nodes.nfa.TRegexBacktrackerSubExecutorNode.NO_SUB_EXECUTORS;

import java.util.ArrayDeque;
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.regex.AbstractRegexObject;
import com.oracle.truffle.regex.RegexExecNode;
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
import com.oracle.truffle.regex.tregex.nodes.TRegexExecNode;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorBaseNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexDFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexDFAExecutorProperties;
import com.oracle.truffle.regex.tregex.nodes.nfa.TRegexBacktrackingNFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.nfa.TRegexLiteralLookAroundExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.nfa.TRegexNFAExecutorNode;
import com.oracle.truffle.regex.tregex.parser.RegexASTPostProcessor;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.RegexProperties;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.ASTLaTexExportVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.PreCalcResultVisitor;
import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavor;
import com.oracle.truffle.regex.tregex.util.DFAExport;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.Loggers;
import com.oracle.truffle.regex.tregex.util.NFAExport;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

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
    private AbstractRegexObject namedCaptureGroups = null;
    private PureNFA pureNFA = null;
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

    public AbstractRegexObject getNamedCaptureGroups() {
        return namedCaptureGroups;
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
        phaseStart("Parser");
        try {
            parse();
            prepareASTForDFA();
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
        if (ast.canTransformToDFA()) {
            try {
                createNFA();
                if (nfa.isDead()) {
                    Loggers.LOG_MATCHING_STRATEGY.fine(() -> "regex cannot match, using dummy matcher");
                    return new DeadRegexExecNode(language, source);
                }
                return new TRegexExecNode(ast, TRegexNFAExecutorNode.create(nfa));
            } catch (UnsupportedRegexException e) {
                // fall back to backtracking executor
                Loggers.LOG_MATCHING_STRATEGY.fine(() -> "NFA generator bailout: " + e.getReason() + ", using back-tracking matcher");
            }
        } else {
            Loggers.LOG_MATCHING_STRATEGY.fine(() -> "using back-tracking matcher, reason: " + ast.canTransformToDFAFailureReason());
        }
        return new TRegexExecNode(ast, compileBacktrackingExecutor());
    }

    private static final class StackEntry {
        private final PureNFA nfa;
        private final TRegexExecutorBaseNode[] subExecutors;
        private int i = 0;

        private StackEntry(PureNFA nfa) {
            this.nfa = nfa;
            this.subExecutors = nfa.getSubtrees().length == 0 ? NO_SUB_EXECUTORS : new TRegexExecutorBaseNode[nfa.getSubtrees().length];
        }
    }

    public TRegexBacktrackingNFAExecutorNode compileBacktrackingExecutor() {
        assert ast != null;
        pureNFA = PureNFAGenerator.mapToNFA(ast);
        debugPureNFA();
        ArrayDeque<StackEntry> stack = new ArrayDeque<>();
        stack.push(new StackEntry(pureNFA));
        int totalNumberOfStates = 0;
        int totalNumberOfTransitions = 0;
        while (true) {
            assert !stack.isEmpty();
            StackEntry cur = stack.peek();
            for (; cur.i < cur.subExecutors.length; cur.i++) {
                PureNFA subNFA = cur.nfa.getSubtrees()[cur.i];
                if (subNFA.getSubtrees().length == 0) {
                    RegexASTSubtreeRootNode astSubtree = subNFA.getASTSubtree(ast);
                    if (astSubtree.isLookAroundAssertion() && astSubtree.asLookAroundAssertion().isLiteral()) {
                        cur.subExecutors[cur.i] = TRegexLiteralLookAroundExecutorNode.create(ast, astSubtree.asLookAroundAssertion(), compilationBuffer);
                    } else {
                        cur.subExecutors[cur.i] = new TRegexBacktrackingNFAExecutorNode(ast, subNFA, subNFA.getNumberOfStates(), subNFA.getNumberOfTransitions(), NO_SUB_EXECUTORS, false,
                                        compilationBuffer);
                    }
                    totalNumberOfStates += cur.subExecutors[cur.i].getNumberOfStates();
                    totalNumberOfTransitions += cur.subExecutors[cur.i].getNumberOfTransitions();
                } else {
                    stack.push(new StackEntry(subNFA));
                    break;
                }
            }
            if (cur.i == cur.subExecutors.length) {
                assert cur == stack.peek();
                stack.pop();
                totalNumberOfStates += cur.nfa.getNumberOfStates();
                totalNumberOfTransitions += cur.nfa.getNumberOfTransitions();
                if (cur.nfa.isRoot()) {
                    assert stack.isEmpty();
                    return new TRegexBacktrackingNFAExecutorNode(ast, cur.nfa, totalNumberOfStates, totalNumberOfTransitions, cur.subExecutors, ast.getOptions().isMustAdvance(), compilationBuffer);
                } else {
                    assert !stack.isEmpty();
                    StackEntry parent = stack.peek();
                    parent.subExecutors[parent.i++] = new TRegexBacktrackingNFAExecutorNode(ast, cur.nfa, cur.nfa.getNumberOfStates(), cur.nfa.getNumberOfTransitions(), cur.subExecutors, false,
                                    compilationBuffer);
                }
            }
        }
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
        if (allowSimpleCG && preCalculatedResults == null && TRegexOptions.TRegexEnableTraceFinder && !ast.getRoot().hasLoops() && properties.isFixedCodePointWidth() &&
                        !ast.getRoot().hasVariablePrefixLength()) {
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
        boolean traceFinder = preCalculatedResults != null && preCalculatedResults.length > 0;
        final boolean trackLastGroup = ast.getOptions().getFlavor().usesLastGroupResultField();
        executorNodeForward = createDFAExecutor(nfa, true, true, false, allowSimpleCG && !traceFinder && !(ast.getRoot().startsWithCaret() && !properties.hasCaptureGroups()), trackLastGroup);
        final boolean createCaptureGroupTracker = !executorNodeForward.isSimpleCG() && (properties.hasCaptureGroups() || properties.hasLookAroundAssertions() || ast.getOptions().isMustAdvance()) &&
                        !traceFinder;
        if (createCaptureGroupTracker) {
            executorNodeCaptureGroups = createDFAExecutor(nfa, true, false, true, false, trackLastGroup);
        }
        if (traceFinder && preCalculatedResults.length > 1) {
            executorNodeBackward = createDFAExecutor(traceFinderNFA, false, false, false, false, false);
        } else if (!executorNodeForward.isAnchored() && !executorNodeForward.isSimpleCG() && (!traceFinder || !nfa.hasReverseUnAnchoredEntry()) && !source.getOptions().isBooleanMatch()) {
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
        assert ast.canTransformToDFA();
        assert properties.hasCaptureGroups() || properties.hasLookAroundAssertions() || ast.getOptions().isMustAdvance();
        assert !ast.getRoot().isDead();
        createNFA();
        TRegexDFAExecutorNode eagerCG = createDFAExecutor(nfa, true, true, true, false, ast.getOptions().getFlavor().usesLastGroupResultField());
        Loggers.LOG_AUTOMATON_SIZES.finer(() -> Json.obj(
                        patternToJson(),
                        Json.prop("flags", source.getFlags()),
                        Json.prop("dfaEagerCG", automatonSize(eagerCG))).toString());
        return eagerCG;
    }

    private void createAST() {
        phaseStart("Parser");
        try {
            parse();
            prepareASTForDFA();
        } finally {
            phaseEnd("Parser");
        }
        debugAST();
    }

    private void parse() {
        RegexFlavor flavor = source.getOptions().getFlavor();
        RegexParser parser = flavor.createParser(language, source, compilationBuffer);
        ast = parser.parse();
        ast.setFlavorSpecificFlags(parser.getFlags());
        namedCaptureGroups = parser.getNamedCaptureGroups();
    }

    private void prepareASTForDFA() {
        new RegexASTPostProcessor(ast, compilationBuffer).prepareForDFA();
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
                        trackLastGroup, nfaArg.getAst().getRoot().getMinPath()), null);
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
                            Json.prop("pureNfa", pureNFA.toJson(ast))))).dump(file);
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
                        patternToJson(),
                        Json.prop("flags", source.getFlags()),
                        Json.prop("props", ast == null ? new RegexProperties() : ast.getProperties()),
                        Json.prop("astNodes", ast == null ? 0 : ast.getNumberOfNodes()),
                        Json.prop("pureNfaStates", pureNFA == null ? 0 : pureNFA.getNumberOfStates()),
                        Json.prop("pureNfaTransitions", pureNFA == null ? 0 : pureNFA.getNumberOfTransitions()),
                        Json.prop("nfaStates", nfa == null ? 0 : nfa.getNumberOfStates()),
                        Json.prop("nfaTransitions", nfa == null ? 0 : nfa.getNumberOfTransitions()),
                        Json.prop("dfaFwd", automatonSize(executorNodeForward)),
                        Json.prop("dfaBck", automatonSize(executorNodeBackward)),
                        Json.prop("dfaLazyCG", automatonSize(executorNodeCaptureGroups)),
                        Json.prop("traceFinder", traceFinderNFA != null),
                        Json.prop("compilerResult", compilerResultToString(result))) + ",");
    }

    private static JsonValue automatonSize(TRegexDFAExecutorNode node) {
        if (node == null) {
            return Json.nullValue();
        }
        return Json.obj(
                        Json.prop("states", node.getNumberOfStates()),
                        Json.prop("transitions", node.getNumberOfTransitions()));
    }

    private JsonObject.JsonObjectProperty patternToJson() {
        return Json.prop("pattern", source.getPattern().length() > 200 ? source.getPattern().substring(0, 200) + "..." : source.getPattern());
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
