/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.st.test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.st.Coverage;
import com.oracle.truffle.st.SimpleCoverageInstrument;

public class SimpleCoverageInstrumentTest {

    private static final String JS_SOURCE = "var N = 2000;\n" +
                    "var EXPECTED = 17393;\n" +
                    "\n" +
                    "function Natural() {\n" +
                    "    x = 2;\n" +
                    "    return {\n" +
                    "        'next' : function() { return x++; }\n" +
                    "    };\n" +
                    "}\n" +
                    "\n" +
                    "function Filter(number, filter) {\n" +
                    "    var self = this;\n" +
                    "    this.number = number;\n" +
                    "    this.filter = filter;\n" +
                    "    this.accept = function(n) {\n" +
                    "      var filter = self;\n" +
                    "      for (;;) {\n" +
                    "          if (n % filter.number === 0) {\n" +
                    "              return false;\n" +
                    "          }\n" +
                    "          filter = filter.filter;\n" +
                    "          if (filter === null) {\n" +
                    "              break;\n" +
                    "          }\n" +
                    "      }\n" +
                    "      return true;\n" +
                    "    };\n" +
                    "    return this;\n" +
                    "}\n" +
                    "\n" +
                    "function Primes(natural) {\n" +
                    "    var self = this;\n" +
                    "    this.natural = natural;\n" +
                    "    this.filter = null;\n" +
                    "    this.next = function() {\n" +
                    "        for (;;) {\n" +
                    "            var n = self.natural.next();\n" +
                    "            if (self.filter === null || self.filter.accept(n)) {\n" +
                    "                self.filter = new Filter(n, self.filter);\n" +
                    "                return n;\n" +
                    "            }\n" +
                    "        }\n" +
                    "    };\n" +
                    "}\n" +
                    "\n" +
                    "var holdsAFunctionThatIsNeverCalled = function(natural) {\n" +
                    "    var self = this;\n" +
                    "    this.natural = natural;\n" +
                    "    this.filter = null;\n" +
                    "    this.next = function() {\n" +
                    "        for (;;) {\n" +
                    "            var n = self.natural.next();\n" +
                    "            if (self.filter === null || self.filter.accept(n)) {\n" +
                    "                self.filter = new Filter(n, self.filter);\n" +
                    "                return n;\n" +
                    "            }\n" +
                    "        }\n" +
                    "    };\n" +
                    "}\n" +
                    "\n" +
                    "var holdsAFunctionThatIsNeverCalledOneLine = function() {return null;}\n" +
                    "\n" +
                    "function primesMain() {\n" +
                    "    var primes = new Primes(Natural());\n" +
                    "    var primArray = [];\n" +
                    "    for (var i=0;i<=N;i++) { primArray.push(primes.next()); }\n" +
                    "    if (primArray[N] != EXPECTED) { throw new Error('wrong prime found: ' + primArray[N]); }\n" +
                    "}\n" +
                    "primesMain();\n";

    @Test
    public void exampleJSTest() throws IOException {
        // This test only makes sense if JS is available.
        Assume.assumeTrue(Engine.create().getLanguages().containsKey("js"));
        // This is how we can create a context with our tool enabled if we are embeddined in java
        try (Context context = Context.newBuilder("js").option(SimpleCoverageInstrument.ID, "true").option(SimpleCoverageInstrument.ID + ".PrintCoverage", "false").build()) {
            Source source = Source.newBuilder("js", JS_SOURCE, "main").build();
            context.eval(source);
            assertJSCorrect(context);
        }
    }

    // NOTE: This lookup mechanism used in this method does not work in normal deployments
    // due to Truffles class path issolation. Services can be looked up by other
    // instruments, but not by the embedder. We can do this in the tests because the
    // class path issolation is disabled in the pom.xml file by adding -XX:-UseJVMCIClassLoader to
    // the command line.
    // This command line flag should never be used in production.
    private static void assertJSCorrect(final Context context) {
        // We can lookup services exported by the instrument, in our case this is
        // the instrument itself but it does not have to be.
        SimpleCoverageInstrument coverageInstrument = context.getEngine().getInstruments().get(SimpleCoverageInstrument.ID).lookup(SimpleCoverageInstrument.class);
        // We then use the looked up service to assert that it behaves as expected, just like in any
        // other test.
        Map<com.oracle.truffle.api.source.Source, Coverage> coverageMap = coverageInstrument.getCoverageMap();
        Assert.assertEquals(1, coverageMap.size());
        coverageMap.forEach((com.oracle.truffle.api.source.Source s, Coverage v) -> {
            Set<Integer> notYetCoveredLineNumbers = coverageInstrument.nonCoveredLineNumbers(s);
            Object[] expected = new Integer[]{47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 61, 67};
            Assert.assertArrayEquals(expected, notYetCoveredLineNumbers.stream().sorted().toArray());
        });
    }

    private static final String SL_SOURCE = "\n" +
                    "function neverCalled() {\n" +
                    "    x = 5;\n" +
                    "    y = 9;\n" +
                    "   return x + 5;\n" +
                    "}\n" +
                    "function isCalled() {\n" +
                    "   return 5 + 5;\n" +
                    "}\n" +
                    "function main() { \n" +
                    "   10 + isCalled(); \n" +
                    "}\n";

    // Similar test using simple language
    @Test
    public void exampleSLTest() throws IOException {
        // This test only makes sense if SL is available.
        Assume.assumeTrue(Engine.create().getLanguages().containsKey("sl"));
        // This is how we can create a context with our tool enabled if we are embeddined in java
        try (Context context = Context.newBuilder("sl").option(SimpleCoverageInstrument.ID, "true").option(SimpleCoverageInstrument.ID + ".PrintCoverage", "false").build()) {
            Source source = Source.newBuilder("sl", SL_SOURCE, "main").build();
            context.eval(source);
            // We can lookup services exported by the instrument, in our case this is
            // the instrument itself but it does not have to be.
            SimpleCoverageInstrument coverageInstrument = context.getEngine().getInstruments().get(SimpleCoverageInstrument.ID).lookup(SimpleCoverageInstrument.class);
            // We then use the looked up service to assert that it behaves as expected, just like in
            // any other test.
            Map<com.oracle.truffle.api.source.Source, Coverage> coverageMap = coverageInstrument.getCoverageMap();
            Assert.assertEquals(1, coverageMap.size());
            coverageMap.forEach((com.oracle.truffle.api.source.Source s, Coverage v) -> {
                Set<Integer> notYetCoveredLineNumbers = coverageInstrument.nonCoveredLineNumbers(s);
                Object[] expected = new Integer[]{3, 4, 5};
                Assert.assertArrayEquals(expected, notYetCoveredLineNumbers.stream().sorted().toArray());
            });
        }
    }
}
