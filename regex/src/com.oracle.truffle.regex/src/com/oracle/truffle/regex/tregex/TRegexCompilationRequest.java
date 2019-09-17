/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex;

import static com.oracle.truffle.regex.tregex.util.DebugUtil.LOG_AUTOMATON_SIZES;
import static com.oracle.truffle.regex.tregex.util.DebugUtil.LOG_BAILOUT_MESSAGES;
import static com.oracle.truffle.regex.tregex.util.DebugUtil.LOG_PHASES;
import static com.oracle.truffle.regex.tregex.util.DebugUtil.LOG_TREGEX_COMPILATIONS;

import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.regex.CompiledRegexObject;
import com.oracle.truffle.regex.RegexExecRootNode;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexOptions;
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
import com.oracle.truffle.regex.tregex.nodes.TRegexDFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.TRegexDFAExecutorProperties;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecRootNode;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.RegexProperties;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.ASTLaTexExportVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.PreCalcResultVisitor;
import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavor;
import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavorProcessor;
import com.oracle.truffle.regex.tregex.util.DFAExport;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.NFAExport;
import com.oracle.truffle.regex.tregex.util.json.Json;

/**
 * This class is responsible for compiling a single regex pattern. The compilation process is
 * designed to be single-threaded, but multiple {@link TRegexCompilationRequest}s can be compiled in
 * parallel.
 */
final class TRegexCompilationRequest {

    private final DebugUtil.Timer timer = shouldLogPhases() ? new DebugUtil.Timer() : null;

    private final TRegexCompiler tRegexCompiler;

    private final RegexSource source;
    private RegexAST ast = null;
    private NFA nfa = null;
    private NFA traceFinderNFA = null;
    private TRegexDFAExecutorNode executorNodeForward = null;
    private TRegexDFAExecutorNode executorNodeBackward = null;
    private TRegexDFAExecutorNode executorNodeCaptureGroups = null;
    private final CompilationBuffer compilationBuffer = new CompilationBuffer();

    TRegexCompilationRequest(TRegexCompiler tRegexCompiler, RegexSource source) {
        this.tRegexCompiler = tRegexCompiler;
        this.source = source;
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
        LOG_TREGEX_COMPILATIONS.finer(() -> String.format("TRegex compiling %s\n%s", DebugUtil.jsStringEscape(source.toString()), new RegexUnifier(source).getUnifiedPattern()));
        createAST();
        RegexProperties properties = ast.getProperties();
        checkFeatureSupport(properties);
        if (ast.getRoot().isDead()) {
            return new DeadRegexExecRootNode(tRegexCompiler.getLanguage(), source);
        }
        LiteralRegexExecRootNode literal = LiteralRegexEngine.createNode(tRegexCompiler.getLanguage(), ast);
        if (literal != null) {
            return literal;
        }
        PreCalculatedResultFactory[] preCalculatedResults = null;
        if (!(properties.hasAlternations() || properties.hasLookAroundAssertions())) {
            preCalculatedResults = new PreCalculatedResultFactory[]{PreCalcResultVisitor.createResultFactory(ast)};
        }
        createNFA();
        if (preCalculatedResults == null && TRegexOptions.TRegexEnableTraceFinder && !ast.getRoot().hasLoops()) {
            try {
                phaseStart("TraceFinder NFA");
                traceFinderNFA = NFATraceFinderGenerator.generateTraceFinder(nfa);
                preCalculatedResults = traceFinderNFA.getPreCalculatedResults();
                phaseEnd("TraceFinder NFA");
                debugTraceFinder();
            } catch (UnsupportedRegexException e) {
                phaseEnd("TraceFinder NFA Bailout");
                LOG_BAILOUT_MESSAGES.fine(() -> "TraceFinder: " + e.getReason() + ": " + source);
                // handle with capture group aware DFA, bailout will always happen before
                // assigning preCalculatedResults
            }
        }
        final boolean createCaptureGroupTracker = (properties.hasCaptureGroups() || properties.hasLookAroundAssertions()) && preCalculatedResults == null;
        executorNodeForward = createDFAExecutor(nfa, true, true, false);
        if (createCaptureGroupTracker) {
            executorNodeCaptureGroups = createDFAExecutor(nfa, true, false, true);
        }
        if (preCalculatedResults != null && preCalculatedResults.length > 1) {
            executorNodeBackward = createDFAExecutor(traceFinderNFA, false, false, false);
        } else if (preCalculatedResults == null || !nfa.hasReverseUnAnchoredEntry()) {
            executorNodeBackward = createDFAExecutor(nfa, false, false, false);
        }
        return new TRegexExecRootNode(
                        tRegexCompiler.getLanguage(),
                        tRegexCompiler,
                        source,
                        ast.getFlags(),
                        tRegexCompiler.getOptions().isRegressionTestMode(),
                        preCalculatedResults,
                        executorNodeForward,
                        executorNodeBackward,
                        executorNodeCaptureGroups);
    }

    @TruffleBoundary
    TRegexDFAExecutorNode compileEagerDFAExecutor() {
        createAST();
        RegexProperties properties = ast.getProperties();
        assert isSupported(properties);
        assert properties.hasCaptureGroups() || properties.hasLookAroundAssertions();
        assert !ast.getRoot().isDead();
        createNFA();
        return createDFAExecutor(nfa, true, true, true);
    }

