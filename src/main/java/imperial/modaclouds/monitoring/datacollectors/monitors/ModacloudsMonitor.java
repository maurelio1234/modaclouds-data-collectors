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
import imperial.modaclouds.monitoring.datacollectors.demo.ofbiz.OFBizLogFileMonitor;
import it.polimi.tower4clouds.data_collector_library.DCAgent;
import it.polimi.tower4clouds.manager.api.ManagerAPI;
import it.polimi.tower4clouds.model.data_collectors.DCDescriptor;
import it.polimi.tower4clouds.model.ontology.CloudProvider;
import it.polimi.tower4clouds.model.ontology.ExternalComponent;
import it.polimi.tower4clouds.model.ontology.InternalComponent;
import it.polimi.tower4clouds.model.ontology.Location;
import it.polimi.tower4clouds.model.ontology.Method;
import it.polimi.tower4clouds.model.ontology.PaaSService;
import it.polimi.tower4clouds.model.ontology.Resource;
import it.polimi.tower4clouds.model.ontology.VM;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * Main class.
 */
public class ModacloudsMonitor implements Observer {
	private static final Logger logger = LoggerFactory
			.getLogger(ModacloudsMonitor.class);

	private Set<String> oldCollectors = new HashSet<String>();

	/**
	 * Index of the monitors.
	 */
	private Map<String, Integer> dcIndex;

	/**
	 * The list of the monitors.
	 */
	private List<AbstractMonitor> monitors;

	/**
	 * The name of the running monitors.
	 */
	private List<String> runningMonitors;

	/**
	 * The static class of ModacloudsMonitor.
	 */
	private static ModacloudsMonitor modacloudsMonitor;

	/**
	 * DDa connector.
	 */
	// private static DDAConnector ddaConnector;

	/**
	 * URI of the DC.
	 */
	// private static String ownURI;

	/**
	 * mode of the DC.
	 */
	private static String mode;

	/**
	 * Knowledge base connector.
	 */

	/**
	 * The mapp
	 */
	private Map<String, String> metricCollectorMapping;

	/**
	 * Object store connector.
	 */
	// private static ObjectStoreConnector objectStoreConnector;

	/**
	 * Initial setup of the monitor.
	 */
	public void initSetup() throws ParserConfigurationException, SAXException,
			IOException {
		dcIndex = new HashMap<String, Integer>();
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
		dcIndex.put("vmavailability", 12);
		dcIndex.put("detailedCost", 13);
		dcIndex.put("haproxy", 14);
		dcIndex.put("appavailability", 15);

		monitors = new ArrayList<AbstractMonitor>();
		runningMonitors = new ArrayList<String>();
	}

