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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;

import polimi.deib.csparql_rest_api.exception.ServerErrorException;
import polimi.deib.csparql_rest_api.exception.StreamErrorException;

/**
 * The monitoring collector for Sigar.
 */
public class SigarMonitor extends AbstractMonitor {

	/**
	 * Sigar instance.
	 */
	protected static Sigar sigar;

	/**
	 * Sigar monitor thread.
	 */
	private Thread sigt;

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
	 *
	 * @param measure Monitoring measure
	 * @throws MalformedURLException 
	 * @throws FileNotFoundException 
	 */
	public SigarMonitor(  ) throws MalformedURLException, FileNotFoundException {
		//this.monitoredResourceID = UUID.randomUUID().toString();
		
		this.monitoredResourceID = "FrontendVM";
		monitorName = "sigar";

		ddaConnector = DDAConnector.getInstance();
		kbConnector = KBConnector.getInstance();

		//ddaConnector.setDdaURL(objectStoreConnector.getDDAUrl());
	}

	@Override
	public void run() {

		long startTime = 0;

		List<Integer> period = null;

		List<Integer> nextPauseTime = null;

		while (!sigt.isInterrupted()) {

			if (System.currentTimeMillis() - startTime > 60000) {

				period = new ArrayList<Integer>();
				nextPauseTime = new ArrayList<Integer>();

				metricList = new ArrayList<Metric>();

				Set<KBEntity> dcConfig = kbConnector.getAll(DataCollector.class);

				for (KBEntity kbEntity: dcConfig) {
					DataCollector dc = (DataCollector) kbEntity;
					
					if (ModacloudsMonitor.findCollector(dc.getCollectedMetric()).equals("sigar")) {
						Metric temp = new Metric();

						temp.setMetricName(dc.getCollectedMetric());
						
						Set<Parameter> parameters = dc.getParameters();

						for (Parameter par: parameters) {
							switch (par.getName()) {
							case "samplingTime":
								period.add(Integer.valueOf(par.getValue()));
								nextPauseTime.add(Integer.valueOf(par.getValue()));
								break;
							case "samplingProbability":
								temp.setSamplingProb(Double.valueOf(par.getValue()));
								break;
							}
						}

						metricList.add(temp);
					}
				}

				startTime = System.currentTimeMillis();
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
				case "memoryused":
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
					ddaConnector.sendSyncMonitoringDatum(String.valueOf(value), metricList.get(index).getMetricName(), monitoredResourceID);
				}
			} catch (ServerErrorException e) {
				e.printStackTrace();
			} catch (StreamErrorException e) {
				e.printStackTrace();
			} catch (ValidationErrorException e) {
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
		System.out.println("Sigar monitor running!");
	}

	@Override
	public void stop() {
		while (!sigt.isInterrupted()) {
			sigt.interrupt();
		}
		System.out.println("Sigar monitor stopped!");
	}


}
