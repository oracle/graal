/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.util;

import org.openide.util.Lookup.Result;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.Utilities;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public class LookupHistory {

    private static final Map<Class<?>, LookupHistoryImpl> cache = new HashMap<>();

    private static class LookupHistoryImpl<T> implements LookupListener {

        private final Class<T> klass;
        private final Result<T> result;
        // do not keep object + environment in memory; last() will report null if relevant
        // component(s) are closed.
        private Reference<T> last;

        public LookupHistoryImpl(Class<T> klass) {
            this.klass = klass;
            result = Utilities.actionsGlobalContext().lookupResult(klass);
            result.addLookupListener(this);
            last = new WeakReference<>(Utilities.actionsGlobalContext().lookup(klass));
        }

        public T getLast() {
            return last.get();
        }

        @Override
        public void resultChanged(LookupEvent ev) {
            T current = Utilities.actionsGlobalContext().lookup(klass);
            if (current != null) {
                last = new WeakReference<>(current);
            }
        }
    }

    public static <T> void init(Class<T> klass) {
        if (!cache.containsKey(klass)) {
            cache.put(klass, new LookupHistoryImpl<>(klass));
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getLast(Class<T> klass) {
        init(klass);
        assert cache.containsKey(klass);
        return (T) cache.get(klass).getLast();
    }
}