	/**
	 * run monitoring given the collector index.
	 * 
	 * @param index
	 *            : monitoring collector index
	 * @param dcAgent
	 * @throws ConfigurationException
	 * 
	 */
	public void runMonitoring(String[] index, DCAgent dcAgent)
			throws ConfigurationException {

		int[] intArray = new int[index.length];
		for (int i = 0; i < index.length; i++) {
			if (dcIndex.get(index[i]) == null) {
				logger.warn("Cannot recognise collector: {}", index[i]);
			} else {
				intArray[i] = dcIndex.get(index[i]);
			}
		}

		for (int i = 0; i < index.length; i++) {
			if (runningMonitors.contains(index[i])) {
				logger.warn("Collector {} has already started!", index[i]);
				continue;
			}

			runningMonitors.add(index[i]);
			AbstractMonitor newMonitor = null;
			switch (intArray[i]) {
			case 1:
				newMonitor = new JMXMonitor(Config.getInstance()
						.getInternalComponentId(), mode);
				monitors.add(newMonitor);
				break;
			case 2:
				newMonitor = new CollectlMonitor(
						Config.getInstance().getVmId(), mode);
				monitors.add(newMonitor);
				break;
			case 3:
				newMonitor = new SigarMonitor(Config.getInstance().getVmId(),
						mode);
				monitors.add(newMonitor);
				break;
			case 4:
				newMonitor = new OFBizLogFileMonitor(Config.getInstance()
						.getInternalComponentId(), mode);
				monitors.add(newMonitor);
				break;
			case 5:
				newMonitor = new LogFileMonitor(Config.getInstance()
						.getInternalComponentId(), mode);
				monitors.add(newMonitor);
				break;
			case 6:
				newMonitor = new MySQLMonitor(Config.getInstance()
						.getInternalComponentId(), mode);
				monitors.add(newMonitor);
				break;
			case 7:
				newMonitor = new CloudWatchMonitor(Config.getInstance()
						.getVmId(), mode);
				monitors.add(newMonitor);
				break;
			case 8:
				newMonitor = new FlexiMonitor(Config.getInstance().getVmId(),
						mode);
				monitors.add(newMonitor);
				break;
			case 9:
				newMonitor = new EC2SpotPriceMonitor(Config.getInstance()
						.getVmId(), mode);
				monitors.add(newMonitor);
				break;
			case 10:
				newMonitor = new StartupTimeMonitor(Config.getInstance()
						.getVmId(), mode);
				monitors.add(newMonitor);
				break;
			case 11:
				newMonitor = new CostMonitor(Config.getInstance().getVmId(),
						mode);
				monitors.add(newMonitor);
				break;
			case 12:
				newMonitor = new VMAvailabilityMonitor(Config.getInstance()
						.getVmId(), mode);
				monitors.add(newMonitor);
				break;
			case 13:
				newMonitor = new DetailedCostMonitor(Config.getInstance()
						.getVmId(), mode);
				monitors.add(newMonitor);
				break;
			case 14:
				newMonitor = new HaproxyLogMonitor(Config.getInstance()
						.getVmId(), mode);
				monitors.add(newMonitor);
				break;
			case 15:
				newMonitor = new AppAvailabilityMonitor(Config.getInstance()
						.getInternalComponentId(), mode);
				monitors.add(newMonitor);
				break;
			}
			newMonitor.setDCAgent(dcAgent);
			newMonitor.start();
			newMonitor.init();
		}
	}

	// /**
	// * Reconfigurate the monitors for the sampling probability.
	// * @param monitor name
	// * @param sampling probability
	// */
	// public static void reconfigMonitor(String monitor, double samplingProb) {
	// if (!runningMonitors.contains(monitor)) {
	// System.out.println("WARNING: collector " + monitor+
	// " has not started yet");
	// }
	// else {
	// for (int j = 0; j < monitors.size(); j++) {
	// if (monitor.equals(monitors.get(j).getMonitorName())) {
	// monitors.get(j).reconfig(samplingProb);
	// break;
	// }
	// }
	// }
	// }

	/**
	 * stop particular monitors.
	 * 
	 * @param index
	 *            The index of the monitors
	 */
	public void stopMonitoring(String[] index) {

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
	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			logger.warn("Default mode is using the tower4clouds.");
			mode = "tower4clouds";
		} else {
			mode = args[0];
		}

		modacloudsMonitor = new ModacloudsMonitor();

		modacloudsMonitor.initSetup();

		if (mode.equals("tower4clouds")) {
			Config config = Config.getInstance();
			DCAgent dcAgent = new DCAgent(new ManagerAPI(config.getMmIP(),
					config.getMmPort()));
			DCDescriptor dcDescriptor = new DCDescriptor();
			if (config.getInternalComponentId() != null) {
				dcDescriptor.addMonitoredResource(getApplicationMetrics(),
						buildInternalComponent(config));
				if (config.getMethodName() != null) {
					dcDescriptor.addMonitoredResource(getMethodMetrics(), buildMethod(config));
				}
				dcDescriptor.addResource(buildInternalComponent(config));
			}
			if (config.getVmId() != null) {
				dcDescriptor.addMonitoredResource(getInfrastructureMetrics(),
						buildExternalComponent(config));
				dcDescriptor.addResource(buildExternalComponent(config));
			}
			
			dcDescriptor.addResources(buildRelatedResources(config));
			dcDescriptor.setConfigSyncPeriod(config.getDcSyncPeriod());
			dcDescriptor.setKeepAlive(config.getResourcesKeepAlivePeriod());
			
			dcAgent.setDCDescriptor(dcDescriptor);
			dcAgent.addObserver(modacloudsMonitor);
			dcAgent.start();
		}

