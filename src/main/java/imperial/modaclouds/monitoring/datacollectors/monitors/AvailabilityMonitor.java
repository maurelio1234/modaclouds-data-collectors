/**
 * Copyright (c) 2012-2013, Imperial College London, developed under the MODAClouds, FP7 ICT Project, grant agreement n�� 318484
 * All rights reserved.
 * 
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import polimi.deib.csparql_rest_api.exception.ServerErrorException;
import polimi.deib.csparql_rest_api.exception.StreamErrorException;

import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import it.polimi.modaclouds.monitoring.ddaapi.DDAConnector;
import it.polimi.modaclouds.monitoring.ddaapi.ValidationErrorException;

/**
 * The monitoring collector for availability of VMs and applications.
 */
public class AvailabilityMonitor extends AbstractMonitor{
	
	Logger logger = Logger.getLogger(AvailabilityMonitor.class);

	/**
	 * Availability monitor thread.
	 */
	private Thread avmt;

	/**
	 * The list of VMs to check.
	 */
	private List<vmInfo> vms;

	/**
	 * The list of applications to check.
	 */
	private List<appInfo> apps;

	/**
	 * DDa connector.
	 */
	private String monitoredResourceID;

	/**
	 * The unique monitored resource ID.
	 */
	private DDAConnector ddaConnector;
	
	/**
	 * The logFile to put the availability.
	 */
	private String logFile;
	
	/**
	 * The time period to calculate the availability.
	 */
	private long availabilityPeriod;

	/**
	 * Constructor of the class.
	 * @throws MalformedURLException 
	 */
	public AvailabilityMonitor () throws MalformedURLException {
		this.monitoredResourceID = UUID.randomUUID().toString();
		monitorName = "availability";
		ddaConnector = DDAConnector.getInstance();
	}

	/**
	 * The VM information.
	 */
	private class vmInfo{
		/**
		 * The public IP of the VM.
		 */
		public String publicIP;
		
		/**
		 * The retry period to connect the VM.
		 */
		public int retryPeriod;
	}

	/**
	 * The application information.
	 */
	private class appInfo{
		/**
		 * The application URL.
		 */
		public String url;
		
		/**
		 * The retry period to connect the VM.
		 */
		public int retryPeriod;
	}
	
	private Map<String,Stat> availabilityStats;
	
	/**
	 * The statistics of the availability.
	 */
	private class Stat {
		
		public long lastTime;
		
		public String wasReachable;
		
		public long successTime;
		
		public long failTime;
		
		public int successCount;
		
		public int failCount;
	}

