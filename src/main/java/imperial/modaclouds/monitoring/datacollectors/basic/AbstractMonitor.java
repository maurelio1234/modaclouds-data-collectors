/**
 * Copyright ${2014} Imperial
 * Contact: imperial <weikun.wang11@imperial.ac.uk>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package imperial.modaclouds.monitoring.datacollectors.basic;


/**
 * The abstract class of monitor.
 * 
 */
public abstract class AbstractMonitor implements Runnable
{	
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
	
	/**
	 * Return the name of the monitor.
	 */
	public String getMonitorName() {
		return monitorName;
	}

}
