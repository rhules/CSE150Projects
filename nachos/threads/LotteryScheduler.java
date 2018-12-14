package nachos.threads;

import nachos.machine.*;

import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.LinkedList;


/**
 * A scheduler that chooses threads using a lottery.
 * <p/>
 * <p/>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 * <p/>
 * <p/>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 * <p/>
 * <p/>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends Scheduler {
	/**
	 * Allocate a new lottery scheduler.
	 */
	public LotteryScheduler() {
	}

	/**
	 * Allocate a new lottery thread queue.
	 *
	 * @param    transferPriority    <tt>true</tt> if this queue should
	 * transfer tickets from waiting threads
	 * to the owning thread.
	 * @return a new lottery thread queue.
	 */

	//function to check the transfer priority and return it in the lottery queue 
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new LotteryQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());
		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());
		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(priority >= priorityMinimum && priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean status = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);

		if (priority == priorityMaximum) {
			return false;
		}

		setPriority(thread, priority + 1);

		Machine.interrupt().restore(status);

		return true;
	}

	public boolean decreasePriority() {
		boolean status = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);

		if (priority == priorityMinimum) {
			return false;
		}

		setPriority(thread, priority - 1);

		Machine.interrupt().restore(status);

		return true;
	}

	public static final int priorityDefault = 1;
	public static final int priorityMinimum = 0;
	public static final int priorityMaximum = 7;



	protected ThreadState getThreadState(KThread thread) {

		if (thread.schedulingState == null) {
			thread.schedulingState = new ThreadState(thread);
		}
		return (ThreadState) thread.schedulingState;
	}

	//since we dealt with priority queues, we must now make lottery queues 
	protected class LotteryQueue extends ThreadQueue {

		LotteryQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());

			if (!thread.getName().equals("main")) {
				getThreadState(thread).acquire(this);
			}

		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			if (waitQueue.isEmpty()) {
				return null;
			}

			int tickets = 0;

			for (int i = 0; i < waitQueue.size(); i++) {
				ThreadState thread = waitQueue.get(i);
				tickets = tickets + thread.getEffectivePriority();
			}

			int win = Lib.random(tickets + 1);

			int remainTickets = 0;
			KThread winningThread = null;
			ThreadState thread = null;

			for (int i = 0; i < waitQueue.size(); i++) {
				thread = waitQueue.get(i);
				remainTickets = remainTickets + thread.getEffectivePriority();
				if (remainTickets >= win) {
					winningThread = thread.thread;
					break;
				}
			}

			if(winningThread != null) {
				waitQueue.remove(thread);
			}

			return winningThread;



		}


		protected ThreadState pickNextThread() {
			return null;
		}


		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());

		}


		public LinkedList <ThreadState> waitQueue = new LinkedList<ThreadState>();
		public boolean transferPriority;
		public ThreadState linkThread = null;
		private int index;

	}

	protected class ThreadState {

		public ThreadState(KThread thread) {
			this.thread = thread;
			setPriority(priorityDefault);
			waitQueue = new LotteryQueue(true);
		}

		public int getPriority() {
			return priority;
		}

		public int getEffectivePriority() {
			effectivePriority = priority;

			for (int i = 0; i < waitQueue.waitQueue.size(); i++) {
				effectivePriority = effectivePriority + waitQueue.waitQueue.get(i).effectivePriority;
			} 

			return effectivePriority;
		}



		public void setPriority(int priority) {
			if (this.priority == priority) {
				return;
			}
			
			this.priority = priority;
		}


		public void waitForAccess(LotteryQueue waitQueue) {
			waitQueue.waitQueue.add(this);

			if (waitQueue.linkThread != this && waitQueue.linkThread != null) {
				waitQueue.linkThread.waitQueue.waitForAccess(this.thread);
			}


		}

		public void acquire(LotteryQueue waitQueue) {
			waitQueue.linkThread = this;
		}

		protected int priority;
		protected int effectivePriority;
		protected LotteryQueue waitQueue;
		protected KThread thread;

	}
}
