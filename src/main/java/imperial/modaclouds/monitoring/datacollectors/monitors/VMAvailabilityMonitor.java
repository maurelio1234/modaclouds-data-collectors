package imperial.modaclouds.monitoring.datacollectors.monitors;

import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import imperial.modaclouds.monitoring.datacollectors.basic.DataCollectorAgent;
import it.polimi.modaclouds.monitoring.dcfactory.DCMetaData;

/**
 * The monitoring collector for availability of VMs.
 */
public class VMAvailabilityMonitor extends AbstractMonitor {

	private Logger logger = LoggerFactory.getLogger(VMAvailabilityMonitor.class);
	
	/**
	 * Availability monitor thread.
	 */
	private Thread vavmt;

	/**
	 * The unique monitored target.
	 */
	private String monitoredTarget;

	private int samplingTime;

	private DataCollectorAgent dcAgent;

	public VMAvailabilityMonitor(String resourceId, String mode) {
		super(resourceId, mode);

		monitoredTarget = resourceId;

		monitorName = "vmavailability";

		dcAgent = DataCollectorAgent.getInstance();
	}

	@Override
	public void run() {

		long startTime = 0;

		while (!vavmt.isInterrupted()) {

			if (mode.equals("kb")) {

				if (System.currentTimeMillis() - startTime > 10000) {
					Collection<DCMetaData> dcConfig = dcAgent.getDataCollectors(resourceId);

					for (DCMetaData dc: dcConfig) {

						if (ModacloudsMonitor.findCollector(dc.getMonitoredMetric()).equals("vmavailability")) {			
							Map<String, String> parameters = dc.getParameters();

							samplingTime = Integer.valueOf(parameters.get("samplingTime"))*1000;

							break;
						}
					}

					startTime = System.currentTimeMillis();
				}
			}

			try {
				dcAgent.sendSyncMonitoringDatum("true","VMAvailable", monitoredTarget);
				Thread.sleep(samplingTime);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}


		}
	}


	@Override
	public void start() {
		vavmt = new Thread(this, "vavm-mon");
	}

	@Override
	public void init() {
		vavmt.start();
		logger.info("VM Availability monitor running!");
	}

	@Override
	public void stop() {
		while (!vavmt.isInterrupted()) {
			vavmt.interrupt();
		}
		logger.info("VM Availability monitor stopped!");
	}

}
