package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat
{
	static BoatGrader bg;

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
		Int cOnOahu = children;
		Int aOnOahu = adults;
		Int cOnMolokai = 0;
		Int aOnMolokai = 0;
		//assume the boat starts on Oahu
		Bool boatOnOahu = true;


		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.

		vector<KThread> AThreads = new vector<KThread>;
		Runnable a = new Runnable(){
			Public void run(){
				AdultItinerary();
			}
		};
		for (int i = adults; i > 0; i--){
				KThread temp = new KThread(r);
				aThreads.add(temp);
		}
		vector<KThread> cThreads = new vector<KThread>;
		Runnable c = new Runnable(){
			Public void run(){
				ChildItinerary();
			}
		};
		for (int i = children; i > 0; i--){
			KThread temp = new KThread(r);
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
	Void aRun(){
		for(i:aThreads){
			aThreads.at(i).fork();
			aThread.at(i).finish();
		}
		cRun();
	}
	Void cRun(){
		for(i:cThreads){
			cThreads.at(i).fork();
			/*deliberate pseudo-infinite loop to keep iterating child threads until the simulation ends internally*/
			if(i == cThreads.end(){
				I = cThreads.begin();
			}
		}
		end();
	}


	static void AdultItinerary()
	{
		/* This is where you should put your solutions. Make calls to the BoatGrader to show that it is synchronized. For example: bg.AdultRowToMolokai(); indicates that an adult has rowed the boat across to Molokai
		*/
		if(!boatOnOahu){
			//if the boat’s on Molokai without any children to row it back, end simulation
			if(cOnMolokai == 0){
				end();
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
		}
	}

	static void ChildItinerary()
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
				end();
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
				end();
			}
		}
		else if (cOnOahu ==2 && aOnOahu == 0){
			//2 children row to Molokai(cOnMolokai+=2, cOnOahu-=2)
			cOnMolokai+=2;
			cOnOahu-=2;
			bg.ChildRowToMolokai();
			bg.ChildRideToMolokai();
			boatOnOahu = false;
			end();
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
	Void end(){
		for(i:aThreads){
			aThreads.at(i).finish();
		}
		for (i:cThreads){
			cThreads.at(i).finish();
		}
		//End simulation
		Machine.terminate();
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
