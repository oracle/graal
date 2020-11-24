/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.RegexSource;

/**
 * An implementation of the Ruby regex flavor.
 *
 * This implementation supports translating all Ruby regular expressions to ECMAScript regular
 * expressions with the exception of the following features:
 * <ul>
 * <li>case-insensitive matching: Ruby regular expression allow both case-sensitive matching and
 * case-insensitive matching within the same regular expression. Also, Ruby's notion of
 * case-insensitivity differs from the one in ECMAScript. For that reason, we would have to
 * translate all Ruby regular expressions to case-sensitive ECMAScript regular expressions and we
 * would support case-insensitivity by case-folding any character matchers in the Ruby regular
 * expression. However, Ruby has a more sophisticated notion of case-insensitivity than ECMAScript,
 * which can lead to, e.g., two characters such as "ss" matching a single character such as
 * "&#xDF;", meaning there is no longer a 1-to-1 correspondence between character matchers. In order
 * to support this, we would have to replicate the same case-folding behaviorin the Ruby flavor
 * implementation.</li>
 * <li>case-insensitive backreferences: As stated above, case-insensitive matching has to be
 * implemented by case-folding. However, there is no way we can case-fold a backreference, since we
 * don't know which string it will match.</li>
 * <li>\G escape sequence: In Ruby regular expressions, \G can be used to assert that we are at some
 * special position that was marked by a previous execution of the regular expression on that input.
 * ECMAScript doesn't support assertions which check the current index against some reference
 * value.</li>
 * <li>\K keep command: This command can be used in Ruby regular expressions to modify the matcher's
 * state so that it deletes any characters matched so far and considers the current position as the
 * start of the reported match. There is no operator like this in ECMAScript that would allow one to
 * tinker with the matcher's state.</li>
 * <li>named capture groups with the same name: Ruby admits regular expressions with named capture
 * groups that share the same name. These situations can't be handled by replacing those capture
 * groups with regular numbered capture groups and then mapping the capture group names to lists of
 * capture group indices as we wouldn't know which of the homonymous capture groups was matched last
 * and therefore which value should be used.</li>
 * <li>Unicode character properties not supported by ECMAScript and not covered by the POSIX
 * character classes: Ruby regular expressions use the syntax \p{...} for Unicode character
 * properties. Similar to ECMAScript, they offer access to Unicode Scripts, General Categories and
 * some character properties. Ruby also allows access to character properties that refer to POSIX
 * character classes (e.g. \p{Alnum} for [[:alnum:]]). We support all of the above, including any
 * character properties specified by Ruby's documentation. However, Ruby regular expressions still
 * have access to extra Unicode character properties (e.g. Age) that we do not support. We could
 * dive through Ruby's implementation to find out which other properties might be used and try
 * providing them too.</li>
 * <li>\g&lt;...&gt; subexpression calls and \k&lt;...+-x&gt; backreferences to other levels: Ruby
 * allows recursive calls into subexpressions of the regular expression. There is nothing like this
 * in ECMAScript or in the TRegex engine. Furthermore, Ruby allows backreferences to access captured
 * groups on different levels (of the call stack), so as we don't support subexpression calls, we
 * also don't support those backreferences.</li>
 * <li>(?>....) atomic groups: This construct allows control over the matcher's backtracking by
 * making committed choices which can't be undone. This is not something we can support using
 * ECMAScript regexes.</li>
 * <li>\X extended grapheme cluster escapes: This is just syntactic sugar for a certain expression
 * which uses atomic groups, and it is therefore not supported.</li>
 * <li>\R line break escapes: These are also translated by Joni to atomic groups, which we do not
 * support.</li>
 * <li>possessive quantifiers, e.g. a*+: Possessive quantifiers are quantifiers which consume
 * greedily and also do not allow backtracking, so they are another example of the atomic groups
 * that we do not support (a*+ is equivalent to (?>a*)).</li>
 * <li>(?~...) absent expressions: These constructs can be used in Ruby regular expressions to match
 * strings that do not contain a match for a given expression. ECMAScript doesn't offer a similar
 * operation.</li>
 * <li>quantifiers on lookaround assertions: We translate the Ruby regular expressions to
 * Unicode-mode ECMAScript regular expressions. Among other reasons, this lets us assume that a
 * single character matcher will match a single Unicode code point, not just a UTF-16 code unit, as
 * would be the case in non-Unicode ECMAScript regular expressions. Unicode-mode ECMAScript regular
 * expressions do not allow quantifiers on lookaround assertions, as they rarely make any sense. One
 * would hope to implement this by dropping any lookaround assertions that have a quantifier on them
 * that makes them optional. However, this is not correct as the lookaround assertion might contain
 * capture groups and thus have visible side effects.</li>
 * <li>conditional backreferences (?(group)then|else): There is no counterpart to this in ECMAScript
 * regular expressions.</li>
 * </ul>
 */
public final class RubyFlavor implements RegexFlavor {

    public static final RubyFlavor INSTANCE = new RubyFlavor();

    private RubyFlavor() {
    }

    @Override
    public RegexFlavorProcessor forRegex(RegexSource source) {
        return new RubyFlavorProcessor(source);
    }

}
