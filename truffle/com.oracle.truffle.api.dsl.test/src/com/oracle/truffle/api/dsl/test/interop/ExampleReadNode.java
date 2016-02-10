/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;
import com.oracle.truffle.api.interop.UnknownIdentifierException;

//@formatter:off
// BEGIN: AcceptMessageExample
@AcceptMessage(value = "READ",
receiverType = ExampleTruffleObject.class,
language = TestTruffleLanguage.class)
public final class ExampleReadNode extends ExampleReadBaseNode {

    @Override
    protected Object access(VirtualFrame frame,
                    ExampleTruffleObject receiver,
                    String name) {
        if (ExampleTruffleObject.MEMBER_NAME.equals(name)) {
            return receiver.getValue();
        }
        throw UnknownIdentifierException.raise(name);
    }
}
// END: AcceptMessageExample
//@formatter:on
