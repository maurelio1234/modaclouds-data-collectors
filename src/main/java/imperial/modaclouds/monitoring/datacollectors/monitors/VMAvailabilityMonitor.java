package imperial.modaclouds.monitoring.datacollectors.monitors;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import imperial.modaclouds.monitoring.datacollectors.basic.Config;
import imperial.modaclouds.monitoring.datacollectors.basic.ConfigurationException;
import it.polimi.tower4clouds.data_collector_library.DCAgent;
import it.polimi.tower4clouds.model.ontology.VM;

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

	private DCAgent dcAgent;

	public VMAvailabilityMonitor(String resourceId, String mode) {
		super(resourceId, mode);

		monitoredTarget = resourceId;

		monitorName = "vmavailability";
	}

	@Override
	public void run() {

		long startTime = 0;

		while (!vavmt.isInterrupted()) {

			if (mode.equals("tower4clouds")) {

				if (System.currentTimeMillis() - startTime > 10000) {

					for (String metric : getProvidedMetrics()) {
						try {
							if (dcAgent.shouldMonitor(new VM(Config.getInstance().getVmType(), 
									Config.getInstance().getVmId()), metric)) {
								Map<String, String> parameters = dcAgent.getParameters(metric);

								samplingTime = Integer.valueOf(parameters.get("samplingTime"))*1000;
							}
						} catch (NumberFormatException | ConfigurationException e) {
							e.printStackTrace();
						}
					}

					startTime = System.currentTimeMillis();
				}
			}

			try {
				logger.info("Sending datum: {} {} {}",1, "VMAvailable", monitoredTarget);
				dcAgent.send(new VM(Config.getInstance().getVmType(), 
						Config.getInstance().getVmId()), "VMAvailable", 1);
				Thread.sleep(samplingTime);
			} catch (InterruptedException | ConfigurationException e) {
				e.printStackTrace();
			}


		}
	}

	private Set<String> getProvidedMetrics() {
		Set<String> metrics = new HashSet<String>();
		metrics.add("VmAvailable");
		return metrics;
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

	@Override
	public void setDCAgent(DCAgent dcAgent) {
		this.dcAgent = dcAgent;		
	}

}
