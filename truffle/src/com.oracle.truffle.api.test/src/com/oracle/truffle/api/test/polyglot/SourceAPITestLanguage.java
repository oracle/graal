/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import java.util.function.Function;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.SourceAPITestLanguage.LanguageContext;

@TruffleLanguage.Registration(id = SourceAPITestLanguage.ID, name = SourceAPITestLanguage.ID, version = "1.0")
public class SourceAPITestLanguage extends TruffleLanguage<LanguageContext> {

    static final String ID = "SourceAPITestLanguage";

    static Function<Env, Object> runinside;
    static LanguageContext langContext;

    static class LanguageContext {

        Env env;

    }

    public static LanguageContext getContext() {
        return getCurrentContext(SourceAPITestLanguage.class);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        Object result = "null result";
        if (runinside != null) {
            try {
                result = runinside.apply(getContext().env);
            } finally {
                runinside = null;
            }
        }
        if (result == null) {
            result = "null result";
        }
        return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(result));
    }

    @Override
    protected LanguageContext createContext(Env env) {
        langContext = new LanguageContext();
        langContext.env = env;
        return langContext;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

}
