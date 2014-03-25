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
import java.io.IOException;
import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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

/**
 * The monitoring collector for MySQL database.
 */
public class MySQLMonitor extends AbstractMonitor {

	/**
	 * Mysql monitor thread.
	 */
	private Thread sqlt;

	/**
	 * SQL connection variable.
	 */
	private static Connection conDb;

	/**
	 * The JDBC name.
	 */
	private static String JDBC_DRIVER = "org.drizzle.jdbc.DrizzleDriver";

	/**
	 * The database url.
	 */
	private String JDBC_URL;

	/**
	 * The user name.
	 */
	private String JDBC_NAME;

	/**
	 * The user password.
	 */
	private String JDBC_PASSWORD;

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
	 * List of required monitoring metrics.
	 */
	private ArrayList<String> requiredMetric;

	/**
	 * Constructor of the class.
	 * @throws MalformedURLException 
	 */
	public MySQLMonitor() throws MalformedURLException {
		this.monitoredResourceID = UUID.randomUUID().toString();
		monitorName = "mysql";
		
		ddaConnector = DDAConnector.getInstance();
		kbConnector = KBConnector.getInstance();
		objectStoreConnector = ObjectStoreConnector.getInstance();
		
		ddaConnector.setDdaURL(objectStoreConnector.getDDAUrl());
	}


	@Override
	public void run() {

		String query = "SHOW GLOBAL STATUS where ";

		int numMetrics = 0;
		for (String s : requiredMetric) {
			if (numMetrics == 0) {
				query = query + "Variable_name like '" + s + "'";
			}
			else {
				query = query + "or Variable_name like '" + s + "'";
			}
			numMetrics++;
		}

		while (!sqlt.isInterrupted()) {
			boolean isSent = false;
			if (Math.random() < this.samplingProb) {
				isSent = true;
			}
			Long t0 = System.currentTimeMillis();
			// TO FIX adding delay
			PreparedStatement ps = null;
			ResultSet rs;

			try {
				try {
					ps = conDb.prepareStatement(query);
				} catch (SQLException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				rs = ps.executeQuery(query);
				while(rs.next()) {
					String variableName = rs.getString("Variable_name");
					String value = rs.getString("Value");

					try {
						if (isSent) {
							ddaConnector.sendSyncMonitoringDatum(value, variableName, monitoredResourceID);
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
					//sendMonitoringDatum(Double.valueOf(value), ResourceFactory.createResource(MC.getURI() + variableName), monitoredResourceURL, monitoredResource);

					//System.out.println(variableName + ": " + value);
				}
			} catch (SQLException ex) {
				System.out.println("Error execute query" + ex);
				ex.printStackTrace();
				System.exit(0);
			}

			Long t1 = System.currentTimeMillis();
			try
			{
				// correct sleep time to ensure periodic sampling
				Thread.sleep( Math.max( period - (t1 - t0), 0));
			}
			catch( InterruptedException e )
			{
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	@Override
	public void start() {

		requiredMetric = new ArrayList<String>();
		try {
			String filePath = System.getProperty("user.dir") + "/config/configuration_MySQL.xml";
			File file = new File(filePath);

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder;
			dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(file);

			doc.getDocumentElement().normalize();

			NodeList nList = doc.getElementsByTagName("metricName");

			for (int temp = 0; temp < nList.getLength(); temp++) {

				Node nNode = nList.item(temp);

				requiredMetric.add(nNode.getTextContent());
			}

			NodeList nList_jdbc = doc.getElementsByTagName("mysql-metric");

			for (int i = 0; i < nList_jdbc.getLength(); i++) {

				Node nNode = nList_jdbc.item(i);

				if (nNode.getNodeType() == Node.ELEMENT_NODE) {

					Element eElement = (Element) nNode;
					JDBC_URL = eElement.getElementsByTagName("databaseAddress").item(0).getTextContent();
					JDBC_NAME = eElement.getElementsByTagName("databaseUser").item(0).getTextContent();
					JDBC_PASSWORD = eElement.getElementsByTagName("databasePassword").item(0).getTextContent();
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

		try {
			Class.forName(JDBC_DRIVER);
		} catch (Exception ex) {
			System.out.println("Error Class.forName" + ex);
			ex.printStackTrace();
			System.exit(0);
		}

		try {
			conDb = DriverManager.getConnection(JDBC_URL,JDBC_NAME,JDBC_PASSWORD);
		} catch (Exception ex) {
			System.out.println("Error getConnection"+ex);
			ex.printStackTrace();
			System.exit(0);
		}

		sqlt = new Thread(this, "mysql-mon");
	}

	@Override
	public void init() {
		sqlt.start();
		System.out.println("MySQL monitor running!");
	}

	@Override
	public void stop() {
		while (!sqlt.isInterrupted()) {
			sqlt.interrupt();
		}
		System.out.println("MySQL monitor stopped!");
	}

}
