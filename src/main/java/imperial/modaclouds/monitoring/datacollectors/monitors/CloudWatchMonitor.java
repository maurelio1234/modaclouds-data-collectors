/**
 * Copyright (c) 2012-2013, Imperial College London and Politecnico di Milano, developed under the MODAClouds, FP7 ICT Project, grant agreement n�� 318484
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
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

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;

import freemarker.core.ParseException;

/**
 * The monitoring collector for CloudWatch.
 */
public class CloudWatchMonitor extends AbstractMonitor {

	/**
	 * CloudWatch monitor thread.
	 */
	private Thread cwmt;

	/**
	 * The instance ID.
	 */
	private String instanceID;

	/**
	 * Amazon CloudWatch client.
	 */
	private AmazonCloudWatchClient cloudWatchClient;

	/**
	 * Monitoring period.
	 */
	private int period;

	/**
	 * DDa connector.
	 */
	private DDAConnector ddaConnector;

	/**
	 * The unique monitored resource ID.
	 */
	private String monitoredResourceID;


	/**
	 * The measure set to store the monitoring value.
	 */
	private static class MeasureSet implements Comparable<MeasureSet> {

		public Calendar timestamp;

		public HashMap<String, Double> measures = new HashMap<String, Double>();

		@Override
		public int compareTo(MeasureSet compare) {
			return (int) (timestamp.getTimeInMillis() - compare.timestamp
					.getTimeInMillis());
		}

		public void setMeasure(String measureName, double value) {
			measures.put(measureName, value);
		}

		public Set<String> getMeasureNames() {
			return measures.keySet();
		}

		public double getMeasure(String measureName) {
			return measures.get(measureName);

		}
	}

	/**
	 * Constructor of the class.
	 * @throws MalformedURLException 
	 */
	public CloudWatchMonitor() throws MalformedURLException  {
		this.monitoredResourceID = UUID.randomUUID().toString();
		ddaConnector = DDAConnector.getInstance();
		monitorName = "cloudwatch";
	}

