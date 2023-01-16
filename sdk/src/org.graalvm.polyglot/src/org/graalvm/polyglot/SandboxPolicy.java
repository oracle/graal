/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * The sandbox policy presets and validates configurations of a {@link Context context} or
 * {@link Engine engine} to be suitable as a code sandbox. The policy is set by passing it to the
 * respective {@link Engine.Builder#sandbox(SandboxPolicy) engine} or
 * {@link Context.Builder#sandbox(SandboxPolicy) engine} builder method.
 * <p>
 *
 * There are four policies to choose from that become strictly more strict:
 * <ul>
 * <li>{@link #TRUSTED} policy intended for fully trusted applications. In this mode access to any
 * resource of the host might be accessible to the guest application. This is the default mode,
 * there are no restrictions to the context or engine configuration.
 * <li>{@link #RELAXED} policy intended for trusted, but potentially buggy applications. In this
 * mode any access to host resources is required to be as restrictive as possible. In this mode the
 * memory address space of the guest application is shared and with the host application.
 * <li>{@link #ISOLATED} policy intended for trusted, but applications that might have security
 * vulnerabilities. For example, a script that processes untrusted input. Security vulnerabilities
 * would allow an attacker to compromise the guest application by providing malicious input. The
 * memory address space of the guest application is isolated from the host application in this mode.
 * <li>{@link #UNTRUSTED} policy intended for fully untrusted applications. This assumes that a
 * malicious actor is supplying the guest code itself that is being run. A strong adversarial
 * scenario is the execution of client-side Javascript in the browser that is supplied by an
 * untrusted website. In this mode the sandbox employs additional hardening mechanisms at the
 * compiler and runtime level to mitigate e.g. speculative execution attacks.
 * </ul>
 *
 * It is strongly recommended to not run untrusted code with any other policy than
 * {@link #UNTRUSTED}.
 *
 * <p>
 * <b> Compatibility Notice: </b> The behavior of sandbox policies is subject to incompatible
 * changes for new GraalVM versions. New presets and validations may be added in new GraalVM
 * releases, that may let configurations valid in older versions fail for newer versions. Therefore,
 * adopting a new GraalVM version with a set sandbox policy might require changes for the embedder.
 * This applies to all policies other than {@link #TRUSTED}. Changes to the policy are announced in
 * the SDK <a href="https://github.com/oracle/graal/blob/master/sdk/CHANGELOG.md">release
 * changelog</a>.
 * <p>
 *
 * @see Context.Builder#sandbox(SandboxPolicy)
 * @see Engine.Builder#sandbox(SandboxPolicy)
 *
 * @since 23.0
 */
public enum SandboxPolicy {

    /**
     * Policy intended for fully trusted applications. In this mode access to any resource of the
     * host might be accessible to the guest application. This is the default mode, there are no
     * restrictions to the context or engine configuration.
     *
     * @since 23.0
     */
    TRUSTED {
        @Override
        HostAccess createDefaultHostAccess(boolean allAccess) {
            return allAccess ? HostAccess.ALL : HostAccess.EXPLICIT;
        }
    },

    /**
     * Policy intended for trusted, but potentially buggy applications. In this mode any access to
     * host resources is required to be as restrictive as possible. In this mode the memory address
     * space of the guest application is shared and with the host application.
     *
     * TODO implement:
     * <ul>
     * <li>Also works in CE. So we should not require any EE features for this.
     * <li>Languages must be set explicitly.
     * <li>Native access must not be enabled.
     * <li>AllowAllAccess must be off.
     * <li>HostAccess allowPublicAccess must not be set.
     * <li>Host class loading must not be enabled
     * <li>Host lookup is allowed, but a type filter must be set.
     * <li>stdout,stderr and std in must be redirected
     * <li>Host access allow access inhertance must not be set.
     * <li>No mutable target type mappings must be set.
     * <li>If host access is not set set it to:
     *
     * <pre>
     * HostAccess.newBuilder().allowAccessAnnotatedBy(HostAccess.Export.class).allowImplementationsAnnotatedBy(HostAccess.Implementable.class).allowMutableTargetMappings().build();
     * </pre>
     *
     * <li>Only language safe to be used with a sandbox can be used
     * <li>Only instruments safe to be used with sandbox policy can be used
     * <li>Only a subset of all options are safe to be used with the sanbox policy can be used.
     * <li>Anything PolyglotAccess is ok.
     * <li>Environment access must be desabled (implement preset if not set)
     * <li>File system can be set to a custom file system. But no default file system must be used
     * (also if wrapped in a readonly one)
     * <li>Default log handler is ok, as System.err is redirected.
     * <li>IO Access must be disabled
     * <li>Experimental options are allowed.
     * <li>Process creation is not allowed
     * <li>Limits may be or not be set.
     * <li>Method scoping for host access may or may not be enabled.
     * <li>TODO allow inner contexts? should be fine, no?
     * <li>TODO can execution listeners be set?
     * <li>Thread creation may or may not be allowed.
     * <li>Arguments can be set.
     * <li>TODO Should options be inherited from System properties? I think so.
     * <li>TODO allow value sharing? Not sure.
     * <li>ServerTransport cannot be set.
     * <li>Polyglot proxies can be used.
     * <li>Host Sytem.exit must not be used. Use preset and validation (fail if it is invalid).
     * <li>Host class loader may be set.
     * <li>Working directory can be changed.
     * </ul>
     *
     *
     * TODO minimal example that fullfils the critera
     *
     *
     * @since 23.0
     */
    RELAXED {
        @Override
        HostAccess createDefaultHostAccess(boolean allAccess) {
            assert !allAccess : "All access cannot be enabled";
            return HostAccess.newBuilder().allowAccessAnnotatedBy(HostAccess.Export.class).allowImplementationsAnnotatedBy(HostAccess.Implementable.class).allowMutableTargetMappings().build();
        }
    },

    /**
     * Policy intended for trusted, but applications that might have security vulnerabilities. For
     * example, a script that processes untrusted input. Security vulnerabilities would allow an
     * attacker to compromise the guest application by providing malicious input. The memory address
     * space of the guest application is isolated from the host application in this mode.
     * <p>
     * This policy also uses all validations and presets specified for {@link #RELAXED}.
     *
     * TODO implement (everything in {@link #RELAXED})
     * <ul>
     * <li>Only works in EE (provide friendly error)
     * <li>Spawn isolate must be set or will be preset. (we know the languages so auto-detection of
     * the image should work)
     * <li>Validate HostAccess uses scopes. If HostAccess is not set use preset:
     *
     * <pre>
     * HostAccess.newBuilder().allowAccessAnnotatedBy(HostAccess.Export.class).allowImplementationsAnnotatedBy(HostAccess.Implementable.class).allowMutableTargetMappings().methoScoping(true).build();
     * </pre>
     * </ul>
     *
     *
     * TODO minimal example that fullfils the critera
     */
    ISOLATED {
        @Override
        HostAccess createDefaultHostAccess(boolean allAccess) {
            assert !allAccess : "All access cannot be enabled";
            return HostAccess.newBuilder().allowAccessAnnotatedBy(HostAccess.Export.class).allowImplementationsAnnotatedBy(HostAccess.Implementable.class).allowMutableTargetMappings().methodScoping(
                            true).build();
        }
    },

    /**
     * Policy intended for fully untrusted applications. This assumes that a malicious actor is
     * supplying the guest code itself that is being run. A strong adversarial scenario is the
     * execution of client-side Javascript in the browser that is supplied by an untrusted website.
     * In this mode the sandbox employs additional hardening mechanisms at the compiler and runtime
     * level to mitigate e.g. speculative execution attacks.
     * <p>
     * This policy also uses all validations and presets specified for {@link #ISOLATED}.
     *
     * TODO
     * <ul>
     * <li>Validate that one of the sandbox mitigations. No preset for now, just the error with
     * instructions on how to fix.
     * <li>Validate sandbox.MaxCPUTime, sandbox.MaxHeapMemory, sandbox.MaxASTDepth,
     * sandbox.MaxStackFrames and sandbox.MaxThreads is set. Recommend use of sandbox.TraceLimits?
     * </ul>
     *
     * TODO minimal example that fullfils the critera
     *
     */
    UNTRUSTED {
        @Override
        HostAccess createDefaultHostAccess(boolean allAccess) {
            return ISOLATED.createDefaultHostAccess(allAccess);
        }
    };

    abstract HostAccess createDefaultHostAccess(boolean allAccess);

}
