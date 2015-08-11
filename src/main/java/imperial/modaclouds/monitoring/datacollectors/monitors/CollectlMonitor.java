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
import imperial.modaclouds.monitoring.datacollectors.basic.Config;
import imperial.modaclouds.monitoring.datacollectors.basic.ConfigurationException;
import it.polimi.tower4clouds.data_collector_library.DCAgent;
import it.polimi.tower4clouds.model.ontology.VM;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * The monitoring collector for collectl.
 */
public class CollectlMonitor extends AbstractMonitor {

	private Logger logger = LoggerFactory.getLogger(CollectlMonitor.class);
	/**
	 * Csparql server address.
	 */
	private String serverIP;

	/**
	 * Socket client.
	 */
	private Socket client;

	/**
	 * collectl monitor thread.
	 */
	private Thread colt;

	/**
	 * collectl start time.
	 */
	private long collectlStartTime;

	/**
	 * Monitoring period.
	 */
	private int period;

	/**
	 * The metric pair containing the category of the metrics, the name of the metrics and their index in collectl.
	 */
	private Map<String,Map<String,Integer>> metricPair;

	/**
	 * Define cpu monitor as number 1.
	 */
	private static final int CPU = 1;

	/**
	 * Define disk monitor as number 2.
	 */
	private static final int DISK = 2;

	/**
	 * Define memory monitor as number 3.
	 */
	private static final int MEMORY = 3;

	/**
	 * Define network monitor as number 4.
	 */
	private static final int NETWORK = 4;

	/**
	 * Define process monitor as number 5.
	 */
	private static final int PROCESS = 5;

	/**
	 * The unique monitored target.
	 */
	private String monitoredTarget;

	/**
	 * The sampling probability.
	 */
	private double samplingProb;

	private DCAgent dcAgent;



	/**
	 * Constructor of the class.
	 */
	public CollectlMonitor(String resourceId, String mode)
	{
		//this.monitoredResourceID = "FrontendVM";
		//this.monitoredTarget = monitoredResourceID;
		super(resourceId, mode);

		monitoredTarget = resourceId;

		monitorName = "collectl";
		serverIP = "localhost";
	}

