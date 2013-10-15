/**
 @header@
 */


package org.pcmm.rcd;

import java.net.InetAddress;
import java.net.Socket;

public interface IPCMMPolicyServer extends IPCMMServer, IPCMMClient {

    /**
     * IANA assigned port number.
     */
    static final int IANA_PORT = 3918;

    /**
     * establishes COPS connection with the CMTS
     *
     * @param host
     *            : remote host name or ip address
     * @return connected socket.
     */
    Socket requestCMTSConnection(String host);

    /**
     * establishes COPS connection with the CMTS
     *
     * @param host
     *            : remote ip address
     * @return connected socket.
     */
    Socket requestCMTSConnection(InetAddress host);

    /**
     * initiates a Gate-Set with the CMTS
     *
     * @return
     */
    boolean gateSet();

    /**
     * initiates a Gate-Info with the CMTS
     *
     * @return
     */
    boolean gateInfo();

    /**
     * initiates a Gate-Delete with the CMTS
     *
     * @return
     */
    boolean gateDelete();

    /**
     * sends synch request
     *
     * @return
     */
    boolean synchronize();

}
