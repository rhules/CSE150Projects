package nachos.threads;

import nachos.ag.BoatGrader;
import nachos.machine.Machine;

import java.util.Vector;

public class Boat {
	static BoatGrader bg;
	static boolean boatOnOahu;
	static int cKnownOnOahu;
	static int cKnownOnMolokai;
	static int aKnownOnOahu;
	static int aKnownOnMolokai;
	static Lock l;
	static Condition2 cWaitingInBoat;
	static Vector<KThread> waiting;
	static Condition2 cWaitingOnOahu;
	static Condition2 cWaitingOnMolokai;
	static Condition2 aWaitingOnOahu;
	static Condition2 aWaitingOnMolokai;

	public static void selfTest() {
		BoatGrader b = new BoatGrader();

		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(0, 2, b);

		// System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
		// begin(1, 2, b);

		// System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		// begin(3, 3, b);
	}

	public static void begin(int adults, int children, BoatGrader b) {
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here
		// assume the boat starts on Oahu
		boatOnOahu = true;
		l = new Lock();
		cWaitingInBoat = new Condition2(l);
		cWaitingOnOahu = new Condition2(l);
		cWaitingOnMolokai = new Condition2(l);
		aWaitingOnOahu = new Condition2(l);
		aWaitingOnMolokai = new Condition2(l);
		waiting = new Vector<KThread>();
		/*
		 * The initial number of people (children and adults) on Oahu is known by
		 * everyone, because they are all present at the start of the simulation. After
		 * this point, it will be treated as if everyone is keeping track of the numbers
		 * by incrementing and decrementing these variables whenever someone leaves or
		 * arrives at one of the islands.
		 */
		cKnownOnMolokai = 0;
		aKnownOnMolokai = 0;
		cKnownOnOahu = children;
		aKnownOnOahu = adults;

		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.

		Runnable a = new Runnable() {
			public void run() {
				adultItinerary();
			}
		};
		for (int i = adults; i > 0; i--) {
			KThread temp = new KThread(a);
			temp.setName("Adult on Oahu");
			aKnownOnOahu++;
			temp.fork();
		}

		Runnable c = new Runnable() {
			public void run() {
				childItinerary();
			}
		};
		for (int i = children; i > 0; i--) {
			KThread temp = new KThread(c);
			temp.setName("Child on Oahu");
			cKnownOnOahu++;
			temp.fork();
		}

		/*
		 * Runnable r = new Runnable() { public void run() { SampleItinerary(); } };
		 */

		// KThread t = new KThread(r);
		// t.setName("Sample Boat Thread");
		// t.fork();

	}

	static void adultItinerary() {
		/*
		 * This is where you should put your solutions. Make calls to the BoatGrader to
		 * show that it is synchronized. For example: bg.AdultRowToMolokai(); indicates
		 * that an adult has rowed the boat across to Molokai
		 */
		l.acquire();
		while (!isDone()) {
			if (!boatOnOahu) {
				if (cKnownOnMolokai == 0) {
					//if there is only one child in the system, end here to avoid infinite looping
					if(cKnownOnOahu < 2) {
						Machine.terminate();
					}
					//if not on Molokai, wake an adult that is
					else if(KThread.currentThread().getName().equals("Adult on Oahu")) {
						if(cKnownOnMolokai > 0) {
							//prioritize sending children from Molokai
							cWaitingOnMolokai.wake();
						}
						else if(aKnownOnMolokai > 0) {
							aWaitingOnMolokai.wake();
						}
						aWaitingOnOahu.sleep();
					}
					//otherwise, row to Oahu
					else {
						bg.AdultRowToOahu();
						boatOnOahu = true;
						KThread.currentThread().setName("Adult on Oahu");
						aKnownOnMolokai--;
						aKnownOnOahu++;
						if(cKnownOnOahu > 0) {
							cWaitingOnOahu.wake();
						}
						else {
							aWaitingOnOahu.wake();
						}
						aWaitingOnMolokai.sleep();
					}
				}
				// otherwise, wake up a child thread on Molokai
				else {
					cWaitingOnMolokai.wake();
					aWaitingOnOahu.sleep();
				}
			} 
			//if the boat is on Oahu, but this thread isn't, wake a thread that is
			else if(KThread.currentThread().getName().equals("Adult on Molokai")) {
				//prioritize waking children, if any
				if(cKnownOnOahu > 0) {
					cWaitingOnOahu.wake();
				}
				else {
					aWaitingOnOahu.wake();
				}
				aWaitingOnMolokai.sleep();
			}
			else if (cKnownOnOahu > 1) {
				//send any children first, so long as there's more than one
				cWaitingOnOahu.wake();
				aWaitingOnOahu.sleep();
			}
			else if (waiting.size() == 0) {
				// if there isn't a child waiting in the boat, row to Molokai
				bg.AdultRowToMolokai();
				boatOnOahu = false;
				KThread.currentThread().setName("Adult On Molokai");
				aKnownOnOahu--;
				aKnownOnMolokai++;
				if(cKnownOnMolokai > 0) {
					cWaitingOnMolokai.wake();
				}
				else if(aKnownOnMolokai > 1){
					aWaitingOnMolokai.wake();
				}
				else {
					Machine.terminate();
				}
				aWaitingOnMolokai.sleep();
			} else {
				// if the boat isn't empty, wake up a child on Oahu
				cWaitingOnOahu.wake();
				aWaitingOnOahu.sleep();
			}
		}
		l.release();
	}

