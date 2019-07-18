package com.oracle.truffle.st.test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.st.Coverage;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.st.SimpleCoverageInstrument;

public class SimpleCoverageInstrumentTest {

    private final String JS_SOURCE = "var N = 2000;\n" +
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
        Assume.assumeTrue(Context.create().getEngine().getLanguages().containsKey("js"));
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
    private void assertJSCorrect(final Context context) {
        // We can lookup services exported by the instrument, in our case this is
        // the instrument itself but it does not have to be.
        SimpleCoverageInstrument coverageInstrument = context.getEngine().getInstruments().get(SimpleCoverageInstrument.ID).lookup(SimpleCoverageInstrument.class);
        // We then use the looked up service to assert that it behaves as expected, just like in any
        // other test.
        Map<com.oracle.truffle.api.source.Source, Coverage> coverageMap = coverageInstrument.getCoverageMap();
        Assert.assertEquals(1, coverageMap.size());
        coverageMap.forEach((com.oracle.truffle.api.source.Source s, Coverage v) -> {
            List<Integer> notYetCoveredLineNumbers = coverageInstrument.notYetCoveredLineNumbers(s);
            Object[] expected = new Integer[]{47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 61, 67};
            Assert.assertArrayEquals(expected, notYetCoveredLineNumbers.toArray());
        });
    }

    private final String SL_SOURCE = "\n" +
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
        Assume.assumeTrue(Context.create().getEngine().getLanguages().containsKey("sl"));
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
                List<Integer> notYetCoveredLineNumbers = coverageInstrument.notYetCoveredLineNumbers(s);
                Object[] expected = new Integer[]{3, 4, 5};
                Assert.assertArrayEquals(expected, notYetCoveredLineNumbers.toArray());
            });
        }
    }
}
