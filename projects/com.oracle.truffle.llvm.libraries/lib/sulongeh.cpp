//===----------------------------------------------------------------------===//
//
// This file is dual licensed under the MIT and the University of Illinois Open
// Source Licenses. See LICENSE.TXT for details.
//
//===----------------------------------------------------------------------===//

#include <typeinfo>
#include <stdio.h>
#include <stdlib.h>

#include "cxxabi.h"
#include "unwind.h"
#include <exception>   

typedef void (*destructorFunction)(void *);

namespace __cxxabiv1 {
class __shim_type_info : public std::type_info {
public:
  virtual ~__shim_type_info();

  virtual void noop1() const;
  virtual void noop2() const;
  virtual bool can_catch(const __shim_type_info *thrown_type,
                                           void *&adjustedPtr) const = 0;
};

class __pbase_type_info : public __shim_type_info {
public:
  unsigned int __flags;
  const __shim_type_info *__pointee;

  enum __masks {
    __const_mask = 0x1,
    __volatile_mask = 0x2,
    __restrict_mask = 0x4,
    __incomplete_mask = 0x8,
    __incomplete_class_mask = 0x10,
    __transaction_safe_mask = 0x20,
    // This implements the following proposal from cxx-abi-dev (not yet part of
    // the ABI document):
    //
    //   http://sourcerytools.com/pipermail/cxx-abi-dev/2016-October/002986.html
    //
    // This is necessary for support of http://wg21.link/p0012, which permits
    // throwing noexcept function and member function pointers and catching
    // them as non-noexcept pointers.
    __noexcept_mask = 0x40,

    // Flags that cannot be removed by a standard conversion.
    __no_remove_flags_mask = __const_mask | __volatile_mask | __restrict_mask,
    // Flags that cannot be added by a standard conversion.
    __no_add_flags_mask = __transaction_safe_mask | __noexcept_mask
  };

  virtual ~__pbase_type_info();
  virtual bool can_catch(const __shim_type_info *,
                                           void *&) const;
};


}

struct __cxa_exception {
#if defined(__LP64__) || LIBCXXABI_ARM_EHABI
    // This is a new field to support C++ 0x exception_ptr.
    // For binary compatibility it is at the start of this
    // struct which is prepended to the object thrown in
    // __cxa_allocate_exception.
    size_t referenceCount;
#endif

    //  Manage the exception object itself.
    std::type_info *exceptionType;
    destructorFunction exceptionDestructor;
    std::unexpected_handler unexpectedHandler;
    std::terminate_handler  terminateHandler;

    __cxa_exception *nextException;

    int handlerCount;

#if LIBCXXABI_ARM_EHABI
    __cxa_exception* nextPropagatingException;
    int propagationCount;
#else
    int handlerSwitchValue;
    const unsigned char *actionRecord;
    const unsigned char *languageSpecificData;
    void *catchTemp;
    void *adjustedPtr;
#endif

#if !defined(__LP64__) && !LIBCXXABI_ARM_EHABI
    // This is a new field to support C++ 0x exception_ptr.
    // For binary compatibility it is placed where the compiler
    // previously adding padded to 64-bit align unwindHeader.
    size_t referenceCount;
#endif

    _Unwind_Exception unwindHeader;
};

__cxa_exception *getCXAException(void *ptr) {
    _Unwind_Exception* unwind_exception = static_cast<_Unwind_Exception*>(ptr);
    void *data = unwind_exception + 1;
    return static_cast<__cxa_exception*>(data) - 1;
}

extern "C"
unsigned int sulong_eh_canCatch(void *ptr, std::type_info *excpType, std::type_info *catchType) {
    void *p = ptr;
    __cxxabiv1::__shim_type_info *et = dynamic_cast<__cxxabiv1::__shim_type_info*>(excpType);
	__cxxabiv1::__shim_type_info *ct = dynamic_cast<__cxxabiv1::__shim_type_info*>(catchType);
    if (et == NULL || ct == NULL) {
        fprintf(stderr, "Type error in sulong_eh_canCatch(...).\n");
        abort();
    }
	return ct->can_catch(et, p);	
}

extern "C"
void *sulong_eh_unwindHeader(void *ptr) {
	__cxa_exception* eh = static_cast<__cxa_exception*>(ptr) - 1; // get the exception object, which is before the ptr to the thrown object
	return &eh->unwindHeader;
}

extern "C"
void *sulong_eh_getExceptionPointer(void *unwindHeader) {
    _Unwind_Exception* unwind_exception = static_cast<_Unwind_Exception*>(unwindHeader);
    return unwind_exception + 1;
}

extern "C"
void *sulong_eh_getThrownObject(void *unwindHeader) {
    _Unwind_Exception* unwind_exception = static_cast<_Unwind_Exception*>(unwindHeader);
    void *data = unwind_exception + 1;
    __cxa_exception *eh = static_cast<__cxa_exception*>(data) - 1;
	std::type_info *exceptionType = eh->exceptionType;
	if (dynamic_cast<__cxxabiv1::__pbase_type_info*>(exceptionType)) {
		// TODO: why is this necessary?!
		void **pData = static_cast<void**>(data);
		return *pData;
	} else {
		return data;
	}
}

extern "C"
void sulong_eh_throw(void *ptr, std::type_info *type, destructorFunction destructor, void (*unexpectedHandler)(), void (*terminateHandler)()) {
	// we fill the exception as usefull as possible for Sulong
    __cxa_exception* eh = static_cast<__cxa_exception*>(ptr) - 1; // get the exception object, which is before the ptr to the thrown object
    eh->unexpectedHandler = unexpectedHandler;
    eh->terminateHandler  = terminateHandler;
    eh->exceptionType = type;
    eh->exceptionDestructor = destructor;
    eh->unwindHeader.exception_class = 0x434C4E47432B2B00; // clangs exception class: "CLNGC++\0"
    eh->referenceCount = 1;
    eh->handlerCount = 0;
}

extern "C"
destructorFunction sulong_eh_getDestructor(void *ptr) {
    __cxa_exception* eh = getCXAException(ptr);
    return eh->exceptionDestructor;
}

extern "C"
void *sulong_eh_getType(void *ptr) {
    __cxa_exception* eh = getCXAException(ptr);
    return eh->exceptionType;
}

extern "C"
void sulong_eh_incrementHandlerCount(void *ptr) {
    __cxa_exception* eh = getCXAException(ptr);
    eh->handlerCount++;
}

extern "C"
void sulong_eh_decrementHandlerCount(void *ptr) {
    __cxa_exception* eh = getCXAException(ptr);
    eh->handlerCount--;
}

extern "C"
int sulong_eh_getHandlerCount(void *ptr) {
    __cxa_exception* eh = getCXAException(ptr);
    return eh->handlerCount;
}

extern "C"
void sulong_eh_setHandlerCount(void *ptr, int value) {
    __cxa_exception* eh = getCXAException(ptr);
    eh->handlerCount = value;
}

extern "C"
void *getNullPointer() {
    return (void*) 0;
}