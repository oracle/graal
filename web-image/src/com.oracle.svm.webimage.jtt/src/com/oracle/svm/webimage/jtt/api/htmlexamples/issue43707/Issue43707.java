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

package com.oracle.svm.webimage.jtt.api.htmlexamples.issue43707;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;

public class Issue43707 {

    static final String JS_FILE = "/com/oracle/svm/webimage/jtt/api/htmlexamples/issue43707/issue-43707.js";

    public static final String[] OUTPUT = new String[]{
                    "JavaScript<object; [object Object]>",
                    "JavaScript<object; [object Object]>"
    };

    public static void main() {
        HTMLDocument0 document = HTMLDocument0.getDocument();
        System.out.println(document);
        // JavaScript<object; [object HTMLDocument]>

        HTMLDivElement0 element = document.getElementById("main");

        // If the HTMLDivElement0 does not get included in the image,
        // we get:
        //
        // Exception in thread "main" java.lang.ClassCastException:
        // JavaScript 'object' value cannot be coerced to a Java
        // 'io.helidon.dejavu.api.dom.nativeimpl.HTMLDivElement0'.
        //
        // Previously, this exception was circumvented with this call:
        //
        // element.hello();
        //
        // This test ensures that such imported objects get included in the image.

        System.out.println(element);
    }
}

@JS.Import
@JS.Code.Include(Issue43707.JS_FILE)
class HTMLDivElement0 extends JSObject {
    @JS("console.log('hello');")
    public native void hello();
}

@JS.Import
@JS.Code.Include(Issue43707.JS_FILE)
class HTMLDocument0 extends JSObject {
    @JS("return document0;")
    @JS.Coerce
    public static native HTMLDocument0 getDocument();

    @JS("return this.getElementById(id)")
    @JS.Coerce
    public native HTMLDivElement0 getElementById(String id);
}
