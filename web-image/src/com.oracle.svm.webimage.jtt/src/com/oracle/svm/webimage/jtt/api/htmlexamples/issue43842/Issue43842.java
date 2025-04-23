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

package com.oracle.svm.webimage.jtt.api.htmlexamples.issue43842;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;

public class Issue43842 {

    static final String JS_FILE = "/com/oracle/svm/webimage/jtt/api/htmlexamples/issue43842/issue-43842.js";

    public static final String[] OUTPUT = new String[]{
                    "object"
    };

    public static void main() {
        var document = HTMLDocumentImpl.getDocument();
        ElementImpl elem = document.getElementById("main");
        System.out.println(elem.typeof());
    }
}

@JS.Import("Element1")
@JS.Code.Include(Issue43842.JS_FILE)
class ElementImpl extends JSObject {
}

@JS.Import("HTMLDocument1")
@JS.Code.Include(Issue43842.JS_FILE)
class HTMLDocumentImpl extends JSObject {
    @JS("return this.getElementById(id);")
    @JS.Coerce
    public native ElementImpl getElementById(String id);

    @JS("return document1;")
    @JS.Coerce
    public static native HTMLDocumentImpl getDocument();
}
