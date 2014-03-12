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

import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import it.polimi.modaclouds.monitoring.ddaapi.DDAConnector;
import it.polimi.modaclouds.monitoring.ddaapi.ValidationErrorException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import polimi.deib.csparql_rest_api.exception.ServerErrorException;
import polimi.deib.csparql_rest_api.exception.StreamErrorException;

import com.sun.management.OperatingSystemMXBean;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import com.thoughtworks.xstream.XStream;


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
	 * Define CPU monitor as number 1.
	 */
	private static final int CPU = 1;

	/**
	 * Define memory monitor as number 2.
	 */
	private static final int MEMORY = 2;

	/**
	 * Define memory pool monitor as number 3.
	 */
	private static final int MEMORYPOOL = 3;

	/**
	 * Define thread monitor as number 4.
	 */
	private static final int THREAD = 4;

	/**
	 * Define ofbiz object monitor as number 5.
	 */
	private static final int BIZOBJ = 5;

	/**
	 * Define run time monitor as number 6.
	 */
	private static final int RUNTIME = 6;

	/**
	 * JMX monitor thread.
	 */
	private Thread jmxt;

	/**
	 * The reflection class mapped from xml file.
	 */
	private ReflectionXML reflectionXML;
	
	/**
	 * DDa connector.
	 */
	private DDAConnector ddaConnector;
	
	/**
	 * The unique monitored resource ID.
	 */
	private String monitoredResourceID;


	/**
	 * Constructor of the class.
	 *
	 * @param connectorAddress JMX connector address
	 * @param measure Monitoring measure
	 * @throws MalformedURLException 
	 */
	public JMXMonitor(  ) throws MalformedURLException {
		this.monitoredResourceID = UUID.randomUUID().toString();
		ddaConnector = DDAConnector.getInstance();
		monitorName = "jmx";

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
				pid = vd.id();
				instances++;
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
	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		try {
			setConnector();
			setMBeans();
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		long processCpuTime = 0;
		long systemTime = 0;
		Long t0 = 0L;
		int toRun = 0;
		Map<Integer, Integer> resolution = new HashMap<Integer, Integer>();
		Map<Integer, Long> lastMonitorTime = new HashMap<Integer, Long>();

		//read xml file into the ReflectionXML object
		try {
			String filePath = System.getProperty("user.dir") + "/config/configuration_JMX.xml";
			File file = new File(filePath);

			XStream xstream = new XStream();
			xstream.autodetectAnnotations(true);
			xstream.processAnnotations(ReflectionXML.class);

			InputStream inputStream = new FileInputStream(file);

			reflectionXML = (ReflectionXML)xstream.fromXML(inputStream);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}		

		//create method
		Map<Integer, Map<String, ArrayList<Method>>> categoryList = new HashMap<Integer, Map<String, ArrayList<Method>>>();

		ArrayList<ReflectionCateogry> listOfCategory = reflectionXML.getListOfCategory();
		for (int i = 0; i < listOfCategory.size(); i++) {
			String category = listOfCategory.get(i).getCategoryName();
			Map<String, ArrayList<Method>> metrics = new HashMap<String, ArrayList<Method>>();

			ArrayList<ReflectionMetric> listOfMetrics = listOfCategory.get(i).getListOfMetrics();
			for (int j = 0; j < listOfMetrics.size() ; j++) {
				String metricName = listOfMetrics.get(j).getMetricName();
				ArrayList<Method> functions = new ArrayList<Method>();

				ArrayList<ReflectionFunction> listOfFunctions = listOfMetrics.get(j).getListOfFunctions();				
				for (int k = 0; k < listOfFunctions.size(); k++) {
					String className = listOfFunctions.get(k).getClassName();
					String methodName = listOfFunctions.get(k).getFunctionName();

					try {
						functions.add(Class.forName(className).getMethod(methodName));
					} catch (NoSuchMethodException e) {
						e.printStackTrace();
					} catch (SecurityException e) {
						e.printStackTrace();
					} catch (ClassNotFoundException e) {
						e.printStackTrace();
					}
				}
				metrics.put(metricName, functions);
			}
			switch (category) {
			case "CPU":
				categoryList.put(CPU, metrics);
				resolution.put(CPU, Integer.valueOf(listOfCategory.get(i).getResolution()));
				toRun = CPU;

				if (osMBean == null) {
					try {
						osMBean = ManagementFactory.newPlatformMXBeanProxy( remote,
								ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME,
								OperatingSystemMXBean.class );
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				break;
			case "Memory":
				categoryList.put(MEMORY, metrics);
				resolution.put(MEMORY, Integer.valueOf(listOfCategory.get(i).getResolution()));
				toRun = MEMORY;

				if (memoryMBean == null) {
					try {
						memoryMBean = ManagementFactory.newPlatformMXBeanProxy( remote,
								ManagementFactory.MEMORY_MXBEAN_NAME, MemoryMXBean.class );
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				break;
			case "MemoryPool":
				categoryList.put(MEMORYPOOL, metrics);
				resolution.put(MEMORYPOOL, Integer.valueOf(listOfCategory.get(i).getResolution()));
				toRun = MEMORYPOOL;

				if (memoryPoolMBeans == null) {
					memoryPoolMBeans = ManagementFactory.getMemoryPoolMXBeans();
				}
				break;
			case "Thread":
				categoryList.put(THREAD, metrics);
				resolution.put(THREAD, Integer.valueOf(listOfCategory.get(i).getResolution()));
				toRun = THREAD;

				if (threadMBean == null) {
					try {
						threadMBean = ManagementFactory.newPlatformMXBeanProxy( remote,
								ManagementFactory.THREAD_MXBEAN_NAME, ThreadMXBean.class );
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				break;
			case "BizObj":
				categoryList.put(BIZOBJ, metrics);
				resolution.put(BIZOBJ, Integer.valueOf(listOfCategory.get(i).getResolution()));
				toRun = BIZOBJ;
				break;
			case "RunTime":
				categoryList.put(RUNTIME, metrics);
				resolution.put(RUNTIME, Integer.valueOf(listOfCategory.get(i).getResolution()));
				toRun = RUNTIME;

				if (remoteRuntime == null) {
					remoteRuntime = ManagementFactory.getRuntimeMXBean();
				}
				break;
			}
		}

		boolean isInitial = true;
		//run
		while (!jmxt.isInterrupted()) {		
			boolean isSent = false;
			if (Math.random() < this.samplingProb) {
				isSent = true;
			}
			for (Map.Entry<Integer, Map<String, ArrayList<Method>>> entry : categoryList.entrySet()) {
				Integer key = entry.getKey();
				if (!isInitial && key != toRun) {
					continue;
				}
				//pre-process
				Object instance = null;
				switch (key) {
				case CPU:
					instance = osMBean;
					break;
				case MEMORY:
					instance = memoryMBean;
					break;
				case MEMORYPOOL:
					instance = memoryPoolMBeans;
					break;
				case THREAD:
					instance = threadMBean;
					break;
				case RUNTIME:
					instance = remoteRuntime;
					break;
				}

				//invoke
				Map<String, ArrayList<Method>> value = entry.getValue();

				t0 = System.currentTimeMillis(); // invoke time

				lastMonitorTime.put(key, t0);

				for (Map.Entry<String, ArrayList<Method>> sub_entry : value.entrySet()) {
					ArrayList<Method> sub_methods = sub_entry.getValue();

					Object metric_value = null;

					try{
						if (instance.getClass() == ArrayList.class) {
							metric_value = new HashMap<String, Object>();
							for(Object object : (ArrayList<Object>) instance){
								Object temp = null;
								for (int i = 1; i < sub_methods.size(); i++) {
									if (i == 1) {
										temp = sub_methods.get(i).invoke(object);
									}
									else {
										temp = sub_methods.get(i).invoke(temp);
									}
								}
								//memberid, should be the first method
								String listMemberID = (String) sub_methods.get(0).invoke(object);
								((HashMap<String, Object>) metric_value).put(listMemberID, temp);
							}
						}
						else {
							for (int i = 0; i < sub_methods.size(); i++) {
								if (i == 0) {
									metric_value = sub_methods.get(i).invoke(instance);
								}
								else {
									metric_value = sub_methods.get(i).invoke(metric_value);
								}		    			
							}
						}
					} catch (IllegalAccessException e) {
						e.printStackTrace();
					} catch (IllegalArgumentException e) {
						e.printStackTrace();
					} catch (InvocationTargetException e) {
						e.printStackTrace();
					}

					try {
						//process
						switch (key) {
						case CPU:
							availableProcessors = osMBean.getAvailableProcessors();
							processCpuTime = (Long) metric_value;
							systemTime = System.nanoTime();

							double cpuUsage = ( double ) ( processCpuTime - lastProcessCpuTime ) / ( systemTime - lastSystemTime );			

							if (isSent){
								ddaConnector.sendSyncMonitoringDatum(String.valueOf(cpuUsage / availableProcessors), sub_entry.getKey(), monitoredResourceID);
							}
							//sendMonitoringDatum(cpuUsage / availableProcessors, ResourceFactory.createResource(MC.getURI() + sub_entry.getKey()), monitoredResourceURL, monitoredResource);

							break;
						case MEMORY:
							if (isSent) {
								ddaConnector.sendSyncMonitoringDatum(String.valueOf(metric_value), sub_entry.getKey(), monitoredResourceID);
							}
							//sendMonitoringDatum((Double)metric_value, ResourceFactory.createResource(MC.getURI() + sub_entry.getKey()), monitoredResourceURL, monitoredResource);

							break;
						case MEMORYPOOL:
							if (metric_value.getClass() == HashMap.class) {
								for (Map.Entry<String, Object> elementEntry : ((HashMap<String, Object>) metric_value).entrySet()) {
									if (isSent){
										ddaConnector.sendSyncMonitoringDatum(String.valueOf(elementEntry.getValue()), sub_entry.getKey() + elementEntry.getKey(), monitoredResourceID);
									}
										//sendMonitoringDatum((Double)(elementEntry.getValue()), ResourceFactory.createResource(MC.getURI() + sub_entry.getKey() + elementEntry.getKey()), monitoredResourceURL, monitoredResource);
								}
							}
							break;
						case THREAD:
							if (isSent) {
								ddaConnector.sendSyncMonitoringDatum(String.valueOf(metric_value), sub_entry.getKey(), monitoredResourceID);
							}
							//sendMonitoringDatum((Double)metric_value, ResourceFactory.createResource(MC.getURI() + sub_entry.getKey()), monitoredResourceURL, monitoredResource);

							break;
						case BIZOBJ:
							if (isSent) {
								ddaConnector.sendSyncMonitoringDatum(String.valueOf(metric_value), sub_entry.getKey(), monitoredResourceID);
							}
							//sendMonitoringDatum((Double)metric_value, ResourceFactory.createResource(MC.getURI() + sub_entry.getKey()), monitoredResourceURL, monitoredResource);

							break;
						case RUNTIME:
							if (isSent) {
								ddaConnector.sendSyncMonitoringDatum(String.valueOf(metric_value), sub_entry.getKey(), monitoredResourceID);
							}
								//sendMonitoringDatum((Double)metric_value, ResourceFactory.createResource(MC.getURI() + sub_entry.getKey()), monitoredResourceURL, monitoredResource);

							break;
						}
					} catch (ServerErrorException e) {
						e.printStackTrace();
					} catch (StreamErrorException e) {
						e.printStackTrace();
					} catch (ValidationErrorException e) {
						e.printStackTrace();
					} 

				}

				//post-process
				switch (key) {
				case CPU:
					lastSystemTime = systemTime;
					lastProcessCpuTime = processCpuTime;
					break;
				case MEMORY:
					break;
				case MEMORYPOOL:
					break;
				case THREAD:
					break;
				case RUNTIME:
					break;
				}
			}
			isInitial = false;

			Long t1 = System.currentTimeMillis(); //finish time
			try {
				Map<Integer, Long> timeToSleep = new HashMap<Integer, Long>();

				long min = 0;
				for (Map.Entry<Integer, Integer> entry : resolution.entrySet()) {
					timeToSleep.put(entry.getKey(), entry.getValue()-(t1-lastMonitorTime.get(entry.getKey())));
					min = timeToSleep.get(entry.getKey());
					toRun = entry.getKey();
				}

				for (Map.Entry<Integer, Long> entry : timeToSleep.entrySet()) {
					if (entry.getValue() < min) {
						min = entry.getValue();
						toRun = entry.getKey();
					}
				}
				Thread.sleep(Math.max(min, 0));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	@Override
	public void start() {
		jmxt = new Thread(this, "jmx-mon");
	}

	@Override
	public void init() {		
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