	static void childItinerary() {
		l.acquire();
		while (!isDone()) {
			if (!boatOnOahu) {
				if (KThread.currentThread().getName().equals("Child On Molokai")) {
					// row to Oahu
					bg.ChildRowToOahu();
					cKnownOnOahu++;
					cKnownOnMolokai--;
					KThread.currentThread().setName("Child On Oahu");
					boatOnOahu = true;
					if(aKnownOnOahu > 0) {
						//prioritize waking adults
						aWaitingOnOahu.wake();
					}
					else {
						cWaitingOnOahu.wake();
					}
				} else if(cKnownOnMolokai > 0){
					// wake up a child on Molokai, if there are any
					cWaitingOnMolokai.wake();
				}
				else if(aKnownOnMolokai > 0){
					//wake an adult on Molokai, if any
					aWaitingOnMolokai.wake();
				}
				else {
					//otherwise, terminate
					Machine.terminate();
				}
				cWaitingOnOahu.sleep();
			} else if (KThread.currentThread().getName().equals("Child On Molokai")) {
				if(cKnownOnOahu > 0) {
					// if this child isn't on Oahu, wake up one that is, if any
					cWaitingOnOahu.wake();
				}
				else {
					//otherwise, wake an adult on Oahu
					aWaitingOnOahu.wake();
				}
				cWaitingOnMolokai.sleep();
			} 
			else if (waiting.size() == 0) {
				if(cKnownOnOahu == 1) {
					//if this thread is the only child on Oahu, wake an adult, if any
					if(aKnownOnOahu > 0) {
						aWaitingOnOahu.wake();
						cWaitingOnOahu.sleep();
					}
					//otherwise, row to Molokai alone
					else {
						bg.ChildRowToMolokai();
						KThread.currentThread().setName("Child On Molokai");
						cKnownOnOahu--;
						cKnownOnMolokai++;
						boatOnOahu = false;
						if(cKnownOnMolokai > 0) {
							// if there are any other children on Molokai, wake them
							cWaitingOnMolokai.wake();
						}
						else {
							//otherwise, wake an adult on Molokai
							aWaitingOnMolokai.wake();
						}
						cWaitingOnMolokai.sleep();
					}
				}
				// otherwise if the boat is empty, get on boat, wait
				else{
					waiting.add(KThread.currentThread());
					KThread.currentThread().setName("Child In Boat");
					cKnownOnOahu--;
					bg.ChildRowToMolokai();
					cWaitingOnOahu.wake();
					cWaitingInBoat.sleep();
				}
			} else {
				// if boat has 1 child in it, get in boat and leave for Molokai
				waiting.add(KThread.currentThread());
				KThread.currentThread().setName("Child In Boat");
				cKnownOnOahu--;
				cKnownOnMolokai += 2;
				bg.ChildRideToMolokai();
				waiting.remove(KThread.currentThread());
				KThread.currentThread().setName("Child On Molokai");
				waiting.firstElement().setName("Child On Molokai");
				waiting.remove(waiting.firstElement());
				cWaitingInBoat.wake();
				cWaitingOnMolokai.wake();
				aWaitingOnOahu.wake();
				cWaitingOnMolokai.sleep();
				boatOnOahu = false;
			}
		}
		l.release();
	}

	static boolean isDone() {
		if (aKnownOnOahu == 0 && cKnownOnOahu == 0) {
			Machine.terminate();
		}
		return false;
	}

	static void SampleItinerary() {
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

}
