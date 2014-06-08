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

import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import imperial.modaclouds.monitoring.datacollectors.basic.Metric;
import it.polimi.modaclouds.monitoring.ddaapi.DDAConnector;
import it.polimi.modaclouds.monitoring.ddaapi.ValidationErrorException;
import it.polimi.modaclouds.monitoring.kb.api.KBConnector;
import it.polimi.modaclouds.monitoring.objectstoreapi.ObjectStoreConnector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.DataCollector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.KBEntity;
import it.polimi.modaclouds.qos_models.monitoring_ontology.Parameter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import polimi.deib.csparql_rest_api.exception.ServerErrorException;
import polimi.deib.csparql_rest_api.exception.StreamErrorException;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;


/**
 * The monitoring collector for JMX.
 */
public class JMXMonitor extends AbstractMonitor {

	/**
	 * JMX remote connector address.
	 */
	private static final String CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";

	/**
	 * Virtual machine variable.
	 */
	private static VirtualMachine vm = null;

	/**
	 * JMX connector.
	 */
	private static JMXConnector connector;

	/**
	 * Remote MBean server connector.
	 */
	private static MBeanServerConnection remote;

	/**
	 * Operating system MXBean.
	 */
	private static OperatingSystemMXBean osMBean;

	/**
	 * Memory MBean.
	 */
	private static MemoryMXBean memoryMBean;

	/**
	 * Memory pool MBean.
	 */
	private static List< MemoryPoolMXBean > memoryPoolMBeans;

	/**
	 * Thread MXBean.
	 */
	private static ThreadMXBean threadMBean;

	/**
	 * Remote run time MXBean.
	 */
	private static RuntimeMXBean remoteRuntime;

	/**
	 * Available processors.
	 */
	private int availableProcessors;

	/**
	 * Lasting system time.
	 */
	private long lastSystemTime;

	/**
	 * Lasting processing cpu time.
	 */
	private long lastProcessCpuTime;

	/**
	 * JMX connector address.
	 */
	private static String connectorAddress;

	/**
	 * Check if this monitor has been registered.
	 */
	boolean isInit = false;

	/**
	 * JMX monitor thread.
	 */
	private Thread jmxt;

	/**
	 * DDa connector.
	 */
	private DDAConnector ddaConnector;

	/**
	 * Knowledge base connector.
	 */
	private KBConnector kbConnector;

	/**
	 * Object store connector.
	 */
	private ObjectStoreConnector objectStoreConnector;

	/**
	 * The unique monitored target.
	 */
	private String monitoredTarget;

	/**
	 * The metric list.
	 */
	private List<Metric> metricList; 

	private String ownURI;

