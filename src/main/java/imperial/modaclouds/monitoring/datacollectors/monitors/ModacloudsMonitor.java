/**
 * Copyright (c) 2012-2013, Imperial College London, developed under the MODAClouds, FP7 ICT Project, grant agreement n�� 318484
 * All rights reserved.
 * 
 *  Contact: imperial <weikun.wang11@imperial.ac.uk>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package imperial.modaclouds.monitoring.datacollectors.monitors;


import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Restlet;
import org.restlet.data.Protocol;
import org.restlet.routing.Router;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import imperial.modaclouds.monitoring.datacollectors.demo.ofbiz.OFBizLogFileMonitor;
import imperial.modaclouds.monitoring.datacollectors.management.ManageMonitors;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Main class.
 */
public class ModacloudsMonitor extends Application
{	
	/**
	 * Index of the monitors.
	 */
	private static Map<String,Integer> dcIndex;
	
	/**
	 * The list of the monitors.
	 */
	private static List<AbstractMonitor> monitors;
	
	/**
	 * The name of the running monitors.
	 */
	private static List<String> runningMonitors;
	
	/**
	 * The static class of ModacloudsMonitor.
	 */
	private static ModacloudsMonitor modacloudsMonitor;
	
	/**
	 * Initial setup of the monitor.
	 */
	public static void initSetup() throws ParserConfigurationException, SAXException, IOException
	{
		//URL urlFile = ModacloudsMonitor.class.getResource("/basicSetup.xml");
		//System.out.println(urlFile);
		//String urlFile2 = ModacloudsMonitor.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		String filePath = System.getProperty("user.dir") + "/config/basicSetup.xml";
		
		File fXmlFile = new File(filePath);
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(fXmlFile);
		
		doc.getDocumentElement().normalize();
		
		NodeList nList = doc.getElementsByTagName("setup");
		
		for (int i = 0; i < nList.getLength(); i++) {
			 
			Node nNode = nList.item(i);
	 	 
			if (nNode.getNodeType() == Node.ELEMENT_NODE) {
	 
			//	Element eElement = (Element) nNode;
				
			}
		}
		
		dcIndex = new HashMap<String,Integer>();
		dcIndex.put("jmx", 1);
		dcIndex.put("collectl", 2);
		dcIndex.put("sigar", 3);
		dcIndex.put("ofbiz", 4);
		dcIndex.put("apache", 5);
		dcIndex.put("mysql", 6);
		dcIndex.put("cloudwatch", 7);
		dcIndex.put("flexiant", 8);
		dcIndex.put("ec2-spotPrice", 9);
		dcIndex.put("startupTime", 10);
		dcIndex.put("cost", 11);
		dcIndex.put("availability", 12);
		dcIndex.put("detailedCost", 13);
		
		monitors = new ArrayList<AbstractMonitor>();
		runningMonitors = new ArrayList<String>();
	}

	
	/**
	 * run monitoring given the collector index.
	 * 
	 * @param index: monitoring collector index
	 * @throws IOException
	 * @throws InterruptedException 
	 * 
	 */
	public static void runMonitoring (String[] index) throws IOException, InterruptedException {
				
		int[] intArray = new int[index.length];
		for(int i = 0; i < index.length; i++) {
			if (dcIndex.get(index[i]) == null) {
				System.out.println("WARNING: Cannot recognise collector: "+index[i]);
			}
			else {
				intArray[i] = dcIndex.get(index[i]);
			}
		}
						
		for(int i = 0; i < index.length; i++) {
			if (runningMonitors.contains(index[i])) {
				System.out.println("WARNING: collector " + index[i]+ " has already started!");
				continue;
			}
			
			runningMonitors.add(index[i]);
			AbstractMonitor newMonitor = null;
			switch (intArray[i]) {
				case 1:
					newMonitor = new JMXMonitor();
					monitors.add(newMonitor);
					break;
				case 2:
					newMonitor = new CollectlMonitor();
					monitors.add(newMonitor);
					break;
				case 3:
					newMonitor = new SigarMonitor();
					monitors.add(newMonitor);
					break;
				case 4:
					newMonitor = new OFBizLogFileMonitor();
					monitors.add(newMonitor);
					break;
				case 5:
					newMonitor = new ApacheLogFileMonitor();
					monitors.add(newMonitor);
					break;
				case 6:
					newMonitor = new MySQLMonitor();
					monitors.add(newMonitor);
					break;
				case 7:
					newMonitor = new CloudWatchMonitor();
					monitors.add(newMonitor);
					break;
				case 8:
					newMonitor = new FlexiMonitor();
					monitors.add(newMonitor);
					break;
				case 9:
					newMonitor = new EC2SpotPriceMonitor();
					monitors.add(newMonitor);
					break;
				case 10:
					newMonitor = new StartupTimeMonitor();
					monitors.add(newMonitor);
					break;
				case 11:
					newMonitor = new CostMonitor();
					monitors.add(newMonitor);
					break;
				case 12:
					newMonitor = new AvailabilityMonitor();
					monitors.add(newMonitor);
					break;
				case 13:
					newMonitor = new DetailedCostMonitor();
					monitors.add(newMonitor);
					break;
			}
			newMonitor.start();
			newMonitor.init();
		}
	}
	
