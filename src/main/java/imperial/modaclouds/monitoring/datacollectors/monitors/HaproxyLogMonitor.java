package imperial.modaclouds.monitoring.datacollectors.monitors;

import imperial.modaclouds.monitoring.datacollectors.basic.AbstractMonitor;
import imperial.modaclouds.monitoring.datacollectors.basic.DataCollectorAgent;
import it.polimi.modaclouds.monitoring.dcfactory.DCConfig;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class HaproxyLogMonitor extends AbstractMonitor {

	/**
	 * Extract the request information according the regular expression.
	 */
	private static Pattern requestPattern;

	/**
	 * The unique monitored target.
	 */
	private String monitoredTarget;

	private DataCollectorAgent dcAgent;

	/**
	 * Monitoring period.
	 */
	private int period;

	/**
	 * Log file name.
	 */
	private String fileName;

	/**
	 * File position.
	 */
	private long filePointer;

	/**
	 * Response time monitor thread.
	 */
	private Thread hamt;

	public HaproxyLogMonitor(String resourceId, String mode) {
		super(resourceId, mode);

		monitorName = "haproxy";
		monitoredTarget = resourceId;

		dcAgent = DataCollectorAgent.getInstance();
	}

	@Override
	public void run() {
		long startTime = 0;

		while (!hamt.isInterrupted()) {

			if (mode.equals("kb")) {

				if (System.currentTimeMillis() - startTime > 60000) {

					System.out.println(resourceId);
					
					Collection<DCConfig> dcConfig = dcAgent.getConfiguration(resourceId,null);
										
					for (DCConfig dc : dcConfig) {
			
						System.out.println(dc.getMonitoredMetric());
						
						if (ModacloudsMonitor.findCollector(
								dc.getMonitoredMetric()).equals("haproxy")) {
							
							Map<String, String> parameters = dc.getParameters();
							
							fileName = parameters.get("logFileName");
							period = Integer.valueOf(parameters
									.get("samplingTime"));
							break;
						}
					}
				}
			} else {
				String folder = null;
				try {
					folder = new File(".").getCanonicalPath();
				} catch (IOException e2) {
					e2.printStackTrace();
				}
				File file_xml = new File(folder
						+ "/config/configuration_Haproxy.xml");

				DocumentBuilderFactory dbFactory = DocumentBuilderFactory
						.newInstance();
				DocumentBuilder dBuilder;
				try {
					dBuilder = dbFactory.newDocumentBuilder();

					Document doc = dBuilder.parse(file_xml);

					doc.getDocumentElement().normalize();

					NodeList nList = doc.getElementsByTagName("haproxy");
					for (int i = 0; i < nList.getLength(); i++) {

						Node nNode = nList.item(i);

						if (nNode.getNodeType() == Node.ELEMENT_NODE) {

							Element eElement = (Element) nNode;

							fileName = eElement
									.getElementsByTagName("LogFileName")
									.item(0).getTextContent();
							period = Integer.valueOf(eElement
									.getElementsByTagName("monitorPeriod")
									.item(0).getTextContent()) * 1000;
						}
					}
				} catch (ParserConfigurationException e1) {
					e1.printStackTrace();
				} catch (SAXException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			startTime = System.currentTimeMillis();

			RandomAccessFile file = null;
			Long t0 = (long) 0;

			try {
				t0 = System.currentTimeMillis();

				file = new RandomAccessFile(fileName, "r");

				if (file.length() < filePointer) {
					file = new RandomAccessFile(fileName, "r");
					filePointer = 0;
				} else {
					file.seek(filePointer);
					String line;
					List<String> lines = new ArrayList<String>();

					while ((line = file.readLine()) != null) {
						if (line.contains("JSESSIONID")) {
							if (line.contains(".css")) {
								continue;
							}
							lines.add(line);
							//System.out.println(line);							
						}
					}
					if (lines.size() > 0) {
						dcAgent.sendSyncMonitoringData(lines, "HaproxyLog", monitoredTarget);
					}
					filePointer = file.getFilePointer();
				}

				file.close();

			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				Long t1 = System.currentTimeMillis();
				try {
					Thread.sleep(Math.max(period - (t1 - t0), 0));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

	}

	@Override
	public void start() {
		hamt = new Thread(this, "ham-mon");
	}

	@Override
	public void init() {
		hamt.start();
		System.out.println("Haproxy monitor running!");
	}

	@Override
	public void stop() {
		while (!hamt.isInterrupted()) {
			hamt.interrupt();
		}

		System.out.println("Haproxy monitor stopped!");
	}

}
