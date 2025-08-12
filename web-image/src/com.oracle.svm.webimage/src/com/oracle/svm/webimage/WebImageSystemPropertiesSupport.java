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

package com.oracle.svm.webimage;

import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.webimage.functionintrinsics.JSFunctionIntrinsics;

public class WebImageSystemPropertiesSupport extends SystemPropertiesSupport {

    @Override
    protected String userNameValue() {
        return "No user name";
    }

    @Override
    protected String userHomeValue() {
        return "No user home";
    }

    @Override
    protected String javaLibraryPathValue() {
        return ".:/usr/lib";
    }

    @Override
    protected String userDirValue() {
        return JSFunctionIntrinsics.getCurrentWorkingDirectory();
    }

    @Override
    protected String javaIoTmpdirValue() {
        return "/tmp";
    }

    @Override
    protected String osNameValue() {
        return "Web Image";
    }

    @Override
    protected String osVersionValue() {
        return "0.0";
    }

}
