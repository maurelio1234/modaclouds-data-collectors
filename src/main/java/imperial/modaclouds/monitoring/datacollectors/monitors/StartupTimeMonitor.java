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
import it.polimi.modaclouds.monitoring.ddaapi.DDAConnector;
import it.polimi.modaclouds.monitoring.ddaapi.ValidationErrorException;
import it.polimi.modaclouds.monitoring.kb.api.KBConnector;
import it.polimi.modaclouds.monitoring.objectstoreapi.ObjectStoreConnector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.DataCollector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.KBEntity;
import it.polimi.modaclouds.qos_models.monitoring_ontology.Parameter;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import polimi.deib.csparql_rest_api.exception.ServerErrorException;
import polimi.deib.csparql_rest_api.exception.StreamErrorException;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

/**
 * The monitoring collector for startup time of the VMs.
 */
public class StartupTimeMonitor extends AbstractMonitor{

	private List<VmDetail> vms;

	/**
	 * Startup time monitor thread.
	 */
	private Thread sutm;

	/**
	 * Monitoring period.
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
	 * The description of the VM.
	 */
	private class VmDetail {

		/**
		 * The public IP of the VM.
		 */
		public String publicIP;

		/**
		 * The identity of the VM.
		 */
		public boolean isSpot;

		/**
		 * The launch time of the VM.
		 */
		public String launchTime;

		/**
		 * The user name of the VM.
		 */
		public String userName;

		/**
		 * The password corresponds to the user name.
		 */
		public String password;

		/**
		 * The key file of the VM.
		 */
		public String keyFile;

		/**
		 * The connection timeout time.
		 */
		public int connectTimeout;

		/**
		 * The startup time of the VM.
		 */
		public double startupTime;
	}

	/**
	 * Constructor of the class.
	 * @throws MalformedURLException 
	 * @throws FileNotFoundException 
	 */
	public StartupTimeMonitor (String ownURI, String mode) throws MalformedURLException, FileNotFoundException {
		//this.monitoredResourceID = "FrontendVM";
		//this.monitoredTarget = monitoredResourceID;
		super(ownURI, mode);
		monitorName = "startupTime";

		ddaConnector = DDAConnector.getInstance();
		kbConnector = KBConnector.getInstance();

		//ddaConnector.setDdaURL(objectStoreConnector.getDDAUrl());
	}

	@Override
	public void run() {

		vms = new ArrayList<VmDetail>();

		Set<KBEntity> dcConfig = kbConnector.getAll(DataCollector.class);
		for (KBEntity kbEntity: dcConfig) {
			DataCollector dc = (DataCollector) kbEntity;

			if (dc.getTargetResources().iterator().next().getUri().equals(ownURI)) {

				if (ModacloudsMonitor.findCollector(dc.getCollectedMetric()).equals("startupTime")) {

					dc.getTargetResources();

					monitoredTarget = dc.getTargetResources().iterator().next().getUri();

					//CollectedMetric = dc.getCollectedMetric();

					Set<Parameter> parameters = dc.getParameters();
					VmDetail vm = new VmDetail();

					for (Parameter par: parameters) {
						switch (par.getName()) {
						case "connectTimeout":
							vm.connectTimeout = Integer.valueOf(par.getValue())*1000;
							break;
						case "userName":
							vm.userName = par.getValue();
							break;	
						case "publicIP":
							vm.publicIP = par.getValue();
							break;
						case "launchTime":
							vm.launchTime = par.getValue();
							break;
						case "isSpot":
							vm.isSpot = Boolean.valueOf(par.getValue());
							break;
						case "keyFile":
							vm.keyFile = par.getValue();
							break;
						case "password":
							vm.password = par.getValue();
							break;
						}
					}
					break;
				}
			}
		}

		analyseVms();
	}

	/**
	 * Start sub thread to monitor the startup time of the VMs.
	 */
	private void analyseVms() {
		for (VmDetail vm: vms) {
			new Thread(new startup_monitor(vm)).start();
		}
	}

	/**
	 * Sub thread to monitor the startup time of the VM.
	 */
	private class startup_monitor implements Runnable{

		private VmDetail vm;

		public startup_monitor(VmDetail vm) {
			this.vm = vm;
		}

		@Override
		public void run() {
			JSch jsch = new JSch();

			SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

			Date date_launch = null;
			try {
				date_launch = format.parse(vm.launchTime);
			} catch (ParseException e) {
				e.printStackTrace();
			}

			while(true){
				try {
					if (vm.keyFile != null || !vm.keyFile.equals("")) {
						jsch.addIdentity(vm.keyFile);
					}
					Session session = jsch.getSession(vm.userName, vm.publicIP, 22);

					if (vm.password != null || !vm.password.equals("")) {
						session.setPassword(vm.password);
					}

					java.util.Properties config = new java.util.Properties();
					config.put("StrictHostKeyChecking", "no");
					session.setConfig(config);

					session.connect(vm.connectTimeout);

					if(session.isConnected()) {
						System.out.println(vm.publicIP);
						System.out.println("Conncected");
						Date currentTime = new Date();
						vm.startupTime = (currentTime.getTime()-date_launch.getTime())/1000;
						try {
							ddaConnector.sendSyncMonitoringDatum(String.valueOf(vm.startupTime), "StartupTime", monitoredTarget);
						} catch (Exception e) {
							e.printStackTrace();
						} 
						//System.out.println(vm.startupTime);
						break;
					}
				} catch (JSchException e1) {
					//e1.printStackTrace();
					System.out.println("Conncection failed. Retry.");
				}
			}

		}

	}

	@Override
	public void start() {
		sutm = new Thread( this, "stm-mon");
	}


	@Override
	public void init() {
		sutm.start();
		System.out.println("Startup time monitor running!");		
	}


	@Override
	public void stop() {
		while (!sutm.isInterrupted()) {
			sutm.interrupt();
		}
		System.out.println("Startup time monitor stopped!");		
	}

}
