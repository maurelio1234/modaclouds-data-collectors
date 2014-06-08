/**
 * Copyright ${year} imperial
 * Contact: imperial <weikun.wang11@imperial.ac.uk>
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
import org.xml.sax.SAXException;

import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import imperial.modaclouds.monitoring.datacollectors.demo.ofbiz.OFBizLogFileMonitor;
import it.polimi.modaclouds.monitoring.ddaapi.DDAConnector;
import it.polimi.modaclouds.monitoring.kb.api.KBConnector;
import it.polimi.modaclouds.monitoring.objectstoreapi.ObjectStoreConnector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.DataCollector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.KBEntity;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	 * DDa connector.
	 */
	private static DDAConnector ddaConnector;

	private static String ownURI;	
	
	/**
	 * Knowledge base connector.
	 */
	private static KBConnector kbConnector;

	private static Map<String,String> metricCollectorMapping;

	/**
	 * Object store connector.
	 */
	private static ObjectStoreConnector objectStoreConnector;

	/**
	 * Initial setup of the monitor.
	 */
	public static void initSetup() throws ParserConfigurationException, SAXException, IOException
	{
		dcIndex = new HashMap<String,Integer>();
		dcIndex.put("jmx", 1);
		dcIndex.put("collectl", 2);
		dcIndex.put("sigar", 3);
		dcIndex.put("ofbiz", 4);
		dcIndex.put("logFile", 5);
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
				newMonitor = new JMXMonitor(ownURI);
				monitors.add(newMonitor);
				break;
			case 2:
				newMonitor = new CollectlMonitor(ownURI);
				monitors.add(newMonitor);
				break;
			case 3:
				newMonitor = new SigarMonitor(ownURI);
				monitors.add(newMonitor);
				break;
			case 4:
				newMonitor = new OFBizLogFileMonitor(ownURI);
				monitors.add(newMonitor);
				break;
			case 5:
				newMonitor = new LogFileMonitor(ownURI);
				monitors.add(newMonitor);
				break;
			case 6:
				newMonitor = new MySQLMonitor(ownURI);
				monitors.add(newMonitor);
				break;
			case 7:
				newMonitor = new CloudWatchMonitor(ownURI);
				monitors.add(newMonitor);
				break;
			case 8:
				newMonitor = new FlexiMonitor(ownURI);
				monitors.add(newMonitor);
				break;
			case 9:
				newMonitor = new EC2SpotPriceMonitor(ownURI);
				monitors.add(newMonitor);
				break;
			case 10:
				newMonitor = new StartupTimeMonitor(ownURI);
				monitors.add(newMonitor);
				break;
			case 11:
				newMonitor = new CostMonitor(ownURI);
				monitors.add(newMonitor);
				break;
			case 12:
				newMonitor = new AvailabilityMonitor(ownURI);
				monitors.add(newMonitor);
				break;
			case 13:
				newMonitor = new DetailedCostMonitor(ownURI);
				monitors.add(newMonitor);
				break;
			}
			newMonitor.start();
			newMonitor.init();
		}
	}

	//	/**
	//	 * Reconfigurate the monitors for the sampling probability.
	//	 * @param monitor name
	//	 * @param sampling probability
	//	 */
	//	public static void reconfigMonitor(String monitor, double samplingProb) {
	//		if (!runningMonitors.contains(monitor)) {
	//			System.out.println("WARNING: collector " + monitor+ " has not started yet");
	//		}
	//		else {
	//			for (int j = 0; j < monitors.size(); j++) {
	//				if (monitor.equals(monitors.get(j).getMonitorName())) {
	//					monitors.get(j).reconfig(samplingProb);
	//					break;
	//				}
	//			}
	//		}
	//	}

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
		if (args.length < 1) {
			System.out.println("Please input the uri of the instance");
			System.exit(-1);
		}
		
		ownURI = args[0];
		
		//ddaConnector = DDAConnector.getInstance();
		try {
			//objectStoreConnector = ObjectStoreConnector.getInstance();
			//MO.setKnowledgeBaseURL(objectStoreConnector.getKBUrl());

			kbConnector = KBConnector.getInstance();
			//kbConnector.setKbURL(new URL(MO.getKnowledgeBaseDataURL()));
		} catch (Exception e) {
			e.printStackTrace();
		}

		Logger.getLogger( "org" ).setLevel( Level.WARN );

		initSetup();

		List<String> oldCollectors = new ArrayList<String>(); 

		long startTime = 0;

		while(true) {

			if (System.currentTimeMillis() - startTime > 10000) {

				List<String> newCollectors = new ArrayList<String>(); 
				Set<KBEntity> dcConfig = kbConnector.getAll(DataCollector.class);
				
				for (KBEntity kbEntity: dcConfig) {
					DataCollector dc = (DataCollector) kbEntity;
					
					if (dc.getTargetResources().iterator().next().getUri().equals(ownURI)) {
					
					//dc.setEnabled(true);
					//kbConnector.add(dc);
						if (!newCollectors.contains(findCollector(dc.getCollectedMetric()))) {
							newCollectors.add(findCollector(dc.getCollectedMetric()));
						}
					}
				}
				
				List<String> toRun = new ArrayList<String>();
				List<String> toStop = new ArrayList<String>();
				
				for (String newCollector: newCollectors) {
					if (!oldCollectors.contains(newCollector)) {
						toRun.add(newCollector);
					}
				}
				
				for (String oldCollector: oldCollectors) {
					if (!newCollectors.contains(oldCollector)) {
						toStop.add(oldCollector);
					}
				}

				runMonitoring(toRun.toArray(new String[toRun.size()]));
				stopMonitoring(toStop.toArray(new String[toStop.size()]));
				
				oldCollectors = newCollectors;
				
				startTime = System.currentTimeMillis();
				
				Thread.sleep(10000);
			}
		}

		//		if (args.length != 0) {
		//			String[] strArray = args[0].split(",");
		//			
		//			runMonitoring(runCollector.toArray(new String[runCollector.size()]));
		//		}

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

	public static String findCollector(String metricName) {
		if (metricCollectorMapping == null) {
			metricCollectorMapping = new HashMap<String,String>();

			metricCollectorMapping.put("cpuutilization", "sigar");
			metricCollectorMapping.put("cpustolen", "sigar");
			metricCollectorMapping.put("memused", "sigar");
			metricCollectorMapping.put("threads_running", "mysql");
			metricCollectorMapping.put("threads_cached", "mysql");
			metricCollectorMapping.put("threads_connected", "mysql");
			metricCollectorMapping.put("threads_created", "mysql");
			metricCollectorMapping.put("queries", "mysql");
			metricCollectorMapping.put("bytes_received", "mysql");
			metricCollectorMapping.put("bytes_sent", "mysql");
			metricCollectorMapping.put("connections", "mysql");
			metricCollectorMapping.put("aborted_connects", "mysql");
			metricCollectorMapping.put("aborted_clients", "mysql");
			metricCollectorMapping.put("table_locks_immediate", "mysql");
			metricCollectorMapping.put("table_locks_waited", "mysql");
			metricCollectorMapping.put("com_insert", "mysql");
			metricCollectorMapping.put("com_delete", "mysql");
			metricCollectorMapping.put("com_update", "mysql");
			metricCollectorMapping.put("com_select", "mysql");
			metricCollectorMapping.put("qcache_hits", "mysql");
			metricCollectorMapping.put("diskreadopscloudwatch", "cloudwatch");
			metricCollectorMapping.put("cpuutilizationcloudwatch", "cloudwatch");
			metricCollectorMapping.put("diskreadopscloudwatch", "cloudwatch");
			metricCollectorMapping.put("diskwriteopscloudwatch", "cloudwatch");
			metricCollectorMapping.put("diskreadbytescloudwatch", "cloudwatch");
			metricCollectorMapping.put("diskwritebytescloudwatch", "cloudwatch");
			metricCollectorMapping.put("networkincloudwatch", "cloudwatch");
			metricCollectorMapping.put("networkoutcloudwatch", "cloudwatch");
			metricCollectorMapping.put("peakthreadcountjmx","jmx");
			metricCollectorMapping.put("heapmemoryusedjmx", "jmx");
			metricCollectorMapping.put("uptimejmx", "jmx");
			metricCollectorMapping.put("cpuutilizationcollectl", "collectl");
			metricCollectorMapping.put("contextswitchcollectl", "collectl");
			metricCollectorMapping.put("cpuutilstolencollectl", "collectl");
			metricCollectorMapping.put("interruptscollectl", "collectl");
			metricCollectorMapping.put("maxprocscollectl", "collectl");
			metricCollectorMapping.put("maxprocsqueuecollectl", "collectl");
			metricCollectorMapping.put("memusedcollectl", "collectl");
			metricCollectorMapping.put("memSwapspaceusedcollectl", "collectl");
			metricCollectorMapping.put("networkinbytescollectl", "collectl");
			metricCollectorMapping.put("networkoutbytescollectl", "collectl");
			metricCollectorMapping.put("generalcost", "cost");
			metricCollectorMapping.put("ec2-spotprice", "ec2-spotPrice");
			metricCollectorMapping.put("responseinfo", "ofbiz");
			metricCollectorMapping.put("startuptime", "startupTime");
			metricCollectorMapping.put("logfile", "logFile");
			metricCollectorMapping.put("detailedcost", "detailedCost");
			metricCollectorMapping.put("availability", "availability");
			metricCollectorMapping.put("flexi", "flexi");
		}
		
		String collector;
		collector = metricCollectorMapping.get(metricName.toLowerCase());
		
		if (collector == null) {
			System.out.println("Metric: "+metricName+" not found");
			return null;
		}
		else {
			return collector;
		}
	}

}
