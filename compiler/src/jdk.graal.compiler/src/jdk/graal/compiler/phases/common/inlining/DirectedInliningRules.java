/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.inlining;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/// Parsed directed inlining directives scoped to one compilation root.
///
/// A directive describes a complete call chain from the compilation root to the selected callee:
///
/// ```
/// A.a[@<bci>]->C.c
/// A.a[@<bci>]->InterfaceB{B_1}.b
/// A.a[@<bci>]->InterfaceB{B_1}.b[@<bci>]->C.c
/// A.a[@<bci>]->B.b[@<bci>]->C.c
/// A.a[@<bci>]->B.b[@<bci>]->C.c[@<bci>]->D.d
/// ```
///
/// An `@<bci>` suffix constrains the outgoing invoke in the method immediately to its left. In a
/// chain directive, `A.a@17` identifies the root invoke that produced the inlined `B.b` instance,
/// while `B.b@42` identifies the selected invoke inside that `B.b` instance. The BCI constraints
/// are independent and may be used together.
///
/// Multiple directives are separated with `,`. Each method component uses {@link MethodFilter}
/// syntax for class, method, and signature matching, but directed rules accept only a single
/// positive pattern per component. MethodFilter wildcards, negated patterns, comma alternatives, and
/// `|` alternatives are not accepted inside a component. Write one directive for each call chain.
/// A mixed command file may contain both directed inline and directed dont-inline directives:
///
/// ```
/// inline,A.a@17->B.b
/// inline,A.a@17->B.b@42->C.c
/// dontinline,A.a@17->InterfaceB{B_2}.b@42->C.c
/// ```
///
/// The command and directive may be separated by `,` or whitespace. Blank lines and lines starting
/// with `#` are ignored.
///
/// A non-root target component may add a receiver-type filter between the declared callee holder and
/// the method name:
///
/// ```
/// InterfaceB{B_1}.b
/// ```
///
/// This matches an invoke whose declared target is `InterfaceB.b`, but selects only candidates reached
/// through receiver type `B_1`. A receiver-type filter must name exactly one receiver type. Write
/// separate directives for separate receiver paths. Receiver type filters accept simple or fully
/// qualified type names. They do not accept wildcards, negation, alternatives, or signatures.
///
/// ```
/// A.a->C.c
/// ```
///
/// Selects invokes of `C.c` directly inside the root method `A.a`.
///
/// ```
/// A.a@17->C.c
/// ```
///
/// Selects only the invoke of `C.c` at BCI 17 in `A.a`.
///
/// ```
/// A.a->B.b@42->C.c
/// ```
///
/// Selects the invoke at BCI 42 in `B.b` after that method has already been inlined into `A.a`.
///
/// ```
/// A.a@17->B.b@42->C.c
/// ```
///
/// Selects the invoke at BCI 42 in the `B.b` instance that was inlined from BCI 17 in `A.a`. Other
/// `B.b` instances in the same compilation root do not match.
///
/// ```
/// A.a@17->B.b@42->C.c@5->D.d
/// ```
///
/// Selects the invoke at BCI 5 in the `C.c` instance reached through the complete inlining path
/// `A.a@17->B.b@42->C.c`. Shorter paths do not match that deeper call site.
///
/// A directed-inline chain forces each prefix edge needed to expose the terminal callee. For
/// example, this single directive forces both `B.b` and then `C.c`:
///
/// ```
/// A.a->B.b@42->C.c
/// ```
///
/// This class only parses and matches rules. The normal inlining policy still owns the profitability
/// decision for call sites not selected by a directed rule.
///
/// For a polymorphic call site, rules are matched against each concrete dispatch candidate.
///
/// ```
/// A.a->InterfaceB.b
/// ```
///
/// If the invoke is declared as `InterfaceB.b`, this selects every concrete candidate for that
/// invoke, for example `B_1.b` and `B_2.b`.
///
/// ```
/// A.a->InterfaceB{B_1}.b
/// ```
///
/// For the same invoke, this selects only the candidate reached through receiver type `B_1`.
/// `B_2` does not match. `A.a->B_1.b` does not select `B_1` from an `InterfaceB.b` call site; it
/// only matches invokes whose declared target is actually `B_1.b`.
///
/// ```
/// A.a->InterfaceB{B_1}.b@42->C.c
/// ```
///
/// Selects the invoke at BCI 42 inside the concrete method reached from the `InterfaceB.b` call
/// site with receiver type `B_1`. For a virtual/interface intermediate component, the concrete
/// receiver type must be explicit because the next `@<bci>` and callee are matched inside the
/// selected concrete implementation. Write separate directives for separate receiver paths.
///
/// Directed inlining therefore forces only the matching concrete candidates; other candidates
/// remain subject to the normal inlining policy, and candidates not selected for inlining remain on
/// the fallback virtual/interface invoke. Directed dont-inlining uses the same matching model in the
/// opposite direction: matching concrete candidates are forbidden, while non-matching
/// candidates may still be selected by the normal policy and any forbidden receiver types remain on
/// the fallback invoke.
public final class DirectedInliningRules {
    private static final char DIRECTIVE_SEPARATOR = ',';
    private static final String EDGE_SEPARATOR = "->";
    private static final String EXPECTED_RULE_FORMAT = "A.a[@<bci>]->B.b or A.a[@<bci>]->InterfaceB{B_1}.b or " +
                    "A.a[@<bci>]->B.b[@<bci>]->...->C.c";

