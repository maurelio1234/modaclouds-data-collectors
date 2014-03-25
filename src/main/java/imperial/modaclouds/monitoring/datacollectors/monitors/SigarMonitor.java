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
import it.polimi.modaclouds.monitoring.kb.api.KBConnector;
import it.polimi.modaclouds.monitoring.objectstoreapi.ObjectStoreConnector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;

import polimi.deib.csparql_rest_api.exception.ServerErrorException;
import polimi.deib.csparql_rest_api.exception.StreamErrorException;

import com.thoughtworks.xstream.XStream;

/**
 * The monitoring collector for Sigar.
 */
public class SigarMonitor extends AbstractMonitor {

	/**
	 * Sigar instance.
	 */
	protected static Sigar sigar;

	/**
	 * Sigar monitor thread.
	 */
	private Thread sigt;

	/**
	 * Define CPU monitor as number 1.
	 */
	private static final int CPU = 1;

	/**
	 * Define memory monitor as number 2.
	 */
	private static final int MEMORY = 2;

	/**
	 * Define network monitor as number 3.
	 */
	private static final int NETWORK = 3;

	/**
	 * Define thread monitor as number 4.
	 */
	private static final int THREAD = 4;

	/**
	 * The category name.
	 */
	//private static final String[] category_name = {"CPU","Memory","Network","Thread"};

	/**
	 * The reflection class mapped from xml file.
	 */
	private ReflectionXML reflectionXML;

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
	 * The unique monitored resource ID.
	 */
	private String monitoredResourceID;


	/**
	 * Constructor of the class.
	 *
	 * @param measure Monitoring measure
	 * @throws MalformedURLException 
	 */
	public SigarMonitor(  ) throws MalformedURLException {
		this.monitoredResourceID = UUID.randomUUID().toString();
		monitorName = "sigar";
		
		ddaConnector = DDAConnector.getInstance();
		kbConnector = KBConnector.getInstance();
		objectStoreConnector = ObjectStoreConnector.getInstance();
		
		ddaConnector.setDdaURL(objectStoreConnector.getDDAUrl());
	}

	@Override
	public void run() {

		Long t0 = 0L;
		int toRun = 0;
		Map<Integer, Integer> resolution = new HashMap<Integer, Integer>();
		Map<Integer, Long> lastMonitorTime = new HashMap<Integer, Long>();

		//read xml file into the ReflectionXML object
		try {
			String filePath = System.getProperty("user.dir") + "/config/configuration_SIGAR.xml";
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
				break;
			case "Memory":
				categoryList.put(MEMORY, metrics);
				resolution.put(MEMORY, Integer.valueOf(listOfCategory.get(i).getResolution()));
				toRun = MEMORY;
				break;
			case "Network":
				categoryList.put(NETWORK, metrics);
				resolution.put(NETWORK, Integer.valueOf(listOfCategory.get(i).getResolution()));
				toRun = NETWORK;
				break;
			case "Thread":
				categoryList.put(THREAD, metrics);
				resolution.put(THREAD, Integer.valueOf(listOfCategory.get(i).getResolution()));
				toRun = THREAD;

				break;
			}
		}


		boolean isInitial = true;
		//run
		while (!sigt.isInterrupted()) {	
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
				try {
					switch (key) {
					case CPU:
						instance = sigar.getCpuPerc();
						break;
					case MEMORY:
						instance = sigar.getMem();
						break;
					case NETWORK:
						instance = sigar.getNetStat();
						break;
					case THREAD:
						instance = sigar.getProcStat();
						break;
					}
				} catch (SigarException e1) {
					e1.printStackTrace();
				}
				//invoke
				Map<String, ArrayList<Method>> value = entry.getValue();

				t0 = System.currentTimeMillis(); // invoke time

				lastMonitorTime.put(key, t0);

				for (Map.Entry<String, ArrayList<Method>> sub_entry : value.entrySet()) {
					ArrayList<Method> sub_methods = sub_entry.getValue();

					Object metric_value = null;

					try{
						for (int i = 0; i < sub_methods.size(); i++) {
							if (i == 0) {
								metric_value = sub_methods.get(i).invoke(instance);
							}
							else {
								metric_value = sub_methods.get(i).invoke(metric_value);
							}		    			
						}
					} catch (IllegalAccessException e) {
						e.printStackTrace();
					} catch (IllegalArgumentException e) {
						e.printStackTrace();
					} catch (InvocationTargetException e) {
						e.printStackTrace();
					}
					//process
					try {
						if (isSent) {
							ddaConnector.sendSyncMonitoringDatum(String.valueOf(metric_value), sub_entry.getKey(), monitoredResourceID);
						}
					} catch (ServerErrorException e) {
						e.printStackTrace();
					} catch (StreamErrorException e) {
						e.printStackTrace();
					} catch (ValidationErrorException e) {
						e.printStackTrace();
					}
					//sendMonitoringDatum((Double)metric_value, ResourceFactory.createResource(MC.getURI() + sub_entry.getKey()), monitoredResourceURL, monitoredResource);

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
		sigar = SingletonSigar.getInstance();
		sigt = new Thread(this, "sig-mon");
	}

	@Override
	public void init() {
		sigt.start();
		System.out.println("Sigar monitor running!");
	}

	@Override
	public void stop() {
		while (!sigt.isInterrupted()) {
			sigt.interrupt();
		}
		System.out.println("Sigar monitor stopped!");
	}


}
