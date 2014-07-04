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
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import polimi.deib.csparql_rest_api.exception.ServerErrorException;
import polimi.deib.csparql_rest_api.exception.StreamErrorException;

import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import it.polimi.modaclouds.monitoring.ddaapi.DDAConnector;
import it.polimi.modaclouds.monitoring.ddaapi.ValidationErrorException;
import it.polimi.modaclouds.monitoring.kb.api.KBConnector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.DataCollector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.KBEntity;
import it.polimi.modaclouds.qos_models.monitoring_ontology.Parameter;

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
	 * The unique monitored target.
	 */
	private String monitoredTarget;

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
	//private ObjectStoreConnector objectStoreConnector;

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
	 * @throws FileNotFoundException 
	 */
	public AvailabilityMonitor (String ownURI, String mode) throws MalformedURLException, FileNotFoundException {
		//this.monitoredResourceID = "FrontendVM";
		//this.monitoredTarget = monitoredResourceID;

		super(ownURI, mode);
		
		monitorName = "availability";

		ddaConnector = DDAConnector.getInstance();
		kbConnector = KBConnector.getInstance();

		//ddaConnector.setDdaURL(objectStoreConnector.getDDAUrl());
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

		Set<KBEntity> dcConfig = kbConnector.getAll(DataCollector.class);
		for (KBEntity kbEntity: dcConfig) {
			DataCollector dc = (DataCollector) kbEntity;

			if (dc.getTargetResources().iterator().next().getUri().equals(ownURI)) {

				if (ModacloudsMonitor.findCollector(dc.getCollectedMetric()).equals("availability")) {

					Set<Parameter> parameters = dc.getParameters();

					monitoredTarget = dc.getTargetResources().iterator().next().getUri();

					String type = null;

					String retryPeriod = null;

					String publicIP = null;

					for (Parameter par: parameters) {
						switch (par.getName()) {
						case "type":
							type = par.getValue();
							break;
						case "retryPeriod":
							retryPeriod = par.getValue();
							break;
						case "publicIP":
							publicIP = par.getValue();
							break;
						case "logFile":
							logFile = par.getValue();
							break;
						case "availabilityPeriod":
							availabilityPeriod = Integer.valueOf(par.getValue())*1000;
							break;
						}
					}

					if (type.equals("vm")) {
						vmInfo vm = new vmInfo();
						vm.retryPeriod = Integer.valueOf(retryPeriod)*1000;
						vm.publicIP = publicIP;

						vms.add(vm);
					}
					if (type.equals("app")) {
						appInfo app = new appInfo();
						app.retryPeriod = Integer.valueOf(retryPeriod)*1000;
						app.url = publicIP;

						apps.add(app);
					}

					break;
				}
			}
		}

		initialize();

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

	private void initialize() {
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
			try {
				BufferedReader br = new BufferedReader(new FileReader(files[i]));

				String sCurrentLine;
				while ((sCurrentLine = br.readLine()) != null) {
					String[] splits = sCurrentLine.split("\t");

					SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy,HH:mm:ss,SSS");
					Date date = sdf.parse(splits[0]);

					if (date.getTime() > startMillis) {
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


			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
	}

	private void computeStatistics() {		
		for (Map.Entry<String, Stat> entry : availabilityStats.entrySet()) {
			String key = entry.getKey();
			Stat value = entry.getValue();

			double MTTF = 0;
			double MTTR = 0;
			double avai = 0;

			double temp_succ = 0;
			double temp_fail = 0;

			try {
				if (value.wasReachable.equals("reachable")) {
					temp_succ = System.currentTimeMillis() - value.lastTime;
					temp_fail = 0;
				}
				else {
					temp_succ = 0;
					temp_fail = System.currentTimeMillis() - value.lastTime;
				}

				avai = ((double)value.successTime+temp_succ)/((double)value.successTime+(double)value.failTime+temp_succ+temp_fail);

				if (value.failCount != 0) {	    		
					MTTF = ((double)value.successTime+temp_succ)/value.failCount;
				}
				else {
					MTTF = (double)value.successTime+temp_succ;
				}

				if (value.successCount != 0) {	    		
					MTTR = ((double)value.failTime+temp_fail)/value.successCount;
				}
				else {
					MTTR = (double)value.failTime+temp_fail;
				}

				ddaConnector.sendSyncMonitoringDatum("MTTF\t"+key+"\t"+MTTF, "Reliability", monitoredTarget);
				ddaConnector.sendSyncMonitoringDatum("Availability\t"+key+"\t"+avai, "Availability", monitoredTarget);
				ddaConnector.sendSyncMonitoringDatum("MTTR\t"+key+"\t"+MTTR, "Reliability", monitoredTarget);
			} catch (Exception e) {
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
					if (availabilityStats.get(vm.publicIP) == null) {
						Stat temp = new Stat();

						if (reachable) {
							temp.wasReachable = "reachable";
							temp.lastTime = System.currentTimeMillis();
							availabilityStats.put(vm.publicIP, temp);

							logger.info(vm.publicIP+"\t"+"reachable");
							System.out.println("The vm is available");
							ddaConnector.sendSyncMonitoringDatum("Available", "Availability", monitoredTarget);
						}
						else {
							temp.wasReachable = "unreachable";
							temp.lastTime = System.currentTimeMillis();
							availabilityStats.put(vm.publicIP, temp);

							logger.info(vm.publicIP+"\t"+"unreachable");
							System.out.println("The vm is not available");
							ddaConnector.sendSyncMonitoringDatum("Unavailable", "Availability", monitoredTarget);
						}
					}
					else {
						Stat temp = availabilityStats.get(vm.publicIP);

						if (reachable) {
							if (temp.wasReachable.equals("reachable")){
								temp.successTime += System.currentTimeMillis() - temp.lastTime;
							}
							else{
								temp.wasReachable = "reachable";
								temp.successCount += 1;

								logger.info(vm.publicIP+"\t"+"reachable");
							}
							System.out.println("The vm is available");
							ddaConnector.sendSyncMonitoringDatum("Available", "Availability", monitoredTarget);
							temp.lastTime = System.currentTimeMillis();
						}
						else{
							if (temp.wasReachable.equals("unreachable")) {
								temp.failTime += System.currentTimeMillis() - temp.lastTime;
							}
							else{
								temp.wasReachable = "unreachable";
								temp.failCount += 1;

								logger.info(vm.publicIP+"\t"+"unreachable");
							}
							System.out.println("The vm is not available");
							ddaConnector.sendSyncMonitoringDatum("Unavailable", "Availability", monitoredTarget);
							temp.lastTime = System.currentTimeMillis();
						}
					}
				} catch (Exception e) {
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
						if (availabilityStats.get(app.url) == null) {
							Stat temp = new Stat();

							if (responseCode == 200) {
								temp.wasReachable = "reachable";
								temp.lastTime = System.currentTimeMillis();
								availabilityStats.put(app.url, temp);

								logger.info(app.url+"\t"+"reachable");
								System.out.println("The application is available");
								ddaConnector.sendSyncMonitoringDatum("Available", "Availability", monitoredTarget);
							}
							else {
								temp.wasReachable = "unreachable";
								temp.lastTime = System.currentTimeMillis();
								availabilityStats.put(app.url, temp);

								logger.info(app.url+"\t"+"unreachable");
								System.out.println("The application is not available");
								ddaConnector.sendSyncMonitoringDatum("Unavailable", "Availability", monitoredTarget);
							}
						}
						else {
							Stat temp = availabilityStats.get(app.url);

							if (responseCode == 200) {
								if (temp.wasReachable.equals("reachable")){
									temp.successTime += System.currentTimeMillis() - temp.lastTime;
								}
								else{
									temp.wasReachable = "reachable";
									temp.successCount += 1;

									logger.info(app.url+"\t"+"reachable");
								}
								System.out.println("The application is available");
								ddaConnector.sendSyncMonitoringDatum("Available", "Availability", monitoredTarget);
								temp.lastTime = System.currentTimeMillis();
							}
							else{
								if (temp.wasReachable.equals("unreachable")) {
									temp.failTime += System.currentTimeMillis() - temp.lastTime;
								}
								else{
									temp.wasReachable = "unreachable";
									temp.failCount += 1;

									logger.info(app.url+"\t"+"unreachable");
								}
								System.out.println("The application is not available");
								ddaConnector.sendSyncMonitoringDatum("Unavailable", "Availability", monitoredTarget);
								temp.lastTime = System.currentTimeMillis();
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					} 
					try {
						Thread.sleep(app.retryPeriod);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}


	@Override
	public void start() {
		avmt = new Thread( this, "avm-mon");		

		Properties log4jProperties = new Properties();
		log4jProperties.setProperty("log4j.rootLogger", "INFO, file");
		log4jProperties.setProperty("log4j.appender.file", "org.apache.log4j.RollingFileAppender");
		log4jProperties.setProperty("log4j.appender.file.File", logFile);
		log4jProperties.setProperty("log4j.appender.file.MaxFileSize", "10MB");
		log4jProperties.setProperty("log4j.appender.file.MaxBackupIndex", "10");
		log4jProperties.setProperty("log4j.appender.file.layout","org.apache.log4j.PatternLayout");
		log4jProperties.setProperty("log4j.appender.file.layout.conversionPattern", "%d{dd-MMM-yyyy,HH:mm:ss,SSS}	%m%n");
		PropertyConfigurator.configure(log4jProperties);
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