    /**
     * Sentinel for rules that match any caller BCI.
     */
    public static final int ANY_BCI = Integer.MIN_VALUE;
    public static final Callsite[] EMPTY_CALLSITES = new Callsite[0];

    private final List<Rule> rules;

    private DirectedInliningRules(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * Parses directed inline and directed dont-inline option values plus an optional mixed command
     * file. Rules from the string options and the file are unioned.
     *
     * @param inlineOptionValue comma-separated directed inline directives
     * @param dontInlineOptionValue comma-separated directed dont-inline directives
     * @param commandFileOptionValue path to a mixed command file, or {@code null}
     * @return parsed inline and dont-inline rules
     */
    public static RuleSet parse(String inlineOptionValue, String dontInlineOptionValue, String commandFileOptionValue) {
        ArrayList<Rule> inlineRules = new ArrayList<>();
        ArrayList<Rule> dontInlineRules = new ArrayList<>();
        parseRules(inlineOptionValue, inlineRules);
        parseRules(dontInlineOptionValue, dontInlineRules);
        parseCommandFile(commandFileOptionValue, inlineRules, dontInlineRules);
        DirectedInliningRules inline = create(inlineRules);
        DirectedInliningRules dontInline = create(dontInlineRules);
        return inline == null && dontInline == null ? RuleSet.EMPTY : new RuleSet(inline, dontInline);
    }

    private static DirectedInliningRules create(ArrayList<Rule> rules) {
        return rules.isEmpty() ? null : new DirectedInliningRules(rules);
    }

    private static void parseRules(String optionValue, ArrayList<Rule> rules) {
        if (optionValue == null || optionValue.trim().isEmpty()) {
            return;
        }

        for (String ruleSpec : splitTopLevel(optionValue, DIRECTIVE_SEPARATOR)) {
            String trimmedRule = ruleSpec.trim();
            if (!trimmedRule.isEmpty()) {
                rules.add(parseRule(trimmedRule));
            }
        }
    }

    private static void parseCommandFile(String commandFileOptionValue, ArrayList<Rule> inlineRules, ArrayList<Rule> dontInlineRules) {
        if (commandFileOptionValue == null || commandFileOptionValue.trim().isEmpty()) {
            return;
        }
        Path commandFile;
        try {
            commandFile = Path.of(commandFileOptionValue.trim());
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid directed inlining rules file path: " + commandFileOptionValue, e);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(commandFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read directed inlining rules file: " + commandFile, e);
        }
        for (int i = 0; i < lines.size(); i++) {
            parseCommandFileLine(commandFile, i + 1, lines.get(i), inlineRules, dontInlineRules);
        }
    }

