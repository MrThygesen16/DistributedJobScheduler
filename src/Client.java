import java.net.*;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


import java.io.*;

public class Client {
	// initialize socket and input output streams
	private static Socket socket = null;
	private BufferedReader input = null;
	private DataOutputStream out = null;
	private BufferedReader in = null;
	

	// constructor to put ip address and port
	public Client(String address, int port) throws IOException {

		// connect to server
		connect(address, port);

		// takes input from keyboard
		input = new BufferedReader(new InputStreamReader(System.in));

		// sends output to the server
		out = new DataOutputStream(socket.getOutputStream());

		// gets input from server
		in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	}

	private void start () {
	
		// Initial handshake
		sendMessage("HELO");
		readMessage();

		// Send user details
		sendMessage("AUTH " + System.getProperty("user.name"));
		readMessage();

		// populate arrayList of server objects from:
		// 		1. read ds-system xml file and populate Server ArrayList
		// 		OR
		//		2. "GETS Capable"
		ArrayList<Server> servers = new ArrayList<Server>();
		
		//	Used for part 1.
		// 		servers = readXML("ds-system.xml");

		// arrayList for holding job info from "GETS Capable"
		ArrayList<Job> jobs = new ArrayList<Job>();


		// 	Used for part 1.
		// 		find index of largest server type (most cores)
		//		int largestServer = findLargest(servers);

		// handshake completed
		boolean connected = true;
		

		// Tells client it is ready to recieve commands
		sendMessage("REDY");

		// temp string to hold readMessage data
		// we check the contents of this string, rather than call readMessage() 
		String msg = readMessage();
		

		// SCHEDULES JOB TO LARGEST SERVER
		while (connected){
			if (msg.contains("JCPL")){ // Job completed we tell ds-server we are ready
				sendMessage("REDY");
				msg = readMessage(); 
			} else if (msg.contains("NONE")){ // there are no more jobs left
				connected = false;			// so we can send a quit message and exit the program
				sendMessage("QUIT");
			}else {

				if (msg.contains("OK")){ 
					sendMessage("REDY");
					msg = readMessage(); 
						/* 
							* We call  "msg = readMessage()" because:
								1. it is easier to check the contents of the message
								2. we have to check for a message everytime we send a message
						*/
				}

				// we have a JOB incoming, so we create a job objet based on it
				if (msg.contains("JOBN")){
					jobs.add(jobCreator(msg)); // create job 

					// the job arrayList will only ever have 1 item in it at a time...
					sendMessage(getsCapable(jobs.get(0))); // GETS Capable called
					msg = readMessage();

					sendMessage("OK");

					// list of capable servers are added to arrayList of server objects
					msg = readMessage();
					servers = serverCreator(msg);
					sendMessage("OK");
				
					// we should receive a "." here
					msg = readMessage();

					sendMessage(custFirstFit(servers, jobs)); // Scheduling algorithm called here
					msg = readMessage();

					// we only need one job at a time; we remove the fist element
					// so that the first element will always be the current job...
					jobs.remove(0);
				} 
			} 
		}

		
		// close the connection
		try {

			// QUIT hand-shake, must receive confirmation from server for quit
			if (readMessage().contains("QUIT")){
				input.close();
				out.close();
				socket.close();
			}
				
		} catch (IOException i) {
			System.out.println(i);
		}

		// Exit the program
		System.exit(1);
	}


	
	/* 
		
	
		First fit algo
			finds first available server and schedules it
		
		This was my first attempt at a First-Fit algo.
		

	*/
	public String firstFit(ArrayList<Server> servers, ArrayList<Job> job){

		// essentially just always picks the first server
		// from the GETS Capable command

		String serv = "";
		serv = servers.get(0).getType() + " " + servers.get(0).getID();
		return "SCHD " + job.get(0).getJobID() + " " + serv;
	}



	/* 
		
		Custom first fit algo
			much faster than the above implementation
			Espeically when it comes to"
				- "avg waiting time" 
				- "avg turnaround time"

	*/
	public String custFirstFit(ArrayList<Server> servers, ArrayList<Job> job){

		String serv = ""; // string for holding the server info to return back

		for (Server s: servers){
			// find best fit for job
			if (s.getDisk() >= job.get(0).getDiskReq() && s.getCores() >= job.get(0).getCoreReq() && s.getMemory() >= job.get(0).getMemeoryReq()){
			 	serv = s.getType() + " " + s.getID();
				return "SCHD " + job.get(0).getJobID() + " " + serv;
			} else { // if there are non that are absolutely optimal in the GETS Capable list, we just defer to the first from that list
				serv = servers.get(0).getType() + " " + servers.get(0).getID(); //servers.get(0) is the first server on the returned list
			}
		}
		// we know job.get(0) will work as there is only ever 1 item in the job arrayList at a time
		return "SCHD " + job.get(0).getJobID() + " " + serv;
	}



	// takes server input and creates arrayList of CAPABLE SERVER OBJECTS
	public ArrayList<Server> serverCreator(String server){

		// remove unwanted data from string i.e. trailing space etc.
		server = server.trim();

		// temp arrayList to be passed back
		ArrayList<Server> newList = new ArrayList<Server>();

		// split strings by newline
		String[] lines = server.split("\\r?\\n");
 		
		for (String line : lines) {

			// split each line by white space
			String[] splitStr = line.split("\\s+");

			/* 
			Constructing based off of this definition:
				String		int			String		int			int		int		int		int		int
				serverType 	serverID 	state 	curStartTime 	core 	mem 	disk 	#wJobs 	#rJobs
			*/

			//						server type		server ID					state		curStart Time					core count						memory						disk							wJobs							rJobs
			Server s = new Server(splitStr[0], Integer.parseInt(splitStr[1]), splitStr[2], Integer.parseInt(splitStr[3]), Integer.parseInt(splitStr[4]), Integer.parseInt(splitStr[5]), Integer.parseInt(splitStr[6]), Integer.parseInt(splitStr[7]), Integer.parseInt(splitStr[8]) );
			newList.add(s);
        }

		return newList;
	}



