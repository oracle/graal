/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import com.oracle.truffle.api.TruffleLanguage;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.ServiceLoader;
import java.util.Set;

/** Locator that allows the users of the Truffle API to find implementations of languages
 * to be available in {@link PolyglotEngine}. One can specify the locator
 * via {@link PolyglotEngine.Builder#locator(com.oracle.truffle.api.vm.PolyglotLocator)}
 * when {@link PolyglotEngine.Builder building} own instance of the engine.
 * When there is no explicitly specified locator, then the system asks the
 * {@link ServiceLoader} and uses all registered ones to find their languages.
 * <p>
 * When the locator is asked to find {@link TruffleLanguage the languages} -
 * via call to
 * {@link #locate(com.oracle.truffle.api.vm.PolyglotLocator.Response) }
 * method, it can respond by registering {@link ClassLoader}(s) that contain
 * implementations and {@link TruffleLanguage.Registration registration}s of
 * the {@link TruffleLanguage languages}.
 *
 * @since 0.14
 */
public interface PolyglotLocator {
    /** Called to locate languages and other parts of the system.
     *
     * @param response the response to fill in with found languages
     * @since 0.14
     */
    void locate(Response response);

    /** Callback to register languages.
     * @since 0.14
     */
    final class Response {
        private final Set<ClassLoader> loaders;

        private Response(Set<ClassLoader> loaders) {
            this.loaders = loaders;
        }

        /** Register a classloader to be used to find implementations
         * of the languages. Can be called multiple times to register
         * as many classloaders as necessary.
         *
         * @param languageLoader the classloader to use
         * @since 0.14
         */
        public void registerClassLoader(ClassLoader languageLoader) {
            loaders.add(languageLoader);
        }

        /** Creates the set of classloaders to be used by the system.
         *
         * @param locator the specified locator or <code>null</code>
         * @return set of classloaders to search registrations in
         */
        static Set<ClassLoader> loaders(PolyglotLocator locator) {
            Iterable<PolyglotLocator> allLocators;
            if (locator != null) {
                allLocators = Collections.singleton(locator);
            } else {
                allLocators = ServiceLoader.load(PolyglotLocator.class);
            }
            LinkedHashSet<ClassLoader> found = new LinkedHashSet<>();
            Response response = new Response(found);
            for (PolyglotLocator test : allLocators) {
                test.locate(response);
            }
            if (found.isEmpty()) {
                ClassLoader l = PolyglotEngine.class.getClassLoader();
                if (l == null) {
                    l = ClassLoader.getSystemClassLoader();
                }
                found.add(l);
            }
            return found;
        }
    }

}
