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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "NEW", receiverType = JavaObject.class, language = JavaInteropLanguage.class)
final class NewNode extends JavaNewBaseNode {

    @Override
    public Object access(VirtualFrame frame, JavaObject object, Object[] args) {
        return execute(object, args);
    }

    static Object execute(JavaObject receiver, Object[] args) {
        if (receiver.obj != null) {
            throw new IllegalStateException("Can only work on classes: " + receiver.obj);
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof JavaObject) {
                args[i] = ((JavaObject) args[i]).obj;
            }
        }
        IllegalStateException ex = new IllegalStateException("No suitable constructor found for " + receiver.clazz);
        for (Constructor<?> constructor : receiver.clazz.getConstructors()) {
            try {
                Object ret = constructor.newInstance(args);
                if (ToJavaNode.isPrimitive(ret)) {
                    return ret;
                }
                return JavaInterop.asTruffleObject(ret);
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException instEx) {
                ex = new IllegalStateException(instEx);
            }
        }
        throw ex;
    }

}
