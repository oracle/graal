/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime.jfr.impl;

import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Description;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.runtime.jfr.RootFunctionEvent;
import com.oracle.truffle.api.nodes.RootNode;

abstract class RootFunctionEventImpl extends Event implements RootFunctionEvent {

    @Label("Id") @Description("Truffle Compilable Unique Id") public long id;
    @Label("Source") @Description("Compiled Source") public String source;
    @Label("Language") @Description("Guest Language") public String language;
    @Label("Root Function") @Description("Root Function") public String rootFunction;

    RootFunctionEventImpl() {
    }

    RootFunctionEventImpl(long id, String source, String language, String rootFunction) {
        this.id = id;
        this.source = source;
        this.language = language;
        this.rootFunction = rootFunction;
    }

    @Override
    public void setRootFunction(OptimizedCallTarget target) {
        this.id = target.id;
        RootNode rootNode = target.getRootNode();
        this.source = targetName(rootNode);
        LanguageInfo languageInfo = rootNode.getLanguageInfo();
        this.language = languageInfo != null ? languageInfo.getId() : null;
        this.rootFunction = rootNode.getName();
        if (this.rootFunction == null) {
            this.rootFunction = rootNode.toString();
        }
    }

    @Override
    public void publish() {
        commit();
    }

    private static String targetName(RootNode rootNode) {
        SourceSection sourceSection = rootNode.getSourceSection();
        if (sourceSection != null && sourceSection.getSource() != null) {
            return sourceSection.getSource().getName() + ":" + sourceSection.getStartLine();
        }
        return null;
    }
}
