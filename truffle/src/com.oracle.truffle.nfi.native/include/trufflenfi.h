/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
#ifndef __TRUFFLE_NFI_H
#define __TRUFFLE_NFI_H

/**
 * Opaque handle to a {@link com.oracle.truffle.api.interop.TruffleObject}.
 */
typedef struct __TruffleObject *TruffleObject;

struct __TruffleContext;
struct __TruffleEnv;

struct __TruffleNativeAPI;
struct __TruffleThreadAPI;

#ifdef __cplusplus
/**
 * Environment pointer that can be used to call functions of the {@link struct __TruffleNativeAPI}.
 * The environment pointer can be injected as argument to native calls using the `env` datatype in
 * the function signature. For example, a native function with signature `(env, double) : double`
 * expects one `double` argument on the Truffle side, but on the native side the signature is
 * `double fn(TruffleEnv*, double)`.
 *
 * The TruffleEnv* is strictly local to the current call, it is not allowed to keep it alive after
 * the call returns. If necessary, the `TruffleContext*` can be obtained using `getTruffleContext`.
 * This object can be stored, and later used to get a fresh `TruffleEnv*`.
 */
typedef __TruffleEnv TruffleEnv;

/**
 * Reference to a Truffle {@link org.graalvm.polyglot.Context}. This can be used to attach and detach
 * new threads, and to get a reference to a `TruffleEnv*` to call {@link struct __TruffleNativeAPI}
 * functions.
 *
 * This object is valid as long as the corresponding {@link org.graalvm.polyglot.Context} is alive.
 *
 * @see struct __TruffleThreadAPI
 */
typedef __TruffleContext TruffleContext;
#else
typedef const struct __TruffleNativeAPI *TruffleEnv;
typedef const struct __TruffleThreadAPI *TruffleContext;
#endif

struct __TruffleNativeAPI {
    /**
     * Get an instance of the current TruffleContext.
     */
    TruffleContext *(*getTruffleContext)(TruffleEnv *env);

    /**
     * Create a new handle to a TruffleObject.
     *
     * TruffleObjects that are passed to native code as argument are owned by the caller. If the native
     * code wants to keep the reference, it has to call {@link newObjectRef} to create a new reference.
     * TruffleObjects that are returned from a callback are owned by the caller. The native code has to
     * call {@link releaseObjectRef} to free the reference.
     */
    TruffleObject (*newObjectRef)(TruffleEnv *env, TruffleObject object);

    /**
     * Release a handle to a TruffleObject.
     *
     * This should be called to free handles to a TruffleObject. The TruffleObject must not be used
     * afterwards.
     *
     * This (or {@link releaseAndReturn}) must be called on any TruffleObject owned by native code.
     * TruffleObjects that are returned from a callback function are owned by the native code and must
     * be released.
     */
    void (*releaseObjectRef)(TruffleEnv *env, TruffleObject object);

    /**
     * Transfer ownership of a TruffleObject to the caller.
     *
     * Similar to {@link releaseObjectRef}, this function releases the ownership of a TruffleObject.
     * The TruffleObject must not be used afterwards. Additionally, it returns a new handle to the
     * TruffleObject. This new handle is not owned by the native code. The new handle can be returned
     * to the calling Truffle code. It must not be used for anything else.
     */
    TruffleObject (*releaseAndReturn)(TruffleEnv *env, TruffleObject object);

    /**
     * Returns 1 iff object1 references the same underlying object as object2, 0 otherwise.
     */
    int (*isSameObject)(TruffleEnv *env, TruffleObject object1, TruffleObject object2);

    /**
     * Increase the reference count of a callback closure.
     *
     * Closures that are passed from Truffle to native code as function pointer are owned by the caller
     * and are freed on return. If the native code wants to keep the function pointer, it needs to call
     * {@link newClosureRef} to increase the reference count.
     *
     * Note that the closure reference count is tied to the TruffleContext that allocated the closure.
     * {@link newClosureRef}, {@link releaseClosureRef} and {@link getClosureObject} can only be called
     * from that TruffleContext.
     */
    void (*newClosureRef)(TruffleEnv *env, void *closure);

    /**
     * Decrease the reference count of a callback closure.
     *
     * Closures that are returned by callback functions as function pointers are owned by the native
     * code and need to be freed manually.
     */
    void (*releaseClosureRef)(TruffleEnv *env, void *closure);

    /**
     * Get a representation of a callback closure as TruffleObject.
     *
     * This TruffleObject holds one reference to the closure, the closure will be kept alive at least
     * as long as this TruffleObject is alive. This can be used as an alternative to {@link newClosureRef}
     * to keep closure references alive. The TruffleObject can also be passed back to managed code and
     * stored there, instead of keeping it alive on the native side.
     *
     * Passing this object back from managed code to another native function will result in the same
     * closure pointer, instead of allocating a new one.
     */
    TruffleObject (*getClosureObject)(TruffleEnv *env, void *closure);
};



struct __TruffleEnv {
    const struct __TruffleNativeAPI *functions;

#ifdef __cplusplus
    TruffleContext *getTruffleContext() {
        return functions->getTruffleContext(this);
    }

    TruffleObject newObjectRef(TruffleObject object) {
        return functions->newObjectRef(this, object);
    }

    void releaseObjectRef(TruffleObject object) {
        functions->releaseObjectRef(this, object);
    }

    TruffleObject releaseAndReturn(TruffleObject object) {
        return functions->releaseAndReturn(this, object);
    }

    int isSameObject(TruffleObject object1, TruffleObject object2) {
        return functions->isSameObject(this, object1, object2);
    }

    template<class T> void newClosureRef(T *closure) {
        functions->newClosureRef(this, (void*) closure);
    }

    template<class T> void releaseClosureRef(T *closure) {
        functions->releaseClosureRef(this, (void*) closure);
    }

    /**
     * Convenience function that calls {@link newClosureRef} on a function pointer, and returns the
     * same function pointer without losing type information.
     */
    template<class T> T *dupClosureRef(T *closure) {
        functions->newClosureRef(this, (void*) closure);
        return closure;
    }

    template<class T> TruffleObject getClosureObject(T *closure) {
        return functions->getClosureObject(this, (void*) closure);
    }
#endif
};


struct __TruffleThreadAPI {
    /**
     * Returns the TruffleEnv of the current thread, or NULL if the current thread is not attached.
     */
    TruffleEnv *(*getTruffleEnv)(TruffleContext *ctx);

    /**
     * Attaches the current thread.
     */
    TruffleEnv *(*attachCurrentThread)(TruffleContext *ctx);

    /**
     * Detaches the current thread.
     */
    void (*detachCurrentThread)(TruffleContext *ctx);
};

struct __TruffleContext {
    const struct __TruffleThreadAPI *functions;

#ifdef __cplusplus
    TruffleEnv *getTruffleEnv() {
        return functions->getTruffleEnv(this);
    }

    TruffleEnv *attachCurrentThread() {
        return functions->attachCurrentThread(this);
    }

    void detachCurrentThread() {
        return functions->detachCurrentThread(this);
    }
#endif
};


#endif
