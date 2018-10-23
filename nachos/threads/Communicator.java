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
	
	Condition activeSpeaker;
	Condition activeListener;
	Condition listenerQueue;
	Condition speakerQueue;
	boolean waitingListener = false; 
	boolean waitingSpeaker = false;
	
	int getWord;
	boolean wordReady;
   
	public Communicator() {
    	
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
		while(waitingSpeaker){ 
			//Add speaker to waiting queue
			speakerQueue.sleep();
		}

		//Prevent other speakers from speaking
		waitingSpeaker = true;

		getWord = word;
		while(!waitingListener || wordReady == true){
			activeListener.wake(); 
			activeSpeaker.sleep(); 
		}

		wordReady = false;
		speakerQueue.wake(); //wake up a waiting speaker
		listenerQueue.wake();
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
		while(waitingListener){
			listenerQueue.sleep();
		}

		waitingListener = true;
	

		while(!waitingSpeaker){ //no speaker, go into loop
			activeListener.sleep();
		}

		
		activeSpeaker.wake(); 
		int word = getWord;
		wordReady = true;
		lock.release();
		return word;
	
		//return 0;
    }
   
}
