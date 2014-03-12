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
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

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
	 * The unique monitored resource ID.
	 */
	private String monitoredResourceID;

	/**
	 * The description of the VM.
	 */
	private class VmDetail {

		/**
		 * The instance ID of the VM.
		 */
		public String instanceID;

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
	 */
	public StartupTimeMonitor () throws MalformedURLException {
		this.monitoredResourceID = UUID.randomUUID().toString();
		monitorName = "startupTime";
		ddaConnector = DDAConnector.getInstance();
	}

	@Override
	public void run() {

		vms = new ArrayList<VmDetail>();

		try {
			String filePath = System.getProperty("user.dir") + "/config/configuration_Startup.xml";
			File file = new File(filePath);

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder;
			dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(file);

			doc.getDocumentElement().normalize();

			NodeList nList = doc.getElementsByTagName("vm");

			for (int i = 0; i < nList.getLength(); i++) {

				Node nNode = nList.item(i);

				if (nNode.getNodeType() == Node.ELEMENT_NODE) {

					Element eElement = (Element) nNode;

					VmDetail vm = new VmDetail();

					vm.connectTimeout = Integer.valueOf(eElement.getElementsByTagName("connectTimeout").item(0).getTextContent());
					vm.userName = eElement.getElementsByTagName("userName").item(0).getTextContent();
					vm.publicIP = eElement.getElementsByTagName("publicIP").item(0).getTextContent();
					vm.instanceID = eElement.getElementsByTagName("instanceID").item(0).getTextContent();
					vm.launchTime = eElement.getElementsByTagName("launchTime").item(0).getTextContent();;
					vm.isSpot = eElement.getElementsByTagName("isSpot").item(0).getTextContent().equals("true");	

					if (eElement.getElementsByTagName("keyFile").item(0)!=null){
						vm.keyFile = eElement.getElementsByTagName("keyFile").item(0).getTextContent();
					}
					
					if (eElement.getElementsByTagName("password").item(0)!=null){
						vm.password = eElement.getElementsByTagName("password").item(0).getTextContent();
					}
					
					vms.add(vm);			

				}
			}

		} catch (ParserConfigurationException e1) {
			e1.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
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
					if (vm.keyFile != null) {
						jsch.addIdentity(vm.keyFile);
					}
					Session session = jsch.getSession(vm.userName, vm.publicIP, 22);
					
					if (vm.password != null) {
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
							ddaConnector.sendSyncMonitoringDatum(String.valueOf(vm.startupTime), "StartupTime", monitoredResourceID);
						} catch (ServerErrorException e) {
							e.printStackTrace();
						} catch (StreamErrorException e) {
							e.printStackTrace();
						} catch (ValidationErrorException e) {
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
