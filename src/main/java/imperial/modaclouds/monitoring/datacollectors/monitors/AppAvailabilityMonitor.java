package imperial.modaclouds.monitoring.datacollectors.monitors;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import imperial.modaclouds.monitoring.datacollectors.basic.DataCollectorAgent;
import it.polimi.modaclouds.monitoring.dcfactory.DCMetaData;

/**
 * The monitoring collector for availability of Applications.
 */
public class AppAvailabilityMonitor extends AbstractMonitor{

	private Logger logger = LoggerFactory.getLogger(AppAvailabilityMonitor.class);

	/**
	 * Availability monitor thread.
	 */
	private Thread aavmt;

	/**
	 * The unique monitored target.
	 */
	private String monitoredTarget;

	private DataCollectorAgent dcAgent;

	private int samplingTime;

	private int port;

	private String path;

	private int retryPeriod;

	private int retryTimes;


	public AppAvailabilityMonitor(String resourceId, String mode) {
		super(resourceId, mode);

		monitoredTarget = resourceId;

		monitorName = "appavailability";

		dcAgent = DataCollectorAgent.getInstance();
	}

	@Override
	public void run() {
		long startTime = 0;

		while (!aavmt.isInterrupted()) {

			if (mode.equals("kb")) {

				if (System.currentTimeMillis() - startTime > 10000) {
					Collection<DCMetaData> dcConfig = dcAgent.getDataCollectors(resourceId);

					for (DCMetaData dc: dcConfig) {

						if (ModacloudsMonitor.findCollector(dc.getMonitoredMetric()).equals("appavailability")) {			
							Map<String, String> parameters = dc.getParameters();

							samplingTime = Integer.valueOf(parameters.get("samplingTime"))*1000;
							port = Integer.valueOf(parameters.get("port"));
							retryPeriod = Integer.valueOf(parameters.get("retryPeriod"));
							retryTimes = Integer.valueOf(parameters.get("retryTimes"));
							path = parameters.get("path");

							break;
						}
					}

					startTime = System.currentTimeMillis();
				}
			}


			HttpURLConnection connection;
				String url = "http://localhost:"+port+path;
				logger.info("URL: "+url);
				int count = 0;

				long t0 = System.currentTimeMillis();

				while (true) {

					int responseCode = 0;
					
					try {
						connection = (HttpURLConnection) new URL(url).openConnection();
						connection.setConnectTimeout(retryPeriod);
						connection.setRequestMethod("HEAD");
						responseCode = connection.getResponseCode();
					} catch (IOException e1) {
						e1.printStackTrace();
					}

					try {
						if (responseCode == 200) {
							dcAgent.sendSyncMonitoringDatum("1", "AppAvailable",monitoredTarget);
							break;
						} 
						else {
							if ( count == retryTimes) {
								dcAgent.sendSyncMonitoringDatum("0", "AppAvailable",monitoredTarget);
								break;
							}
							else {
								count++;
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}


					long t1 = System.currentTimeMillis();

					try {
						Thread.sleep(Math.max(0, samplingTime-t1+t0));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}

			
		}

	}



	@Override
	public void start() {
		aavmt = new Thread(this, "aavm-mon");
	}

	@Override
	public void init() {
		aavmt.start();
		logger.info("App Availability monitor running!");
	}

	@Override
	public void stop() {
		while (!aavmt.isInterrupted()) {
			aavmt.interrupt();
		}
		logger.info("App Availability monitor stopped!");
	}

}
