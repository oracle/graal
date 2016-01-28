/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.tck.impl.TckLanguage;

@AcceptMessage(value = "INVOKE", receiverType = ComplexNumber.class, language = TckLanguage.class)
final class ComplexInvokeNode extends ComplexBaseInvokeNode {

    private static final String ADD = "add";
    private static final String SUB = "sub";

    @Override
    public Object access(VirtualFrame frame, ComplexNumber complex, String identifier, Object[] arguments) {
        if (arguments.length != 1) {
            throw ArityException.raise(1, arguments.length);
        }
        if (!(arguments[0] instanceof ComplexNumber)) {
            throw UnsupportedTypeException.raise(arguments);
        }
        ComplexNumber a = complex;
        ComplexNumber b = (ComplexNumber) arguments[0];

        switch (identifier) {
            case ADD:
                return add(a, b);
            case SUB:
                return sub(a, b);
            default:
                throw UnknownIdentifierException.raise(identifier);
        }
    }

    private static ComplexNumber add(ComplexNumber a, ComplexNumber b) {
        a.set(ComplexNumber.REAL_IDENTIFIER, a.get(ComplexNumber.REAL_IDENTIFIER) + b.get(ComplexNumber.REAL_IDENTIFIER));
        a.set(ComplexNumber.IMAGINARY_IDENTIFIER, a.get(ComplexNumber.IMAGINARY_IDENTIFIER) + b.get(ComplexNumber.IMAGINARY_IDENTIFIER));
        return a;
    }

    private static ComplexNumber sub(ComplexNumber a, ComplexNumber b) {
        a.set(ComplexNumber.REAL_IDENTIFIER, a.get(ComplexNumber.REAL_IDENTIFIER) - b.get(ComplexNumber.REAL_IDENTIFIER));
        a.set(ComplexNumber.IMAGINARY_IDENTIFIER, a.get(ComplexNumber.IMAGINARY_IDENTIFIER) - b.get(ComplexNumber.IMAGINARY_IDENTIFIER));
        return a;
    }
}
