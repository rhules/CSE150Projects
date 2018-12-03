package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A UThread is KThread that can execute user program code inside a user
 * process, in addition to Nachos kernel code.
 */ 
public class UThread extends KThread {
	/**
	 * Allocate a new UThread.
	 */
	public UThread(UserProcess process) {
		super();

		setTarget(new Runnable() {
			public void run() {
				runProgram();
			}
		});

		this.process = process;
	}

	private void runProgram() {
		process.initRegisters();
		process.restoreState();

		Machine.processor().run();

		Lib.assertNotReached();
	}

	/**
	 * Save state before giving up the processor to another thread.
	 */
	protected void saveState() {
		process.saveState();

		for (int i=0; i<Processor.numUserRegisters; i++)
			userRegisters[i] = Machine.processor().readRegister(i);

		super.saveState();
	}

	/**
	 * Restore state before receiving the processor again.
	 */      
	protected void restoreState() {
		super.restoreState();

		for (int i=0; i<Processor.numUserRegisters; i++)
			Machine.processor().writeRegister(i, userRegisters[i]);

		process.restoreState();
	}
	
	public void join(int status, UserProcess child) {
		//Lib.debug(dbgThread, "Joining to thread: " + toString());
		Lib.assertTrue(this != KThread.currentThread());

		// since we have this.status (status of thread)
		// we'll name machine status to machineStatus
		boolean machineStatus = Machine.interrupt().disable();

		// instantiate waitList;
		if (waitList == null) 
		{
			waitList = ThreadedKernel.scheduler.newThreadQueue(true);
		}

		if (status == 0) {
			return;
		} else {
			
			waitList.acquire(this);
			waitList.waitForAccess(child.getFirstThread());
			sleep();
		}

		Machine.interrupt().restore(machineStatus);

	}

	/**
	 * Storage for the user register set.
	 *
	 * <p>
	 * A thread capable of running user code actually has <i>two</i> sets of
	 * CPU registers: one for its state while executing user code, and one for
	 * its state while executing kernel code. While this thread is not running,
	 * its user state is stored here.
	 */
	public int userRegisters[] = new int[Processor.numUserRegisters];

	/**
	 * The process to which this thread belongs.
	 */
	public UserProcess process;
}