	/**
	 * Constructor of the class.
	 *
	 * @param connectorAddress JMX connector address
	 * @param measure Monitoring measure
	 * @throws MalformedURLException 
	 * @throws FileNotFoundException 
	 */
	public JMXMonitor( String ownURI ) throws MalformedURLException, FileNotFoundException {
		//this.monitoredResourceID = "FrontendVM";
		//this.monitoredTarget = monitoredResourceID;
		monitorName = "jmx";

		this.ownURI = ownURI;

		ddaConnector = DDAConnector.getInstance();
		kbConnector = KBConnector.getInstance();

		//ddaConnector.setDdaURL(objectStoreConnector.getDDAUrl());

		try {
			connectToOFBiz();
		} catch (Exception e2) {
			e2.printStackTrace();
		}

		try {
			JMXMonitor.connectorAddress = vm.getAgentProperties().getProperty(CONNECTOR_ADDRESS);

		} catch (IOException e1) {
			e1.printStackTrace();
		}

		try {
			loadAgent();	
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Connect to Ofbiz.
	 */
	public static void connectToOFBiz() throws Exception
	{
		String ofbizpid = findOFBizInstance();
		vm = VirtualMachine.attach( ofbizpid );
	}

	/**
	 * Check if Ofbiz instance exists.
	 * 
	 * @return if ofbiz exists, return ofbiz pid.
	 */
	private static String findOFBizInstance() throws Exception
	{
		String pid = null;
		int instances = 0;

		for( VirtualMachineDescriptor vd : VirtualMachine.list() )
		{
			//			if( vd.displayName().matches( ".*ofbiz.jar" ) )
			//			{
			//				ofbizpid = vd.id();
			//				instances++;
			//			}
			if( vd.displayName().matches( ".*.jar" ) ) {
				if (!vd.displayName().contains("dcs")) {
					pid = vd.id();
					instances++;
				}
			}
		}

		if( instances > 1 ) throw new Exception(
				"multiple instances detected" );
		else if( instances == 0 )
			throw new Exception( "no instance detected" );

		return pid;
	}

	/**
	 * Load JVM agent.
	 */
	public static void loadAgent() throws Exception
	{
		// get system properties in target VM
		Properties props = vm.getSystemProperties();

		// construct path to management agent
		String home = props.getProperty( "java.home" );
		String agent = home + File.separator + "lib" + File.separator
				+ "management-agent.jar";

		// load agent into target VM
		vm.loadAgent( agent );// , "com.sun.management.jmxremote.port=5000");
	}

	/**
	 * Set JMX conenctor.
	 */
	private static void setConnector() throws Exception
	{
		assert connectorAddress != null;

		if (connectorAddress == null) {
			connectorAddress = vm.getAgentProperties().getProperty(CONNECTOR_ADDRESS);
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				//e.printStackTrace();
			}
		}

		JMXServiceURL url = new JMXServiceURL( connectorAddress );
		connector = JMXConnectorFactory.connect( url );
	}

	/**
	 * Set JMX beans.
	 */
	private static void setMBeans() throws Exception
	{
		remote = connector.getMBeanServerConnection();

	}

	@Override
	public void run() {

		long startTime = 0;

		List<Integer> period = null;

		List<Integer> nextPauseTime = null;

		while (!jmxt.isInterrupted()) {

			if (System.currentTimeMillis() - startTime > 10000) {

				period = new ArrayList<Integer>();
				nextPauseTime = new ArrayList<Integer>();

				metricList = new ArrayList<Metric>();

				Set<KBEntity> dcConfig = kbConnector.getAll(DataCollector.class);

				for (KBEntity kbEntity: dcConfig) {
					DataCollector dc = (DataCollector) kbEntity;

					if (dc.getTargetResources().iterator().next().getUri().equals(ownURI)) {

						if (ModacloudsMonitor.findCollector(dc.getCollectedMetric()).equals("jmx")) {
							Metric temp = new Metric();

							temp.setMetricName(dc.getCollectedMetric());

							monitoredTarget = dc.getTargetResources().iterator().next().getUri();

							Set<Parameter> parameters = dc.getParameters();

							for (Parameter par: parameters) {
								switch (par.getName()) {
								case "samplingTime":
									period.add(Integer.valueOf(par.getValue())*1000);
									nextPauseTime.add(Integer.valueOf(par.getValue())*1000);
									break;
								case "samplingProbability":
									temp.setSamplingProb(Double.valueOf(par.getValue()));
									break;
								}
							}

							metricList.add(temp);
						}
					}
				}

				startTime = System.currentTimeMillis();
			}

			int toSleep = Collections.min(nextPauseTime);
			int index = nextPauseTime.indexOf(toSleep);

			try {
				Thread.sleep(Math.max(toSleep, 0));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			long t0 = System.currentTimeMillis();

			double value = 0;

			switch (metricList.get(index).getMetricName().toLowerCase()) {
			case "peakthreadcountjmx":
				value = threadMBean.getPeakThreadCount();
				break;
			case "heapmemoryusedjmx":
				value = memoryMBean.getHeapMemoryUsage().getUsed();
				break;
			case "uptimejmx":
				value = remoteRuntime.getUptime();
				break;
			}


			boolean isSent = false;
			if (Math.random() < metricList.get(index).getSamplingProb()) {
				isSent = true;
			}

			try {
				if (isSent) {
					ddaConnector.sendSyncMonitoringDatum(String.valueOf(value), metricList.get(index).getMetricName(), monitoredTarget);
				}
			} catch (ServerErrorException e) {
				e.printStackTrace();
			} catch (StreamErrorException e) {
				e.printStackTrace();
			} catch (ValidationErrorException e) {
				e.printStackTrace();
			}

			long t1 = System.currentTimeMillis();

			for (int i = 0; i < nextPauseTime.size(); i++) {
				nextPauseTime.set(i, Math.max(0, Integer.valueOf(nextPauseTime.get(i)-toSleep-(int)(t1-t0))));
			}
			nextPauseTime.set(index,period.get(index));
		}


	}

	@Override
	public void start() {
		jmxt = new Thread(this, "jmx-mon");
	}

	@Override
	public void init() {

		try {
			setConnector();
			setMBeans();
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		try {
			osMBean = ManagementFactory.newPlatformMXBeanProxy( remote,
					ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME,
					OperatingSystemMXBean.class );

			memoryMBean = ManagementFactory.newPlatformMXBeanProxy( remote,
					ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean.class );

			memoryPoolMBeans = ManagementFactory.getMemoryPoolMXBeans();

			threadMBean = ManagementFactory.newPlatformMXBeanProxy( remote,
					ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean.class );

			remoteRuntime = ManagementFactory.getRuntimeMXBean();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		jmxt.start();
		System.out.println("JMX monitor running!");
	}

	@Override
	public void stop() {
		while (!jmxt.isInterrupted()){
			jmxt.interrupt();
		}
		try {
			connector.close();
			vm.detach();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("JMX monitor stopped!");
	}

}
