/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors;

import java.util.function.BiPredicate;

import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.nfa.QuantifierGuard;
import com.oracle.truffle.regex.tregex.nodes.nfa.TRegexBacktrackingNFAExecutorNode;
import com.oracle.truffle.regex.tregex.parser.CaseFoldData;
import com.oracle.truffle.regex.tregex.parser.JSRegexParser;
import com.oracle.truffle.regex.tregex.parser.MultiCharacterCaseFolding;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.RegexValidator;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.NFATraversalRegexASTVisitor;

/**
 * An implementation of the Ruby regex flavor.
 *
 * <p>
 * This implementation supports all Ruby regular expressions with the exception of the following
 * features:
 * </p>
 * <ul>
 * <li>\G escape sequence: In Ruby regular expressions, \G can be used to assert that we are still
 * at the initial index. TRegex only provides limited support for this feature by handling cases
 * when \G appears at the beginning of all top-level alternatives.</li>
 * <li>\K keep command: This command can be used in Ruby regular expressions to modify the matcher's
 * state so that it deletes any characters matched so far and considers the current position as the
 * start of the reported match. There is no operator like this in ECMAScript that would allow one to
 * tinker with the matcher's state.</li>
 * <li>Unicode character properties not supported by ECMAScript and not covered by the POSIX
 * character classes: Ruby regular expressions use the syntax \p{...} for Unicode character
 * properties. Similar to ECMAScript, they offer access to Unicode Scripts, General Categories and
 * some character properties. Ruby also allows access to character properties that refer to POSIX
 * character classes (e.g. \p{Alnum} for [[:alnum:]]). We support all of the above, including any
 * character properties specified by Ruby's documentation. However, Ruby regular expressions still
 * have access to extra Unicode character properties (e.g. Age) that we do not support. We could
 * dive through Ruby's implementation to find out which other properties might be used and try
 * providing them too.</li>
 * <li>recursive \g&lt;...&gt; subexpression calls and \k&lt;...+-x&gt; backreferences to other
 * levels</li>
 * <li>\X extended grapheme cluster escapes: This escape sequence expands into a complex expression
 * that references a lot of Unicode character sets that we currently do not have support for in
 * TRegex.</li>
 * <li>(?~...) absent expressions: These constructs can be used in Ruby regular expressions to match
 * strings that do not contain a match for a given expression. TRegex doesn't have support for this
 * kind of construction.</li>
 * </ul>
 *
 * <p>
 * However, there are subtle differences in how some fundamental constructs behave in ECMAScript
 * regular expressions and Ruby regular expressions. This concerns core concepts like loops and
 * capture groups and their interactions. These issues cannot be handled by transpiling alone and
 * they require extra care on the side of TRegex. The issues and the solutions are listed below.
 * </p>
 *
 * <ul>
 * <li>backreferences to unmatched capture groups should fail: In ECMAScript, when a backreference
 * is made to a capture group which hasn't been matched, such a backreference is ignored and
 * matching proceeds. If this happens in Ruby, the backreference will fail to match and the search
 * will stop and backtrack.
 * 
 * <pre>
 * Node.js (ECMAScript):
 * > /(?:(a)|(b))\1/.exec("b")
 * [ 'b', undefined, 'b', index: 0, input: 'b', groups: undefined ]
 *
 * MRI (Ruby):
 * irb(main):001:0> /(?:(a)|(b))\1/.match("b")
 * => nil
 * </pre>
 * 
 * This is solved in {@link TRegexBacktrackingNFAExecutorNode}, by the introduction of the
 * {@code backrefWithNullTargetSucceeds} field, which controls how backreferences to unmatched
 * capture groups are resolved. Also, in {@link JSRegexParser}, an optimization that drops forward
 * references and nested references from ECMAScript regular expressions is turned off for Ruby
 * regular expressions.</li>
 *
 * <li>re-entering a loop should not reset enclosed capture groups: In ECMAScript, when a group is
 * re-entered while looping, all of the capture groups contained within the looping group are reset.
 * On the other hand, in Ruby, their contents are preserved from one iteration of the loop to the
 * next. As we see in the example below, ECMAScript drops the contents of the {@code (a)} capture
 * group, while Ruby keeps it.
 * 
 * <pre>
 * Node.js (ECMAScript):
 * > /((a)|(b))+/.exec("ab")
 * [ 'ab', 'b', undefined, 'b', index: 0, input: 'ab', groups: undefined ]
 *
 * MRI (Ruby):
 * irb(main):001:0> /((a)|(b))+/.match("ab")
 * => #&lt;MatchData "ab" 1:"b" 2:"a" 3:"b"&gt;
 * </pre>
 * 
 * This is solved in {@link NFATraversalRegexASTVisitor}. The method {@code getGroupBoundaries} is
 * modified so that the instructions for clearing enclosed capture groups are omitted from generated
 * NFA transitions when processing Ruby regular expressions.</li>
 *
 * <li>loops should be repeated as long as the state of capture groups evolves: In ECMAScript, when
 * a loop matches the minimum required number of iterations, any further iterations are only matched
 * provided they consume some characters from the input. This is a measure intended to stop infinite
 * loops once they no longer consume any input. Ruby has a similar guard, but it admits extra
 * iterations if they either consume characters or change the state of capture groups. Thus it is
 * possible to have extra iterations that don't consume any characters but that store empty strings
 * as matches of capture groups. In the example below, ECMAScript executes the outer {@code ?} loop
 * zero times, since executing it once would consume no characters. As a result, the contents of
 * capture group 1 are null. On the other hand, Ruby executes the loop once, because the execution
 * modifies the contents of capture group 1 so that it contains the empty string.
 * 
 * <pre>
 * Node.js (ECMAScript):
 * > /(a*)? /.exec("")
 * [ '', undefined, index: 0, input: '', groups: undefined ]
 *
 * MRI (Ruby):
 * irb(main):001:1" /(a*)?/.match("")
 * => #&lt;MatchData "" 1:""&gt;
 * </pre>
 * 
 * This is solved by permitting one extra empty iteration of a loop when traversing the AST and
 * generating the NFA. In the absence of backreferences, an extra empty iteration is sufficient,
 * because any other iteration on top of that will retread the same path and have no further
 * effects. With backreferences (or more specifically, forward references), it is possible to create
 * situations where several empty iterations are required, sometimes even in the middle of a loop,
 * as in the example below.
 *
 * <pre>
 * irb(main):001:0> / (a|\2b|\3()|())* /x.match("aaabbb")
 * => #&lt;MatchData "aaabbb" 1:"" 2:"" 3:""&gt;
 * </pre>
 *
 * In {@link NFATraversalRegexASTVisitor}, we let NFA transitions pass through one empty iteration
 * of a loop ({@code extraEmptyLoopIterations} in {@code NFATraversalRegexASTVisitor#doAdvance}).
 * This generates an extra empty iteration at the end of loops and it also gives correct behavior on
 * constructions such as the one given above, as it lets us generate transitions that use an extra
 * empty iteration though the loop to populate some new capture group and then arrive at a new
 * backreference node. Since a single NFA transition can now correspond to more complex paths
 * through the AST, we also need to change the way we check the guards that the transitions are
 * annotated with by interleaving the state changes and assertions (see the use of
 * {@code TRegexBacktrackingNFAExecutorNode#transitionMatchesStepByStep}). We also need to implement
 * the empty check, by verifying the state of the capture groups on top of verifying the current
 * index (see {@code TRegexBacktrackingNFAExecutorNode#monitorCaptureGroupsInEmptyCheck}). For that,
 * we need fine-grained information about capture group updates and so we include this information
 * in the transition guards by {@link QuantifierGuard#createUpdateCG}.
 *
 * In unrolled loops, we disable empty checks altogether (in {@link JSRegexParser}, in the calls to
 * {@code RegexParser#createOptional}). This is correct since Ruby's empty checks terminate a loop
 * only when it reaches a fixed point w.r.t. to any observable state. Finally, also in
 * {@link JSRegexParser}. we also disable an optimization that drops zero-width groups and
 * lookaround assertions with optional quantifiers.</li>
 *
 * <li>failing the empty check should lead to matching the sequel of the quantified expression
 * instead of backtracking: In ECMAScript, when a loop fails the empty check (an iteration matches
 * only the empty string), the engine terminates the loop by rejecting this branch and backtracking
 * to another alternative (eventually backtracking to the point where it chooses not to re-enter the
 * loop and consider it finished). On the other hand, in Ruby, when a loop fails the empty check (an
 * iteration matches only the empty string and it does not modify the state of the capture groups),
 * the engine continues with the current branch by proceeding to the continuation of the loop. Most
 * notably, it doesn't try to backtrack and alter decisions made inside the loop until some future
 * failure forces it to. This can be illustrated on the following example, where ECMAScript will
 * backtrack into the loop and choose the second alternative, whereas Ruby will proceed with the
 * empty match.
 *
 * <pre>
 * Node.js (ECMAScript)
 * > /(?:|a)?/.exec('a')
 * [ 'a', index: 0, input: 'a', groups: undefined ]
 *
 * MRI (Ruby):
 * irb(main):001:0> /(?:|a)?/.match('a')
 * => #&lt;MatchData ""&gt;
 * </pre>
 *
 * We implement this in {@code NFATraversalRegexASTVisitor} by introducing two transitions whenever
 * we leave a loop, one leading to the start of the loop (empty check passes) and one escaping past
 * the loop (empty check fails). The two transitions are then annotated with complementary guards
 * ({@link QuantifierGuard#createEscapeZeroWidth} and {@link QuantifierGuard#createEscapeZeroWidth},
 * respectively), so that at runtime, only one of the two transitions will be admissible.</li>
 * </ul>
 */
