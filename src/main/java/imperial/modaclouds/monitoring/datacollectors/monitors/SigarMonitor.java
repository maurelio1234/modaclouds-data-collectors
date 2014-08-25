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
import it.polimi.modaclouds.monitoring.dcfactory.DCMetaData;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * The monitoring collector for Sigar.
 */
public class SigarMonitor extends AbstractMonitor {
	
	private Logger logger = LoggerFactory.getLogger(SigarMonitor.class);

	/**
	 * Sigar instance.
	 */
	protected static Sigar sigar;

	/**
	 * Sigar monitor thread.
	 */
	private Thread sigt;

	
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
	 *
	 * @param measure Monitoring measure
	 * @throws MalformedURLException 
	 * @throws FileNotFoundException 
	 */
	public SigarMonitor( String resourceId, String mode)  {
		//this.monitoredResourceID = UUID.randomUUID().toString();
		//this.monitoredResourceID = "FrontendVM";
		super(resourceId, mode);

		monitoredTarget = resourceId;
		monitorName = "sigar";

		dcAgent = DataCollectorAgent.getInstance();
	}

	@Override
	public void run() {

		long startTime = 0;

		List<Integer> period = null;

		List<Integer> nextPauseTime = null;

		while (!sigt.isInterrupted()) {

			if (mode.equals("kb")) {

				if (System.currentTimeMillis() - startTime > 10000) {

					period = new ArrayList<Integer>();
					nextPauseTime = new ArrayList<Integer>();

					metricList = new ArrayList<Metric>();

					Collection<DCMetaData> dcConfig = dcAgent.getDataCollectors(resourceId);

					for (DCMetaData dc: dcConfig) {

							if (ModacloudsMonitor.findCollector(dc.getMonitoredMetric()).equals("sigar")) {
								Metric temp = new Metric();

								temp.setMetricName(dc.getMonitoredMetric());


								Map<String, String> parameters = dc.getParameters();

								period.add(Integer.valueOf(parameters.get("samplingTime"))*1000);
								nextPauseTime.add(Integer.valueOf(parameters.get("samplingTime"))*1000);
								temp.setSamplingProb(Double.valueOf(parameters.get("samplingProbability")));
								

								metricList.add(temp);
							}
						
					}

					startTime = System.currentTimeMillis();
				}
			}
			else {
				if (System.currentTimeMillis() - startTime > 3600000) {
					try {
						period = new ArrayList<Integer>();
						nextPauseTime = new ArrayList<Integer>();

						metricList = new ArrayList<Metric>();

						int temp_period = 0;

						String folder = new File(".").getCanonicalPath();
						File file = new File(folder+"/config/configuration_SIGAR.xml");

						DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
						DocumentBuilder dBuilder;
						dBuilder = dbFactory.newDocumentBuilder();
						Document doc = dBuilder.parse(file);

						doc.getDocumentElement().normalize();

						NodeList nList_jdbc = doc.getElementsByTagName("sigar-metric");

						for (int i = 0; i < nList_jdbc.getLength(); i++) {

							Node nNode = nList_jdbc.item(i);

							if (nNode.getNodeType() == Node.ELEMENT_NODE) {

								Element eElement = (Element) nNode;
								monitoredTarget = eElement.getElementsByTagName("monitoredTarget").item(0).getTextContent();
								temp_period = Integer.valueOf(eElement.getElementsByTagName("monitorPeriod").item(0).getTextContent());
							}
						}

						NodeList nList = doc.getElementsByTagName("metricName");

						for (int temp = 0; temp < nList.getLength(); temp++) {

							Node nNode = nList.item(temp);

							Metric temp_metric = new Metric();
							temp_metric.setMetricName(nNode.getTextContent());
							temp_metric.setSamplingProb(1);
							metricList.add(temp_metric);

							period.add(temp_period*1000);
							nextPauseTime.add(temp_period*1000);
						}



					} catch (ParserConfigurationException e1) {
						e1.printStackTrace();
					} catch (SAXException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
					startTime = System.currentTimeMillis();
				}
			}


			int toSleep = Collections.min(nextPauseTime);
			int index = nextPauseTime.indexOf(toSleep);

			try {
				Thread.sleep(Math.max(toSleep, 0));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			long t0 = System.currentTimeMillis();

			double value = 0;

			try {
				switch (metricList.get(index).getMetricName().toLowerCase()) {
				case "cpuutilization":
					value = 1 - sigar.getCpuPerc().getIdle();
					break;
				case "cpustolen":
					value = sigar.getCpuPerc().getStolen();
					break;
				case "memused":
					value = sigar.getMem().getActualUsed();
					break;
				}
			} catch (SigarException e) {
				e.printStackTrace();
			}

			boolean isSent = false;
			if (Math.random() < metricList.get(index).getSamplingProb()) {
				isSent = true;
			}

			try {
				if (isSent) {
					logger.info("Sending datum: {} {} {}",value, metricList.get(index).getMetricName(), monitoredTarget);
					dcAgent.sendSyncMonitoringDatum(String.valueOf(value), metricList.get(index).getMetricName(), monitoredTarget);
				}
			} catch (Exception e) {
				e.printStackTrace();
			} 

			long t1 = System.currentTimeMillis();

			for (int i = 0; i < nextPauseTime.size(); i++) {
				nextPauseTime.set(i, Math.max(0, Integer.valueOf(nextPauseTime.get(i)-toSleep-(int)(t1-t0))));
			}
			nextPauseTime.set(index,period.get(index));
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
		logger.info("Sigar monitor running");
	}

	@Override
	public void stop() {
		while (!sigt.isInterrupted()) {
			sigt.interrupt();
		}
		logger.info("Sigar monitor stopped!");
	}


}
