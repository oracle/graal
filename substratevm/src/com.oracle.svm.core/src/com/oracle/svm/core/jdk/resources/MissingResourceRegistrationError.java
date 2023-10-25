/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.resources;

import java.io.Serial;
import java.util.MissingResourceException;

/**
 * This exception is thrown when a resource query (such as {@link Class#getResource(String)}) tries
 * to access an element that was not <a href=
 * "https://www.graalvm.org/latest/reference-manual/native-image/metadata/#resources-and-resource-bundles">registered</a>
 * in the program. When an element is not registered, the exception will be thrown both for elements
 * that exist and elements that do not exist on the given classpath.
 * <p/>
 * The purpose of this exception is to easily discover unregistered elements and to assure that all
 * resources operations for registered elements have the expected behaviour.
 * <p/>
 * Examples:
 * <p/>
 * Registration: {@code "resources": {"includes": [{"pattern":
 * "(registered\\.txt)|(nonExistent\\.txt)"}], "excludes": [{"pattern": "excluded\\.txt}]}}<br>
 * {@code class.getResource("registered.txt")} will return the expected resource.<br>
 * {@code class.getResource("nonExistent.txt")} will return null.<br>
 * {@code class.getResource("excluded.txt")} will throw a {@link MissingResourceRegistrationError}.
 * <p/>
 * Registration: {@code "bundles": [{"name": "bundle.name", "locales": ["en", "de"]}, {"name":
 * "nonExistent", "locales" = ["en"]}]}<br>
 * {@code ResourceBundle.getBundle("bundle.name", new Locale("en"))} will return the expected
 * bundle.<br>
 * {@code ResourceBundle.getBundle("bundle.name", new Locale("fr"))} will throw a
 * {@link MissingResourceRegistrationError}.<br>
 * {@code ResourceBundle.getBundle("nonExistent", new Locale("en"))} will throw a
 * {@link MissingResourceException}.<br>
 * {@code ResourceBundle.getBundle("nonRegistered")} will throw a
 * {@link MissingResourceRegistrationError}.<br>
 */
public final class MissingResourceRegistrationError extends Error {
    @Serial private static final long serialVersionUID = 2764341882856270641L;

    private final String resourcePath;

    public MissingResourceRegistrationError(String message, String resourcePath) {
        super(message);
        this.resourcePath = resourcePath;
    }

    /**
     * @return The path of the resource trying to be queried.
     */
    public String getResourcePath() {
        return resourcePath;
    }
}