	@Override
	public void run() {

		String accessKeyId = null;

		String secretKey = null;

		ArrayList<String> measureNames = null;

		try {
			String filePath = System.getProperty("user.dir") + "/config/configuration_EC2.xml";
			File file = new File(filePath);

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder;
			dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(file);

			doc.getDocumentElement().normalize();

			NodeList nList = doc.getElementsByTagName("ec2-metric");

			for (int i = 0; i < nList.getLength(); i++) {

				Node nNode = nList.item(i);

				if (nNode.getNodeType() == Node.ELEMENT_NODE) {

					Element eElement = (Element) nNode;

					accessKeyId = eElement.getElementsByTagName("accessKey").item(0).getTextContent();
					secretKey = eElement.getElementsByTagName("secretKey").item(0).getTextContent();
					instanceID = eElement.getElementsByTagName("instanceID").item(0).getTextContent();
					period = Integer.valueOf(eElement.getElementsByTagName("monitorPeriod").item(0).getTextContent());
				}
			}

			NodeList nList_metric = doc.getElementsByTagName("metricName");
			measureNames = new ArrayList<String>();

			for (int temp = 0; temp < nList_metric.getLength(); temp++) {

				Node nNode = nList_metric.item(temp);

				measureNames.add(nNode.getTextContent());
			}

		} catch (ParserConfigurationException e1) {
			e1.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		cloudWatchClient = new AmazonCloudWatchClient(new BasicAWSCredentials(accessKeyId, secretKey));
		cloudWatchClient.setEndpoint("monitoring.eu-west-1.amazonaws.com");

		while (!cwmt.isInterrupted()) {
			boolean isSent = false;
			if (Math.random() < this.samplingProb) {
				isSent = true;
			}
			MeasureSet measureSet = null;
			try {
				measureSet = this.retrieveMeasureSet(measureNames);
			} catch (ParseException e) {
				e.printStackTrace();
			}
			if (measureSet != null) {
				for (String measureName : measureSet.getMeasureNames()) {
					//System.out.println(measureName+"  "+String.valueOf(measureSet.getMeasure(measureName)));
					try {
						if (isSent){
							ddaConnector.sendSyncMonitoringDatum(String.valueOf(measureSet.getMeasure(measureName)), measureName, monitoredResourceID);
						}
					} catch (ServerErrorException e) {
						e.printStackTrace();
					} catch (StreamErrorException e) {
						e.printStackTrace();
					} catch (ValidationErrorException e) {
						e.printStackTrace();
					}
					//sendMonitoringDatum(Double.valueOf(measureSet.getMeasure(measureName)), ResourceFactory.createResource(MC.getURI() + measureName), monitoredResourceURL, monitoredResource);
				}
			}
			try {
				Thread.sleep(period);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	/**
	 * Retrieve monitoring value from metric names.
	 */
	public MeasureSet retrieveMeasureSet(ArrayList<String> measureNames)
			throws ParseException {

		GetMetricStatisticsRequest getMetricRequest = new GetMetricStatisticsRequest();

		// Dimension Set
		ArrayList<Dimension> dimensions = new ArrayList<Dimension>();
		Dimension dim = new Dimension();
		dim.setName("InstanceId");
		dim.setValue(instanceID);
		dimensions.add(dim);

		// Time Set
		//TimeZone zone = TimeZone.getDefault();
		//int timeOffset = zone.getOffset(new Date().getTime()) / (1000 * 3600);
		//String dateFormatString = "%1$tY-%1$tm-%1$tdT%1tH:%1$tM:%1$tSZ";
		GregorianCalendar calendar = new GregorianCalendar(
				TimeZone.getTimeZone("UTC"));
		calendar.add(GregorianCalendar.SECOND,
				-1 * calendar.get(GregorianCalendar.SECOND));

		// Static Set
		ArrayList<String> stats = new ArrayList<String>();
		stats.add("Average");
		//stats.add("Maximum");

		getMetricRequest.setStatistics(stats);
		getMetricRequest.setNamespace("AWS/EC2");
		getMetricRequest.setPeriod(60);

		getMetricRequest.setDimensions(dimensions);
		getMetricRequest.setEndTime(calendar.getTime());
		calendar.add(GregorianCalendar.MINUTE, -10);
		getMetricRequest.setStartTime(calendar.getTime());

		HashMap<Long, MeasureSet> measureSets = new HashMap<Long, MeasureSet>();
		for (String measureName : measureNames) {

			getMetricRequest.setMetricName(measureName);

			GetMetricStatisticsResult metricStatistics = cloudWatchClient.getMetricStatistics(getMetricRequest);

			List<Datapoint> datapoints = metricStatistics.getDatapoints();
			for (Datapoint point : datapoints) {

				Calendar cal = new GregorianCalendar();
				cal.setTime(point.getTimestamp());
				//cal.add(GregorianCalendar.HOUR, timeOffset);
				MeasureSet measureSet = measureSets.get(cal.getTimeInMillis());

				if (measureSet == null) {
					measureSet = new MeasureSet();
					measureSet.timestamp = cal;
					measureSets.put(cal.getTimeInMillis(), measureSet);
				}
				measureSet.setMeasure(measureName, point.getAverage());
			}

		}

		ArrayList<MeasureSet> sortedMeasureSets = new ArrayList<MeasureSet>(
				measureSets.values());
		if (sortedMeasureSets.size() == 0) {
			return null;

		} else {
			Collections.sort(sortedMeasureSets);
			return sortedMeasureSets.get(sortedMeasureSets.size() - 1);

		}
	}

	@Override
	public void start() {
		cwmt = new Thread( this, "cwm-mon");
	}

	@Override
	public void init() {
		cwmt.start();
		System.out.println("CloudWatch monitor running!");
	}

	@Override
	public void stop() {
		while (!cwmt.isInterrupted()){
			cwmt.interrupt();
		}
		System.out.println("CloudWatch monitor stopped!");
	}

}
