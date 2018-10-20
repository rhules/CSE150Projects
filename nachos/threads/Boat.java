package nachos.threads;
import nachos.ag.BoatGrader;
import nachos.machine.Machine;

import java.util.Vector;

public class Boat
{
	static BoatGrader bg;
	static int cOnOahu;
	static int aOnOahu;
	static int cOnMolokai;
	static int aOnMolokai;
	static boolean boatOnOahu;
	static Vector<KThread> aThreads;
	static Vector<KThread> cThreads;

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
		cOnOahu = children;
		aOnOahu = adults;
		cOnMolokai = 0;
		aOnMolokai = 0;
		//assume the boat starts on Oahu
		boatOnOahu = true;


		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.

		aThreads = new Vector<KThread>();
		Runnable a = new Runnable(){
			public void run(){
				adultItinerary();
			}
		};
		for (int i = adults; i > 0; i--){
				KThread temp = new KThread(a);
				aThreads.add(temp);
		}
		cThreads = new Vector<KThread>();
		Runnable c = new Runnable(){
			public void run(){
				childItinerary();
			}
		};
		for (int i = children; i > 0; i--){
			KThread temp = new KThread(c);
			cThreads.add(temp);
		}
		
		aRun();
		
		/* Runnable r = new Runnable() {
				public void run() {
					SampleItinerary();
				}
			};*/

			//KThread t = new KThread(r);
		// t.setName("Sample Boat Thread");
		// t.fork();

	}
	static void aRun(){
		for(KThread i:aThreads){
			i.fork();
		}
		cRun();
	}
	static void cRun(){
		for(KThread i:cThreads){
			i.fork();
			/*deliberate pseudo-infinite loop to keep iterating child threads until the simulation ends internally*/
			if(i == cThreads.lastElement()){
				i = cThreads.firstElement();
			}
		}
		//terminate if there are no child threads
		Machine.terminate();
	}


	static void adultItinerary()
	{
		/* This is where you should put your solutions. Make calls to the BoatGrader to show that it is synchronized. For example: bg.AdultRowToMolokai(); indicates that an adult has rowed the boat across to Molokai
		*/
		if(!boatOnOahu){
			//if the boat is on Molokai without any children to row it back, end simulation
			if(cOnMolokai == 0){
				Machine.terminate();
			}
			else{
				cRun();
			}
		}
		else if(aOnOahu == 0){
			cRun();
		}
		else{
			//1 adult rows to Molokai(aOnMolokai++, aOnOahu--)
			aOnMolokai++;
			aOnOahu--;
			bg.AdultRowToMolokai();
			boatOnOahu = false;
			//In this implementation, once an adult is across, they no longer act
			//Kill that thread
			KThread.finish();
		}
	}

	static void childItinerary()
	{
		if(!boatOnOahu){
			if(cOnMolokai > 0){
				//1 child rows to Oahu (cOnOahu++, cOnMolokai--)
				cOnOahu++;
				cOnMolokai--;
				bg.ChildRowToOahu();
				boatOnOahu = true;
			}
			else{
				Machine.terminate();
			}
		}
		else if(cOnOahu == 1){
			if(aOnOahu > 0){
				aRun();
			}
			else{
				//1 child rows to Molokai(cOnMolokai++, cOnOahu--)
				cOnMolokai++;
				cOnOahu--;
				bg.ChildRowToMolokai();
				boatOnOahu = false;
				Machine.terminate();
			}
		}
		else if (cOnOahu ==2 && aOnOahu == 0){
			//2 children row to Molokai(cOnMolokai+=2, cOnOahu-=2)
			cOnMolokai+=2;
			cOnOahu-=2;
			bg.ChildRowToMolokai();
			bg.ChildRideToMolokai();
			boatOnOahu = false;
			Machine.terminate();
		}
		else{
			//2 children row to Molokai(cOnMolokai+=2, cOnOahu-=2)
			cOnMolokai+=2;
			cOnOahu-=2;
			bg.ChildRowToMolokai();
			bg.ChildRideToMolokai();
			boatOnOahu = false;
		}
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
