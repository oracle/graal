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
import com.oracle.truffle.api.interop.java.JavaInterop.JavaObject;
import com.oracle.truffle.api.nodes.RootNode;
import java.lang.reflect.Array;

class WriteFieldNode extends RootNode {

    WriteFieldNode() {
        super(JavaInteropLanguage.class, null, null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            JavaInterop.JavaObject receiver = (JavaInterop.JavaObject) ForeignAccess.getReceiver(frame);
            Object obj = receiver.obj;
            final Object indexOrName = ForeignAccess.getArguments(frame).get(0);
            Object value = ForeignAccess.getArguments(frame).get(1);
            if (indexOrName instanceof Integer) {
                Array.set(obj, (Integer) indexOrName, value);
                return JavaObject.NULL;
            }
            String name = (String) indexOrName;
            try {
                receiver.clazz.getField(name).set(obj, value);
                return JavaObject.NULL;
            } catch (NoSuchFieldException ex) {
                throw new RuntimeException(ex);
            }
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

}
