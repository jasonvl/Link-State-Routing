import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Scanner;

public class LinkedStateRouting {

	private HashMap<Integer, Integer> nodeToIndex;
	private HashMap<Integer, Integer> indexToNode;
	private LinkedHashMap<Integer, Integer> multicastSession;
	private int[][] graph;
	private int startNode;
	private int nodeCount;

	// crate graph representation of input file using adjacency matrix
	public void parseFile(Scanner input) {

		nodeToIndex = new HashMap<Integer, Integer>();
		indexToNode = new HashMap<Integer, Integer>();
		int multicastTimeout = 0;
		int multicastStartNode = 0; // the node from event A
		graph = new int[10000][10000]; // MAKE DYNAMIC
		nodeCount = 0;

		// int prevSeqNo = -1; // keep track of previous sequence number
		while (input.hasNextLine()) {
			String line = input.nextLine(); // read a line
			if (!line.startsWith("#") && !line.isEmpty()) { // ignore if the line contains # or is blank
				String modified = line.replaceAll("[<,>]", " "); // get all '<', ',' and '>' with " "
				Scanner temp = new Scanner(modified);

				int timeStamp = temp.nextInt(); // read the timestamp
//				System.out.println("Timestamp: " + timeStamp);
				String event = temp.next(); // read the event type - I, L or F
//				System.out.println("Event: " + event);

				if (event.equals("I")) {
					startNode = temp.nextInt();
					checkNode(startNode);
					int processorNode = startNode;
//					System.out.println("starting node: " + startNode);
					nodeToIndex.put(startNode, nodeCount); // starting node always associated with index 0
					indexToNode.put(nodeCount, startNode);
					graph[0][0] = 0; // distance of starting node to itself is 0

					// read in rest of <node, weight>
					while (temp.hasNext()) {
						int node = temp.nextInt();
						int weight = temp.nextInt();
//						System.out.println("<" + node + ", " + weight + ">");

						constructGraph(processorNode, node, weight);
					}
				} else if (event.equals("L")) {
//					System.out.println("*** type L ***");
					int processorNode = temp.nextInt();
					checkNode(startNode);
//					System.out.println("processor node: " + processorNode);
					int currSeqNo = temp.nextInt(); // LEAVE UNCOMMENTED!!!
//					System.out.println("Sequence number: " + currSeqNo);
					// if sequence number of LSP is greater to previous sequence number
					// if (currSeqNo > prevSeqNo) {
					// read in rest of <node, weight>
					while (temp.hasNext()) {
						int node = temp.nextInt();
						checkNode(startNode);
						int weight = temp.nextInt();
//						System.out.println("<" + node + ", " + weight + ">");

						constructGraph(processorNode, node, weight);
					}
					// discard if LSP has smaller or equal sequence number
					// to max seen so far
					// } else {
					// System.out.println("LSP has smaller or equal sequence
					// number to max seen so far - discarding LSP");
					// }
					// prevSeqNo = currSeqNo; // update previous sequence number

				} else if (event.equals("A")) {
//					System.out.println("*** type A ***");
					int node = temp.nextInt();
					multicastStartNode = node;
					int sessionAddress = temp.nextInt();
					checkMultiAddress(sessionAddress); // check session address
					multicastTimeout = temp.nextInt();
//					System.out.println( "node " + node + " initilized new multicast session with address " + sessionAddress);
					multicastSession = new LinkedHashMap<Integer, Integer>();
					multicastSession.put(node, sessionAddress);

				} else if (event.equals("J")) {
//					System.out.println("*** type J ***");
					int node = temp.nextInt();
					int sessionAddress = temp.nextInt();
					checkMultiAddress(sessionAddress); // check session address

					// check if the session exist - if not then drop the packet
					if (multicastSession.containsValue(sessionAddress)) {
						multicastSession.put(node, sessionAddress);
//						System.out.println(node + " added to session " + sessionAddress);
					} else {
						System.out.println("session that " + node + " is requsting to join does not exist - dropping packet");
						dropMulticastPacket(multicastTimeout, sessionAddress, node);
					}
				} else if (event.equals("F")) {

					int node = temp.nextInt();
					int nodeID = nodeToIndex.get(node); // get ID associated with node

					// F belonging to unicast is followed by one int after the
					// "F" - multicast is followed by two ints
					// e.g., 35 F 4109 vs 67 F 1203 5664
					if (!temp.hasNextInt()) {
//						System.out.println("*** F is of type unicast ***");
						int source = nodeToIndex.get(startNode); // source should always be 0 since it is the starting node
//						System.out.println("source: " + startNode + "(id#" + source + "), destination: " + node + "(id#" + nodeID + ")");

						ArrayList<Integer> shortestPath = dijkstra(graph, source, nodeID);

						// print out the shortest path for testing
//						System.out.println("shortest path to " + node + ":");
//						for (int i = 0; i < shortestPath.size(); i++)
//							System.out.print(shortestPath.get(i) + " ");
//						System.out.println();
//						for (int i = 0; i < shortestPath.size(); i++) {
//							int nextHop = shortestPath.get(i);
//							forwardUnicastPacket(timeStamp, nodeID, nextHop);
//						}
						int nextHop = shortestPath.get(0);
						forwardUnicastPacket(timeStamp, nodeID, nextHop);
					} else {
//						System.out.println("*** F is of type multicast ***");
						int multicastGroup = temp.nextInt();
//						System.out.println("node: " + node + "(id#" + nodeID + "), multicase group: " + multicastGroup);

						// drop packet if multicast session does not exist
						if(!multicastSession.containsValue(multicastGroup)) {
//							System.out.println("session does not exist..dropping packet");
							dropMulticastPacket(timeStamp, multicastGroup, node);
						} else {
						
							// iterate through all the nodes (keys) in map and
							// compute shortest path back to sender
							// (multicastStartNode)
							ArrayList<Integer> listOfNextHops = new ArrayList<Integer>(); // to store list of next hops
						
							// print multicast session for testing
//							for (Integer key : multicastSession.keySet()) {
//							    System.out.println(key + ":" + multicastSession.get(key));
//							}
						
							boolean hasPath = false;
							for (Integer key : multicastSession.keySet()) {
								if (key != node && key != multicastStartNode) { // to avoid comparing node to itself and starting node to itself
//									System.out.println("getting shortest path from " + node + " to " + key);
									ArrayList<Integer> shortestPath = dijkstra(graph, nodeToIndex.get(key), nodeToIndex.get(node)); // shortest path from endpoint to sender

									// if shortest path goes through the router
									// (start node), add first hop towards that
									// endpoint to list of hopes through which the
									// packet is forwarded
									if (shortestPath.contains(nodeToIndex.get(startNode))) {
//										System.out.println("shortest path for node " + key + " DOES goe through router (" + startNode + ")");
										hasPath = true;
									
										// print out path
//										for (Integer i : shortestPath)
//											System.out.print(i + " ");
//										System.out.println();

										int indexOfStartNode = shortestPath.indexOf(nodeToIndex.get(startNode)); 
										if(indexOfStartNode - 1 == -1) {
											if(!listOfNextHops.contains(key)) {
												listOfNextHops.add(key);
//												System.out.println(key + " added to hops");
											} 
										} else {
											if(!listOfNextHops.contains(indexToNode.get(shortestPath.get(indexOfStartNode - 1)))) {
											listOfNextHops.add(indexToNode.get(shortestPath.get(indexOfStartNode - 1)));
//												System.out.println(indexToNode.get(shortestPath.get(indexOfStartNode - 1)) + " added to hops");
											}
										}
									} 
								}
							} 
						
							// drop packet if router lies on none of shortest paths back to source 
							if(!hasPath) {
//								System.out.println("doesn't have path - dropping packet");
								dropMulticastPacket(timeStamp, multicastGroup, node);
							}
							
							forwardMulticastPacket(timeStamp, multicastGroup, listOfNextHops, listOfNextHops.size());
						}
					}
					// else if event.equals("Q")
				} else {
//					System.out.println("*** type Q ***");

					int node = temp.nextInt();
					int sessionAddress = temp.nextInt();

					if (multicastSession.size() != 0) {
						multicastSession.remove(node); // remove node from
														// HashMap
//						System.out.println(node + " has left the session(" + sessionAddress + ")");
						// if session is of size 0
					}
				}
			}
		}
		

		input.close(); // close Scanner
		// printGraph();

	}

