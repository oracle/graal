/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.polyglot.scala;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.polyglot.scala.ScalaFeature.HasMangledPopulateNamesMapMethod;
import com.oracle.svm.polyglot.scala.ScalaFeature.HasPopulateNamesMapMethod;

@TargetClass(className = "scala.Enumeration", onlyWith = HasMangledPopulateNamesMapMethod.class)
final class Target_scala_Enumeration_mangled {
    // Checkstyle: stop
    @SuppressWarnings({"CheckStyle", "static-method"})
    @Substitute
    private void scala$Enumeration$$populateNameMap() {
        throw new Error("Scala Enumerations should have been initialized during compilation.");
    }
    // Checkstyle: resume
}

@TargetClass(className = "scala.Enumeration", onlyWith = HasPopulateNamesMapMethod.class)
final class Target_scala_Enumeration {
    // Checkstyle: stop
    @SuppressWarnings({"CheckStyle", "static-method"})
    @Substitute
    private void populateNameMap() {
        throw new Error("Scala Enumerations should have been initialized during compilation.");
    }
    // Checkstyle: resume
}

public class ScalaSubstitutions {
}