	@Override
	public void run() {
		vms = new ArrayList<vmInfo>();

		apps = new ArrayList<appInfo>();

		try {
			String filePath = System.getProperty("user.dir") + "/config/configuration_Availability.xml";
			File file = new File(filePath);

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder;
			dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(file);

			doc.getDocumentElement().normalize();
			
			NodeList nList = doc.getElementsByTagName("availability");

			for (int i = 0; i < nList.getLength(); i++) {

				Node nNode = nList.item(i);

				if (nNode.getNodeType() == Node.ELEMENT_NODE) {

					Element eElement = (Element) nNode;

					logFile = eElement.getElementsByTagName("logFile").item(0).getTextContent();
					availabilityPeriod = Long.valueOf(eElement.getElementsByTagName("availabilityPeriod").item(0).getTextContent());
				}
			}

			NodeList nList_vm = doc.getElementsByTagName("vm");

			for (int i = 0; i < nList_vm.getLength(); i++) {

				Node nNode = nList_vm.item(i);

				if (nNode.getNodeType() == Node.ELEMENT_NODE) {

					Element eElement = (Element) nNode;

					vmInfo vm = new vmInfo();

					vm.retryPeriod = Integer.valueOf(eElement.getElementsByTagName("retryPeriod").item(0).getTextContent());
					vm.publicIP = eElement.getElementsByTagName("publicIP").item(0).getTextContent();

					vms.add(vm);
				}
			}

			NodeList nList_app = doc.getElementsByTagName("app");

			for (int i = 0; i < nList_app.getLength(); i++) {

				Node nNode = nList_app.item(i);

				if (nNode.getNodeType() == Node.ELEMENT_NODE) {

					Element eElement = (Element) nNode;

					appInfo app = new appInfo();

					app.retryPeriod = Integer.valueOf(eElement.getElementsByTagName("retryPeriod").item(0).getTextContent());
					app.url = eElement.getElementsByTagName("url").item(0).getTextContent();

					apps.add(app);
				}
			}

		} catch (ParserConfigurationException e1) {
			e1.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		Properties log4jProperties = new Properties();
		log4jProperties.setProperty("log4j.rootLogger", "INFO, file");
		log4jProperties.setProperty("log4j.appender.file", "org.apache.log4j.RollingFileAppender");
		log4jProperties.setProperty("log4j.appender.file.File", logFile);
		log4jProperties.setProperty("log4j.appender.file.MaxFileSize", "10MB");
		log4jProperties.setProperty("log4j.appender.file.MaxBackupIndex", "10");
		log4jProperties.setProperty("log4j.appender.file.layout","org.apache.log4j.PatternLayout");
		log4jProperties.setProperty("log4j.appender.file.layout.conversionPattern", "%d{dd-MMM-yyyy,HH:mm:ss,SSS}	%m%n");
		PropertyConfigurator.configure(log4jProperties);
		
		analyseVmApp();
		
		while (true) {
			computeStatistics();
			
			try {
				Thread.sleep(availabilityPeriod);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void computeStatistics() {
		availabilityStats = new HashMap<String,Stat>();
		
		final String fileName;
		File dir = new File(logFile);
		fileName = dir.getName();
		
		if(dir.exists()){
			dir = dir.getParentFile();
	    } 
		else {
			return;
		}
		
		File[] files = null;
		
		long currentMillis = System.currentTimeMillis();
		
		long startMillis = currentMillis - availabilityPeriod;
		
		files = dir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith(fileName);
			}
		});
		
		Arrays.sort(files,Collections.reverseOrder());
				
		for (int i = 0; i < files.length; i++) {
			System.out.println(files[i].getName());
			try {
				BufferedReader br = new BufferedReader(new FileReader(files[i]));
				
				int outdated = 1;
				String sCurrentLine;
				while ((sCurrentLine = br.readLine()) != null) {
					String[] splits = sCurrentLine.split("\t");
					
					SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy,HH:mm:ss,SSS");
					Date date = sdf.parse(splits[0]);
					
					if (date.getTime() > startMillis) {
						outdated = 0;
						if (availabilityStats.get(splits[1]) == null) {
							Stat temp = new Stat();
							temp.lastTime = date.getTime();
							temp.wasReachable = splits[2];
							
							if (splits[2].equals("reachable")) {
								temp.successCount += 1;
							}
							else {
								temp.failCount += 1;
							}
							
							availabilityStats.put(splits[1], temp);
						}
						else {
							Stat temp = availabilityStats.get(splits[1]);
							if (temp.wasReachable.equals(splits[2])) {
								if (temp.wasReachable.equals("reachable")) {
									temp.successTime += date.getTime() - temp.lastTime;
								}
								else {
									temp.failTime += date.getTime() - temp.lastTime;
								}
								temp.lastTime = date.getTime();
							}
							else {
								if (temp.wasReachable.equals("reachable")) {
									temp.failCount += 1;
									temp.wasReachable = "unreachable";
								}
								else {
									temp.successCount += 1;
									temp.wasReachable = "reachable";
								}
								temp.lastTime = date.getTime();
							}
							
							availabilityStats.put(splits[1], temp);
						}
					}
				}
				br.close();
				if (outdated == 1 && i != (files.length-1)) {
					if(files[i].delete()){
		    			System.out.println(files[i].getName() + " is deleted!");
		    		}else{
		    			System.out.println("Delete operation is failed.");
		    		}
				}
				
				
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
		
		for (Map.Entry<String, Stat> entry : availabilityStats.entrySet()) {
		    String key = entry.getKey();
		    Stat value = entry.getValue();
		    
		    try {
		    	if (value.failCount != 0) {
				    double MTTF = (double)value.successTime/value.failCount;
					ddaConnector.sendSyncMonitoringDatum("MTTF\t"+key+"\t"+MTTF, "Availability", monitoredResourceID);
		    	}
		    	else {
		    		ddaConnector.sendSyncMonitoringDatum("MTTF\t"+key+"\t"+"No failure", "Availability", monitoredResourceID);
		    	}
		    	
			    if (value.successCount != 0) {
				    double MTTR = (double)value.failTime/value.successCount;
					ddaConnector.sendSyncMonitoringDatum("MTTR\t"+key+"\t"+MTTR, "Availability", monitoredResourceID);			    	
			    }
			    else {
			    	ddaConnector.sendSyncMonitoringDatum("MTTR\t"+key+"\t"+"No success", "Availability", monitoredResourceID);
			    }

			} catch (ServerErrorException e) {
				e.printStackTrace();
			} catch (StreamErrorException e) {
				e.printStackTrace();
			} catch (ValidationErrorException e) {
				e.printStackTrace();
			}
		}
	
	}

	/**
	 * Start the thread to check the availability of the VMs and applications.
	 */
	private void analyseVmApp() {
		for (vmInfo vm: vms) {
			new Thread(new vmAvailability(vm)).start();
		}

		for (appInfo app: apps) {
			new Thread(new appAvailability(app)).start();
		}
	}

	/**
	 * Sub thread to check the availability of VMs.
	 */
	private class vmAvailability implements Runnable{

		private vmInfo vm;

		public vmAvailability(vmInfo vm){
			this.vm = vm;
		}

		@Override
		public void run() {
			while (true) {
				boolean reachable = false;
				try {
					reachable = InetAddress.getByName(vm.publicIP).isReachable(vm.retryPeriod);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				try{
					if (reachable) {
						logger.info(vm.publicIP+"\t"+"reachable");
						
						System.out.println("The vm is available");
						//ddaConnector.sendSyncMonitoringDatum("Available", "Availability", monitoredResourceID);
						//break;
					}
					else{
						logger.info(vm.publicIP+"\t"+"unreachable");
						
						System.out.println("The vm is not available");
						ddaConnector.sendSyncMonitoringDatum("Unavailable", "Availability", monitoredResourceID);
					}
				} catch (ServerErrorException e) {
					e.printStackTrace();
				} catch (StreamErrorException e) {
					e.printStackTrace();
				} catch (ValidationErrorException e) {
					e.printStackTrace();
				}
				try {
					Thread.sleep(vm.retryPeriod);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Sub thread to check the availability of applications.
	 */
	private class appAvailability implements Runnable{

		private appInfo app;

		public appAvailability(appInfo app) {
			this.app = app;
		}

		@Override
		public void run() {
			while (true) {
				HttpURLConnection connection;
				try {
					connection = (HttpURLConnection) new URL(app.url).openConnection();

					connection.setRequestMethod("HEAD");
					int responseCode = connection.getResponseCode();
					try{
						if (responseCode != 200) {
							logger.info(app.url+"\t"+"unreachable");
							
							System.out.println("The application is not available");
							ddaConnector.sendSyncMonitoringDatum("Unavailable", "Availability", monitoredResourceID);
						}
						else {
							logger.info(app.url+"\t"+"reachable");
							
							System.out.println("The application is available");	
							ddaConnector.sendSyncMonitoringDatum("Available", "Availability", monitoredResourceID);
							//break;
						}
					} catch (ServerErrorException e) {
						e.printStackTrace();
					} catch (StreamErrorException e) {
						e.printStackTrace();
					} catch (ValidationErrorException e) {
						e.printStackTrace();
					}
					Thread.sleep(app.retryPeriod);
				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}


	@Override
	public void start() {
		avmt = new Thread( this, "avm-mon");		
	}

	@Override
	public void init() {
		avmt.start();
		System.out.println("Availability monitor running!");		
	}

	@Override
	public void stop() {
		while (!avmt.isInterrupted()) {
			avmt.interrupt();
		}
		System.out.println("Availability monitor stopped!");		
	}

}
