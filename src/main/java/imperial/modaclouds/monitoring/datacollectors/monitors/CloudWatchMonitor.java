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

import freemarker.core.ParseException;
import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import imperial.modaclouds.monitoring.datacollectors.basic.Config;
import imperial.modaclouds.monitoring.datacollectors.basic.ConfigurationException;
import imperial.modaclouds.monitoring.datacollectors.basic.Metric;
import it.polimi.tower4clouds.data_collector_library.DCAgent;
import it.polimi.tower4clouds.model.ontology.VM;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;

/**
 * The monitoring collector for CloudWatch.
 */
public class CloudWatchMonitor extends AbstractMonitor {

	private Logger logger = LoggerFactory.getLogger(CloudWatchMonitor.class);

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
	 * The unique monitored target.
	 */
	private String monitoredTarget;

	/**
	 * The metric list.
	 */
	private List<Metric> metricList;

	private DCAgent dcAgent; 



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
	 */
	public CloudWatchMonitor(String resourceId, String mode)   {
		//this.monitoredResourceID = "FrontendVM";
		//this.monitoredTarget = monitoredResourceID;
		super(resourceId, mode);

		monitoredTarget = resourceId;

		monitorName = "cloudwatch";

	}

	@Override
	public void run() {

		long startTime = 0;

		String accessKeyId = null;

		String secretKey = null;

		String endpoint = null;

		ArrayList<String> measureNames = null;

		while (!cwmt.isInterrupted()) {

			if (System.currentTimeMillis() - startTime > 10000) {

				measureNames = new ArrayList<String>();

				metricList = new ArrayList<Metric>();

				for (String metric : getProvidedMetrics()) {
					try {
						if (dcAgent.shouldMonitor(new VM(Config.getInstance().getVmType(),
								Config.getInstance().getVmId()), metric)){
							Map<String, String> parameters = dcAgent.getParameters(metric);

							Metric temp = new Metric();

							temp.setMetricName(metric);

							measureNames.add(metric.replace("CloudWatch", ""));

							accessKeyId = parameters.get("accessKey");
							instanceID = parameters.get("instanceID");
							secretKey = parameters.get("secretKey");
							period = Integer.valueOf(parameters.get("samplingTime"))*1000;
							temp.setSamplingProb(Double.valueOf(parameters.get("samplingProbability")));
							endpoint = parameters.get("endpoint");

							metricList.add(temp);
						}
					} catch (NumberFormatException | ConfigurationException e) {
						e.printStackTrace();
					}
				}

				cloudWatchClient = new AmazonCloudWatchClient(new BasicAWSCredentials(accessKeyId, secretKey));
				cloudWatchClient.setEndpoint(endpoint);

				startTime = System.currentTimeMillis();
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
						for (Metric metric: metricList) {
							if (metric.getMetricName().equals(measureName)) {
								if (Math.random() < metric.getSamplingProb()) {
									logger.info("Sending datum: {} {} {}",String.valueOf(measureSet.getMeasure(measureName)), measureName, monitoredTarget);
									dcAgent.send(new VM(Config.getInstance().getVmType(),
											Config.getInstance().getVmId()), measureName,measureSet.getMeasure(measureName));
								}
							}
						}
					} catch (Exception e) {
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

	private Set<String> getProvidedMetrics() {
		Set<String> metrics = new HashSet<String>();
		metrics.add("DiskreadopsCloudWatch");
		metrics.add("CpuutilizationCloudWatch");
		metrics.add("DiskReadOpsCloudWatch");
		metrics.add("DiskWriteOpsCloudWatch");
		metrics.add("DiskReadBytesCloudWatch");
		metrics.add("DiskWriteBytesCloudWatch");
		metrics.add("NetworkInCloudWatch");
		metrics.add("NetworkOutCloudWatch");
		return metrics;
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

	@Override
	public void setDCAgent(DCAgent dcAgent) {
		this.dcAgent = dcAgent;
	}

}
