/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;

@EspressoSubstitutions
public class Target_java_lang_String {
    @Substitution(hasReceiver = true)
    public static char charAt(@Host(String.class) StaticObject self, int at,
                    // @GuestCall(target = "java_lang_String_charAt") DirectCallNode charAt,
                    @InjectMeta Meta meta) {
        if (meta.getJavaVersion().compactStringsEnabled()) {
            // return (char) charAt.call(self, at);
            return 0;
        } else {
            return Meta.toHostString(self).charAt(at);
        }
    }

    @Substitution(hasReceiver = true)
    public static int indexOf(@Host(String.class) StaticObject self, int ch, int from,
                    // @GuestCall(target = "java_lang_String_indexOf") DirectCallNode indexOf,
                    @InjectMeta Meta meta) {
        if (meta.getJavaVersion().compactStringsEnabled()) {
            // return (char) indexOf.call(self, ch, from);
            return 0;
        } else {
            return Meta.toHostString(self).indexOf(ch, from);
        }
    }
}
