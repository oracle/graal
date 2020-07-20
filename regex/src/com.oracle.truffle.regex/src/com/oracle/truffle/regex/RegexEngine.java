/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.regex.runtime.nodes.StringEqualsNode;
import com.oracle.truffle.regex.runtime.nodes.ToStringNode;
import com.oracle.truffle.regex.tregex.parser.RegexValidator;
import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavor;
import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavorProcessor;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;
import com.oracle.truffle.regex.util.TruffleReadOnlyKeysArray;

/**
 * {@link RegexEngine} is an executable {@link TruffleObject} that compiles regular expressions and
 * packages the results in {@link RegexObject}s. It takes the following arguments:
 * <ol>
 * <li>{@link String} {@code pattern}: the source of the regular expression to be compiled</li>
 * <li>{@link String} {@code flags} (optional): a textual representation of the flags to be passed
 * to the compiler (one letter per flag), see {@link RegexFlags} for the supported flags</li>
 * <li>{@link String} {@code encoding} (optional): the expected encoding of all strings given to the
 * resulting {@link RegexObject}. If no encoding is specified, it is determined from {@code flags}
 * in ECMAScript fashion: if the {@code 'u'}-flag is present, the encoding is expected to be UTF-16,
 * otherwise "raw" UTF-16, where no surrogate pairs are actually decoded, and every 16-bit value in
 * the string is treated as a code point. TRegex currently supports the following encodings:
 * <ul>
 * <li>UTF-8</li>
 * <li>UTF-16</li>
 * <li>UTF-16-RAW</li>
 * </ul>
 * </li>
 * </ol>
 * Executing the {@link RegexEngine} can also lead to the following exceptions:
 * <ul>
 * <li>{@link RegexSyntaxException}: if the input regular expression is malformed</li>
 * <li>{@link UnsupportedRegexException}: if the input regular expression cannot be compiled by this
 * engine</li>
 * </ul>
 * <p>
 * A {@link RegexEngine} can be obtained by executing the {@link RegexEngineBuilder}.
 */
@ExportLibrary(InteropLibrary.class)
public class RegexEngine extends AbstractConstantKeysObject {

    private static final String PROP_VALIDATE = "validate";
    private static final TruffleReadOnlyKeysArray KEYS = new TruffleReadOnlyKeysArray(PROP_VALIDATE);

    private final RegexCompiler compiler;
    private final RegexOptions options;

    public RegexEngine(RegexCompiler compiler, RegexOptions options) {
        this.compiler = compiler;
        this.options = options;
    }

    public RegexObject compile(RegexSource regexSource) throws RegexSyntaxException, UnsupportedRegexException {
        // Detect SyntaxErrors in regular expressions early.
        RegexFlavor flavor = options.getFlavor();
        RegexObject regexObject;
        if (flavor != null) {
            RegexFlavorProcessor flavorProcessor = flavor.forRegex(regexSource);
            flavorProcessor.validate();
            regexObject = new RegexObject(compiler, regexSource, flavorProcessor.getFlags(), flavorProcessor.getNumberOfCaptureGroups(), flavorProcessor.getNamedCaptureGroups());
        } else {
            RegexValidator validator = new RegexValidator(regexSource, options);
            validator.validate();
            options.getFeatureSet().checkSupport(regexSource, validator.getFeatures());
            regexObject = new RegexObject(compiler, regexSource, RegexFlags.parseFlags(regexSource.getFlags()), validator.getNumberOfCaptureGroups(), validator.getNamedCaptureGroups());
        }
        if (options.isRegressionTestMode()) {
            // Force the compilation of the RegExp.
            regexObject.getCompiledRegexObject();
        }
        return regexObject;
    }

    @Override
    public TruffleReadOnlyKeysArray getKeys() {
        return KEYS;
    }

    @Override
    public Object readMemberImpl(String symbol) throws UnknownIdentifierException {
        switch (symbol) {
            case PROP_VALIDATE:
                return new ValidateMethod(this);
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnknownIdentifierException.create(symbol);
        }
    }

    @ExportMessage
    public boolean isExecutable() {
        return true;
    }

    @ExportMessage
    Object execute(Object[] args,
                    @Shared("patternToStringNode") @Cached ToStringNode patternToStringNode,
                    @Shared("flagsToStringNode") @Cached ToStringNode flagsToStringNode,
                    @Shared("encodingToStringNode") @Cached ToStringNode encodingToStringNode) throws ArityException, UnsupportedTypeException {
        return compile(argsToRegexSource(args, patternToStringNode, flagsToStringNode, encodingToStringNode));
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isMemberInvocable(String member,
                    @Shared("isValidatePropNode") @Cached StringEqualsNode isValidatePropNode) {
        return isValidatePropNode.execute(member, PROP_VALIDATE);
    }

    @ExportMessage
    Object invokeMember(String member, Object[] args,
                    @Shared("isValidatePropNode") @Cached StringEqualsNode isValidatePropNode,
                    @Shared("patternToStringNode") @Cached ToStringNode patternToStringNode,
                    @Shared("flagsToStringNode") @Cached ToStringNode flagsToStringNode,
                    @Shared("encodingToStringNode") @Cached ToStringNode encodingToStringNode) throws UnknownIdentifierException, ArityException, UnsupportedTypeException {
        if (!isValidatePropNode.execute(member, PROP_VALIDATE)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnknownIdentifierException.create(member);
        }
        RegexValidator.validate(argsToRegexSource(args, patternToStringNode, flagsToStringNode, encodingToStringNode));
        return true;
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class ValidateMethod extends AbstractRegexObject {

        private final RegexEngine engine;

        private ValidateMethod(RegexEngine engine) {
            this.engine = engine;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        Object execute(Object[] args,
                        @Cached ToStringNode patternToStringNode,
                        @Cached ToStringNode flagsToStringNode,
                        @Cached ToStringNode encodingToStringNode) throws ArityException, UnsupportedTypeException {
            RegexValidator.validate(engine.argsToRegexSource(args, patternToStringNode, flagsToStringNode, encodingToStringNode));
            return true;
        }
    }

    private RegexSource argsToRegexSource(Object[] args, ToStringNode patternToStringNode, ToStringNode flagsToStringNode, ToStringNode encodingToStringNode)
                    throws ArityException, UnsupportedTypeException {
        if (args.length == 0 || args.length > 3) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw ArityException.create(3, args.length);
        }
        String pattern = patternToStringNode.execute(args[0]);
        String flags = args.length >= 2 ? flagsToStringNode.execute(args[1]) : "";
        Encoding encoding;
        if (args.length == 3) {
            encoding = Encodings.getEncoding(encodingToStringNode.execute(args[2]));
        } else {
            encoding = flags.indexOf('u') >= 0 && !options.isUTF16ExplodeAstralSymbols() ? Encodings.UTF_16 : Encodings.UTF_16_RAW;
        }
        return new RegexSource(pattern, flags, encoding);
    }
}
