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
 * <h1>Contents</h1>
 * 
 * <div id="toc"></div>
 * <div id="contents">
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
 * <h2>Getting Started</h2>
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
 * <h3>It's a Polyglot World</h3>
 *
 * How to list all available languages?
 *
 * <h3>Adding additional Language</h3>
 *
 * Put its JAR on classpath.
 *
 * <h3>Hello World in Ruby and JavaScript</h3>
 *
 * Mixing languages
 *
 * <h2>Calling Functions</h2>
 *
 * <h3>Define and call JavaScript function</h3>
 *
 * To use a function written in a dynamic language from Java one needs to give
 * it a type. The following sample defines {@code Mul} interface with a single
 * method. Then it evaluates the dynamic code to define the function and
 * {@link com.oracle.truffle.api.vm.PolyglotEngine.Value#as(java.lang.Class) uses the result as} the {@code Mul}
 * interface:
 *
 * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#defineJavaScriptFunctionAndUseItFromJava}
 *
 * In case of JavaScript, it is adviced to wrap the function into parenthesis,
 * as then the function doesn't polute the global scope - the only reference to
 * it is via implementation of the {@code Mul} interface.
 *
 * <h3>Define and call Ruby function</h3>
 * <h3>Define and call R function</h3>
 *
 * <h2>Multiple functions with a state</h2>
 *
 * Often it is necessary to expose multiple dynamic language functions that work
 * in orchestration - for example when they share some common variables. This
 * can be done by typing these functions via Java interface as well. Just the
 * interface needs to have more than a single method:
 *
 * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#defineMultipleJavaScriptFunctionsAndUseItFromJava}
 *
 * The previous example defines an object with two functions:
 * <code>addTime</code> and <code>timeInSeconds</code>. The Java interface
 * <code>Times</code> wraps this dynamic object and gives it a type: for example
 * the <code>addTime</code> method is known to take three integer arguments.
 * Both functions in the dynamic language are defined in a single, encapsulating
 * function - as such they can share variable <code>seconds</code>. Because of
 * using parenthesis when defining the encapsulating function, nothing is
 * visible in a global JavaScript scope - the only reference to the system is
 * from Java via the implementation of <code>Times</code> interface.
 *
 * <h2>Accessing Dynamic Structures from Java</h2>
 * 
 * <h3>Type-safe View of an Array</h3>
 *
 * The following example defines a {@link java.lang.FunctionalInterface} which's method returns a
 * {@link java.util.List} of points:
 *
 * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#arrayWithTypedElements}
 *
 * <h3>Accessing JavaScript Classes</h3>
 *
 * Version six of JavaScript added a concept of typeless
 * classes. With Java interop one can give the classes types. Here is an example
 * that defines <code>class Incrementor</code> with two functions and one field
 * and "types it" with Java interface:
 *
 * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#incrementor}
 *
 *
 * <h3>Accessing JSON Structure</h3>
 *
 * Imagine a need to safely access complex JSON-like structure. The next example uses one
 * modeled after JSON response returned by a GitHub API. It contains a list of repository
 * objects. Each repository has an id, name, list of URLs and a nested structure describing
 * owner. Let's start by defining the structure with Java interfaces:
 *
 * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessJSONObjectProperties}
 *
 * The example defines a parser that somehow obtains object representing the JSON data and
 * converts it into {@link java.util.List} of {@code Repository} instances. After calling the method
 * we can safely use the interfaces ({@link java.util.List}, {@code Repository}, {@code Owner}) and
 * inspect the JSON structure in a type-safe way.
 *
 * <h2>Accessing Java from Dynamic Languages</h2>
 *
 * <h3>Access Fields and Methods of Java Objects</h3>
 *
 * This method allows one to easily expose <b>public</b> members of Java objects to scripts
 * written in dynamic languages. For example the next code defines class <code>Moment</code>
 * and allows dynamic language access its fields:
 *
 * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessFieldsOfJavaObject}
 *
 * Of course, the {@link com.oracle.truffle.api.vm.PolyglotEngine.Value#as(java.lang.Class) manual conversion} to {@link java.lang.Number} is
 * annoying. Should it be performed frequently, it is better to define a
 * <code>MomentConvertor</code> interface:
 *
 * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#accessFieldsOfJavaObjectWithConvertor}
 *
 * then one gets completely type-safe view of the dynamic function including its parameters
 * and return type.
 *
 * <h3>Accessing Static Methods</h3>
 *
 * Dynamic languages can also access <b>public</b> static methods and <b>public</b>
 * constructors of Java classes, if they can get reference to them. Luckily
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine.Value#execute(java.lang.Object...) there is a support}
 * for wrapping instances of
 * {@link java.lang.Class} to appropriate objects:
 *
 * {@codesnippet com.oracle.truffle.tck.impl.PolyglotEngineWithJavaScript#createNewMoment}
 *
 * In the above example the <code>Moment.class</code> is passed into the JavaScript function
 * as first argument and can be used by the dynamic language as a constructor in
 * <code>new Moment(h, m, s)</code> - that creates new instances of the Java class. Static
 * methods of the passed in <code>Moment</code> object could be invoked as well.
 *
 * <h1>Writing Own Language</h1>
 *
 * Expert topic: see {@link com.oracle.truffle.api.TruffleLanguage other tutorial}.
 * 
 * </div>
<script>

window.onload = function () {
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

    var toc = "";
    var level = 0;

    document.getElementById("contents").innerHTML =
        document.getElementById("contents").innerHTML.replace(
            /<h([\d])>([^<]+)<\/h([\d])>/gi,
            function (str, openLevel, titleText, closeLevel) {
                if (openLevel != closeLevel) {
                    return str;
                }

                if (openLevel > level) {
                    toc += (new Array(openLevel - level + 1)).join("<ul>");
                } else if (openLevel < level) {
                    toc += (new Array(level - openLevel + 1)).join("</ul>");
                }

                level = parseInt(openLevel);

                var anchor = titleText.replace(/ /g, "_");
                toc += "<li><a href=\"#" + anchor + "\">" + titleText
                    + "</a></li>";

                return "<h" + openLevel + "><a name=\"" + anchor + "\">"
                    + titleText + "</a></h" + closeLevel + ">";
            }
        );

    if (level) {
        toc += (new Array(level + 1)).join("</ul>");
    }

    document.getElementById("toc").innerHTML += toc;
};
</script>
 * 
 * @since 0.23
 */
package com.oracle.truffle.tutorial;
