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

package com.oracle.svm.webimage.jtt.api;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSError;

public class JSErrorsTest {
    /**
     * This matches the expected error message produced by the JS runtime used for automated.
     * <p>
     * Error messages may differ between runtimes so this may need to be changed when updating or
     * switching runtimes.
     */
    public static final String[] OUTPUT = new String[]{
                    "JavaScript<object; TypeError: Cannot read properties of undefined (reading 'a')>",
                    "Caught something: Some Error",
    };

    public static void main(String[] args) {
        try {
            typeError();
        } catch (JSError e) {
            System.out.println(e.getMessage());
        }

        try {
            run(() -> {
                throw new CustomException("Some Error");
            });
        } catch (CustomException t) {
            System.out.println("Caught something: " + t.getMessage());
        } catch (Throwable t) {
            System.out.println("ERROR:");
            t.printStackTrace();
        }
    }

    @JS("return undefined.a;")
    private static native Object typeError();

    @JS("r();")
    private static native void run(Runnable r);

    @SuppressWarnings("serial")
    static class CustomException extends RuntimeException {
        CustomException(String message) {
            super(message);
        }
    }
}
