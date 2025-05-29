/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.jtt.javascriptbody;

import com.oracle.svm.webimage.thirdparty.JavaScriptBodyObject;

import net.java.html.js.JavaScriptBody;

public class JavaScriptBodyObjectTest {
    public static void main(String[] args) {
        Object pair = createPair(3.0, 4.0);

        System.out.println(pair.toString());
        if (!(pair instanceof JavaScriptBodyObject)) {
            return;
        }
        JavaScriptBodyObject jsPair = (JavaScriptBodyObject) pair;

        System.out.println(jsPair.get("x"));
        System.out.println(jsPair.get("y"));
        System.out.println(jsPair.get("z"));

        System.out.println(sumPair(jsPair));

        jsPair.set("x", 5.0);
        System.out.println(jsPair.get("x"));
        System.out.println(sumPair(jsPair));
    }

    @JavaScriptBody(args = {"x", "y"}, body = "return {x: x, y: y};")
    public static native Object createPair(double x, double y);

    @JavaScriptBody(args = {"pair"}, body = "return pair.x + pair.y;")
    public static native double sumPair(Object pair);
}
