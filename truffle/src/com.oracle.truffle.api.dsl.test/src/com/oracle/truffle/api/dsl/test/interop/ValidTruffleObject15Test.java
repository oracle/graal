/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test.interop;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;

public class ValidTruffleObject15Test {

    @Test(expected = UnsupportedSpecializationException.class)
    public void expectUnsupportedSpecializationException() {
        ValidTruffleObject15 object = new ValidTruffleObject15();
        Node read = Message.WRITE.createNode();
        try {
            ForeignAccess.sendWrite(read, object, "name", new UnknownObject());
        } catch (UnknownIdentifierException e) {
            Assert.fail();
        } catch (UnsupportedMessageException e) {
            Assert.fail();
        } catch (UnsupportedTypeException e) {
            Assert.fail();
        }
    }

    @Test(expected = UnsupportedTypeException.class)
    public void expectTypeError() throws UnknownIdentifierException, UnsupportedTypeException, UnsupportedMessageException {
        ValidTruffleObject15 object = new ValidTruffleObject15();
        Node read = Message.WRITE.createNode();
        ForeignAccess.sendWrite(read, object, new UnknownObject(), new UnknownObject());
    }

    private static final class UnknownObject implements TruffleObject {

        @Override
        public ForeignAccess getForeignAccess() {
            return null;
        }

    }
}