    private static void parseCommandFileLine(Path commandFile, int lineNumber, String line, ArrayList<Rule> inlineRules, ArrayList<Rule> dontInlineRules) {
        String commandLine = stripComment(line).trim();
        if (commandLine.isEmpty()) {
            return;
        }
        int commandSeparator = findCommandSeparator(commandLine);
        if (commandSeparator < 0) {
            throw invalidCommand(commandFile, lineNumber, commandLine);
        }
        String command = commandLine.substring(0, commandSeparator).trim().toLowerCase(Locale.ROOT);
        String ruleSpec = commandLine.substring(commandSeparator + 1).trim();
        if (ruleSpec.startsWith(String.valueOf(DIRECTIVE_SEPARATOR))) {
            ruleSpec = ruleSpec.substring(1).trim();
        }
        if (ruleSpec.isEmpty()) {
            throw invalidCommand(commandFile, lineNumber, commandLine);
        }
        Rule rule;
        try {
            rule = parseRule(ruleSpec);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(commandFile + ":" + lineNumber + ": " + e.getMessage(), e);
        }
        switch (command) {
            case "inline" -> inlineRules.add(rule);
            case "dontinline" -> dontInlineRules.add(rule);
            default -> throw invalidCommand(commandFile, lineNumber, commandLine);
        }
    }

    private static String stripComment(String line) {
        int commentStart = line.indexOf('#');
        return commentStart < 0 ? line : line.substring(0, commentStart);
    }

    private static int findCommandSeparator(String commandLine) {
        for (int i = 0; i < commandLine.length(); i++) {
            char c = commandLine.charAt(i);
            if (c == DIRECTIVE_SEPARATOR || Character.isWhitespace(c)) {
                return i;
            }
        }
        return -1;
    }

    private static IllegalArgumentException invalidCommand(Path commandFile, int lineNumber, String commandLine) {
        return new IllegalArgumentException(commandFile + ":" + lineNumber +
                        ": Directed inlining command must have the form 'inline <rule>' or 'dontinline <rule>': " + commandLine);
    }

    private static Rule parseRule(String ruleSpec) {
        ArrayList<String> components = splitTopLevel(ruleSpec, EDGE_SEPARATOR);
        if (components.size() < 2) {
            throw invalidRule(ruleSpec);
        }

        ComponentFilter[] callerFilters = new ComponentFilter[components.size() - 1];
        int[] callerBcis = new int[callerFilters.length];
        for (int i = 0; i < callerFilters.length; i++) {
            String callerSpec = components.get(i).trim();
            if (callerSpec.isEmpty()) {
                throw new IllegalArgumentException("Directed inlining rule must have non-empty caller and callee filters: " + ruleSpec);
            }
            ParsedMethodSpec caller = parseMethodSpec(callerSpec, i == 0 ? "root" : "inlined caller", ruleSpec);
            if (i == 0) {
                callerFilters[i] = new ComponentFilter(parseSinglePositiveMethodFilter(caller.methodSpec(), "root", ruleSpec), ReceiverTypeFilter.EMPTY_ARRAY);
            } else {
                ParsedCalleeSpec callerTarget = parseCalleeSpec(caller.methodSpec(), ruleSpec);
                callerFilters[i] = new ComponentFilter(parseSinglePositiveMethodFilter(callerTarget.methodSpec(), "inlined caller", ruleSpec), callerTarget.receiverTypes());
            }
            callerBcis[i] = caller.bci();
        }

        String calleeSpec = components.get(components.size() - 1).trim();
        if (calleeSpec.isEmpty()) {
            throw new IllegalArgumentException("Directed inlining rule must have non-empty caller and callee filters: " + ruleSpec);
        }

        ParsedCalleeSpec callee = parseCalleeSpec(calleeSpec, ruleSpec);

        return new Rule(ruleSpec,
                        callerFilters,
                        callerBcis,
                        new ComponentFilter(parseSinglePositiveMethodFilter(callee.methodSpec(), "callee", ruleSpec), callee.receiverTypes()));
    }