	private void constructGraph(int processorNode, int node, int weight) {

		// System.out.println("[" + processorNode + ", " + node + ", " + weight
		// + "]");
		// either add processor node to map or get index of it if not contained
		// in map
		int processorIndex = 0;
		if (!nodeToIndex.containsKey(processorNode)) {
//			System.out.println("graph DOES NOT contain processor node");
			nodeCount++;
			processorIndex = nodeCount; // update index of processor node
			nodeToIndex.put(processorNode, nodeCount); // add node and index to
														// map e.g., node 4325
														// (first node read) ->
														// 0
			indexToNode.put(nodeCount, processorNode);
//			System.out.println("processor node " + processorNode + " -> " + nodeCount + " added to map");
			// else get the ID assoicated with the node
		} else {
//			System.out.println("graph DOES contain processor node");
			processorIndex = nodeToIndex.get(processorNode);
//			System.out.println("retrieved processor node " + processorNode + " -> " + processorIndex);
		}

		// either add non-processor node to map or get index of it if not
		// contained in map
		int nodeIndex = 0;
		if (!nodeToIndex.containsKey(node)) {
//			System.out.println("graph DOES NOT contain node");
			nodeCount++;
			nodeIndex = nodeCount;
			nodeToIndex.put(node, nodeCount); // add node and index to map e.g.,
												// node 4325 (first node read)
												// -> 0
			indexToNode.put(nodeCount, node);
//			System.out.println(node + " -> " + nodeCount + " added to map");
			// else get the ID associated with the node
		} else {
//			System.out.println("graph DOES contain node");
			nodeIndex = nodeToIndex.get(node);
//			System.out.println("retrieved " + node + " -> " + nodeIndex);
		}

		// update graph
		graph[processorIndex][nodeIndex] = weight;
		graph[nodeIndex][processorIndex] = weight; // mirror graph diagonally
													// from top-left to
													// bottom-right
		// System.out.println("graph at " + "(" + processorIndex + ", " + nodeIndex + ") " + " is " + weight);
		// System.out.println("graph at " + "(" + nodeIndex + ", " + processorIndex + ") " + " is " + weight);

	}
	
