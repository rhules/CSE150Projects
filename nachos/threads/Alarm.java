package nachos.threads;

import java.util.Iterator;
import java.util.LinkedList;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 *
	 * <p><b>Note</b>: Nachos will not function correctly with more than one
	 * alarm.
	 */
	
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() { timerInterrupt(); }
		});
		
		lock = new Lock();
		waitQueue = new LinkedList<wakeAlarmThread>();
		
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread
	 * that should be run.
	 */
	public void timerInterrupt() {

		// disable interrupts;
		boolean status = Machine.interrupt().disable();

		//int i = 0;
		wakeAlarmThread threadNext = null;
		
		
		Iterator<wakeAlarmThread> i =waitQueue.iterator();
		while(i.hasNext()) {
			// to see if the thread is ready to be awaken;
			threadNext = (wakeAlarmThread) i.next(); 
			
			// and if ready to wake up
			if (threadNext.wake <= Machine.timer().getTime()) {
				waitQueue.remove(i);
				threadNext.waitThread.ready(); // now waiting thread is ready
			}
		}
		
//			
//			// right amount of time? 
//			if (Machine.timer().getTime() == 500)
//			//if (waitThread <= Machine.timer().getTime() )
//			{
//				// remove from waitQueue to ready;
//				((KThread) waitQueue.remove(i)).ready();
//
//			}
//		}

		// restore interrupts;
		Machine.interrupt().restore(status);

		KThread.currentThread().yield();

	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks,
	 * waking it up in the timer interrupt handler. The thread must be
	 * woken up (placed in the scheduler ready set) during the first timer
	 * interrupt where
	 *
	 * <p><blockquote>
	 * (current time) >= (WaitUntil called time)+(x)
	 * </blockquote>
	 *
	 * @param	x	the minimum number of clock ticks to wait.
	 *
	 * @see	nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		// given; 
		long wakeTime = Machine.timer().getTime() + x;
		//	while (wakeTime > Machine.timer().getTime())
		
		// disable interrupts; 
		boolean status = Machine.interrupt().disable();
		
		// acquire the lock;
		lock.acquire();
		
		thread = new wakeAlarmThread(wakeTime, KThread.currentThread());
		
		// add the thread to the waitQueue;
		waitQueue.add(thread);
		
		// release lock and let thread sleep;
		lock.release();
		KThread.sleep();
		
		// restore interrupts;
		Machine.interrupt().restore(status);

		KThread.yield();
	}
	
	private Lock lock;
	// private LinkedList<Long> sQueue;
	private LinkedList<wakeAlarmThread> waitQueue;
	private wakeAlarmThread thread;
	

}

// implement waiting for thread to wake up time
class wakeAlarmThread {
	long wake;
	KThread waitThread;
	
	public wakeAlarmThread (long wTime, KThread wThread) {
		this.wake = wTime;
		this.waitThread = wThread;
	}
	
}

