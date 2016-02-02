/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.sl.nodes.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.nodes.access.SLWritePropertyCacheNode;
import com.oracle.truffle.sl.nodes.access.SLWritePropertyCacheNodeGen;
import com.oracle.truffle.sl.runtime.SLObjectType;

@AcceptMessage(value = "WRITE", receiverType = SLObjectType.class, language = SLLanguage.class)
public final class SLForeignWriteNode extends SLWriteBaseNode {

    @Child private SLMonomorphicNameWriteNode write;

    @Override
    public Object access(VirtualFrame frame, DynamicObject receiver, String name, Object value) {
        if (write == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            write = insert(new SLMonomorphicNameWriteNode(name));
        }
        return write.execute(frame, receiver, name, value);
    }

    private abstract static class SLWriteNode extends Node {
        @Child protected SLForeignToSLTypeNode toSLType = SLForeignToSLTypeNodeGen.create(getSourceSection(), null);

        abstract Object execute(VirtualFrame frame, DynamicObject receiver, String name, Object value);
    }

    private static final class SLMonomorphicNameWriteNode extends SLWriteNode {

        private final String cachedName;
        @Child private SLWritePropertyCacheNode writePropertyCacheNode;

        SLMonomorphicNameWriteNode(String name) {
            this.cachedName = name;
            this.writePropertyCacheNode = SLWritePropertyCacheNodeGen.create(name);
        }

        @Override
        Object execute(VirtualFrame frame, DynamicObject receiver, String name, Object value) {
            if (this.cachedName.equals(name)) {
                Object convertedValue = toSLType.executeWithTarget(frame, value);
                writePropertyCacheNode.executeObject(receiver, convertedValue);
                return receiver;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return this.replace(new SLPolymorphicNameWriteNode()).execute(frame, receiver, name, value);
            }
        }
    }

    private static final class SLPolymorphicNameWriteNode extends SLWriteNode {

        @Override
        Object execute(VirtualFrame frame, DynamicObject receiver, String name, Object value) {
            Object convertedValue = toSLType.executeWithTarget(frame, value);
            Property property = receiver.getShape().getProperty(name);
            return receiver.set(property.getKey(), convertedValue);
        }
    }
}
