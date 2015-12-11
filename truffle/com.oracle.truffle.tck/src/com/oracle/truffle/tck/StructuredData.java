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

import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.ForeignAccess.Factory;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;

public final class StructuredData implements TruffleObject {

    private final byte[] buffer;
    private final Schema schema;

    public StructuredData(byte[] buffer, Schema schema) {
        this.buffer = buffer;
        this.schema = schema;
    }

    public Map<String, Object> getEntry(int index) {
        return schema.getEntry(buffer, index);
    }

    public ForeignAccess getForeignAccess() {
        return ForeignAccess.create(new StructuredDataForeignAccessFactory());
    }

    private static class StructuredDataForeignAccessFactory implements Factory {

        public boolean canHandle(TruffleObject obj) {
            return obj instanceof StructuredData;
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
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true));
            } else if (Message.READ.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(new StructuredDataReadNode());
            } else if (Message.GET_SIZE.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(new StructuredDataSizeNode());
            } else {
                throw new IllegalArgumentException(tree.toString() + " not supported");
            }
        }
    }

    private static class StructuredDataReadNode extends RootNode {
        protected StructuredDataReadNode() {
            super(TckLanguage.class, null, null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            StructuredData data = (StructuredData) ForeignAccess.getReceiver(frame);
            Number index = (Number) ForeignAccess.getArguments(frame).get(0);
            return new MapTruffleObject(data.getEntry(index.intValue()));
        }

    }

    private static class StructuredDataSizeNode extends RootNode {
        protected StructuredDataSizeNode() {
            super(TckLanguage.class, null, null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            StructuredData data = (StructuredData) ForeignAccess.getReceiver(frame);
            return data.schema.length();
        }

    }
}