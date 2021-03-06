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
	Condition2 speakerInQueue;
	//Condition2 listenerQueue;
	//Condition2 speakerQueue;
	int waitingListener = 0; 
	int waitingSpeaker = 0;
	boolean speakerReady = false;
	
	int getWord;
	boolean wordReady = false;
   
	public Communicator() {
		activeSpeaker = new Condition2(lock);
		activeListener = new Condition2(lock);
		//listenerQueue = new Condition2(lock);
		speakerInQueue = new Condition2(lock);
		
	 	
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
    	 /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    	lock.acquire();
    	waitingSpeaker++; //add one speaker
		speakerReady = true;
		while(waitingSpeaker == 0 || wordReady){  //sleep if no word
			
			activeSpeaker.sleep();
		}
		//waitingListener--;
		
		
		
		getWord = word; // recieved word
		wordReady = true; // check if word is ready
		activeListener.wake(); //ready to listen
		speakerInQueue.sleep(); 
		waitingSpeaker--;
		speakerReady = false;
		
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
		
		while(waitingSpeaker == 0 || !wordReady) 
			activeListener.sleep();
		
		int gotWord = getWord;
		wordReady = false; //word was listened to
		
		activeSpeaker.wake();  //wake up after giving up word
		speakerInQueue.wake(); //wake up any other waiting speaker
		
		lock.release();
		return gotWord;
	
		//return 0;
    }
   
}