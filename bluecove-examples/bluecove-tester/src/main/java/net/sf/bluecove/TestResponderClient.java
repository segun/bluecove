/**
 *  BlueCove - Java library for Bluetooth
 *  Copyright (C) 2006-2007 Vlad Skarzhevskyy
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *  @version $Id$
 */
package net.sf.bluecove;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.io.Connection;
import javax.microedition.io.Connector;

import net.sf.bluecove.awt.JavaSECommon;
import net.sf.bluecove.util.BluetoothTypesInfo;
import net.sf.bluecove.util.CountStatistic;
import net.sf.bluecove.util.IOUtils;
import net.sf.bluecove.util.IntVar;
import net.sf.bluecove.util.StringUtils;
import net.sf.bluecove.util.TimeStatistic;
import net.sf.bluecove.util.TimeUtils;

/**
 * @author vlads
 * 
 */
public class TestResponderClient extends TestResponderCommon implements Runnable {

	public static int countSuccess = 0;

	public static FailureLog failure = new FailureLog("Client failure");

	public static int discoveryCount = 0;

	public static int connectionCount = 0;

	public static int discoveryDryCount = 0;

	public static int discoverySuccessCount = 0;

	public static long lastSuccessfulDiscovery;

	public static int countConnectionThreads = 0;

	private int connectedConnectionsExpect = 1;

	private int connectionLogPrefixLength = 0;

	private int connectedConnectionsInfo = 1;

	private Vector concurrentConnections = new Vector();

	public static CountStatistic concurrentStatistic = new CountStatistic();

	public static TimeStatistic connectionDuration = new TimeStatistic();

	public static CountStatistic connectionRetyStatistic = new CountStatistic();

	public Thread thread;

	private boolean stoped = false;

	boolean discoveryOnce = false;

	boolean connectOnce = false;

	boolean useDiscoveredDevices = false;

	boolean searchOnlyBluecoveUuid = false;

	boolean isRunning = false;

	boolean runStressTest = false;

	BluetoothInquirer bluetoothInquirer;

	public static Hashtable recentDeviceNames = new Hashtable/* <BTAddress,Name> */();

	public String connectDevice = null;

	public String connectURL = null;

	boolean configured = false;

	private static int sdAttrRetrievableMax = 255;

	public static synchronized void clear() {
		countSuccess = 0;
		failure.clear();
		discoveryCount = 0;
		concurrentStatistic.clear();
		connectionDuration.clear();
	}

	public class BluetoothInquirer implements DiscoveryListener {

		boolean inquiring;

		boolean inquiringDevice;

		boolean searchingServices;

		boolean deviceDiscoveryError;

		Vector devices = new Vector();

		Vector serverURLs = new Vector();

		public int[] attrIDs;

		public final UUID L2CAP = new UUID(0x0100);

		public final UUID RFCOMM = new UUID(0x0003);

		private UUID searchUuidSet[];

		private UUID searchUuidSet2[];

		DiscoveryAgent discoveryAgent;

		int servicesSearchTransID;

		private String servicesOnDeviceName = null;

		private String servicesOnDeviceAddress = null;

		private boolean servicesFound = false;

		private boolean anyServicesFound = false;

		private int anyServicesFoundCount;

