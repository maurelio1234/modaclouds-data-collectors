package imperial.modaclouds.monitoring.datacollectors.basic;

import imperial.modaclouds.monitoring.datacollectors.monitors.ModacloudsMonitor;
import it.polimi.modaclouds.monitoring.dcfactory.DCConfig;
import it.polimi.modaclouds.monitoring.dcfactory.DataCollectorFactory;
import it.polimi.modaclouds.monitoring.dcfactory.wrappers.DDAConnector;
import it.polimi.modaclouds.monitoring.dcfactory.wrappers.KBConnector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataCollectorAgent extends DataCollectorFactory {

	private static final Logger logger = LoggerFactory
			.getLogger(DataCollectorAgent.class);

	private static DataCollectorAgent _INSTANCE = null;
	private static int kbSyncPeriod;
	private static String appId;
	private static String vmId;
	private static String ddaURL;
	private static String kbURL;

	private static List<String> oldCollectors = new ArrayList<String>();

	private static Config config;

	public static void initialize() throws ConfigurationException {
		logger.info("Initializing {}...",
				DataCollectorAgent.class.getSimpleName());
		if (_INSTANCE != null) {
			logger.warn("{} is already initialized. Nothing to do.");
			return;
		}

		loadConfiguration();

		DDAConnector dda = new DDAConnector(ddaURL);
		KBConnector kb = new KBConnector(kbURL);
		_INSTANCE = new DataCollectorAgent(dda, kb);

//		_INSTANCE.addMonitoredResourceId(appId);
//		_INSTANCE.addMonitoredResourceId(vmId);

		logger.info(
				"{} initialized with:\n\tddaURL: {}\n\tkbURL: {}\n\tkbSyncPeriod: {}\n\tappId: {}\n\tvmId: {}",
				DataCollectorAgent.class.getSimpleName(), ddaURL, kbURL,
				kbSyncPeriod, appId, vmId);
	}

	private static void loadConfiguration() throws ConfigurationException {
		config = Config.getInstance();
		ddaURL = config.getDdaUrl();
		kbURL = config.getKbUrl();
		kbSyncPeriod = config.getKbSyncPeriod();
		appId = config.getAppId();
		vmId = config.getVmId();
	}

	public void startSyncingWithKB() {
		startSyncingWithKB(kbSyncPeriod);
		logger.info("{} started", DataCollectorAgent.class.getSimpleName());
	}

	public static boolean isInitialized() {
		return _INSTANCE != null;
	}

	public static DataCollectorAgent getInstance() {
		if (_INSTANCE == null)
			logger.error(
					"{} not initialized. Please run {}.initialize() before",
					DataCollectorAgent.class.getSimpleName(),
					DataCollectorAgent.class.getSimpleName());
		return _INSTANCE;
	}

	private DataCollectorAgent(DDAConnector dda, KBConnector kb) {
		super(dda, kb);
	}

	@Override
	protected void syncedWithKB() {
		Collection<DCConfig> appDataCollectors = getConfiguration(appId, null);
		Collection<DCConfig> vmDataCollectors = getConfiguration(vmId, null);

		List<String> newCollectors = new ArrayList<String>();

		// VM METRIC
		for (DCConfig vmDcConfig : vmDataCollectors) {
			if (!newCollectors.contains(ModacloudsMonitor
					.findCollector(vmDcConfig.getMonitoredMetric()))) {
				newCollectors.add(ModacloudsMonitor.findCollector(vmDcConfig
						.getMonitoredMetric()));
			}
		}

		// APP METRIC
		for (DCConfig appDcConfig : appDataCollectors) {
			if (!newCollectors.contains(ModacloudsMonitor
					.findCollector(appDcConfig.getMonitoredMetric()))) {
				newCollectors.add(ModacloudsMonitor.findCollector(appDcConfig
						.getMonitoredMetric()));
			}
		}

		List<String> toRun = new ArrayList<String>();
		List<String> toStop = new ArrayList<String>();

		for (String newCollector : newCollectors) {
			if (!oldCollectors.contains(newCollector)) {
				toRun.add(newCollector);
			}
		}

		for (String oldCollector : oldCollectors) {
			if (!newCollectors.contains(oldCollector)) {
				toStop.add(oldCollector);
			}
		}

		ModacloudsMonitor
				.runMonitoring(toRun.toArray(new String[toRun.size()]));
		ModacloudsMonitor.stopMonitoring(toStop.toArray(new String[toStop
				.size()]));

		oldCollectors = newCollectors;

	}

	public static String getAppId() {
		return appId;
	}

	public static String getVmId() {
		return vmId;
	}


}