		if (mode.equals("file")) {
			String[] strArray = args[2].split(",");
			modacloudsMonitor.runMonitoring(strArray, null);
		}

	}

	private static Set<Resource> buildRelatedResources(Config config) {
		Set<Resource> relatedResources = new HashSet<Resource>();
		if (config.getCloudProviderId() != null) {
			relatedResources.add(new CloudProvider(config
					.getCloudProviderType(), config.getCloudProviderId()));
		}
		if (config.getLocationId() != null) {
			relatedResources.add(new Location(config.getLocationtype(), config
					.getLocationId()));
		}
		return relatedResources;
	}

	private static Resource buildExternalComponent(Config config)
			throws ConfigurationException {
		ExternalComponent externalComponent;
		if (config.getVmId() != null) {
			externalComponent = new VM();
			externalComponent.setId(config.getVmId());
			externalComponent.setType(config.getVmType());
		} else if (config.getPaasServiceId() != null) {
			externalComponent = new PaaSService();
			externalComponent.setId(config.getPaasServiceId());
			externalComponent.setType(config.getPaasServiceType());
		} else {
			throw new ConfigurationException(
					"Neither VM nor PaaS service were specified");
		}
		if (config.getCloudProviderId() != null)
			externalComponent.setCloudProvider(config.getCloudProviderId());
		if (config.getLocationId() != null)
			externalComponent.setLocation(config.getLocationId());
		return externalComponent;
	}

	private static Resource buildInternalComponent(Config config) {
		InternalComponent internalComponent = new InternalComponent(
				config.getInternalComponentType(),
				config.getInternalComponentId());
		if (config.getVmId() != null)
			internalComponent.addRequiredComponent(config.getVmId());
		
		if (config.getMethodName() != null) {
			internalComponent.addProvidedMethod(buildMethod(config).getId());
		}
		return internalComponent;
	}

	private static Method buildMethod(Config config) {
		return new Method(config.getMethodName(), config.getMethodName());
	}
	
	private static Set<String> getMethodMetrics() {
		// TODO return a set with all the method level metrics provided
		// by this dc (case sensitive)
		Set<String> metrics = new HashSet<String>();
//		metrics.add("EffectiveResponseTime");
		metrics.add("ResponseTime");
//		metrics.add("Throughput");

		return metrics;
	}

	private static Set<String> getInfrastructureMetrics() {
		// TODO return a set with all the infrastructure level metrics provided
		// by this dc (case sensitive)
		Set<String> metrics = new HashSet<String>();
		metrics.add("CPUUtilization");
		metrics.add("CPUStolen");
		metrics.add("MemUsed");
		metrics.add("DiskreadopsCloudWatch");
		metrics.add("CpuutilizationCloudWatch");
		metrics.add("DiskReadOpsCloudWatch");
		metrics.add("DiskWriteOpsCloudWatch");
		metrics.add("DiskReadBytesCloudWatch");
		metrics.add("DiskWriteBytesCloudWatch");
		metrics.add("NetworkInCloudWatch");
		metrics.add("NetworkOutCloudWatch");
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
		metrics.add("GeneralCost");
		metrics.add("EC2-SpotPrice");
		metrics.add("HaproxyLog");
		metrics.add("Flexi");
		metrics.add("VmAvailable");
		metrics.add("StartupTime");
		metrics.add("DetailedCost");

		return metrics;
	}

	private static Set<String> getApplicationMetrics() {
		// TODO return a set with all the application level metrics provided by
		// this dc (case sensitive)
		Set<String> metrics = new HashSet<String>();
		metrics.add("Threads_running");
		metrics.add("Threads_cached");
		metrics.add("Threads_connected");
		metrics.add("Threads_created");
		metrics.add("Queries");
		metrics.add("Bytes_received");
		metrics.add("Bytes_sent");
		metrics.add("Connections");
		metrics.add("Threads_connected");
		metrics.add("Aborted_connects");
		metrics.add("Aborted_clients");
		metrics.add("Table_locks_immediate");
		metrics.add("Table_locks_waited");
		metrics.add("Com_insert");
		metrics.add("Com_delete");
		metrics.add("Com_update");
		metrics.add("Com_select");
		metrics.add("Qcache_hits");
		metrics.add("PeakThreadCountJMX");
		metrics.add("HeapMemoryUsedJMX");
		metrics.add("UptimeJMX");
		metrics.add("ResponseInfo");
		metrics.add("AppAvailable");
		metrics.add("LogFile");

		return metrics;
	}

	@Override
	public void update(Observable o, Object arg) {
		DCAgent dcAgent = (DCAgent) o;
		Set<String> metrics = dcAgent.getRequiredMetrics();
		Set<String> newCollectors = new HashSet<String>();
		for (String metric : metrics) {
			String collector = findCollector(metric);
			if (collector == null) {
				logger.error("Required metric {} has no associated collector",
						metric);
				continue;
			}
			if (!newCollectors.contains(collector)) {
				newCollectors.add(collector);
			}
		}

		Set<String> toRun = new HashSet<String>();
		Set<String> toStop = new HashSet<String>();

		for (String newCollector : newCollectors) {
			if (!oldCollectors.contains(newCollector)) {
				toRun.add(newCollector);
			}
		}

		for (String oldCollector : oldCollectors) {
			if (!newCollectors.contains(oldCollector)) {
				toStop.add(oldCollector);
			}
		}

		try {
			modacloudsMonitor.runMonitoring(
					toRun.toArray(new String[toRun.size()]), dcAgent);
		} catch (ConfigurationException e) {
			throw new RuntimeException(e);
		}
		modacloudsMonitor.stopMonitoring(toStop.toArray(new String[toStop
				.size()]));

		oldCollectors = newCollectors;
	}

	public String findCollector(String metricName) {
		if (metricCollectorMapping == null) {
			metricCollectorMapping = new HashMap<String, String>();

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
			metricCollectorMapping
					.put("cpuutilizationcloudwatch", "cloudwatch");
			metricCollectorMapping.put("diskreadopscloudwatch", "cloudwatch");
			metricCollectorMapping.put("diskwriteopscloudwatch", "cloudwatch");
			metricCollectorMapping.put("diskreadbytescloudwatch", "cloudwatch");
			metricCollectorMapping
					.put("diskwritebytescloudwatch", "cloudwatch");
			metricCollectorMapping.put("networkincloudwatch", "cloudwatch");
			metricCollectorMapping.put("networkoutcloudwatch", "cloudwatch");
			metricCollectorMapping.put("peakthreadcountjmx", "jmx");
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
			metricCollectorMapping.put("vmavailable", "vmavailability");
			metricCollectorMapping.put("appavailable", "appavailability");
			metricCollectorMapping.put("flexi", "flexi");
			metricCollectorMapping.put("haproxylog", "haproxy");

			metricCollectorMapping.put("responsetime", "logFile");			
		}

		String collector;
		collector = metricCollectorMapping.get(metricName.toLowerCase());

		if (collector == null) {
			logger.error("Metric {} not found", metricName);
			return "";
		} else {
			return collector;
		}
	}

}
