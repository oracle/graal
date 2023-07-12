;;
;; Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
;; DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
;;
;; The Universal Permissive License (UPL), Version 1.0
;;
;; Subject to the condition set forth below, permission is hereby granted to any
;; person obtaining a copy of this software, associated documentation and/or
;; data (collectively the "Software"), free of charge and under any and all
;; copyright rights in the Software, and any and all patent rights owned or
;; freely licensable by each licensor hereunder covering either (i) the
;; unmodified Software as contributed to or provided by such licensor, or (ii)
;; the Larger Works (as defined below), to deal in both
;;
;; (a) the Software, and
;;
;; (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
;; one is included with the Software each a "Larger Work" to which the Software
;; is contributed by such licensors),
;;
;; without restriction, including without limitation the rights to copy, create
;; derivative works of, display, perform, and distribute the Software and make,
;; use, sell, offer for sale, import, export, have made, and have sold the
;; Software and the Larger Work(s), and to sublicense the foregoing rights on
;; either these or other terms.
;;
;; This license is subject to the following condition:
;;
;; The above copyright notice and either this complete permission notice or at a
;; minimum a reference to the UPL must be included in all copies or substantial
;; portions of the Software.
;;
;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
;; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
;; FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
;; AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
;; LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
;; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
;; SOFTWARE.
;;
(module
    ;; Declare 1 page (64Kib) of shared memory.
    (memory 1 1 shared)

    (func (export "_main") (result i32)
        i32.const 0
        call $lockMutex
        i32.const 0
        call $unlockMutex
        i32.const 5
    )

    ;; Try to lock a mutex at the given address.
    ;; Returns 1 if the mutex was successfully locked, and 0 otherwise.
    (func $tryLockMutex (export "tryLockMutex")
        (param $mutexAddr i32) (result i32)
        ;; Attempt to grab the mutex. The cmpxchg operation atomically
        ;; does the following:
        ;; - Loads the value at $mutexAddr.
        ;; - If it is 0 (unlocked), set it to 1 (locked).
        ;; - Return the originally loaded value.
        (i32.atomic.rmw.cmpxchg
            (local.get $mutexAddr) ;; mutex address
            (i32.const 0)          ;; expected value (0 => unlocked)
            (i32.const 1))         ;; replacement value (1 => locked)

        ;; The top of the stack is the originally loaded value.
        ;; If it is 0, this means we acquired the mutex. We want to
        ;; return the inverse (1 means mutex acquired), so use i32.eqz
        ;; as a logical not.
        (i32.eqz)
    )

    ;; Lock a mutex at the given address, retrying until successful.
    (func $lockMutex (export "lockMutex")
        (param $mutexAddr i32)
        (block $done
            (loop $retry
                ;; Try to lock the mutex. $tryLockMutex returns 1 if the mutex
                ;; was locked, and 0 otherwise.
                (call $tryLockMutex (local.get $mutexAddr))
                (br_if $done)

                ;; Wait for the other agent to finish with mutex.
                (memory.atomic.wait32
                    (local.get $mutexAddr) ;; mutex address
                    (i32.const 1)          ;; expected value (1 => locked)
                    (i64.const -1))        ;; infinite timeout

                ;; memory.atomic.wait32 returns:
                ;;     0 => "ok", woken by another agent.
                ;;     1 => "not-equal", loaded value != expected value
                ;;     2 => "timed-out", the timeout expired
                ;;
                ;; Since there is an infinite timeout, only 0 or 1 will be returned. In
                ;; either case we should try to acquire the mutex again, so we can
                ;; ignore the result.
                (drop)

                ;; Try to acquire the lock again.
                (br $retry)
            )
        )
    )

    ;; Unlock a mutex at the given address.
    (func $unlockMutex (export "unlockMutex")
        (param $mutexAddr i32)
        ;; Unlock the mutex.
        (i32.atomic.store
            (local.get $mutexAddr) ;; mutex address
            (i32.const 0))         ;; 0 => unlocked

        ;; Notify one agent that is waiting on this lock.
;;        (drop
;;            (memory.atomic.notify
;;                (local.get $mutexAddr) ;; mutex address
;;                (i32.const 1)))        ;; notify 1 waiter
    )
)