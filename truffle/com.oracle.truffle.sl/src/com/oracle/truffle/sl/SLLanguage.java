/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.sl.nodes.SLEvalRootNode;
import com.oracle.truffle.sl.nodes.SLRootNode;
import com.oracle.truffle.sl.parser.Parser;
import com.oracle.truffle.sl.runtime.SLContext;
import com.oracle.truffle.sl.runtime.SLFunction;

@TruffleLanguage.Registration(name = "SL", version = "0.12", mimeType = SLLanguage.MIME_TYPE)
@ProvidedTags({StandardTags.CallTag.class, StandardTags.StatementTag.class, StandardTags.RootTag.class, DebuggerTags.AlwaysHalt.class})
public final class SLLanguage extends TruffleLanguage<SLContext> {

    public static final String MIME_TYPE = "application/x-sl";

    /**
     * The singleton instance of the language.
     */
    public static final SLLanguage INSTANCE = new SLLanguage();

    /**
     * No instances allowed apart from the {@link #INSTANCE singleton instance}.
     */
    private SLLanguage() {
    }

    @Override
    protected SLContext createContext(Env env) {
        BufferedReader in = new BufferedReader(new InputStreamReader(env.in()));
        PrintWriter out = new PrintWriter(env.out(), true);
        return new SLContext(env, in, out);
    }

    @Override
    protected CallTarget parse(Source source, Node node, String... argumentNames) throws IOException {
        Map<String, SLRootNode> functions;
        try {
            /*
             * Parse the provided source. At this point, we do not have a SLContext yet.
             * Registration of the functions with the SLContext happens lazily in SLEvalRootNode.
             */
            functions = Parser.parseSL(source);
        } catch (Throwable ex) {
            /*
             * The specification says that exceptions during parsing have to wrapped with an
             * IOException.
             */
            throw new IOException(ex);
        }

        SLRootNode main = functions.get("main");
        SLRootNode evalMain;
        if (main != null) {
            /*
             * We have a main function, so "evaluating" the parsed source means invoking that main
             * function. However, we need to lazily register functions into the SLContext first, so
             * we cannot use the original SLRootNode for the main function. Instead, we create a new
             * SLEvalRootNode that does everything we need.
             */
            evalMain = new SLEvalRootNode(main.getFrameDescriptor(), main.getBodyNode(), main.getSourceSection(), main.getName(), functions);
        } else {
            /*
             * Even without a main function, "evaluating" the parsed source needs to register the
             * functions into the SLContext.
             */
            evalMain = new SLEvalRootNode(null, null, null, "[no_main]", functions);
        }
        return Truffle.getRuntime().createCallTarget(evalMain);
    }

    @Override
    protected Object findExportedSymbol(SLContext context, String globalName, boolean onlyExplicit) {
        return context.getFunctionRegistry().lookup(globalName, false);
    }

    @Override
    protected Object getLanguageGlobal(SLContext context) {
        /*
         * The context itself is the global function registry. SL does not have global variables.
         */
        return context;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return object instanceof SLFunction;
    }

    @Override
    protected Object evalInContext(Source source, Node node, MaterializedFrame mFrame) throws IOException {
        throw new IllegalStateException("evalInContext not supported in SL");
    }

    public SLContext findContext() {
        CompilerAsserts.neverPartOfCompilation();
        return super.findContext(super.createFindContextNode());
    }
}
