package swarmBots;



import java.io.BufferedReader;

import java.io.Closeable;

import java.io.DataOutputStream;

import java.io.IOException;

import java.io.InputStreamReader;

import java.io.PrintWriter;

import java.lang.reflect.Type;

import java.net.ServerSocket;

import java.net.Socket;

import java.net.UnknownHostException;

import java.util.ArrayList;

import java.util.HashSet;

import java.util.List;

import java.util.Random;

import java.util.Set;



import com.google.gson.Gson;

import com.google.gson.GsonBuilder;

import com.google.gson.reflect.TypeToken;



import common.Coord;

import common.MapTile;

import common.ScanMap;

import communication.Group;

import communication.RoverCommunication;

import enums.RoverDriveType;

import enums.RoverToolType;

import enums.Terrain;

import enums.Science;



/**

 * The seed that this program is built on is a chat program example found here:

 * http://cs.lmu.edu/~ray/notes/javanetexamples/ Many thanks to the authors for

 * publishing their code examples

 */



public class ROVER_04 {



	BufferedReader in;

	PrintWriter out;

	String rovername;

	ScanMap scanMap;

	int sleepTime;

	String SERVER_ADDRESS = "localhost";

	static final int PORT_ADDRESS = 9537;


	int counter;



	List<Socket> output_Sockets = new ArrayList<Socket>();



	// objects contain each rover IP, port, and name

	List<Group> blue = new ArrayList<Group>();



	// every science detected will be added in to this set

	Set<Coord> science_Store = new HashSet<Coord>();

	static ArrayList<String> paths = new ArrayList<String>();



	// this set contains all the science the ROVERED has shared

	// thus whatever thats in science_collection that is not in display_science

	// are "new" and "unshared"

	Set<Coord> displayed_science = new HashSet<Coord>();



	// ROVER current location

	Coord roverLoc;



	String currentDir = "E";

	// Your ROVER is going to listen for connection with this

	ServerSocket listenSocket;

	Coord Rover_Current_Loc = null;

	Coord Rover_Previous_Loc = null;



	/* Communication Module*/

	RoverCommunication rocom;



	public ROVER_04() {

		// constructor

		System.out.println("ROVER_04 rover object constructed");

		rovername = "ROVER_04";

		SERVER_ADDRESS = "localhost";

		// this should be a safe but slow timer value

		sleepTime = 300; // in milliseconds - smaller is faster, but the server

		// will cut connection if it is too small

	}



	public ROVER_04(String serverAddress) {

		// constructor

		System.out.println("ROVER_04 rover object constructed");

		rovername = "ROVER_04";

		SERVER_ADDRESS = serverAddress;

		sleepTime = 200; // in milliseconds - smaller is faster, but the server

		// will cut connection if it is too small

	}

	/**

	 * Connects to the server then enters the processing loop.

	 */

