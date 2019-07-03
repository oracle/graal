# TRegex

TRegex is an implementation of a subset of ECMAScript regular expressions that uses the GraalVM compiler and Truffle API to execute regular expressions in an efficient way.
Its role is to provide support for Truffle languages that need to expose regular expression functionality.
In its current iteration, TRegex provides an implementation of ECMAScript regular expressions (ECMAScript regular expressions are based on the widely popular Perl 5 regular expressions).
A distinguishing feature of TRegex is that it compiles regular expressions into finite-state automata.
This means that the performance of searching for a match is predictable (linear to the size of the input).
On the other hand, some features of ECMAScript regular expressions (notably backreferences and complex lookbehind assertions) are not supported and users of TRegex must supply a fallback implementation of regular expressions to handle such cases.


## Overview

Unlike most regular expression engines which use backtracking, TRegex uses an automaton-based approach.
The regex is parsed and then translated into a nondeterministic finite-state automaton (NFA).
A powerset construction is then used to expand the NFA into a deterministic finite-state automaton (DFA).
The resulting DFA is then executed when matching against input strings.
At that point, TRegex exploits the GraalVM compiler and Truffle to get efficient machine code when interpreting the DFA.

The benefit of using this approach is that finding out whether a match is found can be done during a single pass over the input string: whenever several alternative ways to match the remaining input are admissible, TRegex considers all of them simultaneously.
This is in contrast to backtracking approaches which consider all possible alternatives separately, one after the other.
This can lead to up to exponential execution times in specific adversarial cases (https://swtch.com/~rsc/regexp/regexp1.html).
Since TRegex adopts the automaton-based approach, the runtime of the matching procedure is consistent and predictable.
However, the downside of the automaton-based approach is that it cannot cover some of the features which are now commonly supported by regular expression engines (e.g., backreferences, such as in `/([a-z]+)=\1/`, or lookbehind, such as in `/(?<=a.+)b/`).
For this reason, TRegex only handles a subset of ECMAScript regular expressions and the rest must be handled by a fallback (backtracking) engine.

TRegex originated as part of the Graal JavaScript implementation, but is now standalone so implementers of other languages can use it to have a fast implementation of ECMAScript-like regular expressions.


## Using TRegex

The API of TRegex is accessible through Truffle's interop mechanisms.
The `TruffleObject`s and AST nodes introduced by TRegex are grouped under a new Truffle language, [RegexLanguage](./src/com.oracle.truffle.regex/src/com/oracle/truffle/regex/RegexLanguage.java).
The javadoc of [RegexLanguage](./src/com.oracle.truffle.regex/src/com/oracle/truffle/regex/RegexLanguage.java) contains pointers on how to use the TRegex API.

For an example of how to integrate TRegex into a language implementation, you can check out Graal JavaScript.
Setting up the engine is done in [JSContext#getRegexEngine](https://github.com/graalvm/graaljs/blob/master/graal-js/src/com.oracle.truffle.js.runtime/src/com/oracle/truffle/js/runtime/JSContext.java), compilation requests to the engine are sent from [RegexCompilerInterface#compile](https://github.com/graalvm/graaljs/blob/master/graal-js/src/com.oracle.truffle.js.runtime/src/com/oracle/truffle/js/runtime/RegexCompilerInterface.java) and the compiled regular expression is executed in [JSRegExpExecBuiltinNode](https://github.com/graalvm/graaljs/blob/master/graal-js/src/com.oracle.truffle.js.builtins/src/com/oracle/truffle/js/builtins/helper/JSRegExpExecIntlNode.java).
The [TRegexUtil](https://github.com/graalvm/graaljs/blob/master/graal-js/src/com.oracle.truffle.js.runtime/src/com/oracle/truffle/js/runtime/util/TRegexUtil.java) class also shows how to write interop access wrappers for the methods and properties of the `TruffleLanguage` objects.

## Feature Support

TRegex supports ECMAScript regular expressions as described in the ECMAScript 2018 specification, with the exception of some omissions, which are listed in the table below:

Feature                                                                                  | TRegex
---------------------------------------------------------------------------------------- | ------
Backreferences                                                                           | ❌
Negative lookaround<sup>[1](#fn1)</sup>                                                  | ❌
[Full lookbehind](https://github.com/tc39/proposal-regexp-lookbehind)<sup>[2](#f2)</sup> | ❌

<sub>
<a name="fn1">1</a>: Positive lookaround is supported in TRegex.
<br/>
<a name="fn2">2</a>: TRegex only supports lookbehind assertions that consist of literal characters or character classes.
</sub>

<br/>
<br/>

We are currently working on implementing negative lookahead and more support for lookbehind in TRegex.
On the other hand, full support of backreferences is out of scope for a finite-state automaton engine like TRegex.


## License

TRegex is licensed under the [GPL 2 with Classpath exception](./LICENSE.GPL.md).
