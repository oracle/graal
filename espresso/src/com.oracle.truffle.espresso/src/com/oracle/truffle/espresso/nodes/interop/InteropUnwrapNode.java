/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.interop;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.VM;

@GenerateUncached
@ImportStatic(EspressoContext.class)
public abstract class InteropUnwrapNode extends EspressoNode {

    public abstract Object execute(Object object);

    @Specialization(guards = {"!object.isForeignObject()", "object.getKlass() == cachedKlass"}, limit = "3")
    @SuppressWarnings("unused")
    static Object doStaticObjectCached(StaticObject object,
                    @Bind Node node,
                    @Bind("get(node).getMeta()") Meta meta,
                    @Cached("object.getKlass()") Klass cachedKlass,
                    @Cached("meta.isBoxed(cachedKlass)") boolean needsUnboxing,
                    @Cached("maybeForeignException(cachedKlass, meta)") boolean foreignException) {
        assert (cachedKlass == null) == StaticObject.isNull(object);
        if (cachedKlass == null) { // is StaticObject.NULL
            return object;
        } else if (needsUnboxing) {
            return unboxBoundary(EspressoContext.get(node).getMeta(), object);
        } else if (foreignException) {
            return unwrapForeignException(object, EspressoContext.get(node).getMeta());
        } else {
            return object;
        }
    }

    @Specialization(guards = "!object.isForeignObject()", replaces = "doStaticObjectCached")
    static Object doStaticObjectGeneric(StaticObject object,
                    @Bind Node node,
                    @Bind("get(node).getMeta()") Meta meta,
                    @Cached InlinedBranchProfile seenNull,
                    @Cached InlinedBranchProfile seenBoxed,
                    @Cached InlinedBranchProfile seenForeignException,
                    @Cached InlinedBranchProfile seenRegular) {
        Klass klass = object.getKlass();
        assert (klass == null) == StaticObject.isNull(object);
        if (klass == null) { // is StaticObject.NULL
            seenNull.enter(node);
            return object;
        } else if (meta.isBoxed(klass)) {
            seenBoxed.enter(node);
            return unboxBoundary(meta, object);
        } else if (maybeForeignException(klass, meta)) {
            seenForeignException.enter(node);
            return unwrapForeignException(object, meta);
        } else {
            seenRegular.enter(node);
            return object;
        }
    }

    @Specialization(guards = "object.isForeignObject()")
    static Object doStaticObjectForeign(StaticObject object, @Bind Node node) {
        return object.rawForeignObject(EspressoLanguage.get(node));
    }

    @Fallback
    public static Object doOther(Object object) {
        return object;
    }

    @TruffleBoundary
    private static Object unboxBoundary(Meta meta, StaticObject object) {
        // meta.unboxGuest would need to be properly optimized as well
        return meta.unboxGuest(object);
    }

    static boolean maybeForeignException(Klass klass, Meta meta) {
        return klass != null && meta.polyglot != null && meta.java_lang_Throwable.isAssignableFrom(klass);
    }

    private static Object unwrapForeignException(StaticObject object, Meta meta) {
        assert meta.java_lang_Throwable.isAssignableFrom(object.getKlass());
        if (meta.HIDDEN_FRAMES.getHiddenObject(object) == VM.StackTrace.FOREIGN_MARKER_STACK_TRACE) {
            return meta.java_lang_Throwable_backtrace.getObject(object).rawForeignObject(meta.getLanguage());
        }
        return object;
    }
}
