package imperial.modaclouds.monitoring.datacollectors.basic;

import org.apache.commons.validator.routines.UrlValidator;

public class Config {
	
	private static Config _instance = null;
	private UrlValidator validator;
	private String ddaIP;
	private String ddaPort;
	private String kbIP;
	private String kbPort;
	private String kbPath;
	private String ddaUrl;
	private String kbUrl;
	private int kbSyncPeriod;
	private String appId;
	private String vmId;
	
	public static Config getInstance() throws ConfigurationException {
		if (_instance == null)
			_instance = new Config();
		return _instance;
	}
	
	private Config() throws ConfigurationException{
		validator = new UrlValidator();
		ddaIP = getMandatoryEnvVar(Env.MODACLOUDS_MONITORING_DDA_ENDPOINT_IP);
		ddaPort = getMandatoryEnvVar(Env.MODACLOUDS_MONITORING_DDA_ENDPOINT_PORT);
		kbIP = getMandatoryEnvVar(Env.MODACLOUDS_KNOWLEDGEBASE_ENDPOINT_IP);
		kbPort = getMandatoryEnvVar(Env.MODACLOUDS_KNOWLEDGEBASE_ENDPOINT_PORT);
		kbPath = getMandatoryEnvVar(Env.MODACLOUDS_KNOWLEDGEBASE_DATASET_PATH);
		String kbSyncPeriodString = getOptionalEnvVar(Env.MODACLOUDS_KNOWLEDGEBASE_SYNC_PERIOD);
		appId = getMandatoryEnvVar(Env.MODACLOUDS_MONITORED_APP_ID);
		vmId = getMandatoryEnvVar(Env.MODACLOUDS_MONITORED_VM_ID);
		
		ddaUrl = "http://" + ddaIP + ":" + ddaPort;
		kbUrl = "http://" + kbIP + ":" + kbPort + kbPath;
		
		if (!validator.isValid(ddaUrl))
			throw new ConfigurationException(ddaUrl + " is not a valid URL");
		if (!validator.isValid(kbUrl))
			throw new ConfigurationException(kbUrl + " is not a valid URL");
		
		try {
			kbSyncPeriod = Integer.parseInt(kbSyncPeriodString);
		} catch (NumberFormatException e) {
			throw new ConfigurationException(kbSyncPeriodString
					+ " is not a valid value for "
					+ Env.MODACLOUDS_KNOWLEDGEBASE_SYNC_PERIOD);
		}
	}

	

	private String getMandatoryEnvVar(String varName)
			throws ConfigurationException {
		String var = System.getenv(varName);
		if (var == null)
			throw new ConfigurationException(varName
					+ " variable was not defined");
		return var;
	}


	private String getOptionalEnvVar(String varName) {
		String var = System.getenv(varName);
		if (var == null) {
			var = getDefaultValue(Env.MODACLOUDS_KNOWLEDGEBASE_SYNC_PERIOD);
		}
		return var;
	}

	private  String getDefaultValue(String varName) {
		switch (varName) {
		case Env.MODACLOUDS_KNOWLEDGEBASE_SYNC_PERIOD:
			return "10";
		default:
			return "";
		}
	}

	public String getDdaUrl() {
		return ddaUrl;
	}

	public String getKbUrl() {
		return kbUrl;
	}

	public String getAppId() {
		return appId;
	}

	public String getVmId() {
		return vmId;
	}

	public int getKbSyncPeriod() {
		return kbSyncPeriod;
	}
	
	
}