    private static void checkFeatureSupport(RegexProperties properties) throws UnsupportedRegexException {
        if (properties.hasBackReferences()) {
            throw new UnsupportedRegexException("backreferences not supported");
        }
        if (properties.hasLargeCountedRepetitions()) {
            throw new UnsupportedRegexException("bounds of range quantifier too high");
        }
        if (properties.hasNegativeLookAheadAssertions()) {
            throw new UnsupportedRegexException("negative lookahead assertions not supported");
        }
        if (properties.hasNonLiteralLookBehindAssertions()) {
            throw new UnsupportedRegexException("body of lookbehind assertion too complex");
        }
        if (properties.hasNegativeLookBehindAssertions()) {
            throw new UnsupportedRegexException("negative lookbehind assertions not supported");
        }
    }

    private static boolean isSupported(RegexProperties properties) {
        try {
            checkFeatureSupport(properties);
            return true;
        } catch (UnsupportedRegexException e) {
            return false;
        }
    }

    private void createAST() {
        RegexOptions options = tRegexCompiler.getOptions();
        RegexFlavor flavor = options.getFlavor();
        RegexSource ecmascriptSource = source;
        if (flavor != null) {
            phaseStart("Flavor");
            RegexFlavorProcessor flavorProcessor = flavor.forRegex(source);
            ecmascriptSource = flavorProcessor.toECMAScriptRegex();
            phaseEnd("Flavor");
        }
        phaseStart("Parser");
        ast = new RegexParser(ecmascriptSource, options).parse();
        phaseEnd("Parser");
        debugAST();
    }

    private void createNFA() {
        phaseStart("NFA");
        nfa = NFAGenerator.createNFA(ast, compilationBuffer);
        phaseEnd("NFA");
        debugNFA();
    }

    private TRegexDFAExecutorNode createDFAExecutor(NFA nfaArg, boolean forward, boolean searching, boolean trackCaptureGroups) {
        DFAGenerator dfa = new DFAGenerator(nfaArg, createExecutorProperties(nfaArg, forward, searching, trackCaptureGroups), compilationBuffer, tRegexCompiler.getOptions());
        phaseStart(dfa.getDebugDumpName() + " DFA");
        dfa.calcDFA();
        TRegexDFAExecutorNode executorNode = dfa.createDFAExecutor();
        phaseEnd(dfa.getDebugDumpName() + " DFA");
        debugDFA(dfa);
        return executorNode;
    }

    private TRegexDFAExecutorProperties createExecutorProperties(NFA nfaArg, boolean forward, boolean searching, boolean trackCaptureGroups) {
        return new TRegexDFAExecutorProperties(
                        forward,
                        searching,
                        trackCaptureGroups,
                        tRegexCompiler.getOptions().isRegressionTestMode(),
                        nfaArg.getAst().getNumberOfCaptureGroups(),
                        nfaArg.getAst().getRoot().getMinPath());
    }

    private void debugAST() {
        if (tRegexCompiler.getOptions().isDumpAutomata()) {
            Env env = RegexLanguage.getCurrentContext().getEnv();
            TruffleFile file = env.getTruffleFile("./ast.tex");
            ASTLaTexExportVisitor.exportLatex(ast, file);
            file = env.getTruffleFile("ast.json");
            ast.getWrappedRoot().toJson().dump(file);
        }
    }

    private void debugNFA() {
        if (tRegexCompiler.getOptions().isDumpAutomata()) {
            Env env = RegexLanguage.getCurrentContext().getEnv();
            TruffleFile file = env.getTruffleFile("./nfa.gv");
            NFAExport.exportDot(nfa, file, true, false);
            file = env.getTruffleFile("./nfa.tex");
            NFAExport.exportLaTex(nfa, file, false, true);
            file = env.getTruffleFile("./nfa_reverse.gv");
            NFAExport.exportDotReverse(nfa, file, true, false);
            file = env.getTruffleFile("nfa.json");
            nfa.toJson().dump(file);
        }
    }

    private void debugTraceFinder() {
        if (tRegexCompiler.getOptions().isDumpAutomata()) {
            Env env = RegexLanguage.getCurrentContext().getEnv();
            TruffleFile file = env.getTruffleFile("./trace_finder.gv");
            NFAExport.exportDotReverse(traceFinderNFA, file, true, false);
            file = env.getTruffleFile("nfa_trace_finder.json");
            traceFinderNFA.toJson().dump(file);
        }
    }

    private void debugDFA(DFAGenerator dfa) {
        if (tRegexCompiler.getOptions().isDumpAutomata()) {
            Env env = RegexLanguage.getCurrentContext().getEnv();
            TruffleFile file = env.getTruffleFile("dfa_" + dfa.getDebugDumpName() + ".gv");
            DFAExport.exportDot(dfa, file, false);
            file = env.getTruffleFile("dfa_" + dfa.getDebugDumpName() + ".json");
            Json.obj(Json.prop("dfa", dfa.toJson())).dump(file);
        }
    }

    private static boolean shouldLogPhases() {
        return LOG_PHASES.isLoggable(Level.FINER);
    }

    private void phaseStart(String phase) {
        if (shouldLogPhases()) {
            LOG_PHASES.finer(phase + " Start");
            timer.start();
        }
    }

    private void phaseEnd(String phase) {
        if (shouldLogPhases()) {
            LOG_PHASES.finer(phase + " End, elapsed: " + timer.elapsedToString());
        }
    }

    private void logAutomatonSizes(RegexExecRootNode result) {
        LOG_AUTOMATON_SIZES.finer(() -> Json.obj(
                        Json.prop("pattern", source.getPattern()),
                        Json.prop("flags", source.getFlags()),
                        Json.prop("props", ast == null ? new RegexProperties() : ast.getProperties()),
                        Json.prop("astNodes", ast == null ? 0 : ast.getNumberOfNodes()),
                        Json.prop("nfaStates", nfa == null ? 0 : nfa.getStates().length),
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
