/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.polybench.micro;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;

import java.io.IOException;
import java.nio.charset.Charset;

import static com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import static com.oracle.truffle.api.TruffleLanguage.Env;
import static com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.Node;

@Registration(id = "pmh", name = "PolyglotMicrobenchmarkHarness", interactive = false, contextPolicy = ContextPolicy.SHARED, //
                characterMimeTypes = {"application/pmh"}, fileTypeDetectors = MicrobenchLanguage.Detector.class)
public class MicrobenchLanguage extends TruffleLanguage<Env> {

    public static class Detector implements TruffleFile.FileTypeDetector {

        @Override
        public String findMimeType(TruffleFile file) throws IOException {
            if (file.getName().endsWith(".pmh")) {
                return "application/pmh";
            } else {
                return null;
            }
        }

        @Override
        public Charset findEncoding(TruffleFile file) throws IOException {
            return Charset.forName("UTF-8");
        }
    }

    private static final LanguageReference<MicrobenchLanguage> REFERENCE = LanguageReference.create(MicrobenchLanguage.class);
    private static final ContextReference<Env> CTXREF = ContextReference.create(MicrobenchLanguage.class);

    private final Assumption singleContextAssumption = Truffle.getRuntime().createAssumption("PMH single context");

    public static MicrobenchLanguage get(Node node) {
        return REFERENCE.get(node);
    }

    public static Env getContext(Node node) {
        return CTXREF.get(node);
    }

    public Assumption getSingleContextAssumption() {
        return singleContextAssumption;
    }

    @Override
    protected Env createContext(Env env) {
        return env;
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        return Parser.doParse(this, request.getSource());
    }

    @Override
    protected void initializeMultipleContexts() {
        super.initializeMultipleContexts();
        singleContextAssumption.invalidate();
    }
}
