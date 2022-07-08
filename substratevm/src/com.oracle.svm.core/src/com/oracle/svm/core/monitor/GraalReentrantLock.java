package com.oracle.svm.core.monitor;

//import jdk.internal.vm.annotation.ReservedStackAccess;

import java.util.concurrent.locks.Condition;

public class GraalReentrantLock {
	private final Sync sync;

	public abstract static class Sync extends GraalAbstractQueuedSynchronizer {
		public GraalConditionObject graalConditionObject; //change to private?

		public GraalConditionObject getGraalConditionObject() {
			return graalConditionObject;
		}

		Sync() {
		}

		/**
		 * Performs non-fair tryLock.
		 */
//		@ReservedStackAccess
		final boolean tryLock() {
			Thread current = Thread.currentThread();
			int c = getState();
			if (c == 0) {
				if (compareAndSetState(0, 1)) {
					setExclusiveOwnerThread(current);
					return true;
				}
			} else if (getExclusiveOwnerThread() == current) {
				if (++c < 0) // overflow
					throw new Error("Maximum lock count exceeded");
				setState(c);
				return true;
			}
			return false;
		}

		/**
		 * Checks for reentrancy and acquires if lock immediately available under fair
		 * vs nonfair rules. Locking methods perform initialTryLock check before
		 * relaying to corresponding AQS acquire methods.
		 */
		abstract boolean initialTryLock();

//		@ReservedStackAccess
		final void lock() {
			boolean initialSuccess = initialTryLock();
			if (!initialSuccess) {
				acquire(1);
			} else {
			}
		}

//		@ReservedStackAccess
		protected final boolean tryRelease(int releases) {
			int c = getState() - releases; //state must be 0 here
			if (getExclusiveOwnerThread() != Thread.currentThread()) {
				throw new IllegalMonitorStateException(); //owner is null and c =-1
			}
			boolean free = (c == 0);
			if (free) {
				setExclusiveOwnerThread(null);
			}
			setState(c);
			return free;
		}

		protected final boolean isHeldExclusively() {
			// While we must in general read state before owner,
			// we don't need to do so to check if current thread is owner
			return getExclusiveOwnerThread() == Thread.currentThread();
		}

		final boolean isLocked() {
			return this.getState() != 0;
		}

		final GraalConditionObject newCondition() {
			return new GraalConditionObject();
		}

	}

	protected final Thread getExclusiveOwnerThread() {
		return sync.getExclusiveOwnerThread1();
	}

	protected final void setExclusiveOwnerThread(Thread thread) {
		sync.setExclusiveOwnerThread1(thread);
	}

	public Sync getSync() {
		return sync;
	}

	public GraalAbstractQueuedSynchronizer.GraalConditionObject getCondition() {
		return sync.getGraalConditionObject();
	}

	public void setCondition(GraalAbstractQueuedSynchronizer.GraalConditionObject newCondition) {
		sync.graalConditionObject = newCondition;
	}

	public static final class GraalNonfairSync extends GraalReentrantLock.Sync {
		GraalNonfairSync() {
		}
		final boolean initialTryLock() {
			Thread current = Thread.currentThread();
			if (compareAndSetState(0, 1)) { // first attempt is unguarded
				setExclusiveOwnerThread(current);
				return true;
			} else if (getExclusiveOwnerThread() == current) {
				int c = getState() + 1;
				if (c < 0) // overflow
					throw new Error("Maximum lock count exceeded");
				setState(c);
				return true;
			} else
				return false;
		}

		protected final boolean tryAcquire(int acquires) {
			if (getState() == 0 && compareAndSetState(0, acquires)) {
				setExclusiveOwnerThread(Thread.currentThread());
				return true;
			}
			return false;
		}
	}

	public GraalReentrantLock() {
		sync = new GraalReentrantLock.GraalNonfairSync();
	}

	public void lock() {
		sync.lock();
	}

	public boolean tryLock() {
		return sync.tryLock();
	}

	public void unlock() {
		if (isLocked()) { //why doesnt open jdk check this? Should we?
			sync.release(1);
		}
//		else {
//			System.out.println("nothing to unlock");
//		}
	}

	public boolean isLocked() {
		return sync.isLocked();
	}

	public boolean isHeldByCurrentThread() {
		return sync.isHeldExclusively();
	}

	public Condition newCondition() {
		return sync.newCondition();
	}

}
