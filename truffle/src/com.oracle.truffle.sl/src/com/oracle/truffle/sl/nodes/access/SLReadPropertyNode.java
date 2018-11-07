/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.access;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.sl.nodes.SLExpressionNode;
import com.oracle.truffle.sl.nodes.interop.SLForeignToSLTypeNode;
import com.oracle.truffle.sl.runtime.SLContext;
import com.oracle.truffle.sl.runtime.SLUndefinedNameException;

/**
 * The node for reading a property of an object. When executed, this node:
 * <ol>
 * <li>evaluates the object expression on the left hand side of the object access operator</li>
 * <li>evaluated the property name</li>
 * <li>reads the named property</li>
 * </ol>
 */
@NodeInfo(shortName = ".")
@NodeChild("receiverNode")
@NodeChild("nameNode")
@ImportStatic({SLContext.class, Message.class})
public abstract class SLReadPropertyNode extends SLExpressionNode {

    @Specialization(guards = "isSLObject(receiver)")
    protected Object read(DynamicObject receiver, Object name,
                    @Cached("create()") SLReadPropertyCacheNode readNode) {
        /**
         * The polymorphic cache node that performs the actual read. This is a separate node so that
         * it can be re-used in cases where the receiver and name are not nodes but already
         * evaluated values.
         */
        return readNode.executeRead(receiver, name);
    }

    /**
     * Language interoperability: if the receiver object is a foreign value we use Truffle's interop
     * API to access the foreign data.
     */
    @Specialization(guards = "!isSLObject(receiver)")
    protected Object readForeign(TruffleObject receiver, Object name,
                    // The child node to access the foreign object
                    @Cached("READ.createNode()") Node foreignReadNode,
                    // The child node to convert the result of the foreign read to a SL value
                    @Cached("create()") SLForeignToSLTypeNode toSLTypeNode) {

        try {
            /* Perform the foreign object access. */
            Object result = ForeignAccess.sendRead(foreignReadNode, receiver, name);
            /* Convert the result to a SL value. */
            return toSLTypeNode.executeConvert(result);

        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            /* Foreign access was not successful. */
            throw SLUndefinedNameException.undefinedProperty(this, name);
        }
    }

    /**
     * When no specialization fits, the receiver is either not an object (which is a type error), or
     * the object has a shape that has been invalidated.
     */
    @Fallback
    protected Object typeError(@SuppressWarnings("unused") Object r, Object name) {
        /* Non-object types do not have properties. */
        throw SLUndefinedNameException.undefinedProperty(this, name);
    }

}