	private void run() throws IOException, InterruptedException {



		// Make connection to SwarmServer and initialize streams


		Socket socket = new Socket(SERVER_ADDRESS, PORT_ADDRESS);



		in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

		out = new PrintWriter(socket.getOutputStream(), true);

		// ******************* SET UP COMMUNICATION MODULE by Shay *********************

		/* Your Group Info*/

		Group group = new Group(rovername, SERVER_ADDRESS, 53704, RoverDriveType.WALKER,

				RoverToolType.DRILL, RoverToolType.RADAR_SENSOR);



		/* Setup communication, only communicates with gatherers */

		rocom = new RoverCommunication(group);

		rocom.setGroupList(Group.getGatherers());



		/* Can't go on ROCK, thus ignore any SCIENCE COORDS that is on ROCK */

		//rocom.ignoreTerrain(Terrain.ROCK);



		/* Start your server, receive incoming message from other ROVERS */

		rocom.startServer();

		// ******************************************************************





		// Process all messages from server, wait until server requests Rover ID

		// name - Return Rover Name to complete connection

		while (true) {

			String line = in.readLine();

			if (line.startsWith("SUBMITNAME")) {

				out.println(rovername); // This sets the name of this instance

				// of a swarmBot for identifying the

				// thread to the server

				break;

			}

		}

		// ********* Rover logic setup *********



		String line = "";

		Coord rovergroupStartPosition = null;

		Coord targetLocation = null;



		/**

		 * Get initial values that won't change

		 */

		// **** get equipment listing ****

		ArrayList<String> equipment = new ArrayList<String>();

		equipment = getEquipment();

		System.out.println(rovername + " equipment list results "

+ equipment + "\n");



		// **** Request START_LOC Location from SwarmServer ****

		out.println("START_LOC");

		line = in.readLine();

		if (line == null) {

			System.out.println(rovername + " check connection to server");

			line = "";

		}

		if (line.startsWith("START_LOC")) {

			rovergroupStartPosition = extractLocationFromString(line);

		}

		System.out.println(rovername + " START_LOC "

+ rovergroupStartPosition);



		// **** Request TARGET_LOC Location from SwarmServer ****

		out.println("TARGET_LOC");

		line = in.readLine();

		if (line == null) {

			System.out.println(rovername + " check connection to server");

			line = "";

		}

		if (line.startsWith("TARGET_LOC")) {

			targetLocation = extractLocationFromString(line);

		}

		System.out.println(rovername + " TARGET_LOC " + targetLocation);







		boolean stuck = false; // just means it did not change locations

		// between requests,

		// could be velocity limit or obstruction

		// etc.



		/*

		 * String[] cardinals = new String[4]; cardinals[0] = "N";

		 * cardinals[1] = "E"; cardinals[2] = "S"; cardinals[3] = "W";

		 * 

		 * String currentDir = cardinals[0];

		 */

		// Coord Rover_Current_Loc = null;

		// Coord Rover_Previous_Loc = null;



		/**

		 * #### Rover controller process loop ####

		 */

		while (true) {



			// **** Request Rover Location from SwarmServer ****

			out.println("LOC");

			line = in.readLine();

			if (line == null) {

				System.out.println(rovername

						+ " check connection to server");

				line = "";

			}

			if (line.startsWith("LOC")) {

				// loc = line.substring(4);

				Rover_Current_Loc = extractLocationFromString(line);



			}

			System.out.println(rovername + " currentLoc at start: "

+ Rover_Current_Loc);



			// after getting location set previous equal current to be able

			// to check for stuckness and blocked later

			Rover_Previous_Loc = Rover_Current_Loc;



			// ***** do a SCAN *****



			// gets the scanMap from the server based on the Rover current

			// location

			loadScanMapFromSwarmServer();

			// prints the scanMap to the Console output for debug purposes

			scanMap.debugPrintMap();



			// ***** MOVING *****

			// pull the MapTile array out of the ScanMap object

			MapTile[][] scanMapTiles = scanMap.getScanMap();

			int centerIndex = (scanMap.getEdgeSize() - 1) / 2;

			// tile S = y + 1; N = y - 1; E = x + 1; W = x - 1





			// try moving east 5 block if blocked

			masterMovement(scanMapTiles, Rover_Current_Loc, targetLocation);





			// another call for current location

			out.println("LOC");

			line = in.readLine();

			if (line == null) {

				System.out.println("ROVER_04 check connection to server");

				line = "";

			}

			if (line.startsWith("LOC")) {

				Rover_Current_Loc = extractLocationFromString(line);



			}



			// TODO - logic to calculate where to move next





			/* ********* Detect and Share Science ***************/

			//rocom.detectAndShare(scanMap.getScanMap(), Rover_Current_Loc, 3);

			/* *************************************************/



			// this is the Rovers HeartBeat, it regulates how fast the Rover cycles through the control loop

			Thread.sleep(sleepTime);



			System.out.println("ROVER_04 ------------ bottom process control --------------");

		}





	} // END of Rover main control loop



	// ####################### Support Methods #############################



	private void clearReadLineBuffer() throws IOException {

		while (in.ready()) {

			// System.out.println("ROVER_04 clearing readLine()");

			in.readLine();

		}

	}

	// movement of rover

