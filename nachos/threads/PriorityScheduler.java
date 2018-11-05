package nachos.threads;

import nachos.machine.*;

import java.util.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads based on their priorities.
 * <p/>
 * <p/>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 * <p/>
 * <p/>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 * <p/>
 * <p/>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks,` and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
	
	boolean checkPriority;
	
    public PriorityScheduler() {
    }

    /**
     * Allocate a new priority thread queue.
     *
     * @param transferPriority <tt>true</tt> if this queue should
     *                         transfer priority from waiting threads
     *                         to the owning thread.
     * @return a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new PriorityQueue(transferPriority);
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

        Lib.assertTrue(priority >= priorityMinimum &&
                priority <= priorityMaximum);

        getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMaximum)
            return false;

        setPriority(thread, priority + 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    public boolean decreasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMinimum)
            return false;

        setPriority(thread, priority - 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param thread the thread whose scheduling state to return.
     * @return the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null)
            thread.schedulingState = new ThreadState(thread);

        return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
    	
    	//tried storing threads into a linkedlist to be able to manage better 
    	//protected LinkedList<ThreadState> queue;

        PriorityQueue(boolean transferPriority) {
            this.givePriority = transferPriority;
            this.waitThread = new LinkedList<ThreadState>();
        }

        public void waitForAccess(KThread thread) {
        	
            Lib.assertTrue(Machine.interrupt().disabled());
            
            //make instance of ThreadState specifically to wait for access 
            final ThreadState threadS = getThreadState(thread);
            
            //add to the waiting for access queue 
            this.waitThread.add(threadS);
            
            threadS.waitForAccess(this);
        }

        public void acquire(KThread thread) {
        	
            Lib.assertTrue(Machine.interrupt().disabled());
            
            //make instance of threadState to acquire thread 
            final ThreadState threadS = getThreadState(thread);
            
            //if we see that the resource holder is 
            if (this.holder != null) {
                this.holder.release(this);
            }
            
            this.holder = threadS;
            
            //acquire the thread state 
            threadS.acquire(this);
            
        }

        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());

            // Pick the next thread
            final ThreadState nThread = this.pickNextThread();

            //check to see if the thread is empty 
            if (nThread == null) return null;

            // Take out the next thread from the waiting queue 
            this.waitThread.remove(nThread);

            // Call acquire to get thread 
            this.acquire(nThread.getThread());

            //return the thread we just got 
            return nThread.getThread();
        }

        //not too sure what is going on here 
        /** For testing! **/
        public ThreadState peekNext() {
            return this.pickNextThread();
        }

        /**
         * Return the next thread that <tt>nextThread()</tt> would return,
         * without modifying the state of this queue.
         *
         * @return the next thread that <tt>nextThread()</tt> would
         *         return.
         */
        
        
        protected ThreadState pickNextThread() {
        	
        	//assure the smallest priority 
            int nextPriority = priorityMinimum;
            
            //ensure the thread state is null
            ThreadState next = null;
            
            //
            for (final ThreadState currThread : this.waitThread) {
            	
                int currPriority = currThread.getEffectivePriority();
                
                //this will ensure threads have correct priority 
                if (next == null || (currPriority > nextPriority)) {
                	
                    next = currThread;
                    
                    nextPriority = currPriority;
                }
            }
            
            return next;
        }

        /**
         * This method returns the effectivePriority of this PriorityQueue.
         * The return value is cached for as long as possible. If the cached value
         * has been invalidated, this method will spawn a series of mutually
         * recursive calls needed to recalculate effectivePriorities across the
         * entire resource graph.
         * @return
         */
        
        //not too sure what is happening here 
        public int getEffectivePriority() {
        	
            if (!this.givePriority) {
            	
                return priorityMinimum;
                
            } 
            
            else if (this.changedPriority) {
            	
                // Recalculate effective priorities
                this.ePriority = priorityMinimum;
                
                for (final ThreadState curr : this.waitThread) {
                	
                    this.ePriority = Math.max(this.ePriority, curr.getEffectivePriority());
                }
                
                this.changedPriority = false;
            }
            
            return ePriority;
        }

        //optional to implement 
        public void print() {
        	
            Lib.assertTrue(Machine.interrupt().disabled());
            
            for (final ThreadState ts : this.waitThread) {
            	
                System.out.println(ts.getEffectivePriority());
                
            }
        }

        //not too sure whats going on here 
        private void invalidateCachedPriority() {
        	
            if (!this.givePriority) return;

            this.changedPriority = true;

            if (this.holder != null) {
            	
            	holder.invalidateCachedPriority();
                
            }
        }

        //changed these variables
       
        protected final List<ThreadState> waitThread;
        
        //holds the thread states 
        protected ThreadState holder = null;
        
        //var for the effective priority 
        protected int ePriority = priorityMinimum;
       
        //did the priority change
        protected boolean changedPriority = false;
        
        //check to see if the priority was given 
        public boolean givePriority;
        
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
        /**
         * Allocate a new <tt>ThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param thread the thread this state belongs to.
         */
    	
    	
        public ThreadState(KThread thread) {
        	
        	//current thread 
            this.thread = thread;

            //make a list for resources as an instance of priority queue 
            this.resourcesIHave = new LinkedList<PriorityQueue>();
            
            this.resourcesIWant = new LinkedList<PriorityQueue>();

            setPriority(priorityDefault);

        }

        /**
         * Return the priority of the associated thread.
         *
         * @return the priority of the associated thread.
         */
        
        //return priority
        public int getPriority() {
        	
            return priority;
        }

        /**
         * Return the effective priority of the associated thread.
         *
         * @return the effective priority of the associated thread.
         */
        
        //again get effective priority    not too sure how this is working, not too much changed 
        public int getEffectivePriority() {

        	//check to see if the list is empty 
            if (this.resourcesIHave.isEmpty()) {
            	
                return this.getPriority();
                
            } 
            
            //if it is not empty, change priority
            else if (this.priorityChange) {
            	
                this.effectivePriority = this.getPriority();
                
                for (final PriorityQueue pq : this.resourcesIHave) {
                	
                    this.effectivePriority = Math.max(this.effectivePriority, pq.getEffectivePriority());
                    
                }
                
                this.priorityChange = false;
            }
            
            return this.effectivePriority;
        }

        /**
         * Set the priority of the associated thread to the specified value.
         *
         * @param priority the new priority.
         */
        
        //function to set priority   not much changed
        public void setPriority(int priority) {
        	
            if (this.priority == priority)
            	
                return;
            
            this.priority = priority;
            
            // force priority invalidation
            for (final PriorityQueue pq : resourcesIWant) {
            	
                pq.invalidateCachedPriority();
                
            }
            
        }

        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * The associated thread is therefore waiting for access to the
         * resource guarded by <tt>waitQueue</tt>. This method is only called
         * if the associated thread cannot immediately obtain access.
         *
         * @param waitQueue the queue that the associated thread is
         *                  now waiting on.
         * @see nachos.threads.ThreadQueue#waitForAccess
         */
        
        //not much changed
        public void waitForAccess(PriorityQueue waitQueue) {
        	
            this.resourcesIWant.add(waitQueue);
            
            this.resourcesIHave.remove(waitQueue);
            
            waitQueue.invalidateCachedPriority();
            
        }

        /**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         *
         * @see nachos.threads.ThreadQueue#acquire
         * @see nachos.threads.ThreadQueue#nextThread
         */
        
        //not much changed
        public void acquire(PriorityQueue waitQueue) {
        	
            this.resourcesIHave.add(waitQueue);
            
            this.resourcesIWant.remove(waitQueue);
            
            this.invalidateCachedPriority();
        }

        /**
         * Called when the associated thread has relinquished access to whatever
         * is guarded by waitQueue.
          * @param waitQueue The waitQueue corresponding to the relinquished resource.
         */
        
        //not much changed
        public void release(PriorityQueue waitQueue) {
        	
            this.resourcesIHave.remove(waitQueue);
            
            this.invalidateCachedPriority();
            
        }

        //not much changed
        public KThread getThread() {
            return thread;
        }

        //not much changed
        private void invalidateCachedPriority() {
        	
            if (this.priorityChange) return;
            
            this.priorityChange = true;
            
            for (final PriorityQueue pq : this.resourcesIWant) {
            	
                pq.invalidateCachedPriority();
                
            }
        }


        //change the variables like in above 
        /**
         * The thread with which this object is associated.
         */
        protected KThread thread;
        /**
         * The priority of the associated thread.
         */
        protected int priority;

        /**
         * True if effective priority has been invalidated for this ThreadState.
         */
        protected boolean priorityChange = false;
        /**
         * Holds the effective priority of this Thread State.
         */
        protected int effectivePriority = priorityMinimum;
        /**
         * A list of the queues for which I am the current resource holder.
         */
        protected final List<PriorityQueue> resourcesIHave;
        /**
         * A list of the queues in which I am waiting.
         */
        protected final List<PriorityQueue> resourcesIWant;
    }
}
