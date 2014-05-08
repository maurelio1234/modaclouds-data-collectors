/**
 * Copyright ${year} deib-polimi
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

import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import imperial.modaclouds.monitoring.datacollectors.basic.Metric;
import it.polimi.modaclouds.monitoring.ddaapi.DDAConnector;
import it.polimi.modaclouds.monitoring.ddaapi.ValidationErrorException;
import it.polimi.modaclouds.monitoring.kb.api.KBConnector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.DataCollector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.KBEntity;
import it.polimi.modaclouds.qos_models.monitoring_ontology.Parameter;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
	//private ObjectStoreConnector objectStoreConnector;

	/**
	 * The unique monitored resource ID.
	 */
	private String monitoredResourceID;
	
	/**
	 * The metric list.
	 */
	private List<Metric> metricList; 

	/**
	 * Constructor of the class.
	 * @throws MalformedURLException 
	 * @throws FileNotFoundException 
	 */
	public MySQLMonitor() throws MalformedURLException, FileNotFoundException {
		this.monitoredResourceID = "FrontendVM";
		monitorName = "mysql";

		ddaConnector = DDAConnector.getInstance();
		kbConnector = KBConnector.getInstance();

		//ddaConnector.setDdaURL(objectStoreConnector.getDDAUrl());
	}


	@Override
	public void run() {

		long startTime = 0;
				
		while (!sqlt.isInterrupted()) {

			if (System.currentTimeMillis() - startTime > 60000) {
				
				metricList = new ArrayList<Metric>();
				
				List<Integer> periodList = new ArrayList<Integer>();
				
				Set<KBEntity> dcConfig = kbConnector.getAll(DataCollector.class);
				for (KBEntity kbEntity: dcConfig) {
					DataCollector dc = (DataCollector) kbEntity;
					if (ModacloudsMonitor.findCollector(dc.getCollectedMetric()).equals("mysql")) {

						Metric temp = new Metric();

						temp.setMetricName(dc.getCollectedMetric());
						
						Set<Parameter> parameters = dc.getParameters();

						for (Parameter par: parameters) {
							switch (par.getName()) {
							case "samplingTime":
								periodList.add(Integer.valueOf(par.getValue()));
								break;
							case "samplingProbability":
								temp.setSamplingProb(Double.valueOf(par.getValue()));
								break;
							}
						}
						
						metricList.add(temp);
					}
				}
				
				period = Collections.min(periodList);
				startTime = System.currentTimeMillis();
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
									ddaConnector.sendSyncMonitoringDatum(value, variableName, monitoredResourceID);
								}
							}
						}
					} catch (ServerErrorException e) {
						e.printStackTrace();
					} catch (StreamErrorException e) {
						e.printStackTrace();
					} catch (ValidationErrorException e) {
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

		Set<KBEntity> dcConfig = kbConnector.getAll(DataCollector.class);
		for (KBEntity kbEntity: dcConfig) {
			DataCollector dc = (DataCollector) kbEntity;
			if (ModacloudsMonitor.findCollector(dc.getCollectedMetric()).equals("mysql")) {

				Set<Parameter> parameters = dc.getParameters();

				for (Parameter par: parameters) {
					switch (par.getName()) {
					case "databaseAddress":
						JDBC_URL = par.getValue();
						break;
					case "databaseUser":
						JDBC_NAME = par.getValue();
						break;
					case "databasePassword":
						JDBC_PASSWORD = par.getValue();
						break;
					}
				}
				break;
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
