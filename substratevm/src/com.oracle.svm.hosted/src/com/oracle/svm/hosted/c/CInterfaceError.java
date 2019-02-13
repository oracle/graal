/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.oracle.svm.hosted.c.info.ElementInfo;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class CInterfaceError {

    private final List<Object> context;
    private final String message;

    public CInterfaceError(String msg, Object... context) {
        this.context = fillContext(context, new ArrayList<>());
        this.message = fullMessage(msg);
    }

    public List<Object> getContext() {
        return context;
    }

    private String fullMessage(String msg) {
        StringBuilder result = new StringBuilder(msg);
        for (Object element : this.context) {
            if (element instanceof ResolvedJavaMethod) {
                result.append("\n    method ").append(((ResolvedJavaMethod) element).format("%H.%n(%p)"));
            } else if (element instanceof ResolvedJavaType) {
                result.append("\n    type ").append(((ResolvedJavaType) element).toJavaName(true));
            } else {
                result.append("\n    ").append(element);
            }
        }
        return result.toString();
    }

    private static List<Object> fillContext(Object obj, List<Object> result) {
        if (obj == null) {
            /* ignore */
        } else if (obj instanceof Object[]) {
            for (Object inner : ((Object[]) obj)) {
                fillContext(inner, result);
            }
        } else if (obj instanceof Collection) {
            for (Object inner : ((Collection<?>) obj)) {
                fillContext(inner, result);
            }
        } else if (obj instanceof ElementInfo) {
            result.add(((ElementInfo) obj).getAnnotatedElement());
        } else {
            result.add(obj);
        }
        return result;
    }

    public String getMessage() {
        return message;
    }
}
