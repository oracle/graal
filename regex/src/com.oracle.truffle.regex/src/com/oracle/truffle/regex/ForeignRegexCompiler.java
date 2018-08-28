/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

/**
 * {@link ForeignRegexCompiler} wraps a {@link TruffleObject} that is compatible with
 * {@link RegexCompiler} and lets us use it as if it were an actual {@link RegexCompiler}.
 * 
 * @author Jirka Marsik <jiri.marsik@oracle.com>
 */
public class ForeignRegexCompiler extends RegexCompiler {

    private final TruffleObject foreignCompiler;

    private final Node executeNode = Message.EXECUTE.createNode();

    public ForeignRegexCompiler(TruffleObject foreignCompiler) {
        this.foreignCompiler = foreignCompiler;
    }

    /**
     * Wraps the supplied {@link TruffleObject} in a {@link ForeignRegexCompiler}, unless it already
     * is a {@link RegexCompiler}. Use this when accepting {@link RegexCompiler}s over Truffle
     * interop.
     */
    public static RegexCompiler importRegexCompiler(TruffleObject regexCompiler) {
        return regexCompiler instanceof RegexCompiler ? (RegexCompiler) regexCompiler : new ForeignRegexCompiler(regexCompiler);
    }

    @Override
    public TruffleObject compile(RegexSource source) throws RegexSyntaxException, UnsupportedRegexException {
        try {
            return (TruffleObject) ForeignAccess.sendExecute(executeNode, foreignCompiler, source.getPattern(), source.getFlags().toString());
        } catch (InteropException ex) {
            throw ex.raise();
        }
    }
}
