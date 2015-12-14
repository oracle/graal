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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.ForeignAccess.Factory;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;

final class StructuredDataEntry implements TruffleObject {

    private final byte[] buffer;
    private final Schema schema;
    private final int index;

    StructuredDataEntry(byte[] buffer, Schema schema, int index) {
        this.buffer = buffer;
        this.schema = schema;
        this.index = index;
    }

    public ForeignAccess getForeignAccess() {
        return ForeignAccess.create(new StructuredDataEntryForeignAccessFactory());
    }

    private static class StructuredDataEntryForeignAccessFactory implements Factory {

        public boolean canHandle(TruffleObject obj) {
            return obj instanceof StructuredDataEntry;
        }

        public CallTarget accessMessage(Message tree) {
            // for simplicity: this StructuredData is read-only
            if (Message.IS_NULL.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            } else if (Message.IS_EXECUTABLE.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            } else if (Message.IS_BOXED.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            } else if (Message.HAS_SIZE.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            } else if (Message.READ.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(new StructuredDataEntryReadNode());
            } else {
                throw new IllegalArgumentException(tree.toString() + " not supported");
            }
        }
    }

    private static class StructuredDataEntryReadNode extends RootNode {
        protected StructuredDataEntryReadNode() {
            super(TckLanguage.class, null, null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            StructuredDataEntry data = (StructuredDataEntry) ForeignAccess.getReceiver(frame);
            String name = TckLanguage.expectString(ForeignAccess.getArguments(frame).get(0));
            return data.schema.get(data.buffer, data.index, name);
        }

    }
}
