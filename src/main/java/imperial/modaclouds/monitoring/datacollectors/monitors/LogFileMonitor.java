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
import it.polimi.modaclouds.qos_models.monitoring_ontology.DataCollector;
import it.polimi.modaclouds.qos_models.monitoring_ontology.KBEntity;
import it.polimi.modaclouds.qos_models.monitoring_ontology.Parameter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import polimi.deib.csparql_rest_api.exception.ServerErrorException;
import polimi.deib.csparql_rest_api.exception.StreamErrorException;


/**
 * The monitoring collector for Apache server log file.
 *

 */
public class LogFileMonitor extends AbstractMonitor {

	/**
	 * Extract the request information according the regular expression.
	 */
	private static Pattern requestPattern;

	/**
	 * Log file name.
	 */
	private String fileName;

	/**
	 * Apache log file monitor thread.
	 */
	private Thread almt;

	/**
	 * Pattern to match.
	 */
	private String pattern;

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
	 * The unique monitored target.
	 */
	private String monitoredTarget;

	/**
	 * The collected metric.
	 */
	private String CollectedMetric;

	/**
	 * The sampling probability.
	 */
	private double samplingProb;

	/**
	 * Constructor of the class.
	 * @throws MalformedURLException 
	 * @throws FileNotFoundException 
	 */
	public LogFileMonitor (String ownURI, String mode) throws MalformedURLException, FileNotFoundException {
		//this.monitoredResourceID = "FrontendVM";
		//this.monitoredTarget = monitoredResourceID;
		super(ownURI, mode);
		monitorName = "logFile";
		
		ddaConnector = DDAConnector.getInstance();
		kbConnector = KBConnector.getInstance();

		//ddaConnector.setDdaURL(objectStoreConnector.getDDAUrl());
	}

	@Override
	public void run() {

		long startTime = 0;

		while(!almt.isInterrupted()) {

			if (System.currentTimeMillis() - startTime > 10000) {

				Set<KBEntity> dcConfig = kbConnector.getAll(DataCollector.class);
				for (KBEntity kbEntity: dcConfig) {
					DataCollector dc = (DataCollector) kbEntity;

					if (dc.getTargetResources().iterator().next().getUri().equals(ownURI)) {

						if (ModacloudsMonitor.findCollector(dc.getCollectedMetric()).equals("apache")) {

							Set<Parameter> parameters = dc.getParameters();

							monitoredTarget = dc.getTargetResources().iterator().next().getUri();

							for (Parameter par: parameters) {
								switch (par.getName()) {
								case "fileName":
									fileName = par.getValue();
									break;
								case "pattern":
									pattern = par.getValue();
									break;
								case "samplingTime":
									period = Integer.valueOf(par.getValue())*1000;
									break;
								case "samplingProbability":
									samplingProb = Double.valueOf(par.getValue());
									break;
								}
							}
							break;
						}
					}
				}
				startTime = System.currentTimeMillis();
			}

			requestPattern = Pattern.compile(pattern);

			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			Date date = new Date();

			String fullFileName = fileName+"."+dateFormat.format(date);

			try {
				RandomAccessFile in = new RandomAccessFile(fullFileName, "r");
				String line;

				Long t0 = System.currentTimeMillis();

				if((line = in.readLine()) != null) {
					Matcher requestMatcher = requestPattern.matcher(line);
					while (requestMatcher.find()) {
						String temp = "";
						for (int i = 1; i <= requestMatcher.groupCount(); i++) {
							temp = temp + requestMatcher.group(i);
							if (i != requestMatcher.groupCount())
								temp = temp + ", ";
						}
						//System.out.println(temp);
						try {
							if (Math.random() < samplingProb) {
								ddaConnector.sendSyncMonitoringDatum(temp, CollectedMetric, monitoredTarget);
							}
							//sendMonitoringDatum(Double.valueOf(temp), ResourceFactory.createResource(MC.getURI() + "ApacheLogFile"), monitoredResourceURL, monitoredResource);
						} catch (NumberFormatException e) {
							e.printStackTrace();
						} catch (ServerErrorException e) {
							e.printStackTrace();
						} catch (StreamErrorException e) {
							e.printStackTrace();
						} catch (ValidationErrorException e) {
							e.printStackTrace();
						}

					}	
				} else {
					Long t1 = System.currentTimeMillis();
					try {
						Thread.sleep(Math.max( period - (t1 - t0), 0));
					} catch (InterruptedException e) {
						in.close();
						Thread.currentThread().interrupt();
						break;
					} 
				}
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			} 
		} 
	}

	@Override
	public void start() {
		almt = new Thread( this, "alm-mon");
	}

	@Override
	public void init() {
		almt.start();
		System.out.println("Apache log file monitor running!");
	}

	@Override
	public void stop() {
		while (!almt.isInterrupted()) {
			almt.interrupt();
		}
		System.out.println("Apache log file monitor stopped!");
	}	

}