	/**
	 * Reconfigurate the monitors for the sampling probability.
	 * @param monitor name
	 * @param sampling probability
	 */
	public static void reconfigMonitor(String monitor, double samplingProb) {
		if (!runningMonitors.contains(monitor)) {
			System.out.println("WARNING: collector " + monitor+ " has not started yet");
		}
		else {
			for (int j = 0; j < monitors.size(); j++) {
				if (monitor.equals(monitors.get(j).getMonitorName())) {
					monitors.get(j).reconfig(samplingProb);
					break;
				}
			}
		}
	}

	/**
	 * stop particular monitors.
	 * @param index The index of the monitors
	 */
	public static void stopMonitoring (String[] index) {
		
		for (int i = 0; i < index.length; i++) {
			if (!runningMonitors.contains(index[i])) {
				System.out.println("WARNING: collector " + index[i]+ " has already stopped!");
				continue;
			}
			
			for (int j = 0; j < monitors.size(); j++) {
				if (index[i].equals(monitors.get(j).getMonitorName())) {
					monitors.get(j).stop();
					monitors.remove(j);
					runningMonitors.remove(index[i]);
					break;
				}
			}
		}
	}
	
	/**
	 * Main function.
	 */
	public static void main( String[] args ) throws Exception
	{
		Logger.getLogger( "org" ).setLevel( Level.WARN );

		Component component = new Component();
		component.getServers().add(Protocol.HTTP, 8182);
		
		modacloudsMonitor = new ModacloudsMonitor();
		component.getDefaultHost().attach("", modacloudsMonitor);

	    component.start();
		
		initSetup();
		
		if (args.length != 0) {
			String[] strArray = args[0].split(",");
			
			runMonitoring(strArray);
		}
		
//		Scanner scanIn = new Scanner(System.in);
//		String line;
//		while (true) {
//			if (scanIn.hasNextLine()) {
//				if ((line = scanIn.nextLine()) != null) {
//					String[] command = line.split("[,\\s]+");
//					if (command[0].equals("start")) {
//						String[] startIndex = new String[command.length-1];
//						for(int i = 1; i < command.length; i++) {
//						    startIndex[i-1] = command[i];
//						}
//						runMonitoring(startIndex);
//					}
//					else if (command[0].equals("stop")) {
//						String[] threadName = new String[command.length-1];
//						for(int i = 1; i < command.length; i++) {
//							threadName[i-1] = command[i];
//						}
//						stopMonitoring(threadName);
//					}
//					else if (command[0].equals("close")) {
//						scanIn.close();
//						System.exit( 0 );
//					}
//				}
//			}
//		}

	}
	
	@Override
    public synchronized Restlet createInboundRoot() {
        Router router = new Router(getContext());

        // Defines only one route
        router.attach("/commands", ManageMonitors.class);

        return router;
    }

}