	public void masterMovement(MapTile[][] scanMapTiles, Coord currentLocation,

			Coord targetLocation) throws IOException, InterruptedException {




		int centerIndex = (scanMap.getEdgeSize() - 1) / 2;

		int cx = currentLocation.xpos, cy = currentLocation.ypos;

		int tx = targetLocation.xpos, ty = targetLocation.ypos;

		/*if (counter < 5)

counter++;

else{

counter = 0;

eastBlock = true;

}

		 */

		if (tx == cx && cy == ty) {



			System.out.println("ROVER_04 reaches target location");

			counter++;

			currentDir = changeRoverDirection(scanMapTiles, currentLocation, targetLocation);





		} else if (cx < tx) {

			if (counter == 0)

				currentDir = "E";

			else if (counter < 50){





				currentDir = changeRoverDirection(scanMapTiles, currentLocation, targetLocation);



				counter++;

			}

			else

				counter=0;





		} else if (cx > tx) {

			if (counter == 0)

				currentDir = "W";

			else if (counter < 50){





				currentDir = changeRoverDirection(scanMapTiles, currentLocation, targetLocation);

				counter++;

			}

			else

				counter=0;





		} else if (cy < ty) {

			if (counter == 0)

				currentDir = "S";

			else if (counter < 50){





				currentDir = changeRoverDirection(scanMapTiles, currentLocation, targetLocation);



				counter++;

			}

			else

				counter=0;

		}

		else{

			if (counter == 0)

				currentDir = "N";

			else if (counter < 50){





				currentDir =changeRoverDirection(scanMapTiles, currentLocation, targetLocation);



				counter++;

			}

			else

				counter=0;

		}

		if (isValidMovement(scanMapTiles, currentDir)) {

			move(currentDir);

			// Thread.sleep(1100);

			collectScience(scanMapTiles , centerIndex);

			rocom.detectAndShare(scanMap.getScanMap(), Rover_Current_Loc, 3);





			// counter ++;

		} else {





			while (!isValidMovement(scanMapTiles, currentDir)) {



				currentDir = changeRoverDirection(scanMapTiles, currentLocation, targetLocation);

			}

			counter ++;

			move(currentDir);

			// Thread.sleep(1100);

			collectScience(scanMapTiles , centerIndex);

			rocom.detectAndShare(scanMap.getScanMap(), Rover_Current_Loc, 3);










		}



	}

	/*

	 * if(counter == 5) { counter = 0; direction =

	 * changeRoverDirection(direction); }

	 */





	// validty of rover next move

	public Boolean isValidMovement(MapTile[][] scanMapTiles, String currentDir) {

		int centerIndex = (scanMap.getEdgeSize() - 1) / 2;

		int x_Position = centerIndex;

		int y_Position = centerIndex;

		switch (currentDir) {



		case "E":

			x_Position = x_Position + 1;

			break;

		case "W":

			x_Position = x_Position - 1;

			break;

		case "N":

			y_Position = y_Position - 1;

			break;

		case "S":

			y_Position = y_Position + 1;

			break;

		}

		if (scanMapTiles[x_Position][y_Position].getHasRover()

				|| scanMapTiles[x_Position][y_Position].getTerrain() == Terrain.SAND

				|| scanMapTiles[x_Position][y_Position].getTerrain() == Terrain.NONE) {

			//eastBlock = true;

			return false;

		} else

			return true;



	}



	// this function will move the rover randomly in the east,west,north or

	// south direction.

	public String changeRoverDirection(MapTile[][] scanMapTiles, Coord currentLocation,

			Coord targetLocation) {

		int centerIndex = (scanMap.getEdgeSize() - 1) / 2;

		int cx = currentLocation.xpos, cy = currentLocation.ypos;

		int tx = targetLocation.xpos, ty = targetLocation.ypos;

		Random r = new Random();

		if (cx < tx)

			return paths.get(r.nextInt(3));

		else

			return paths.get(r.nextInt(4));




	}



	/*

	 * public void calculateX_Y(String currentDir) { int centerIndex =

	 * (scanMap.getEdgeSize() - 1) / 2; int x_Position = centerIndex; int

	 * y_Position = centerIndex; switch(currentDir){

	 * 

	 * case "E" : x_Position = x_Position+1; break; case "W" : x_Position =

	 * x_Position-1; break; case "N" : y_Position = y_Position-1; break; case

	 * "S" : y_Position = y_Position+1; break; }

	 * 

	 * }

	 */

	public void move(String direction) {

		out.println("MOVE " + direction);

	}



	public void collectScience(MapTile[][] scanMapTiles , int centerIndex) {

		System.out.println("ROVER_04: scanMapTiles[centerIndex][centerIndex].getScience().getSciString() "

+ scanMapTiles[centerIndex][centerIndex]

		.getScience().getSciString());

		if (!scanMapTiles[centerIndex][centerIndex].getScience()

				.getSciString().equals("N")) {

			System.out.println("ROVER_04 request GATHER");

			out.println("GATHER");

		}	}



