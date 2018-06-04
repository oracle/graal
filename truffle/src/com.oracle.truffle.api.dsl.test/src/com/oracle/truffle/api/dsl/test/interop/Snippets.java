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

import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;

public class Snippets {

//@formatter:off
public static class ExampleTruffleObject implements TruffleObject {
    static final String MEMBER_NAME = "value";

    private int value = 0;

    void setValue(int value) {
        this.value = value;
    }

    int getValue() {
        return value;
    }

    // BEGIN: com.oracle.truffle.api.dsl.test.interop.Snippets.ExampleTruffleObject#getForeignAccessMethod
    public ForeignAccess getForeignAccess() {
        return ExampleTruffleObjectMRForeign.ACCESS;
    }

    // END: com.oracle.truffle.api.dsl.test.interop.Snippets.ExampleTruffleObject#getForeignAccessMethod

    // BEGIN: com.oracle.truffle.api.dsl.test.interop.Snippets.ExampleTruffleObject#isInstanceCheck
    public static boolean isInstance(TruffleObject obj) {
        return obj instanceof ExampleTruffleObject;
    }
    // END: com.oracle.truffle.api.dsl.test.interop.Snippets.ExampleTruffleObject#isInstanceCheck
}

// BEGIN: com.oracle.truffle.api.dsl.test.interop.Snippets.ExampleTruffleObjectMR
@MessageResolution(receiverType = ExampleTruffleObject.class)
public static class ExampleTruffleObjectMR {

     @Resolve(message = "READ")
     public abstract static class ExampleReadNode extends Node {

         protected Object access(ExampleTruffleObject receiver,
                                 String name) {
             if (ExampleTruffleObject.MEMBER_NAME.equals(name)) {
                 return receiver.getValue();
             }
             throw UnknownIdentifierException.raise(name);
         }
     }

    @Resolve(message = "WRITE")
    public abstract static class ExampleWriteNode extends Node {

        protected static int access(ExampleTruffleObject receiver,
                                    String name, int value) {
            if (ExampleTruffleObject.MEMBER_NAME.equals(name)) {
                receiver.setValue(value);
                return value;
            }
            throw UnknownIdentifierException.raise(name);
        }
    }

    @CanResolve
    public abstract static class Check extends Node {

        protected static boolean test(TruffleObject receiver) {
            return receiver instanceof ExampleTruffleObject;
        }
    }

}
// END: com.oracle.truffle.api.dsl.test.interop.Snippets.ExampleTruffleObjectMR

public static class RethrowExample {
    private Object identifier;
    private TruffleObject receiver;
    private Node readNode;

    public void foo() {
        // BEGIN: com.oracle.truffle.api.dsl.test.interop.Snippets.RethrowExample
        try {
            ForeignAccess.sendRead(readNode, receiver, identifier);
        } catch (InteropException ex) {
            throw ex.raise();
        }
        // END: com.oracle.truffle.api.dsl.test.interop.Snippets.RethrowExample
    }
}

//@formatter:on

}
