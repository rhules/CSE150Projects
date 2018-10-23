package nachos.threads;
import nachos.ag.BoatGrader;
import nachos.machine.Machine;

import java.util.Vector;

public class Boat
{
	static BoatGrader bg;
	static boolean boatOnOahu;
	static int cKnownOnOahu;
	static int cKnownOnMolokai;
	static int aKnownOnOahu;
	static Lock l;
	static Condition2 cWaitingInBoat;
	static Vector<KThread> waiting;
	static Condition2 cWaitingOnOahu;
	static Condition2 cWaitingOnMolokai;
	static Condition2 aWaiting;
	

	public static void selfTest()
	{
		BoatGrader b = new BoatGrader();

		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(0, 2, b);

		//	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
		//  	begin(1, 2, b);

		//  	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		//  	begin(3, 3, b);
	}

	public static void begin( int adults, int children, BoatGrader b )
	{
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here
		//assume the boat starts on Oahu
		boatOnOahu = true;
		l = new Lock();
		cWaitingInBoat = new Condition2(l);
		waiting = new Vector<KThread>();
		/*
		 * The initial number of people (children and adults) on Oahu is 
		 * known by everyone, because they are all present at the start
		 * of the simulation. After this point, it will be treated as if
		 * everyone is keeping track of the numbers by incrementing and
		 * decrementing these variables whenever someone leaves or arrives
		 * at one of the islands. 
		 * 
		 * The number of adults on Molokai is not tracked, because it 
		 * isn't important in this implementation of the problem.
		*/
		cKnownOnMolokai = 0;
		cKnownOnOahu = children;
		aKnownOnOahu = adults;


		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.

		Runnable a = new Runnable(){
			public void run(){
				adultItinerary();
			}
		};
		for (int i = adults; i > 0; i--){
				KThread temp = new KThread(a);
				temp.setName("Adult on Oahu");
				aKnownOnOahu++;
				temp.fork();
		}

		Runnable c = new Runnable(){
			public void run(){
				childItinerary();
			}
		};
		for (int i = children; i > 0; i--){
			KThread temp = new KThread(c);
			temp.setName("Child on Oahu");
			cKnownOnOahu++;
			temp.fork();
		}
		
		
		/* Runnable r = new Runnable() {
				public void run() {
					SampleItinerary();
				}
			};*/

			//KThread t = new KThread(r);
		// t.setName("Sample Boat Thread");
		// t.fork();

	}


	static void adultItinerary()
	{
		/* This is where you should put your solutions. Make calls to the BoatGrader to show that it is synchronized. For example: bg.AdultRowToMolokai(); indicates that an adult has rowed the boat across to Molokai
		*/
		l.acquire();
		while (!isDone()) {
			if(!boatOnOahu){
				//if the boat is on Molokai without any children to row it back, end simulation
				if(cKnownOnMolokai == 0){
					Machine.terminate();
				}
				//otherwise, wake up a child thread on Molokai
				else{
					cWaitingOnMolokai.wake();
					aWaiting.sleep();
				}
			}
			else if(waiting.size() == 0){
				//if there isn't a child waiting in the boat, row to Molokai(aOnMolokai++, aOnOahu--)
				bg.AdultRowToMolokai();
				boatOnOahu = false;
				KThread.currentThread().setName("Adult On Molokai");
				aKnownOnOahu--;
				cWaitingOnMolokai.wake();
				//In this implementation, once an adult is across, they no longer act
				//Kill that thread
				KThread.finish();
			}
			else {
				//if the boat isn't empty, wake up a child on Oahu
				cWaitingOnOahu.wake();
				aWaiting.sleep();
			}
		}
		l.release();
	}

	static void childItinerary()
	{
		l.acquire();
		while (!isDone()) {
			if(!boatOnOahu){
				if(KThread.currentThread().getName().equals("Child On Molokai")){
					//row to Oahu (cKnownOnOahu++, cKnownOnMolokai--)
					bg.ChildRowToOahu();
					cKnownOnOahu++;
					cKnownOnMolokai--;
					KThread.currentThread().setName("Child On Oahu");
					boatOnOahu = true;
					aWaiting.wake();
					cWaitingOnOahu.wake();
					cWaitingOnOahu.sleep();
				}
				else {
					//wake up a child on Molokai
					cWaitingOnMolokai.wake();
					cWaitingOnOahu.sleep();
				}
			}
			else if (KThread.currentThread().getName().equals("Child On Molokai")) {
				//if this child isn't on Oahu, wake up one that is
				cWaitingOnOahu.wake();
				cWaitingOnMolokai.sleep();
			}
			else if(aKnownOnOahu > 0) {
				//prioritize sending adults over
				aWaiting.wake();
				cWaitingOnOahu.sleep();
			}
			else if(cKnownOnOahu == 1){
				//row to Molokai, end simulation
				KThread.currentThread().setName("Child On Molokai");
				bg.ChildRowToMolokai();
				cKnownOnMolokai++;
				cKnownOnOahu--;
				boatOnOahu = false;
				Machine.terminate();
			}
			else if(waiting.size() == 0){
				//if the boat is empty, get on boat, wait
				waiting.add(KThread.currentThread());
				KThread.currentThread().setName("Child In Boat");
				cKnownOnOahu--;
				bg.ChildRowToMolokai();
				cWaitingOnOahu.wake();
				cWaitingInBoat.sleep();
			}
			else {
				//if boat has 1 child in it, get in boat and leave for Molokai
				waiting.add(KThread.currentThread());
				KThread.currentThread().setName("Child In Boat");
				cKnownOnOahu --;
				cKnownOnMolokai +=2;
				bg.ChildRideToMolokai();
				waiting.remove(KThread.currentThread());
				KThread.currentThread().setName("Child On Molokai");
				waiting.firstElement().setName("Child On Molokai");
				waiting.remove(waiting.firstElement());
				cWaitingInBoat.wake();
				cWaitingOnMolokai.wake();
				aWaiting.wake();
				cWaitingOnMolokai.sleep();
				boatOnOahu = false;
			}
		}
		l.release();
	}
	
	static boolean isDone() {
		if(aKnownOnOahu == 0 && cKnownOnOahu == 0) {
			Machine.terminate();
		}
		return false;
	}


	static void SampleItinerary()
	{
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
