/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.resident;

import com.oracle.svm.interpreter.debug.EventHandler;
import com.oracle.svm.jdwp.bridge.jniutils.JNIMethodScope;
import com.oracle.svm.jdwp.bridge.EventHandlerBridge;
import com.oracle.svm.jdwp.bridge.TagConstants;
import com.oracle.svm.jdwp.bridge.TypeTag;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class EnterHSEventHandler implements EventHandler {
    private final EventHandlerBridge jdwpEventHandler;

    public EnterHSEventHandler(EventHandlerBridge jdwpEventHandler) {
        this.jdwpEventHandler = jdwpEventHandler;
    }

    @SuppressWarnings("try")
    @Override
    public void onEventAt(Thread thread, ResolvedJavaMethod method, int bci, Object returnValue, int eventKindFlags) {
        try (JNIMethodScope ignored = new JNIMethodScope("JDWPServer::onEventAt", DebuggingOnDemandHandler.currentThreadJniEnv())) {
            long threadId = JDWPBridgeImpl.getIds().toId(thread);
            long methodId = JDWPBridgeImpl.getIds().toId(method);
            ResolvedJavaType declaringType = method.getDeclaringClass();
            long classId = JDWPBridgeImpl.getIds().toId(declaringType);
            byte typeTag = TypeTag.getKind(declaringType);
            JavaKind returnKind = method.getSignature().getReturnKind();
            long returnPrimitiveOrId;
            byte returnTag;
            if (returnValue == null) {
                returnPrimitiveOrId = 0;
                if (JavaKind.Void == returnKind) {
                    returnTag = TagConstants.VOID;
                } else {
                    returnTag = TagConstants.OBJECT;
                }
            } else {
                returnPrimitiveOrId = DebuggingOnDemandHandler.toPrimitiveOrId(returnKind, returnValue);
                Class<?> returnClass = switch (returnKind) {
                    case Byte -> Byte.TYPE;
                    case Boolean -> Boolean.TYPE;
                    case Char -> Character.TYPE;
                    case Short -> Short.TYPE;
                    case Int -> Integer.TYPE;
                    case Float -> Float.TYPE;
                    case Long -> Long.TYPE;
                    case Double -> Double.TYPE;
                    case Void -> Void.TYPE;
                    default -> returnValue.getClass();
                };
                returnTag = TagConstants.getTagFromClass(returnClass);
            }
            assert TagConstants.isValidTag(returnTag) : returnTag;
            jdwpEventHandler.onEventAt(threadId, classId, typeTag, methodId, bci, returnTag, returnPrimitiveOrId, eventKindFlags);
        }
    }

}
