/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.dynamicaccess;

import java.util.ResourceBundle;

import org.graalvm.nativeimage.hosted.Feature;

/**
 * This interface is used to register Java resources and {@link java.util.ResourceBundle}s that
 * should be accessible at run time. An instance of this interface is acquired via
 * {@link Feature.AfterRegistrationAccess#getResourceAccess()}.
 * <p>
 * All methods in {@link ResourceAccess} require a {@link AccessCondition} as their first parameter.
 * Resources will be registered for run-time access only if the specified <code>condition</code> is
 * satisfied.
 *
 * <h3>How to use</h3>
 *
 * {@link ResourceAccess} should only be used during {@link Feature#afterRegistration}. Attempts to
 * register metadata in any other phase of the {@link Feature} API will result in an error.
 * <p>
 * <strong>Example:</strong>
 *
 * <pre>{@code @Override
 * public void afterRegistration(AfterRegistrationAccess access) {
 *     ResourceAccess resources = access.getResourceAccess();
 *     AccessCondition condition = AccessCondition.typeReached(ConditionType.class);
 *
 *     resources.register(condition, "example/**");
 *
 *     resources.register(AccessCondition.unconditional(), "example/directory/concreteResource");
 *
 *     resources.register(condition, "example/concreteResourceStar\\*");
 *
 *     try {
 *         ResourceBundle bundle = ResourceBundle.getBundle("bundlePath1");
 *         resources.registerResourceBundle(condition, bundle);
 *     } catch (MissingResourceException e) {
 *         // Log or handle the missing resource
 *     }
 * }
 * }</pre>
 *
 * @since 25.0.1
 */
public interface ResourceAccess {

    /**
     * Registers resources matching the specified {@code glob} in the provided {@code module} for
     * run-time access, if the {@code condition} is satisfied.
     * <p>
     * The {@code glob} uses a subset of
     * <a href="https://en.wikipedia.org/wiki/Glob_(programming)">glob-pattern</a> rules for
     * specifying resources.
     * <p>
     * There are several rules to be observed when specifying a resource path:
     * <ul>
     * <li>The {@code native-image} tool supports only {@code star (*)} and {@code globstar (**)}
     * wildcard patterns.
     * <ul>
     * <li>By definition, {@code star} can match any number of any characters on one level while
     * {@code globstar} can match any number of characters at any level.</li>
     * <li>If there is a need to treat a star literally (without special meaning), it can be escaped
     * using {@code \} (for example, {@code \*}).</li>
     * </ul>
     * </li>
     * <li>In the glob, a <strong>level</strong> represents a part of the pattern separated with
     * {@code /}.</li>
     * <li>When writing glob patterns the following rules must be observed:
     * <ul>
     * <li>Glob cannot be empty (for example, {@code ""})</li>
     * <li>Glob cannot end with a trailing slash ('/') (for example, {@code "foo/bar/"})</li>
     * <li>Glob cannot contain more than two consecutive (non-escaped) {@code *} characters on one
     * level (for example, <code>"foo/***&#47;"</code>)</li>
     * <li>Glob cannot contain empty levels (for example, {@code "foo//bar"})</li>
     * <li>Glob cannot contain two consecutive globstar wildcards (example,
     * <code>"foo/**&#47;**"</code>)</li>
     * <li>Glob cannot have other content on the same level as globstar wildcard (for example,
     * {@code "foo/**bar/x"})</li>
     * </ul>
     * </li>
     * </ul>
     * <p>
     * Given the following project structure:
     *
     * <pre>{@code
     * app/src/main/resources/{Resource0.txt, Resource1.txt}
     * }</pre>
     * <p>
     * You can:
     * <ul>
     * <li>Include all resources with glob <code>**&#47;Resource*.txt</code></li>
     * <li>Include {@code Resource0.txt} with glob <code>**&#47;Resource0.txt</code></li>
     * <li>Include {@code Resource0.txt} and <code>Resource1.txt</code> with globs
     * <code>**&#47;Resource0.txt</code> and <code>**&#47;Resource1.txt</code></li>
     * </ul>
     *
     * @param condition the condition that must be satisfied to register the target resource
     * @param module the Java module instance that contains target resources. If the provided value
     *            is {@code null} or an unnamed module, resources are looked up on the classpath
     *            instead.
     * @param glob the glob that is matched against all resources
     *
     * @since 25.0.1
     */
    void register(AccessCondition condition, Module module, String glob);

