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

@SuppressWarnings({"rawtypes", "unchecked"})
public final class MapTruffleObject implements TruffleObject {

    private final Map map;

    public MapTruffleObject(Map map) {
        this.map = map;
    }

    public ForeignAccess getForeignAccess() {
        return ForeignAccess.create(new MapForeignAccessFactory());
    }

    private static class MapForeignAccessFactory implements Factory {

        public boolean canHandle(TruffleObject obj) {
            return obj instanceof MapTruffleObject;
        }

        public CallTarget accessMessage(Message tree) {
            if (Message.IS_NULL.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            } else if (Message.IS_EXECUTABLE.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            } else if (Message.IS_BOXED.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(false));
            } else if (Message.HAS_SIZE.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true));
            } else if (Message.READ.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(new MapReadNode());
            } else if (Message.WRITE.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(new MapWriteNode());
            } else if (Message.GET_SIZE.equals(tree)) {
                return Truffle.getRuntime().createCallTarget(new MapSizeNode());
            } else {
                throw new IllegalArgumentException(tree.toString() + " not supported");
            }
        }
    }

    private static class MapWriteNode extends RootNode {
        protected MapWriteNode() {
            super(TckLanguage.class, null, null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            MapTruffleObject map = (MapTruffleObject) ForeignAccess.getReceiver(frame);
            Object key = ForeignAccess.getArguments(frame).get(0);
            Object value = ForeignAccess.getArguments(frame).get(1);
            map.map.put(key, value);
            return value;
        }
    }

    private static class MapReadNode extends RootNode {
        protected MapReadNode() {
            super(TckLanguage.class, null, null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            MapTruffleObject map = (MapTruffleObject) ForeignAccess.getReceiver(frame);
            Object key = ForeignAccess.getArguments(frame).get(0);
            return map.map.get(key);
        }

    }

    private static class MapSizeNode extends RootNode {
        protected MapSizeNode() {
            super(TckLanguage.class, null, null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            MapTruffleObject map = (MapTruffleObject) ForeignAccess.getReceiver(frame);
            return map.map.size();
        }

    }

}
