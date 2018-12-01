package nachos.threads;

import nachos.machine.*;

import java.util.Random;

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
public class LotteryScheduler extends PriorityScheduler {
    /**
     * Allocate a new lottery scheduler.
     */
    public LotteryScheduler() {
    }

    public static final int priorityDefault = 1;
    //values of the min and max priorities were changed 
    public static final int priorityMinimum = 1;
    public static final int priorityMaximum = Integer.MAX_VALUE;

    @Override
    public void setPriority(KThread thread, int priority) {
        Lib.assertTrue(Machine.interrupt().disabled());

        //set the condition of the priorities 
        Lib.assertTrue(priority >= priorityMinimum &&
                       priority <= priorityMaximum);

        getThreadState(thread).setPriority(priority);
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

    //override getThreadState function previously inside priority scheduler 
    //the new function checks for null and if it does, assigns the scheduling state to lottery 
    //we will then return the thread state of this thread 
    @Override
    protected ThreadState getThreadState(KThread thread) {
    	
        if (thread.schedulingState == null)
            thread.schedulingState = new LotteryThreadState(thread);

        return (ThreadState) thread.schedulingState;
    }

    //since we dealt with priority queues, we must now make lottery queues 
    protected class LotteryQueue extends PriorityQueue {
    	
    	private final Random rand;
    	boolean transferPriority;
    	
    	//this will randomize 
        LotteryQueue(boolean transferPriority) {
        	
            super(transferPriority);
            //assign a random number here 
            this.rand = new Random();
            
        }
        
        //override previous get effective priority because we are now
        //getting effective priorities a different way 
        @Override
        public int getEffectivePriority() {
        	
            if (!this.givePriority) {
            	
                return priorityMinimum;
                
            } else if (this.changedPriority) {
            	
                // recalculate effective priorities
                this.efficientPriority = priorityMinimum;
                
                for (final ThreadState cur : this.waitThread) {
                	
                    Lib.assertTrue(cur instanceof LotteryThreadState);
                    
                    this.efficientPriority += cur.getEffectivePriority();
                }
                this.changedPriority = false;
            }
            
            return efficientPriority;

        }
        
        //we will also not just pick the next thread
        //we will chose thread that has won the lottery 
        @Override
        public ThreadState pickNextThread() {
        	
            int totalTickets = this.getEffectivePriority();
            
            int winningTicket = totalTickets > 0 ? rand.nextInt(totalTickets) : 0;
            
            for (final ThreadState thread : this.waitThread) {
            	
                Lib.assertTrue(thread instanceof LotteryThreadState);
                
                winningTicket -= thread.getEffectivePriority();
                
                if (winningTicket <= 0) {
                    return thread;
                }
            }

            return null;
        }


    }
    
    protected class LotteryThreadState extends ThreadState {
    	
        public LotteryThreadState(KThread thread) {
        	
            super(thread);
        }
        
        //override effective priority because we get them a different way instead 
        //of just checking that its empty 
        @Override
        public int getEffectivePriority() {
        	
            if (this.resourcesIHave.isEmpty()) {
            	
                return this.getPriority();
                
            } else if (this.priorityChange) {
            	
                this.effectivePriority = this.getPriority();
                
                for (final PriorityQueue pq : this.resourcesIHave) {
                	
                    Lib.assertTrue(pq instanceof LotteryQueue);
                    
                    this.effectivePriority += pq.getEffectivePriority();
                }
                
                this.priorityChange = false;
            }
            
            return this.effectivePriority;
        }
    }
}

