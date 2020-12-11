/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.regex.tregex.TRegexCompiler;
import com.oracle.truffle.regex.tregex.parser.RegexValidator;
import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavor;
import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavorProcessor;
import com.oracle.truffle.regex.util.TruffleNull;

public final class GetRegexObjectNode extends RootNode {

    private static final FrameDescriptor SHARED_EMPTY_FRAMEDESCRIPTOR = new FrameDescriptor();

    private final RegexLanguage language;
    private final SourceSection sourceSection;
    private final RegexSource source;

    @CompilationFinal private Object regexObject;

    public GetRegexObjectNode(RegexLanguage language, Source truffleSource, RegexSource regexSource) {
        super(language, SHARED_EMPTY_FRAMEDESCRIPTOR);
        this.language = language;
        this.sourceSection = truffleSource.createSection(0, truffleSource.getLength());
        this.source = regexSource;
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (regexObject == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // Detect SyntaxErrors in regular expressions early.
            RegexFlavor flavor = source.getOptions().getFlavor();
            try {
                if (flavor != null) {
                    RegexFlavorProcessor flavorProcessor = flavor.forRegex(source);
                    flavorProcessor.validate();
                    if (!source.getOptions().isValidate()) {
                        regexObject = new RegexObject(TRegexCompiler.compile(language, source), source, flavorProcessor.getFlags(), flavorProcessor.getNumberOfCaptureGroups(),
                                        flavorProcessor.getNamedCaptureGroups());
                    }
                } else {
                    RegexValidator validator = new RegexValidator(source);
                    validator.validate();
                    if (!source.getOptions().isValidate()) {
                        regexObject = new RegexObject(TRegexCompiler.compile(language, source), source, RegexFlags.parseFlags(source.getFlags()), validator.getNumberOfCaptureGroups(),
                                        validator.getNamedCaptureGroups());
                    }
                }
                if (source.getOptions().isValidate()) {
                    regexObject = TruffleNull.INSTANCE;
                }
            } catch (UnsupportedRegexException e) {
                regexObject = TruffleNull.INSTANCE;
            }
        }
        return regexObject;
    }
}
