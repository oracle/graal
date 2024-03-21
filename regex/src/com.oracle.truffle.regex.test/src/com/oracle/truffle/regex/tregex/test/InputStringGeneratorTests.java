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
package com.oracle.truffle.regex.tregex.test;

import java.util.Random;

import com.oracle.truffle.api.strings.TruffleString;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.analysis.InputStringGenerator;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.string.Encodings;

public class InputStringGeneratorTests extends RegexTestBase {

    private final RegexLanguage language = new RegexLanguage();
    private final Random rng = new Random(1234);

    @Override
    String getEngineOptions() {
        return "";
    }

    @Override
    Encodings.Encoding getTRegexEncoding() {
        return Encodings.UTF_16_RAW;
    }

    @Test
    public void testBenchmarkRegexes() {
        testInputStringGenerator("(((\\w+):\\/\\/)([^\\/:]*)(:(\\d+))?)?([^#?]*)(\\?([^#]*))?(#(.*))?");
        testInputStringGenerator("([aeiouAEIOU]+)");
        testInputStringGenerator("((([1-3][0-9])|[1-9])\\/((1[0-2])|0?[1-9])\\/[0-9]{4})|((([1-3][0-9])|[1-9])-((1[0-2])|0?[1-9])-[0-9]{4})|((([1-3][0-9])|[1-9])\\.((1[0-2])|0?[1-9])\\.[0-9]{4})");
        testInputStringGenerator("((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)");
        testInputStringGenerator("([A-Fa-f0-9]{1,4}:){7}[A-Fa-f0-9]{1,4}");
        testInputStringGenerator("([A-Fa-f0-9]{1,4}:){6}(([A-Fa-f0-9]{1,4}:[A-Fa-f0-9]{1,4})|(((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])))");
        testInputStringGenerator(
                        "([-!#-''*+/-9=?A-Z^-~]+(\\.[-!#-''*+/-9=?A-Z^-~]+)*|\"([ ]!#-[^-~ ]|(\\\\[-~ ]))+\")@[0-9A-Za-z]([0-9A-Za-z-]{0,61}[0-9A-Za-z])?(\\.[0-9A-Za-z]([0-9A-Za-z-]{0,61}[0-9A-Za-z])?)+");
        testInputStringGenerator(
                        "([-!#-''*+/-9=?A-Z^-~]+(\\.[-!#-''*+/-9=?A-Z^-~]+)*|\"([ ]!#-[^-~ ]|(\\\\[-~ ]))+\")@[0-9A-Za-z]([0-9A-Za-z-]*[0-9A-Za-z])?(\\.[0-9A-Za-z]([0-9A-Za-z-]*[0-9A-Za-z])?)+");
        testInputStringGenerator("(\\S+) (\\S+) (\\S+) \\[([A-Za-z0-9_:/]+\\s[-+]\\d{4})\\] \"(\\S+)\\s?(\\S+)?\\s?(\\S+)?\" (\\d{3}|-) (\\d+|-)\\s?\"?([^\"]*)\"?\\s?\"?([^\"]*)?\"?");
    }

    private TruffleString generateInputString(String pattern, String flags, String options, Encodings.Encoding encoding) {
        String sourceString = createSourceString(pattern, flags, options, encoding);
        Source source = Source.newBuilder("regex", sourceString, "regexSource").build();
        RegexSource regexSource = RegexLanguage.createRegexSource(source);
        RegexAST ast = regexSource.getOptions().getFlavor().createParser(language, regexSource, new CompilationBuffer(regexSource.getEncoding())).parse();
        return InputStringGenerator.generate(ast, rng.nextLong());
    }

    void testInputStringGenerator(String pattern) {
        testInputStringGenerator(pattern, "", getEngineOptions(), getTRegexEncoding());
    }

    void testInputStringGenerator(String pattern, String flags, String options, Encodings.Encoding encoding) {
        Value compiledRegex = compileRegex(pattern, flags);
        testInputStringGenerator(pattern, flags, options, encoding, compiledRegex);
    }

    private void testInputStringGenerator(String pattern, String flags, String options, Encodings.Encoding encoding, Value compiledRegex) {
        for (int i = 0; i < 20; i++) {
            TruffleString input = generateInputString(pattern, flags, options, encoding);
            Assert.assertNotNull(input);
            Value result = execRegex(compiledRegex, encoding, input, 0);
            Assert.assertTrue(result.getMember("isMatch").asBoolean());
        }
    }
}