    private static ParsedMethodSpec parseMethodSpec(String methodSpec, String componentName, String ruleSpec) {
        int bci = ANY_BCI;
        int bciSeparator = findLastTopLevel(methodSpec, '@');
        String parsedMethodSpec = methodSpec;
        if (bciSeparator >= 0) {
            String bciSpec = methodSpec.substring(bciSeparator + 1).trim();
            parsedMethodSpec = methodSpec.substring(0, bciSeparator).trim();
            if (parsedMethodSpec.isEmpty() || bciSpec.isEmpty()) {
                throw new IllegalArgumentException("Directed inlining rule has an empty " + componentName + " filter or bci: " + ruleSpec);
            }
            bci = Integer.parseInt(bciSpec);
        }
        return new ParsedMethodSpec(parsedMethodSpec, bci);
    }

    private static ParsedCalleeSpec parseCalleeSpec(String calleeSpec, String ruleSpec) {
        int typeFilterStart = findTopLevel(calleeSpec, "{");
        if (typeFilterStart < 0) {
            if (findTopLevel(calleeSpec, "}") >= 0) {
                throw invalidRule(ruleSpec);
            }
            return new ParsedCalleeSpec(calleeSpec, ReceiverTypeFilter.EMPTY_ARRAY);
        }

        int typeFilterEnd = findMatchingBrace(calleeSpec, typeFilterStart);
        if (typeFilterEnd < 0) {
            throw invalidRule(ruleSpec);
        }
        String holderSpec = calleeSpec.substring(0, typeFilterStart).trim();
        String typeListSpec = calleeSpec.substring(typeFilterStart + 1, typeFilterEnd).trim();
        String methodAndSignatureSpec = calleeSpec.substring(typeFilterEnd + 1).trim();
        if (holderSpec.isEmpty() || typeListSpec.isEmpty() || methodAndSignatureSpec.isEmpty() || !methodAndSignatureSpec.startsWith(".") ||
                        findTopLevel(calleeSpec, "{", typeFilterEnd + 1) >= 0 || findTopLevel(calleeSpec, "}", typeFilterEnd + 1) >= 0) {
            throw invalidRule(ruleSpec);
        }

        ArrayList<String> receiverTypeSpecs = splitTopLevel(typeListSpec, DIRECTIVE_SEPARATOR);
        if (receiverTypeSpecs.size() != 1) {
            throw new IllegalArgumentException("Directed inlining rule receiver type filter must name exactly one receiver type; use separate rules for separate receiver paths: " +
                            ruleSpec);
        }
        ArrayList<ReceiverTypeFilter> receiverTypes = new ArrayList<>();
        for (String typeSpec : receiverTypeSpecs) {
            String trimmedTypeSpec = typeSpec.trim();
            if (trimmedTypeSpec.isEmpty()) {
                throw invalidRule(ruleSpec);
            }
            receiverTypes.add(parseReceiverTypeFilter(trimmedTypeSpec, ruleSpec));
        }
        return new ParsedCalleeSpec(holderSpec + methodAndSignatureSpec, receiverTypes.toArray(ReceiverTypeFilter.EMPTY_ARRAY));
    }

    private static ReceiverTypeFilter parseReceiverTypeFilter(String typeSpec, String ruleSpec) {
        if (typeSpec.indexOf('*') >= 0 || typeSpec.indexOf('?') >= 0 ||
                        typeSpec.indexOf('|') >= 0 || typeSpec.indexOf('@') >= 0 ||
                        typeSpec.indexOf('(') >= 0 || typeSpec.indexOf(')') >= 0 ||
                        typeSpec.indexOf('{') >= 0 || typeSpec.indexOf('}') >= 0 ||
                        typeSpec.startsWith("~")) {
            throw new IllegalArgumentException("Directed inlining rule receiver type filter must contain simple or qualified positive type names: " +
                            ruleSpec);
        }
        return new ReceiverTypeFilter(typeSpec);
    }

