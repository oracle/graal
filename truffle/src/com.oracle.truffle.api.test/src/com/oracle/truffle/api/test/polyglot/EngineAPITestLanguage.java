/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.EngineAPITestLanguage.LanguageContext;

@TruffleLanguage.Registration(id = EngineAPITestLanguage.ID, implementationName = EngineAPITestLanguage.IMPL_NAME, name = EngineAPITestLanguage.NAME, version = EngineAPITestLanguage.VERSION, characterMimeTypes = EngineAPITestLanguage.MIME)
public class EngineAPITestLanguage extends TruffleLanguage<LanguageContext> {

    static EngineAPITestLanguage.LanguageContext langContext;

    static final String ID = "EngineAPITestLanguage";
    static final String NAME = "Name";
    static final String IMPL_NAME = "ImplName";
    static final String VERSION = "Version";
    static final String MIME = "text/mime";

    static final String Option1_HELP = "Option1_HELP";
    static final boolean Option1_DEPRECATED = false;
    static final OptionCategory Option1_CATEGORY = OptionCategory.USER;
    static final String Option1_NAME = EngineAPITestLanguage.ID + ".Option1";
    static final String Option1_DEFAULT = EngineAPITestLanguage.ID + "Option1_Default";

    static final String Option2_HELP = "Option2_HELP";
    static final boolean Option2_DEPRECATED = true;
    static final OptionCategory Option2_CATEGORY = OptionCategory.EXPERT;
    static final String Option2_NAME = EngineAPITestLanguage.ID + "";
    static final String Option2_DEFAULT = EngineAPITestLanguage.ID + "Option2_Default";

    static final String Option3_HELP = "Option2_HELP";
    static final boolean Option3_DEPRECATED = true;
    static final OptionCategory Option3_CATEGORY = OptionCategory.INTERNAL;
    static final String Option3_NAME = EngineAPITestLanguage.ID + ".Option3";
    static final String Option3_DEFAULT = EngineAPITestLanguage.ID + "Option3_Default";

    @Option(category = OptionCategory.USER, help = Option1_HELP, deprecated = Option1_DEPRECATED) //
    static final OptionKey<String> Option1 = new OptionKey<>(Option1_DEFAULT);

    @Option(category = OptionCategory.EXPERT, name = "", help = Option2_HELP, deprecated = Option2_DEPRECATED) //
    static final OptionKey<String> Option2 = new OptionKey<>(Option2_DEFAULT);

    @Option(category = OptionCategory.INTERNAL, help = Option3_HELP, deprecated = Option3_DEPRECATED) //
    static final OptionKey<String> Option3 = new OptionKey<>(Option3_DEFAULT);

    static class LanguageContext {
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new EngineAPITestLanguageOptionDescriptors();
    }

    public static LanguageContext getContext() {
        return getCurrentContext(EngineAPITestLanguage.class);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
    }

    @Override
    protected LanguageContext createContext(Env env) {
        langContext = new LanguageContext();
        return langContext;
    }

    @Override
    protected void disposeContext(LanguageContext context) {

    }

}
