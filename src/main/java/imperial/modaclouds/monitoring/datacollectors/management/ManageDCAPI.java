package imperial.modaclouds.monitoring.datacollectors.management;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;

public class ManageDCAPI {
	/**
	 * Rest server address.
	 */
	private String address;
	
	/**
	 * Constructor of the class.
	 * @param IP of the server
	 * @param port of the server
	 */
	public ManageDCAPI(String IP, String port) {
		address = "http://"+IP+":"+port+"/commands";
	}
	
	/**
	 * Activate the data collector
	 * @param DC Name of the dataCollector
	 */
	public void activate(String DC) {
		JSONObject obj = new JSONObject();
		obj.put("DC", DC);
		obj.put("Activate", "True");
		
		sendMessage(obj.toJSONString());
	}
	
	/**
	 * Deactivate the data collector
	 * @param DC Name of the dataCollector
	 */
	public void deactivate(String DC){
		JSONObject obj = new JSONObject();
		obj.put("DC", DC);
		obj.put("Activate", "False");
		
		sendMessage(obj.toJSONString());
	}
	
	/**
	 * Reconfigurate the sampling probability.
	 * @param DC Name of the data collector.
	 * @param samplingProb Sampling probability.
	 */
	public void reconfigSamplingProb(String DC, double samplingProb){
		JSONObject obj = new JSONObject();
		obj.put("DC", DC);
		obj.put("SamplingProb", samplingProb);
		
		sendMessage(obj.toJSONString());
	}
	
	/**
	 * Send JSON message.
	 * @param message JSON message.
	 */
	private void sendMessage(String message) {
		HttpPost method = null;

		DefaultHttpClient client = null;

		URI uri;

		HttpResponse httpResponse;
		HttpEntity httpEntity;
		HttpParams httpParams;
		
		try {
			client = new DefaultHttpClient();

			uri = new URI(address);

			method = new HttpPost(uri);
			method.setHeader("Cache-Control","no-cache");
			
			method.setEntity(new StringEntity(message));

			httpResponse = client.execute(method);
			httpEntity = httpResponse.getEntity();

			httpParams = client.getParams();
			HttpConnectionParams.setConnectionTimeout(httpParams, 30000);

			EntityUtils.consume(httpEntity);
		} catch (URISyntaxException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
