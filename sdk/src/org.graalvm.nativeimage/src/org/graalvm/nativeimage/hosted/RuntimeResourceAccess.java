/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativeimage.hosted;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeResourceSupport;

/**
 * This class can be used to register Java resources and ResourceBundles that should be accessible
 * at run time.
 *
 * @since 22.3
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class RuntimeResourceAccess {

    /**
     * Make Java resource {@code resourcePath} from {@code module} available at run time. If the
     * given {@code module} is unnamed, the resource is looked up on the classpath instead.
     *
     * @since 22.3
     */
    public static void addResource(Module module, String resourcePath) {
        Objects.requireNonNull(module);
        Objects.requireNonNull(resourcePath);
        ImageSingletons.lookup(RuntimeResourceSupport.class).addResources(ConfigurationCondition.alwaysTrue(),
                        withModuleName(module, Pattern.quote(resourcePath)));
    }

    /**
     * Inject a Java resource at {@code resourcePath} in {@code module} with the specified
     * {@code resourceContent}. At runtime the resource can be accessed as if it was part of the
     * original application. If the given {@code module} is unnamed, the resource is placed on the
     * classpath instead.
     *
     * @since 22.3
     */
    public static void addResource(Module module, String resourcePath, byte[] resourceContent) {
        Objects.requireNonNull(module);
        Objects.requireNonNull(resourcePath);
        Objects.requireNonNull(resourceContent);
        ImageSingletons.lookup(RuntimeResourceSupport.class).injectResource(
                        module, resourcePath, resourceContent);
    }

    /**
     * Make Java ResourceBundle that is specified by a {@code baseBundleName} and {@code locales}
     * from module {@code module} available at run time. If the given {@code module} is unnamed, the
     * ResourceBundle is looked up on the classpath instead.
     *
     * @since 22.3
     */
    public static void addResourceBundle(Module module, String baseBundleName, Locale[] locales) {
        Objects.requireNonNull(locales);
        ImageSingletons.lookup(RuntimeResourceSupport.class).addResourceBundles(ConfigurationCondition.alwaysTrue(),
                        withModuleName(module, baseBundleName), Arrays.asList(locales));
    }

    /**
     * Make Java ResourceBundle that is specified by a {@code bundleName} from module {@code module}
     * available at run time. If the given {@code module} is unnamed, the ResourceBundle is looked
     * up on the classpath instead.
     *
     * @since 22.3
     */
    public static void addResourceBundle(Module module, String bundleName) {
        ImageSingletons.lookup(RuntimeResourceSupport.class).addResourceBundles(ConfigurationCondition.alwaysTrue(),
                        withModuleName(module, bundleName));
    }

    private static String withModuleName(Module module, String str) {
        Objects.requireNonNull(module);
        Objects.requireNonNull(str);
        return (module.isNamed() ? module.getName() : "ALL-UNNAMED") + ":" + str;
    }

    private RuntimeResourceAccess() {
    }
}
