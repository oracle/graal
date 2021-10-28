# TRegex - Truffle Regular Expression Language

This Truffle language represents classic regular expressions. It treats a given regular expression as a "program" that
you can execute to obtain a regular expression matcher object, which in turn can be used to perform regex searches via
Truffle interop.

The expected syntax is `options/regex/flags`, where `options` is a comma-separated list of key-value pairs which affect
how the regex is interpreted, and `/regex/flags` is equivalent to the popular regular expression literal format found in
e.g. JavaScript or Ruby.

### Parsing

When parsing a regular expression, TRegex will return a Truffle CallTarget, which, when called, will yield one of the
following results:

* a (Truffle) null value, indicating that TRegex cannot handle the given regex.
* a "compiled regex" (`RegexObject`) object, which can be used to match the given regex.
* a Truffle `PARSE_ERROR` exception may be thrown to indicate a syntax error.

An example of how to parse a regular expression:

```java
Source source = Source.newBuilder("regex", "Flavor=ECMAScript/(a|(b))c/i", "myRegex").mimeType("application/tregex").internal(true).build();
Object regex;
try {
    regex = getContext().getEnv().parseInternal(source).call();
} catch (AbstractTruffleException e) {
    if (InteropLibrary.getUncached().getExceptionType(e) == ExceptionType.PARSE_ERROR) {
        // handle parser error
    } else {
        // fatal error, this should never happen
    }
}
if (InteropLibrary.getUncached().isNull(regex)) {
    // regex is not supported by TRegex, fall back to a different regex engine
}
```

### The compiled regex object

A `RegexObject` represents a compiled regular expression that can be used to match against input strings. It exposes the
following three properties:

* `pattern`: the source string of the compiled regular expression.
* `flags`: an object representing the set of flags passed to the regular expression compiler, depending on the flavor of
  regular expressions used.
* `groupCount`: the number of capture groups present in the regular expression, including group 0.
* `groups`: a map of all named capture groups to their respective group number, or a null value if the expression does
  not contain named capture groups.
* `exec`: an executable method that matches the compiled regular expression against a string. The method accepts two
  parameters:
    * `input`: the character sequence to search in. This may either be a Java String, or a Truffle Object that behaves
      like a `char`-array.
    * `fromIndex`: the position to start searching from.
    * The return value is a `RegexResult` object.

### The result object

A `RegexResult` object represents the result of matching a regular expression against a string. It can be obtained as
the result of a `RegexObject`'s
`exec`-method and has the following properties:

* `boolean isMatch`: `true` if a match was found, `false` otherwise.
* `int getStart(int groupNumber)`: returns the position where the beginning of the capture group with the given number
  was found. If the result is no match, the returned value is undefined. Capture group number `0` denotes the boundaries
  of the entire expression. If no match was found for a particular capture group, the returned value is `-1`.
* `int getEnd(int groupNumber)`: returns the position where the end of the capture group with the given number was
  found. If the result is no match, the returned value is undefined. Capture group number `0` denotes the boundaries of
  the entire expression. If no match was found for a particular capture group, the returned value is `-1`.

Compiled regex usage example in pseudocode:

```java
regex = <matcher from previous example>
assert(regex.pattern == "(a|(b))c")
assert(regex.flags.ignoreCase == true)
assert(regex.groupCount == 3)

result = regex.exec("xacy", 0)
assert(result.isMatch == true)
assertEquals([result.getStart(0), result.getEnd(0)], [ 1,  3])
assertEquals([result.getStart(1), result.getEnd(1)], [ 1,  2])
assertEquals([result.getStart(2), result.getEnd(2)], [-1, -1])

result2 = regex.exec("xxx", 0)
assert(result2.isMatch == false)
// result2.getStart(...) and result2.getEnd(...) are undefined

```

### Available options

These options define how TRegex should interpret a given regular expression:

#### User options
* `Flavor`: specifies the regex dialect to use. Possible values:
  * `ECMAScript`: ECMAScript/JavaScript syntax (default).
  * `Python`: Python 3 syntax.
  * `Ruby`: Ruby syntax.
* `Encoding`: specifies the string encoding to match against. Possible values:
  * `UTF-8`
  * `UTF-16` (default)
  * `UTF-32`
  * `LATIN-1`
  * `BYTES` (equivalent to `LATIN-1`)
* `Validate`: don't generate a regex matcher object, just check the regex for syntax errors.
* `U180EWhitespace`: treat `0x180E MONGOLIAN VOWEL SEPARATOR` as part of `\s`. This is a legacy feature for languages
  using a Unicode standard older than 6.3, such as ECMAScript 6 and older.

#### Performance tuning options
* `UTF16ExplodeAstralSymbols`: generate one DFA states per (16 bit) `char` instead of per-codepoint. This may
  improve performance in certain scenarios, but increases the likelihood of DFA state explosion.
* `AlwaysEager`: do not generate any lazy regex matchers (lazy in the sense that they may lazily compute properties of a
  {@link RegexResult}).

#### Debugging options
* `RegressionTestMode`: exercise all supported regex matcher variants, and check if they produce the same results.
* `DumpAutomata`: dump all generated parser trees, NFA, and DFA to disk. This will generate debugging dumps of most
  relevant data structures in JSON, GraphViz and LaTex format.
* `StepExecution`: dump tracing information about all DFA matcher runs.

All options except `Flavor` and `Encoding` are boolean and `false` by default.
