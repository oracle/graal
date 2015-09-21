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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.nodes.RootNode;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

final class InvokeMemberNode extends RootNode {
    InvokeMemberNode() {
        super(JavaInteropLanguage.class, null, null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        JavaInterop.JavaObject receiver = (JavaInterop.JavaObject) ForeignAccess.getReceiver(frame);
        final List<Object> args = ForeignAccess.getArguments(frame);
        final Object nameOrIndex = args.get(0);
        final int argsLength = args.size() - 1;
        if (nameOrIndex instanceof Integer) {
            throw new IllegalStateException();
        } else {
            String name = (String) nameOrIndex;
            for (Method m : receiver.clazz.getMethods()) {
                final boolean isStatic = (m.getModifiers() & Modifier.STATIC) != 0;
                if (isStatic) {
                    continue;
                }
                if (m.getName().equals(name) && m.getParameterCount() == argsLength) {
                    Object[] arr = args.subList(1, args.size()).toArray();
                    return JavaFunctionNode.execute(m, receiver.obj, arr);
                }
            }
            throw new IllegalArgumentException(name);
        }
    }

}
