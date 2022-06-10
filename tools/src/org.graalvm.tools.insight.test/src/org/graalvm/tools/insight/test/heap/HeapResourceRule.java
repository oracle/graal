/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight.test.heap;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.LinkedList;
import java.util.List;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;

/**
 *
 * @author martin
 */
public abstract class HeapResourceRule implements TestRule {

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface HeapParams {

        int cacheSize() default 0;

        String cacheReplacement() default "flush";
    }

    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                HeapParams heapParams = description.getAnnotation(HeapParams.class);
                int cacheSize = (heapParams != null) ? heapParams.cacheSize() : 0;
                String cacheReplacement = (heapParams != null) ? heapParams.cacheReplacement() : null;

                before(cacheSize, cacheReplacement);
                List<Throwable> errors = new LinkedList<>();
                try {
                    base.evaluate();
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    try {
                        after();
                    } catch (Throwable t) {
                        errors.add(t);
                    }
                }
                MultipleFailureException.assertEmpty(errors);
            }
        };
    }

    protected abstract void before(int cacheSize, String cacheReplacement) throws Throwable;

    protected void after() throws Throwable {
    }
}
