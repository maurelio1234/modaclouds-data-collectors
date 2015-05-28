package imperial.modaclouds.monitoring.datacollectors.monitors;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import imperial.modaclouds.monitoring.datacollectors.basic.ConfigurationException;
import it.polimi.tower4clouds.data_collector_library.DCAgent;
import imperial.modaclouds.monitoring.datacollectors.basic.Config;
import it.polimi.tower4clouds.model.ontology.InternalComponent;

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

	private DCAgent dcAgent;

	private int samplingTime;

	private int port;

	private String path;

	private int retryPeriod;

	private int retryTimes;


	public AppAvailabilityMonitor(String resourceId, String mode) {
		super(resourceId, mode);

		monitoredTarget = resourceId;

		monitorName = "appavailability";
	}

	@Override
	public void run() {
		long startTime = 0;

		while (!aavmt.isInterrupted()) {

			if (mode.equals("tower4clouds")) {

				if (System.currentTimeMillis() - startTime > 10000) {

					for (String metric : getProvidedMetrics()) {

						try {
							if (dcAgent.shouldMonitor(new InternalComponent(Config.getInstance().getInternalComponentType(),
									Config.getInstance().getInternalComponentId()), metric)) {

								Map<String, String> parameters = dcAgent.getParameters(metric);

								samplingTime = Integer.valueOf(parameters.get("samplingTime"))*1000;
								port = Integer.valueOf(parameters.get("port"));
								retryPeriod = Integer.valueOf(parameters.get("retryPeriod"));
								retryTimes = Integer.valueOf(parameters.get("retryTimes"));
								path = parameters.get("path");
							}
						} catch (NumberFormatException e) {
							e.printStackTrace();
						} catch (ConfigurationException e) {
							e.printStackTrace();
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
					logger.info("Service not available: {}", e1.getMessage());
				}

				try {
					if (responseCode >= 200 && responseCode < 300) {
						logger.info("Sending datum: {} {} {}","1", "AppAvailable", monitoredTarget);
						dcAgent.send(new InternalComponent(Config.getInstance().getInternalComponentType(),
								Config.getInstance().getInternalComponentId()), "AppAvailable",1);
						break;
					}
					else {
						if ( count == retryTimes) {
							logger.info("Sending datum: {} {} {}","0", "AppAvailable", monitoredTarget);
							dcAgent.send(new InternalComponent(Config.getInstance().getInternalComponentType(),
									Config.getInstance().getInternalComponentId()), "AppAvailable",0);
							break;
						}
						else {
							count++;
						}
					}
				} catch (Exception e) {
					logger.error("Error while sending datum", e);
				}
			}
			long t1 = System.currentTimeMillis();

			try {
				Thread.sleep(Math.max(0, samplingTime-t1+t0));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	private Set<String> getProvidedMetrics() {
		Set<String> metrics = new HashSet<String>();
		metrics.add("AppAvailable");
		return metrics;
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

	@Override
	public void setDCAgent(DCAgent dcAgent) {
		this.dcAgent = dcAgent;
	}

}