		public BluetoothInquirer() {
			inquiringDevice = false;
			inquiring = false;
			if (searchOnlyBluecoveUuid) {
				searchUuidSet = new UUID[] { L2CAP, RFCOMM, Configuration.blueCoveUUID() };
				if (Configuration.testL2CAP.booleanValue()) {
					searchUuidSet2 = new UUID[] { L2CAP, Configuration.blueCoveL2CAPUUID() };
				}
			} else {
				searchUuidSet = new UUID[] { Configuration.discoveryUUID };
			}
			if (!Configuration.testServiceAttributes.booleanValue()) {
				attrIDs = null;
			} else if (Configuration.testAllServiceAttributes.booleanValue()) {
				int allSize = ServiceRecordTester.allTestServiceAttributesSize();
				attrIDs = new int[allSize + 1];
				attrIDs[0] = Consts.TEST_SERVICE_ATTRIBUTE_INT_ID;
				for (int i = 0; i < allSize; i++) {
					attrIDs[1 + i] = Consts.SERVICE_ATTRIBUTE_ALL_START + i;
				}
			} else if (Configuration.testIgnoreNotWorkingServiceAttributes.booleanValue()) {
				attrIDs = new int[] { Consts.TEST_SERVICE_ATTRIBUTE_INT_ID, Consts.TEST_SERVICE_ATTRIBUTE_URL_ID,
						Consts.TEST_SERVICE_ATTRIBUTE_BYTES_ID, Consts.VARIABLE_SERVICE_ATTRIBUTE_BYTES_ID,
						Consts.SERVICE_ATTRIBUTE_BYTES_SERVER_INFO };
			} else {
				attrIDs = new int[] {
						0x0100, // Service name
						Consts.TEST_SERVICE_ATTRIBUTE_INT_ID, Consts.TEST_SERVICE_ATTRIBUTE_STR_ID,
						Consts.TEST_SERVICE_ATTRIBUTE_URL_ID, Consts.TEST_SERVICE_ATTRIBUTE_LONG_ID,
						Consts.TEST_SERVICE_ATTRIBUTE_BYTES_ID, Consts.VARIABLE_SERVICE_ATTRIBUTE_BYTES_ID,
						Consts.SERVICE_ATTRIBUTE_BYTES_SERVER_INFO };
			}
		}

		public boolean hasServers() {
			return ((serverURLs != null) && (serverURLs.size() >= 1));
		}

		public void shutdown() {
			if (inquiring && (discoveryAgent != null)) {
				cancelInquiry();
				cancelServiceSearch();
			}
		}

		private void cancelInquiry() {
			try {
				if (discoveryAgent != null) {
					if (discoveryAgent.cancelInquiry(this)) {
						Logger.debug("Device inquiry was canceled");
					} else if (inquiringDevice) {
						Logger.debug("Device inquiry was not canceled");
					}
				}
			} catch (Throwable e) {
				Logger.error("Cannot cancel Device inquiry", e);
			}
		}

		private void cancelServiceSearch() {
			try {
				if ((servicesSearchTransID != 0) && (discoveryAgent != null)) {
					discoveryAgent.cancelServiceSearch(servicesSearchTransID);
					servicesSearchTransID = 0;
				}
			} catch (Throwable e) {
			}
		}

		public boolean runDeviceInquiry() {
			boolean needToFindDevice = Configuration.clientContinuousDiscoveryDevices
					|| ((devices.size() == 0) && (serverURLs.size() == 0));
			try {
				if (useDiscoveredDevices) {
					copyDiscoveredDevices();
					useDiscoveredDevices = false;
				} else if (needToFindDevice) {
					Logger.debug("Starting Device inquiry");
					deviceDiscoveryError = false;
					devices.removeAllElements();
					long start = System.currentTimeMillis();
					inquiring = true;
					inquiringDevice = true;
					try {
						discoveryAgent = LocalDevice.getLocalDevice().getDiscoveryAgent();
						boolean started = discoveryAgent.startInquiry(DiscoveryAgent.GIAC, this);
						if (!started) {
							Logger.error("Inquiry was not started (may be because the accessCode is not supported)");
							return false;
						}
					} catch (BluetoothStateException e) {
						Logger.error("Cannot start Device inquiry", e);
						return false;
					}
					// By this time inquiryCompleted maybe already been called,
					// because we are too fast
					while (inquiringDevice) {
						synchronized (this) {
							try {
								wait();
							} catch (InterruptedException e) {
								return false;
							}
						}
					}
					inquiringDevice = false;
					if (stoped) {
						return true;
					}
					cancelInquiry();
					Logger.debug("  Device inquiry took " + TimeUtils.secSince(start));
					RemoteDeviceInfo.discoveryInquiryFinished(TimeUtils.since(start));
					if (deviceDiscoveryError && (devices.size() == 0)) {
						return false;
					}
				}

				if (Configuration.clientContinuousServicesSearch || serverURLs.size() == 0) {
					serverURLs.removeAllElements();
					try {
						return startServicesSearch();
					} finally {
						cancelServiceSearch();
					}
				} else {
					return true;
				}
			} finally {
				inquiring = false;
				inquiringDevice = false;
			}
		}

