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

public interface Consts {

	public static final String RESPONDER_UUID = "B1011111111111111111111111110001";

	public static final String RESPONDER_SERVERNAME = "bluecoveResponderSrv";

	public static final int reconnectSleep = 1000;
	
    public static final int DEVICE_COMPUTER = 0x0100;

    public static final int DEVICE_PHONE = 0x0200;
    
	public static final int TEST_REPLY_OK = 77;
	
	public static final int TEST_TERMINATE = 99;
	
	public static final int TEST_START = 1;
	
	public static final int TEST_STRING = 1;
	
	public static final int TEST_STRING_BACK = 2;
	
	public static final int TEST_BYTE = 3;
	
	public static final int TEST_BYTE_BACK = 4;
	
	public static final int TEST_LAST = 4;
}
