/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.logging.Level;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags.ReadVariableTag;
import com.oracle.truffle.api.instrumentation.StandardTags.WriteVariableTag;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public final class InteropUtils {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    private InteropUtils() {
        assert false;
    }

    public static Integer getNumberOfArguments(Object nodeObject, TruffleLogger logger) {
        if (nodeObject instanceof TruffleObject && INTEROP.isMemberReadable(nodeObject, "numberOfArguments")) {
            try {
                Object object = INTEROP.readMember(nodeObject, "numberOfArguments");
                return object instanceof Number ? ((Number) object).intValue() : null;
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t) {
                logger.log(Level.INFO, nodeObject.toString(), t);
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

    public static VariableInfo[] getNodeObjectVariables(InstrumentableNode node) {
        Object nodeObject = node.getNodeObject();
        if (nodeObject == null) {
            return VariableInfo.EMPTY;
        }
        try {
            if (node.hasTag(ReadVariableTag.class) && INTEROP.isMemberReadable(nodeObject, ReadVariableTag.NAME)) {
                return getNodeObjectVariables(node, INTEROP.readMember(nodeObject, ReadVariableTag.NAME));
            }
            if (node.hasTag(WriteVariableTag.class) && INTEROP.isMemberReadable(nodeObject, WriteVariableTag.NAME)) {
                return getNodeObjectVariables(node, INTEROP.readMember(nodeObject, WriteVariableTag.NAME));
            }
        } catch (ThreadDeath td) {
            throw td;
        } catch (InteropException ex) {
            throw new AssertionError("Unexpected interop exception", ex);
        }
        return VariableInfo.EMPTY;
    }

    private static VariableInfo[] getNodeObjectVariables(InstrumentableNode node, Object namesObject) throws UnsupportedMessageException, InvalidArrayIndexException {
        if (INTEROP.hasArrayElements(namesObject)) {
            int size = (int) INTEROP.getArraySize(namesObject);
            VariableInfo[] infos = new VariableInfo[size];
            for (int i = 0; i < size; i++) {
                Object element = INTEROP.readArrayElement(namesObject, i);
                infos[i] = new VariableInfo(node, element);
            }
            return infos;
        } else {
            return new VariableInfo[]{new VariableInfo(node, namesObject)};
        }
    }

    public static final class VariableInfo {

        private static final VariableInfo[] EMPTY = new VariableInfo[0];

        private final InstrumentableNode node;
        private final Object nameObject;
        private final String name;

        VariableInfo(InstrumentableNode node, Object nameObject) throws UnsupportedMessageException {
            this.node = node;
            this.nameObject = nameObject;
            assert INTEROP.isString(nameObject);
            this.name = INTEROP.asString(nameObject);
        }

        public String getName() {
            return name;
        }

        public SourceSection getSourceSection() {
            if (INTEROP.hasSourceLocation(nameObject)) {
                try {
                    return INTEROP.getSourceLocation(nameObject);
                } catch (UnsupportedMessageException ex) {
                }
            }
            return ((Node) node).getSourceSection();
        }
    }

}
