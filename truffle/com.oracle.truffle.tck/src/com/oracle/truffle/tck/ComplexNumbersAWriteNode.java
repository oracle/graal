/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.tck.impl.TckLanguage;

@AcceptMessage(value = "WRITE", receiverType = ComplexNumbersA.class, language = TckLanguage.class)
final class ComplexNumbersAWriteNode extends ComplexNumbersAWriteBaseNode {
    @Child private Node readReal;
    @Child private Node readImag;

    @Override
    public Object access(VirtualFrame frame, ComplexNumbersA complexNumbers, Number index, TruffleObject value) {
        if (readReal == null || readImag == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            this.readReal = insert(Message.READ.createNode());
            this.readImag = insert(Message.READ.createNode());
        }
        try {
            Number realPart = TckLanguage.expectNumber(ForeignAccess.sendRead(readReal, frame, value, new Object[]{ComplexNumber.REAL_IDENTIFIER}));
            Number imagPart = TckLanguage.expectNumber(ForeignAccess.sendRead(readImag, frame, value, new Object[]{ComplexNumber.IMAGINARY_IDENTIFIER}));

            int idx = TckLanguage.checkBounds(index.intValue(), complexNumbers.getData().length / 2);

            complexNumbers.getData()[idx * 2] = realPart.doubleValue();
            complexNumbers.getData()[idx * 2 + 1] = imagPart.doubleValue();
            return value;
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            return null;
        }
    }

}
