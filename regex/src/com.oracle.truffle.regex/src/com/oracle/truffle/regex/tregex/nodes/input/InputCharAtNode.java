/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes.input;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.tregex.util.ForeignAccessUtil;

@ImportStatic(ForeignAccessUtil.class)
public abstract class InputCharAtNode extends Node {

    public static InputCharAtNode create() {
        return InputCharAtNodeGen.create();
    }

    public abstract char execute(Object input, int index);

    @Specialization
    public char doCharAt(String input, int index) {
        return input.charAt(index);
    }

    @Specialization
    public char doCharAt(TruffleObject input, int index, @Cached("createReadMessageNode()") Node readNode) {
        try {
            Object c = ForeignAccess.sendRead(readNode, input, index);
            if (c instanceof Character) {
                return (char) c;
            }
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedTypeException.raise(new Object[]{c});
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new RuntimeException(e);
        }
    }
}