		private void copyDiscoveredDevices() {
			if (RemoteDeviceInfo.devices.size() == 0) {
				Logger.warn("No device in history, run Discovery");
			}
			for (Enumeration iter = RemoteDeviceInfo.devices.elements(); iter.hasMoreElements();) {
				RemoteDeviceInfo dev = (RemoteDeviceInfo) iter.nextElement();
				devices.addElement(dev.remoteDevice);
			}
			if (devices.size() == 0) {
				if (Configuration.storage == null) {
					Logger.warn("no storage");
					return;
				}
				String lastURL = Configuration.getLastServerURL();
				if (StringUtils.isStringSet(lastURL)) {
					Logger.info("Will used device from recent Connections");
					devices.addElement(new RemoteDeviceIheritance(BluetoothTypesInfo.extractBluetoothAddress(lastURL)));
				} else {
					Logger.warn("no recent Connections");
				}
			}
		}

		public void deviceDiscovered(RemoteDevice remoteDevice, DeviceClass cod) {
			if (stoped) {
				return;
			}
			if (Configuration.listedDevicesOnly.booleanValue()
					&& !Configuration.isWhiteDevice(remoteDevice.getBluetoothAddress())) {
				Logger.debug("ignore device " + niceDeviceName(remoteDevice.getBluetoothAddress()) + " "
						+ BluetoothTypesInfo.toString(cod));
				return;
			}
			if ((!Configuration.deviceClassFilter.booleanValue())
					|| ((Configuration.discoverDevicesComputers.booleanValue() && (cod.getMajorDeviceClass() == Consts.DEVICE_COMPUTER)) || ((Configuration.discoverDevicesPhones
							.booleanValue() && (cod.getMajorDeviceClass() == Consts.DEVICE_PHONE))))) {
				devices.addElement(remoteDevice);
			} else {
				Logger.debug("ignore device " + niceDeviceName(remoteDevice.getBluetoothAddress()) + " "
						+ BluetoothTypesInfo.toString(cod));
				return;
			}
			String name = "";
			try {
				if ((Configuration.discoveryGetDeviceFriendlyName.booleanValue()) || Configuration.isBlueCove) {
					name = " [" + remoteDevice.getFriendlyName(false) + "]";
				}
			} catch (IOException e) {
				Logger.debug("er.getFriendlyName," + remoteDevice.getBluetoothAddress(), e);
			}
			if (remoteDevice.isTrustedDevice()) {
				name += " Trusted";
			}
			RemoteDeviceInfo.deviceFound(remoteDevice);
			Logger.debug("deviceDiscovered " + niceDeviceName(remoteDevice.getBluetoothAddress()) + name + " "
					+ remoteDevice.getBluetoothAddress() + " " + BluetoothTypesInfo.toString(cod));
		}

