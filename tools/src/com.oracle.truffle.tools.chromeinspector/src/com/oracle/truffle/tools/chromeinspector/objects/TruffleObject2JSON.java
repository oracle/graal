/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.objects;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

/**
 * Converts TruffleObject to JSONObject.
 */
final class TruffleObject2JSON {

    private TruffleObject2JSON() {
    }

    static JSONObject fromObject(TruffleObject object) {
        JSONObject json = new JSONObject();
        TruffleObject keys;
        try {
            keys = ForeignAccess.sendKeys(Message.KEYS.createNode(), object);
        } catch (UnsupportedMessageException ex) {
            return json;
        }
        int size;
        try {
            size = ((Number) ForeignAccess.sendGetSize(Message.GET_SIZE.createNode(), keys)).intValue();
        } catch (UnsupportedMessageException ex) {
            return json;
        }
        if (size > 0) {
            Node nodeHasSize = Message.HAS_SIZE.createNode();
            Node nodeRead = Message.READ.createNode();
            for (int i = 0; i < size; i++) {
                try {
                    Object key = ForeignAccess.sendRead(nodeRead, keys, i);
                    Object value = ForeignAccess.sendRead(nodeRead, object, key);
                    json.put(key.toString(), from(value, nodeHasSize));
                } catch (UnknownIdentifierException | UnsupportedMessageException ex) {
                    // ignore that key
                }
            }
        }
        return json;
    }

    static JSONArray fromArray(TruffleObject array) {
        JSONArray json = new JSONArray();
        int size;
        try {
            size = ((Number) ForeignAccess.sendGetSize(Message.GET_SIZE.createNode(), array)).intValue();
        } catch (UnsupportedMessageException ex) {
            return json;
        }
        if (size > 0) {
            Node nodeHasSize = Message.HAS_SIZE.createNode();
            Node nodeRead = Message.READ.createNode();
            for (int i = 0; i < size; i++) {
                try {
                    Object value = ForeignAccess.sendRead(nodeRead, array, i);
                    json.put(i, from(value, nodeHasSize));
                } catch (UnknownIdentifierException | UnsupportedMessageException ex) {
                    // ignore that element
                    break;
                }
            }
        }
        return json;
    }

    private static Object from(Object object, Node nodeHasSize) {
        if (object instanceof TruffleObject) {
            TruffleObject truffleObject = (TruffleObject) object;
            if (ForeignAccess.sendHasSize(nodeHasSize, truffleObject)) {
                return fromArray(truffleObject);
            } else {
                return fromObject(truffleObject);
            }
        } else {
            return object;
        }
    }
}
