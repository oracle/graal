/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

/*
 * current status:
 *
 * truffle.regex.tregex can handle quantifiers, character classes, alternations,
 * positive look-aheads, and positive look-behinds of fixed length.
 * Counted repetitions are implemented by transforming them to alternations
 * (e.g. a{2,4} => aa|aaa|aaaa).
 *
 * basic structure of truffle.regex.tregex:
 *
 * tregex parses a regular expression using the custom RegexParser and transforms it to a non-deterministic finite
 * automaton (NFA) using the data structures found in tregex.nfa.
 *
 * The NFA is compiled to a DFA (deterministic finite automaton) during pattern matching. Each DFA stateSet is a
 * set of NFA states, which is stored as a BitSet where each bit corresponds to a slot in the NFA array.
 *
 */

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.regex.CompiledRegexObject;
import com.oracle.truffle.regex.RegexCompiler;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
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
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.NFAExport;

public final class TRegexCompiler extends RegexCompiler {

    private final DebugUtil.DebugLogger logBailout = new DebugUtil.DebugLogger("TRegex Bailout: ", DebugUtil.LOG_BAILOUT_MESSAGES);
    private final DebugUtil.DebugLogger logPhases = new DebugUtil.DebugLogger("TRegex Phase: ", DebugUtil.LOG_PHASES);
    private final DebugUtil.DebugLogger logSizes = new DebugUtil.DebugLogger("", DebugUtil.LOG_AUTOMATON_SIZES);
    private final DebugUtil.Timer timer = DebugUtil.LOG_PHASES ? new DebugUtil.Timer() : null;

    private final RegexLanguage language;
    private final RegexOptions options;

    public TRegexCompiler(RegexLanguage language, RegexOptions options) {
        this.language = language;
        this.options = options;
    }

    @CompilerDirectives.TruffleBoundary
    @Override
    public TruffleObject compile(RegexSource source) throws RegexSyntaxException {
        CompilationBuffer compilationBuffer = new CompilationBuffer();
        // System.out.println("TRegex compiling " +
        // DebugUtil.jsStringEscape(source.toString()));
        // System.out.println(new RegexUnifier(pattern, flags).getUnifiedPattern());
        phaseStart("Parser");
        RegexAST ast = new RegexParser(source, options).parse();
        phaseEnd("Parser");
        debugAST(ast);
        RegexProperties properties = ast.getProperties();
        if (!isSupported(properties)) {
            // features not supported by DFA
            throw new UnsupportedRegexException("unsupported feature: " + source);
        }
        if (ast.getRoot().isDead()) {
            return new CompiledRegexObject(new DeadRegexExecRootNode(language, source));
        }
        LiteralRegexExecRootNode literal = LiteralRegexEngine.createNode(language, ast);
        if (literal != null) {
            logSizes.log(String.format("\"/%s/\", \"%s\", %d, %d, %d, %d, %d, \"literal\"", source.getPattern(), source.getFlags(), 0, 0, 0, 0, 0));
            return new CompiledRegexObject(literal);
        }
        PreCalculatedResultFactory[] preCalculatedResults = null;
        if (!(properties.hasAlternations() || properties.hasLookAroundAssertions())) {
            preCalculatedResults = new PreCalculatedResultFactory[]{PreCalcResultVisitor.createResultFactory(ast)};
        }
        phaseStart("NFA");
        NFA nfa = NFAGenerator.createNFA(ast, compilationBuffer);
        phaseEnd("NFA");
        debugNFA(nfa);
        NFA traceFinder = null;
        if (preCalculatedResults == null && TRegexOptions.TRegexEnableTraceFinder &&
                        (properties.hasCaptureGroups() || properties.hasLookAroundAssertions()) && !properties.hasLoops()) {
            try {
                phaseStart("TraceFinder");
                traceFinder = NFATraceFinderGenerator.generateTraceFinder(nfa);
                preCalculatedResults = traceFinder.getPreCalculatedResults();
                phaseEnd("TraceFinder");
                debugTraceFinder(traceFinder);
            } catch (UnsupportedRegexException e) {
                phaseEnd("TraceFinder Bailout");
                logBailout.log("TraceFinder: " + e.getMessage());
                // handle with capture group aware DFA, bailout will always happen before
                // assigning preCalculatedResults
            }
        }
        final boolean createCaptureGroupTracker = (properties.hasCaptureGroups() || properties.hasLookAroundAssertions()) && preCalculatedResults == null;
        TRegexDFAExecutorNode captureGroupExecutor = null;
        int nCG = nfa.getAst().getNumberOfCaptureGroups();
        phaseStart("Forward DFA");
        TRegexDFAExecutorNode executorNode = DFAGenerator.createForwardDFAExecutor(nfa, createExecutorProperties(true, true, false, nCG), compilationBuffer);
        phaseEnd("Forward DFA");
        if (createCaptureGroupTracker) {
            phaseStart("CG DFA");
            captureGroupExecutor = DFAGenerator.createForwardDFAExecutor(nfa, createExecutorProperties(true, false, true, nCG), compilationBuffer);
            phaseEnd("CG DFA");
        }
        TRegexDFAExecutorNode executorNodeB = null;
        if (preCalculatedResults != null && preCalculatedResults.length > 1) {
            phaseStart("Backward DFA");
            executorNodeB = DFAGenerator.createBackwardDFAExecutor(traceFinder, createExecutorProperties(false, false, false, nCG), compilationBuffer);
            phaseEnd("Backward DFA");
        } else if (preCalculatedResults == null || !nfa.hasReverseUnAnchoredEntry()) {
            phaseStart("Backward DFA");
            executorNodeB = DFAGenerator.createBackwardDFAExecutor(nfa, createExecutorProperties(false, false, false, nCG), compilationBuffer);
            phaseEnd("Backward DFA");
        }
        TRegexExecRootNode tRegexRootNode = new TRegexExecRootNode(
                        language, this, source, options.isRegressionTestMode(), preCalculatedResults, executorNode, executorNodeB, captureGroupExecutor);
        if (DebugUtil.LOG_AUTOMATON_SIZES) {
            logAutomatonSizes(source, ast, nfa, traceFinder, captureGroupExecutor, executorNode, executorNodeB);
            logAutomatonSizesCSV(source, ast, nfa, traceFinder, captureGroupExecutor, executorNode, executorNodeB);
        }
        return new CompiledRegexObject(tRegexRootNode);
    }