		private boolean startServicesSearch() {
			if (devices.size() == 0) {
				return true;
			}
			Logger.debug("Starting Services search " + TimeUtils.timeNowToString());
			long inquiryStart = System.currentTimeMillis();
			nextDevice: for (Enumeration iter = devices.elements(); iter.hasMoreElements();) {
				if (stoped) {
					break;
				}
				servicesFound = false;
				anyServicesFound = false;
				anyServicesFoundCount = 0;
				long start = System.currentTimeMillis();
				RemoteDevice remoteDevice = (RemoteDevice) iter.nextElement();
				String name = "";
				if ((Configuration.discoveryGetDeviceFriendlyName.booleanValue()) || Configuration.isBlueCove) {
					try {
						name = remoteDevice.getFriendlyName(false);
						if ((name != null) && (name.length() > 0)) {
							recentDeviceNames.put(remoteDevice.getBluetoothAddress().toUpperCase(), name);
						}
					} catch (Throwable e) {
						Logger.error("er.getFriendlyName," + remoteDevice.getBluetoothAddress(), e);
					}
				}
				servicesOnDeviceAddress = remoteDevice.getBluetoothAddress();
				servicesOnDeviceName = niceDeviceName(servicesOnDeviceAddress);
				Logger.debug("Search Services on " + servicesOnDeviceName + " " + name);

				int transID = -1;

				for (int uuidType = 1; uuidType <= 2; uuidType++) {
					UUID[] uuidSet = searchUuidSet;
					if (uuidType == 2) {
						if (searchUuidSet2 != null) {
							uuidSet = searchUuidSet2;
						} else {
							break;
						}
					}
					try {
						discoveryAgent = LocalDevice.getLocalDevice().getDiscoveryAgent();

						int[] shortAttrSet;
						if ((sdAttrRetrievableMax != 0) && (attrIDs != null) && (sdAttrRetrievableMax < attrIDs.length)) {
							shortAttrSet = new int[sdAttrRetrievableMax];
							for (int i = 0; i < sdAttrRetrievableMax; i++) {
								shortAttrSet[i] = attrIDs[i];
							}
							Logger.debug("search attr first " + shortAttrSet.length + " of " + attrIDs.length);
						} else {
							shortAttrSet = attrIDs;
						}
						searchingServices = true;
						servicesSearchTransID = discoveryAgent
								.searchServices(shortAttrSet, uuidSet, remoteDevice, this);
						transID = servicesSearchTransID;
						if (transID <= 0) {
							Logger.warn("servicesSearch TransID mast be positive, " + transID);
						}
					} catch (BluetoothStateException e) {
						Logger.error("Cannot start searchServices", e);
						continue nextDevice;
					}
					// By this time serviceSearchCompleted maybe already been
					// called, because we are too fast
					while (searchingServices) {
						synchronized (this) {
							try {
								wait();
							} catch (InterruptedException e) {
								break;
							}
						}
					}
					cancelServiceSearch();
				}

				RemoteDeviceInfo.searchServices(remoteDevice, servicesFound, TimeUtils.since(start));
				String msg = (anyServicesFound) ? "; " + anyServicesFoundCount + " service(s) found" : "; no services";
				Logger.debug(" Services Search " + transID + " took " + TimeUtils.secSince(start) + msg);
			}
			String msg = "";
			if (serverURLs.size() > 0) {
				msg = "; BC Srv(s) " + serverURLs.size();
			}
			Logger.debug("Services search completed " + TimeUtils.secSince(inquiryStart) + msg);
			return true;
		}

		public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
			if (stoped) {
				return;
			}
			for (int i = 0; i < servRecord.length; i++) {
				anyServicesFound = true;
				anyServicesFoundCount++;
				String url = servRecord[i].getConnectionURL(Configuration.getRequiredSecurity(), false);
				Logger.info("*found server " + url);
				if (discoveryOnce) {
					Logger.debug("ServiceRecord " + (i + 1) + "/" + servRecord.length + "\n"
							+ BluetoothTypesInfo.toString(servRecord[i]));
				}
				if (url == null) {
					// Bogus service Record
					continue;
				}

				boolean isBlueCoveTestService;

				if (searchOnlyBluecoveUuid) {
					isBlueCoveTestService = ServiceRecordTester.testServiceAttributes(servRecord[i],
							servicesOnDeviceName, servicesOnDeviceAddress);
				} else {
					isBlueCoveTestService = ServiceRecordTester.hasServiceClassBlieCoveUUID(servRecord[i]);
					if (isBlueCoveTestService) {

						// Retive other service attributes
						if ((sdAttrRetrievableMax != 0) && (attrIDs != null) && (sdAttrRetrievableMax < attrIDs.length)) {
							// int[] shortAttrSet;
							for (int ai = sdAttrRetrievableMax; ai < attrIDs.length; ai++) {
								try {
									servRecord[i].populateRecord(new int[] { attrIDs[ai] });
								} catch (IOException e) {
									Logger.error("populateRecord", e);
								}
							}
						}

						ServiceRecordTester.testServiceAttributes(servRecord[i], servicesOnDeviceName,
								servicesOnDeviceAddress);
					}
				}

				if (isBlueCoveTestService) {
					discoveryCount++;
					Logger.info("Found BlueCove SRV:"
							+ niceDeviceName(servRecord[i].getHostDevice().getBluetoothAddress()));
				}

				if (searchOnlyBluecoveUuid || isBlueCoveTestService) {
					serverURLs.addElement(url);
				} else {
					Logger.info("is not TestService on "
							+ niceDeviceName(servRecord[i].getHostDevice().getBluetoothAddress()));
				}
				if (isBlueCoveTestService) {
					servicesFound = true;
				}
			}
		}