public final class RubyFlavor extends RegexFlavor {

    public static final RubyFlavor INSTANCE = new RubyFlavor();

    private RubyFlavor() {
        super(BACKREFERENCES_TO_UNMATCHED_GROUPS_FAIL | EMPTY_CHECKS_MONITOR_CAPTURE_GROUPS | NESTED_CAPTURE_GROUPS_KEPT_ON_LOOP_REENTRY | FAILING_EMPTY_CHECKS_DONT_BACKTRACK |
                        HAS_CONDITIONAL_BACKREFERENCES | EMPTY_CHECKS_ON_MANDATORY_LOOP_ITERATIONS);
    }

    @Override
    public RegexValidator createValidator(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer) {
        return RubyRegexParser.createValidator(source);
    }

    @Override
    public RegexParser createParser(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer) {
        return RubyRegexParser.createParser(language, source, compilationBuffer);
    }

    @Override
    public BiPredicate<Integer, Integer> getEqualsIgnoreCasePredicate(RegexAST ast) {
        return RubyFlavor::equalsIgnoreCase;
    }

    private static boolean equalsIgnoreCase(int codePointA, int codePointB) {
        return MultiCharacterCaseFolding.equalsIgnoreCase(CaseFoldData.CaseFoldAlgorithm.Ruby, codePointA, codePointB);
    }
}
