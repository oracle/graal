/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

// @formatter:off

/*
 @ApiInfo(
 group="Tutorial"
 )
 */
/**
 * <a href="https://github.com/graalvm/truffle">Truffle</a>
 * is a framework for implementing languages as simple interpreters.
 * Together with the <a href="https://github.com/graalvm/graal-core/">Graal compiler</a>,
 * Truffle interpreters are automatically just-in-time compiled and programs running on
 * top of them can reach performance of normal Java.
 * <p>
 * Truffle is developed and maintained by Oracle Labs and the
 * Institute for System Software of the Johannes Kepler University Linz.
 *
 * <h1>Embedding Truffle</h1>
 *
 * In case you want to embedded Truffle into your Java application,
 * start by downloading 
 * <a href="http://www.oracle.com/technetwork/oracle-labs/program-languages/overview/">GraalVM</a>
 * which contains all the necessary pre-built components.
 * Truffle bits are <a href="http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.oracle.truffle%22%20AND%20a%3A%22truffle-api%22">
 * uploaded to Maven central</a>. You can use them from your <em>pom.xml</em>
 * file as:
 * <pre>
&lt;dependency&gt;
    &lt;groupId&gt;<b>com.oracle.truffle</b>&lt;/groupId&gt;
    &lt;artifactId&gt;<b>truffle-api</b>&lt;/artifactId&gt;
    &lt;version&gt;0.23&lt;/version&gt; <em>&lt;!-- or any later version --&gt;</em>
&lt;/dependency&gt;
&lt;dependency&gt;
    &lt;groupId&gt;<b>com.oracle.truffle</b>&lt;/groupId&gt;
    &lt;artifactId&gt;<b>truffle-dsl-processor</b>&lt;/artifactId&gt;
    &lt;version&gt;0.23&lt;/version&gt; <em>&lt;!-- same version as above --&gt;</em>
    &lt;scope&gt;provided&lt;/scope&gt;
&lt;/dependency&gt;
 * </pre>
 *
 * <h3>Simple Hello World!</h3>
 *
 * Integrating Truffle into your Java application starts with building
 * an instance of {@link com.oracle.truffle.api.vm.PolyglotEngine} - a
 * gate way into the polyglot world of languages. Once you have an instance
 * of the engine, you can {@link com.oracle.truffle.api.source.Source build sources}
 * and {@link com.oracle.truffle.api.vm.PolyglotEngine#eval evaluate them}.
 * The following example create a <code>hello.js</code> JavaScript source,
 * executees it and checks result of the evaluation:
 *
 * {@codesnippet com.oracle.truffle.tutorial.HelloWorld#helloWorldInJavaScript}
 *
 * 
<script>
function hide(tagname, cnt, clazz) {
    var elems = document.getElementsByTagName(tagname)
    for (var i = 0; cnt > 0; i++) {
        var e = elems[i];
        if (!e) {
            break;
        }
        if (!clazz || e.getAttribute("class") === clazz) {
            e.style.display = 'none';
            cnt--;
        }
    }
}
hide("h1", 1);
hide("h2", 1);
hide("p", 1);
hide("div", 1, "docSummary");
</script>
 * 
 * @since 0.23
 */
package com.oracle.truffle.tutorial;