    private static MethodFilter parseSinglePositiveMethodFilter(String filterSpec, String componentName, String ruleSpec) {
        if (filterSpec.indexOf('*') >= 0 || filterSpec.indexOf('?') >= 0 ||
                        findLastTopLevel(filterSpec, ',') >= 0 || filterSpec.indexOf('|') >= 0 ||
                        filterSpec.indexOf('@') >= 0 || filterSpec.indexOf('{') >= 0 || filterSpec.indexOf('}') >= 0 ||
                        filterSpec.startsWith("~")) {
            throw new IllegalArgumentException("Directed inlining rule " + componentName +
                            " filter must be a single positive MethodFilter pattern; use ',' to separate rules: " + ruleSpec);
        }
        return MethodFilter.parse(filterSpec);
    }

    private static IllegalArgumentException invalidRule(String ruleSpec) {
        return new IllegalArgumentException("Directed inlining rule must have the form " + EXPECTED_RULE_FORMAT + ": " + ruleSpec);
    }

    private String findRule(Callsite[] callsites, ResolvedJavaMethod declaredCalleeMethod, ResolvedJavaMethod concreteCalleeMethod, ResolvedJavaType receiverType, boolean includePrefixes) {
        for (Rule rule : rules) {
            if (rule.matchesDirectedInvoke(callsites, declaredCalleeMethod, receiverType) ||
                            (includePrefixes && rule.matchesDirectedPrefix(callsites, declaredCalleeMethod, concreteCalleeMethod, receiverType))) {
                return rule.ruleSpec();
            }
        }
        return null;
    }

    /**
     * Finds the first rule whose complete call chain matches the candidate invoke target. If the
     * terminal rule has a receiver-type filter, the filter must match {@code receiverType}. This lets
     * directives name an abstract or interface method and optionally select the one receiver path
     * that may be inlined from that declared call site.
     *
     * @param callsites complete sequence of caller edges from the compilation root to the candidate
     *            invoke
     * @param calleeMethod concrete candidate target method
     * @param receiverType receiver type for the candidate, or {@code null}
     * @param declaredCalleeMethod target method declared by the invoke before devirtualization
     * @return matched rule text, or {@code null} when no rule applies
     */
    public String findMatchingRule(Callsite[] callsites, ResolvedJavaMethod calleeMethod, ResolvedJavaType receiverType, ResolvedJavaMethod declaredCalleeMethod) {
        ResolvedJavaMethod methodToMatch = declaredCalleeMethod == null ? calleeMethod : declaredCalleeMethod;
        return findRule(callsites, methodToMatch, calleeMethod, receiverType, false);
    }

    /**
     * Finds the first rule that either matches the complete candidate call chain or has the
     * candidate as the next required prefix edge. Directed inline uses this to force each edge in a
     * complete chain.
     *
     * @param callsites complete sequence of caller edges from the compilation root to the candidate
     *            invoke
     * @param calleeMethod concrete candidate target method
     * @param receiverType receiver type for the candidate, or {@code null}
     * @param declaredCalleeMethod target method declared by the invoke before devirtualization
     * @return matched rule text, or {@code null} when no rule applies
     */
    public String findMatchingRuleOrPrefix(Callsite[] callsites, ResolvedJavaMethod calleeMethod, ResolvedJavaType receiverType, ResolvedJavaMethod declaredCalleeMethod) {
        ResolvedJavaMethod methodToMatch = declaredCalleeMethod == null ? calleeMethod : declaredCalleeMethod;
        return findRule(callsites, methodToMatch, calleeMethod, receiverType, true);
    }

    /**
     * Returns {@code true} if any rule matches the complete candidate call chain.
     *
     * @param callsites complete sequence of caller edges from the compilation root to the candidate
     *            invoke
     * @param calleeMethod concrete candidate target method
     * @param receiverType receiver type for the candidate, or {@code null}
     * @param declaredCalleeMethod target method declared by the invoke before devirtualization
     */
    public boolean matches(Callsite[] callsites, ResolvedJavaMethod calleeMethod, ResolvedJavaType receiverType, ResolvedJavaMethod declaredCalleeMethod) {
        return findMatchingRule(callsites, calleeMethod, receiverType, declaredCalleeMethod) != null;
    }