	public ArrayList<Integer> dijkstra(int graph[][], int src, int dest) {
		
		int V = graph.length;
		int dist[] = new int[V]; // vertices included in SPT
		boolean visited[] = new boolean[V]; // vertices not yet included in SPT
		int pathToDest[] = new int[V]; // shortest path from source to destination

		for (int i = 0; i < V; i++) {
			dist[i] = Integer.MAX_VALUE;
			pathToDest[src] = -1;
		}

		dist[src] = 0;
		for (int i = 0; i < V - 1; i++) {
			// find the min distance 
			int min = Integer.MAX_VALUE;
			int minDist = 0;
			for (int v = 0; v < V; v++) {
				if (visited[v] == false && dist[v] <= min) {
					min = dist[v];
					minDist = v;
				}
			}
			
			if(minDist == dest)
				break;
			
//			System.out.println("min distance: " + minDist);
			visited[minDist] = true;
//			System.out.println(minDist + " is marked as TRUE");

			for (int j = 0; j < V; j++) {
				if (!visited[j] && graph[minDist][j] != 0 && dist[minDist] + graph[minDist][j] < dist[j]) {
					int totalDist = dist[minDist] + graph[minDist][j];
					dist[j] = totalDist;
//					System.out.println("new distance from " + minDist + " to " + j + " is now " + totalDist);
					pathToDest[j] = minDist;
//					System.out.println(minDist + " added to path at index " + j);
				}
			}
		}
		
		ArrayList<Integer> shortestPath = new ArrayList<Integer>();
		createPath(pathToDest, shortestPath, dest);
		
		return shortestPath;
		
	}

	private void createPath(int[] path, ArrayList<Integer> shortestPath, int j) {
	
		if(path[j] == -1)
			return;
		createPath(path, shortestPath, path[j]);
		//	System.out.print(" " + j);
		shortestPath.add(j);
	
}

	public void forwardUnicastPacket(int timeNow, int destination, int nextHop) {

		System.out.println("FU " + timeNow + " " + indexToNode.get(destination) + " " + indexToNode.get(nextHop));

	}

	public void forwardMulticastPacket(int timeNow, int destination, ArrayList<Integer> nextHops, int listLen) {

		// reorder hops from least to greatest
		Collections.sort(nextHops);
		
		for(int i = 0; i < listLen; i++) {
			System.out.println("FMP " + timeNow + " " + destination + " " + nextHops.get(i));
		}
	}

	public void dropMulticastPacket(int timeNow, int destination, int source) {

		System.out.println("DMP " +  timeNow + " " + destination + " " + source);

	}

	// print graph
	public void printGraph() {

		for (int r = 0; r < graph.length; r++) {
			for (int c = 0; c < graph[r].length; c++)
				System.out.print(graph[r][c] + " ");
			System.out.println();
		}

	}

	// check if a node is valid
	private void checkNode(int node) {

		if (node > 0 && node < 32767)
			return;
		else
			throw new IllegalArgumentException();
	}

	// check if a multicast address is in range
	private void checkMultiAddress(int address) {

		if (address > 32768 && address < 65536)
			return;
		else
			throw new IllegalArgumentException();

	}

	public static void main(String[] args) {

		LinkedStateRouting lsr = new LinkedStateRouting();

		// read in file
		try {
			File file = new File(args[0]);
//			File file = new File("/Users/jasonvl/Desktop/CompSci/CSCI280/Project2/src/multicast_trace.txt");
			//File file = new File("/Users/jasonvl/Desktop/CompSci/CSCI280/Project2/src/bigger_trace_3.txt");
			Scanner input = new Scanner(file);
			lsr.parseFile(input);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

}