	@Override
	public void run ()
	{	
		try {
			client = new Socket(serverIP, 2655);
			BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

			long startTime = 0;

			// correspond to command: collectl -scndm --verbose -A server
			while(!colt.isInterrupted()) 
			{
				if (mode.equals("tower4clouds")) {
					if (System.currentTimeMillis() - startTime > 10000) {
						ArrayList<String> requiredMetric = new ArrayList<String>();

						for (String metric : getProvidedMetrics()) {
							VM resource = new VM(Config.getInstance().getVmType(), 
									Config.getInstance().getVmId());
							if (dcAgent.shouldMonitor(resource, metric)) {
								Map<String, String> parameters = dcAgent.getParameters(resource, metric);

								requiredMetric.add(metric);

								samplingProb = Double.valueOf(parameters.get("samplingProbability"));
							}
						}

						metricPair = parseMetrics(requiredMetric);

						startTime = System.currentTimeMillis();
					}
				}

				boolean isSent = false;
				if (Math.random() < samplingProb) {
					isSent = true;
				}
				String fromServer;
				int toCollect = -1;

				while ((fromServer = in.readLine()) != null && !colt.isInterrupted()) {
					if ( !fromServer.isEmpty() ) {
						String[] values = fromServer.split("\\s+");

						if (values[1].contains("CPU")) {
							toCollect  = CPU;
						}
						else if (values[1].contains("DISK")) {
							toCollect = DISK;
						}
						else if (values[1].contains("MEMORY")) {
							toCollect = MEMORY;
						}
						else if (values[1].contains("NETWORK")) {
							toCollect = NETWORK;
						}
						else if (values[1].contains("PROCESS")) {
							toCollect = PROCESS;
						}


						if (!values[0].contains("#")) {
							Map<String,Integer> metrics;
							switch (toCollect) {
							case CPU:
								metrics = metricPair.get("CPU");
								if (metrics == null)
									continue;

								for (Map.Entry<String, Integer> entry : metrics.entrySet()) {
									String key = entry.getKey();
									Integer value = entry.getValue();

									if (isSent) {
										if (key.toLowerCase().equals("cpuutilizationcollectl")) {
											try {
												logger.info("Sending datum: {} {} {}",String.valueOf(100-Integer.valueOf(values[8])), key, monitoredTarget);
												dcAgent.send(new VM(Config.getInstance().getVmType(), 
														Config.getInstance().getVmId()), key, 100-Integer.valueOf(values[8])); 
											} catch (Exception e) {
												e.printStackTrace();
											}
										}
										else {
											try {
												logger.info("Sending datum: {} {} {}",String.valueOf(100-Integer.valueOf(values[8])), key, monitoredTarget);
												dcAgent.send(new VM(Config.getInstance().getVmType(), 
														Config.getInstance().getVmId()), key, values[value]); 
											} catch (Exception e) {
												e.printStackTrace();
											}
										}
									}
								}
								break;
							case DISK:
								metrics = metricPair.get("DISK");

								if (metrics == null)
									continue;

								for (Map.Entry<String, Integer> entry : metrics.entrySet()) {
									String key = entry.getKey();
									Integer value = entry.getValue();

									if (isSent) {
										try {
											logger.info("Sending datum: {} {} {}",String.valueOf(100-Integer.valueOf(values[8])), key, monitoredTarget);
											dcAgent.send(new VM(Config.getInstance().getVmType(), 
													Config.getInstance().getVmId()), key, values[value]); 
										} catch (Exception e) {
											e.printStackTrace();
										}
									}
								}
								break;
							case MEMORY:
								metrics = metricPair.get("MEMORY");

								if (metrics == null)
									continue;

								for (Map.Entry<String, Integer> entry : metrics.entrySet()) {
									String key = entry.getKey();
									Integer value = entry.getValue();

									if (isSent) {
										try {
											logger.info("Sending datum: {} {} {}",String.valueOf(100-Integer.valueOf(values[8])), key, monitoredTarget);
											dcAgent.send(new VM(Config.getInstance().getVmType(), 
													Config.getInstance().getVmId()), key, values[value]); 
										} catch (Exception e) {
											e.printStackTrace();
										}
									}
								}
								break;
							case NETWORK:
								metrics = metricPair.get("NETWORK");

								if (metrics == null)
									continue;

								for (Map.Entry<String, Integer> entry : metrics.entrySet()) {
									String key = entry.getKey();
									Integer value = entry.getValue();

									if (isSent) {
										try {
											logger.info("Sending datum: {} {} {}",String.valueOf(100-Integer.valueOf(values[8])), key, monitoredTarget);
											dcAgent.send(new VM(Config.getInstance().getVmType(), 
													Config.getInstance().getVmId()), key, values[value]); 
										} catch (Exception e) {
											e.printStackTrace();
										}
									}
								}
								break;
							}
						}
					}
				}
			}
		}
		catch (UnknownHostException e) {
			System.err.println("Don't know about host: " + serverIP);
			System.exit(1);
		} 
		catch (IOException e) {
			System.err.println("Couldn't get I/O for the connection to: " + serverIP);
			System.exit(1);
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (ConfigurationException e) {
			e.printStackTrace();
		} 
	}

	/**
	 * Parse the input monitored metrics to the corresponding metrics in collectl
	 * @param metrics
	 * @return Map structure containing the category of metrics as the key and the metric name and index as value.
	 */
	private Map<String,Map<String,Integer>> parseMetrics(ArrayList<String> metrics) {
		Map<String,Map<String,Integer>> pair = new HashMap<String,Map<String,Integer>>();

		Map<String,Integer> CPUMetrics = new HashMap<String,Integer>();
		CPUMetrics.put("cpuutilizationsyscollectl", 3);
		CPUMetrics.put("cpuutilizationusercollectl", 1);
		CPUMetrics.put("cpuutilizationwaitcollectl", 4);
		CPUMetrics.put("cpuutilizationcollectl", 8);
		CPUMetrics.put("contextswitchcollectl", 11);
		CPUMetrics.put("cpuutilstolencollectl", 7);
		CPUMetrics.put("interruptscollectl", 10);
		CPUMetrics.put("maxProcscollectl", 14);
		CPUMetrics.put("maxProcsqueuecollectl", 13);

		Map<String,Integer> DiskMetrics = new HashMap<String,Integer>();
		DiskMetrics.put("diskreadbytescollectl", 1);
		DiskMetrics.put("diskreadopscollectl", 3);
		DiskMetrics.put("diskwritebytescollectl", 5);
		DiskMetrics.put("diskwriteopscollectl", 7);
		DiskMetrics.put("diskqlencollectl", 10);
		DiskMetrics.put("diskwaitcollectl", 11);

		Map<String,Integer> MemoryMetrics = new HashMap<String,Integer>();
		MemoryMetrics.put("memusedcollectl", 2);
		MemoryMetrics.put("memorybuffcollectl", 4);
		MemoryMetrics.put("memorycachecollectl", 5);
		MemoryMetrics.put("memorycommitcollectl", 8);
		MemoryMetrics.put("memswapspaceusedcollectl", 11);
		MemoryMetrics.put("memoryswapincollectl", 12);
		MemoryMetrics.put("memoryswapoutcollectl", 13);

		Map<String,Integer> NetworkMetrics = new HashMap<String,Integer>();
		NetworkMetrics.put("networkinbytescollectl", 1);
		NetworkMetrics.put("networkoutbytescollectl", 7);


		for (int i=0; i < metrics.size(); i++) {
			if (CPUMetrics.get(metrics.get(i).toLowerCase()) != null) {
				Map<String,Integer> values;
				if (pair.get("CPU") == null) {
					values = new HashMap<String,Integer>();
				}
				else {
					values = pair.get("CPU");
				}

				values.put(metrics.get(i), CPUMetrics.get(metrics.get(i).toLowerCase()));
				pair.put("CPU", values);
				continue;
			}
			if (DiskMetrics.get(metrics.get(i).toLowerCase()) != null) {
				Map<String,Integer> values;
				if (pair.get("DISK") == null) {
					values = new HashMap<String,Integer>();
				}
				else {
					values = pair.get("DISK");
				}

				values.put(metrics.get(i), DiskMetrics.get(metrics.get(i).toLowerCase()));
				pair.put("DISK", values);
				continue;
			}
			if (MemoryMetrics.get(metrics.get(i).toLowerCase()) != null) {
				Map<String,Integer> values;
				if (pair.get("MEMORY") == null) {
					values = new HashMap<String,Integer>();
				}
				else {
					values = pair.get("MEMORY");
				}

				values.put(metrics.get(i), MemoryMetrics.get(metrics.get(i).toLowerCase()));
				pair.put("MEMORY", values);
				continue;
			}
			if (NetworkMetrics.get(metrics.get(i).toLowerCase()) != null) {
				Map<String,Integer> values;
				if (pair.get("NETWORK") == null) {
					values = new HashMap<String,Integer>();
				}
				else {
					values = pair.get("NETWORK");
				}

				values.put(metrics.get(i), NetworkMetrics.get(metrics.get(i).toLowerCase()));
				pair.put("NETWORK", values);
				continue;
			}
		}

		return pair;
	}

	@Override
	public void start() {
		ArrayList<String> requiredMetric = new ArrayList<String>();

		List<Integer> periodList = new ArrayList<Integer>();

		if (mode.equals("tower4clouds")) {

			for (String metric : getProvidedMetrics()) {
				try {
					VM resource = new VM(Config.getInstance().getVmType(), 
							Config.getInstance().getVmId());
					if (dcAgent.shouldMonitor(resource, metric)) {
						Map<String, String> parameters = dcAgent.getParameters(resource, metric);

						requiredMetric.add(metric);

						serverIP = parameters.get("serverIP");
						periodList.add(Integer.valueOf(parameters.get("samplingTime"))*1000);
						samplingProb = Double.valueOf(parameters.get("samplingProbability"));
					}
				} catch (NumberFormatException | ConfigurationException e) {
					e.printStackTrace();
				}
			}

			period = Collections.min(periodList);
			metricPair = parseMetrics(requiredMetric);
		}
		else{
			try {
				String folder = new File(".").getCanonicalPath();
				File file = new File(folder+"/config/configuration_Collectl.xml");

				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder;
				dBuilder = dbFactory.newDocumentBuilder();
				Document doc = dBuilder.parse(file);

				doc.getDocumentElement().normalize();

				NodeList nList = doc.getElementsByTagName("metricName");

				for (int temp = 0; temp < nList.getLength(); temp++) {

					Node nNode = nList.item(temp);

					requiredMetric.add(nNode.getTextContent());
				}

				NodeList nList_period = doc.getElementsByTagName("collectl-metric");

				for (int i = 0; i < nList_period.getLength(); i++) {

					Node nNode = nList_period.item(i);

					if (nNode.getNodeType() == Node.ELEMENT_NODE) {

						Element eElement = (Element) nNode;

						period = Integer.valueOf(eElement.getElementsByTagName("monitorPeriod").item(0).getTextContent());
						monitoredTarget = eElement.getElementsByTagName("monitoredTarget").item(0).getTextContent();
					}
				}

				samplingProb = 1;
				metricPair = parseMetrics(requiredMetric);

			} catch (ParserConfigurationException e1) {
				e1.printStackTrace();
			} catch (SAXException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}


		//start collectl if collectl has not started yet
		boolean isRunning = false;

		try {
			String line;
			Process p = Runtime.getRuntime().exec("ps -e");
			BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
			while ((line = input.readLine()) != null) {
				String[] strings = line.split("\\s+");
				if (strings[strings.length-1].equals("collectl") && !strings[2].equals("?")) { //root also running collectl in College
					System.out.println("collectl is running already");
					isRunning = true;
					break;
				}
			}
			input.close();
			p.destroy();

			if (!isRunning) {
				CollectlRunner colSerRun = new CollectlRunner();
				Thread colSert = new Thread(colSerRun, "collectl");
				colSert.start();
			}

			collectlStartTime = System.currentTimeMillis();
		} catch (Exception err) {
			err.printStackTrace();
		}

		colt = new Thread(this, "col-mon");
	}

	private Set<String> getProvidedMetrics() {
		Set<String> metrics = new HashSet<String>();
		metrics.add("CpuUtilizationCollectl");
		metrics.add("ContextSwitchCollectl");
		metrics.add("CpuUtilStolenCollectl");
		metrics.add("InterruptsCollectl");
		metrics.add("MaxProcsCollectl");
		metrics.add("MaxProcsQueueCollectl");
		metrics.add("MemUsedCollectl");
		metrics.add("MemSwapSpaceUsedCollectl");
		metrics.add("NetworkInBytesCollectl");
		metrics.add("NetworkOutBytesCollectl");
		return metrics;
	}

	@Override
	public void init() {
		long currentTime = System.currentTimeMillis();
		try {
			//wait for collectl to fully start
			Thread.sleep( Math.max(5000 - currentTime + collectlStartTime, 0));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			colt.interrupt();
		}
		colt.start();
		System.out.println("Collectl monitor running!");
	}

	@Override
	public void stop() {
		while (!colt.isInterrupted()) {
			colt.interrupt();
		}
		System.out.println("Collectl monitor stopped!");
	}

	/**
	 * The class for starting collectl if collectl is not running at the beginning of monitoring
	 */
	private class CollectlRunner implements Runnable{
		public void run(){
			try {
				float freq = ((float) period);
				//Runtime.getRuntime().exec("collectl -scnDmZ --verbose -A server -i" + freq + ":5");
				Runtime.getRuntime().exec("collectl -scnDm --verbose -A server -i" + freq);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void setDCAgent(DCAgent dcAgent) {
		this.dcAgent = dcAgent;
	}

}
