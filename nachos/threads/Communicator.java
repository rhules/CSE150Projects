package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
	Lock lock = new Lock();
	
	Condition2 activeSpeaker;
	Condition2 activeListener;
	Condition2 listenerQueue;
	Condition2 speakerQueue;
	boolean waitingListener = false; 
	boolean waitingSpeaker = false;
	
	int getWord;
	boolean wordReady;
   
	public Communicator() {
		activeSpeaker = new Condition2(lock);
		activeListener = new Condition2(lock);
		listenerQueue = new Condition2(lock);
		speakerQueue = new Condition2(lock);
		
	 	
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
    	
    	lock.acquire();
    	waitingSpeaker = true;
		while(waitingListener){ 
			//Add speaker to waiting queue
			waitingSpeaker = true;
			activeListener.sleep();
		}
		waitingListener = false;
		//Prevent other speakers from speaking
		
		
		getWord = word;
		activeSpeaker.wake();

		
	
		lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
    	lock.acquire();
		waitingListener = true;
		activeListener.wake();
		while(waitingSpeaker = false)
			activeSpeaker.sleep();
		waitingSpeaker = false;
		lock.release();
		return getWord;
	
		//return 0;
    }
   
}