    /**
     * Returns {@code true} if any rule matches the complete candidate call chain or has the
     * candidate as the next required prefix edge.
     *
     * @param callsites complete sequence of caller edges from the compilation root to the candidate
     *            invoke
     * @param calleeMethod concrete candidate target method
     * @param receiverType receiver type for the candidate, or {@code null}
     * @param declaredCalleeMethod target method declared by the invoke before devirtualization
     */
    public boolean matchesOrPrefix(Callsite[] callsites, ResolvedJavaMethod calleeMethod, ResolvedJavaType receiverType, ResolvedJavaMethod declaredCalleeMethod) {
        return findMatchingRuleOrPrefix(callsites, calleeMethod, receiverType, declaredCalleeMethod) != null;
    }

    /**
     * Returns the caller method recorded on {@code invoke}, or {@code fallbackMethod} when no caller
     * state is attached yet.
     *
     * @param invoke invoke whose frame state may carry the logical caller
     * @param fallbackMethod method to use when the invoke has no attached caller state
     */
    public static ResolvedJavaMethod callerMethod(Invoke invoke, ResolvedJavaMethod fallbackMethod) {
        /*
         * Inlining runs before FrameStateAssignmentPhase. At this point high-level invokes carry
         * their caller state in stateAfter; stateDuring is assigned later for deoptimizing invokes.
         */
        FrameState stateAfter = invoke.stateAfter();
        return stateAfter != null && stateAfter.getMethod() != null ? stateAfter.getMethod() : fallbackMethod;
    }

    /**
     * Builds the directed-inlining callsite chain for an invoke that is still in the root graph.
     *
     * @param invoke invoke to describe
     * @param callerMethod logical caller method for {@code invoke}
     */
    public static Callsite[] rootGraphCallsites(Invoke invoke, ResolvedJavaMethod callerMethod) {
        ArrayList<Callsite> callsites = new ArrayList<>();
        FrameState stateAfter = invoke.stateAfter();
        FrameState frameState = stateAfter == null ? null : stateAfter.outerFrameState();
        while (frameState != null) {
            callsites.add(0, new Callsite(frameState.getMethod(), frameState.bci));
            frameState = frameState.outerFrameState();
        }
        callsites.add(new Callsite(callerMethod, invoke.bci()));
        return callsites.toArray(EMPTY_CALLSITES);
    }

    /**
     * Returns the BCI of the invoke in the compilation root that produced the current inlined graph.
     * This differs from {@link Invoke#bci()}, which is the BCI of the selected invoke in its immediate
     * caller.
     *
     * @param invoke invoke whose frame-state chain may identify the root invoke
     * @param inheritedRootInvokeBci root invoke BCI already inherited by the current inlined graph,
     *            or {@link #ANY_BCI} when unknown
     */
    public static int rootInvokeBci(Invoke invoke, int inheritedRootInvokeBci) {
        if (inheritedRootInvokeBci != ANY_BCI) {
            return inheritedRootInvokeBci;
        }
        int rootInvokeBci = invoke.bci();
        FrameState stateAfter = invoke.stateAfter();
        FrameState outerFrameState = stateAfter == null ? null : stateAfter.outerFrameState();
        while (outerFrameState != null) {
            rootInvokeBci = outerFrameState.bci;
            outerFrameState = outerFrameState.outerFrameState();
        }
        return rootInvokeBci;
    }

    /**
     * Returns {@code true} if any rule selects a specific receiver type from the declared callee
     * target.
     */
    public boolean hasReceiverTypeFilters() {
        for (Rule rule : rules) {
            if (rule.hasReceiverTypeFilters()) {
                return true;
            }
        }
        return false;
    }