	/*
		SENDS THE QUERY FOR AVAILABLE SERVERS 
	*/
	public String getsCapable(Job j){
		
		// grab info from job inputted job object
		return("GETS Capable " + j.getCoreReq() + " " + j.getMemeoryReq() + " " + j.getDiskReq());
	}

	
	/*
		string input to create a new job object
	*/
	public Job jobCreator(String job){

		// get rid of trailing stuff in message
		// this is vital otherwise trailing whitespace 
		// 		will cause an error in parsing the strings to ints
		job = job.trim();

		// split string up by white space; "\\s+" is a regex expression
		String[] splitStr = job.split("\\s+");

		/* 
			Create a new job object;
				[1] = submit Time	| [4] = core req
				[2] = jobID	 	 	| [5] =	memory req
				[3] = run Time 	 	| [6] =	disk req
		*/
		Job j = new Job(Integer.parseInt(splitStr[1]), Integer.parseInt(splitStr[2]), Integer.parseInt(splitStr[3]),  Integer.parseInt(splitStr[4]) ,Integer.parseInt(splitStr[5]), Integer.parseInt(splitStr[6]));

		// returns job object to fill arrayList
		return j;
	}


	// STAGE 1 CODE: Commented Out as it is not used for Stage 2

	// receives string input and the largest server as input
	// schedules job to largest server
	// private String toLargest(String job, Server s){
	// 	String[] splitStr = job.split("\\s+");
	// 	return "SCHD " + splitStr[2] + " " + s.getType() + " " + (s.getLimit()-s.getLimit());
		
	// 	// the index [2] represents the job ID from the String "job"
	// 	//		This string is sent by ds-server to the client 
		
	// }


	// STAGE 1 CODE: Commented Out as it is not used for Stage 2

	// find index of largest server, relative to number of cores
	// private int findLargest(ArrayList<Server> s){
	// 	int lrgNum = 0;
	// 	int lrgIndex = 0;

	// 	for (int i = 0; i < s.size(); i++){
	// 		if (s.get(i).getCores() > lrgNum){
	// 			lrgIndex = i;
	// 			lrgNum = s.get(i).getCores();
	// 		} 
	// 	}

	// 	return lrgIndex;
	// }


	/* 
		We have chosen to comment out the console printing portions of
		sendMessage() and readMessage() as it significantly reduces the
		speed of the test files/scripts. 
	*/
	private void sendMessage (String outStr) {
		// send message to server
		byte[] byteMsg = outStr.getBytes();
		try {
			out.write(byteMsg);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Display outgoing message from client
		System.out.println("OUT: " + outStr);		
	}

	private String readMessage () {
		// read string sent from server
		String inStr = "";

		/* 
			A short is 2 bytes; 
			It stores whole numbers from -32,768 to 32,767:
			
			So the max value is 32,767... We multiply by 2 to get: 65,534
			This should be of sufficient size to store pretty any message from ds-server.
		*/
		char[] cbuf = new char[((int)Short.MAX_VALUE)*2]; 
		try {
			in.read(cbuf);
		} catch (IOException e) {
			e.printStackTrace();
		}
		inStr = new String(cbuf, 0, cbuf.length);

		// Display incoming message from server
		System.out.println("INC: " + inStr);

		return inStr;
	}

	private static void connect(String address, int port) {
		// try establish a connection
		double secondsToWait = 1;
		int tryNum = 1;
		while (true) {
			try {
				System.out.println("Connecting to server at: " + address + ":" + port);
				socket = new Socket(address, port);
				System.out.println("Connected");
				break; // we connected, so exit the loop
			} catch (IOException e) {
				// reconnect failed, wait.
				secondsToWait = Math.min(30, Math.pow(2, tryNum));
				tryNum++;
				System.out.println("Connection timed out, retrying in  " + (int) secondsToWait + " seconds ...");
				try {
					TimeUnit.SECONDS.sleep((long) secondsToWait);
				} catch (InterruptedException ie) {
					// interrupted
				}
			}
		}
	}

	
	// crates arrayList based on an XML File
	// used for stage 1
	public static ArrayList<Server> readXML(String fileName){
        ArrayList<Server> serverList = new ArrayList<Server>();
		
		try {
			// XML file to read
			File systemXML = new File(fileName);

			// Setup XML document parser
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(systemXML);

			// String converting to normalized form
			doc.getDocumentElement().normalize();
			NodeList servers = doc.getElementsByTagName("server");
			for (int i = 0; i < servers.getLength(); i++) {
				Element server = (Element) servers.item(i);

				// Parse all XML attributes to new Server object
				String type = server.getAttribute("type");
				int limit = Integer.parseInt(server.getAttribute("limit"));
				int bootupTime = Integer.parseInt(server.getAttribute("bootupTime"));
				float hourlyRate = Float.parseFloat(server.getAttribute("hourlyRate"));
				int coreCount = Integer.parseInt(server.getAttribute("coreCount"));
				int memory = Integer.parseInt(server.getAttribute("memory"));
				int disk = Integer.parseInt(server.getAttribute("disk"));
				
				Server s = new Server(type,limit,bootupTime,hourlyRate,coreCount,memory,disk);
				serverList.add(s);
			}

			
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return serverList;
    }


	// MAIN function of program
	
	public static void main(String args[]) throws IOException {
		// Specify Server IP address and Port
		Client client = new Client("127.0.0.1", 50000);
		client.start();
	}
	
}