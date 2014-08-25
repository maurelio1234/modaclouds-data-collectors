/**
 * Copyright ${year} imperial
 * Contact: imperial <weikun.wang11@imperial.ac.uk>
 *
 *    Licensed under the BSD 3-Clause License (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://opensource.org/licenses/BSD-3-Clause
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package imperial.modaclouds.monitoring.datacollectors.monitors;


import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import imperial.modaclouds.monitoring.datacollectors.basic.DataCollectorAgent;
import imperial.modaclouds.monitoring.datacollectors.demo.ofbiz.OFBizLogFileMonitor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.restlet.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * Main class.
 */
public class ModacloudsMonitor extends Application
{	
	private static final Logger logger = LoggerFactory.getLogger(ModacloudsMonitor.class);
	
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
//	private static DDAConnector ddaConnector;

	/**
	 * URI of the DC.
	 */
//	private static String ownURI;	

	/**
	 * mode of the DC.
	 */
	private static String mode;

	/**
	 * Knowledge base connector.
	 */
//	private static KBConnector kbConnector;

	/**
	 * The mapp
	 */
	private static Map<String,String> metricCollectorMapping;

	/**
	 * Object store connector.
	 */
//	private static ObjectStoreConnector objectStoreConnector;

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
	 * 
	 */
	public static void runMonitoring (String[] index) {

		int[] intArray = new int[index.length];
		for(int i = 0; i < index.length; i++) {
			if (dcIndex.get(index[i]) == null) {
				logger.warn("Cannot recognise collector: {}", index[i]);
			}
			else {
				intArray[i] = dcIndex.get(index[i]);
			}
		}

		for(int i = 0; i < index.length; i++) {
			if (runningMonitors.contains(index[i])) {
				logger.warn("Collector {} has already started!", index[i]);
				continue;
			}

			runningMonitors.add(index[i]);
			AbstractMonitor newMonitor = null;
			switch (intArray[i]) {
			case 1:
				newMonitor = new JMXMonitor(DataCollectorAgent.getAppId(), mode);
				monitors.add(newMonitor);
				break;
			case 2:
				newMonitor = new CollectlMonitor(DataCollectorAgent.getVmId(), mode);
				monitors.add(newMonitor);
				break;
			case 3:
				newMonitor = new SigarMonitor(DataCollectorAgent.getVmId(), mode);
				monitors.add(newMonitor);
				break;
			case 4:
				newMonitor = new OFBizLogFileMonitor(DataCollectorAgent.getAppId(), mode);
				monitors.add(newMonitor);
				break;
			case 5:
				newMonitor = new LogFileMonitor(DataCollectorAgent.getAppId(), mode);
				monitors.add(newMonitor);
				break;
			case 6:
				newMonitor = new MySQLMonitor(DataCollectorAgent.getAppId(), mode);
				monitors.add(newMonitor);
				break;
			case 7:
				newMonitor = new CloudWatchMonitor(DataCollectorAgent.getVmId(), mode);
				monitors.add(newMonitor);
				break;
			case 8:
				newMonitor = new FlexiMonitor(DataCollectorAgent.getVmId(), mode);
				monitors.add(newMonitor);
				break;
			case 9:
				newMonitor = new EC2SpotPriceMonitor(DataCollectorAgent.getVmId(), mode);
				monitors.add(newMonitor);
				break;
			case 10:
				newMonitor = new StartupTimeMonitor(DataCollectorAgent.getVmId(), mode);
				monitors.add(newMonitor);
				break;
			case 11:
				newMonitor = new CostMonitor(DataCollectorAgent.getVmId(), mode);
				monitors.add(newMonitor);
				break;
			case 12:
				newMonitor = new AvailabilityMonitor(DataCollectorAgent.getVmId(), mode);
				monitors.add(newMonitor);
				break;
			case 13:
				newMonitor = new DetailedCostMonitor(DataCollectorAgent.getVmId(), mode);
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
				logger.warn("Collector {} has already stopped!", index[i]);
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
			logger.error("Please input the mode of the DC");
			System.exit(-1);
		}

		mode = args[0];

		initSetup();
		DataCollectorAgent.initialize();
		
		if (mode.equals("kb")) {
			DataCollectorAgent.getInstance().startSyncingWithKB();
		}
		else {
			String[] strArray = args[2].split(",");
			runMonitoring(strArray);
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
			logger.error("Metric {} not found", metricName);
			return null;
		}
		else {
			return collector;
		}
	}

}
