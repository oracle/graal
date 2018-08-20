/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.regex.CompiledRegex;
import com.oracle.truffle.regex.CompiledRegexObject;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.UnsupportedRegexException;
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
import com.oracle.truffle.regex.tregex.util.DFAExport;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.NFAExport;
import com.oracle.truffle.regex.tregex.util.json.Json;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * This class is responsible for compiling a single regex pattern. The compilation process is
 * designed to be single-threaded, but multiple {@link TRegexCompilationRequest}s can be compiled in
 * parallel.
 */
final class TRegexCompilationRequest {

    private DebugUtil.DebugLogger logBailout;
    private DebugUtil.DebugLogger logPhases;
    private DebugUtil.DebugLogger logSizes;
    private final DebugUtil.Timer timer = DebugUtil.LOG_PHASES ? new DebugUtil.Timer() : null;

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
    TruffleObject compile() {
        try {
            CompiledRegex compiledRegex = compileInternal();
            if (DebugUtil.LOG_AUTOMATON_SIZES) {
                logAutomatonSizes(compiledRegex);
            }
            return new CompiledRegexObject(compiledRegex);
        } catch (UnsupportedRegexException e) {
            if (DebugUtil.LOG_AUTOMATON_SIZES) {
                logAutomatonSizes(null);
            }
            e.setReason("TRegex: " + e.getReason());
            e.setRegex(source);
            throw e;
        }
    }

    @TruffleBoundary
    private CompiledRegex compileInternal() {
        // System.out.println("TRegex compiling " +
        // DebugUtil.jsStringEscape(source.toString()));
        // System.out.println(new RegexUnifier(pattern, flags).getUnifiedPattern());
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
        if (preCalculatedResults == null && TRegexOptions.TRegexEnableTraceFinder &&
                        (properties.hasCaptureGroups() || properties.hasLookAroundAssertions()) && !properties.hasLoops()) {
            try {
                phaseStart("TraceFinder NFA");
                traceFinderNFA = NFATraceFinderGenerator.generateTraceFinder(nfa);
                preCalculatedResults = traceFinderNFA.getPreCalculatedResults();
                phaseEnd("TraceFinder NFA");
                debugTraceFinder();
            } catch (UnsupportedRegexException e) {
                phaseEnd("TraceFinder NFA Bailout");
                logBailout("TraceFinder: " + e.getMessage());
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
        phaseStart("Parser");
        ast = new RegexParser(source, tRegexCompiler.getOptions()).parse();
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
        DFAGenerator dfa = new DFAGenerator(nfaArg, createExecutorProperties(nfaArg, forward, searching, trackCaptureGroups), compilationBuffer);
        phaseStart(dfa.getDebugDumpName() + " DFA");
        dfa.calcDFA();
        TRegexDFAExecutorNode executorNode = dfa.createDFAExecutor();
        phaseEnd(dfa.getDebugDumpName() + " DFA");
        debugDFA(dfa);
        return executorNode;
    }

    private TRegexDFAExecutorProperties createExecutorProperties(NFA nfaArg, boolean forward, boolean searching, boolean trackCaptureGroups) {
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        FrameSlot inputFS = frameDescriptor.addFrameSlot("input", FrameSlotKind.Object);
        FrameSlot fromIndexFS = frameDescriptor.addFrameSlot("fromIndex", FrameSlotKind.Int);
        FrameSlot indexFS = frameDescriptor.addFrameSlot("index", FrameSlotKind.Int);
        FrameSlot maxIndexFS = frameDescriptor.addFrameSlot("maxIndex", FrameSlotKind.Int);
        FrameSlot curMaxIndexFS = frameDescriptor.addFrameSlot("curMaxIndex", FrameSlotKind.Int);
        FrameSlot successorIndexFS = frameDescriptor.addFrameSlot("successorIndex", FrameSlotKind.Int);
        FrameSlot resultFS = frameDescriptor.addFrameSlot("result", FrameSlotKind.Int);
        FrameSlot captureGroupResultFS = frameDescriptor.addFrameSlot("captureGroupResult", FrameSlotKind.Object);
        FrameSlot lastTransitionFS = frameDescriptor.addFrameSlot("lastTransition", FrameSlotKind.Int);
        FrameSlot cgDataFS = frameDescriptor.addFrameSlot("cgData", FrameSlotKind.Object);
        FrameSlot inputIsCompactStringFS = frameDescriptor.addFrameSlot("inputIsCompactString", FrameSlotKind.Boolean);
        return new TRegexDFAExecutorProperties(
                        frameDescriptor,
                        inputFS,
                        fromIndexFS,
                        indexFS,
                        maxIndexFS,
                        curMaxIndexFS,
                        successorIndexFS,
                        resultFS,
                        captureGroupResultFS,
                        lastTransitionFS,
                        cgDataFS,
                        inputIsCompactStringFS,
                        forward,
                        searching,
                        trackCaptureGroups,
                        tRegexCompiler.getOptions().isRegressionTestMode(),
                        nfaArg.getAst().getNumberOfCaptureGroups(),
                        nfaArg.getAst().getRoot().getMinPath());
    }

    private void logPhase(String msg) {
        if (logPhases == null) {
            logPhases = new DebugUtil.DebugLogger("TRegex Phase: ", DebugUtil.LOG_PHASES);
        }
        logPhases.log(msg);
    }

    private void logBailout(String msg) {
        if (logBailout == null) {
            logBailout = new DebugUtil.DebugLogger("TRegex Bailout: ", DebugUtil.LOG_BAILOUT_MESSAGES);
        }
        logBailout.log(msg);
    }

    private void logSizes(String msg) {
        if (logSizes == null) {
            logSizes = new DebugUtil.DebugLogger("", DebugUtil.LOG_AUTOMATON_SIZES);
        }
        logSizes.log(msg);
    }

    private void debugAST() {
        if (DebugUtil.DEBUG) {
            ASTLaTexExportVisitor.exportLatex(ast, "./ast.tex", ASTLaTexExportVisitor.DrawPointers.LOOKBEHIND_ENTRIES);
            ast.getWrappedRoot().toJson().dump("ast.json");
        }
    }

    private void debugNFA() {
        if (DebugUtil.DEBUG) {
            NFAExport.exportDot(nfa, "./nfa.gv", true, false);
            NFAExport.exportLaTex(nfa, "./nfa.tex", false, true);
            NFAExport.exportDotReverse(nfa, "./nfa_reverse.gv", true, false);
            nfa.toJson().dump("nfa.json");
        }
    }

    private void debugTraceFinder() {
        if (DebugUtil.DEBUG) {
            NFAExport.exportDotReverse(traceFinderNFA, "./trace_finder.gv", true, false);
            traceFinderNFA.toJson().dump("nfa_trace_finder.json");
        }
    }

    private static void debugDFA(DFAGenerator dfa) {
        if (DebugUtil.DEBUG) {
            DFAExport.exportDot(dfa, "dfa_" + dfa.getDebugDumpName() + ".gv", false);
            Json.obj(Json.prop("dfa", dfa.toJson())).dump("dfa_" + dfa.getDebugDumpName() + ".json");
        }
    }

    private void phaseStart(String phase) {
        if (DebugUtil.LOG_PHASES) {
            logPhase(phase + " Start");
            timer.start();
        }
    }

    private void phaseEnd(String phase) {
        if (DebugUtil.LOG_PHASES) {
            logPhase(phase + " End, elapsed: " + timer.elapsedToString());
        }
    }

    private void logAutomatonSizes(CompiledRegex result) {
        logSizes(Json.obj(
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

    private static String compilerResultToString(CompiledRegex result) {
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
