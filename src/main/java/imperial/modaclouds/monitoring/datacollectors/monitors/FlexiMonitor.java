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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;


/**
 * The monitoring collector for Flexiant cloud.
 */
public class FlexiMonitor extends AbstractMonitor {

	/**
	 * Flexi monitor thread.
	 */
	private Thread fmt;

	/**
	 * The channel variable.
	 */
	private Channel channel;

	/**
	 * The session variable.
	 */
	private	Session session;

	/**
	 * Monitoring period.
	 */
	private int period;

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
	 * @throws MalformedURLException 
	 */
	public FlexiMonitor() throws MalformedURLException  {
		this.monitoredResourceID = UUID.randomUUID().toString();
		monitorName = "flexiant";
		
		ddaConnector = DDAConnector.getInstance();
		kbConnector = KBConnector.getInstance();
		objectStoreConnector = ObjectStoreConnector.getInstance();
		
		ddaConnector.setDdaURL(objectStoreConnector.getDDAUrl());
	}

	@Override
	public void run() {
		String monitoredMachineAddress = null;
		String user = null;
		String password = null;
		String host = null;

		try {
			String filePath = System.getProperty("user.dir") + "/config/configuration_Flexi.xml";
			File file = new File(filePath);

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder;
			dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(file);

			doc.getDocumentElement().normalize();

			NodeList nList = doc.getElementsByTagName("flexi-metric");

			for (int i = 0; i < nList.getLength(); i++) {

				Node nNode = nList.item(i);

				if (nNode.getNodeType() == Node.ELEMENT_NODE) {

					Element eElement = (Element) nNode;

					monitoredMachineAddress = eElement.getElementsByTagName("nodeAddress").item(0).getTextContent();
					user = eElement.getElementsByTagName("user").item(0).getTextContent();
					password = eElement.getElementsByTagName("password").item(0).getTextContent();
					host = eElement.getElementsByTagName("host").item(0).getTextContent();
					period = Integer.valueOf(eElement.getElementsByTagName("monitorPeriod").item(0).getTextContent());

				}
			}

		} catch (ParserConfigurationException e1) {
			e1.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		JSch jsch = new JSch();

		try {
			session = jsch.getSession(user, host, 22);

			session.setPassword(password);

			session.setConfig("StrictHostKeyChecking", "no");

			session.connect(10*1000);
			while (!fmt.isInterrupted()) {
				boolean isSent = false;
				if (Math.random() < this.samplingProb) {
					isSent = true;
				}
				channel = session.openChannel("exec");

				String command = "snmpwalk -c public -v 1 "+monitoredMachineAddress+" .1.3.6.1.4.1.2021.51";
				//command = "ls -l";
				((ChannelExec)channel).setCommand(command);
				channel.setInputStream(null);
				((ChannelExec)channel).setErrStream(System.err);
				channel.setOutputStream(System.out);
				InputStream in = channel.getInputStream();


				channel.connect();

				BufferedReader buf = new BufferedReader(new InputStreamReader(in));
				String line = "";

				int count = 0;
				boolean isFirst = true;
				String metricName = null;

				while ((line = buf.readLine()) != null) {
					Pattern p = Pattern.compile("\"([^\"]*)\"");
					Matcher m = p.matcher(line);

					while (m.find()) {
						if (isFirst) {
							if (m.group(1).equals("NodeID")) {
								count++;
								metricName = "NodeID";
								System.out.println("NodeID");
								isFirst = false;
							}
							continue;
						}
						else {
							if (count%2 == 1) {
								try {
									if (isSent) {
										ddaConnector.sendSyncMonitoringDatum(m.group(1), metricName, monitoredResourceID);
									}
								} catch (ServerErrorException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} catch (StreamErrorException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								} catch (ValidationErrorException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								//sendMonitoringDatum(Double.valueOf(m.group(1)), ResourceFactory.createResource(MC.getURI() + metricName), monitoredResourceURL, monitoredResource);
								//System.out.println(metricName+"   "+m.group(1));
								count ++;
							}
							else {
								metricName = m.group(1);
								count ++;
							}
						}
					}
				}

				Thread.sleep(period);
			}

		} catch (JSchException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


	}

	@Override
	public void start() {
		fmt = new Thread( this, "fm-mon");	
	}

	@Override
	public void init() {
		fmt.start();
		System.out.println("Flexiant cloud monitor running!");
	}

	@Override
	public void stop() {
		channel.disconnect();
		session.disconnect();
		while (!fmt.isInterrupted()){
			fmt.interrupt();
		}
		System.out.println("Flexiant cloud monitor stopped!");
	}

}
