/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Red Hat Inc. All rights reserved.
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

package hello;

import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;

@TargetClass(value = Hello.DefaultGreeter.class)
final class Target_hello_Hello_DefaultGreeter {

    @SuppressWarnings("static-method")
    @Substitute()
    @TargetElement(name = "hashCode")
    private int hashCodeSubst() {
        return 24;
    }

    @SuppressWarnings("static-method")
    @Substitute
    private void greet() {
        SubstituteHelperClass substituteHelperClass = new SubstituteHelperClass();
        substituteHelperClass.inlineGreet();
    }

}

class SubstituteHelperClass {
    @AlwaysInline("For testing purposes")
    void inlineGreet() {
        staticInlineGreet();
    }

    @AlwaysInline("For testing purposes")
    private static void staticInlineGreet() {
        nestedGreet();
    }

    @NeverInline("For testing purposes")
    private static void nestedGreet() {
        System.out.println("Hello, substituted world!");
    }
}
