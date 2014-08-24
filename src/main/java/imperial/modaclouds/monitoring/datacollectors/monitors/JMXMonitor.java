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
import imperial.modaclouds.monitoring.datacollectors.basic.Metric;
import it.polimi.modaclouds.monitoring.dcfactory.DCMetaData;

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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

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
	 * The unique monitored target.
	 */
	private String monitoredTarget;

	/**
	 * The metric list.
	 */
	private List<Metric> metricList;

	private DataCollectorAgent dcAgent; 


	/**
	 * Constructor of the class.
	 *
	 * @param connectorAddress JMX connector address
	 * @param measure Monitoring measure
	 * @throws MalformedURLException 
	 * @throws FileNotFoundException 
	 */
	public JMXMonitor( String resourceId, String mode )  {
		//this.monitoredResourceID = "FrontendVM";
		//this.monitoredTarget = monitoredResourceID;
		super(resourceId, mode);
		
		monitoredTarget = resourceId;
		monitorName = "jmx";

		dcAgent = DataCollectorAgent.getInstance();

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

				Collection<DCMetaData> dcConfig = dcAgent.getDataCollectors(resourceId);

				for (DCMetaData dc: dcConfig) {

						if (ModacloudsMonitor.findCollector(dc.getMonitoredMetric()).equals("jmx")) {
							Metric temp = new Metric();

							temp.setMetricName(dc.getMonitoredMetric());

							Map<String,String> parameters = dc.getParameters();

							period.add(Integer.valueOf(parameters.get("samplingTime"))*1000);
							nextPauseTime.add(Integer.valueOf(parameters.get("samplingTime"))*1000);
							temp.setSamplingProb(Double.valueOf(parameters.get("samplingProbability")));

							metricList.add(temp);
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
					dcAgent.sendSyncMonitoringDatum(String.valueOf(value), metricList.get(index).getMetricName(), monitoredTarget);
				}
			} catch (Exception e) {
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
