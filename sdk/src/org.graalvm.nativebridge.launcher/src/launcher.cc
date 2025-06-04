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

#include<algorithm>
#include<atomic>
#include<cstdlib>
#include<cstdint>
#include<string>
#include<sstream>
#include<iostream>

#include<jni.h>

#if defined(__linux__)
#include<dlfcn.h>
#elif defined(__APPLE__)
#include<dlfcn.h>
#include<pthread.h>
#include<Cocoa/Cocoa.h>
#include<objc/objc-runtime.h>
#include<objc/objc-auto.h>
#elif defined(_WIN32)
#include<windows.h>
#include<libloaderapi.h>
#include<process.h>
#include<errno.h>
#include<sys/types.h>
#include<sys/stat.h>
#else
#error platform not supported or undefined
#endif

#define PROCESS_ISOLATE_ENTRY_POINT_METHOD_NAME "start"
#define PROCESS_ISOLATE_ENTRY_POINT_METHOD_SIGNATURE "([Ljava/lang/String;)I"

#define ABORT(message) \
    std::cerr << "abort: " << message << " (" __FILE__ << ":" << __LINE__ <<")" << std::endl; \
    std::abort();

#define REQUIRE_0(n, message) \
if (n != 0) {                 \
    ABORT(message)            \
}

#define REQUIRE_JNI_OK(env, code, message) \
    code;                         \
    if (env->ExceptionCheck()) {  \
        env->ExceptionClear();    \
        ABORT(message)            \
    }

struct ProcessIsolateOptions {
    std::string library_path;
    std::string entry_point_class;
    int entry_point_args_count;
    char** entry_point_args;
};

using CreateJavaVM = jint(*)(JavaVM **, void **, void *);

static CreateJavaVM loadIsolateLibrary(const std::string library_path) {
#if defined(__linux__) || defined(__APPLE__)
    void* handle = dlopen(library_path.c_str(), RTLD_NOW);
    if (handle) {
        return reinterpret_cast<CreateJavaVM>(dlsym(handle, "JNI_CreateJavaVM"));
    } else {
        const char* err = dlerror();
        std::stringstream builder {};
        builder << "Failed to load isolate library " << library_path;
        if (err) {
            builder << " due to: " << err;
        }
        ABORT(builder.str().c_str())
    }
#elif defined(_WIN32)
    HMODULE handle = LoadLibraryA(library_path.c_str());
    if (handle) {
        return reinterpret_cast<CreateJavaVM>(GetProcAddress(handle, "JNI_CreateJavaVM"));
    } else {
        std::stringstream builder {};
        builder << "Failed to load isolate library " << library_path;
        ABORT(builder.str().c_str())
    }
#else
#error platform not supported or undefined
#endif
}

static int launch_jvm(const ProcessIsolateOptions& isolate_options) {
    CreateJavaVM createVm = loadIsolateLibrary(isolate_options.library_path);
    if (!createVm) {
        std::stringstream builder {};
        builder << "Failed to lookup symbol JNI_CreateJavaVM in the isolate library " << isolate_options.library_path;
        ABORT(builder.str().c_str())
    }
    JavaVM* vm {};
    JNIEnv* env {};
    JavaVMInitArgs vm_args {JNI_VERSION_21, 0, nullptr, false};
    REQUIRE_0(createVm(&vm, reinterpret_cast<void**>(&env), &vm_args), "Failed to create VM")
    std::string entry_point_binary_name {isolate_options.entry_point_class};
    std::replace(entry_point_binary_name.begin(), entry_point_binary_name.end(), '.', '/');
    REQUIRE_JNI_OK(env, jclass jstring_class = env->FindClass("java/lang/String"), "Failed to load string class")
    REQUIRE_JNI_OK(env, jclass process_isolate_entry_point = env->FindClass(entry_point_binary_name.c_str()), "Failed to load isolate entry point class")
    REQUIRE_JNI_OK(env, jmethodID process_isolate_entry_method = env->GetStaticMethodID(process_isolate_entry_point, PROCESS_ISOLATE_ENTRY_POINT_METHOD_NAME,
                                                                                       PROCESS_ISOLATE_ENTRY_POINT_METHOD_SIGNATURE), "Failed to lookup isolate entry point method")
    REQUIRE_JNI_OK(env, jobjectArray jargs = env->NewObjectArray(isolate_options.entry_point_args_count, jstring_class, nullptr), "Failed to create arguments array")
    for (int i = 0; i < isolate_options.entry_point_args_count; i++) {
        REQUIRE_JNI_OK(env, jstring jstr = env->NewStringUTF(isolate_options.entry_point_args[i]), "Failed to create Java string for socket path")
        REQUIRE_JNI_OK(env, env->SetObjectArrayElement(jargs, i, jstr), "Failed to create arguments array")
    }
    jvalue java_call_params[1] {};
    java_call_params[0].l = jargs;
    REQUIRE_JNI_OK(env, jint result = env->CallStaticIntMethodA(process_isolate_entry_point, process_isolate_entry_method, java_call_params), "Failed to call isolate entry point main method")
    return result;
}

#if defined(__APPLE__)

static CFRunLoopSourceRef run_loop_cancel_source;
std::atomic<int>
isolate_result {0xff};

static void run_loop_cancel(void* info) {
    CFRunLoopStop(CFRunLoopGetCurrent()); // Stop the run loop
}

static CFRunLoopSourceRef create_run_loop_cancel_source() {
    CFRunLoopSourceContext context {0};
    context.perform = run_loop_cancel;
    return CFRunLoopSourceCreate(kCFAllocatorDefault, 0, &context);
}

static void* apple_run(void* options_ptr) {
    ProcessIsolateOptions options = *static_cast<ProcessIsolateOptions*>(options_ptr);
    int res = launch_jvm(options);
    isolate_result.store(res);
    CFRunLoopSourceSignal(run_loop_cancel_source);
    CFRunLoopWakeUp(CFRunLoopGetMain());
    return nullptr;
}

static void park_event_loop() {
    CFRunLoopAddSource(CFRunLoopGetCurrent(), run_loop_cancel_source, kCFRunLoopDefaultMode);
    CFRunLoopRun();
    CFRunLoopRemoveSource(CFRunLoopGetCurrent(), run_loop_cancel_source, kCFRunLoopDefaultMode);
}

/*
 * On macOS launch the isolate in a separate thread.
 * The actual main thread must run the UI event loop on macOS.
 */
static int launch_darwin(const ProcessIsolateOptions& options) {
    run_loop_cancel_source = create_run_loop_cancel_source();
    pthread_attr_t attrs {};
    pthread_t thread {};
    REQUIRE_0(pthread_attr_init(&attrs), "Failed to initialize thread attributes")
    REQUIRE_0(pthread_attr_setdetachstate(&attrs, PTHREAD_CREATE_DETACHED), "Failed to set detach thread attribute")
    REQUIRE_0(pthread_attr_setstacksize(&attrs, pthread_get_stacksize_np(pthread_self())), "Failed to set thread stack size")
    REQUIRE_0(pthread_create(&thread, &attrs, apple_run, static_cast<void*>(const_cast<ProcessIsolateOptions*>(&options))), "Failed to create thread")
    park_event_loop();
    CFRelease(run_loop_cancel_source);
    return isolate_result.load();
}
#endif

int main(int argc, char** argv) {
    if (argc < 3) {
        ABORT("usage: launcher isolate_library_path entry_point_class isolate_option*")
    }
    ProcessIsolateOptions options {argv[1], argv[2], argc - 3, argv + 3};
#if defined (__APPLE__)
    return launch_darwin(options);
#else
    return launch_jvm(options);
#endif
}
