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
import it.polimi.modaclouds.monitoring.dcfactory.kbconnectors.DCMetaData;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

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
	 * Object store connector.
	 */
	//private ObjectStoreConnector objectStoreConnector;

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
	 * @throws MalformedURLException 
	 * @throws FileNotFoundException 
	 */
	public MySQLMonitor(String resourceId, String mode)  {
		//this.monitoredResourceID = "FrontendVM";
		//this.monitoredTarget = monitoredResourceID;
	
		super(resourceId, mode);
		monitoredTarget = resourceId;

		monitorName = "mysql";
		dcAgent = DataCollectorAgent.getInstance();
	}


	@Override
	public void run() {

		long startTime = 0;

		while (!sqlt.isInterrupted()) {

			if (mode.equals("kb")) {

				if (System.currentTimeMillis() - startTime > 10000) {
					
					metricList = new ArrayList<Metric>();

					List<Integer> periodList = new ArrayList<Integer>();

					Collection<DCMetaData> dcConfig = dcAgent.getDataCollectors(resourceId);

					for (DCMetaData dc: dcConfig) {

						

							if (ModacloudsMonitor.findCollector(dc.getMonitoredMetric()).equals("mysql")) {

								Metric temp = new Metric();

								temp.setMetricName(dc.getMonitoredMetric());

								Map<String, String> parameters = dc.getParameters();

								periodList.add(Integer.valueOf(parameters.get("samplingTime"))*1000);
								temp.setSamplingProb(Double.valueOf(parameters.get("samplingProbability")));

								metricList.add(temp);
							}
						
					}

					period = Collections.min(periodList);
					startTime = System.currentTimeMillis();
				}
			}
			String query = "SHOW GLOBAL STATUS where ";

			int numMetrics = 0;
			for (Metric metric : metricList) {
				if (numMetrics == 0) {
					query = query + "Variable_name like '" + metric.getMetricName() + "'";
				}
				else {
					query = query + "or Variable_name like '" + metric.getMetricName() + "'";
				}
				numMetrics++;
			}

			Long t0 = System.currentTimeMillis();
			// TO FIX adding delay
			PreparedStatement ps = null;
			ResultSet rs;

			try {
				try {
					ps = conDb.prepareStatement(query);
				} catch (SQLException e1) {
					e1.printStackTrace();
				}
				rs = ps.executeQuery(query);
				while(rs.next()) {
					String variableName = rs.getString("Variable_name");
					String value = rs.getString("Value");

					try {
						for (Metric metric: metricList) {
							if (metric.getMetricName().equals(variableName)) {
								if (Math.random() < metric.getSamplingProb()) {
									dcAgent.sendSyncMonitoringDatum(value, variableName, monitoredTarget);
								}
							}
						}
					} catch (Exception e) {
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
				Thread.sleep( Math.max( period*1000 - (t1 - t0), 0));
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
		metricList = new ArrayList<Metric>();
		if (mode.equals("kb")) {

			Collection<DCMetaData> dcConfig = dcAgent.getDataCollectors(resourceId);

			for (DCMetaData dc: dcConfig) {
				if (ModacloudsMonitor.findCollector(dc.getMonitoredMetric()).equals("mysql")) {

					Map<String, String> parameters = dc.getParameters();

					JDBC_URL = parameters.get("databaseAddress");
					JDBC_NAME = parameters.get("databaseUser");
					JDBC_PASSWORD = parameters.get("databasePassword");
				
					break;
				}
			}
		}
		else {
			try {
				String folder = new File(".").getCanonicalPath();
				File file = new File(folder+"/config/configuration_MySQL.xml");

				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder;
				dBuilder = dbFactory.newDocumentBuilder();
				Document doc = dBuilder.parse(file);

				doc.getDocumentElement().normalize();

				NodeList nList = doc.getElementsByTagName("metricName");

				for (int temp = 0; temp < nList.getLength(); temp++) {

					Node nNode = nList.item(temp);

					Metric temp_metric = new Metric();
					temp_metric.setMetricName(nNode.getTextContent());
					temp_metric.setSamplingProb(1);
					metricList.add(temp_metric);
				}

				NodeList nList_jdbc = doc.getElementsByTagName("mysql-metric");

				for (int i = 0; i < nList_jdbc.getLength(); i++) {

					Node nNode = nList_jdbc.item(i);

					if (nNode.getNodeType() == Node.ELEMENT_NODE) {

						Element eElement = (Element) nNode;
						JDBC_URL = eElement.getElementsByTagName("databaseAddress").item(0).getTextContent();
						JDBC_NAME = eElement.getElementsByTagName("databaseUser").item(0).getTextContent();
						JDBC_PASSWORD = eElement.getElementsByTagName("databasePassword").item(0).getTextContent();
						monitoredTarget = eElement.getElementsByTagName("monitoredTarget").item(0).getTextContent();
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