    /**
     * Registers resources matching the specified {@code glob} from the classpath for run-time
     * access, if the {@code condition} is satisfied.
     * <p>
     * The {@code glob} uses a subset of
     * <a href="https://en.wikipedia.org/wiki/Glob_(programming)">glob-pattern</a> rules for
     * specifying resources.
     * <p>
     * There are several rules to be observed when specifying a resource path:
     * <ul>
     * <li>The {@code native-image} tool supports only {@code star (*)} and {@code globstar (**)}
     * wildcard patterns.
     * <ul>
     * <li>By definition, {@code star} can match any number of any characters on one level while
     * {@code globstar} can match any number of characters at any level.</li>
     * <li>If there is a need to treat a star literally (without special meaning), it can be escaped
     * using {@code \} (for example, {@code \*}).</li>
     * </ul>
     * </li>
     * <li>In the glob, a <strong>level</strong> represents a part of the pattern separated with
     * {@code /}.</li>
     * <li>When writing glob patterns the following rules must be observed:
     * <ul>
     * <li>Glob cannot be empty (for example, {@code ""})</li>
     * <li>Glob cannot end with a trailing slash ('/') (for example, {@code "foo/bar/"})</li>
     * <li>Glob cannot contain more than two consecutive (non-escaped) {@code *} characters on one
     * level (for example, <code>"foo/***&#47;"</code>)</li>
     * <li>Glob cannot contain empty levels (for example, {@code "foo//bar"})</li>
     * <li>Glob cannot contain two consecutive globstar wildcards (example,
     * <code>"foo/**&#47;**"</code>)</li>
     * <li>Glob cannot have other content on the same level as globstar wildcard (for example,
     * {@code "foo/**bar/x"})</li>
     * </ul>
     * </li>
     * </ul>
     *
     * Given the following project structure:
     *
     * <pre>{@code
     * app/src/main/resources/{Resource0.txt, Resource1.txt}
     * }</pre>
     * <p>
     * You can:
     * <ul>
     * <li>Include all resources with glob <code>**&#47;Resource*.txt</code></li>
     * <li>Include {@code Resource0.txt} with glob <code>**&#47;Resource0.txt</code></li>
     * <li>Include {@code Resource0.txt} and <code>Resource1.txt}</code> with globs
     * <code>**&#47;Resource0.txt</code> and <code>**&#47;Resource1.txt</code></li>
     * </ul>
     *
     * @param condition the condition that must be satisfied to register the target resource
     *
     * @param glob the glob that is matched against all resources
     *
     * @since 25.0.1
     */
    default void register(AccessCondition condition, String glob) {
        register(condition, null, glob);
    }

    /**
     * Registers the provided {@link java.util.ResourceBundle}s for run-time access, if the
     * {@code condition} is satisfied. Each {@link java.util.ResourceBundle} must be obtained using
     * any version of the {@link java.util.ResourceBundle#getBundle} method. A bundle created in any
     * other way will result in an error.
     * <p>
     * <strong>Recommended use to avoid crashes during image building:</strong>
     * 
     * <pre>{@code @Override
     * public void afterRegistration(AfterRegistrationAccess access) {
     *     ResourceAccess resources = access.getResourceAccess();
     *     AccessCondition condition = AccessCondition.typeReached(ConditionType.class);
     *     try {
     *         ResourceBundle bundle1 = ResourceBundle.getBundle("bundlePath1");
     *         ResourceBundle bundle2 = ResourceBundle.getBundle("bundlePath2");
     *         resources.registerResourceBundle(condition, bundle1, bundle2);
     *     } catch (MissingResourceException e) {
     *         // Log or handle the missing resource
     *     }
     * }
     * }</pre>
     * 
     * @since 25.0.1
     */
    void registerResourceBundle(AccessCondition condition, ResourceBundle... bundles);
}