		public synchronized void serviceSearchCompleted(int transID, int respCode) {
			switch (respCode) {
			case SERVICE_SEARCH_ERROR:
				Logger.error("error occurred while processing the service search");
				break;
			case SERVICE_SEARCH_TERMINATED:
				Logger.info("SERVICE_SEARCH_TERMINATED");
				break;
			case SERVICE_SEARCH_DEVICE_NOT_REACHABLE:
				Logger.info("SERVICE_SEARCH_DEVICE_NOT_REACHABLE");
				break;
			}
			searchingServices = false;
			notifyAll();
		}

		public synchronized void inquiryCompleted(int discType) {
			switch (discType) {
			case INQUIRY_ERROR:
				Logger.error("device inquiry ended abnormally");
				deviceDiscoveryError = true;
				break;
			case INQUIRY_TERMINATED:
				Logger.info("Device discovery has been canceled by the application");
				break;
			case INQUIRY_COMPLETED:
			}
			inquiringDevice = false;
			notifyAll();
		}

	}

	public TestResponderClient() throws BluetoothStateException {

		TestResponderCommon.startLocalDevice();

		String v = LocalDevice.getProperty("bluetooth.sd.attr.retrievable.max");
		if (v != null) {
			sdAttrRetrievableMax = Integer.valueOf(v).intValue();
			if ((sdAttrRetrievableMax > 7) && (Configuration.isJ2ME)) {
				sdAttrRetrievableMax = 7;
			}
		}

	}

	static boolean isMultiProtocol() {
		return ((Configuration.supportL2CAP) && Configuration.testL2CAP.booleanValue() && Configuration.testRFCOMM
				.booleanValue());
	}

	public void connectAndTest(String serverURL, String urlArgs, IntVar firstCase, IntVar lastCase,
			TestResponderClientConnection connectionHandler) {
		String deviceAddress = BluetoothTypesInfo.extractBluetoothAddress(serverURL);
		String deviceName = niceDeviceName(deviceAddress);
		long start = System.currentTimeMillis();
		Logger.debug("connect:" + deviceName + " " + serverURL);
		String logPrefix = "";
		if (isMultiProtocol()) {
			deviceName = connectionHandler.protocolID() + " " + deviceName;
		}
		if (connectedConnectionsExpect > 1) {
			logPrefix = "[" + StringUtils.padRight(deviceName, connectionLogPrefixLength, ' ') + "] ";
		}
		for (int testType = firstCase.getValue(); (!stoped) && (runStressTest || testType <= lastCase.getValue()); testType++) {
			Connection conn = null;
			ConnectionHolder c = null;
			TestStatus testStatus = new TestStatus();
			testStatus.pairBTAddress = deviceAddress;
			TestTimeOutMonitor monitor = null;
			long connectionStartTime = 0;
			try {
				if (!runStressTest) {
					Logger.debug(logPrefix + "test #" + testType + " connects");
				} else {
					testType = Configuration.STERSS_TEST_CASE.getValue();
				}
				int connectionOpenTry = 0;
				while ((conn == null) && (!stoped)) {
					try {
						conn = Connector.open(serverURL + urlArgs, Connector.READ_WRITE, true);
					} catch (IOException e) {
						connectionOpenTry++;
						if ((stoped) || (connectionOpenTry > CommunicationTester.clientConnectionOpenRetry)) {
							throw e;
						}
						Logger.debug(logPrefix + "Connector error", e);
						Thread.sleep(Configuration.clientSleepOnConnectionRetry);
						Logger.debug(logPrefix + "connect retry:" + connectionOpenTry);
						String cCount = LocalDevice.getProperty("bluecove.connections");
						if ((cCount != null) && (!"0".equals(cCount))) {
							Logger.debug(logPrefix + "has connections:" + cCount);
						}
					}
				}
				if (stoped) {
					return;
				}
				c = connectionHandler.connected(conn);

				connectionStartTime = System.currentTimeMillis();
				connectionRetyStatistic.add(connectionOpenTry);
				connectionCount++;

				c.registerConcurrent(concurrentConnections);
				c.concurrentNotify();

				if (connectedConnectionsInfo < c.concurrentCount) {
					connectedConnectionsInfo = c.concurrentCount;
					Logger.info(logPrefix + "now connected:" + connectedConnectionsInfo);
					synchronized (TestResponderClient.this) {
						TestResponderClient.this.notifyAll();
					}
				}
				c.active();
				monitor = new TestTimeOutMonitor(logPrefix + "test #" + testType, c, Configuration.clientTestTimeOutSec);
				if (!runStressTest) {
					Logger.debug(logPrefix + "run test #" + testType);
				} else {
					Logger.debug(logPrefix + "connected:" + connectionCount);
					if (connectionCount % 5 == 0) {
						Logger.debug("Test time " + TimeUtils.secSince(start));
					}
				}
				connectionHandler.executeTest(testType, testStatus);

				if (monitor.isShutdownCalled()) {
					failure.addFailure(deviceName + " test #" + testType + " " + testStatus.getName()
							+ " termintade by  by TimeOut");
				} else if (testStatus.isError) {
					failure.addFailure(deviceName + " test #" + testType + " " + testStatus.getName());
				} else if (testStatus.isSuccess) {
					countSuccess++;
					Logger.debug(logPrefix + "test #" + testType + " " + testStatus.getName() + ": OK");
				} else if (testStatus.streamClosed) {
					Logger.debug(logPrefix + "see server log");
				} else {
					connectionHandler.replySuccess(logPrefix, testType);
					countSuccess++;
					Logger.debug(logPrefix + "test #" + testType + " " + testStatus.getName() + ": OK");
				}
				if (connectionCount % 5 == 0) {
					Logger.info("*Success:" + countSuccess + " Failure:" + failure.countFailure);
				}
				Configuration.setLastServerURL(serverURL);

				// Dellay to see if many connections are made.
				if ((connectedConnectionsExpect > 1) && (connectedConnectionsInfo < connectedConnectionsExpect)) {
					synchronized (TestResponderClient.this) {
						try {
							TestResponderClient.this.wait(3 * 1000);
						} catch (InterruptedException e) {
							break;
						}
					}
					Logger.debug(logPrefix + "concurrentCount " + c.concurrentCount);
				}
			} catch (Throwable e) {
				if (!stoped) {
					if ((monitor != null) && (monitor.isShutdownCalled())) {
						failure.addFailure(deviceName + " test #" + testType + " " + testStatus.getName()
								+ " termintade by  by TimeOut");
					} else {
						failure.addFailure(deviceName + " test #" + testType + " " + testStatus.getName(), e);
					}
				}
				Logger.error(deviceName + " test #" + testType + " " + testStatus.getName(), e);
			} finally {
				if (connectionStartTime != 0) {
					connectionDuration.add(TimeUtils.since(connectionStartTime));
				}
				if (monitor != null) {
					monitor.finish();
				}
				if (c != null) {
					c.disconnected();
					if (c.concurrentCount != 0) {
						concurrentStatistic.add(c.concurrentCount);
					}
					c.shutdown();
				} else {
					IOUtils.closeQuietly(conn);
				}
			}
			// Let the server restart
			if (!stoped) {
				try {
					Thread.sleep(Configuration.clientSleepBetweenConnections);
				} catch (InterruptedException e) {
					break;
				}
			}
		}
		if (!Configuration.clientContinuous.booleanValue()) {
			connectionHandler.sendStopServerCmd(serverURL);
		}
	}

	private boolean connectAndTest(String serverURL) {
		if (BluetoothTypesInfo.isRFCOMM(serverURL)) {
			if (Configuration.testRFCOMM.booleanValue()) {
				TestResponderClientRFCOMM.connectAndTest(this, serverURL);
				return true;
			}
		} else if (BluetoothTypesInfo.isL2CAP(serverURL)) {
			if (Configuration.supportL2CAP) {
				if (Configuration.testL2CAP.booleanValue()) {
					TestResponderClientL2CAP.connectAndTest(this, serverURL);
					return true;
				}
			} else {
				Logger.warn("Can't test L2CAP on this stack");
			}
		} else {
			Logger.warn("No tests for connection type " + serverURL);
		}
		return false;
	}

	private class ClientConnectionTread extends Thread {

		String url;

		boolean started;

		ClientConnectionTread(String url) {
			// CLDC_1_0 super("ClientConnectionTread" +
			// (++countConnectionThreads));
			++countConnectionThreads;

			this.url = url;
		}

		public void run() {
			started = connectAndTest(url);
		}
	}

	private void connectAndTest(Vector urls) {
		int numberOfURLs = urls.size();
		if ((!Configuration.clientTestConnectionsMultipleThreads) || (numberOfURLs == 1)) {
			connectedConnectionsExpect = 1;
			connectedConnectionsInfo = 1;
			for (Enumeration en = urls.elements(); en.hasMoreElements();) {
				if (stoped) {
					break;
				}
				String url = (String) en.nextElement();
				connectAndTest(url);
			}
		} else {
			connectedConnectionsExpect = numberOfURLs;
			connectedConnectionsInfo = 1;

			connectionLogPrefixLength = 0;
			for (Enumeration en = urls.elements(); en.hasMoreElements();) {
				String deviceAddress = BluetoothTypesInfo.extractBluetoothAddress((String) en.nextElement());
				String deviceName = niceDeviceName(deviceAddress);
				if (deviceName.length() > connectionLogPrefixLength) {
					connectionLogPrefixLength = deviceName.length();
				}
			}

			if (isMultiProtocol()) {
				connectionLogPrefixLength += 3;
			}

			Logger.debug("start " + numberOfURLs + " threads");
			Vector threads = new Vector();
			for (Enumeration en = urls.elements(); en.hasMoreElements();) {
				ClientConnectionTread t = new ClientConnectionTread((String) en.nextElement());
				t.start();
				threads.addElement(t);
			}
			int connectedConnectionsStartedExpect = 0;
			for (Enumeration en = threads.elements(); en.hasMoreElements();) {
				ClientConnectionTread t = (ClientConnectionTread) en.nextElement();
				if (t.started) {
					connectedConnectionsStartedExpect++;
				}
				try {
					t.join();
				} catch (InterruptedException e) {
					break;
				}
			}
			if (connectedConnectionsInfo < connectedConnectionsStartedExpect) {
				if (!stoped) {
					failure.addFailure("Fails to establish " + connectedConnectionsStartedExpect
							+ " connections same time");
					Logger.error("Fails to establish " + connectedConnectionsStartedExpect + " connections same time");
				}
			} else {
				if (connectedConnectionsInfo > 1) {
					Logger.info("Established " + connectedConnectionsInfo + " connections same time");
				}
			}
		}
	}

	public void configured() {
		synchronized (this) {
			configured = true;
			this.notifyAll();
		}
	}

	public void run() {
		synchronized (this) {
			while (!configured) {
				try {
					this.wait();
				} catch (InterruptedException e) {
					return;
				}
			}
		}
		Logger.debug("Client started..." + TimeUtils.timeNowToString());
		isRunning = true;
		try {
			bluetoothInquirer = new BluetoothInquirer();

			int startTry = 0;
			if (connectURL != null) {
				if (!connectURL.equals("")) {
					bluetoothInquirer.serverURLs.addElement(connectURL);
				}
			} else if (connectDevice != null) {
				Configuration.clientContinuousDiscoveryDevices = false;
				bluetoothInquirer.devices.addElement(new RemoteDeviceIheritance(connectDevice));
			}

			while (!stoped) {
				if ((connectURL != null) && (connectURL.equals(""))) {
					try {
						bluetoothInquirer.serverURLs.removeAllElements();
						DiscoveryAgent discoveryAgent = LocalDevice.getLocalDevice().getDiscoveryAgent();
						UUID uuid = (searchOnlyBluecoveUuid) ? Configuration.blueCoveUUID()
								: Configuration.discoveryUUID;
						String url = discoveryAgent.selectService(uuid, Configuration.getRequiredSecurity(), false);
						if (url != null) {
							Logger.debug("selectService service found " + url);
							bluetoothInquirer.serverURLs.addElement(url);
						} else {
							Logger.debug("selectService service not found");
						}
					} catch (BluetoothStateException e) {
						Logger.error("Cannot selectService", e);
					}
				} else if ((!bluetoothInquirer.hasServers())
						|| (Configuration.clientContinuousDiscovery && (connectURL == null))
						|| (!Configuration.clientTestConnections)) {
					if (!bluetoothInquirer.runDeviceInquiry()) {
						if (stoped) {
							break;
						}
						startTry++;
						try {
							Thread.sleep(Configuration.clientSleepOnDeviceInquiryError);
						} catch (Exception e) {
							break;
						}
						if (startTry < 3) {
							continue;
						}
						Switcher.yield(this);
					} else {
						startTry = 0;
					}
					while (bluetoothInquirer.inquiring) {
						try {
							Thread.sleep(1000);
						} catch (Exception e) {
							break;
						}
					}
				}
				if ((Configuration.clientTestConnections) && (bluetoothInquirer.hasServers())) {
					discoveryDryCount = 0;
					discoverySuccessCount++;
					lastSuccessfulDiscovery = System.currentTimeMillis();
					if (!discoveryOnce) {
						connectAndTest(bluetoothInquirer.serverURLs);
					}
				} else {
					discoveryDryCount++;
					if ((discoveryDryCount % 5 == 0) && (lastSuccessfulDiscovery != 0)) {
						Logger.debug("No services " + discoveryDryCount + " times for "
								+ TimeUtils.secSince(lastSuccessfulDiscovery) + " " + discoverySuccessCount);
					}
				}
				Logger.info("*Success:" + countSuccess + " Failure:" + failure.countFailure);
				if ((countSuccess + failure.countFailure > 0) && (!Configuration.clientContinuous.booleanValue())) {
					break;
				}
				if (stoped || discoveryOnce || connectOnce) {
					break;
				}
				Switcher.yield(this);
			}
		} catch (Throwable e) {
			if (!stoped) {
				Logger.error("cleint error ", e);
			}
		} finally {
			connectURL = null;
			isRunning = false;
			Logger.info("Client finished! " + TimeUtils.timeNowToString());
			Switcher.yield(this);
		}
	}

	public void shutdown() {
		Logger.info("shutdownClient");
		stoped = true;
		if (bluetoothInquirer != null) {
			bluetoothInquirer.shutdown();
		}
		if (Configuration.cldcStub != null) {
			Configuration.cldcStub.interruptThread(thread);
		}
	}

	public static void main(String[] args) {
		JavaSECommon.initOnce();
		try {
			(new TestResponderClient()).run();
			// System.exit(0);
		} catch (Throwable e) {
			Logger.error("start error ", e);
		}
	}
}