    private static ArrayList<String> splitTopLevel(String value, char separator) {
        ArrayList<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (c == separator && depth == 0) {
                parts.add(value.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(value.substring(start));
        return parts;
    }

    private static ArrayList<String> splitTopLevel(String value, String separator) {
        ArrayList<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        int i = 0;
        while (i <= value.length() - separator.length()) {
            char c = value.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (depth == 0 && value.startsWith(separator, i)) {
                parts.add(value.substring(start, i));
                start = i + separator.length();
                i = start;
                continue;
            }
            i++;
        }
        parts.add(value.substring(start));
        return parts;
    }

    private static int findTopLevel(String value, String needle) {
        return findTopLevel(value, needle, 0);
    }

    private static int findTopLevel(String value, String needle, int startIndex) {
        int depth = 0;
        for (int i = startIndex; i <= value.length() - needle.length(); i++) {
            char c = value.charAt(i);
            if (depth == 0 && value.startsWith(needle, i)) {
                return i;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
        }
        return -1;
    }

    private static int findLastTopLevel(String value, char needle) {
        int depth = 0;
        for (int i = value.length() - 1; i >= 0; i--) {
            char c = value.charAt(i);
            if (c == ')') {
                depth++;
            } else if (c == '(') {
                depth--;
            } else if (c == '}') {
                depth++;
            } else if (c == '{') {
                depth--;
            } else if (c == needle && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static int findMatchingBrace(String value, int startIndex) {
        int depth = 0;
        for (int i = startIndex; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Appends a caller edge to an existing callsite chain.
     *
     * @param callsites existing callsite chain
     * @param callsite caller edge to append
     * @return a new callsite chain with the extra edge, or {@code callsites} when
     *         {@code callsite.method()} is {@code null}
     */
    public static Callsite[] append(Callsite[] callsites, Callsite callsite) {
        if (callsite.method() == null) {
            return callsites;
        }
        Callsite[] result = new Callsite[callsites.length + 1];
        System.arraycopy(callsites, 0, result, 0, callsites.length);
        result[callsites.length] = callsite;
        return result;
    }

    /**
     * A caller edge in a directed inlining chain. {@link #method()} is the method component visible
     * to the rule, which may be the declared target of the previous virtual/interface invoke.
     * {@link #concreteMethod()} is the method whose graph contains the outgoing invoke at
     * {@link #bci()}.
     */
    public record Callsite(ResolvedJavaMethod method, ResolvedJavaType receiverType, ResolvedJavaMethod concreteMethod, int bci) {
        /**
         * Creates a statically known caller edge.
         *
         * @param method method component visible to directed-rule matching
         * @param bci BCI of the outgoing invoke inside {@code method}
         */
        public Callsite(ResolvedJavaMethod method, int bci) {
            this(method, null, method, bci);
        }
    }

    /**
     * Parsed directed-inline and directed-dont-inline rules for one option set.
     */
    public record RuleSet(DirectedInliningRules inlineRules, DirectedInliningRules dontInlineRules) {
        public static final RuleSet EMPTY = new RuleSet(null, null);

        /**
         * Returns {@code true} if either directed-inline or directed-dont-inline rules are present.
         */
        public boolean hasRules() {
            return inlineRules != null || dontInlineRules != null;
        }

        /**
         * Returns {@code true} when the inline rules select {@code targetMethod} at {@code invoke},
         * or when they require it as the next prefix edge of a longer directed chain.
         *
         * @param invoke invoke being considered for inlining
         * @param targetMethod concrete candidate target method
         * @param receiverType receiver type for the candidate, or {@code null}
         */
        public boolean matchesInlineOrPrefix(Invoke invoke, ResolvedJavaMethod targetMethod, ResolvedJavaType receiverType) {
            if (inlineRules == null) {
                return false;
            }
            ResolvedJavaMethod actualCallerMethod = callerMethod(invoke, invoke.asNode().graph().method());
            return inlineRules.matchesOrPrefix(rootGraphCallsites(invoke, actualCallerMethod), targetMethod, receiverType, invoke.getTargetMethod());
        }

        /**
         * Returns {@code true} when the dont-inline rules select {@code targetMethod} at
         * {@code invoke}.
         *
         * @param invoke invoke being considered for inlining
         * @param targetMethod concrete candidate target method
         */
        public boolean matchesDontInline(Invoke invoke, ResolvedJavaMethod targetMethod) {
            if (dontInlineRules == null) {
                return false;
            }
            ResolvedJavaMethod actualCallerMethod = callerMethod(invoke, invoke.asNode().graph().method());
            return dontInlineRules.matches(rootGraphCallsites(invoke, actualCallerMethod), targetMethod, null, invoke.getTargetMethod());
        }
    }

    private record ParsedMethodSpec(String methodSpec, int bci) {
    }

    private record ParsedCalleeSpec(String methodSpec, ReceiverTypeFilter[] receiverTypes) {
    }

    private record ReceiverTypeFilter(String typeName) {
        private static final ReceiverTypeFilter[] EMPTY_ARRAY = new ReceiverTypeFilter[0];

        private boolean matches(ResolvedJavaType receiverType) {
            if (receiverType == null) {
                return false;
            }
            String receiverName = receiverType.toJavaName();
            if (typeName.indexOf('.') >= 0) {
                return typeName.equals(receiverName);
            }
            return receiverName.equals(typeName) || receiverName.endsWith("." + typeName) || receiverName.endsWith("$" + typeName);
        }
    }

    private record ComponentFilter(MethodFilter methodFilter, ReceiverTypeFilter[] receiverTypes) {
        private boolean matchesCallsite(Callsite callsite) {
            return callsite.method() != null &&
                            methodFilter.matches(callsite.method()) &&
                            matchesReceiverType(callsite.receiverType()) &&
                            matchesConcreteCallerRequirement(callsite.method(), callsite.concreteMethod());
        }

        private boolean matchesTarget(ResolvedJavaMethod method, ResolvedJavaType receiverType) {
            return method != null && methodFilter.matches(method) && matchesReceiverType(receiverType);
        }

        private boolean matchesIntermediateTarget(ResolvedJavaMethod method, ResolvedJavaMethod concreteMethod, ResolvedJavaType receiverType) {
            return matchesTarget(method, receiverType) && matchesConcreteCallerRequirement(method, concreteMethod);
        }

        private boolean matchesConcreteCallerRequirement(ResolvedJavaMethod method, ResolvedJavaMethod concreteMethod) {
            if (concreteMethod != null && method != null && !method.equals(concreteMethod)) {
                return receiverTypes.length == 1;
            }
            return true;
        }

        private boolean matchesReceiverType(ResolvedJavaType receiverType) {
            if (receiverTypes.length == 0) {
                return true;
            }
            for (ReceiverTypeFilter receiverTypeFilter : receiverTypes) {
                if (receiverTypeFilter.matches(receiverType)) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasReceiverTypeFilters() {
            return receiverTypes.length > 0;
        }

    }

    private record Rule(String ruleSpec, ComponentFilter[] callerFilters, int[] callerBcis,
                    ComponentFilter calleeFilter) {
        private boolean matchesDirectedInvoke(Callsite[] callsites, ResolvedJavaMethod declaredCalleeMethod, ResolvedJavaType receiverType) {
            return declaredCalleeMethod != null &&
                            callsites.length == callerFilters.length &&
                            matchesCallsites(callsites) &&
                            calleeFilter.matchesTarget(declaredCalleeMethod, receiverType);
        }

        private boolean matchesDirectedPrefix(Callsite[] callsites, ResolvedJavaMethod nextCallerMethod, ResolvedJavaMethod concreteNextCallerMethod, ResolvedJavaType receiverType) {
            return nextCallerMethod != null &&
                            callsites.length < callerFilters.length &&
                            matchesCallsites(callsites) &&
                            callerFilters[callsites.length].matchesIntermediateTarget(nextCallerMethod, concreteNextCallerMethod, receiverType);
        }

        private boolean matchesCallsites(Callsite[] callsites) {
            if (callsites.length > callerFilters.length) {
                return false;
            }
            for (int i = 0; i < callsites.length; i++) {
                Callsite callsite = callsites[i];
                if (!callerFilters[i].matchesCallsite(callsite) ||
                                (callerBcis[i] != ANY_BCI && callerBcis[i] != callsite.bci())) {
                    return false;
                }
            }
            return true;
        }

        private boolean hasReceiverTypeFilters() {
            if (calleeFilter.hasReceiverTypeFilters()) {
                return true;
            }
            for (ComponentFilter callerFilter : callerFilters) {
                if (callerFilter.hasReceiverTypeFilters()) {
                    return true;
                }
            }
            return false;
        }

    }
}