	// method to retrieve a list of the rover's EQUIPMENT from the server

	private ArrayList<String> getEquipment() throws IOException {

		// System.out.println("ROVER_04 method getEquipment()");

		Gson gson = new GsonBuilder().setPrettyPrinting()

				.enableComplexMapKeySerialization().create();

		out.println("EQUIPMENT");



		String jsonEqListIn = in.readLine(); // grabs the string that was

		// returned first

		if (jsonEqListIn == null) {

			jsonEqListIn = "";

		}

		StringBuilder jsonEqList = new StringBuilder();

		// System.out.println("ROVER_04 incomming EQUIPMENT result - first readline: "

		// + jsonEqListIn);



		if (jsonEqListIn.startsWith("EQUIPMENT")) {

			while (!(jsonEqListIn = in.readLine()).equals("EQUIPMENT_END")) {

				if (jsonEqListIn == null) {

					break;

				}

				// System.out.println("ROVER_04 incomming EQUIPMENT result: " +

				// jsonEqListIn);

				jsonEqList.append(jsonEqListIn);

				jsonEqList.append("\n");

				// System.out.println("ROVER_04 doScan() bottom of while");

			}

		} else {

			// in case the server call gives unexpected results

			clearReadLineBuffer();

			return null; // server response did not start with "EQUIPMENT"

		}



		String jsonEqListString = jsonEqList.toString();

		ArrayList<String> returnList;

		returnList = gson.fromJson(jsonEqListString,

				new TypeToken<ArrayList<String>>() {

		}.getType());

		// System.out.println("ROVER_04 returnList " + returnList);



		return returnList;

	}



	// sends a SCAN request to the server and puts the result in the scanMap

	// array

	public void loadScanMapFromSwarmServer() throws IOException {

		// System.out.println("ROVER_04 method doScan()");

		Gson gson = new GsonBuilder().setPrettyPrinting()

				.enableComplexMapKeySerialization().create();

		out.println("SCAN");



		String jsonScanMapIn = in.readLine(); // grabs the string that was

		// returned first

		if (jsonScanMapIn == null) {

			System.out.println("ROVER_04 check connection to server");

			jsonScanMapIn = "";

		}

		StringBuilder jsonScanMap = new StringBuilder();

		System.out.println("ROVER_04 incomming SCAN result - first readline: "

+ jsonScanMapIn);



		if (jsonScanMapIn.startsWith("SCAN")) {

			while (!(jsonScanMapIn = in.readLine()).equals("SCAN_END")) {

				// System.out.println("ROVER_04 incomming SCAN result: " +

				// jsonScanMapIn);

				jsonScanMap.append(jsonScanMapIn);

				jsonScanMap.append("\n");

				// System.out.println("ROVER_04 doScan() bottom of while");

			}

		} else {

			// in case the server call gives unexpected results

			clearReadLineBuffer();

			return; // server response did not start with "SCAN"

		}

		// System.out.println("ROVER_04 finished scan while");



		String jsonScanMapString = jsonScanMap.toString();

		// debug print json object to a file

		// new MyWriter( jsonScanMapString, 0); //gives a strange result -

		// prints the \n instead of newline character in the file



		// System.out.println("ROVER_04 convert from json back to ScanMap class");

		// convert from the json string back to a ScanMap object

		scanMap = gson.fromJson(jsonScanMapString, ScanMap.class);

	}



	// this takes the server response string, parses out the x and x values and

	// returns a Coord object

	public static Coord extractLocationFromString(String sStr) {

		int indexOf;

		indexOf = sStr.indexOf(" ");

		sStr = sStr.substring(indexOf + 1);

		if (sStr.lastIndexOf(" ") != -1) {

			String xStr = sStr.substring(0, sStr.lastIndexOf(" "));

			// System.out.println("extracted xStr " + xStr);



			String yStr = sStr.substring(sStr.lastIndexOf(" ") + 1);

			// System.out.println("extracted yStr " + yStr);

			return new Coord(Integer.parseInt(xStr), Integer.parseInt(yStr));

		}

		return null;

	}



	/**

	 * Runs the client

	 */

	public static void main(String[] args) throws Exception {

		ROVER_04 client = new ROVER_04();

		paths.add("S");

		paths.add("W");

		paths.add("N");

		paths.add("E");

		client.run();

	}

}