    @CompilerDirectives.TruffleBoundary
    public TRegexDFAExecutorNode compileEagerDFAExecutor(RegexSource source) {
        CompilationBuffer compilationBuffer = new CompilationBuffer();
        phaseStart("Parser");
        RegexAST ast = new RegexParser(source, options).parse();
        phaseEnd("Parser");
        RegexProperties properties = ast.getProperties();
        assert isSupported(properties);
        assert properties.hasCaptureGroups() || properties.hasLookAroundAssertions();
        assert !ast.getRoot().isDead();
        phaseStart("NFA");
        NFA nfa = NFAGenerator.createNFA(ast, compilationBuffer);
        phaseEnd("NFA");
        phaseStart("CG Eager DFA");
        TRegexDFAExecutorNode eagerCaptureGroupExecutor = DFAGenerator.createForwardDFAExecutor(nfa, createExecutorProperties(true, true, true, nfa.getAst().getNumberOfCaptureGroups()),
                        compilationBuffer);
        phaseEnd("CG Eager DFA");
        return eagerCaptureGroupExecutor;
    }

    private static boolean isSupported(RegexProperties properties) {
        return !(properties.hasBackReferences() ||
                        properties.hasLargeCountedRepetitions() ||
                        properties.hasNegativeLookAheadAssertions() ||
                        properties.hasComplexLookBehindAssertions());
    }

    private static TRegexDFAExecutorProperties createExecutorProperties(boolean forward, boolean searching, boolean trackCaptureGroups, int numberOfCaptureGroups) {
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
                        forward,
                        searching,
                        trackCaptureGroups,
                        numberOfCaptureGroups);
    }

    private static void debugAST(RegexAST ast) {
        if (DebugUtil.DEBUG) {
            System.out.println(ast);
            ASTLaTexExportVisitor.exportLatex(ast, "./ast.tex", ASTLaTexExportVisitor.DrawPointers.LOOKBEHIND_ENTRIES);
            System.out.println(ast.getWrappedRoot().toTable());
        }
    }

    private static void debugNFA(NFA nfa) {
        if (DebugUtil.DEBUG) {
            NFAExport.exportDot(nfa, "./nfa.gv", true);
            NFAExport.exportLaTex(nfa, "./nfa.tex", false);
            NFAExport.exportDotReverse(nfa, "./nfa_reverse.gv", true);
        }
    }

    private static void debugTraceFinder(NFA traceFinder) {
        if (DebugUtil.DEBUG) {
            NFAExport.exportDotReverse(traceFinder, "./trace_finder.gv", true);
            for (int i = 0; i < traceFinder.getPreCalculatedResults().length; i++) {
                System.out.println(i + ": " + traceFinder.getPreCalculatedResults()[i].toTable());
            }
        }
    }

    private void phaseStart(String phase) {
        if (DebugUtil.LOG_PHASES) {
            logPhases.log(phase + " Start");
            timer.start();
        }
    }

    private void phaseEnd(String phase) {
        if (DebugUtil.LOG_PHASES) {
            logPhases.log(phase + " End, elapsed: " + timer.elapsedToString());
        }
    }

    private void logAutomatonSizes(RegexSource source, RegexAST ast, NFA nfa, NFA traceFinder,
                    TRegexDFAExecutorNode captureGroupExecutor, TRegexDFAExecutorNode executorNode, TRegexDFAExecutorNode executorNodeB) {
        logSizes.log(new DebugUtil.Table("AutomatonSizes",
                        new DebugUtil.Value("pattern", source.getPattern()),
                        new DebugUtil.Value("flags", source.getFlags()),
                        new DebugUtil.Value("ASTNodes", ast.getNumberOfNodes()),
                        new DebugUtil.Value("NFAStates", nfa.getStates().length),
                        new DebugUtil.Value("DFAStatesFwd", executorNode.getNumberOfStates()),
                        new DebugUtil.Value("DFAStatesBck", traceFinder == null ? executorNodeB.getNumberOfStates() : 0),
                        new DebugUtil.Value("TraceFinderStates", traceFinder == null ? 0 : executorNodeB.getNumberOfStates()),
                        new DebugUtil.Value("CGDFAStates", captureGroupExecutor == null ? 0 : captureGroupExecutor.getNumberOfStates())).toString());
    }

    private void logAutomatonSizesCSV(RegexSource source, RegexAST ast, NFA nfa, NFA traceFinder,
                    TRegexDFAExecutorNode captureGroupExecutor, TRegexDFAExecutorNode executorNode, TRegexDFAExecutorNode executorNodeB) {
        logSizes.log(String.format("\"/%s/\", \"%s\", %d, %d, %d, %d, %d, %d, \"dfa\"",
                        source.getPattern(), source.getFlags(),
                        ast.getNumberOfNodes(),
                        nfa.getStates().length,
                        executorNode.getNumberOfStates(),
                        traceFinder == null ? executorNodeB.getNumberOfStates() : 0,
                        traceFinder == null ? 0 : executorNodeB.getNumberOfStates(),
                        captureGroupExecutor == null ? 0 : captureGroupExecutor.getNumberOfStates()));
    }
}
