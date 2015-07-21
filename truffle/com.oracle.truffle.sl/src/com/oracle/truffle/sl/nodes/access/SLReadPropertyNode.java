/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.sl.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * The node for accessing a property of an object. When executed, this node first evaluates the
 * object expression on the left side of the dot operator and then reads the named property.
 */
@NodeInfo(shortName = ".")
public final class SLReadPropertyNode extends SLExpressionNode {

    public static SLReadPropertyNode create(SourceSection src, SLExpressionNode receiverNode, String propertyName) {
        return new SLReadPropertyNode(src, receiverNode, propertyName);
    }

    @Child private SLExpressionNode receiverNode;
    @Child private SLReadPropertyCacheNode cacheNode;
    private final ConditionProfile receiverTypeCondition = ConditionProfile.createBinaryProfile();

    private SLReadPropertyNode(SourceSection src, SLExpressionNode receiverNode, String propertyName) {
        super(src);
        this.receiverNode = receiverNode;
        this.cacheNode = SLReadPropertyCacheNodeGen.create(propertyName);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object object = receiverNode.executeGeneric(frame);
        if (receiverTypeCondition.profile(SLContext.isSLObject(object))) {
            return cacheNode.executeObject(SLContext.castSLObject(object));
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new SLException("unexpected receiver type");
        }
    }
}
