/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import java.lang.reflect.Method;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.java.ExecuteNode.DoExecuteNode;

@AcceptMessage(value = "INVOKE", receiverType = JavaObject.class, language = JavaInteropLanguage.class)
final class InvokeNode extends InvokeBaseNode {

    @Child private DoExecuteNode doExecute;

    @Override
    public Object access(VirtualFrame frame, JavaObject object, String name, Object[] args) {
        for (Method m : object.clazz.getMethods()) {
            if (m.getName().equals(name)) {
                if (m.getParameterTypes().length == args.length || m.isVarArgs()) {
                    if (doExecute == null || args.length != doExecute.numberOfArguments()) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        doExecute = insert(new DoExecuteNode(args.length));
                    }
                    return doExecute.execute(frame, m, object.obj, args);
                }
            }
        }
        throw UnknownIdentifierException.raise(name);
    }

}
