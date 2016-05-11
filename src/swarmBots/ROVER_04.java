package swarmBots;

import java.io.BufferedReader;
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
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import common.Coord;
import common.MapTile;
import common.ScanMap;
import common.Group;
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
	
	List<Socket> output_Sockets = new ArrayList<Socket>();

    //objects contain each rover IP, port, and name
    List<Group> blue = new ArrayList<Group>();

    // every science detected will be added in to this set
    Set<Coord> science_Store = new HashSet<Coord>();

    // this set contains all the science the ROVERED has shared
    // thus whatever thats in science_collection that is not in display_science
    // are "new" and "unshared"
    Set<Coord> displayed_science = new HashSet<Coord>();
    
    // ROVER current location
    Coord roverLoc;
    
    // Your ROVER is going to listen for connection with this
    ServerSocket listenSocket;
    Coord Rover_Current_Loc = null;
	Coord Rover_Previous_Loc = null;
	public ROVER_04() {
		// constructor
		System.out.println("ROVER_04 rover object constructed");
		rovername = "ROVER_04";
		SERVER_ADDRESS = "localhost";
		// this should be a safe but slow timer value
		sleepTime = 300; // in milliseconds - smaller is faster, but the server will cut connection if it is too small
	}
	
	public ROVER_04(String serverAddress) {
		// constructor
		System.out.println("ROVER_04 rover object constructed");
		rovername = "ROVER_04";
		SERVER_ADDRESS = serverAddress;
		sleepTime = 200; // in milliseconds - smaller is faster, but the server will cut connection if it is too small
	}
	
	/**
     * Connect each socket on a separate thread. It will try until it works.
     * When socket is created, save it to a LIST
     */
    class RoverComm implements Runnable {

        String ip;
        int port;
        Socket socket;

        public RoverComm(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        @Override
        public void run() {
            do {
                try {
                    socket = new Socket(ip, port);
                } catch (UnknownHostException e) {

                } catch (IOException e) {

                }
            } while (socket == null);
            
            output_Sockets.add(socket);
            System.out.println(socket.getPort() + " " + socket.getInetAddress());
        }

    }
    
    /**
     * For connection add all the blue group rovers (ip , port#) into a List
     */
    public void initConnection() {
        // dummy value # 1
        blue.add(new Group("Dummy Group #1", "localhost", 53799));

        // blue rooster
        blue.add(new Group("GROUP_01", "localhost", 53701));
        blue.add(new Group("GROUP_02", "localhost", 53702));
        blue.add(new Group("GROUP_03", "localhost", 53703));
        blue.add(new Group("GROUP_05", "localhost", 53705));
        blue.add(new Group("GROUP_06", "localhost", 53706));
        blue.add(new Group("GROUP_07", "localhost", 53707));
        blue.add(new Group("GROUP_08", "localhost", 53708));
        blue.add(new Group("GROUP_09", "localhost", 53709));
    }

	

	/**
	 * Connects to the server then enters the processing loop.
	 */
	private void run() throws IOException, InterruptedException {

		// Make connection to SwarmServer and initialize streams
		Socket socket = null;
		try {
			socket = new Socket(SERVER_ADDRESS, PORT_ADDRESS);

			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(socket.getOutputStream(), true);
			
	
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
			 *  Get initial values that won't change
			 */
			// **** get equipment listing ****			
			ArrayList<String> equipment = new ArrayList<String>();
			equipment = getEquipment();
			System.out.println(rovername + " equipment list results " + equipment + "\n");
			
			
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
			System.out.println(rovername + " START_LOC " + rovergroupStartPosition);
			
			
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
			
			

			
	
	
			boolean goingSouth = false;
			boolean goingNorth = false;
			boolean goingWest = false;

			boolean stuck = false; // just means it did not change locations between requests,
									// could be velocity limit or obstruction etc.
			boolean blocked = true;
	
			String[] cardinals = new String[4];
			cardinals[0] = "N";
			cardinals[1] = "E";
			cardinals[2] = "S";
			cardinals[3] = "W";
	
			String currentDir = cardinals[0];
			//Coord Rover_Current_Loc = null;
			//Coord Rover_Previous_Loc = null;
	

			/**
			 *  ####  Rover controller process loop  ####
			 */
			while (true) {
	
				
				// **** Request Rover Location from SwarmServer ****
				out.println("LOC");
				line = in.readLine();
	            if (line == null) {
	            	System.out.println(rovername + " check connection to server");
	            	line = "";
	            }
				if (line.startsWith("LOC")) {
					// loc = line.substring(4);
					Rover_Current_Loc = extractLocationFromString(line);
					
				}
				System.out.println(rovername + " currentLoc at start: " + Rover_Current_Loc);
				
				// after getting location set previous equal current to be able to check for stuckness and blocked later
				Rover_Previous_Loc = Rover_Current_Loc;		
				
				

				
		
	
				// ***** do a SCAN *****

				// gets the scanMap from the server based on the Rover current location
				loadScanMapFromSwarmServer();
				// prints the scanMap to the Console output for debug purposes
				scanMap.debugPrintMap();
				
		
				
				

				
	
				
				// ***** MOVING *****
				// pull the MapTile array out of the ScanMap object
				MapTile[][] scanMapTiles = scanMap.getScanMap();
				int centerIndex = (scanMap.getEdgeSize() - 1)/2;
				// tile S = y + 1; N = y - 1; E = x + 1; W = x - 1
				
				detectMineral(scanMap.getScanMap());
				shareScience();
				// try moving east 5 block if blocked
				if (blocked) {
				
					//for (int i = 0; i < 5; i++) {
						if (scanMapTiles[centerIndex +1][centerIndex].getHasRover() 
								|| scanMapTiles[centerIndex +1][centerIndex].getTerrain() == Terrain.SAND
								|| scanMapTiles[centerIndex +1][centerIndex].getTerrain() == Terrain.NONE) {
							if (scanMapTiles[centerIndex -1][centerIndex].getHasRover() 
									|| scanMapTiles[centerIndex -1][centerIndex].getTerrain() == Terrain.SAND
									|| scanMapTiles[centerIndex -1][centerIndex].getTerrain() == Terrain.NONE) {
								
									// request to server to move
									out.println("MOVE S");
									Thread.sleep(1100);
									//System.out.println("ROVER_04 request move S");
									System.out.println("ROVER_04: scanMapTiles[centerIndex][centerIndex].getScience().getSciString() " + scanMapTiles[centerIndex][centerIndex].getScience().getSciString());
									if (!scanMapTiles[centerIndex][centerIndex].getScience().getSciString().equals("N")) {
										System.out.println("ROVER_04 request GATHER");
										out.println("GATHER");
										
								}
							} else {
								// request to server to move
								out.println("MOVE W");
								//System.out.println("ROVER_04 request move W");
								Thread.sleep(1100);
								System.out.println("ROVER_04: scanMapTiles[centerIndex][centerIndex].getScience().getSciString() " + scanMapTiles[centerIndex][centerIndex].getScience().getSciString());
								if (!scanMapTiles[centerIndex][centerIndex].getScience().getSciString().equals("N")) {
									System.out.println("ROVER_04 request GATHER");
									out.println("GATHER");
									
							}
							}
							
						} else {
							
							// request to server to move
							out.println("MOVE E");
							//System.out.println("ROVER_04 request move E");
							Thread.sleep(1100);

							System.out.println("ROVER_04: scanMapTiles[centerIndex][centerIndex].getScience().getSciString() " + scanMapTiles[centerIndex][centerIndex].getScience().getSciString());
							if (!scanMapTiles[centerIndex][centerIndex].getScience().getSciString().equals("N")) {
								System.out.println("ROVER_04 request GATHER");
								out.println("GATHER");
								
						}
					}
					//}
					
					blocked = false;
					//reverses direction after being blocked
			
					if(goingNorth)
					goingSouth = !goingSouth;
					goingNorth = !goingNorth;
					
					

				} else {
	
					
	
					if (goingSouth) {
						// check scanMap to see if path is blocked to the south
						if (scanMapTiles[centerIndex][centerIndex +1].getHasRover() 
								|| scanMapTiles[centerIndex][centerIndex +1].getTerrain() == Terrain.SAND
								|| scanMapTiles[centerIndex][centerIndex +1].getTerrain() == Terrain.NONE) {
							blocked = true;
						} else {
							// request to server to move
							out.println("MOVE S");
							//System.out.println("ROVER_04 request move S");
							System.out.println("ROVER_04: scanMapTiles[centerIndex][centerIndex].getScience().getSciString() " + scanMapTiles[centerIndex][centerIndex].getScience().getSciString());
							if (!scanMapTiles[centerIndex][centerIndex].getScience().getSciString().equals("N")) {
								System.out.println("ROVER_04 request GATHER");
								out.println("GATHER");
								
						}
						}
						
					
					
					}else {
						// check scanMap to see if path is blocked to the north
						//System.out.println("ROVER_04 scanMapTiles[2][1].getHasRover() " + scanMapTiles[2][1].getHasRover());
						//System.out.println("ROVER_04 scanMapTiles[2][1].getTerrain() " + scanMapTiles[2][1].getTerrain().toString());
						
						if (scanMapTiles[centerIndex][centerIndex -1].getHasRover() 
								|| scanMapTiles[centerIndex][centerIndex -1].getTerrain() == Terrain.SAND
								|| scanMapTiles[centerIndex][centerIndex -1].getTerrain() == Terrain.NONE) {
							goingNorth = true;
							blocked = true;
						} else {
							// request to server to move
							out.println("MOVE N");
							
							//System.out.println("ROVER_04 request move N");
							System.out.println("ROVER_04: scanMapTiles[centerIndex][centerIndex].getScience().getSciString() " + scanMapTiles[centerIndex][centerIndex].getScience().getSciString());
							if (!scanMapTiles[centerIndex][centerIndex].getScience().getSciString().equals("N")) {
								System.out.println("ROVER_04 request GATHER");
								out.println("GATHER");
								
						}
						}					
					}
				}
	
				// another call for current location
				out.println("LOC");
				line = in.readLine();
				if(line == null){
					System.out.println("ROVER_04 check connection to server");
					line = "";
				}
				if (line.startsWith("LOC")) {
					Rover_Current_Loc = extractLocationFromString(line);
					
				}
	
	
				// test for stuckness
				stuck = Rover_Current_Loc.equals(Rover_Previous_Loc);
	
				//System.out.println("ROVER_04 stuck test " + stuck);
				System.out.println("ROVER_04 blocked test " + blocked);
	
				// TODO - logic to calculate where to move next
	
				
				// this is the Rovers HeartBeat, it regulates how fast the Rover cycles through the control loop
				Thread.sleep(sleepTime);
				
				System.out.println("ROVER_04 ------------ bottom process control --------------"); 
			}
		
		// This catch block closes the open socket connection to the server
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
	        if (socket != null) {
	            try {
	            	socket.close();
	            } catch (IOException e) {
	            	System.out.println("ROVER_04 problem closing socket");
	            }
	        }
	    }

	} // END of Rover main control loop
	
	// ####################### Support Methods #############################
	
	private void clearReadLineBuffer() throws IOException{
		while(in.ready()){
			//System.out.println("ROVER_04 clearing readLine()");
			in.readLine();	
		}
	}
	
	public void move(String direction) {
		out.println("MOVE " + direction);
	}
	
	/**
	 * iterate through a scan map to find a tile with radiation. get the
	 * adjusted (absolute) coordinate of the tile and added into a hash set
	 */
	private void detectMineral(MapTile[][] scanMapTiles) {
		for (int x = 0; x < scanMapTiles.length; x++) {
			for (int y = 0; y < scanMapTiles[x].length; y++) {
				MapTile mapTile = scanMapTiles[x][y];
				if (mapTile.getScience() == Science.MINERAL) {
					int tileX = Rover_Current_Loc.xpos + (x - 5);
					int tileY = Rover_Current_Loc.ypos + (y - 5);
					Coord coord = new Coord(mapTile.getTerrain(), mapTile.getScience(), tileX, tileY);
					science_Store.add(coord);
				}
			}
		}
	}

	
	 // Write to each rover the coords of a tile that contains radiation.
	public void shareScience() {
		for (Coord c : science_Store) {
			if (!displayed_science.contains(c)) {
				for (Socket s : output_Sockets)
					try {
						new DataOutputStream(s.getOutputStream()).writeBytes(c.toString() + "\r\n");
					} catch (Exception e) {

					}
				displayed_science.add(c);
			}
		}
	}
	// method to retrieve a list of the rover's EQUIPMENT from the server
	private ArrayList<String> getEquipment() throws IOException {
		//System.out.println("ROVER_04 method getEquipment()");
		Gson gson = new GsonBuilder()
    			.setPrettyPrinting()
    			.enableComplexMapKeySerialization()
    			.create();
		out.println("EQUIPMENT");
		
		String jsonEqListIn = in.readLine(); //grabs the string that was returned first
		if(jsonEqListIn == null){
			jsonEqListIn = "";
		}
		StringBuilder jsonEqList = new StringBuilder();
		//System.out.println("ROVER_04 incomming EQUIPMENT result - first readline: " + jsonEqListIn);
		
		if(jsonEqListIn.startsWith("EQUIPMENT")){
			while (!(jsonEqListIn = in.readLine()).equals("EQUIPMENT_END")) {
				if(jsonEqListIn == null){
					break;
				}
				//System.out.println("ROVER_04 incomming EQUIPMENT result: " + jsonEqListIn);
				jsonEqList.append(jsonEqListIn);
				jsonEqList.append("\n");
				//System.out.println("ROVER_04 doScan() bottom of while");
			}
		} else {
			// in case the server call gives unexpected results
			clearReadLineBuffer();
			return null; // server response did not start with "EQUIPMENT"
		}
		
		String jsonEqListString = jsonEqList.toString();		
		ArrayList<String> returnList;		
		returnList = gson.fromJson(jsonEqListString, new TypeToken<ArrayList<String>>(){}.getType());		
		//System.out.println("ROVER_04 returnList " + returnList);
		
		return returnList;
	}
	

	// sends a SCAN request to the server and puts the result in the scanMap array
	public void loadScanMapFromSwarmServer() throws IOException {
		//System.out.println("ROVER_04 method doScan()");
		Gson gson = new GsonBuilder()
    			.setPrettyPrinting()
    			.enableComplexMapKeySerialization()
    			.create();
		out.println("SCAN");

		String jsonScanMapIn = in.readLine(); //grabs the string that was returned first
		if(jsonScanMapIn == null){
			System.out.println("ROVER_04 check connection to server");
			jsonScanMapIn = "";
		}
		StringBuilder jsonScanMap = new StringBuilder();
		System.out.println("ROVER_04 incomming SCAN result - first readline: " + jsonScanMapIn);
		
		if(jsonScanMapIn.startsWith("SCAN")){	
			while (!(jsonScanMapIn = in.readLine()).equals("SCAN_END")) {
				//System.out.println("ROVER_04 incomming SCAN result: " + jsonScanMapIn);
				jsonScanMap.append(jsonScanMapIn);
				jsonScanMap.append("\n");
				//System.out.println("ROVER_04 doScan() bottom of while");
			}
		} else {
			// in case the server call gives unexpected results
			clearReadLineBuffer();
			return; // server response did not start with "SCAN"
		}
		//System.out.println("ROVER_04 finished scan while");

		String jsonScanMapString = jsonScanMap.toString();
		// debug print json object to a file
		//new MyWriter( jsonScanMapString, 0);  //gives a strange result - prints the \n instead of newline character in the file

		//System.out.println("ROVER_04 convert from json back to ScanMap class");
		// convert from the json string back to a ScanMap object
		scanMap = gson.fromJson(jsonScanMapString, ScanMap.class);		
	}
	

	// this takes the server response string, parses out the x and x values and
	// returns a Coord object	
	public static Coord extractLocationFromString(String sStr) {
		int indexOf;
		indexOf = sStr.indexOf(" ");
		sStr = sStr.substring(indexOf +1);
		if (sStr.lastIndexOf(" ") != -1) {
			String xStr = sStr.substring(0, sStr.lastIndexOf(" "));
			//System.out.println("extracted xStr " + xStr);

			String yStr = sStr.substring(sStr.lastIndexOf(" ") + 1);
			//System.out.println("extracted yStr " + yStr);
			return new Coord(Integer.parseInt(xStr), Integer.parseInt(yStr));
		}
		return null;
	}
	

	/**
	 * Runs the client
	 */
	public static void main(String[] args) throws Exception {
		ROVER_04 client = new ROVER_04();
		client.run();
	}
}