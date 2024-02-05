/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot;

import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.io.MessageTransport;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * The sandbox policy presets and validates configurations of a {@link Context context} or
 * {@link Engine engine} to be suitable as a code sandbox. The policy is set by passing it to the
 * {@link Engine.Builder#sandbox(SandboxPolicy) engine} or
 * {@link Context.Builder#sandbox(SandboxPolicy) context} builder method.
 * <p>
 *
 * There are four policies to choose from that become strictly more strict:
 * <ul>
 * <li>{@link #TRUSTED} policy intended for fully trusted applications. In this mode, access to any
 * resource of the host might be accessible to the guest application. This is the default mode,
 * there are no restrictions to the context or engine configuration.
 * <li>{@link #CONSTRAINED} policy intended for trusted, but potentially buggy applications. In this
 * mode, any access to host resources is required to be as restrictive as possible. In this mode,
 * the guest and host application share a heap and execute on the same underlying virtual machine.
 * <li>{@link #ISOLATED} policy intended for trusted applications, but which might have security
 * vulnerabilities and optionally that can be mitigated using this policy. For example, a script
 * that processes untrusted input. Security vulnerabilities would allow an attacker to compromise
 * the guest application by providing malicious input. In this mode, guest and host application
 * execute on separate virtual machine instances.
 * <li>{@link #UNTRUSTED} policy intended for fully untrusted applications. This assumes that a
 * potentially malicious actor is supplying the guest code itself that is being run. A strong
 * adversarial scenario is the execution of client-side Javascript in the browser that is supplied
 * by an untrusted website. In this mode, the sandbox employs additional hardening mechanisms at the
 * compiler and runtime level to mitigate e.g. JIT spraying or speculative execution attacks.
 * </ul>
 *
 * It is unsupported to run untrusted code with any other policy than {@link #UNTRUSTED}.
 *
 * <p>
 * <b> Compatibility Notice: </b> The behavior of sandbox policies is subject to incompatible
 * changes for new GraalVM major releases. New presets and validations may be added in new GraalVM
 * releases that may let configurations valid in older versions fail for newer versions. Therefore,
 * adopting a new GraalVM version with a set sandbox policy might require changes for the embedder.
 * This applies to all policies other than {@link #TRUSTED}. Changes to the policy are announced in
 * the SDK <a href="https://github.com/oracle/graal/blob/master/sdk/CHANGELOG.md">release
 * changelog</a>.
 * <p>
 *
 * For further information on Polyglot Sandboxing, please refer to the
 * <a href="https://www.graalvm.org/latest/security-guide/polyglot-sandbox/">security guide</a>.
 *
 * @see Context.Builder#sandbox(SandboxPolicy)
 * @see Engine.Builder#sandbox(SandboxPolicy)
 *
 * @since 23.0
 */
public enum SandboxPolicy {

    /**
     * Policy intended for fully trusted applications. In this mode, access to any resource of the
     * host might be accessible to the guest application. This is the default mode, there are no
     * restrictions to the context or engine configuration.
     *
     * @since 23.0
     */
    TRUSTED,

    /**
     * Policy intended for trusted, but potentially buggy applications. In this mode, any access to
     * host resources is required to be as restrictive as possible. In this mode, the guest and host
     * application share a heap and execute on the same underlying virtual machine.
     * <p>
     * The {@code CONSTRAINED} sandbox policy enforces the following context restriction:
     * <ul>
     * <li>The list of {@link Context#newBuilder(String...) permitted languages} must be explicitly
     * set.</li>
     * <li>If {@link Context.Builder#in(InputStream) in} is not specified, the
     * {@link InputStream#nullInputStream()} is used. Otherwise, it must be redirected elsewhere
     * than to {@link System#in}.
     * <li>Standard {@link Context.Builder#out(OutputStream) out} and
     * {@link Context.Builder#err(OutputStream)} err} streams must be redirected.</li>
     * <li>The {@link Context.Builder#allowAllAccess(boolean) all access} must not be enabled.</li>
     * <li>The {@link Context.Builder#allowNativeAccess(boolean) native access} must not be
     * enabled.</li>
     * <li>The {@link Context.Builder#allowHostClassLoading(boolean)} host class loading} must not
     * be enabled.</li>
     * <li>The {@link Context.Builder#allowCreateProcess(boolean) external process execution} must
     * not be enabled.</li>
     * <li>The {@link Context.Builder#allowEnvironmentAccess(EnvironmentAccess) environment access}
     * must be {@link EnvironmentAccess#NONE}.</li>
     * <li>The {@link Context.Builder#useSystemExit(boolean) host System.exit} must not be
     * used.</li>
     * <li>The {@link Context.Builder#allowIO(IOAccess) access to the host file system} must be
     * disabled. IO can be {@link IOAccess#NONE disabled} or it can use a
     * {@link org.graalvm.polyglot.io.IOAccess.Builder#fileSystem(FileSystem) custom file
     * system}.</li>
     * <li>If a custom filesystem is used, it must not be the
     * {@link FileSystem#newDefaultFileSystem() default filesystem} or a filesytem wrapping the
     * default file system.</li>
     * <li>Only languages with a sandbox policy of at least {@code CONSTRAINED} can be used.</li>
     * <li>Only instruments with a sandbox policy of at least {@code CONSTRAINED} can be used.</li>
     * <li>Only a subset of options that are safe with the sandbox policy can be used.</li>
     * <li>If {@link HostAccess} is not specified, the {@link HostAccess#CONSTRAINED} is used.</li>
     * Otherwise, the specified {@link HostAccess} must not allow
     * {@link HostAccess.Builder#allowPublicAccess(boolean) public access},
     * {@link HostAccess.Builder#allowAccessInheritance(boolean) access inheritance},
     * {@link HostAccess.Builder#allowAllClassImplementations(boolean) all class implementations},
     * {@link HostAccess.Builder#allowAllImplementations(boolean) all interface implementations} and
     * {@link HostAccess.Builder#allowMutableTargetMappings(HostAccess.MutableTargetMapping...)
     * mutable target type mappings}.
     * <li>The {@link org.graalvm.polyglot.management.ExecutionListener.Builder#attach(Engine)
     * execution listeners} must not be attached.</li>
     * <li>The {@link Engine.Builder#serverTransport(MessageTransport) message transport} must not
     * be set.</li>
     * </ul>
     * </p>
     * <p>
     * Constrained Context building example:
     *
     * <pre>
     * ByteArrayOutputStream output = new ByteArrayOutputStream();
     * ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
     * try (Context context = Context.newBuilder("js") //
     *                 .sandbox(SandboxPolicy.CONSTRAINED) //
     *                 .out(output) //
     *                 .err(errorOutput) //
     *                 .build()) {
     *     context.eval(source);
     * }
     * </pre>
     * </p>
     *
     * @since 23.0
     */
    CONSTRAINED,

    /**
     * Policy intended for trusted applications, but which might have security vulnerabilities and
     * optionally that can be mitigated using this policy. For example, a script that processes
     * untrusted input. Security vulnerabilities may allow an attacker to compromise the guest
     * application by providing malicious input. In this mode, guest and host application execute on
     * separate virtual machine instances.
     * <p>
     * In addition to the {@link #CONSTRAINED} restrictions, the {@code ISOLATED} sandbox policy
     * adds the following constraints:
     * <ul>
     * <li>The {@code engine.SpawnIsolate} option is preset to <code>true</code> if it has not been
     * explicitly set.</li>
     * <li>The {@code engine.MaxIsolateMemory} option must be set.</li>
     * <li>The {@code sandbox.MaxCPUTime} limits option must be set. Use {@code sandbox.TraceLimits}
     * to estimate an application's optimal sandbox parameters.</li>
     * <li>If {@link HostAccess} is not specified, the {@link HostAccess#ISOLATED} is used.</li>
     * Otherwise, the specified {@link HostAccess} must meet all the constraints of the
     * {@link #CONSTRAINED} sandbox policy and must in addition use
     * {@link HostAccess.Builder#methodScoping(boolean) scoped references}.
     * </ul>
     * </p>
     * <p>
     * Isolated Context building example:
     *
     * <pre>
     * ByteArrayOutputStream output = new ByteArrayOutputStream();
     * ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
     * try (Context context = Context.newBuilder("js") //
     *                 .sandbox(SandboxPolicy.ISOLATED)  //
     *                 .out(output)  //
     *                 .err(errorOutput)  //
     *                 .option("engine.MaxIsolateMemory", "1GB")  //
     *                 .option("sandbox.MaxCPUTime", "10s") //
     *                 .build()) {
     *     context.eval(source);
     * }
     * </pre>
     * </p>
     *
     * @since 23.0
     */
    ISOLATED,

    /**
     * Policy intended for untrusted applications. This assumes that a malicious actor is supplying
     * the guest code itself that is being run. A strong adversarial scenario is the execution of
     * client-side Javascript in the browser that is supplied by an untrusted website. In this mode,
     * the sandbox employs additional hardening mechanisms at the compiler and runtime level to
     * mitigate e.g. speculative execution attacks.
     * <p>
     * In addition to the {@link #ISOLATED} constraints, the {@code UNTRUSTED} sandbox policy adds
     * the following requirements:
     * <ul>
     * <li>If {@link HostAccess} is not explicitly specified, the {@link HostAccess#UNTRUSTED} is
     * utilized. In the case where a specific {@link HostAccess} is provided, it must strictly
     * adhere to all the constraints outlined in the {@link #ISOLATED} sandbox policy. Additionally,
     * for UNTRUSTED, the following {@link HostAccess} options are not allowed:
     * <ul>
     * <li>Setting {@link HostAccess.Builder#allowImplementationsAnnotatedBy(Class) implementations
     * of types annotated by an annotation}.</li>
     * <li>Setting {@link HostAccess.Builder#allowArrayAccess(boolean) array access} to
     * {@code true}.</li>
     * <li>Setting {@link HostAccess.Builder#allowListAccess(boolean) list access} to
     * {@code true}.</li>
     * <li>Setting {@link HostAccess.Builder#allowMapAccess(boolean) map access} to
     * {@code true}.</li>
     * <li>Setting {@link HostAccess.Builder#allowBufferAccess(boolean) buffer access} to
     * {@code true}.</li>
     * <li>Setting {@link HostAccess.Builder#allowIterableAccess(boolean) iterable access} to
     * {@code true}.</li>
     * <li>Setting {@link HostAccess.Builder#allowIteratorAccess(boolean) iterator access} to
     * {@code true}.</li>
     * </ul>
     * </li>
     * <li>The {@code engine.UntrustedCodeMitigation} option is preset to {@code software} if it has
     * not been explicitly set.</li>
     * <li>The {@code sandbox.MaxCPUTime}, {@code sandbox.MaxHeapMemory},
     * {@code sandbox.MaxASTDepth}, {@code sandbox.MaxStackFrames}, {@code sandbox.MaxThreads},
     * {@code sandbox.MaxOutputStreamSize}, {@code sandbox.MaxErrorStreamSize} limits options must
     * be set. Use {@code sandbox.TraceLimits} to estimate an application's optimal sandbox
     * parameters.</li>
     * </ul>
     * </p>
     * <p>
     * Untrusted Context building example:
     *
     * <pre>
     * ByteArrayOutputStream output = new ByteArrayOutputStream();
     * ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
     * try (Context context = Context.newBuilder("js") //
     *                 .sandbox(SandboxPolicy.UNTRUSTED) //
     *                 .out(output) //
     *                 .err(errorOutput) //
     *                 .option("engine.MaxIsolateMemory", "1GB") //
     *                 .option("sandbox.MaxHeapMemory", "800MB") //
     *                 .option("sandbox.MaxCPUTime", "10s") //
     *                 .option("sandbox.MaxASTDepth", "100") //
     *                 .option("sandbox.MaxStackFrames", "10") //
     *                 .option("sandbox.MaxThreads", "1") //
     *                 .option("sandbox.MaxOutputStreamSize", "1MB") //
     *                 .option("sandbox.MaxErrorStreamSize", "1MB") //
     *                 .build()) {
     *     context.eval(source);
     * }
     * </pre>
     * </p>
     *
     * @since 23.0
     */
    UNTRUSTED;

    /**
     * Tests whether this {@link SandboxPolicy} is stricter than {@code other}.
     *
     * @since 23.0
     */
    public boolean isStricterThan(SandboxPolicy other) {
        return this.ordinal() > other.ordinal();
    }

    /**
     * Tests whether this {@link SandboxPolicy} is stricter or equal to {@code other}.
     *
     * @since 23.0
     */
    public boolean isStricterOrEqual(SandboxPolicy other) {
        return this.ordinal() >= other.ordinal();
    }
}
