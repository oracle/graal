/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.test.dummylang;

import java.util.ArrayList;
import java.util.Iterator;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.SandboxPolicy;

import com.oracle.truffle.api.TruffleOptionDescriptors;
import com.oracle.truffle.regex.RegexOptions;

/**
 * To be removed once TRegex is a non-internal language (GR-65841).
 */
final class TRegexTestDummyLangOptionDescriptors implements TruffleOptionDescriptors {

    private final OptionDescriptors tregexOptionDescriptors = RegexOptions.getDescriptors();
    private final TRegexTestDummyLanguageOptionsOptionDescriptors ownOptions = new TRegexTestDummyLanguageOptionsOptionDescriptors();

    @Override
    public OptionDescriptor get(String optionName) {
        OptionDescriptor o = tregexOptionDescriptors.get(optionName.replace("regexDummyLang", "regex"));
        if (o != null) {
            return copyDescriptor(optionName, o);
        }
        return ownOptions.get(optionName);
    }

    @Override
    public SandboxPolicy getSandboxPolicy(String optionName) {
        assert get(optionName) != null : "Unknown option " + optionName;
        return SandboxPolicy.TRUSTED;
    }

    @Override
    public Iterator<OptionDescriptor> iterator() {
        ArrayList<OptionDescriptor> tregexOptions = new java.util.ArrayList<>();
        for (OptionDescriptor o : tregexOptionDescriptors) {
            tregexOptions.add(copyDescriptor(o.getName().replace("regex", "regexDummyLang"), o));
        }
        for (OptionDescriptor ownOption : ownOptions) {
            tregexOptions.add(ownOption);
        }
        return tregexOptions.iterator();
    }

    private static OptionDescriptor copyDescriptor(String optionName, OptionDescriptor o) {
        return OptionDescriptor.newBuilder(o.getKey(), optionName).deprecated(o.isDeprecated()).help(o.getHelp()).usageSyntax(o.getUsageSyntax()).category(o.getCategory()).stability(
                        o.getStability()).build();
    }
}
