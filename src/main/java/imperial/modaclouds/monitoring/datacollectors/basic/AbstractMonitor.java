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
package imperial.modaclouds.monitoring.datacollectors.basic;

import it.polimi.tower4clouds.data_collector_library.DCAgent;


/**
 * The abstract class of monitor.
 * 
 */
public abstract class AbstractMonitor implements Runnable
{	
	public AbstractMonitor(String resourceId, String mode) {
		this.resourceId = resourceId;
		this.mode = mode;
	}
	
	/**
	 * Start monitor.
	 */
	public abstract void start();
	
	/**
	 * Initialize monitor.
	 */
	public abstract void init();
	
	/**
	 * Stop monitor.
	 */
	public abstract void stop();

	/**
	 * The name of the monitor.
	 */
	protected String monitorName;
	
	protected String mode;
	
	protected String resourceId;

	/**
	 * Return the name of the monitor.
	 */
	public String getMonitorName() {
		return monitorName;
	}

	public abstract void setDCAgent(DCAgent dcAgent);

}
