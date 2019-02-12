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
package org.graalvm.tools.lsp.server.utils;

import java.util.Map;

import org.graalvm.tools.lsp.hacks.LanguageSpecificHacks;
import org.graalvm.tools.lsp.interop.ObjectStructures;
import org.graalvm.tools.lsp.interop.ObjectStructures.MessageNodes;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags.DeclarationTag;
import com.oracle.truffle.api.interop.TruffleObject;

public final class InteropUtils {

    private InteropUtils() {
        assert false;
    }

    public static String getNormalizedSymbolName(Object nodeObject, String symbol, MessageNodes messageNodes) {
        if (!(nodeObject instanceof TruffleObject)) {
            return LanguageSpecificHacks.normalizeSymbol(symbol);
        } else {
            Map<Object, Object> map = ObjectStructures.asMap((TruffleObject) nodeObject, messageNodes);
            if (map.containsKey(DeclarationTag.NAME)) {
                return map.get(DeclarationTag.NAME).toString();
            }
        }
        return symbol;
    }

    public static Integer getNumberOfArguments(Object nodeObject, MessageNodes messageNodes) {
        if (nodeObject instanceof TruffleObject) {
            Map<Object, Object> map = ObjectStructures.asMap((TruffleObject) nodeObject, messageNodes);
            if (map.containsKey("numberOfArguments")) {
                Object object = map.get("numberOfArguments");
                return object instanceof Integer ? (Integer) object : null;
            }
        }
        return null;
    }

    public static boolean isPrimitive(Object object) {
        Class<?> clazz = object.getClass();
        return (clazz == Byte.class ||
                        clazz == Short.class ||
                        clazz == Integer.class ||
                        clazz == Long.class ||
                        clazz == Float.class ||
                        clazz == Double.class ||
                        clazz == Character.class ||
                        clazz == Boolean.class ||
                        clazz == String.class);
    }

    public static String getNodeObjectName(InstrumentableNode node, MessageNodes messageNodes) {
        Object nodeObject = node.getNodeObject();
        if (nodeObject instanceof TruffleObject) {
            Map<Object, Object> map = ObjectStructures.asMap((TruffleObject) nodeObject, messageNodes);
            if (map.containsKey("name")) {
                return map.get("name").toString();
            }
        }
        return null;
    }
}
