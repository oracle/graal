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
package com.oracle.svm.webimage.jtt.xhr;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.graalvm.webimage.api.JS;

public class UrlStreamTest {
    public static void main(String[] args) {
        if (args.length == 2) {
            defineGlobals(args[0]);
            System.out.println(getContent(args[1]));
        } else {
            System.out.println("Bug in test: expected exactly 2 arguments");
        }
    }

    @JS.Coerce
    @JS("(0,eval)(jsCode);")
    public static native void defineGlobals(String jsCode);

    public static String getContent(String url) {
        try (InputStream is = URI.create(url).toURL().openStream()) {
            byte[] buff = new byte[4096];
            int size = is.read(buff);
            if (size <= 0 || size >= 4096) {
                return "Bad content size";
            } else if (is.read() != -1) {
                return "Bad content size";
            } else {
                return new String(buff, 0, size);
            }
        } catch (IOException ex) {
            return "Caught " + ex;
        }
    }
}
