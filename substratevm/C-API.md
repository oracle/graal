# Substrate VM C API

Substrate VM provides an API for the C language for initializing isolates and attaching threads for use with the entry point feature that is demonstrated in the [README](README.md).
The C API is available when Substrate VM is built as a shared library and its declarations are included in the header file that is generated during the build:

```c
/*
 * Structure representing an isolate. A pointer to such a structure can be
 * passed to an entry point as the execution context.
 */
struct __graal_isolate_t;
typedef struct _graal_isolate_t graal_isolate_t;

/*
 * Structure representing a thread that is attached to an isolate. A pointer to
 * such a structure can be passed to an entry point as the execution context,
 * requiring that the calling thread has been attached to that isolate.
 */
struct __graal_isolatethread_t;
typedef struct __graal_isolatethread_t graal_isolatethread_t;

/* Parameters for the creation of a new isolate. */
struct __graal_create_isolate_params_t {
    /* for future use */
};
typedef struct __graal_create_isolate_params_t graal_create_isolate_params_t;

/*
 * Create a new isolate, considering the passed parameters (which may be NULL).
 * Returns 0 on success, or a non-zero value on failure.
 * On success, the current thread is attached to the created isolate, and the
 * address of the isolate and the isolate thread structures is written to the
 * passed pointers if they are not NULL.
 */
int graal_create_isolate(graal_create_isolate_params_t* params, graal_isolate_t** isolate, graal_isolatethread_t** thread);

/*
 * Attaches the current thread to the passed isolate.
 * On failure, returns a non-zero value. On success, writes the address of the
 * created isolate thread structure to the passed pointer and returns 0.
 * If the thread has already been attached, the call succeeds and also provides
 * the thread's isolate thread structure.
 */
int graal_attach_thread(graal_isolate_t* isolate, graal_isolatethread_t** thread);

/*
 * Given an isolate to which the current thread is attached, returns the address of
 * the thread's associated isolate thread structure.  If the current thread is not
 * attached to the passed isolate or if another error occurs, returns NULL.
 */
graal_isolatethread_t* graal_get_current_thread(graal_isolate_t* isolate);

/*
 * Given an isolate thread structure, determines to which isolate it belongs and
 * returns the address of its isolate structure. If an error occurs, returns NULL
 * instead.
 */
graal_isolate_t* graal_get_isolate(graal_isolatethread_t* thread);

/*
 * Detaches the passed isolate thread from its isolate and discards any state or
 * context that is associated with it. At the time of the call, no code may still
 * be executing in the isolate thread's context.
 * Returns 0 on success, or a non-zero value on failure.
 */
int graal_detach_thread(graal_isolatethread_t* thread);

/*
 * Tears down the passed isolate, waiting for any attached threads to detach from
 * it, then discards the isolate's objects, threads, and any other state or context
 * that is associated with it.
 * Returns 0 on success, or a non-zero value on failure.
 */
int graal_tear_down_isolate(graal_isolatethread_t* thread);
```
In addition to the C level API, there is also a way to initialize an isolate
from Java and thus use Java and Substrate VM to
[implement native methods in Java](ImplementingNativeMethodsInJavaWithSVM.md).
